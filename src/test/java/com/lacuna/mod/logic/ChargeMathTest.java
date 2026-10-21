package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChargeMathTest {
    @Test
    void hitsAddTenUpToHundred() {
        assertEquals(10, ChargeMath.afterHit(0));
        assertEquals(100, ChargeMath.afterHit(95));
        assertEquals(100, ChargeMath.afterHit(100));
    }

    @Test
    void hitRateLimited() {
        assertTrue(ChargeMath.canGain(100, 0));
        assertFalse(ChargeMath.canGain(104, 100));
        assertTrue(ChargeMath.canGain(105, 100));
    }

    @Test
    void decayStartsAfterEightSeconds() {
        assertEquals(50, ChargeMath.afterDecaySecond(50, 1160, 1000));
        assertEquals(48, ChargeMath.afterDecaySecond(50, 1161, 1000));
        assertEquals(0, ChargeMath.afterDecaySecond(1, 5000, 1000));
        assertEquals(0, ChargeMath.afterDecaySecond(0, 5000, 1000));
    }

    @Test
    void dashFormulas() {
        assertFalse(ChargeMath.canDash(19));
        assertTrue(ChargeMath.canDash(20));
        assertEquals(6.0, ChargeMath.dashDistance(20, 14.0), 1e-9);
        assertEquals(14.0, ChargeMath.dashDistance(100, 14.0), 1e-9);
        assertEquals(10.0, ChargeMath.dashDistance(100, 10.0), 1e-9);
        assertEquals(15.6F, ChargeMath.dashDamage(20), 1e-5);
        assertEquals(30.0F, ChargeMath.dashDamage(100), 1e-5);
        assertEquals(8, ChargeMath.durabilityCost(20));
        assertEquals(20, ChargeMath.durabilityCost(100));
        assertEquals(56, ChargeMath.shadowWalkTicks(20));
        assertEquals(120, ChargeMath.shadowWalkTicks(100));
    }

    @Test
    void textureStages() {
        assertEquals(0, ChargeMath.stage(0));
        assertEquals(1, ChargeMath.stage(1));
        assertEquals(1, ChargeMath.stage(33));
        assertEquals(2, ChargeMath.stage(34));
        assertEquals(2, ChargeMath.stage(66));
        assertEquals(3, ChargeMath.stage(67));
        assertEquals(3, ChargeMath.stage(100));
    }

    @Test
    void segmentDistance() {
        assertEquals(1.0, ChargeMath.segmentDistance(5, 1, 0, 0, 0, 0, 10, 0, 0), 1e-9);
        assertEquals(5.0, ChargeMath.segmentDistance(-3, 4, 0, 0, 0, 0, 10, 0, 0), 1e-9);
        assertEquals(2.0, ChargeMath.segmentDistance(12, 0, 0, 0, 0, 0, 10, 0, 0), 1e-9);
        assertEquals(1.0, ChargeMath.segmentDistance(0, 1, 0, 0, 0, 0, 0, 0, 0), 1e-9);
    }
}
