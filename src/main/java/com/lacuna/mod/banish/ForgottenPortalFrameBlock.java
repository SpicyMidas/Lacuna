package com.lacuna.mod.banish;

import com.lacuna.mod.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Forgotten Portal Frame: shaped like the End portal frame (13/16 tall, placed facing the player). Eight of them,
 * two per side with the corners empty, ring a 2x2 opening that the Totem of Banishing turns into a
 * {@link ForgottenPortalBlock} (see {@link ForgottenPortals}). {@link #ACTIVE} lights the runes while the frame
 * borders an open portal; it switches itself off as soon as no portal block is left next to it.
 */
public class ForgottenPortalFrameBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ForgottenPortalFrameBlock> CODEC = simpleCodec(ForgottenPortalFrameBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0);

    public ForgottenPortalFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        // the portal closed (a frame of the ring was broken): the runes go dark unless another open portal is beside us
        if (state.getValue(ACTIVE) && direction.getAxis().isHorizontal() && !ForgottenPortals.isPortal(neighborState)
                && !ForgottenPortals.bordersPortal(level, pos)) {
            return state.setValue(ACTIVE, false);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE) && random.nextInt(3) == 0) {
            // a violet wisp now and then off the lit sigil
            level.addParticle(ModParticles.UMBRAL_WISP.get(), pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + 0.85,
                    pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
        }
    }
}
