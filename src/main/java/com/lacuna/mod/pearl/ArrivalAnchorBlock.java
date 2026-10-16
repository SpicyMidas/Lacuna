package com.lacuna.mod.pearl;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Arrival Anchor (spec M5): a small crystal shell stuck where an Arrival Shard locks (on a floor, wall or ceiling). Never despawns,
 * blast resistance 1200, not replaceable by fluids. Breaking it by hand gives the shard back and clears the link.
 */
public class ArrivalAnchorBlock extends Block implements EntityBlock {
    public static final MapCodec<ArrivalAnchorBlock> CODEC = simpleCodec(ArrivalAnchorBlock::new);
    /** The side the anchor sticks out towards: its base rests on the opposite face (up = on the floor). */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.UP, Block.box(5.0, 0.0, 5.5, 11.0, 5.0, 10.5),
            Direction.DOWN, Block.box(5.0, 11.0, 5.5, 11.0, 16.0, 10.5),
            Direction.NORTH, Block.box(5.0, 5.5, 11.0, 11.0, 10.5, 16.0),
            Direction.SOUTH, Block.box(5.0, 5.5, 0.0, 11.0, 10.5, 5.0),
            Direction.EAST, Block.box(0.0, 5.5, 5.0, 5.0, 10.5, 11.0),
            Direction.WEST, Block.box(11.0, 5.5, 5.0, 16.0, 10.5, 11.0)));

    public ArrivalAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArrivalAnchorBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArrivalAnchorBlockEntity anchor) {
            PearlLinks.onAnchorBrokenByPlayer((ServerLevel) level, pos, anchor, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // around the crystal's body: 2.5 px out from the face it rests on
        Direction out = state.getValue(FACING);
        double cx = pos.getX() + 0.5 - out.getStepX() * 0.344;
        double cy = pos.getY() + 0.5 - out.getStepY() * 0.344;
        double cz = pos.getZ() + 0.5 - out.getStepZ() * 0.344;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.PORTAL, cx + (random.nextDouble() - 0.5) * 0.4, cy + (random.nextDouble() - 0.5) * 0.4,
                    cz + (random.nextDouble() - 0.5) * 0.4, out.getStepX() * 0.05, 0.05, out.getStepZ() * 0.05);
        }
        if (random.nextInt(6) == 0) {
            // a gold glint off the seam, matching the pearl's palette
            level.addParticle(com.lacuna.mod.registry.ModParticles.SOLAR_GLINT.get(), cx + (random.nextDouble() - 0.5) * 0.35,
                    cy + (random.nextDouble() - 0.5) * 0.3, cz + (random.nextDouble() - 0.5) * 0.35, 0.0, 0.0, 0.0);
        }
    }
}
