package com.lacuna.mod.star;

import com.lacuna.mod.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fallen Star: what a called star leaves where it struck. A small lump of charred star-stone around a glowing core
 * (light 12) that sheds glints; mined with any pickaxe it breaks into 1-3 Star Fragments (Silk Touch keeps it whole).
 * It needs solid ground under it, like a torch.
 */
public class FallenStarBlock extends Block {
    public static final MapCodec<FallenStarBlock> CODEC = simpleCodec(FallenStarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 4, 13, 4, 12), Block.box(5, 4, 5, 11, 9, 11));

    public FallenStarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.45;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.SOLAR_GLINT.get(), x + (random.nextDouble() - 0.5) * 0.7, y + random.nextDouble() * 0.5,
                    z + (random.nextDouble() - 0.5) * 0.7, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.3, y + 0.2, z + (random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.01, 0.02 + random.nextDouble() * 0.02, (random.nextDouble() - 0.5) * 0.01);
        }
        if (random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.5, y + 0.1, z + (random.nextDouble() - 0.5) * 0.5,
                    0.0, 0.03, 0.0);
        }
    }
}
