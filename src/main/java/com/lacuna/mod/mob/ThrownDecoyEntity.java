package com.lacuna.mod.mob;

import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The thrown Clockwork Decoy: unfolds into a {@link DecoyEntity} where it lands. */
public class ThrownDecoyEntity extends ThrowableItemProjectile {
    public ThrownDecoyEntity(EntityType<? extends ThrownDecoyEntity> type, Level level) {
        super(type, level);
    }

    public ThrownDecoyEntity(Level level, LivingEntity shooter) {
        super(ModEntities.THROWN_DECOY.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CLOCKWORK_DECOY.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        Vec3 at = result.getLocation();
        if (result instanceof BlockHitResult block) {
            // stand on top of the face that was hit (or just outside a wall)
            at = Vec3.atBottomCenterOf(block.getBlockPos().relative(block.getDirection()));
        }
        Vec3 spawnAt = at;
        SafeRun.run("clockwork_decoy", () -> {
            DecoyEntity decoy = ModEntities.DECOY.get().create(level());
            if (decoy != null) {
                decoy.moveTo(spawnAt.x, spawnAt.y, spawnAt.z, getYRot(), 0.0F);
                decoy.setOwner(getOwner() == null ? null : getOwner().getUUID());
                level().addFreshEntity(decoy);
            }
        });
        discard();
    }
}
