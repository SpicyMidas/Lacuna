package com.lacuna.mod.eclipse;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.logic.ChargeMath;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import com.lacuna.mod.util.Teleporter;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Eclipser dash: consumes all charge and carries the player along the look vector over a few ticks.
 * <p>
 * The path is computed once on the server and stops before solid blocks (no phasing). The dashing client receives
 * it through the {@code eclipse_dash} effect payload and drives its own velocity along it each tick (smooth, no
 * teleport); the server follows the player's real progress and sweeps the dasher's box through space every tick
 * (both along the planned line and along the player's real motion since the last tick). Every creature the swept
 * volume touches is cut exactly once, however many stand in a row, and hurled sideways off the dash line like air
 * shoved aside by a speeding train (small lift, a little drag along the dash); creatures just beside the path are
 * pushed by the air wake without damage. When the player arrives (or after a short grace period, teleporting a
 * client that never moved) the dash ends and only then Shadow Walk starts.
 */
public final class EclipseDash {
    public static final String FEATURE = "eclipse_dash";
    private static final double STEP = 0.25;
    /** Extra ticks the server waits for a lagging client to reach the end before finishing the dash itself. */
    private static final int GRACE = 10;
    /** Creatures closer than this to the dash line (sideways) are blown to a random side. */
    private static final double CENTER_TOLERANCE = 0.15;
    /** Hit sound layers per sweep at most: a long row of victims must not flood the sound engine. */
    private static final int SOUNDS_PER_SWEEP = 3;
    private static final Map<UUID, Dash> ACTIVE = new HashMap<>();

    private static final class Dash {
        final ResourceKey<Level> dimension;
        final Vec3 start;
        final Vec3 end;
        final Vec3 dir;
        final double length;
        final int ticks;
        final int charge;
        final float damage;
        final BlockPos origin;
        /** Horizontal dash direction and its horizontal perpendicular (null for a dash straight up or down). */
        @Nullable final Vec3 flat;
        @Nullable final Vec3 side;
        /** Sideways speed given to cut creatures (blocks per tick) and their upward lift. */
        final double shove;
        final double lift;
        /** Entity ids already cut / already pushed by the wake: each happens once per dash. */
        final IntSet hit = new IntOpenHashSet();
        final IntSet gusted = new IntOpenHashSet();
        double progress;
        /** Where the dasher stood at the previous sweep (its real motion is swept too). */
        Vec3 lastPos;
        int age;

        Dash(ResourceKey<Level> dimension, Vec3 start, Vec3 end, Vec3 dir, int charge, BlockPos origin) {
            this.dimension = dimension;
            this.start = start;
            this.end = end;
            this.dir = dir;
            this.length = start.distanceTo(end);
            this.ticks = ChargeMath.dashTicks(length);
            this.charge = charge;
            this.damage = ChargeMath.dashDamage(charge);
            this.origin = origin;
            Vec3 horizontal = new Vec3(dir.x, 0.0, dir.z);
            if (horizontal.lengthSqr() > 0.0225) {
                this.flat = horizontal.normalize();
                this.side = new Vec3(-flat.z, 0.0, flat.x);
            } else {
                this.flat = null;
                this.side = null;
            }
            double power = ChargeMath.clamp(charge) / 100.0;
            double speed = length / Math.max(1, ticks);
            this.shove = Balance.ECLIPSE_SHOVE_BASE + Balance.ECLIPSE_SHOVE_PER_SPEED * speed + Balance.ECLIPSE_SHOVE_PER_CHARGE * power;
            this.lift = Balance.ECLIPSE_SHOVE_LIFT + Balance.ECLIPSE_SHOVE_LIFT_PER_CHARGE * power;
            this.lastPos = start;
        }

        Vec3 at(double fraction) {
            return start.add(end.subtract(start).scale(fraction));
        }
    }

    private EclipseDash() {}

    public static boolean isDashing(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static boolean perform(ServerPlayer player, ItemStack stack, InteractionHand hand, int charge) {
        if (isDashing(player)) {
            return false;
        }
        return SafeRun.call(FEATURE, () -> begin(player, stack, hand, charge), false, () -> ACTIVE.remove(player.getUUID()));
    }

    private static boolean begin(ServerPlayer player, ItemStack stack, InteractionHand hand, int charge) {
        ServerLevel level = player.serverLevel();
        player.stopRiding();
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
        Vec3 look = player.getLookAngle().normalize();
        Vec3 dir = look;
        if (player.onGround() && look.y < 0.0) {
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            if (flat.lengthSqr() > 0.04) {
                dir = flat.normalize(); // aiming at the floor while standing: skim along it instead of stopping at once
            }
        }
        double distance = ChargeMath.dashDistance(charge, LacunaConfig.get(LacunaConfig.ECLIPSE_DASH_MAX));
        Vec3 start = player.position();
        Vec3 end = start;
        AABB box = player.getBoundingBox();
        for (double t = STEP; t <= distance + 1e-6; t += STEP) {
            Vec3 p = start.add(dir.scale(t));
            BlockPos bp = BlockPos.containing(p);
            if (!level.hasChunkAt(bp) || !level.getWorldBorder().isWithinBounds(bp)
                    || p.y < level.getMinBuildHeight() + 1 || p.y > level.getMaxBuildHeight() - 3) {
                break;
            }
            if (!level.noCollision(player, box.move(p.subtract(start)))) {
                break; // the dash always stops before solid blocks
            }
            end = p;
        }

        Dash dash = new Dash(level.dimension(), start, end, dir, charge, player.blockPosition());
        ACTIVE.put(player.getUUID(), dash);
        EclipserItem.setState(stack, new EclipseCharge(0, EclipserItem.state(stack).lastHit()));
        stack.hurtAndBreak(ChargeMath.durabilityCost(charge), player, LivingEntity.getSlotForHand(hand));
        EclipserItem.showCharge(player, 0);
        player.resetFallDistance();

        LacunaFx.send(level, start.add(end).scale(0.5), LacunaFx.NEAR + dash.length,
                new FxPayload("eclipse_dash", player.getId(), start, end, dash.ticks, charge, player.getYRot(), 0xFFFFFFFF));
        float pitch = 0.9F + level.random.nextFloat() * 0.15F;
        level.playSound(null, start.x, start.y + 1.0, start.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 0.7F * pitch);
        level.playSound(null, start.x, start.y + 1.0, start.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.5F * pitch);
        level.playSound(null, start.x, start.y + 1.0, start.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.9F, 0.55F * pitch);
        // the air tearing open in front of the blade
        Vec3 chest = start.add(0, 1.0, 0);
        Sfx.at(level, chest, SoundEvents.WIND_CHARGE_BURST, 0.85F, 0.6F * pitch);
        Sfx.at(level, chest, SoundEvents.BREEZE_SHOOT, 0.7F, 0.5F * pitch);
        Sfx.at(level, chest, SoundEvents.TRIDENT_RIPTIDE_1, 0.6F, 1.25F * pitch);
        sweep(player, dash, 0.0); // whatever stands right on the starting point is cut at once
        return true;
    }

    // ------------------------------------------------------------------------------------------ ticking

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Dash dash = ACTIVE.get(player.getUUID());
        if (dash == null) {
            return;
        }
        if (SafeRun.isDisabled(FEATURE)) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        SafeRun.run(FEATURE, () -> step(player, dash), () -> ACTIVE.remove(player.getUUID()));
    }

    private static void step(ServerPlayer player, Dash dash) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.level().dimension() != dash.dimension) {
            ACTIVE.remove(player.getUUID()); // died, left, changed dimension or an operator took over: no Shadow Walk
            return;
        }
        dash.age++;
        player.resetFallDistance();
        // real progress of the player along the dash line (monotonic, never off the line)
        Vec3 rel = player.position().subtract(dash.start);
        double along = dash.length < 1.0E-4 ? 1.0 : rel.dot(dash.dir) / dash.length;
        sweep(player, dash, Math.max(0.0, Math.min(1.0, along)));
        boolean arrived = player.position().distanceTo(dash.end) < 0.75;
        if (dash.age >= dash.ticks && (arrived || dash.age >= dash.ticks + GRACE)) {
            finish(player, dash);
        }
    }

    /**
     * Sweeps the dasher's box from the last progress to {@code upTo} along the dash line, and along the player's real
     * motion since the previous sweep. Every valid creature the swept volume touches is cut (once per dash, in order
     * along the dash, never stopping at the first one); creatures beside it, within the air wake, are pushed aside.
     */
    private static void sweep(ServerPlayer player, Dash dash, double upTo) {
        double from = dash.progress;
        double to = Math.max(from, upTo);
        dash.progress = to;
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        Vec3 prev = dash.lastPos;
        dash.lastPos = pos;
        Vec3 a = dash.at(from);
        Vec3 ab = dash.at(to).subtract(a);
        Vec3 moved = pos.subtract(prev);
        // a jump far off the dash (an operator teleport...) is not a sweep
        boolean real = moved.lengthSqr() > 1.0E-8 && moved.length() < dash.length + 4.0;

        // the dasher's box, feet-relative, widened to the cut reach and to the wake reach
        AABB local = player.getBoundingBox().move(pos.scale(-1.0));
        double margin = Math.max(0.0, Balance.ECLIPSE_DASH_RADIUS - player.getBbWidth() / 2.0);
        AABB cutBox = local.inflate(margin, Balance.ECLIPSE_DASH_VERTICAL_MARGIN, margin);
        AABB wakeBox = cutBox.inflate(Balance.ECLIPSE_GUST_WIDTH, 0.5, Balance.ECLIPSE_GUST_WIDTH);

        AABB area = wakeBox.move(a).expandTowards(ab);
        if (real) {
            area = area.minmax(wakeBox.move(prev).expandTowards(moved));
        }
        double head = Math.max(a.add(ab).subtract(dash.start).dot(dash.dir), pos.subtract(dash.start).dot(dash.dir));

        List<LivingEntity> cut = new ArrayList<>();
        List<LivingEntity> wake = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> !dash.hit.contains(e.getId()) && affects(player, e))) {
            AABB box = e.getBoundingBox();
            if (sweptHits(cutBox.move(a), ab, box) || (real && sweptHits(cutBox.move(prev), moved, box))) {
                cut.add(e);
            } else if (!dash.gusted.contains(e.getId())
                    && box.getCenter().subtract(dash.start).dot(dash.dir) <= head + 0.25 // beside the blade, never ahead
                    && (sweptHits(wakeBox.move(a), ab, box) || (real && sweptHits(wakeBox.move(prev), moved, box)))) {
                wake.add(e);
            }
        }
        cut.sort(Comparator.comparingDouble(e -> e.position().subtract(dash.start).dot(dash.dir)));
        int sounds = 0;
        for (LivingEntity target : cut) {
            dash.hit.add(target.getId());
            cut(player, dash, target, sounds++ < SOUNDS_PER_SWEEP);
        }
        if (!wake.isEmpty()) {
            Vec3 at = wake.get(0).getBoundingBox().getCenter();
            Sfx.at(level, at, SoundEvents.WIND_CHARGE_BURST, 0.45F, 1.35F + level.random.nextFloat() * 0.2F);
            Sfx.at(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.4F);
        }
        for (LivingEntity target : wake) {
            dash.gusted.add(target.getId());
            gust(level, dash, target);
        }
    }

    /** One creature cut by the dash: full damage (armour applies), then flung sideways off the dash line. */
    private static void cut(ServerPlayer player, Dash dash, LivingEntity target, boolean sound) {
        ServerLevel level = player.serverLevel();
        Vec3 c = target.getBoundingBox().getCenter();
        Vec3 side = sideOf(dash, c, level.random);
        // the hurt cooldown of a melee swing just before must not swallow the cut; the eclipse damage type also
        // bypasses it (minecraft:bypasses_cooldown) and skips vanilla knockback (the wake shoves victims itself)
        target.invulnerableTime = 0;
        boolean landed = target.hurt(ModDamageTypes.source(level, ModDamageTypes.ECLIPSE, player), dash.damage);
        Vec3 velocity = landed || target.isBlocking() ? shove(target, side, dash.shove, dash.flat, dash.lift) : Vec3.ZERO;

        LacunaFx.send(level, c, new FxPayload("eclipse_hit", target.getId(), c, dash.dir,
                target.getBbHeight(), target.getBbWidth(), dash.hit.size(), 0xFFFFFFFF));
        LacunaFx.send(level, c, new FxPayload("eclipse_gust", target.getId(), c,
                velocity.lengthSqr() > 1.0E-6 ? velocity : side.scale(0.4), target.getBbHeight(), target.getBbWidth(), 1.0F, 0xFFFFFFFF));
        if (sound) {
            float pitch = 0.85F + level.random.nextFloat() * 0.3F;
            level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F * pitch);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 0.6F * pitch);
            Sfx.at(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9F, 0.75F * pitch);
            Sfx.at(level, c, SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.55F, 0.95F * pitch);
        }
    }

    /** A creature just beside the path: the air wake pushes it aside (weaker further out), no damage. */
    private static void gust(ServerLevel level, Dash dash, LivingEntity target) {
        Vec3 c = target.getBoundingBox().getCenter();
        double distance = ChargeMath.segmentDistance(c.x, c.y, c.z, dash.start.x, dash.start.y, dash.start.z,
                dash.end.x, dash.end.y, dash.end.z);
        double inner = Balance.ECLIPSE_DASH_RADIUS + target.getBbWidth() / 2.0;
        double falloff = 1.0 - 0.7 * Mth.clamp((distance - inner) / Balance.ECLIPSE_GUST_WIDTH, 0.0, 1.0);
        Vec3 side = sideOf(dash, c, level.random);
        Vec3 velocity = shove(target, side, dash.shove * Balance.ECLIPSE_GUST_FACTOR * falloff, dash.flat, Balance.ECLIPSE_GUST_LIFT);
        LacunaFx.send(level, c, new FxPayload("eclipse_gust", target.getId(), c,
                velocity.lengthSqr() > 1.0E-6 ? velocity : side.scale(0.25), target.getBbHeight(), target.getBbWidth(), 0.0F, 0xFFFFFFFF));
    }

    /** Unit horizontal vector pointing from the dash line to the side {@code c} is on (a random side right on the line). */
    private static Vec3 sideOf(Dash dash, Vec3 c, RandomSource random) {
        Vec3 rel = c.subtract(dash.start);
        if (dash.side != null) {
            double lateral = rel.dot(dash.side);
            if (Math.abs(lateral) < CENTER_TOLERANCE) {
                return random.nextBoolean() ? dash.side : dash.side.scale(-1.0);
            }
            return lateral > 0.0 ? dash.side : dash.side.scale(-1.0);
        }
        // straight up or down: blown outwards from the line
        Vec3 onLine = dash.start.add(dash.dir.scale(Mth.clamp(rel.dot(dash.dir), 0.0, dash.length)));
        Vec3 radial = new Vec3(c.x - onLine.x, 0.0, c.z - onLine.z);
        if (radial.lengthSqr() < CENTER_TOLERANCE * CENTER_TOLERANCE) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        }
        return radial.normalize();
    }

    /**
     * Throws {@code target} sideways at {@code strength} blocks per tick plus a little drag along the dash and an
     * upward {@code lift}. Goes through vanilla knockback, so knockback resistance and the NeoForge knockback event
     * apply; returns the resulting velocity (zero if nothing moved).
     */
    private static Vec3 shove(LivingEntity target, Vec3 side, double strength, @Nullable Vec3 drag, double lift) {
        if (target.getType().is(ModTags.Entities.CANNOT_BE_BANISHED)) {
            strength *= Balance.ECLIPSE_SHOVE_BOSS;
            lift *= Balance.ECLIPSE_SHOVE_BOSS;
        }
        Vec3 push = side.scale(strength);
        if (drag != null) {
            push = push.add(drag.scale(strength * Balance.ECLIPSE_SHOVE_DRAG));
        }
        double len = push.horizontalDistance();
        double resist = Mth.clamp(1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        if (len < 1.0E-4 || resist <= 0.0) {
            return Vec3.ZERO;
        }
        Vec3 before = target.getDeltaMovement();
        target.knockback(len, -push.x / len, -push.z / len);
        Vec3 v = target.getDeltaMovement();
        if (v.equals(before)) {
            return Vec3.ZERO; // cancelled by a knockback event
        }
        target.setDeltaMovement(v.x, Math.max(before.y, lift * resist), v.z);
        target.hasImpulse = true;
        target.hurtMarked = true; // players get the new motion at once
        return target.getDeltaMovement();
    }

    /** True if {@code box} moved by {@code delta * t} overlaps {@code target} for some t in 0..1 (slab test). */
    static boolean sweptHits(AABB box, Vec3 delta, AABB target) {
        double[] t = {0.0, 1.0};
        return slab(box.minX, box.maxX, delta.x, target.minX, target.maxX, t)
                && slab(box.minY, box.maxY, delta.y, target.minY, target.maxY, t)
                && slab(box.minZ, box.maxZ, delta.z, target.minZ, target.maxZ, t);
    }

    private static boolean slab(double lo, double hi, double d, double targetLo, double targetHi, double[] t) {
        if (Math.abs(d) < 1.0E-9) {
            return lo <= targetHi && hi >= targetLo;
        }
        double s0 = (targetLo - hi) / d;
        double s1 = (targetHi - lo) / d;
        if (s0 > s1) {
            double swap = s0;
            s0 = s1;
            s1 = swap;
        }
        t[0] = Math.max(t[0], s0);
        t[1] = Math.min(t[1], s1);
        return t[0] <= t[1];
    }

    /**
     * Who the dash may cut and push: never the dasher, its mount or passengers, teammates, armour stands, villagers,
     * snow golems, player-built iron golems or its own pets (nor anyone's pets without PvP); other players only when
     * PvP is on and the dasher may harm them (never creative or spectating players).
     */
    static boolean affects(ServerPlayer player, LivingEntity e) {
        if (e == player || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) {
            return false;
        }
        if (e == player.getVehicle() || player.hasPassenger(e) || player.isPassengerOfSameVehicle(e) || player.isAlliedTo(e)) {
            return false;
        }
        MinecraftServer server = player.getServer();
        boolean pvp = server != null && server.isPvpAllowed();
        if (e instanceof Player other) {
            return pvp && !other.isCreative() && player.canHarmPlayer(other);
        }
        if (e instanceof AbstractVillager || e instanceof SnowGolem || (e instanceof IronGolem golem && golem.isPlayerCreated())) {
            return false;
        }
        if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            return pvp && !player.getUUID().equals(pet.getOwnerUUID())
                    && !(pet.getOwner() instanceof LivingEntity owner && player.isAlliedTo(owner));
        }
        return true;
    }

    private static void finish(ServerPlayer player, Dash dash) {
        ACTIVE.remove(player.getUUID());
        sweep(player, dash, 1.0);
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        if (pos.distanceTo(dash.end) > 1.0
                && ChargeMath.segmentDistance(pos.x, pos.y, pos.z, dash.start.x, dash.start.y, dash.start.z,
                dash.end.x, dash.end.y, dash.end.z) < 3.0
                && level.noCollision(player, player.getBoundingBox().move(dash.end.subtract(pos)))) {
            // the client never moved (lost payload, heavy lag): finish the dash for it
            Teleporter.teleport(player, level, dash.end, player.getYRot(), player.getXRot());
            pos = dash.end;
        }
        player.resetFallDistance();
        LacunaFx.send(level, pos, new FxPayload("eclipse_end", player.getId(), pos, dash.dir, dash.charge, 0, 0, 0xFFFFFFFF));
        level.playSound(null, pos.x, pos.y + 1.0, pos.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 1.0F, 0.55F);
        level.playSound(null, pos.x, pos.y + 1.0, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, pos.x, pos.y + 1.0, pos.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.35F, 0.6F);
        // the torn air slamming shut behind the dasher
        Sfx.at(level, pos.add(0, 1.0, 0), SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.6F, 0.7F);
        Sfx.at(level, dash.start.add(dash.end).scale(0.5).add(0, 1.0, 0), SoundEvents.WIND_CHARGE_BURST, 0.5F, 0.85F);
        if (ShadowWalk.enabled()) {
            ShadowWalk.begin(player, ChargeMath.shadowWalkTicks(dash.charge), dash.origin);
        }
    }

    // ------------------------------------------------------------------------------------------ cleanup

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ACTIVE.remove(player.getUUID());
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
    }
}
