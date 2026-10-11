package com.lacuna.mod.beam;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.BeamMath;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server-side beam logic of the Divine Magnifying Glass, called every use tick. */
public final class BeamHandler {
    public static final String FEATURE = "beam";

    private BeamHandler() {}

    public static void tick(Level level, LivingEntity user, ItemStack stack, int ticks) {
        if (!(level instanceof ServerLevel server) || !LacunaConfig.get(LacunaConfig.BEAM_ENABLED)) {
            return;
        }
        SafeRun.run(FEATURE, () -> fire(server, user, stack, ticks), user::stopUsingItem);
    }

    private static void fire(ServerLevel level, LivingEntity user, ItemStack stack, int ticks) {
        boolean underwater = user.isEyeInFluid(FluidTags.WATER);
        boolean night = BeamMath.isNight(ModDimensions.isForgottenLand(level), level.dimensionType().hasSkyLight(), level.isDay());
        BeamMath.Beam beam = BeamMath.compute(underwater, night);
        double range = LacunaConfig.get(LacunaConfig.BEAM_RANGE) * beam.rangeMultiplier();

        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, user, eye, stop,
                user.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e != user && e.isPickable());
        if (entityHit != null) {
            stop = entityHit.getLocation();
        }

        // Damage pulses
        if (entityHit != null && ticks % Balance.BEAM_DAMAGE_INTERVAL == 0) {
            Entity target = entityHit.getEntity();
            float amount = BeamMath.pulseDamage(LacunaConfig.get(LacunaConfig.BEAM_DAMAGE_PER_SECOND), Balance.BEAM_DAMAGE_INTERVAL,
                    beam.damageMultiplier());
            if (amount > 0 && target.hurt(ModDamageTypes.source(level, ModDamageTypes.SOLAR_BEAM, user), amount) && beam.ignites()) {
                target.igniteForSeconds(Balance.BEAM_FIRE_SECONDS);
            }
        }

        // Water vapor where the fluid-aware ray meets water
        BlockHitResult fluidHit = level.clip(new ClipContext(eye, stop, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, user));
        boolean hitsWater = !underwater && fluidHit.getType() == HitResult.Type.BLOCK
                && level.getFluidState(fluidHit.getBlockPos()).is(FluidTags.WATER);
        if (hitsWater) {
            stop = fluidHit.getLocation();
            if (ticks % Balance.BEAM_VAPOR_INTERVAL == 0) {
                vapor(level, fluidHit.getBlockPos(), fluidHit.getLocation());
            }
        } else if (entityHit == null && beam.ignites() && blockHit.getType() == HitResult.Type.BLOCK
                && ticks % Balance.BEAM_BLOCK_INTERVAL == 0 && LacunaConfig.get(LacunaConfig.BEAM_AFFECTS_BLOCKS)) {
            affectBlock(level, user, blockHit);
        }

        if (ticks % Balance.BEAM_PARTICLE_INTERVAL == 0) {
            particles(level, eye, look, stop, beam);
        }

        if (BeamMath.spendsDurability(ticks, Balance.GLASS_TICKS_PER_DURABILITY)) {
            stack.hurtAndBreak(1, user, LivingEntity.getSlotForHand(user.getUsedItemHand()));
        }
    }

    private static void vapor(ServerLevel level, BlockPos pos, Vec3 at) {
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 4, 0.2, 0.1, 0.2, 0.02);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 2, 0.15, 0.05, 0.15, 0.01);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
        if (LacunaConfig.get(LacunaConfig.BEAM_EVAPORATE_WATER)) {
            FluidState fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && !fluid.isSource() && level.getBlockState(pos).is(Blocks.WATER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void affectBlock(ServerLevel level, LivingEntity user, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        if (!level.hasChunkAt(pos)) {
            return;
        }
        if (user instanceof Player player && (!level.mayInteract(player, pos) || !player.mayBuild())) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            smoke(level, pos);
            return;
        }
        if (state.is(BlockTags.ICE) && !state.is(ModTags.Blocks.NOT_MELTABLE)) {
            boolean water = !level.dimensionType().ultraWarm();
            level.setBlock(pos, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            smoke(level, pos);
            return;
        }
        BlockPos firePos = pos.relative(face);
        if (state.isFlammable(level, pos, face) && level.getBlockState(firePos).isAir()
                && BaseFireBlock.canBePlacedAt(level, firePos, face.getOpposite())) {
            level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
            smoke(level, firePos);
        } else if (state.is(Blocks.TNT)) {
            state.onCaughtFire(level, pos, face, user);
            level.removeBlock(pos, false);
        }
    }

    private static void smoke(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 3, 0.25, 0.2, 0.25, 0.01);
    }

    private static void particles(ServerLevel level, Vec3 eye, Vec3 look, Vec3 stop, BeamMath.Beam beam) {
        int rgb = beam.color();
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), 1.0F);
        double length = stop.distanceTo(eye);
        int count = (int) Math.min(Balance.BEAM_MAX_PARTICLES, Math.floor(length));
        for (int i = 1; i <= count; i++) {
            Vec3 p = eye.add(look.scale(i));
            level.sendParticles(dust, p.x, p.y - 0.1, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (beam.underwater() && i % 3 == 0) {
                level.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }
}
