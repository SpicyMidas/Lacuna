package com.lacuna.mod.logic;

/**
 * Sun's Fury timing and curves (spec 11.3, revision 2): trident-style throw strength, the focus phase that sweeps the
 * day time to the next noon, the strike/fade phases of the solar column, and the easing/attenuation curves the
 * client effects share. Pure Java, unit-tested.
 */
public final class SolarMath {
    public static final long DAY = 24000L;
    public static final long NOON = 6000L;

    /** Phases of the solar column after the focus ends. */
    public enum RayPhase { STRIKE, FADE, DONE }

    private SolarMath() {}

    public static double clamp01(double t) {
        return t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
    }

    /** Smootherstep ease in-out: 0 at 0, 1 at 1, zero slope at both ends, monotonic. */
    public static double ease(double t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** Ease out (fast start, gentle stop), for expanding rings. */
    public static double easeOut(double t) {
        t = 1.0 - clamp01(t);
        return 1.0 - t * t * t;
    }

    /** {@code age / duration} clamped to 0..1 (a non-positive duration counts as finished). */
    public static double progress(double age, double duration) {
        return duration <= 0 ? 1.0 : clamp01(age / duration);
    }

    /**
     * The noon the sun is pulled to: the current day's noon if it is still ahead (or now), otherwise the next day's.
     * Never earlier than {@code dayTime}, so the day count is kept and time never runs backwards.
     */
    public static long nextNoon(long dayTime) {
        long noon = Math.floorDiv(dayTime, DAY) * DAY + NOON;
        return noon < dayTime ? noon + DAY : noon;
    }

    /** Eased day time at focus progress {@code t} (0..1) from {@code start} to {@code target}; never below start. */
    public static long sweep(long start, long target, double t) {
        if (target <= start) {
            return start;
        }
        return start + Math.round((target - start) * ease(t));
    }

    /** The day time to apply this tick: the sweep value, but never earlier than the current time. */
    public static long nextDayTime(long current, long start, long target, double t) {
        return Math.max(current, sweep(start, target, t));
    }

    /**
     * Trident-style throw: below {@code minCharge} ticks of drawing nothing is thrown (0); from there the speed
     * grows linearly from 55% to 100% of {@code maxSpeed}, reached at {@code fullCharge} ticks.
     */
    public static float throwSpeed(int chargeTicks, int minCharge, int fullCharge, float maxSpeed) {
        if (chargeTicks < minCharge || maxSpeed <= 0.0F) {
            return 0.0F;
        }
        double t = fullCharge <= minCharge ? 1.0 : clamp01((chargeTicks - minCharge) / (double) (fullCharge - minCharge));
        return (float) (maxSpeed * (0.55 + 0.45 * t));
    }

    /** Phase of the solar column {@code age} ticks after it appeared. */
    public static RayPhase rayPhase(int age, int strikeTicks, int fadeTicks) {
        if (age <= strikeTicks) {
            return RayPhase.STRIKE;
        }
        return age <= strikeTicks + fadeTicks ? RayPhase.FADE : RayPhase.DONE;
    }

    /**
     * Brightness of the column (0..1) at {@code age} (fractional ticks): a quick 4-tick swell, full during the
     * strike, then an eased fade to 0 over {@code fadeTicks}.
     */
    public static double columnIntensity(double age, int strikeTicks, int fadeTicks) {
        if (age < 0) {
            return 0.0;
        }
        if (age <= strikeTicks) {
            return ease(age / 4.0);
        }
        return 1.0 - ease((age - strikeTicks) / Math.max(1.0, fadeTicks));
    }

    /** Linear interpolation. */
    public static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    /** 1 within {@code full} blocks, 0 beyond {@code zero} blocks, eased in between. */
    public static double attenuation(double distance, double full, double zero) {
        if (distance <= full) {
            return 1.0;
        }
        if (distance >= zero || zero <= full) {
            return 0.0;
        }
        return 1.0 - ease((distance - full) / (zero - full));
    }

    /**
     * Vanilla sky-darkness rule ({@code Level.updateSkyBrightness}) for a celestial angle {@code timeOfDay} (0..1) and
     * rain/thunder levels; it is day while the result is below 4. Clients need it because their cached value is stale.
     */
    public static int skyDarken(float timeOfDay, float rain, float thunder) {
        double rainFactor = 1.0 - rain * 5.0F / 16.0;
        double thunderFactor = 1.0 - thunder * 5.0F / 16.0;
        double sun = 0.5 + 2.0 * Math.max(-0.25, Math.min(0.25, Math.cos(timeOfDay * Math.PI * 2.0)));
        return (int) ((1.0 - sun * rainFactor * thunderFactor) * 11.0);
    }
}
