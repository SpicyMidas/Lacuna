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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Prime Star Rock: the star at its best (see {@link StarRock}). Sheds glints and chimes softly now and then, so a star
 * that fell out of sight can be found by ear; after about three days it burns out into the Extinguished Block of the Star.
 */
public class PrimeStarRockBlock extends Block {
    public static final MapCodec<PrimeStarRockBlock> CODEC = simpleCodec(PrimeStarRockBlock::new);

    public PrimeStarRockBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) {
            StarRock.schedule(level, pos, this, StarRock.PRIME_MIN, StarRock.PRIME_SPREAD);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, ModBlocks.EXTINGUISHED_STAR_ROCK.get().defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.4F, 1.7F);
        level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.4, 0.1, 0.4, 0.0);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        StarRock.ensureScheduled(level, pos, this, StarRock.PRIME_MIN, StarRock.PRIME_SPREAD);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            StarRock.faceParticle(level, pos, random, ModParticles.SOLAR_GLINT.get(), 0.0, 0.0, 0.0);
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(5) == 0 && !level.getBlockState(pos.above()).isSolidRender(level, pos.above())) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.6, y + 0.05, z + (random.nextDouble() - 0.5) * 0.6,
                    (random.nextDouble() - 0.5) * 0.01, 0.02 + random.nextDouble() * 0.02, (random.nextDouble() - 0.5) * 0.01);
        }
        // a faint crystal chime now and then, so a star that fell out of sight can be found by ear
        if (random.nextInt(3) == 0) {
            level.playLocalSound(x, pos.getY() + 0.5, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F,
                    1.3F + random.nextFloat() * 0.5F, false);
        }
    }
}
