package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SolarMathTest {
    @Test
    void nextNoonIsNeverEarlierAndKeepsTheDay() {
        assertEquals(6000L, SolarMath.nextNoon(0L));
        assertEquals(6000L, SolarMath.nextNoon(5999L));
        assertEquals(6000L, SolarMath.nextNoon(6000L)); // already noon: stay
        assertEquals(30000L, SolarMath.nextNoon(6001L));
        assertEquals(30000L, SolarMath.nextNoon(18000L)); // midnight -> next day's noon
        assertEquals(24000L * 7 + 6000L, SolarMath.nextNoon(24000L * 7 - 100L));
        for (long t = 0; t < 3 * 24000L; t += 137) {
            long noon = SolarMath.nextNoon(t);
            assertTrue(noon >= t, "never backwards at " + t);
            assertTrue(noon - t < 24000L, "at most one day ahead at " + t);
            assertEquals(6000L, Math.floorMod(noon, 24000L));
        }
    }

    @Test
    void sweepIsEasedMonotonicAndLandsExactlyOnTarget() {
        long start = 18000L;
        long target = SolarMath.nextNoon(start);
        assertEquals(start, SolarMath.sweep(start, target, 0.0));
        assertEquals(target, SolarMath.sweep(start, target, 1.0));
        long previous = start;
        for (int i = 0; i <= 100; i++) {
            long now = SolarMath.sweep(start, target, i / 100.0);
            assertTrue(now >= previous, "monotonic at step " + i);
            previous = now;
        }
        // eased: slow at the start, fastest in the middle
        long first = SolarMath.sweep(start, target, 0.01) - start;
        long middle = SolarMath.sweep(start, target, 0.51) - SolarMath.sweep(start, target, 0.50);
        assertTrue(first < middle, "ease-in: " + first + " vs " + middle);
    }

    @Test
    void sweepNeverRunsBackwards() {
        assertEquals(5000L, SolarMath.sweep(5000L, 4000L, 0.7));
        // the current time wins when something else already moved it further
        assertEquals(29000L, SolarMath.nextDayTime(29000L, 18000L, 30000L, 0.2));
        assertEquals(30000L, SolarMath.nextDayTime(29999L, 18000L, 30000L, 1.0));
    }

    @Test
    void throwNeedsTenTicksAndScalesToMaxSpeed() {
        assertEquals(0.0F, SolarMath.throwSpeed(9, 10, 25, 2.5F));
        assertEquals(2.5F * 0.55F, SolarMath.throwSpeed(10, 10, 25, 2.5F), 1e-5);
        assertEquals(2.5F, SolarMath.throwSpeed(25, 10, 25, 2.5F), 1e-5);
        assertEquals(2.5F, SolarMath.throwSpeed(200, 10, 25, 2.5F), 1e-5);
        assertTrue(SolarMath.throwSpeed(17, 10, 25, 2.5F) > SolarMath.throwSpeed(12, 10, 25, 2.5F));
    }

    @Test
    void rayPhases() {
        assertEquals(SolarMath.RayPhase.STRIKE, SolarMath.rayPhase(1, 60, 20));
        assertEquals(SolarMath.RayPhase.STRIKE, SolarMath.rayPhase(60, 60, 20));
        assertEquals(SolarMath.RayPhase.FADE, SolarMath.rayPhase(61, 60, 20));
        assertEquals(SolarMath.RayPhase.FADE, SolarMath.rayPhase(80, 60, 20));
        assertEquals(SolarMath.RayPhase.DONE, SolarMath.rayPhase(81, 60, 20));
    }

    @Test
    void columnSwellsHoldsAndFades() {
        assertEquals(0.0, SolarMath.columnIntensity(0, 60, 20), 1e-9);
        assertEquals(1.0, SolarMath.columnIntensity(30, 60, 20), 1e-9);
        assertEquals(1.0, SolarMath.columnIntensity(60, 60, 20), 1e-9);
        assertTrue(SolarMath.columnIntensity(70, 60, 20) < 1.0);
        assertEquals(0.0, SolarMath.columnIntensity(80, 60, 20), 1e-9);
    }

    @Test
    void attenuationFallsOffSmoothly() {
        assertEquals(1.0, SolarMath.attenuation(5, 10, 100), 1e-9);
        assertEquals(0.0, SolarMath.attenuation(150, 10, 100), 1e-9);
        double mid = SolarMath.attenuation(55, 10, 100);
        assertTrue(mid > 0.3 && mid < 0.7, "mid " + mid);
    }

    @Test
    void skyDarkenMatchesVanillaDayNight() {
        // noon (celestial angle 0) is bright, midnight (0.5) is dark; rain darkens
        assertTrue(SolarMath.skyDarken(0.0F, 0.0F, 0.0F) < 4);
        assertTrue(SolarMath.skyDarken(0.5F, 0.0F, 0.0F) >= 4);
        assertTrue(SolarMath.skyDarken(0.0F, 1.0F, 1.0F) > SolarMath.skyDarken(0.0F, 0.0F, 0.0F));
    }
}
