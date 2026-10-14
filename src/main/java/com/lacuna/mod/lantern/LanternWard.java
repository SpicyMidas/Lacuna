package com.lacuna.mod.lantern;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * The Null Lantern's ward, shared by the held item and the placed block. A player is <em>protected</em> while holding
 * a lit lantern in either hand or while standing within {@link Balance#LANTERN_PLACED_RADIUS} blocks of a lit placed
 * one: hostile and neutral threat mobs (never bosses or golems) cannot target or hurt them, mobs already hunting them
 * forget them with a puff of shadow and a whisper, and natural monster spawns within
 * {@link Balance#LANTERN_SPAWN_RADIUS} blocks of a holder or of a lit placed lantern fail.
 * <p>
 * Lit placed lanterns are tracked in a volatile per-dimension registry maintained by their block entities (added on
 * load / when lit, removed when removed, unloaded or extinguished; cleared on server stop), so the target, damage and
 * spawn events check zones without touching the world.
 */
public final class LanternWard {
    public static final String FEATURE = "null_lantern";

    private static final Map<ResourceKey<Level>, Set<BlockPos>> PLACED = new ConcurrentHashMap<>();

    private LanternWard() {}

    public static boolean active() {
        return LacunaConfig.get(LacunaConfig.LANTERN_ENABLED) && !SafeRun.isDisabled(FEATURE);
    }

    // ------------------------------------------------------------------ placed lantern registry

    public static void register(Level level, BlockPos pos) {
        if (level != null && !level.isClientSide()) {
            PLACED.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
        }
    }

    public static void unregister(Level level, BlockPos pos) {
        if (level != null && !level.isClientSide()) {
            Set<BlockPos> set = PLACED.get(level.dimension());
            if (set != null) {
                set.remove(pos);
            }
        }
    }

    public static void clearVolatile() {
        PLACED.clear();
    }

    /** True when a lit placed lantern's centre lies within {@code radius} blocks of the given point. */
    public static boolean nearPlaced(Level level, double x, double y, double z, double radius) {
        Set<BlockPos> set = PLACED.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return false;
        }
        double r2 = radius * radius;
        for (BlockPos pos : set) {
            double dx = pos.getX() + 0.5 - x;
            double dy = pos.getY() + 0.5 - y;
            double dz = pos.getZ() + 0.5 - z;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    public static boolean inZone(Player player) {
        return nearPlaced(player.level(), player.getX(), player.getY(0.5), player.getZ(), Balance.LANTERN_PLACED_RADIUS);
    }

    /** Held lit lantern or inside a lit placed lantern's zone. */
    public static boolean protects(Player player) {
        return !player.isSpectator() && (NullLanternItem.holdsLit(player) || inZone(player));
    }

    // ------------------------------------------------------------------ mobs

    /** Hostile mobs and neutral mobs that can turn on a player (golems keep guarding villages); never bosses. */
    static boolean isThreat(Mob mob) {
        if (mob.getType().is(ModTags.Entities.CANNOT_BE_BANISHED) || mob instanceof AbstractGolem) {
            return false;
        }
        return mob instanceof Enemy || mob instanceof NeutralMob;
    }

    /** True when {@code mob} is a threat currently hunting {@code player} in any way. */
    static boolean affects(Mob mob, Player player) {
        if (!isThreat(mob)) {
            return false;
        }
        if (mob.getTarget() == player) {
            return true;
        }
        if (mob instanceof NeutralMob neutral && player.getUUID().equals(neutral.getPersistentAngerTarget())) {
            return true;
        }
        return brainTargets(mob.getBrain(), player) || brainAngryAt(mob.getBrain(), player);
    }

    /*
     * Brain memories are only registered for brain-driven mobs (piglins, hoglins, wardens...). Goal-based mobs
     * (zombies, skeletons, spiders...) do not register ATTACK_TARGET or ANGRY_AT and Brain#getMemory throws
     * "Unregistered memory fetched" for them, so every read is guarded by hasMemoryValue (false when unregistered).
     */
    private static boolean brainTargets(Brain<?> brain, Player player) {
        return brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).filter(t -> t == player).isPresent();
    }

    private static boolean brainAngryAt(Brain<?> brain, Player player) {
        return brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)
                && brain.getMemory(MemoryModuleType.ANGRY_AT).filter(id -> id.equals(player.getUUID())).isPresent();
    }

    /** Drops every way {@code mob} could be hunting {@code player}, with a cold whisper and a puff of shadow. */
    static void forget(Mob mob, Player player) {
        if (mob.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.SHADOW_MOTE.get(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.7,
                    mob.getZ(), 8, 0.3, 0.3, 0.3, 0.01);
            Sfx.varied(mob, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.7F, 0.1F);
            Sfx.at(mob, SoundEvents.SCULK_CLICKING_STOP, 0.6F, 0.6F);
        }
        if (mob.getTarget() == player) {
            mob.setTarget(null);
        }
        Brain<?> brain = mob.getBrain();
        if (brainTargets(brain, player)) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        if (brainAngryAt(brain, player)) {
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }
        if (mob instanceof NeutralMob neutral && player.getUUID().equals(neutral.getPersistentAngerTarget())) {
            neutral.stopBeingAngry();
        }
        if (mob instanceof Creeper creeper && creeper.getSwellDir() > 0) {
            creeper.setSwellDir(-1);
        }
        if (mob.getLastHurtByMob() == player) {
            mob.setLastHurtByMob(null); // HurtByTargetGoal would pick the player right back up
        }
        if (mob.isUsingItem()) {
            mob.stopUsingItem(); // a skeleton's drawn bow or a crossbow being loaded at the player
        }
        mob.setAggressive(false);
    }

    /** Makes every threat hunting {@code player} within {@code radius} blocks of {@code centre} give up. */
    static void sweep(ServerLevel level, Player player, Vec3 centre, double radius) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(radius),
                m -> affects(m, player))) {
            forget(mob, player);
        }
    }

    static double sweepRadius() {
        return Math.max(Balance.LANTERN_SWEEP_RADIUS, LacunaConfig.get(LacunaConfig.LANTERN_RADIUS));
    }

    /** A lit placed lantern at {@code pos}: every player inside its zone is shed by the mobs hunting them. */
    static void guardZone(ServerLevel level, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        double r2 = (double) Balance.LANTERN_PLACED_RADIUS * Balance.LANTERN_PLACED_RADIUS;
        for (Player player : level.players()) {
            if (!player.isSpectator() && player.position().add(0, player.getBbHeight() * 0.5, 0).distanceToSqr(centre) <= r2) {
                sweep(level, player, player.position(), sweepRadius());
            }
        }
    }

    // ------------------------------------------------------------------ charge, fuel and cues

    /** Charge one {@code fuel} item restores (Ender Pearl 50 %, Echo Shard 100 % of {@code max}); 0 if it is no fuel. */
    public static int fuelValue(ItemStack fuel, int max) {
        if (fuel.is(Items.ENDER_PEARL)) {
            return Math.max(1, max * Balance.LANTERN_PEARL_RECHARGE_PERCENT / 100);
        }
        if (fuel.is(Items.ECHO_SHARD)) {
            return Math.max(1, max * Balance.LANTERN_ECHO_RECHARGE_PERCENT / 100);
        }
        return 0;
    }

    public static int percent(int charge, int max) {
        if (max <= 0 || charge <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(charge * 100.0F / max));
    }

    /** The lantern runs dry: a hiss, a low soul sigh and a wisp of smoke. */
    public static void fizzle(Level level, Vec3 pos) {
        if (level instanceof ServerLevel server) {
            play(server, pos, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.1F);
            play(server, pos, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.45F);
            server.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 8, 0.15, 0.15, 0.15, 0.01);
            server.sendParticles(ModParticles.SHADOW_MOTE.get(), pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** Fuel accepted: the anchor-charge thrum, an amethyst chime and a swirl of purple. */
    public static void rechargeCue(Level level, Vec3 pos) {
        if (level instanceof ServerLevel server) {
            play(server, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 1.2F);
            play(server, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
            rechargeParticles(server, pos);
        }
    }

    /**
     * The same cue for a lantern fed inside an inventory. Called on both sides of the click: Player#playSound plays
     * locally on the client and for everyone else on the server, so each player hears it once (creative clicks too).
     */
    public static void rechargeCue(Player player) {
        player.playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 1.2F);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
        if (player.level() instanceof ServerLevel server) {
            rechargeParticles(server, player.position().add(0, player.getBbHeight() * 0.6, 0));
        }
    }

    private static void rechargeParticles(ServerLevel server, Vec3 pos) {
        server.sendParticles(ModParticles.UMBRAL_WISP.get(), pos.x, pos.y, pos.z, 10, 0.25, 0.25, 0.25, 0.02);
        server.sendParticles(ParticleTypes.WITCH, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.05);
    }

    private static void play(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // ------------------------------------------------------------------ events

    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player) || !(event.getEntity() instanceof Mob mob)
                || player.level().isClientSide() || !active()) {
            return;
        }
        if (SafeRun.call(FEATURE, () -> isThreat(mob) && protects(player), false)) {
            event.setCanceled(true);
        }
    }

    /** Contact, melee and projectile damage from threats is cancelled too (slimes, magma cubes, stray shots...). */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || !active()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof Mob mob && SafeRun.call(FEATURE, () -> isThreat(mob) && protects(player), false)) {
            event.setCanceled(true);
        }
    }

    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !active()) {
            return;
        }
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        boolean blocked = SafeRun.call(FEATURE, () -> {
            double r = Balance.LANTERN_SPAWN_RADIUS;
            for (Player player : event.getLevel().players()) {
                if (player.distanceToSqr(event.getX(), event.getY(), event.getZ()) <= r * r && NullLanternItem.holdsLit(player)) {
                    return true;
                }
            }
            return nearPlaced(event.getLevel().getLevel(), event.getX(), event.getY(), event.getZ(), r);
        }, false);
        if (blocked) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
