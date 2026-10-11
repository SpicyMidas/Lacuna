package com.lacuna.mod.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The thrown Sun's Fury: speed 2.5, gravity 0.03, detonates on impact or after 10 s. */
public class SunsFuryEntity extends ThrowableItemProjectile {
    private boolean loadedFromDisk;

    public SunsFuryEntity(EntityType<? extends SunsFuryEntity> type, Level level) {
        super(type, level);
    }

    public SunsFuryEntity(Level level, LivingEntity shooter) {
        super(ModEntities.SUNS_FURY.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SUNS_FURY.get();
    }

    @Override
    protected double getDefaultGravity() {
        return Balance.SUNS_FURY_GRAVITY;
    }

    @Override
    public void tick() {
        if (loadedFromDisk) {
            discard(); // no in-flight state survives a reload
            return;
        }
        super.tick();
        if (!level().isClientSide() && tickCount >= Balance.SUNS_FURY_FUSE) {
            detonate(position());
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            detonate(result.getLocation());
        }
    }

    private void detonate(Vec3 at) {
        if (isRemoved() || !(level() instanceof ServerLevel server)) {
            return;
        }
        SafeRun.run(SolarRayEntity.FEATURE, () -> {
            if (server.dimension() == Level.OVERWORLD && LacunaConfig.get(LacunaConfig.SOLAR_CHANGE_TIME)) {
                long day = server.getDayTime();
                long noon = (day / 24000L) * 24000L + 6000L;
                if (noon <= day) {
                    noon += 24000L;
                }
                server.setDayTime(noon);
                server.setWeatherParameters(12000, 0, false, false);
            }
            SolarRayEntity ray = new SolarRayEntity(ModEntities.SOLAR_RAY.get(), server);
            ray.setPos(at.x, at.y, at.z);
            ray.setOwnerUuid(getOwner() == null ? null : getOwner().getUUID());
            server.addFreshEntity(ray);
        });
        discard();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadedFromDisk = true;
    }
}
