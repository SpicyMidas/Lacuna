package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class VillageBoundsTest {
    @Test
    void radiusIsHalfTheLongerSidePlusMarginClamped() {
        // 101 x 61 box: half the longer side (51) + margin 8
        assertEquals(59, VillageBounds.radius(0, 0, 100, 60));
        assertEquals(59, VillageBounds.radius(-50, 10, 50, 70));
        // tiny village -> minimum radius; enormous one -> maximum
        assertEquals(VillageBounds.MIN_RADIUS, VillageBounds.radius(0, 0, 9, 9));
        assertEquals(VillageBounds.MAX_RADIUS, VillageBounds.radius(0, 0, 400, 20));
    }

    @Test
    void centreIsTheMiddleOfTheBox() {
        assertEquals(50, VillageBounds.center(0, 100));
        assertEquals(-51, VillageBounds.center(-101, 0));
    }

    @Test
    void containsUsesRadiusAndVerticalBand() {
        assertTrue(VillageBounds.contains(0, 0, 40, 50, 120, 40, 64, 0));
        assertTrue(VillageBounds.contains(0, 0, 40, 50, 120, 28, 64, 28));   // 39.6 away
        assertFalse(VillageBounds.contains(0, 0, 40, 50, 120, 29, 64, 29));  // 41.0 away
        assertFalse(VillageBounds.contains(0, 0, 40, 50, 120, 0, 49, 0));    // below the band
        assertFalse(VillageBounds.contains(0, 0, 40, 50, 120, 0, 121, 0));   // above the band
    }

    @Test
    void indexCellsCoverTheCircle() {
        long[] cells = VillageBounds.cells(0, 0, 48);
        // x and z from -48 to 48 -> cells -1 and 0 on both axes
        assertEquals(4, cells.length);
        for (int x : new int[] {-48, -1, 0, 48}) {
            for (int z : new int[] {-48, -1, 0, 48}) {
                long c = VillageBounds.cell(x, z);
                assertTrue(Arrays.stream(cells).anyMatch(v -> v == c), "cell of " + x + "," + z);
            }
        }
        assertEquals(VillageBounds.cell(127, -1), VillageBounds.cell(0, -128));
        assertFalse(VillageBounds.cell(128, 0) == VillageBounds.cell(127, 0));
    }
}
