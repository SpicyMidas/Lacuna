package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MerchantPricingTest {
    @Test
    void familiesFromBlockIds() {
        assertEquals("iron", MerchantPricing.familyOf("deepslate_iron_ore"));
        assertEquals("gold", MerchantPricing.familyOf("nether_gold_ore"));
        assertEquals("quartz", MerchantPricing.familyOf("nether_quartz_ore"));
        assertEquals("umbral_steel", MerchantPricing.familyOf("deepslate_umbral_steel_ore"));
        assertEquals("ruby", MerchantPricing.familyOf("ruby_ore"));
        assertNull(MerchantPricing.familyOf("ancient_debris"));
    }

    @Test
    void topMinedFamilyGetsBulkOffer() {
        var offers = MerchantPricing.build(Map.of("copper", 300, "iron", 20), "minecraft:emerald", 1.0, new Random(1), false);
        assertEquals("minecraft:raw_copper", offers.get(0).costItem());
        assertTrue(offers.stream().anyMatch(o -> o.costItem().equals("minecraft:raw_copper") && o.costCount() == 48 && o.resultCount() == 5));
        assertTrue(offers.stream().anyMatch(o -> o.costItem().equals("minecraft:raw_iron")));
        assertFalse(offers.stream().anyMatch(o -> o.resultItem().equals("lacuna:solar_flare_fragment")));
    }

    @Test
    void sellsTheFourGoodsAndOptionallyAFragment() {
        var offers = MerchantPricing.build(Map.of(), "minecraft:emerald", 1.0, new Random(7), true);
        for (String good : List.of("lacuna:divine_glass_shard", "lacuna:ruby", "lacuna:chronal_shard", "minecraft:echo_shard")) {
            assertTrue(offers.stream().anyMatch(o -> o.resultItem().equals(good)), good);
        }
        var fragment = offers.stream().filter(o -> o.resultItem().equals("lacuna:solar_flare_fragment")).findFirst().orElseThrow();
        assertEquals(16, fragment.costCount());
        assertEquals(24, fragment.cost2Count());
        assertEquals(1, fragment.maxUses());
        assertFalse(offers.stream().anyMatch(o -> o.costCount() > 64 || o.costCount() < 1));
    }

    @Test
    void priceIndexRaisesSellPrices() {
        assertEquals(3, MerchantPricing.scaled(2, 1.5));
        assertEquals(2, MerchantPricing.scaled(2, 1.0));
        assertEquals(1, MerchantPricing.scaled(1, 0.1));
    }
}
