package com.lacuna.mod.logic;

/** Divine Magnifying Glass beam modifiers (spec M3). Pure Java, unit-tested. */
public final class BeamMath {
    public static final int YELLOW = 0xFFD23C;
    public static final int BLUE = 0x3C8CFF;
    public static final int WHITE = 0xFFFFFF;

    public static final double UNDERWATER_DAMAGE = 0.4;
    public static final double UNDERWATER_RANGE = 0.5;
    public static final double NIGHT_DAMAGE = 0.5;

    private BeamMath() {}

    /** Resulting beam parameters for the current conditions. */
    public record Beam(double damageMultiplier, double rangeMultiplier, int color, boolean ignites, boolean underwater) {}

    public static Beam compute(boolean underwater, boolean night) {
        double damage = 1.0;
        double range = 1.0;
        int color = YELLOW;
        if (night) {
            damage *= NIGHT_DAMAGE;
            color = WHITE;
        }
        if (underwater) {
            damage *= UNDERWATER_DAMAGE;
            range *= UNDERWATER_RANGE;
            color = BLUE;
        }
        return new Beam(damage, range, color, !underwater, underwater);
    }

    /** Damage of one pulse when {@code perSecond} damage is split into pulses every {@code intervalTicks}. */
    public static float pulseDamage(double perSecond, int intervalTicks, double multiplier) {
        if (perSecond <= 0 || intervalTicks <= 0 || multiplier <= 0) {
            return 0.0F;
        }
        return (float) (perSecond * intervalTicks / 20.0 * multiplier);
    }

    /**
     * Night rule: the Forgotten Land (fixed afternoon) always counts as day; dimensions with a sky follow the
     * day/night cycle; dimensions without a sky count as night.
     */
    public static boolean isNight(boolean forgottenLand, boolean hasSkyLight, boolean isDay) {
        if (forgottenLand) {
            return false;
        }
        return !hasSkyLight || !isDay;
    }

    /** Durability spent so far for a beam that has been channelled for {@code ticks}. */
    public static boolean spendsDurability(int ticks, int ticksPerPoint) {
        return ticksPerPoint > 0 && ticks > 0 && ticks % ticksPerPoint == 0;
    }
}
