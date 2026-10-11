package com.lacuna.mod.logic;

/** The four township laws (spec 18.2); each township gets two, chosen by its seed. */
public final class TownshipLaws {
    public static final int NO_DRAWN_WEAPONS = 0;
    public static final int GATES_CLOSE_AT_DUSK = 1;
    public static final int NO_BREAKING = 2;
    public static final int BELL_AT_DAWN = 3;
    public static final int COUNT = 4;

    private TownshipLaws() {}

    /** Two distinct laws from the seed. */
    public static int[] choose(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        int a = (int) Math.floorMod(h, (long) COUNT);
        int b = (int) Math.floorMod(h >>> 7, (long) (COUNT - 1));
        if (b >= a) {
            b++;
        }
        return new int[] {a, b};
    }

    /** Day phase helpers on the 24000-tick day: dawn is 0-2000, the gates are open 23000-12000. */
    public static boolean isDawn(long dayTime) {
        return Math.floorMod(dayTime, 24000L) <= 2000L;
    }

    public static boolean gatesOpen(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 23000L || t < 12000L;
    }
}
