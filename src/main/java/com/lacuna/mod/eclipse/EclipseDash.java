package com.lacuna.mod.eclipse;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.logic.ChargeMath;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Teleporter;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Eclipser dash: consumes all charge and carries the player along the look vector over a few ticks.
 * <p>
 * The path is computed once on the server and stops before solid blocks (no phasing). The dashing client receives
 * it through the {@code eclipse_dash} effect payload and drives its own velocity along it each tick (smooth, no
 * teleport); the server follows the player's real progress, cuts every living entity the dash passes (once each),
 * and when the player arrives (or after a short grace period, teleporting a client that never moved) the dash ends
 * and only then Shadow Walk starts.
 */
public final class EclipseDash {
    public static final String FEATURE = "eclipse_dash";
    private static final double STEP = 0.25;
    /** Extra ticks the server waits for a lagging client to reach the end before finishing the dash itself. */
    private static final int GRACE = 10;
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
        final IntSet hit = new IntOpenHashSet();
        double progress;
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

    /** Cuts every living entity within the dash radius of the path between the last progress and {@code upTo}. */
    private static void sweep(ServerPlayer player, Dash dash, double upTo) {
        double from = dash.progress;
        double to = Math.max(from, upTo);
        dash.progress = to;
        ServerLevel level = player.serverLevel();
        double half = player.getBbHeight() / 2.0;
        Vec3 a = dash.at(from).add(0, half, 0);
        Vec3 b = dash.at(to).add(0, half, 0);
        AABB area = new AABB(a, b).inflate(Balance.ECLIPSE_DASH_RADIUS + 1.5);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !e.isSpectator() && !dash.hit.contains(e.getId()))) {
            Vec3 c = target.getBoundingBox().getCenter();
            double d = ChargeMath.segmentDistance(c.x, c.y, c.z, a.x, a.y, a.z, b.x, b.y, b.z);
            if (d > Balance.ECLIPSE_DASH_RADIUS + target.getBbWidth() / 2.0) {
                continue;
            }
            dash.hit.add(target.getId());
            target.hurt(ModDamageTypes.source(level, ModDamageTypes.ECLIPSE, player), dash.damage);
            LacunaFx.send(level, c, new FxPayload("eclipse_hit", target.getId(), c, dash.dir,
                    target.getBbHeight(), target.getBbWidth(), dash.hit.size(), 0xFFFFFFFF));
            float pitch = 0.85F + level.random.nextFloat() * 0.3F;
            level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F * pitch);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 0.6F * pitch);
        }
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
