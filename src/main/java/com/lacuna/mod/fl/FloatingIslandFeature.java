package com.lacuna.mod.fl;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Inverted Falls floating island (spec M10): a squashed ellipsoid of stone at Y 110-150 with a grass/moss top,
 * zenithite veins, divine glass clusters and sometimes a Crystal Willow. A chain of updraft springs (in a pool basin
 * on the ground, then on small islets every <= 38 blocks) rises just beside the island and ends 2 blocks above its
 * top, so the island can be reached. Every write stays within 27 blocks of the chunk origin (3x3 chunk region).
 */
public class FloatingIslandFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceKey<ConfiguredFeature<?, ?>> WILLOW =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, Lacuna.id("crystal_willow_tree"));
    private static final int SEGMENT = 38;

    public FloatingIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource r = context.random();
        BlockPos origin = context.origin();
        int cy = 110 + r.nextInt(41);
        int rx = 4 + r.nextInt(5);
        int rz = 4 + r.nextInt(5);
        int up = 2 + r.nextInt(2);
        int down = 4 + r.nextInt(4);
        BlockPos center = new BlockPos(origin.getX(), cy, origin.getZ());
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                int colTop = topOffset(dx, dz, rx, rz, up);
                if (colTop < 0) {
                    continue;
                }
                double edge = 1.0 - sq(dx / (rx + 0.5)) - sq(dz / (rz + 0.5));
                int colBottom = (int) Math.round(down * edge) + r.nextInt(2);
                for (int dy = -colBottom; dy <= colTop; dy++) {
                    p.set(center.getX() + dx, cy + dy, center.getZ() + dz);
                    BlockState s = dy == colTop ? (r.nextInt(4) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState()
                            : Blocks.GRASS_BLOCK.defaultBlockState()) : dy >= colTop - 2 ? dirt : stone;
                    level.setBlock(p, s, 2);
                }
            }
        }
        int top = cy + up;
        veins(level, r, center, rx, rz, down);
        clusters(level, r, center, rx, rz, up);
        if (r.nextInt(2) == 0 && topOffset(-1, 0, rx, rz, up) >= 0) {
            // heightmaps are not reliable mid-decoration (neighbour chunks are unprimed): use the island geometry
            BlockPos treeAt = new BlockPos(center.getX() - 1, cy + topOffset(-1, 0, rx, rz, up) + 1, center.getZ());
            level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap(reg -> reg.getHolder(WILLOW))
                    .ifPresent(h -> h.value().place(level, context.chunkGenerator(), r, treeAt));
        }
        springs(level, r, center.offset(rx + 2, 0, 0), top);
        return true;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Height of the island surface above its centre plane at (dx, dz), or -1 outside the ellipse. */
    static int topOffset(int dx, int dz, int rx, int rz, int up) {
        double d = sq(dx / (rx + 0.5)) + sq(dz / (rz + 0.5));
        return d > 1.0 ? -1 : (int) Math.round(up * Math.sqrt(1.0 - d));
    }

    private static void veins(WorldGenLevel level, RandomSource r, BlockPos c, int rx, int rz, int down) {
        BlockState ore = ModBlocks.ZENITHITE_ORE.get().defaultBlockState();
        int veins = 2 + r.nextInt(2);
        for (int v = 0; v < veins; v++) {
            BlockPos at = c.offset(r.nextInt(rx + 1) - rx / 2, -1 - r.nextInt(Math.max(1, down / 2)), r.nextInt(rz + 1) - rz / 2);
            int size = 3 + r.nextInt(4);
            for (int k = 0; k < size; k++) {
                if (level.getBlockState(at).is(Blocks.STONE)) {
                    level.setBlock(at, ore, 2);
                }
                at = at.relative(Direction.getRandom(r));
            }
        }
    }

    private static void clusters(WorldGenLevel level, RandomSource r, BlockPos c, int rx, int rz, int up) {
        BlockState cluster = ModBlocks.DIVINE_GLASS_CLUSTER.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
        int n = 1 + r.nextInt(3);
        for (int k = 0; k < n; k++) {
            int dx = r.nextInt(rx * 2 + 1) - rx;
            int dz = r.nextInt(rz * 2 + 1) - rz;
            int off = topOffset(dx, dz, rx, rz, up);
            if (off < 0) {
                continue;
            }
            BlockPos at = c.offset(dx, off + 1, dz);
            if (level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) && level.isEmptyBlock(at)) {
                level.setBlock(at, cluster, 2);
            }
        }
    }

    /** Ground basin spring plus islet springs up to 2 blocks above the island top, beside the island. */
    private static void springs(WorldGenLevel level, RandomSource r, BlockPos column, int islandTop) {
        // scan down instead of reading a worldgen heightmap (those are unprimed when /place feature runs on live chunks)
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos(column.getX(), islandTop - 6, column.getZ());
        while (scan.getY() > level.getMinBuildHeight() && level.isEmptyBlock(scan)) {
            scan.move(0, -1, 0);
        }
        BlockPos ground = scan.immutable();
        if (!level.getFluidState(ground).isEmpty() || ground.getY() >= islandTop - 4 || ground.getY() <= level.getMinBuildHeight()) {
            return;
        }
        basin(level, ground);
        int target = islandTop + 2;
        int y = ground.getY();
        while (y < target) {
            int range = Math.min(SEGMENT, target - y);
            BlockPos spring = new BlockPos(column.getX(), y, column.getZ());
            if (y != ground.getY()) {
                islet(level, r, spring);
            }
            level.setBlock(spring, ModBlocks.UPDRAFT_SPRING.get().defaultBlockState()
                    .setValue(UpdraftSpringBlock.RANGE, Math.max(1, Math.min(Balance.UPDRAFT_HEIGHT, range))), 2);
            y += range;
        }
    }

    private static void basin(WorldGenLevel level, BlockPos center) {
        BlockState rim = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos at = center.offset(dx, 0, dz);
                boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (inner) {
                    level.setBlock(at, Blocks.WATER.defaultBlockState(), 2);
                    level.setBlock(at.below(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
                    level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 2);
                } else if (!level.getBlockState(at).isSolid()) {
                    level.setBlock(at, rim, 2);
                }
            }
        }
    }

    private static void islet(WorldGenLevel level, RandomSource r, BlockPos spring) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring == 0 || (ring == 2 && (Math.abs(dx) == 2 && Math.abs(dz) == 2 || r.nextInt(3) == 0))) {
                    continue;
                }
                BlockPos at = spring.offset(dx, 0, dz);
                level.setBlock(at, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 2);
                if (ring == 1) {
                    level.setBlock(at.below(2), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
    }
}
