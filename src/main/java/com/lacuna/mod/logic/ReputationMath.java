package com.lacuna.mod.logic;

/**
 * Township reputation (spec 18.2), pure and unit-tested: range −100..100, five tiers, price multiplier, and daily
 * caps per kind of change.
 */
public final class ReputationMath {
    public static final int MIN = -100;
    public static final int MAX = 100;

    public enum Tier { HOSTILE, WARY, NEUTRAL, FRIENDLY, HONORED }

    /** Kinds of reputation change: base amount and daily cap (absolute, 0 = uncapped). */
    public enum Change {
        TRADE(1, 2), HOSTILE_KILL(3, 10), KILL_VILLAGER(-20, 0), HURT_VILLAGER(-10, 0), BREAK_PROTECTED(-5, 15),
        BREAK_INSIDE_LAW(-2, 10), HARVEST(-1, 10), OPEN_CONTAINER(-3, 9), BELL_DAWN(2, 2), BELL_ANNOYED(-5, 5),
        BELL_LAW(-3, 9), WEAPON_LAW(-1, 0), CURFEW_LAW(-1, 0), FINE_PAID(5, 0), CONTRACT(3, 0);

        public final int amount;
        public final int dailyCap;

        Change(int amount, int dailyCap) {
            this.amount = amount;
            this.dailyCap = dailyCap;
        }
    }

    private ReputationMath() {}

    public static int clamp(int rep) {
        return Math.max(MIN, Math.min(MAX, rep));
    }

    public static Tier tier(int rep) {
        if (rep < -50) {
            return Tier.HOSTILE;
        }
        if (rep <= -10) {
            return Tier.WARY;
        }
        if (rep <= 9) {
            return Tier.NEUTRAL;
        }
        return rep <= 49 ? Tier.FRIENDLY : Tier.HONORED;
    }

    /** Villager price multiplier 1 − rep/400 (±25 %). */
    public static double priceMultiplier(int rep) {
        return 1.0 - clamp(rep) / 400.0;
    }

    /**
     * How much of {@code delta} may still be applied today, given {@code usedToday} (absolute amount already applied
     * for that kind of change) and the daily cap (0 = uncapped). Gains may be multiplied (Festival) before capping.
     */
    public static int allowed(int delta, int usedToday, int dailyCap) {
        if (dailyCap <= 0) {
            return delta;
        }
        int room = Math.max(0, dailyCap - Math.abs(usedToday));
        return delta >= 0 ? Math.min(delta, room) : -Math.min(-delta, room);
    }

    /** Applies a change: returns the new reputation. {@code gainMultiplier} only scales positive changes. */
    public static int apply(int rep, int delta, double gainMultiplier) {
        int d = delta > 0 ? (int) Math.round(delta * gainMultiplier) : delta;
        return clamp(rep + d);
    }

    /** Paying a fine restores 5 per coin but never above 0. Returns the coins actually used. */
    public static int coinsForFine(int rep, int coinsOffered) {
        if (rep >= 0) {
            return 0;
        }
        int needed = (-rep + 4) / 5;
        return Math.min(needed, Math.max(0, coinsOffered));
    }
}
