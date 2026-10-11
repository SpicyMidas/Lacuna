package com.lacuna.mod.util;

import com.lacuna.mod.Balance;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Safe-spot search used by every teleport (spec section 6):
 * (1) preferred Y, (2) spiral up to 16 blocks horizontally and +-24 vertically for a 2-high free space with a solid,
 * non-harmful floor, (3) surface at the target column, (4) world spawn. Only loaded chunks are inspected; the
 * target chunk is loaded once beforehand.
 */
public final class SafeSpot {
    private SafeSpot() {}

    /** Loads (and if needed generates) the chunk containing pos exactly once. */
    public static void loadChunk(ServerLevel level, BlockPos pos) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static BlockPos find(ServerLevel level, BlockPos preferred, boolean preferSurface) {
        BlockPos clamped = clampToWorld(level, preferred);
        loadChunk(level, clamped);
        if (preferSurface) {
            BlockPos surface = surface(level, clamped);
            if (isSafe(level, surface)) {
                return surface;
            }
        }
        if (isSafe(level, clamped)) {
            return clamped;
        }
        Optional<BlockPos> spiral = spiral(level, clamped);
        if (spiral.isPresent()) {
            return spiral.get();
        }
        BlockPos surface = surface(level, clamped);
        if (isSafe(level, surface)) {
            return surface;
        }
        BlockPos spawn = clampToWorld(level, level.getSharedSpawnPos());
        loadChunk(level, spawn);
        BlockPos spawnSurface = surface(level, spawn);
        if (isSafe(level, spawnSurface)) {
            return spawnSurface;
        }
        return spiral(level, spawnSurface).orElse(spawnSurface);
    }

    /** The position itself if safe, else the nearest safe spot within 16 blocks / +-24 (loaded chunks only). */
    public static Optional<BlockPos> findNear(ServerLevel level, BlockPos pos) {
        if (isSafe(level, pos)) {
            return Optional.of(pos);
        }
        return spiral(level, pos);
    }

    public static BlockPos surface(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return pos;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), Math.max(y, level.getMinBuildHeight() + 1), pos.getZ());
    }

    private static Optional<BlockPos> spiral(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= Balance.SAFE_SPOT_HORIZONTAL; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int i = 0; i <= Balance.SAFE_SPOT_VERTICAL * 2; i++) {
                        int dy = (i % 2 == 0) ? i / 2 : -(i + 1) / 2;
                        m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (isSafe(level, m)) {
                            return Optional.of(m.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }
        BlockPos floor = feet.below();
        BlockPos head = feet.above();
        if (!level.hasChunkAt(floor) || !level.hasChunkAt(head)) {
            return false;
        }
        return isFree(level, feet) && isFree(level, head) && isGoodFloor(level, floor);
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return !isHarmful(state);
    }

    private static boolean isGoodFloor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isHarmful(state) || state.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean isHarmful(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.NETHER_PORTAL);
    }

    public static BlockPos clampToWorld(ServerLevel level, BlockPos pos) {
        var border = level.getWorldBorder();
        int x = (int) Math.max(Math.ceil(border.getMinX()) + 1, Math.min(Math.floor(border.getMaxX()) - 2, pos.getX()));
        int z = (int) Math.max(Math.ceil(border.getMinZ()) + 1, Math.min(Math.floor(border.getMaxZ()) - 2, pos.getZ()));
        int y = Math.max(level.getMinBuildHeight() + 1, Math.min(level.getMaxBuildHeight() - 2, pos.getY()));
        return new BlockPos(x, y, z);
    }
}
