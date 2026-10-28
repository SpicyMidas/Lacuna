package com.lacuna.mod.chart;

/**
 * The Wayfarer's Chart's pixel grid, shared by the server sampler and the client painter. Unlike a vanilla map it is
 * anchored to the world, not to a map item: pixel {@code (px, pz)} at zoom {@code z} always covers the Overworld blocks
 * {@code [px * 2^z, (px + 1) * 2^z)} (same for Z), so tiles cut from it can be cached, shared between players and
 * scrolled under a chart that stays centred on its holder.
 */
public final class ChartGrid {
    /** Pixels per tile side (the unit the server samples, caches and sends) and its log2. */
    public static final int TILE = 32;
    public static final int TILE_SHIFT = 5;
    public static final int TILE_PIXELS = TILE * TILE;
    /** Bytes of the per-pixel "sketched, not surveyed" bit set of one tile. */
    public static final int RUMOUR_BYTES = TILE_PIXELS / 8;
    /** Pixels across the drawn chart (vanilla maps are 128 too). */
    public static final int VIEW = 128;
    /** Zoom levels 0..4: 1, 2, 4, 8 and 16 blocks per pixel. */
    public static final int ZOOM_LEVELS = 5;

    private ChartGrid() {}

    public static int clampZoom(int zoom) {
        return Math.max(0, Math.min(ZOOM_LEVELS - 1, zoom));
    }

    public static int blocksPerPixel(int zoom) {
        return 1 << clampZoom(zoom);
    }

    /** The tile holding world pixel {@code pixel} (floor division, negative pixels included). */
    public static int tileOf(int pixel) {
        return pixel >> TILE_SHIFT;
    }

    /** One tile of the grid: zoom level and tile coordinates. */
    public record TileKey(int zoom, int tx, int tz) {}
}
