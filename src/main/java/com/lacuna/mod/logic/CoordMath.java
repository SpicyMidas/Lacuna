package com.lacuna.mod.logic;

/**
 * Overworld &lt;-&gt; Forgotten Land coordinate translation (spec section 6). Pure Java, unit-tested.
 * <p>
 * Entering divides by the scale and floors toward negative infinity; leaving multiplies by the scale.
 * For integer block coordinates the round trip error is always in {@code [0, scale - 1]}.
 */
public final class CoordMath {
    private CoordMath() {}

    /** Overworld block coordinate to Forgotten Land block coordinate. */
    public static int toForgotten(int overworld, double scale) {
        checkScale(scale);
        return (int) Math.floor(overworld / scale);
    }

    /** Overworld precise coordinate to Forgotten Land block coordinate. */
    public static int toForgotten(double overworld, double scale) {
        checkScale(scale);
        return (int) Math.floor(Math.floor(overworld) / scale);
    }

    /** Forgotten Land block coordinate to Overworld block coordinate. */
    public static int toOverworld(int forgotten, double scale) {
        checkScale(scale);
        double v = forgotten * scale;
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (v < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) Math.floor(v);
    }

    /** Forgotten Land precise coordinate to Overworld block coordinate. */
    public static int toOverworld(double forgotten, double scale) {
        return toOverworld((int) Math.floor(forgotten), scale);
    }

    public static int clamp(int value, int min, int max) {
        if (min > max) {
            return value;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Coarse coordinates for chat messages (e.g. relic notices), rounded to the nearest {@code step}. */
    public static int coarse(int value, int step) {
        if (step <= 0) {
            return value;
        }
        return Math.round(value / (float) step) * step;
    }

    private static void checkScale(double scale) {
        if (!(scale >= 1.0) || Double.isInfinite(scale)) {
            throw new IllegalArgumentException("scale must be a finite number >= 1, got " + scale);
        }
    }
}
