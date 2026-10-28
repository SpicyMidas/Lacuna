package com.lacuna.mod.chart;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * One cached tile of the Overworld chart: what it shows now ({@link #colors}, packed vanilla map colours, and
 * {@link #rumour}, one bit per pixel for land sketched from the climate rather than surveyed) and, while it is being
 * (re)sampled, the pass in progress. A pass works into fresh arrays row by row, so it can stop at the tick budget and
 * resume next tick; published arrays are never written again (payloads may still be encoding them).
 */
final class ChartTile {
    static final int IDLE = Integer.MIN_VALUE;

    final ChartGrid.TileKey key;
    // published content, null until the first pass completes
    byte[] colors;
    byte[] rumour;
    /** Surface height per pixel (real or estimated): shades the pixel south of it on the next pass. */
    short[] heights;
    /** Heights of the pixel row just north of the tile. */
    short[] north;
    /** Content version, unique across tiles: bumped whenever a finished pass changed what the tile shows. */
    long version;
    long sampledAt;
    long wantedAt;

    // the pass in progress: next row to sample (-1 = the row north of the tile), IDLE when none
    int row = IDLE;
    boolean firstPass;
    byte[] nextColors;
    byte[] nextRumour;
    short[] nextHeights;
    short[] nextNorth;
    /** Height of the last pixel sampled in each column (the north neighbour of the next row). */
    short[] above;
    /** Climate samples of this pass by quart column ({@code ink << 32 | height}): pixels share quarts below zoom 4. */
    final Long2LongOpenHashMap climate = new Long2LongOpenHashMap();

    ChartTile(ChartGrid.TileKey key) {
        this.key = key;
        climate.defaultReturnValue(Long.MIN_VALUE);
    }

    boolean ready() {
        return colors != null;
    }

    boolean busy() {
        return row != IDLE;
    }

    static boolean bit(byte[] bits, int i) {
        return (bits[i >> 3] & (1 << (i & 7))) != 0;
    }

    static void setBit(byte[] bits, int i, boolean on) {
        if (on) {
            bits[i >> 3] |= (byte) (1 << (i & 7));
        } else {
            bits[i >> 3] &= (byte) ~(1 << (i & 7));
        }
    }
}
