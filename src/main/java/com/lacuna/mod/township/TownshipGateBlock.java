package com.lacuna.mod.township;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.util.SafeRun;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One cell of a township gate (gates are 2 wide x 3 tall). OPEN gates have no collision. Every cell keeps its own
 * 40-tick scheduled tick: open from dawn to dusk (23000-12000), closed at night unless an Honored player of the
 * township is within 4 blocks. Worldgen does not run onPlace, so a random tick (re)starts the schedule. Survival
 * players can only break it while sneaking with an axe (and lose 5 reputation through the protected-block rule).
 */
public class TownshipGateBlock extends Block {
    public static final MapCodec<TownshipGateBlock> CODEC = simpleCodec(TownshipGateBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape OPEN_X = Block.box(0, 14, 6, 16, 16, 10);
    private static final VoxelShape OPEN_Z = Block.box(6, 14, 0, 10, 16, 16);
    private static final int INTERVAL = 40;

    public TownshipGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, true).setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean x = state.getValue(AXIS) == Direction.Axis.X;
        return state.getValue(OPEN) ? (x ? OPEN_X : OPEN_Z) : (x ? X_SHAPE : Z_SHAPE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SafeRun.run(TownshipService.FEATURE, () -> {
            boolean open = TownshipService.isGateOpenTime(level) || honoredNear(level, pos);
            if (open != state.getValue(OPEN)) {
                level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
                level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 0.8F);
            }
        });
        level.scheduleTick(pos, this, INTERVAL);
    }

    private static boolean honoredNear(ServerLevel level, BlockPos pos) {
        var township = TownshipService.at(level, pos);
        if (township.isEmpty()) {
            return false;
        }
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(4.0))) {
            if (ReputationMath.tier(township.get().reputation(p.getUUID())) == ReputationMath.Tier.HONORED) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.isCreative() && !(player.isShiftKeyDown() && player.getMainHandItem().getItem() instanceof AxeItem)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : getShape(state, level, pos, context);
    }
}
