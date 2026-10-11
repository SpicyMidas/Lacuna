package com.lacuna.mod.mob;

import com.lacuna.mod.Balance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Clockwork Decoy (spec M9): a standing brass mannequin that lasts 15 s. When it activates, hostile mobs within 16
 * blocks that target its owner (or nothing) switch to it; it keeps re-asserting itself on those mobs while alive.
 * Never saved, no loot, no experience.
 */
public class DecoyEntity extends PathfinderMob {
    private static final double LURE_RADIUS = 16.0;

    @Nullable
    private UUID owner;
    private final List<Integer> lured = new ArrayList<>();
    private int life;

    public DecoyEntity(EntityType<? extends DecoyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (life == 0) {
            lure();
            playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.6F);
        }
        if (++life >= Balance.DECOY_LIFETIME) {
            expire();
            return;
        }
        if (life % 5 == 0) {
            reassert();
        }
    }

    private void lure() {
        LivingEntity ownerEntity = ownerEntity();
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(LURE_RADIUS),
                m -> m instanceof Enemy && m.isAlive() && m != this)) {
            LivingEntity current = mob.getTarget();
            if (current == null || current == ownerEntity) {
                mob.setTarget(this);
                lured.add(mob.getId());
            }
        }
    }

    /** Target goals would switch lured mobs back to the player within a second; pull them back while we stand. */
    private void reassert() {
        LivingEntity ownerEntity = ownerEntity();
        lured.removeIf(id -> {
            Entity e = level().getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                return true;
            }
            LivingEntity current = mob.getTarget();
            if (current == null || current == ownerEntity) {
                mob.setTarget(this);
            }
            return false;
        });
    }

    @Nullable
    private LivingEntity ownerEntity() {
        if (owner == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        return server.getEntity(owner) instanceof LivingEntity living ? living : null;
    }

    private void expire() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
            playSound(SoundEvents.CHAIN_BREAK, 1.0F, 1.2F);
        }
        releaseLured();
        discard();
    }

    private void releaseLured() {
        for (int id : lured) {
            if (level().getEntity(id) instanceof Mob mob && mob.getTarget() == this) {
                mob.setTarget(null);
            }
        }
        lured.clear();
    }

    @Override
    public void die(DamageSource source) {
        releaseLured();
        super.die(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (owner != null && source.getEntity() != null && owner.equals(source.getEntity().getUUID())) {
            return false; // the owner cannot break their own decoy by accident
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        // no loot, no experience
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
