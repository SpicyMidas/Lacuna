package com.lacuna.mod.star;

import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Molten Star Rock: the star just after impact (see {@link StarRock}). A hot floor like magma; smokes, spits lava and
 * sizzles; cools to Prime Star Rock on its own after a minute or two, at once when water touches it or rain falls on it.
 */
public class MoltenStarRockBlock extends Block {
    public static final MapCodec<MoltenStarRockBlock> CODEC = simpleCodec(MoltenStarRockBlock::new);

    public MoltenStarRockBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) {
            if (StarRock.touchesWater(level, pos)) {
                StarRock.schedule(level, pos, this, StarRock.QUENCH_MIN, StarRock.QUENCH_SPREAD);
            } else {
                StarRock.schedule(level, pos, this, StarRock.MOLTEN_MIN, StarRock.MOLTEN_SPREAD);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moving) {
        if (level instanceof ServerLevel server && StarRock.touchesWater(level, pos)) {
            cool(server, pos, true);
            return;
        }
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        cool(level, pos, StarRock.touchesWater(level, pos) || level.isRainingAt(pos.above()));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isRainingAt(pos.above())) {
            cool(level, pos, true);
        } else {
            StarRock.ensureScheduled(level, pos, this, StarRock.MOLTEN_MIN, StarRock.MOLTEN_SPREAD);
        }
    }

    /** Becomes Prime Star Rock: with a hiss of steam when quenched, else with a soft crackle and a last puff of smoke. */
    static void cool(ServerLevel level, BlockPos pos, boolean quenched) {
        level.setBlock(pos, ModBlocks.PRIME_STAR_ROCK.get().defaultBlockState(), 3);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        if (quenched) {
            level.levelEvent(LevelEvent.LAVA_FIZZ, pos, 0);
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 10, 0.4, 0.15, 0.4, 0.02);
        } else {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.6F + level.random.nextFloat() * 0.3F);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.2F);
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 8, 0.3, 0.05, 0.3, 0.02);
        }
        level.sendParticles(ModParticles.SOLAR_GLINT.get(), x, pos.getY() + 0.5, z, 6, 0.4, 0.4, 0.4, 0.0);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully() && entity instanceof LivingEntity) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        boolean open = !level.getBlockState(pos.above()).isSolidRender(level, pos.above());
        if (open && random.nextInt(2) == 0) {
            level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                    x + (random.nextDouble() - 0.5) * 0.8, y + 0.05, z + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.04, 0.0);
        }
        if (open && random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.LAVA, x + (random.nextDouble() - 0.5) * 0.6, y, z + (random.nextDouble() - 0.5) * 0.6,
                    0.0, 0.0, 0.0);
            level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.3F + random.nextFloat() * 0.2F,
                    0.9F + random.nextFloat() * 0.2F, false);
        }
        // white-hot star specks still flying off it
        if (random.nextInt(3) == 0) {
            StarRock.faceParticle(level, pos, random, ModParticles.SOLAR_GLINT.get(), 0.0, 0.0, 0.0);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.25F + random.nextFloat() * 0.15F,
                    0.9F + random.nextFloat() * 0.15F, false);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.12F, 1.8F + random.nextFloat() * 0.4F, false);
        }
    }
}
