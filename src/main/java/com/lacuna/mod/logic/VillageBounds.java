package com.lacuna.mod.logic;

/**
 * Geometry of a village record, pure and unit-tested: a vanilla village covers a circle around the centre of its
 * structure bounding box (radius = half the longer side + margin, clamped) and a vertical band around the box;
 * a player-founded village covers a fixed radius around its ledger. Records are indexed in 128-block cells.
 */
public final class VillageBounds {
    public static final int MARGIN = 8;
    public static final int MIN_RADIUS = 32;
    public static final int MAX_RADIUS = 112;
    public static final int PLAYER_RADIUS = 48;
    /** Vertical slack below/above the bounding box (mines under a village are not "inside" it). */
    public static final int BELOW = 16;
    public static final int ABOVE = 32;
    public static final int CELL_SHIFT = 7;

    private VillageBounds() {}

    public static int clampRadius(int r) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r));
    }

    /** Radius for a bounding box given by inclusive block coordinates. */
    public static int radius(int minX, int minZ, int maxX, int maxZ) {
        int longer = Math.max(maxX - minX + 1, maxZ - minZ + 1);
        return clampRadius((longer + 1) / 2 + MARGIN);
    }

    public static int center(int min, int max) {
        return Math.floorDiv(min + max, 2);
    }

    public static boolean contains(int cx, int cz, int radius, int minY, int maxY, int x, int y, int z) {
        if (y < minY || y > maxY) {
            return false;
        }
        long dx = x - cx;
        long dz = z - cz;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    public static long cell(int x, int z) {
        return ((long) (x >> CELL_SHIFT) << 32) | ((z >> CELL_SHIFT) & 0xFFFFFFFFL);
    }

    /** Every index cell touched by the square around a circle. */
    public static long[] cells(int cx, int cz, int radius) {
        int x0 = (cx - radius) >> CELL_SHIFT;
        int x1 = (cx + radius) >> CELL_SHIFT;
        int z0 = (cz - radius) >> CELL_SHIFT;
        int z1 = (cz + radius) >> CELL_SHIFT;
        long[] out = new long[(x1 - x0 + 1) * (z1 - z0 + 1)];
        int i = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                out[i++] = ((long) x << 32) | (z & 0xFFFFFFFFL);
            }
        }
        return out;
    }
}
