package com.lacuna.mod.logic;

import java.util.Random;

/**
 * Village council (spec 18.3), pure and unit-tested: every 7 days one policy for 7 days, weighted by the average
 * reputation of players in the village over the week (higher → Free Market/Festival, lower → Austerity/Martial Law).
 * Also the coin-value rules.
 */
public final class CouncilMath {
    public static final String[] POLICIES = {"free_market", "austerity", "festival", "martial_law"};
    public static final int TERM_DAYS = 7;

    private CouncilMath() {}

    public static double[] weights(double averageRep) {
        double good = Math.max(0.0, averageRep) / 25.0;
        double bad = Math.max(0.0, -averageRep) / 25.0;
        return new double[] {1.0 + good, 1.0 + bad, 1.0 + good, 1.0 + bad};
    }

    public static String choose(double averageRep, Random random) {
        double[] w = weights(averageRep);
        double total = 0;
        for (double v : w) {
            total += v;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll < 0) {
                return POLICIES[i];
            }
        }
        return POLICIES[POLICIES.length - 1];
    }

    /** Coin value drops 2 % per 16 ore delivered (never below 0.6). */
    public static double afterDelivery(double coinValue, int oreBefore, int oreAfter) {
        int steps = oreAfter / 16 - oreBefore / 16;
        double v = coinValue;
        for (int i = 0; i < steps; i++) {
            v *= 0.98;
        }
        return Math.max(0.6, Math.min(1.4, v));
    }

    /** Daily recovery: +0.01 toward 1.0 (from either side). */
    public static double recover(double coinValue) {
        if (Math.abs(coinValue - 1.0) <= 0.01) {
            return 1.0;
        }
        return coinValue < 1.0 ? coinValue + 0.01 : coinValue - 0.01;
    }
}
