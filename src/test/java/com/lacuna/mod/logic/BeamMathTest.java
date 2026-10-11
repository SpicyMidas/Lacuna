package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BeamMathTest {
    @Test
    void dayIsFullYellow() {
        BeamMath.Beam b = BeamMath.compute(false, false);
        assertEquals(1.0, b.damageMultiplier(), 1e-9);
        assertEquals(1.0, b.rangeMultiplier(), 1e-9);
        assertEquals(BeamMath.YELLOW, b.color());
        assertTrue(b.ignites());
    }

    @Test
    void underwaterIsWeakShortBlueAndCold() {
        BeamMath.Beam b = BeamMath.compute(true, false);
        assertEquals(0.4, b.damageMultiplier(), 1e-9);
        assertEquals(0.5, b.rangeMultiplier(), 1e-9);
        assertEquals(BeamMath.BLUE, b.color());
        assertFalse(b.ignites());
    }

    @Test
    void nightIsHalfWhite() {
        BeamMath.Beam b = BeamMath.compute(false, true);
        assertEquals(0.5, b.damageMultiplier(), 1e-9);
        assertEquals(BeamMath.WHITE, b.color());
    }

    @Test
    void underwaterAtNightMultiplies() {
        BeamMath.Beam b = BeamMath.compute(true, true);
        assertEquals(0.2, b.damageMultiplier(), 1e-9);
        assertEquals(0.5, b.rangeMultiplier(), 1e-9);
        assertEquals(BeamMath.BLUE, b.color());
    }

    @Test
    void pulseDamageSplitsDps() {
        assertEquals(3.0F, BeamMath.pulseDamage(12.0, 5, 1.0), 1e-6);
        assertEquals(0.6F, BeamMath.pulseDamage(12.0, 5, 0.2), 1e-6);
        assertEquals(0.0F, BeamMath.pulseDamage(12.0, 0, 1.0), 1e-6);
    }

    @Test
    void forgottenLandCountsAsDay() {
        assertFalse(BeamMath.isNight(true, true, false));
        assertTrue(BeamMath.isNight(false, true, false));
        assertFalse(BeamMath.isNight(false, true, true));
        assertTrue(BeamMath.isNight(false, false, true));
    }

    @Test
    void durabilityEveryTwentyTicks() {
        assertFalse(BeamMath.spendsDurability(0, 20));
        assertFalse(BeamMath.spendsDurability(19, 20));
        assertTrue(BeamMath.spendsDurability(20, 20));
        assertTrue(BeamMath.spendsDurability(40, 20));
    }
}
