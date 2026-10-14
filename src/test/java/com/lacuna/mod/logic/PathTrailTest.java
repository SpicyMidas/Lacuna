package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathTrailTest {
    @Test
    void recordsEveryIntervalOnly() {
        PathTrail t = new PathTrail(200, 3600, 18);
        assertTrue(t.record(0, 64, 0, 0));
        assertFalse(t.record(1, 64, 0, 199));
        assertTrue(t.record(2, 64, 0, 200));
        assertEquals(2, t.points().size());
    }

    @Test
    void windowAndCapacityAreBounded() {
        PathTrail t = new PathTrail(200, 3600, 18);
        for (int i = 0; i <= 40; i++) {
            t.record(i, 64, 0, i * 200L);
        }
        assertTrue(t.points().size() <= 18);
        long newest = t.points().get(t.points().size() - 1).time();
        assertTrue(t.points().stream().allMatch(p -> newest - p.time() <= 3600));
    }

    @Test
    void followsFromOldestNearbyPoint() {
        PathTrail t = new PathTrail(1, 10_000, 18);
        t.record(0, 0, 0, 0);
        t.record(10, 0, 0, 1);
        t.record(20, 0, 0, 2);
        assertEquals(1, t.firstPointNear(12, 0, 0, 5));
        assertEquals(-1, t.firstPointNear(100, 0, 0, 5));
    }

    @Test
    void breakingForgetsOlderPoints() {
        PathTrail t = new PathTrail(1, 10_000, 18);
        for (int i = 0; i < 5; i++) {
            t.record(i, 0, 0, i);
        }
        t.breakAt(2);
        assertEquals(2, t.points().size());
        assertEquals(3.0, t.points().get(0).x(), 1e-9);
        t.breakAt(-1);
        assertEquals(2, t.points().size());
    }

    @Test
    void newestNearbyAndTimeLookup() {
        PathTrail t = new PathTrail(1, 10_000, 18);
        t.record(0, 0, 0, 10);
        t.record(2, 0, 0, 20);
        t.record(50, 0, 0, 30);
        assertEquals(1, t.lastPointNear(1, 0, 0, 5));
        assertEquals(-1, t.lastPointNear(200, 0, 0, 5));
        assertEquals(1, t.firstAfter(10));
        assertEquals(0, t.firstAfter(Long.MIN_VALUE));
        assertEquals(-1, t.firstAfter(30));
    }

    @Test
    void replaysInterpolatedPositions() {
        PathTrail t = new PathTrail(5, 120, 40);
        t.record(0, 64, 0, 100);
        t.record(10, 64, 0, 105);
        t.record(10, 64, 20, 110);
        assertTrue(t.positionAt(99).isEmpty());
        assertEquals(0.0, t.positionAt(100).orElseThrow()[0], 1e-9);
        assertEquals(5.0, t.positionAt(102).orElseThrow()[0] + 1.0, 1e-9); // 4 + 1
        assertEquals(10.0, t.positionAt(107).orElseThrow()[0], 1e-9);
        assertEquals(8.0, t.positionAt(107).orElseThrow()[2], 1e-9);
        assertEquals(20.0, t.positionAt(500).orElseThrow()[2], 1e-9);
    }
}
