package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/** Homing bolt of the Pyrrhic Scepter: particle trail only (NoopRenderer), never saved (noSave). */
public class PyrrhicBoltEntity extends Projectile {
    private static final int MAX_LIFE = 100;
    private float damage = Balance.BOLT_BASE_DAMAGE;
    @Nullable
    private LivingEntity homingTarget;

    public PyrrhicBoltEntity(EntityType<? extends PyrrhicBoltEntity> type, Level level) {
        super(type, level);
    }

    public PyrrhicBoltEntity(Level level, LivingEntity owner, float damage) {
        this(ModEntities.PYRRHIC_BOLT.get(), level);
        setOwner(owner);
        this.damage = damage;
        setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick();
        if (tickCount > MAX_LIFE) {
            discard();
            return;
        }
        if (!level().isClientSide()) {
            home();
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            hitTargetOrDeflectSelf(hit);
        }
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (level() instanceof ServerLevel server && tickCount % 2 == 0) {
            server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.0);
            server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void home() {
        Vec3 motion = getDeltaMovement();
        double speed = Math.max(0.2, motion.length());
        if (homingTarget == null || !homingTarget.isAlive() || tickCount % 10 == 0) {
            homingTarget = findTarget(motion.normalize());
        }
        if (homingTarget != null) {
            Vec3 want = homingTarget.position().add(0, homingTarget.getBbHeight() * 0.5, 0).subtract(position()).normalize();
            Vec3 dir = motion.normalize().scale(1.0 - Balance.BOLT_TURN_RATE).add(want.scale(Balance.BOLT_TURN_RATE)).normalize();
            setDeltaMovement(dir.scale(speed));
        }
    }

    @Nullable
    private LivingEntity findTarget(Vec3 forward) {
        double r = Balance.BOLT_RANGE;
        double cos = Math.cos(Math.toRadians(Balance.BOLT_CONE_DEGREES / 2.0));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(r),
                e -> e instanceof Enemy && e.isAlive() && e != getOwner())) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(position());
            double dist = to.length();
            if (dist > r || dist < 1.0E-3 || to.normalize().dot(forward) < cos) {
                continue;
            }
            if (dist < bestDist) {
                best = e;
                bestDist = dist;
            }
        }
        return best;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (!level().isClientSide()) {
            if (target.hurt(ModDamageTypes.source(level(), ModDamageTypes.PYRRHIC_BOLT, this, getOwner()), damage)) {
                target.igniteForSeconds(2.0F);
            }
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide()) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            }
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
