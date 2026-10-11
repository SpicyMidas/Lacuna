package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoordMathTest {
    private static final double SCALE = 12.0;

    @Test
    void entersByFlooringDivision() {
        assertEquals(0, CoordMath.toForgotten(0, SCALE));
        assertEquals(0, CoordMath.toForgotten(11, SCALE));
        assertEquals(1, CoordMath.toForgotten(12, SCALE));
        assertEquals(-1, CoordMath.toForgotten(-1, SCALE));
        assertEquals(-1, CoordMath.toForgotten(-12, SCALE));
        assertEquals(-2, CoordMath.toForgotten(-13, SCALE));
        assertEquals(83333, CoordMath.toForgotten(1_000_000, SCALE));
    }

    @Test
    void preciseCoordinatesFloorFirst() {
        assertEquals(-1, CoordMath.toForgotten(-0.5, SCALE));
        assertEquals(0, CoordMath.toForgotten(11.99, SCALE));
        assertEquals(2, CoordMath.toOverworld(2.7, 1.0));
    }

    @Test
    void leavesByMultiplying() {
        assertEquals(0, CoordMath.toOverworld(0, SCALE));
        assertEquals(120, CoordMath.toOverworld(10, SCALE));
        assertEquals(-120, CoordMath.toOverworld(-10, SCALE));
    }

    @Test
    void roundTripErrorIsBounded() {
        for (int x = -10_000; x <= 10_000; x++) {
            int back = CoordMath.toOverworld(CoordMath.toForgotten(x, SCALE), SCALE);
            int error = x - back;
            assertTrue(error >= 0 && error <= 11, "x=" + x + " back=" + back);
        }
    }

    @Test
    void forgottenRoundTripIsExact() {
        for (int f = -2000; f <= 2000; f++) {
            assertEquals(f, CoordMath.toForgotten(CoordMath.toOverworld(f, SCALE), SCALE));
        }
    }

    @Test
    void extremeValuesSaturate() {
        assertEquals(Integer.MAX_VALUE, CoordMath.toOverworld(Integer.MAX_VALUE, SCALE));
        assertEquals(Integer.MIN_VALUE, CoordMath.toOverworld(Integer.MIN_VALUE, SCALE));
    }

    @Test
    void rejectsBadScale() {
        assertThrows(IllegalArgumentException.class, () -> CoordMath.toForgotten(5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> CoordMath.toForgotten(5, Double.NaN));
    }

    @Test
    void clampAndCoarse() {
        assertEquals(5, CoordMath.clamp(5, 0, 10));
        assertEquals(0, CoordMath.clamp(-5, 0, 10));
        assertEquals(10, CoordMath.clamp(50, 0, 10));
        assertEquals(1200, CoordMath.coarse(1234, 100));
        assertEquals(-1200, CoordMath.coarse(-1234, 100));
    }
}
