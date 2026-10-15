package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.flute.EchoEntity;
import com.lacuna.mod.gear.PyrrhicBoltEntity;
import com.lacuna.mod.merchant.LastMerchant;
import com.lacuna.mod.mob.DecoyEntity;
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
                    .sized(0.5F, 0.5F).clientTrackingRange(16).updateInterval(2).fireImmune().noSave().build("suns_fury"));
    public static final DeferredHolder<EntityType<?>, EntityType<SolarRayEntity>> SOLAR_RAY = ENTITIES.register("solar_ray",
            () -> EntityType.Builder.<SolarRayEntity>of(SolarRayEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(20).updateInterval(20).fireImmune().noSave().build("solar_ray"));

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

    public static final DeferredHolder<EntityType<?>, EntityType<LastMerchant>> LAST_MERCHANT = ENTITIES.register("last_merchant",
            () -> EntityType.Builder.<LastMerchant>of(LastMerchant::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).noSave().build("last_merchant"));

    // ------------------------------------------------------------------ Wand of Quickening
    public static final DeferredHolder<EntityType<?>, EntityType<com.lacuna.mod.gear.AnimatedItemEntity>> ANIMATED_ITEM = ENTITIES.register(
            "animated_item", () -> EntityType.Builder.<com.lacuna.mod.gear.AnimatedItemEntity>of(com.lacuna.mod.gear.AnimatedItemEntity::new,
                    MobCategory.MISC).sized(0.5F, 0.9F).eyeHeight(0.6F).clientTrackingRange(8).build("animated_item"));

    private ModEntities() {}

    /** Mod bus: attribute tables for every living Lacuna entity. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ECHO.get(), EchoEntity.createAttributes().build());
        event.put(LANTERN_MOTH.get(), LanternMoth.createAttributes().build());
        event.put(DECOY.get(), DecoyEntity.createAttributes().build());
        event.put(REFLECTION.get(), ReflectionEntity.createAttributes().build());
        event.put(LAST_MERCHANT.get(), net.minecraft.world.entity.Mob.createMobAttributes().build());
        event.put(ANIMATED_ITEM.get(), com.lacuna.mod.gear.AnimatedItemEntity.createAttributes().build());
    }

    /** Mod bus: natural spawn placement rules for the M9 Lantern Moth (new entries need a placement type and heightmap). */
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(LANTERN_MOTH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LanternMoth::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
