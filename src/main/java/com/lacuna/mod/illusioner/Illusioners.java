package com.lacuna.mod.illusioner;

import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Illusioner, vanilla's unused illager, brought into the game:
 * <ul>
 *   <li>raids: later waves on normal and hard ({@link IllusionerRaids}, an extra Raid.RaiderType);</li>
 *   <li>rare night spawns in dark forests (biome modifier, data/lacuna/neoforge/biome_modifier/spawn_illusioner), on
 *       the ground under the usual monster rules ({@link #checkSpawnRules});</li>
 *   <li>woodland mansions: {@link #MANSION_CHANCE} of the evokers a mansion places are illusioners instead;</li>
 *   <li>its special ability, duplication ({@link DuplicationSpellGoal}, replacing the vanilla mirror spell; the vanilla
 *       blindness spell and bow stay): {@link #DOUBLES_MIN}-{@link #DOUBLES_MAX} identical duplicates
 *       ({@link IllusionerDouble}) step out of a burst of its particles around it, and with {@link #SWAP_CHANCE} it
 *       slips into one of their places itself, so the one in the middle is not always the real one;</li>
 *   <li>its look (starcloth robe and pointed hood, assets/minecraft/textures/entity/illager/illusioner.png) and the
 *       Totem of Confusion it sometimes drops (global loot modifier) are data.</li>
 * </ul>
 * Tuning lives here (not in the shared Balance) so the feature stays self-contained.
 */
public final class Illusioners {
    /** SafeRun feature name. */
    public static final String FEATURE = "illusioner";

    // ------------------------------------------------------------------ the duplication spell
    /** Ticks with raised arms before the duplicates appear. */
    public static final int CAST_WARMUP = 20;
    /** Ticks the casting pose lasts in all. */
    public static final int CAST_TIME = 30;
    /** 20-25 s between casts. */
    public static final int CAST_INTERVAL_MIN = 400;
    public static final int CAST_INTERVAL_MAX = 500;
    public static final int DOUBLES_MIN = 3;
    public static final int DOUBLES_MAX = 4;
    public static final double MIN_RADIUS = 2.0;
    public static final double MAX_RADIUS = 4.0;
    /** Duplicates never stand closer than this to each other (or to the real one). */
    public static final double MIN_SPACING = 1.5;
    /** Chance that the real one swaps places with one of its duplicates. */
    public static final float SWAP_CHANCE = 0.5F;

    // ------------------------------------------------------------------ the duplicates
    /** About 15 s. */
    public static final int DOUBLE_LIFETIME = 300;
    /** A duplicate this far from the real one (or out of its level) fades. */
    public static final double LEASH = 32.0;

    // ------------------------------------------------------------------ woodland mansions
    public static final float MANSION_CHANCE = 0.3F;

    /** The vanilla mirror spell's colour (the spell this one replaces), for the swirls and puffs. */
    private static final float[] SPELL_COLOR = {0.3F, 0.3F, 0.8F};

    private Illusioners() {}

    /** Common wiring (called from the mod constructor). */
    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(Illusioners::onAttributes);
        modBus.addListener(Illusioners::onSpawnPlacements);
        forgeBus.addListener(Illusioners::onJoin);
        forgeBus.addListener(Illusioners::onFinalizeSpawn);
        forgeBus.addListener(Illusioners::onProjectileImpact);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ILLUSIONER_DOUBLE.get(), IllusionerDouble.createAttributes().build());
    }

    /** On the ground (vanilla registers no restrictions, as nothing spawned it), monster rules, and at night. */
    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(EntityType.ILLUSIONER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Illusioners::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    static boolean checkSpawnRules(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos,
                                   RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)
                && (spawnType != MobSpawnType.NATURAL || level.getLevel().isNight());
    }

    /** Every illusioner that joins a server level casts duplication instead of the vanilla mirror spell. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Illusioner illusioner) {
            SafeRun.run(FEATURE, () -> install(illusioner));
        }
    }

    static void install(Illusioner illusioner) {
        boolean installed = illusioner.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof DuplicationSpellGoal);
        if (!installed) {
            illusioner.goalSelector.removeAllGoals(g -> g instanceof Illusioner.IllusionerMirrorSpellGoal);
            illusioner.goalSelector.addGoal(4, new DuplicationSpellGoal(illusioner));
        }
    }

    /** Woodland mansions: some of the evokers they place (structure spawns) are illusioners instead. */
    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.STRUCTURE || event.getEntity().getType() != EntityType.EVOKER
                || !(event.getEntity() instanceof Evoker evoker) || event.isSpawnCancelled() || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        ServerLevelAccessor level = event.getLevel();
        if (level.getRandom().nextFloat() >= MANSION_CHANCE) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            Illusioner illusioner = EntityType.ILLUSIONER.create(level.getLevel());
            if (illusioner == null) {
                return;
            }
            illusioner.setPersistenceRequired();
            illusioner.moveTo(evoker.getX(), evoker.getY(), evoker.getZ(), evoker.getYRot(), evoker.getXRot());
            EventHooks.finalizeMobSpawn(illusioner, level, event.getDifficulty(), MobSpawnType.STRUCTURE, null);
            event.setSpawnCancelled(true); // the evoker never enters the world...
            level.addFreshEntityWithPassengers(illusioner); // ...an illusioner stands in its place
        });
    }

    /** The real one's arrows pass through its duplicates, and theirs through it and each other. */
    private static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && (hit.getEntity() instanceof IllusionerDouble || hit.getEntity() instanceof Illusioner)
                && sameCircle(event.getProjectile().getOwner(), hit.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ duplication

    /** The duplication itself (server, from the spell): duplicates around {@code caster}, perhaps a swap, a burst at each. */
    static void duplicate(Illusioner caster) {
        if (!(caster.level() instanceof ServerLevel level) || !caster.isAlive()) {
            return;
        }
        RandomSource random = caster.getRandom();
        dismiss(level, caster);
        int count = DOUBLES_MIN + random.nextInt(DOUBLES_MAX - DOUBLES_MIN + 1);
        List<Vec3> spots = findSpots(level, caster, count, random);
        if (spots.isEmpty()) {
            return;
        }
        Vec3 origin = caster.position();
        if (!caster.isPassenger() && !caster.isVehicle() && random.nextFloat() < SWAP_CHANCE) {
            int pick = random.nextInt(spots.size());
            Vec3 to = spots.get(pick);
            caster.teleportTo(to.x, to.y, to.z);
            caster.getNavigation().stop();
            spots.set(pick, origin);
        }
        List<Vec3> bodies = new ArrayList<>(spots);
        bodies.add(caster.position());
        for (Vec3 p : spots) {
            IllusionerDouble copy = ModEntities.ILLUSIONER_DOUBLE.get().create(level);
            if (copy != null) {
                copy.setup(caster, p);
                level.addFreshEntity(copy);
            }
        }
        // identical bursts and sounds at every body, the real one's included: nothing tells it apart
        for (Vec3 p : bodies) {
            puff(level, p, 1.0F);
        }
    }

    /** Up to {@code count} standable spots on a ring round the caster, evenly spread, each in its line of sight. */
    private static List<Vec3> findSpots(ServerLevel level, Illusioner caster, int count, RandomSource random) {
        List<Vec3> out = new ArrayList<>();
        Vec3 origin = caster.position();
        Vec3 chest = origin.add(0.0, 1.2, 0.0);
        float base = random.nextFloat() * 360.0F;
        for (int k = 0; k < count; k++) {
            Vec3 found = null;
            for (int attempt = 0; attempt < 8 && found == null; attempt++) {
                double angle = Math.toRadians(base + k * (360.0 / count) + (attempt == 0 ? 0.0 : (random.nextDouble() - 0.5) * 50.0));
                double r = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
                found = spotAt(level, caster, chest, origin.x + Math.cos(angle) * r, origin.y, origin.z + Math.sin(angle) * r, out);
            }
            if (found != null) {
                out.add(found);
            }
        }
        return out;
    }

    @Nullable
    private static Vec3 spotAt(ServerLevel level, Illusioner caster, Vec3 chest, double x, double y, double z, List<Vec3> taken) {
        int baseY = Mth.floor(y + 0.01);
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            BlockPos feet = BlockPos.containing(x, baseY + dy, z);
            if (!SafeSpot.isSafe(level, feet)) {
                continue;
            }
            Vec3 pos = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            if (pos.distanceTo(caster.position()) < MIN_SPACING || taken.stream().anyMatch(t -> t.distanceTo(pos) < MIN_SPACING)) {
                continue;
            }
            if (level.clip(new ClipContext(chest, pos.add(0.0, 1.2, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }
            return pos;
        }
        return null;
    }

    /** Removes {@code caster}'s standing duplicates (a fresh cast replaces the old crowd). */
    private static void dismiss(ServerLevel level, Illusioner caster) {
        UUID id = caster.getUUID();
        for (IllusionerDouble copy : level.getEntitiesOfClass(IllusionerDouble.class, caster.getBoundingBox().inflate(LEASH),
                c -> id.equals(c.casterId()))) {
            copy.vanish(false);
        }
    }

    /** The illusioner's particles (a cloud burst and the mirror spell's blue swirls) and its mirror sound. */
    static void puff(ServerLevel level, Vec3 p, float strength) {
        level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1.0, p.z, Math.round(14 * strength), 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(spellParticle(), p.x, p.y + 1.0, p.z, Math.round(16 * strength), 0.35, 0.6, 0.35, 0.0);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.5F + 0.4F * strength,
                0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    static ColorParticleOption spellParticle() {
        return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, SPELL_COLOR[0], SPELL_COLOR[1], SPELL_COLOR[2]);
    }

    /** The illusioner whose circle {@code entity} belongs to: an illusioner itself, or the caster of a duplicate. */
    @Nullable
    private static UUID circle(@Nullable Entity entity) {
        if (entity instanceof IllusionerDouble copy) {
            return copy.casterId();
        }
        return entity instanceof Illusioner ? entity.getUUID() : null;
    }

    /** True when both are the same illusioner or its duplicates. */
    static boolean sameCircle(@Nullable Entity a, @Nullable Entity b) {
        UUID ca = circle(a);
        return ca != null && ca.equals(circle(b));
    }
}
