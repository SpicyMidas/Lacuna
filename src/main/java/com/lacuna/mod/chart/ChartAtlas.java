package com.lacuna.mod.chart;

import com.lacuna.mod.Balance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * The server's chart of the Overworld: a cache of {@link ChartTile}s shared by every holder, and the sampler that fills
 * them. It never loads or generates a chunk:
 * <ul>
 * <li>where the chunk is loaded the pixel is surveyed like a vanilla map does it (top block under the surface
 * heightmap, its map colour, water depth, slope shading), and stays surveyed after the chunk unloads (until the tile
 * leaves the cache);</li>
 * <li>anywhere else it is sketched from the world generator's climate: the biome the noise would put there (asked
 * with the surface depth, so caves never show) gives a {@link ChartInk}, and the generator's terrain depth gives an
 * approximate surface height for the same slope and water-depth shading. One climate sample costs a few
 * microseconds and is taken once per quart column at most.</li>
 * </ul>
 * Work is spent row by row inside the caller's deadline, so any number of charts share one per-tick budget.
 * Main thread only.
 */
final class ChartAtlas {
    private static final int UNSET = Integer.MIN_VALUE;

    private final Map<ChartGrid.TileKey, ChartTile> tiles = new HashMap<>();
    private final ChartInk.Resolver inks = new ChartInk.Resolver();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos depthCursor = new BlockPos.MutableBlockPos();
    private long versions;

    // bound per work() call
    private ServerLevel level;
    private Climate.Sampler sampler;
    private BiomeSource biomes;
    private int seaLevel;
    private int minY;
    private int lastChunkX = UNSET;
    private int lastChunkZ = UNSET;
    @Nullable
    private LevelChunk lastChunk;
    // results of the last survey()
    private int surveyHeight;
    private int surveyDepth;

    ChartTile tile(ChartGrid.TileKey key, long now) {
        ChartTile tile = tiles.computeIfAbsent(key, ChartTile::new);
        tile.wantedAt = now;
        return tile;
    }

    /** True while the tile is unfinished, never sampled, or due for a fresh survey of its loaded land. */
    static boolean due(ChartTile tile, long now) {
        return tile.busy() || !tile.ready() || now - tile.sampledAt >= Balance.CHART_REFRESH_TICKS;
    }

