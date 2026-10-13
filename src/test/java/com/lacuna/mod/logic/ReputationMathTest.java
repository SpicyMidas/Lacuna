package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
