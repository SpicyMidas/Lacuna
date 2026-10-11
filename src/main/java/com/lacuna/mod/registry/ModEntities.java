package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.pearl.ArrivalShardEntity;
import com.lacuna.mod.solar.SolarRayEntity;
import com.lacuna.mod.solar.SunsFuryEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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

    private ModEntities() {}
}
