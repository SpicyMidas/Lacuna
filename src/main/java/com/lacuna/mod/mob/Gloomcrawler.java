package com.lacuna.mod.mob;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Gloomcrawler (spec M9): an ambush spider (vanilla SpiderModel, scaled 0.8). Idle next to tall grass, ferns, bushes
 * or leaves it crouches (smaller hitbox, near-invisible texture); a non-sneaking player within 4 blocks triggers a
 * leap. It flees toward darkness (block light <= 4) when light >= 9 is within 6 blocks and never attacks players
 * holding a light inside 6 blocks. Climbs walls like a spider.
 */
public class Gloomcrawler extends Monster {
    private static final EntityDataAccessor<Boolean> CROUCHED = SynchedEntityData.defineId(Gloomcrawler.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(Gloomcrawler.class, EntityDataSerializers.BOOLEAN);

    public Gloomcrawler(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22.0).add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CROUCHED, false);
        builder.define(CLIMBING, false);
    }

    public boolean isCrouched() {
        return entityData.get(CROUCHED);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (CROUCHED.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions base = super.getDefaultDimensions(pose);
        return isCrouched() ? base.scale(1.0F, 0.5F) : base;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(CLIMBING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new FleeLightGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> target instanceof Player p && mayAmbush(p)));
    }

    private boolean mayAmbush(Player player) {
        float distance = player.distanceTo(this);
        if (holdsLight(player) && distance <= 6.0F) {
            return false;
        }
        if (isCrouched() && LacunaConfig.get(LacunaConfig.GLOOMCRAWLER_ENABLED)) {
            // lying in ambush: only a careless (non-sneaking) player close by springs the trap
            return !player.isShiftKeyDown() && distance <= 4.0F;
        }
        return true;
    }

    static boolean holdsLight(Player player) {
        return player.getMainHandItem().is(ModTags.Items.LIGHT_SOURCES) || player.getOffhandItem().is(ModTags.Items.LIGHT_SOURCES);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            entityData.set(CLIMBING, horizontalCollision);
            if (getTarget() instanceof Player p && holdsLight(p) && p.distanceTo(this) <= 6.0F) {
                setTarget(null); // never presses an attack against a lit light inside 6 blocks
            }
            if (tickCount % 10 == 0) {
                entityData.set(CROUCHED, getTarget() == null && nearCover());
            }
        }
    }

    private boolean nearCover() {
        BlockPos pos = blockPosition();
        for (Direction d : Direction.values()) {
            BlockState s = level().getBlockState(pos.relative(d));
            if (s.is(BlockTags.LEAVES) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN)
                    || s.is(Blocks.LARGE_FERN) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.DEAD_BUSH) || s.is(BlockTags.SAPLINGS)) {
                return true;
            }
        }
        return false;
    }

    boolean lightNearby() {
        if (level().getBrightness(LightLayer.BLOCK, blockPosition()) >= 7) {
            return true;
        }
        Player p = level().getNearestPlayer(this, 6.0);
        return p != null && holdsLight(p);
    }

    /**
     * Normal dark-only monster rules in the Overworld; in the Forgotten Land (permanent dusk, light 15 skies) any
     * light is allowed, like bogged/strays in their structures, otherwise it could never appear there.
     */
    public static boolean checkSpawnRules(EntityType<Gloomcrawler> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                          RandomSource random) {
        if (level.getLevel().dimension() == ModDimensions.FORGOTTEN_LAND) {
            return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
        }
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    /** Flees toward the darkest reachable spot (block light <= 4) when light is near. */
    static final class FleeLightGoal extends Goal {
        private final Gloomcrawler mob;
        @Nullable
        private Vec3 dark;

        FleeLightGoal(Gloomcrawler mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!mob.lightNearby()) {
                return false;
            }
            dark = null;
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < 10; i++) {
                Vec3 p = DefaultRandomPos.getPos(mob, 10, 4);
                if (p == null) {
                    continue;
                }
                int light = mob.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(p));
                if (light < best) {
                    best = light;
                    dark = p;
                }
            }
            return dark != null && best <= 4;
        }

        @Override
        public void start() {
            if (dark != null) {
                mob.getNavigation().moveTo(dark.x, dark.y, dark.z, 1.3);
                mob.setTarget(null);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone();
        }
    }
}
