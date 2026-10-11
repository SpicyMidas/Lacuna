package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CouncilMathTest {
    @Test
    void goodReputationFavoursFreeMarketAndFestival() {
        Map<String, Integer> counts = new HashMap<>();
        Random r = new Random(42);
        for (int i = 0; i < 4000; i++) {
            counts.merge(CouncilMath.choose(75.0, r), 1, Integer::sum);
        }
        assertTrue(counts.get("free_market") > 2 * counts.get("austerity"));
        assertTrue(counts.get("festival") > 2 * counts.get("martial_law"));
    }

    @Test
    void badReputationFavoursAusterityAndMartialLaw() {
        Map<String, Integer> counts = new HashMap<>();
        Random r = new Random(7);
        for (int i = 0; i < 4000; i++) {
            counts.merge(CouncilMath.choose(-75.0, r), 1, Integer::sum);
        }
        assertTrue(counts.get("martial_law") > 2 * counts.get("festival"));
    }

    @Test
    void coinValueDropsWithDeliveriesAndRecovers() {
        assertEquals(0.98, CouncilMath.afterDelivery(1.0, 0, 16), 1e-9);
        assertEquals(1.0, CouncilMath.afterDelivery(1.0, 0, 15), 1e-9);
        assertEquals(0.98 * 0.98, CouncilMath.afterDelivery(1.0, 10, 40), 1e-9);
        assertEquals(0.6, CouncilMath.afterDelivery(0.61, 0, 160), 1e-9);
        assertEquals(0.99, CouncilMath.recover(0.98), 1e-9);
        assertEquals(1.0, CouncilMath.recover(1.005), 1e-9);
        assertEquals(1.19, CouncilMath.recover(1.2), 1e-9);
    }
}
