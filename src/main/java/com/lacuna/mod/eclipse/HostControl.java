package com.lacuna.mod.eclipse;

import com.lacuna.mod.Balance;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * How a shadow drives its host ({@link Possession}). A creature's own will is suspended: its running goals stop, the
 * goal flags it would move, look, jump or target with are held disabled every tick, its navigation stops and the brain
 * memories that would walk it, look it or make it attack are erased. Then, before its tick, the shadow's movement keys
 * become its velocity (relative to the shadow's look, eased like a walking mob; flyers and swimmers move in 3D along
 * the look, jump rises), it jumps when asked or when it walks into a step; after its tick it faces where the shadow
 * looks (and twitches while fighting back). Physics, collisions, fluids and step-up stay vanilla.
 * Strikes: melee on the creature under the crosshair within reach ({@code doHurtTarget}, or a feeble headbutt for
 * creatures without attack damage), otherwise the ranged attack of ranged creatures; creepers ignite, ghasts and
 * blazes spit fire along the look. A player host strikes with its own weapon.
 */
final class HostControl {
    private static final List<MemoryModuleType<?>> WILL_MEMORIES = List.of(MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.ATTACK_TARGET, MemoryModuleType.PATH, MemoryModuleType.RAM_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

    private HostControl() {}

    // ------------------------------------------------------------------------------------------ will

    static void seize(Mob mob) {
        stopGoals(mob);
        suppress(mob);
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setJumping(false);
        mob.setAggressive(false);
    }

    /** The creature gets its own will back (its goals pick up again on their own). */
    static void free(Mob mob) {
        mob.goalSelector.enableControlFlag(Goal.Flag.MOVE);
        mob.goalSelector.enableControlFlag(Goal.Flag.LOOK);
        mob.goalSelector.enableControlFlag(Goal.Flag.JUMP);
        mob.targetSelector.enableControlFlag(Goal.Flag.TARGET);
        mob.getNavigation().stop();
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        mob.setJumping(false);
    }

    private static void stopGoals(Mob mob) {
        for (WrappedGoal goal : List.copyOf(mob.goalSelector.getAvailableGoals())) {
            if (goal.isRunning()) {
                goal.stop();
            }
        }
        for (WrappedGoal goal : List.copyOf(mob.targetSelector.getAvailableGoals())) {
            if (goal.isRunning()) {
                goal.stop();
            }
        }
    }

    private static void suppress(Mob mob) {
        mob.goalSelector.disableControlFlag(Goal.Flag.MOVE);
        mob.goalSelector.disableControlFlag(Goal.Flag.LOOK);
        mob.goalSelector.disableControlFlag(Goal.Flag.JUMP);
        mob.targetSelector.disableControlFlag(Goal.Flag.TARGET);
        mob.getNavigation().stop();
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
        Brain<?> brain = mob.getBrain();
        for (MemoryModuleType<?> memory : WILL_MEMORIES) {
            brain.eraseMemory(memory);
        }
    }

    /** Struggle per tick before randomisation: hostile, big and wounded creatures resist more; your own pets less. */
    static float will(Mob mob, ServerPlayer possessor) {
        float w = Balance.POSSESS_MOB_WILL_BASE + (mob instanceof Enemy ? Balance.POSSESS_MOB_WILL_HOSTILE : 0.0F)
                + Math.min(Balance.POSSESS_MOB_WILL_HEALTH_CAP, mob.getMaxHealth() * Balance.POSSESS_MOB_WILL_PER_HEALTH);
        if (mob.getHealth() < mob.getMaxHealth() * 0.5F) {
            w += Balance.POSSESS_MOB_WILL_WOUNDED;
        }
        if (mob instanceof OwnableEntity pet && possessor.getUUID().equals(pet.getOwnerUUID())) {
            w *= Balance.POSSESS_MOB_WILL_OWN_PET;
        }
        return w;
    }

    // ------------------------------------------------------------------------------------------ movement

    private static boolean flies(Mob mob) {
        return mob instanceof FlyingMob || mob instanceof FlyingAnimal || mob instanceof Bat || mob.isNoGravity()
                || mob.getNavigation() instanceof FlyingPathNavigation;
    }

    private static boolean swims(Mob mob) {
        return mob.isInWater() && (mob instanceof WaterAnimal || mob.getNavigation() instanceof WaterBoundPathNavigation
                || mob.getNavigation() instanceof AmphibiousPathNavigation);
    }

    private static double speed(Mob mob, boolean flyer) {
        double base = flyer && mob.getAttributes().hasAttribute(Attributes.FLYING_SPEED)
                ? mob.getAttributeValue(Attributes.FLYING_SPEED) * 0.5
                : mob.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED) ? mob.getAttributeValue(Attributes.MOVEMENT_SPEED) : 0.25;
        return Mth.clamp(base * Balance.POSSESS_MOB_SPEED_SCALE, Balance.POSSESS_MOB_SPEED_MIN, Balance.POSSESS_MOB_SPEED_MAX);
    }

