package com.lacuna.mod.beam;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.BeamMath;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.registry.ModDimensions;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One Divine Magnifying Glass ray, computed with the same rules on the server (damage, blocks) and on every client
 * (the drawn beam): range 24 (x0.5 underwater), stops at the first solid block, then at the first living entity, and
 * visually at the water surface when it meets water from above.
 *
 * @param stop where the beam visibly ends (entity, water surface, block face or full range)
 */
public record BeamTrace(BeamMath.Beam beam, Vec3 eye, Vec3 look, double range, BlockHitResult blockHit,
                        @Nullable EntityHitResult entityHit, @Nullable BlockHitResult waterHit, Vec3 stop) {

    /** Traces the beam of {@code user} from {@code eye} along {@code look} (unit vector). */
    public static BeamTrace of(Level level, LivingEntity user, Vec3 eye, Vec3 look, float partialTick) {
        boolean underwater = user.isEyeInFluid(FluidTags.WATER);
        boolean night = BeamMath.isNight(ModDimensions.isForgottenLand(level), level.dimensionType().hasSkyLight(),
                isDay(level, partialTick));
        BeamMath.Beam beam = BeamMath.compute(underwater, night);
        double range = LacunaConfig.get(LacunaConfig.BEAM_RANGE) * beam.rangeMultiplier();
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, user, eye, stop,
                user.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e != user && e.isPickable());
        if (entityHit != null) {
            stop = entityHit.getLocation();
        }

        BlockHitResult waterHit = null;
        if (!underwater) {
            BlockHitResult fluidHit = level.clip(new ClipContext(eye, stop, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, user));
            if (fluidHit.getType() == HitResult.Type.BLOCK && level.getFluidState(fluidHit.getBlockPos()).is(FluidTags.WATER)) {
                waterHit = fluidHit;
                stop = fluidHit.getLocation();
            }
        }
        return new BeamTrace(beam, eye, look, range, blockHit, entityHit, waterHit, stop);
    }

    /** True when the beam ends on a solid block face (not an entity, not water, not thin air). */
    public boolean hitsBlock() {
        return entityHit == null && waterHit == null && blockHit.getType() == HitResult.Type.BLOCK;
    }

    public boolean hitsSomething() {
        return entityHit != null || waterHit != null || blockHit.getType() == HitResult.Type.BLOCK;
    }

    /**
     * Day test that also works on clients, whose cached sky darkness is only computed when the level is created:
     * the server uses {@link Level#isDay()}, clients recompute the same vanilla rule from the current sky.
     */
    public static boolean isDay(Level level, float partialTick) {
        if (!level.isClientSide()) {
            return level.isDay();
        }
        return !level.dimensionType().hasFixedTime()
                && SolarMath.skyDarken(level.getTimeOfDay(partialTick), level.getRainLevel(partialTick), level.getThunderLevel(partialTick)) < 4;
    }
}
