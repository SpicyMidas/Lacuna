package com.lacuna.mod.logic;

/** Eclipser charge, dash and Shadow Walk formulas (spec M4). Pure Java, unit-tested. */
public final class ChargeMath {
    public static final int MAX = 100;
    public static final int PER_HIT = 10;
    public static final int HIT_INTERVAL = 5;
    public static final int DECAY_DELAY = 160;
    public static final int DECAY_PER_SECOND = 2;
    public static final int MIN_DASH = 20;

    private ChargeMath() {}

    public static int clamp(int charge) {
        return Math.max(0, Math.min(MAX, charge));
    }

    /** True if a hit at {@code now} may add charge (at most once per 5 ticks). */
    public static boolean canGain(long now, long lastHit) {
        return lastHit <= 0 || now - lastHit >= HIT_INTERVAL;
    }

    public static int afterHit(int charge) {
        return clamp(charge + PER_HIT);
    }

    /** Charge after one decay step (called once per second); decays only after 8 s without hitting. */
    public static int afterDecaySecond(int charge, long now, long lastHit) {
        if (charge <= 0 || now - lastHit <= DECAY_DELAY) {
            return clamp(charge);
        }
        return clamp(charge - DECAY_PER_SECOND);
    }

    public static boolean canDash(int charge) {
        return charge >= MIN_DASH;
    }

    public static double dashDistance(int charge, double max) {
        return Math.min(max, 4.0 + 0.10 * clamp(charge));
    }

    public static float dashDamage(int charge) {
        return (float) Math.min(20.0, 6.0 + 0.14 * clamp(charge));
    }

    public static int durabilityCost(int charge) {
        return (int) Math.round(5 + 15 * clamp(charge) / 100.0);
    }

    public static int shadowWalkTicks(int charge) {
        return 40 + 80 * clamp(charge) / 100;
    }

    /** Texture stage for the model override: 0 = 0, 1 = 1-33, 2 = 34-66, 3 = 67-100. */
    public static int stage(int charge) {
        int c = clamp(charge);
        if (c == 0) {
            return 0;
        }
        if (c <= 33) {
            return 1;
        }
        if (c <= 66) {
            return 2;
        }
        return 3;
    }

    /** Distance from point P to segment AB (all coordinates in blocks). */
    public static double segmentDistance(double px, double py, double pz, double ax, double ay, double az,
                                         double bx, double by, double bz) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 <= 1e-12 ? 0.0 : ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + dx * t - px;
        double cy = ay + dy * t - py;
        double cz = az + dz * t - pz;
        return Math.sqrt(cx * cx + cy * cy + cz * cz);
    }
}
