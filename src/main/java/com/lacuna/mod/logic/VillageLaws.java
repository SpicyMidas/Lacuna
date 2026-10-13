package com.lacuna.mod.logic;

/**
 * The four village laws (spec 18.2, revision 2: the civic system runs in vanilla villages); each village gets two,
 * chosen by its seed. Also the day-phase helpers used by the bell and curfew rules. Pure and unit-tested.
 */
public final class VillageLaws {
    public static final int NO_DRAWN_WEAPONS = 0;
    /** Strangers (reputation below {@link #STRANGER_BELOW}) must not be outdoors in the village from dusk to dawn. */
    public static final int CURFEW = 1;
    public static final int NO_BREAKING = 2;
    public static final int BELL_AT_DAWN = 3;
    public static final int COUNT = 4;
    /** Players below this reputation are strangers to a village. */
    public static final int STRANGER_BELOW = 10;

    private VillageLaws() {}

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

    /** The village seed: world seed mixed with the village key (structure start chunk or ledger position). */
    public static long villageSeed(long worldSeed, long key) {
        long h = worldSeed ^ (key * 0x5DEECE66DL + 0xBL);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 32);
    }

    /** Dawn is day time 0-2000 (24000-tick day). */
    public static boolean isDawn(long dayTime) {
        return Math.floorMod(dayTime, 24000L) <= 2000L;
    }

    /** Curfew runs from dusk to dawn: day time 13000-23000. */
    public static boolean isCurfew(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 13000L && t < 23000L;
    }

    public static boolean isStranger(int reputation) {
        return reputation < STRANGER_BELOW;
    }

    /** What ringing a village bell does (spec 18.2). */
    public enum Bell { NOTHING, DAWN_BONUS, ANNOYED, LAW_BROKEN }

    /** More than 3 rings a day annoy the village; otherwise a ring at dawn pleases it, and outside dawn breaks law 4. */
    public static Bell bell(int ringsToday, long dayTime, boolean bellLaw) {
        if (ringsToday > 3) {
            return Bell.ANNOYED;
        }
        if (isDawn(dayTime)) {
            return Bell.DAWN_BONUS;
        }
        return bellLaw ? Bell.LAW_BROKEN : Bell.NOTHING;
    }
}
