package com.lacuna.mod.flute;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Echo (spec M6): a translucent, short-lived (200 ticks) shade of a mob that recently died, fighting for whoever
 * played the Whisperbone Flute. Immune to its owner, no XP or loot, never saved; leaves an Echo Fragment on expiry.
 */
public class EchoEntity extends PathfinderMob {
    @Nullable
    private UUID owner;
    private int life;

    public EchoEntity(EntityType<? extends EchoEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Balance.ECHO_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, Balance.ECHO_MAX_ATTACK)
                .add(Attributes.MOVEMENT_SPEED, Balance.ECHO_SPEED)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    public void setup(@Nullable UUID owner, float health, float attack) {
        this.owner = owner;
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.max(1.0F, Math.min(Balance.ECHO_MAX_HEALTH, health)));
        }
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(Math.max(1.0F, Math.min(Balance.ECHO_MAX_ATTACK, attack)));
        }
        setHealth(getMaxHealth());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Nullable
    public LivingEntity ownerEntity() {
        if (owner == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity e = server.getEntity(owner);
        return e instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (++life >= Balance.ECHO_LIFETIME) {
            expire();
            return;
        }
        if (life % 10 != 0) {
            return;
        }
        LivingEntity master = ownerEntity();
        if (master == null) {
            return;
        }
        LivingEntity wanted = master.getLastHurtMob();
        if (wanted == null || !wanted.isAlive() || wanted == this) {
            wanted = master.getLastHurtByMob();
        }
        if (wanted != null && wanted.isAlive() && wanted != master && !(wanted instanceof EchoEntity)) {
            setTarget(wanted);
        } else if (getTarget() == null && distanceToSqr(master) > 36.0) {
            getNavigation().moveTo(master, 1.1);
        }
    }

    private void expire() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.0, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            spawnAtLocation(new ItemStack(ModItems.ECHO_FRAGMENT.get()));
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (owner != null && source.getEntity() != null && owner.equals(source.getEntity().getUUID())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return (owner == null || !owner.equals(target.getUUID())) && super.canAttack(target);
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
