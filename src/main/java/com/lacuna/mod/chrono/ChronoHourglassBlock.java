package com.lacuna.mod.chrono;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.registry.ModItems;
import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Chrono Hourglass: two blocks tall like a door (placing needs two free blocks, breaking either half removes both
 * and drops one item, from the lower half). Only the lower half carries the {@link ChronoHourglassBlockEntity}, which
 * holds the sand time bought with Chronal Shards and speeds up the world around it while the sand runs.
 */
public class ChronoHourglassBlock extends BaseEntityBlock {
    public static final MapCodec<ChronoHourglassBlock> CODEC = simpleCodec(ChronoHourglassBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape LOWER = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(2, 2, 2, 14, 16, 14));
    private static final VoxelShape UPPER = Shapes.or(Block.box(2, 0, 2, 14, 14, 14), Block.box(1, 14, 1, 15, 16, 15));

    public ChronoHourglassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ------------------------------------------------------------------ two halves

    /** The lower half's position, whichever half {@code pos} is. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState();
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        // the other half went away (or is not ours): this half goes too
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        // called before the upper half is set (placement pre-check): only the lower half must be in place then
        return state.getBlock() != this || below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /** Creative breaking of the upper half must not let the lower half drop the item (vanilla door/tall plant rule). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            ChronoHourglassBlockEntity.suppressRefund(level, base(state, pos));
            if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                BlockPos below = pos.below();
                BlockState lower = level.getBlockState(below);
                if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, below, Block.getId(lower));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        return net.minecraft.util.Mth.getSeed(pos.getX(), base(state, pos).getY(), pos.getZ());
    }

    /** Unspent whole minutes of sand come back as Chronal Shards when the hourglass is broken. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && state.getValue(HALF) == DoubleBlockHalf.LOWER && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof ChronoHourglassBlockEntity hourglass) {
            hourglass.refund(server, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ------------------------------------------------------------------ block entity

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new ChronoHourglassBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CHRONO_HOURGLASS.get(),
                level.isClientSide() ? ChronoHourglassBlockEntity::clientTick : ChronoHourglassBlockEntity::serverTick);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.CHRONAL_SHARD.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(base(state, pos)) instanceof ChronoHourglassBlockEntity hourglass) {
            return hourglass.feed((ServerLevel) level, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(base(state, pos)) instanceof ChronoHourglassBlockEntity hourglass) {
            player.displayClientMessage(hourglass.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ------------------------------------------------------------------ comparator: the sand left, 0-15

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(base(state, pos)) instanceof ChronoHourglassBlockEntity hourglass ? hourglass.comparatorSignal() : 0;
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.lacuna.chrono_hourglass.use",
                Balance.HOURGLASS_TICKS_PER_SHARD / 20, Balance.HOURGLASS_MAX_TICKS / 1200).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.lacuna.chrono_hourglass.use2", Balance.HOURGLASS_SPEED,
                Balance.HOURGLASS_RADIUS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.lacuna.chrono_hourglass.use4", Balance.HOURGLASS_FLUID_SPEED,
                Balance.HOURGLASS_PLAYER_SPEED).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.lacuna.chrono_hourglass.use3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("block.lacuna.chrono_hourglass.use5").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Placement helper for structures and commands: both halves at once (mirrors {@link DoublePlantBlock#placeAt}). */
    public static void placeAt(LevelAccessor level, BlockState state, BlockPos pos, int flags) {
        level.setBlock(pos, state.setValue(HALF, DoubleBlockHalf.LOWER), flags);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), flags);
    }
}
