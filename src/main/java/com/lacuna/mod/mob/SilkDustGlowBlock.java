package com.lacuna.mod.mob;

import com.lacuna.mod.Balance;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * Silk Dust glow decal (spec M9): a thin, non-solid, light-9 sparkle stuck to a block face. It removes itself after
 * 3600 ticks through a scheduled tick (scheduled ticks are saved with the chunk); a random tick re-schedules it if the
 * tick was ever lost. FACING points away from the supporting block.
 */
public class SilkDustGlowBlock extends DirectionalBlock {
    public static final MapCodec<SilkDustGlowBlock> CODEC = simpleCodec(SilkDustGlowBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    private static final DustParticleOptions SPARKLE = new DustParticleOptions(new Vector3f(1.0F, 0.92F, 0.55F), 0.5F);

    static {
        SHAPES.put(Direction.UP, Block.box(0, 0, 0, 16, 1, 16));
        SHAPES.put(Direction.DOWN, Block.box(0, 15, 0, 16, 16, 16));
        SHAPES.put(Direction.NORTH, Block.box(0, 0, 15, 16, 16, 16));
        SHAPES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 1));
        SHAPES.put(Direction.EAST, Block.box(0, 0, 0, 1, 16, 16));
        SHAPES.put(Direction.WEST, Block.box(15, 0, 0, 16, 16, 16));
    }

    public SilkDustGlowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return !level.getBlockState(support).isAir();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, Balance.SILK_GLOW_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, Balance.SILK_GLOW_TICKS);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            VoxelShape shape = SHAPES.get(state.getValue(FACING));
            double x = pos.getX() + shape.min(Direction.Axis.X) + random.nextDouble() * (shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X));
            double y = pos.getY() + shape.min(Direction.Axis.Y) + random.nextDouble() * (shape.max(Direction.Axis.Y) - shape.min(Direction.Axis.Y));
            double z = pos.getZ() + shape.min(Direction.Axis.Z) + random.nextDouble() * (shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z));
            level.addParticle(SPARKLE, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
