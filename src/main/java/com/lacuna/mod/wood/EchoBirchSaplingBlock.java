package com.lacuna.mod.wood;

import com.lacuna.mod.registry.ModWoodBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Echo Birch sapling (spec M8): if another Echo Birch sapling stands within 4 blocks (same Y +-1) when this one
 * grows, both grow at once and a bridge of persistent leaves joins their canopies (air only).
 */
public class EchoBirchSaplingBlock extends SaplingBlock {
    public EchoBirchSaplingBlock(TreeGrower grower, Properties properties) {
        super(grower, properties);
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), 4);
            return;
        }
        BlockPos partner = findPartner(level, pos);
        boolean grew = treeGrower.growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
        if (!grew || partner == null) {
            return;
        }
        BlockState partnerState = level.getBlockState(partner);
        if (partnerState.getBlock() instanceof EchoBirchSaplingBlock
                && treeGrower.growTree(level, level.getChunkSource().getGenerator(), partner, partnerState, random)) {
            bridge(level, canopyTop(level, pos), canopyTop(level, partner));
        }
    }

    @Nullable
    private BlockPos findPartner(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
            if (!p.equals(pos) && level.hasChunkAt(p) && level.getBlockState(p).getBlock() instanceof EchoBirchSaplingBlock) {
                return p.immutable();
            }
        }
        return null;
    }

    private static BlockPos canopyTop(ServerLevel level, BlockPos base) {
        BlockPos.MutableBlockPos m = base.mutable();
        int guard = 0;
        while (guard++ < 24 && level.getBlockState(m.above()).is(BlockTags.LOGS)) {
            m.move(0, 1, 0);
        }
        return m.immutable();
    }

    private static void bridge(ServerLevel level, BlockPos a, BlockPos b) {
        BlockState leaves = ModWoodBlocks.ECHO_BIRCH_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int y = Math.max(a.getY(), b.getY());
        int steps = Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ()));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0.0 : i / (double) steps;
            BlockPos p = new BlockPos((int) Math.round(a.getX() + (b.getX() - a.getX()) * t), y,
                    (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t));
            if (level.hasChunkAt(p) && level.getBlockState(p).isAir()) {
                level.setBlock(p, leaves, 3);
            }
        }
    }
}
