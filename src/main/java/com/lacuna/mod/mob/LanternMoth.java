package com.lacuna.mod.mob;

import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModTags;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Lantern Moth (spec M9): a passive glowing flyer drawn to light. It extends Bee only to reuse the vanilla BeeModel
 * and flying movement; every bee goal is removed (no hive, no pollination, no stinging, no anger, no breeding).
 */
public class LanternMoth extends Bee {
    public LanternMoth(EntityType<? extends Bee> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Bee.createAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.FLYING_SPEED, 0.5);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // initialises Bee's internal goal fields (used by Bee#hurt), then replace everything
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SeekLightGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public Bee getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(null);
    }

    @Override
    public boolean isAngry() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true; // ambient like bats: never piles up
    }

    /** Night, open sky (no cave spawns) and solid ground; spawners always pass. */
    public static boolean checkSpawnRules(EntityType<LanternMoth> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                          RandomSource random) {
        if (reason == MobSpawnType.SPAWNER) {
            return true;
        }
        // the Forgotten Land has a fixed dusk (no night), so its marsh spawns ignore the clock
        boolean dusk = level.getLevel().isNight() || level.getLevel().dimension() == ModDimensions.FORGOTTEN_LAND;
        boolean night = dusk && level.getBrightness(LightLayer.SKY, pos) >= 8;
        return night && level.getBlockState(pos.below()).isSolid();
    }

    /** Hovers around the nearest strong light (block light >= 12) or a player holding a moth attractor. */
    static final class SeekLightGoal extends Goal {
        private final LanternMoth moth;
        @Nullable
        private Vec3 target;
        private int recheck;

        SeekLightGoal(LanternMoth moth) {
            this.moth = moth;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--recheck > 0) {
                return target != null;
            }
            recheck = 40;
            target = findLight();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && recheck-- > -200;
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            RandomSource r = moth.getRandom();
            if (moth.getNavigation().isDone() || r.nextInt(20) == 0) {
                moth.getMoveControl().setWantedPosition(target.x + (r.nextDouble() - 0.5) * 3.0, target.y + 0.5 + r.nextDouble() * 1.5,
                        target.z + (r.nextDouble() - 0.5) * 3.0, 0.8);
            }
            if (recheck <= 0) {
                target = findLight();
                recheck = 40;
            }
        }

        @Nullable
        private Vec3 findLight() {
            Player player = moth.level().getNearestPlayer(moth, 16.0);
            if (player != null && (player.getMainHandItem().is(ModTags.Items.MOTH_ATTRACTOR) || player.getOffhandItem().is(ModTags.Items.MOTH_ATTRACTOR))) {
                return player.getEyePosition();
            }
            RandomSource r = moth.getRandom();
            BlockPos origin = moth.blockPosition();
            for (int i = 0; i < 24; i++) {
                BlockPos p = origin.offset(r.nextInt(33) - 16, r.nextInt(9) - 4, r.nextInt(33) - 16);
                if (moth.level().hasChunkAt(p) && moth.level().getBrightness(LightLayer.BLOCK, p) >= 12) {
                    return Vec3.atCenterOf(p);
                }
            }
            return null;
        }
    }
}
