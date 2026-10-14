package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Tidecaller Sickle currents (server): the travelling Tidal Wave and the Maelstrom whirlpool with its closing geyser.
 * Both are short-lived, never saved, ticked once per server tick and cleared when the server stops. The visuals are
 * drawn by the clients (client/gear/TidecallerFx) from a single {@link FxPayload} each; the wave's terrain path is
 * computed with the same {@link #path} helper on both sides so the drawn wave rolls where the real one does.
 */
public final class TideCurrents {
    public static final String WAVE_FEATURE = "tidecaller_wave";
    public static final String MAELSTROM_FEATURE = "tidecaller_maelstrom";
    public static final String WAVE_FX = "tide_wave";
    public static final String MAELSTROM_FX = "tide_maelstrom";
    public static final String GEYSER_FX = "tide_geyser";
    /** Spacing of the precomputed wave path samples, in blocks. */
    public static final double PATH_STEP = 0.5;
    /** The wave rises this far in front of the caster. */
    public static final double WAVE_START = 1.0;
    /** How high the wave can climb in one step (a single block), and how far it can pour down. */
    public static final double WAVE_CLIMB = 1.2;
    public static final int WAVE_DROP = 6;
    /** Geyser column height drawn by the clients (the launch itself is {@link Balance#SICKLE_GEYSER_LAUNCH}). */
    public static final float GEYSER_HEIGHT = 8.0F;
    private static final int MAX_ACTIVE = 48;

    private static final List<Wave> WAVES = new ArrayList<>();
    private static final List<Maelstrom> MAELSTROMS = new ArrayList<>();

    private TideCurrents() {}

    // ------------------------------------------------------------------ terrain (shared with the client)

    /**
     * Height of the surface the water rides on at (x, z), starting from height {@code y}: the first collision top or
     * top-of-fluid surface found scanning down from {@code y + climb} (submerged fluid is passed through). Returns NaN for a wall too tall to climb and
     * {@code y - 1} when there is nothing within {@code drop} blocks below (the water pours down).
     */
    public static double surface(BlockGetter level, double x, double z, double y, double climb, int drop) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y + climb), Mth.floor(z));
        int bottom = Mth.floor(y) - drop;
        while (m.getY() >= bottom) {
            BlockState state = level.getBlockState(m);
            double top = Double.NaN;
            VoxelShape shape = state.getCollisionShape(level, m);
            if (!shape.isEmpty()) {
                top = m.getY() + shape.max(Direction.Axis.Y);
            }
            FluidState fluid = state.getFluidState();
            // only the top layer of a body of fluid is a surface: under water the wave rolls along the bed
            if (!fluid.isEmpty() && !fluid.getType().isSame(level.getFluidState(m.above()).getType())) {
                double surface = m.getY() + fluid.getHeight(level, m);
                top = Double.isNaN(top) ? surface : Math.max(top, surface);
            }
            if (!Double.isNaN(top)) {
                return top > y + climb + 1.0E-3 ? Double.NaN : top;
            }
            m.move(Direction.DOWN);
        }
        return y - 1.0;
    }

    /**
     * Surface heights every {@link #PATH_STEP} blocks from {@code origin} along {@code dir} (horizontal unit vector)
     * for {@code length} blocks. With {@code stopAtWall} the path ends before the first wall (the wave breaks there);
     * otherwise a wall keeps the previous height.
     */
    public static double[] path(BlockGetter level, Vec3 origin, Vec3 dir, double length, boolean stopAtWall) {
        int samples = Math.max(1, Mth.ceil(length / PATH_STEP));
        double[] ys = new double[samples + 1];
        double y = origin.y;
        ys[0] = y;
        for (int i = 1; i <= samples; i++) {
            double d = i * PATH_STEP;
            double s = surface(level, origin.x + dir.x * d, origin.z + dir.z * d, y, WAVE_CLIMB, WAVE_DROP);
            if (Double.isNaN(s)) {
                if (stopAtWall) {
                    return Arrays.copyOf(ys, i);
                }
                s = y;
            }
            y = s;
            ys[i] = y;
        }
        return ys;
    }

    public static double pathLength(double[] path) {
        return (path.length - 1) * PATH_STEP;
    }

    /** Linear interpolation of a {@link #path} at distance {@code d} (clamped to its ends). */
    public static double heightAt(double[] path, double d) {
        double f = Mth.clamp(d / PATH_STEP, 0.0, path.length - 1);
        int i = (int) f;
        if (i >= path.length - 1) {
            return path[path.length - 1];
        }
        return Mth.lerp(f - i, path[i], path[i + 1]);
    }

    /** Horizontal facing of {@code player} as a unit vector. */
    public static Vec3 facing(Player player) {
        return Vec3.directionFromRotation(0.0F, player.getYRot());
    }

    // ------------------------------------------------------------------ casting

    /** Tidal Wave: a curling wall of water rolls forward from the caster's feet. */
    public static void startWave(ServerLevel level, Player player) {
        Vec3 dir = facing(player);
        Vec3 feet = player.position();
        double y0 = surface(level, feet.x, feet.z, feet.y, 0.6, 3);
        Vec3 origin = new Vec3(feet.x, Double.isNaN(y0) ? feet.y : y0, feet.z);
        double[] path = path(level, origin, dir, Balance.SICKLE_WAVE_RANGE, true);
        double length = Math.max(WAVE_START + 0.5, pathLength(path));
        if (WAVES.size() >= MAX_ACTIVE) {
            WAVES.remove(0);
        }
        WAVES.add(new Wave(level.dimension(), player.getUUID(), origin, dir, path, length));
        LacunaFx.send(level, origin, FxPayload.line(WAVE_FX, origin, origin.add(dir)).withEntity(player.getId())
                .withParams((float) length, (float) Balance.SICKLE_WAVE_SPEED, (float) Balance.SICKLE_WAVE_HALF_WIDTH));
        // the water gathers and surges: a heavy splash, the riptide's rush and a muffled underwater swell
        Sfx.at(level, origin, SoundEvents.GENERIC_SPLASH, 1.2F, 0.7F);
        Sfx.at(level, origin, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.9F, 0.85F + level.random.nextFloat() * 0.15F);
        Sfx.at(level, origin, SoundEvents.TRIDENT_RIPTIDE_2, 1.0F, 0.8F);
        Sfx.at(level, origin, SoundEvents.AMBIENT_UNDERWATER_ENTER, 0.8F, 1.35F);
    }

    /** Maelstrom: a whirlpool opens where the caster looks (or a few blocks ahead). */
    public static void startMaelstrom(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(Balance.SICKLE_MAELSTROM_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        Vec3 at;
        if (hit.getType() == HitResult.Type.BLOCK) {
            at = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3));
        } else {
            at = player.position().add(facing(player).scale(Balance.SICKLE_MAELSTROM_FALLBACK)).add(0, 1.0, 0);
        }
        double y = surface(level, at.x, at.z, at.y, 0.5, 12);
        Vec3 center = new Vec3(at.x, Double.isNaN(y) ? at.y : y, at.z);
        if (MAELSTROMS.size() >= MAX_ACTIVE) {
            MAELSTROMS.remove(0);
        }
        MAELSTROMS.add(new Maelstrom(level.dimension(), player.getUUID(), center));
        LacunaFx.send(level, center, FxPayload.at(MAELSTROM_FX, center).withEntity(player.getId())
                .withParams((float) Balance.SICKLE_MAELSTROM_RADIUS, Balance.SICKLE_MAELSTROM_TICKS, 0.0F));
        Sfx.at(level, center, SoundEvents.CONDUIT_ACTIVATE, 1.4F, 0.8F);
        Sfx.at(level, center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 2.0F, 0.6F);
        Sfx.at(level, center, SoundEvents.AMBIENT_UNDERWATER_ENTER, 1.0F, 0.7F);
        Sfx.at(level, center, SoundEvents.ELDER_GUARDIAN_CURSE, 0.12F, 1.4F);
        Sfx.at(level, player.position(), SoundEvents.TRIDENT_RIPTIDE_3, 0.7F, 0.6F);
    }

    // ------------------------------------------------------------------ ticking

    public static void onServerTick(ServerTickEvent.Post event) {
        if (WAVES.isEmpty() && MAELSTROMS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!WAVES.isEmpty()) {
            SafeRun.run(WAVE_FEATURE, () -> {
                Iterator<Wave> it = WAVES.iterator();
                while (it.hasNext()) {
                    Wave wave = it.next();
                    ServerLevel level = server.getLevel(wave.dimension);
                    if (level == null || !wave.tick(level)) {
                        it.remove();
                    }
                }
            }, WAVES::clear);
        }
        if (!MAELSTROMS.isEmpty()) {
            SafeRun.run(MAELSTROM_FEATURE, () -> {
                Iterator<Maelstrom> it = MAELSTROMS.iterator();
                while (it.hasNext()) {
                    Maelstrom m = it.next();
                    ServerLevel level = server.getLevel(m.dimension);
                    if (level == null || !m.tick(level)) {
                        it.remove();
                    }
                }
            }, MAELSTROMS::clear);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        WAVES.clear();
        MAELSTROMS.clear();
    }

    // ------------------------------------------------------------------ shared rules

    /** Entities the currents never hurt or move: the caster, its allies and pets, villagers, golems, players without PvP. */
    static boolean spared(@Nullable Player owner, LivingEntity e) {
        if (!e.isAlive() || e.isSpectator() || e == owner || e instanceof ArmorStand) {
            return true;
        }
        if (owner != null && (e == owner.getVehicle() || owner.isAlliedTo(e))) {
            return true;
        }
        if (e instanceof AbstractVillager || e instanceof SnowGolem || (e instanceof IronGolem golem && golem.isPlayerCreated())) {
            return true;
        }
        if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return true;
        }
        if (e instanceof Player other) {
            return owner == null || other.isCreative() || !owner.canHarmPlayer(other) || owner.getServer() == null
                    || !owner.getServer().isPvpAllowed();
        }
        return false;
    }

    static boolean isBoss(LivingEntity e) {
        return e.getType().is(ModTags.Entities.CANNOT_BE_BANISHED);
    }

    /** 1 for ordinary mobs, less for knockback-resistant ones (an iron golem barely moves). */
    private static double heft(LivingEntity e) {
        return 1.0 - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0) * 0.75;
    }

    /** Applies a server-side velocity change so that every client sees it (players need an explicit motion packet). */
    private static void push(LivingEntity e, Vec3 velocity) {
        e.setDeltaMovement(velocity);
        e.hasImpulse = true;
        if (e instanceof ServerPlayer) {
            e.hurtMarked = true;
        }
    }

    @Nullable
    private static Player owner(ServerLevel level, UUID id) {
        return level.getPlayerByUUID(id);
    }

    // ------------------------------------------------------------------ the tidal wave

    private static final class Wave {
        final ResourceKey<Level> dimension;
        final UUID owner;
        final Vec3 origin;
        final Vec3 dir;
        final Vec3 side;
        final double[] path;
        final double length;
        final Set<Integer> hit = new HashSet<>();
        final Set<Integer> carried = new HashSet<>();
        final Set<Long> touched = new HashSet<>();
        double front = WAVE_START;
        boolean charged;
        int age;

        Wave(ResourceKey<Level> dimension, UUID owner, Vec3 origin, Vec3 dir, double[] path, double length) {
            this.dimension = dimension;
            this.owner = owner;
            this.origin = origin;
            this.dir = dir;
            this.side = new Vec3(-dir.z, 0.0, dir.x);
            this.path = path;
            this.length = length;
        }

        Vec3 at(double d, double lateral) {
            return new Vec3(origin.x + dir.x * d + side.x * lateral, heightAt(path, d), origin.z + dir.z * d + side.z * lateral);
        }

        boolean tick(ServerLevel level) {
            double from = front;
            double to = Math.min(length, front + Balance.SICKLE_WAVE_SPEED);
            front = to;
            age++;
            double y = Math.max(heightAt(path, from), heightAt(path, to));
            Player player = owner(level, owner);
            sweep(level, player, from, to, y);
            douse(level, player, from, to);
            if (age % 4 == 1) {
                // the roar follows the front
                Sfx.at(level, at(to, 0.0), SoundEvents.GENERIC_SPLASH, 0.8F, 0.85F + level.random.nextFloat() * 0.35F);
            }
            if (to >= length - 1.0E-6) {
                crash(level, player, y);
                return false;
            }
            return true;
        }

        private void sweep(ServerLevel level, @Nullable Player player, double from, double to, double y) {
            double hw = Balance.SICKLE_WAVE_HALF_WIDTH;
            Vec3 a = at(from - 2.5, 0.0);
            Vec3 b = at(to + 1.0, 0.0);
            AABB box = new AABB(a.x, y - 1.0, a.z, b.x, y + Balance.SICKLE_WAVE_HEIGHT + 0.5, b.z).inflate(hw + 1.0, 0.0, hw + 1.0);
            DamageSource source = ModDamageTypes.source(level, ModDamageTypes.TIDAL_WAVE, player);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
                double rx = e.getX() - origin.x;
                double rz = e.getZ() - origin.z;
                double along = rx * dir.x + rz * dir.z;
                double lateral = rx * side.x + rz * side.z;
                double reach = e.getBbWidth() * 0.5;
                // nothing behind the caster: the water rises a block in front of them
                if (Math.abs(lateral) > hw + reach + 0.3 || along > to + 0.6 + reach || along < Math.max(from - 2.5, WAVE_START - 0.6)
                        || e.getY() > y + Balance.SICKLE_WAVE_HEIGHT || e.getY() + e.getBbHeight() < y - 0.6) {
                    continue;
                }
                if (e.isOnFire()) {
                    e.extinguishFire(); // the wave puts out anyone it washes over, friends included
                }
                if (spared(player, e)) {
                    continue;
                }
                if (hit.add(e.getId())) {
                    if (player != null) {
                        e.setLastHurtByPlayer(player);
                    }
                    e.hurt(source, Balance.SICKLE_WAVE_DAMAGE);
                    Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                    level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 16, 0.35, 0.4, 0.35, 0.25);
                    level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.05);
                    Sfx.at(level, c, SoundEvents.PLAYER_SPLASH, 0.6F, 1.1F + level.random.nextFloat() * 0.3F);
                    if (!charged && player != null) {
                        charged = true;
                        TidecallerSickleItem.gainTide(player);
                    }
                }
                if (isBoss(e) || !e.isAlive()) {
                    continue;
                }
                // ride the front: keep just ahead of the crest, lifted onto the water
                double k = heft(e);
                double want = (to + Balance.SICKLE_WAVE_SPEED + 0.35) - along;
                double forward = Mth.clamp(want, Balance.SICKLE_WAVE_SPEED * 0.6, Balance.SICKLE_WAVE_SPEED * 1.9) * k;
                double lift = Mth.clamp((y + 0.9 - e.getY()) * 0.4, -0.15, 0.42);
                if (e.onGround()) {
                    lift = Math.max(lift, 0.22);
                }
                Vec3 v = e.getDeltaMovement();
                double sideways = (v.x * side.x + v.z * side.z) * 0.5;
                push(e, dir.scale(forward).add(side.scale(sideways)).add(0.0, lift * k, 0.0));
                e.fallDistance = 0.0F;
                carried.add(e.getId());
            }
        }

        /** Puts out fire, campfires and candles and quenches lava along the strip the front crossed this tick. */
        private void douse(ServerLevel level, @Nullable Player player, double from, double to) {
            boolean mayEdit = player != null && player.mayBuild();
            int fizzles = 0;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            double hw = Balance.SICKLE_WAVE_HALF_WIDTH;
            for (double d = from; d <= to + 1.0E-6; d += 0.5) {
                int base = Mth.floor(heightAt(path, d));
                for (double l = -hw; l <= hw + 1.0E-6; l += 0.5) {
                    double x = origin.x + dir.x * d + side.x * l;
                    double z = origin.z + dir.z * d + side.z * l;
                    for (int dy = -1; dy <= 2; dy++) {
                        m.set(Mth.floor(x), base + dy, Mth.floor(z));
                        if (!touched.add(m.asLong())) {
                            continue;
                        }
                        BlockState state = level.getBlockState(m);
                        if (state.isAir()) {
                            continue;
                        }
                        if (state.is(BlockTags.FIRE)) {
                            level.removeBlock(m, false);
                            if (fizzles++ < 3) {
                                level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, m, 0);
                            }
                        } else if (!mayEdit || !level.mayInteract(player, m)) {
                            continue;
                        } else if (CampfireBlock.isLitCampfire(state)) {
                            level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, m, 0);
                            CampfireBlock.dowse(player, level, m, state);
                            level.setBlockAndUpdate(m, state.setValue(CampfireBlock.LIT, false));
                        } else if (AbstractCandleBlock.isLit(state)) {
                            AbstractCandleBlock.extinguish(null, state, level, m);
                        } else if (state.is(Blocks.LAVA) && !level.getFluidState(m.above()).is(FluidTags.LAVA)) {
                            // the top of the lava meets the water: sources harden into obsidian, flows into cobblestone
                            BlockState into = state.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState()
                                    : Blocks.COBBLESTONE.defaultBlockState();
                            BlockPos pos = m.immutable();
                            level.setBlockAndUpdate(pos, EventHooks.fireFluidPlaceBlockEvent(level, pos, pos, into));
                            if (fizzles++ < 4) {
                                level.levelEvent(LevelEvent.LAVA_FIZZ, pos, 0);
                            }
                        }
                    }
                }
            }
        }

        /** The wave breaks: whatever it carried is thrown on, with a final crash of water. */
        private void crash(ServerLevel level, @Nullable Player player, double y) {
            for (int id : carried) {
                if (level.getEntity(id) instanceof LivingEntity e && e.isAlive() && !spared(player, e) && !isBoss(e)) {
                    double k = heft(e);
                    push(e, dir.scale(0.85 * k).add(0.0, 0.32 * k, 0.0));
                }
            }
            Vec3 c = at(length, 0.0);
            Sfx.at(level, c, SoundEvents.GENERIC_SPLASH, 1.3F, 0.6F);
            Sfx.at(level, c, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.9F, 0.7F);
        }
    }

    // ------------------------------------------------------------------ the maelstrom

    private static final class Maelstrom {
        final ResourceKey<Level> dimension;
        final UUID owner;
        final Vec3 center;
        int age;

        Maelstrom(ResourceKey<Level> dimension, UUID owner, Vec3 center) {
            this.dimension = dimension;
            this.owner = owner;
            this.center = center;
        }

        private List<LivingEntity> caught(ServerLevel level, @Nullable Player player) {
            double r = Balance.SICKLE_MAELSTROM_RADIUS;
            AABB box = new AABB(center, center).inflate(r, 0.0, r).expandTowards(0.0, 6.0, 0.0).expandTowards(0.0, -2.0, 0.0);
            return level.getEntitiesOfClass(LivingEntity.class, box, e -> {
                if (spared(player, e) || isBoss(e)) {
                    return false;
                }
                double dx = e.getX() - center.x;
                double dz = e.getZ() - center.z;
                return dx * dx + dz * dz <= r * r;
            });
        }

        boolean tick(ServerLevel level) {
            age++;
            Player player = owner(level, owner);
            if (age % 20 == 1) {
                Sfx.at(level, center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 2.0F, 0.55F + level.random.nextFloat() * 0.3F);
                Sfx.at(level, center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.8F, 0.6F + level.random.nextFloat() * 0.2F);
            }
            if (age % 40 == 20) {
                Sfx.at(level, center, SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS, 1.2F, 0.7F);
            }
            if (age >= Balance.SICKLE_MAELSTROM_TICKS) {
                geyser(level, player);
                return false;
            }
            boolean drown = age % 20 == 10;
            double radius = Balance.SICKLE_MAELSTROM_RADIUS;
            int splashes = 0;
            for (LivingEntity e : caught(level, player)) {
                if (e.isOnFire()) {
                    e.extinguishFire();
                }
                double dx = center.x - e.getX();
                double dz = center.z - e.getZ();
                double r = Math.sqrt(dx * dx + dz * dz);
                double near = 1.0 - Mth.clamp(r / radius, 0.0, 1.0);
                Vec3 in = r > 1.0E-3 ? new Vec3(dx / r, 0.0, dz / r) : Vec3.ZERO;
                // counter-clockwise from above, the same way the drawn whirlpool turns
                Vec3 around = new Vec3(in.z, 0.0, -in.x);
                double k = heft(e);
                double inward = (0.08 + 0.13 * near) * Math.min(1.0, r);
                double spin = 0.2 + 0.22 * near;
                double lift = Mth.clamp((center.y + 0.5 + 1.1 * near - e.getY()) * 0.25, -0.2, 0.3);
                push(e, in.scale(inward * k).add(around.scale(spin * k)).add(0.0, lift, 0.0));
                e.fallDistance = 0.0F;
                if (drown) {
                    if (player != null) {
                        e.setLastHurtByPlayer(player);
                    }
                    if (e.hurt(level.damageSources().drown(), Balance.SICKLE_MAELSTROM_DAMAGE) && splashes++ < 8) {
                        Vec3 c = e.position().add(0, e.getBbHeight() * 0.6, 0);
                        level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.04);
                        level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 10, 0.3, 0.2, 0.3, 0.1);
                    }
                }
            }
            return true;
        }

        /** The whirlpool collapses into a geyser that blasts everything still inside straight up. */
        private void geyser(ServerLevel level, @Nullable Player player) {
            for (LivingEntity e : caught(level, player)) {
                double k = heft(e);
                Vec3 v = e.getDeltaMovement();
                double up = (Balance.SICKLE_GEYSER_LAUNCH + level.random.nextDouble() * 0.15) * (0.5 + 0.5 * k);
                push(e, new Vec3(v.x * 0.2, up, v.z * 0.2));
                if (e.isOnFire()) {
                    e.extinguishFire();
                }
            }
            LacunaFx.send(level, center, FxPayload.at(GEYSER_FX, center)
                    .withParams((float) Balance.SICKLE_MAELSTROM_RADIUS, GEYSER_HEIGHT, 0.0F));
            Sfx.at(level, center, SoundEvents.GENERIC_EXPLODE, 0.45F, 0.55F);
            Sfx.at(level, center, SoundEvents.GENERIC_SPLASH, 2.0F, 0.45F);
            Sfx.at(level, center, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.6F, 0.55F);
            Sfx.at(level, center, SoundEvents.TRIDENT_RIPTIDE_3, 1.4F, 0.6F);
            Sfx.at(level, center, SoundEvents.TRIDENT_THUNDER, 0.35F, 1.5F);
            Sfx.at(level, center, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 2.0F, 0.8F);
        }
    }
}
