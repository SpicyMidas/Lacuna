package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VillageLawsTest {
    @Test
    void lawsAreTwoDistinctAndDeterministic() {
        boolean[] seen = new boolean[VillageLaws.COUNT];
        for (long seed = -500; seed < 500; seed++) {
            int[] laws = VillageLaws.choose(seed);
            assertEquals(2, laws.length);
            assertNotEquals(laws[0], laws[1]);
            assertTrue(laws[0] >= 0 && laws[0] < 4 && laws[1] >= 0 && laws[1] < 4);
            assertEquals(laws[0], VillageLaws.choose(seed)[0]);
            assertEquals(laws[1], VillageLaws.choose(seed)[1]);
            seen[laws[0]] = true;
            seen[laws[1]] = true;
        }
        for (boolean s : seen) {
            assertTrue(s);
        }
    }

    @Test
    void lawsAreSpreadAcrossVillageStarts() {
        int[] count = new int[VillageLaws.COUNT];
        for (long chunk = 0; chunk < 400; chunk++) {
            for (int law : VillageLaws.choose(VillageLaws.villageSeed(12345L, chunk * 0x10001L))) {
                count[law]++;
            }
        }
        for (int c : count) {
            assertTrue(c > 120, "every law is common: " + java.util.Arrays.toString(count));
        }
    }

    @Test
    void dawnIsTheFirstTwoThousandTicks() {
        assertTrue(VillageLaws.isDawn(24000 * 3 + 100));
        assertTrue(VillageLaws.isDawn(2000));
        assertFalse(VillageLaws.isDawn(2001));
        assertFalse(VillageLaws.isDawn(6000));
    }

    @Test
    void curfewRunsFromDuskToDawnForStrangersOnly() {
        assertFalse(VillageLaws.isCurfew(12999));
        assertTrue(VillageLaws.isCurfew(13000));
        assertTrue(VillageLaws.isCurfew(24000 * 5 + 18000));
        assertTrue(VillageLaws.isCurfew(22999));
        assertFalse(VillageLaws.isCurfew(23000));
        assertFalse(VillageLaws.isCurfew(1000));
        assertTrue(VillageLaws.isStranger(9));
        assertTrue(VillageLaws.isStranger(-40));
        assertFalse(VillageLaws.isStranger(10));
    }

    @Test
    void villageSeedsDependOnWorldAndStart() {
        long a = VillageLaws.villageSeed(42L, 1234L);
        assertEquals(a, VillageLaws.villageSeed(42L, 1234L));
        assertNotEquals(a, VillageLaws.villageSeed(43L, 1234L));
        assertNotEquals(a, VillageLaws.villageSeed(42L, 1235L));
    }

    @Test
    void bellRulesFollowTheSpec() {
        assertEquals(VillageLaws.Bell.DAWN_BONUS, VillageLaws.bell(1, 500, false));
        assertEquals(VillageLaws.Bell.DAWN_BONUS, VillageLaws.bell(3, 24000 + 1500, true));
        assertEquals(VillageLaws.Bell.ANNOYED, VillageLaws.bell(4, 500, false));
        assertEquals(VillageLaws.Bell.ANNOYED, VillageLaws.bell(4, 9000, true));
        assertEquals(VillageLaws.Bell.NOTHING, VillageLaws.bell(1, 9000, false));
        assertEquals(VillageLaws.Bell.LAW_BROKEN, VillageLaws.bell(2, 9000, true));
    }
}
