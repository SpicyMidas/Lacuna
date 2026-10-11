package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ObeliskRulesTest {
    @Test
    void loreIndexIsStableInRangeAndSpread() {
        Set<Integer> seen = new HashSet<>();
        for (int x = -200; x <= 200; x += 7) {
            for (int z = -200; z <= 200; z += 11) {
                int i = ObeliskRules.loreIndex(x, 64, z, 24);
                assertTrue(i >= 0 && i < 24);
                assertEquals(i, ObeliskRules.loreIndex(x, 64, z, 24));
                seen.add(i);
            }
        }
        assertEquals(24, seen.size(), "every line should be reachable");
    }

    @Test
    void rewardKindsCoverAllRolls() {
        assertEquals(0, ObeliskRules.rewardKind(0.0));
        assertEquals(1, ObeliskRules.rewardKind(0.5));
        assertEquals(2, ObeliskRules.rewardKind(0.99));
    }

    @Test
    void rewardIsOncePerPlayerAndRespectsCooldown() {
        assertTrue(ObeliskRules.mayReward(false, 100, 0));
        assertFalse(ObeliskRules.mayReward(true, 100, 0));
        assertFalse(ObeliskRules.mayReward(false, 100, 200));
    }
}