    /** Before the creature's own tick: its will held down, the shadow's keys become its motion. */
    static void preTick(Possession.Link link, Mob mob) {
        suppress(mob);
        mob.setNoActionTime(0);
        float fwd = link.forward;
        float str = link.strafe;
        float len = Mth.sqrt(fwd * fwd + str * str);
        if (len > 1.0F) {
            fwd /= len;
            str /= len;
        }
        boolean moving = len > 0.01F;
        boolean jump = link.jump;
        boolean flyer = flies(mob);
        boolean swimmer = !flyer && swims(mob);
        double speed = speed(mob, flyer);
        if (link.sprint && fwd > 0.0F) {
            speed *= Balance.POSSESS_SPRINT;
        }
        if (!flyer && !swimmer && (mob.isInWater() || mob.isInLava())) {
            speed *= 0.5;
        }
        float yawRad = link.yaw * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yawRad);
        double cos = Mth.cos(yawRad);
        Vec3 v = mob.getDeltaMovement();
        if (flyer || swimmer) {
            Vec3 look = Vec3.directionFromRotation(link.pitch, link.yaw);
            Vec3 d = new Vec3(look.x * fwd + str * cos, look.y * fwd + (jump ? 0.8 : 0.0), look.z * fwd + str * sin);
            if (d.lengthSqr() > 1.0) {
                d = d.normalize();
            }
            d = d.scale(speed);
            double k = mob.hurtTime > 0 ? 0.15 : 0.45;
            mob.setDeltaMovement(v.x + (d.x - v.x) * k, flyer || moving || jump ? v.y + (d.y - v.y) * (mob.hurtTime > 0 ? 0.3 : 1.0) : v.y,
                    v.z + (d.z - v.z) * k);
        } else {
            if (moving) {
                double wx = str * cos - fwd * sin;
                double wz = fwd * cos + str * sin;
                double k = mob.onGround() ? 0.6 : 0.12;
                if (mob.hurtTime > 0) {
                    k *= 0.3; // let knockback land
                }
                mob.setDeltaMovement(v.x + (wx * speed - v.x) * k, v.y, v.z + (wz * speed - v.z) * k);
                if (mob.horizontalCollision && mob.onGround()) {
                    jump = true; // walk up steps and ledges like a walking creature would
                }
            }
            if (jump) {
                mob.getJumpControl().jump();
            }
        }
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
    }

    /** After the creature's own tick: it faces where the shadow looks (twitching while it fights back). */
    static void postTick(Possession.Link link, Mob mob) {
        float yaw = link.yaw;
        float pitch = link.pitch;
        if (link.fightingBack() && link.struggled) {
            yaw += (mob.getRandom().nextFloat() - 0.5F) * 24.0F;
            pitch += (mob.getRandom().nextFloat() - 0.5F) * 12.0F;
        }
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
    }

    // ------------------------------------------------------------------------------------------ strikes

    static void strike(Possession.Link link, int hint, long now) {
        if (now < link.nextStrike) {
            return;
        }
        LivingEntity host = link.host;
        ServerLevel level = (ServerLevel) host.level();
        Vec3 eye = host.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(link.pitch, link.yaw);
        if (host instanceof ServerPlayer player) {
            LivingEntity target = pick(level, link, eye, look, player.entityInteractionRange(), hint);
            if (target != null) {
                Possession.forcing = true;
                try {
                    player.attack(target);
                } finally {
                    Possession.forcing = false;
                }
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            link.nextStrike = now + 10;
            return;
        }
        Mob mob = (Mob) host;
        if (mob instanceof Creeper creeper) {
            creeper.ignite();
            link.nextStrike = now + 40;
            return;
        }
        double reach = Math.max(2.5, mob.getBbWidth() * 0.5 + 2.0);
        LivingEntity target = pick(level, link, eye, look, Balance.POSSESS_RANGED_RANGE, hint);
        boolean inReach = target != null && target.getBoundingBox().distanceToSqr(eye) <= reach * reach && mob.hasLineOfSight(target);
        if (inReach) {
            melee(mob, target, link.yaw);
            link.nextStrike = now + Balance.POSSESS_MELEE_COOLDOWN;
        } else if (ranged(level, mob, target, look)) {
            link.nextStrike = now + Balance.POSSESS_RANGED_COOLDOWN;
        } else {
            mob.swing(InteractionHand.MAIN_HAND);
            link.nextStrike = now + 6;
        }
    }

    private static void melee(Mob mob, LivingEntity target, float yaw) {
        mob.swing(InteractionHand.MAIN_HAND);
        if (mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
            mob.doHurtTarget(target);
        } else if (target.hurt(mob.damageSources().mobAttack(mob), 1.0F)) {
            target.knockback(0.4, Mth.sin(yaw * Mth.DEG_TO_RAD), -Mth.cos(yaw * Mth.DEG_TO_RAD)); // a feeble headbutt
        }
    }

    private static boolean ranged(ServerLevel level, Mob mob, @Nullable LivingEntity target, Vec3 look) {
        if (mob instanceof Ghast ghast) {
            LargeFireball ball = new LargeFireball(level, ghast, look, ghast.getExplosionPower());
            ball.setPos(ghast.getX() + look.x * 4.0, ghast.getY(0.5) + 0.5, ghast.getZ() + look.z * 4.0);
            level.addFreshEntity(ball);
            level.levelEvent(null, 1016, ghast.blockPosition(), 0);
            return true;
        }
        if (mob instanceof Blaze blaze) {
            SmallFireball ball = new SmallFireball(level, blaze, look);
            ball.setPos(blaze.getX() + look.x * 0.6, blaze.getY(0.5) + 0.5, blaze.getZ() + look.z * 0.6);
            level.addFreshEntity(ball);
            level.levelEvent(null, 1018, blaze.blockPosition(), 0);
            return true;
        }
        if (mob instanceof RangedAttackMob shooter && target != null && mob.hasLineOfSight(target)) {
            mob.swing(InteractionHand.MAIN_HAND);
            shooter.performRangedAttack(target, 1.0F);
            return true;
        }
        return false;
    }

    /**
     * The living thing the host looks at within {@code range}: the client's crosshair entity when it really lies on the
     * shadow's look ray, else the first one along that ray up to the first block. Players the shadow may not hurt (no
     * PvP, teammates) are never struck through a host.
     */
    @Nullable
    private static LivingEntity pick(ServerLevel level, Possession.Link link, Vec3 eye, Vec3 look, double range, int hint) {
        LivingEntity host = link.host;
        Vec3 end = eye.add(look.scale(range));
        Entity hinted = hint >= 0 ? level.getEntity(hint) : null;
        if (hinted instanceof PartEntity<?> part) {
            hinted = part.getParent();
        }
        if (hinted instanceof LivingEntity living && living != host && living != link.possessor && living.isAlive() && !living.isSpectator()
                && living.getBoundingBox().inflate(0.3).clip(eye, end).isPresent()) {
            return spared(link, living) ? null : living;
        }
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, host));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(host, eye, end, host.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                e -> e instanceof LivingEntity && e != link.possessor && e.isAlive() && !e.isSpectator() && e.isPickable(), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living && !spared(link, living) ? living : null;
    }

    private static boolean spared(Possession.Link link, LivingEntity target) {
        return target instanceof Player other && !link.possessor.canHarmPlayer(other);
    }
}
