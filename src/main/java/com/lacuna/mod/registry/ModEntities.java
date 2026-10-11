package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.flute.EchoEntity;
import com.lacuna.mod.gear.PyrrhicBoltEntity;
import com.lacuna.mod.mob.ClockworkHound;
import com.lacuna.mod.mob.DecoyEntity;
import com.lacuna.mod.mob.Gloomcrawler;
import com.lacuna.mod.mob.LanternMoth;
import com.lacuna.mod.mob.ThrownDecoyEntity;
import com.lacuna.mod.structure.ReflectionEntity;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import com.lacuna.mod.pearl.ArrivalShardEntity;
import com.lacuna.mod.solar.SolarRayEntity;
import com.lacuna.mod.solar.SunsFuryEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Lacuna.MODID);

    // ------------------------------------------------------------------ M4
    public static final DeferredHolder<EntityType<?>, EntityType<SunsFuryEntity>> SUNS_FURY = ENTITIES.register("suns_fury",
            () -> EntityType.Builder.<SunsFuryEntity>of(SunsFuryEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(5).build("suns_fury"));
    public static final DeferredHolder<EntityType<?>, EntityType<SolarRayEntity>> SOLAR_RAY = ENTITIES.register("solar_ray",
            () -> EntityType.Builder.<SolarRayEntity>of(SolarRayEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(16).updateInterval(20).fireImmune().noSave().build("solar_ray"));

    // ------------------------------------------------------------------ M5
    public static final DeferredHolder<EntityType<?>, EntityType<ArrivalShardEntity>> ARRIVAL_SHARD = ENTITIES.register("arrival_shard",
            () -> EntityType.Builder.<ArrivalShardEntity>of(ArrivalShardEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("arrival_shard"));

    // ------------------------------------------------------------------ M6
    public static final DeferredHolder<EntityType<?>, EntityType<EchoEntity>> ECHO = ENTITIES.register("echo",
            () -> EntityType.Builder.<EchoEntity>of(EchoEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).noSave().build("echo"));

    // ------------------------------------------------------------------ M7
    public static final DeferredHolder<EntityType<?>, EntityType<PyrrhicBoltEntity>> PYRRHIC_BOLT = ENTITIES.register("pyrrhic_bolt",
            () -> EntityType.Builder.<PyrrhicBoltEntity>of(PyrrhicBoltEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(2).fireImmune().noSave().build("pyrrhic_bolt"));

    // ------------------------------------------------------------------ M9 mobs (vanilla models; sizes are vanilla x 0.8 where scaled)
    public static final DeferredHolder<EntityType<?>, EntityType<LanternMoth>> LANTERN_MOTH = ENTITIES.register("lantern_moth",
            () -> EntityType.Builder.<LanternMoth>of(LanternMoth::new, MobCategory.AMBIENT)
                    .sized(0.56F, 0.48F).eyeHeight(0.24F).clientTrackingRange(8).build("lantern_moth"));
    public static final DeferredHolder<EntityType<?>, EntityType<Gloomcrawler>> GLOOMCRAWLER = ENTITIES.register("gloomcrawler",
            () -> EntityType.Builder.<Gloomcrawler>of(Gloomcrawler::new, MobCategory.MONSTER)
                    .sized(1.12F, 0.72F).eyeHeight(0.52F).clientTrackingRange(8).build("gloomcrawler"));
    public static final DeferredHolder<EntityType<?>, EntityType<ClockworkHound>> CLOCKWORK_HOUND = ENTITIES.register("clockwork_hound",
            () -> EntityType.Builder.<ClockworkHound>of(ClockworkHound::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.85F).eyeHeight(0.68F).clientTrackingRange(10).build("clockwork_hound"));
    public static final DeferredHolder<EntityType<?>, EntityType<DecoyEntity>> DECOY = ENTITIES.register("clockwork_decoy",
            () -> EntityType.Builder.<DecoyEntity>of(DecoyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).noSave().build("clockwork_decoy"));
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownDecoyEntity>> THROWN_DECOY = ENTITIES.register("thrown_clockwork_decoy",
            () -> EntityType.Builder.<ThrownDecoyEntity>of(ThrownDecoyEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("thrown_clockwork_decoy"));

    // ------------------------------------------------------------------ M10 structures
    public static final DeferredHolder<EntityType<?>, EntityType<ReflectionEntity>> REFLECTION = ENTITIES.register("reflection",
            () -> EntityType.Builder.<ReflectionEntity>of(ReflectionEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).updateInterval(1).noSave().build("reflection"));

    private ModEntities() {}

    /** Mod bus: attribute tables for every living Lacuna entity. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ECHO.get(), EchoEntity.createAttributes().build());
        event.put(LANTERN_MOTH.get(), LanternMoth.createAttributes().build());
        event.put(GLOOMCRAWLER.get(), Gloomcrawler.createAttributes().build());
        event.put(CLOCKWORK_HOUND.get(), ClockworkHound.createAttributes().build());
        event.put(DECOY.get(), DecoyEntity.createAttributes().build());
        event.put(REFLECTION.get(), ReflectionEntity.createAttributes().build());
    }

    /** Mod bus: natural spawn placement rules for the M9 mobs (new entries need a placement type and heightmap). */
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(LANTERN_MOTH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LanternMoth::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GLOOMCRAWLER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Gloomcrawler::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(CLOCKWORK_HOUND.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ClockworkHound::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
