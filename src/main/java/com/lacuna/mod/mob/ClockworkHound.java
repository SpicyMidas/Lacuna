package com.lacuna.mod.mob;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ScentTrail;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Clockwork Hound (spec M9): a neutral brass tracker. It extends Wolf only to reuse the vanilla WolfModel, sounds and
 * anger handling; taming, feeding, breeding, armor and every wolf goal are disabled. With no target it picks up a
 * player's scent trail within 48 blocks, follows it oldest to newest and targets the player inside 16 blocks. A
 * player in water or sneaking inside 8 blocks makes it lose the scent (the trail is broken at that point).
 */
public class ClockworkHound extends Wolf {
    private static final double PICKUP_RADIUS = 48.0;
    private static final double TARGET_RADIUS = 16.0;
    private static final double CALM_RADIUS = 8.0;
    private static final int CALM_TICKS = 400;

    @Nullable
    private UUID trackedPlayer;
    private long reachedTime;
    private int calm;

    public ClockworkHound(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // let Wolf build its goals, then replace all of them
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(5, new FollowScentGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    // ---------------------------------------------------------------- no wolf behaviour

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS; // no taming, feeding, dyeing or armor
    }

    @Override
    protected void applyTamingSideEffects() {
        // never tamed; keeps the 30 HP attribute instead of the wolf's 8/40
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Nullable
    @Override
    public Wolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    // ---------------------------------------------------------------- scent

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (calm > 0) {
            calm--;
        }
        if (getTarget() instanceof Player p && trackedPlayer != null && p.getUUID().equals(trackedPlayer)
                && getLastHurtByMob() != p && hidesScent(p)) {
            loseScent(p);
        }
    }

    private boolean hidesScent(Player p) {
        return distanceTo(p) <= CALM_RADIUS && (p.isInWater() || p.isShiftKeyDown());
    }

    /** The player hid their scent: stop hunting and break their trail at the newest point near them. */
    private void loseScent(Player p) {
        ScentTrail trail = ScentTracker.trail(p.getUUID());
        if (trail != null) {
            trail.breakAt(trail.lastPointNear(p.getX(), p.getY(), p.getZ(), TARGET_RADIUS));
        }
        trackedPlayer = null;
        calm = CALM_TICKS;
        stopBeingAngry();
        getNavigation().stop();
    }

    @Nullable
    private ServerPlayer tracked() {
        if (trackedPlayer == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        ServerPlayer p = server.getServer().getPlayerList().getPlayer(trackedPlayer);
        return p != null && p.level() == level() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) ? p : null;
    }

    /** Picks the first trail with a point inside the pickup radius. */
    private boolean pickTrail() {
        if (!(level() instanceof ServerLevel server)) {
            return false;
        }
        for (Map.Entry<UUID, ScentTrail> e : ScentTracker.all().entrySet()) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p.level() != level() || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) {
                continue;
            }
            ScentTrail trail = e.getValue();
            int index = trail.firstPointNear(getX(), getY(), getZ(), PICKUP_RADIUS);
            if (index >= 0) {
                trackedPlayer = p.getUUID();
                reachedTime = trail.points().get(index).time() - 1;
                return true;
            }
        }
        return false;
    }

    public static boolean checkSpawnRules(EntityType<ClockworkHound> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                          RandomSource random) {
        if (reason == MobSpawnType.SPAWNER) {
            return true;
        }
        var below = level.getBlockState(pos.below());
        return (below.is(BlockTags.SAND) || below.is(BlockTags.TERRACOTTA)) && level.getRawBrightness(pos, 0) > 8;
    }

    /** Follows the tracked trail point by point (oldest to newest) and switches to the player inside 16 blocks. */
    static final class FollowScentGoal extends Goal {
        private final ClockworkHound hound;
        private int scan;
        private int stuck;

        FollowScentGoal(ClockworkHound hound) {
            this.hound = hound;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (hound.getTarget() != null || hound.calm > 0 || !LacunaConfig.get(LacunaConfig.SCENT_ENABLED)) {
                return false;
            }
            if (--scan > 0) {
                return false;
            }
            scan = 40 + hound.getRandom().nextInt(20);
            return hound.pickTrail();
        }

        @Override
        public boolean canContinueToUse() {
            return hound.getTarget() == null && hound.trackedPlayer != null && hound.calm <= 0;
        }

        @Override
        public void start() {
            stuck = 0;
            moveToNext();
        }

        @Override
        public void stop() {
            hound.getNavigation().stop();
            if (hound.getTarget() == null) {
                hound.trackedPlayer = null;
            }
        }

        @Override
        public void tick() {
            ServerPlayer p = hound.tracked();
            ScentTrail trail = p == null ? null : ScentTracker.trail(p.getUUID());
            if (p == null || trail == null) {
                hound.trackedPlayer = null;
                return;
            }
            if (hound.distanceTo(p) <= TARGET_RADIUS) {
                if (hound.hidesScent(p)) {
                    hound.loseScent(p);
                } else {
                    hound.setTarget(p);
                }
                return;
            }
            if (hound.getNavigation().isDone() || ++stuck > 200) {
                stuck = 0;
                moveToNext();
            }
        }

        private void moveToNext() {
            ScentTrail trail = hound.trackedPlayer == null ? null : ScentTracker.trail(hound.trackedPlayer);
            int index = trail == null ? -1 : trail.firstAfter(hound.reachedTime);
            if (index < 0) {
                hound.trackedPlayer = null; // trail ran out or was broken
                return;
            }
            List<ScentTrail.Point> points = trail.points();
            ScentTrail.Point point = points.get(index);
            hound.reachedTime = point.time();
            hound.getNavigation().moveTo(point.x(), point.y(), point.z(), 1.0);
        }
    }
}
