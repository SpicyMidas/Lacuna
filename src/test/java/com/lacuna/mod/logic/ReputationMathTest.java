package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReputationMathTest {
    @Test
    void tiersFollowTheSpecBoundaries() {
        assertEquals(ReputationMath.Tier.HOSTILE, ReputationMath.tier(-51));
        assertEquals(ReputationMath.Tier.WARY, ReputationMath.tier(-50));
        assertEquals(ReputationMath.Tier.WARY, ReputationMath.tier(-10));
        assertEquals(ReputationMath.Tier.NEUTRAL, ReputationMath.tier(-9));
        assertEquals(ReputationMath.Tier.NEUTRAL, ReputationMath.tier(9));
        assertEquals(ReputationMath.Tier.FRIENDLY, ReputationMath.tier(10));
        assertEquals(ReputationMath.Tier.FRIENDLY, ReputationMath.tier(49));
        assertEquals(ReputationMath.Tier.HONORED, ReputationMath.tier(50));
    }

    @Test
    void clampAndPrices() {
        assertEquals(100, ReputationMath.clamp(250));
        assertEquals(-100, ReputationMath.clamp(-250));
        assertEquals(0.75, ReputationMath.priceMultiplier(100), 1e-9);
        assertEquals(1.25, ReputationMath.priceMultiplier(-100), 1e-9);
        assertEquals(1.0, ReputationMath.priceMultiplier(0), 1e-9);
    }

    @Test
    void dailyCapsLimitChanges() {
        assertEquals(1, ReputationMath.allowed(1, 1, 2));
        assertEquals(0, ReputationMath.allowed(1, 2, 2));
        assertEquals(-5, ReputationMath.allowed(-5, 5, 15));
        assertEquals(-5, ReputationMath.allowed(-5, 10, 15));
        assertEquals(0, ReputationMath.allowed(-5, 15, 15));
        assertEquals(-20, ReputationMath.allowed(-20, 400, 0));
        assertEquals(1, ReputationMath.allowed(3, 9, 10));
    }

    @Test
    void festivalDoublesOnlyGains() {
        assertEquals(6, ReputationMath.apply(0, 3, 2.0));
        assertEquals(-3, ReputationMath.apply(0, -3, 2.0));
        assertEquals(100, ReputationMath.apply(99, 3, 1.0));
    }

    @Test
    void finesRestoreUpToZero() {
        assertEquals(3, ReputationMath.coinsForFine(-12, 10));
        assertEquals(2, ReputationMath.coinsForFine(-12, 2));
        assertEquals(0, ReputationMath.coinsForFine(5, 10));
    }

    @Test
    void lawsAreTwoDistinctAndDeterministic() {
        boolean[] seen = new boolean[VillageLaws.COUNT];
        for (long seed = -500; seed < 500; seed++) {
            int[] laws = VillageLaws.choose(seed);
            assertEquals(2, laws.length);
            assertNotEquals(laws[0], laws[1]);
            assertTrue(laws[0] >= 0 && laws[0] < 4 && laws[1] >= 0 && laws[1] < 4);
            assertEquals(laws[0], VillageLaws.choose(seed)[0]);
            seen[laws[0]] = true;
            seen[laws[1]] = true;
        }
        for (boolean s : seen) {
            assertTrue(s);
        }
        assertTrue(VillageLaws.isDawn(24000 * 3 + 100));
        assertTrue(!VillageLaws.isDawn(6000));
    }

    @Test
    void curfewRunsFromDuskToDawnForStrangersOnly() {
        assertTrue(!VillageLaws.isCurfew(12999));
        assertTrue(VillageLaws.isCurfew(13000));
        assertTrue(VillageLaws.isCurfew(24000 * 5 + 18000));
        assertTrue(VillageLaws.isCurfew(22999));
        assertTrue(!VillageLaws.isCurfew(23000));
        assertTrue(!VillageLaws.isCurfew(1000));
        assertTrue(VillageLaws.isStranger(9));
        assertTrue(!VillageLaws.isStranger(10));
    }

    @Test
    void villageSeedsDependOnWorldAndStart() {
        long a = VillageLaws.villageSeed(42L, 1234L);
        assertEquals(a, VillageLaws.villageSeed(42L, 1234L));
        assertNotEquals(a, VillageLaws.villageSeed(43L, 1234L));
        assertNotEquals(a, VillageLaws.villageSeed(42L, 1235L));
    }
}