    /** Advances the tile's pass until it completes (true) or {@code deadline} ({@link System#nanoTime()}) passes. */
    boolean work(ServerLevel overworld, ChartTile tile, long now, long deadline) {
        bind(overworld);
        if (!tile.busy()) {
            begin(tile);
        }
        while (tile.row < ChartGrid.TILE) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            sampleRow(tile);
            tile.row++;
        }
        finish(tile, now);
        return true;
    }

    /** Drops the least recently wanted tiles once the cache is over its size (never one wanted in the last 5 s). */
    void evict(long now) {
        if (tiles.size() <= Balance.CHART_MAX_TILES) {
            return;
        }
        List<ChartTile> idle = new ArrayList<>();
        for (ChartTile tile : tiles.values()) {
            if (now - tile.wantedAt > 100) {
                idle.add(tile);
            }
        }
        idle.sort(Comparator.comparingLong(t -> t.wantedAt));
        int excess = tiles.size() - Balance.CHART_MAX_TILES * 9 / 10;
        for (int i = 0; i < idle.size() && i < excess; i++) {
            tiles.remove(idle.get(i).key);
        }
    }

    void clear() {
        tiles.clear();
        inks.clear();
        level = null;
        sampler = null;
        biomes = null;
        lastChunk = null;
    }

    /** Tags were reloaded: biome inks are classified again. */
    void forgetInks() {
        inks.clear();
    }

    // ------------------------------------------------------------------------------------------ passes

    private void bind(ServerLevel overworld) {
        level = overworld;
        sampler = overworld.getChunkSource().randomState().sampler();
        biomes = overworld.getChunkSource().getGenerator().getBiomeSource();
        seaLevel = overworld.getChunkSource().getGenerator().getSeaLevel();
        minY = overworld.getMinBuildHeight();
        // chunks may have unloaded since the last call
        lastChunkX = UNSET;
        lastChunkZ = UNSET;
        lastChunk = null;
    }

    private static void begin(ChartTile tile) {
        tile.firstPass = !tile.ready();
        if (tile.firstPass) {
            tile.nextColors = new byte[ChartGrid.TILE_PIXELS];
            tile.nextRumour = new byte[ChartGrid.RUMOUR_BYTES];
            tile.nextHeights = new short[ChartGrid.TILE_PIXELS];
            tile.nextNorth = new short[ChartGrid.TILE];
        } else {
            // a refresh starts from what the tile shows: unloaded pixels keep it
            tile.nextColors = tile.colors.clone();
            tile.nextRumour = tile.rumour.clone();
            tile.nextHeights = tile.heights.clone();
            tile.nextNorth = tile.north.clone();
        }
        tile.above = new short[ChartGrid.TILE];
        tile.climate.clear();
        tile.row = -1;
    }

    private void finish(ChartTile tile, long now) {
        boolean changed = tile.firstPass || !Arrays.equals(tile.colors, tile.nextColors) || !Arrays.equals(tile.rumour, tile.nextRumour);
        tile.colors = tile.nextColors;
        tile.rumour = tile.nextRumour;
        tile.heights = tile.nextHeights;
        tile.north = tile.nextNorth;
        if (changed) {
            tile.version = ++versions;
        }
        tile.nextColors = null;
        tile.nextRumour = null;
        tile.nextHeights = null;
        tile.nextNorth = null;
        tile.above = null;
        tile.climate.clear();
        tile.climate.trim();
        tile.sampledAt = now;
        tile.row = ChartTile.IDLE;
    }

    private void sampleRow(ChartTile tile) {
        ChartGrid.TileKey key = tile.key;
        int zoom = key.zoom();
        int bpp = 1 << zoom;
        int half = bpp >> 1;
        int r = tile.row;
        int pz = (key.tz() << ChartGrid.TILE_SHIFT) + r;
        int bz = pz * bpp + half;
        for (int c = 0; c < ChartGrid.TILE; c++) {
            int px = (key.tx() << ChartGrid.TILE_SHIFT) + c;
            int bx = px * bpp + half;
            LevelChunk chunk = chunk(bx, bz);
            if (r < 0) {
                // the row north of the tile only shades row 0
                short h;
                if (chunk != null) {
                    survey(chunk, bx, bz);
                    h = (short) surveyHeight;
                } else if (tile.firstPass) {
                    h = (short) climateHeight(tile, bx, bz);
                } else {
                    h = tile.nextNorth[c];
                }
                tile.nextNorth[c] = h;
                tile.above[c] = h;
                continue;
            }
            int i = r * ChartGrid.TILE + c;
            int checker = (px + pz) & 1;
            if (!level.getWorldBorder().isWithinBounds(bx, bz)) {
                tile.nextColors[i] = 0;
                ChartTile.setBit(tile.nextRumour, i, false);
                tile.nextHeights[i] = tile.above[c];
            } else if (chunk != null) {
                MapColor color = survey(chunk, bx, bz);
                tile.nextColors[i] = color == MapColor.WATER ? water(surveyDepth, checker)
                        : land(color, surveyHeight, tile.above[c], bpp, checker);
                ChartTile.setBit(tile.nextRumour, i, false);
                tile.nextHeights[i] = (short) surveyHeight;
            } else if (tile.firstPass) {
                long sample = climate(tile, bx, bz);
                ChartInk ink = ChartInk.byId((int) (sample >> 32));
                int height = (int) sample;
                MapColor color = ink.colorAt(hash(px, pz, zoom));
                tile.nextColors[i] = ink.water && color == MapColor.WATER
                        ? water(Math.max(1, seaLevel - height), checker) : land(color, height, tile.above[c], bpp, checker);
                ChartTile.setBit(tile.nextRumour, i, true);
                tile.nextHeights[i] = (short) height;
            }
            // else: unloaded on a refresh pass, the pixel keeps what it showed (surveyed land stays surveyed)
            tile.above[c] = tile.nextHeights[i];
        }
    }

    @Nullable
    private LevelChunk chunk(int bx, int bz) {
        int cx = bx >> 4;
        int cz = bz >> 4;
        if (cx != lastChunkX || cz != lastChunkZ) {
            lastChunkX = cx;
            lastChunkZ = cz;
            lastChunk = level.getChunkSource().getChunkNow(cx, cz);
        }
        return lastChunk;
    }

    /** One column, the way {@code MapItem#update} reads it: colour returned, height and water depth in fields. */
    private MapColor survey(LevelChunk chunk, int bx, int bz) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) + 1;
        int depth = 0;
        BlockState state;
        if (y <= minY + 1) {
            state = Blocks.BEDROCK.defaultBlockState();
            cursor.set(bx, minY, bz);
        } else {
            do {
                cursor.set(bx, --y, bz);
                state = chunk.getBlockState(cursor);
            } while (state.getMapColor(level, cursor) == MapColor.NONE && y > minY);
            if (y > minY && !state.getFluidState().isEmpty()) {
                int below = y - 1;
                BlockState under;
                do {
                    depthCursor.set(bx, below--, bz);
                    under = chunk.getBlockState(depthCursor);
                    depth++;
                } while (below > minY && !under.getFluidState().isEmpty());
                if (!state.isFaceSturdy(level, cursor, Direction.UP)) {
                    state = state.getFluidState().createLegacyBlock();
                }
            }
        }
        surveyHeight = y;
        surveyDepth = depth;
        return state.getMapColor(level, cursor);
    }

    private int climateHeight(ChartTile tile, int bx, int bz) {
        return (int) climate(tile, bx, bz);
    }

    /**
     * Ink and estimated surface height of the quart column holding {@code (bx, bz)}, as {@code ink << 32 | height}.
     * The biome is asked at surface depth (0), so the cave biomes under mountains never show; the height comes from the
     * generator's depth noise at Y 0, where {@code depth = 1 + offset} and the terrain surface sits near
     * {@code y = 128 * depth} (vanilla's depth gradient runs from 1.5 at Y -64 to -1.5 at Y 320).
     */
    private long climate(ChartTile tile, int bx, int bz) {
        int qx = QuartPos.fromBlock(bx);
        int qz = QuartPos.fromBlock(bz);
        long column = ChunkPos.asLong(qx, qz);
        long cached = tile.climate.get(column);
        if (cached != Long.MIN_VALUE) {
            return cached;
        }
        Climate.TargetPoint point = sampler.sample(qx, 0, qz);
        int height = Math.round(Climate.unquantizeCoord(point.depth()) * 128.0F);
        height = Math.max(minY, Math.min(level.getMaxBuildHeight(), height));
        Holder<Biome> biome = biomes instanceof MultiNoiseBiomeSource multiNoise
                ? multiNoise.getNoiseBiome(new Climate.TargetPoint(point.temperature(), point.humidity(), point.continentalness(),
                        point.erosion(), 0L, point.weirdness()))
                : biomes.getNoiseBiome(qx, QuartPos.fromBlock(seaLevel), qz, sampler);
        long sample = ((long) inks.of(biome).ordinal() << 32) | (height & 0xFFFFFFFFL);
        tile.climate.put(column, sample);
        return sample;
    }

    /** Vanilla land shading: brighter facing a rise from the north, darker facing a drop, checker-dithered. */
    static byte land(MapColor color, int height, int northHeight, int bpp, int checker) {
        double slope = (height - northHeight) * 4.0 / (bpp + 4) + (checker - 0.5) * 0.4;
        return color.getPackedId(slope > 0.6 ? MapColor.Brightness.HIGH : slope < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
    }

    /** Vanilla water shading: shallow water is light, deep water dark, checker-dithered in between. */
    static byte water(int depth, int checker) {
        double d = depth * 0.1 + checker * 0.2;
        return MapColor.WATER.getPackedId(d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
    }

    /** Stable per-pixel hash: the same sketch every time the land is drawn at this zoom. */
    static int hash(int px, int pz, int zoom) {
        int h = px * 0x27D4EB2D + pz * 0x165667B1 + zoom * 0x61C88647;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        return h ^ (h >>> 16);
    }
}
