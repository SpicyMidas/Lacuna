package com.lacuna.mod.pearl;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Arrival Anchor (spec M5): a floating teal crystal created where an Arrival Shard locks. Never despawns,
 * blast resistance 1200, not replaceable by fluids. Breaking it by hand gives the shard back and clears the link.
 */
public class ArrivalAnchorBlock extends Block implements EntityBlock {
    public static final MapCodec<ArrivalAnchorBlock> CODEC = simpleCodec(ArrivalAnchorBlock::new);
    private static final VoxelShape SHAPE = Block.box(5.0, 4.0, 5.0, 11.0, 12.0, 11.0);

    public ArrivalAnchorBlock(Properties properties) {
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
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 0.4 + random.nextDouble() * 0.6, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    0.0, 0.05, 0.0);
        }
    }
}
