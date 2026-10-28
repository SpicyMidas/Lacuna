package com.lacuna.mod.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
        var offers = MerchantPricing.build(Map.of("copper", 300, "iron", 20), "lacuna:ruby", 1.0, new Random(1), false);
        assertEquals("minecraft:raw_copper", offers.get(0).costItem());
        assertTrue(offers.stream().anyMatch(o -> o.costItem().equals("minecraft:raw_copper") && o.costCount() == 64 && o.resultCount() == 5));
        assertTrue(offers.stream().anyMatch(o -> o.costItem().equals("minecraft:raw_iron")));
        assertFalse(offers.stream().anyMatch(o -> o.resultItem().equals("lacuna:solar_flare_fragment")));
    }

    @Test
    void sellsItsCatalogForRubiesAndOptionallyAFragment() {
        var offers = MerchantPricing.build(Map.of(), "lacuna:ruby", 1.0, new Random(7), true);
        assertFalse(offers.stream().anyMatch(o -> o.costItem().equals("lacuna:ruby") && o.resultItem().equals("lacuna:ruby")));
        assertTrue(offers.stream().allMatch(o -> o.costItem().equals("lacuna:ruby") || o.resultItem().equals("lacuna:ruby")));
        for (String good : List.of("lacuna:divine_glass_shard", "lacuna:chronal_shard", "minecraft:echo_shard")) {
            assertTrue(offers.stream().anyMatch(o -> o.resultItem().equals(good)), good);
        }
        var fragment = offers.stream().filter(o -> o.resultItem().equals("lacuna:solar_flare_fragment")).findFirst().orElseThrow();
        assertEquals(16, fragment.costCount());
        assertEquals(8, fragment.cost2Count());
        assertEquals(1, fragment.maxUses());
        assertFalse(offers.stream().anyMatch(o -> o.costCount() > 64 || o.costCount() < 1));
    }

    @Test
    void bringsTwoRandomRelicsAndTheSunsFuryForgeForRubiesButNeverATotem() {
        var relicIds = java.util.Arrays.stream(MerchantPricing.RELICS).map(r -> (String) r[0]).toList();
        var offers = MerchantPricing.build(Map.of(), "lacuna:ruby", 1.0, new Random(3), false, false);
        var relics = offers.stream().filter(o -> relicIds.contains(o.resultItem())).toList();
        assertEquals(MerchantPricing.RELIC_OFFERS, relics.size());
        assertEquals(relics.size(), relics.stream().map(MerchantPricing.Offer::resultItem).distinct().count());
        assertTrue(relics.stream().allMatch(o -> o.costItem().equals("lacuna:ruby") && o.maxUses() == 1));
        var forge = offers.stream().filter(o -> o.resultItem().equals("lacuna:suns_fury")).findFirst().orElseThrow();
        assertEquals("lacuna:solar_flare", forge.cost2Item());
        assertEquals(MerchantPricing.SUNS_FURY_FORGE_PRICE, forge.costCount());
        assertFalse(offers.stream().anyMatch(o -> o.resultItem().startsWith("lacuna:totem_of")
                || o.resultItem().equals("minecraft:totem_of_undying") || o.resultItem().equals("lacuna:lacunar_quintessence")));
        assertFalse(offers.stream().anyMatch(o -> o.costCount() > 64 || o.cost2Count() > 64));
    }

    @Test
    void everyRelicComesRoundInTime() {
        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 300; seed++) {
            MerchantPricing.build(Map.of(), "lacuna:ruby", 1.0, new Random(seed), false, false)
                    .forEach(o -> seen.add(o.resultItem()));
        }
        for (Object[] relic : MerchantPricing.RELICS) {
            assertTrue(seen.contains((String) relic[0]), (String) relic[0]);
        }
    }

    @Test
    void dearOffersArePaidPartlyInRubyBlocks() {
        assertArrayEquals(new int[] {64, 2}, MerchantPricing.split(82, 1.0, "lacuna:ruby"));
        assertArrayEquals(new int[] {64, 4}, MerchantPricing.split(100, 1.0, "lacuna:ruby"));
        assertArrayEquals(new int[] {41, 0}, MerchantPricing.split(82, 0.5, "lacuna:ruby"));
        assertArrayEquals(new int[] {64, 0}, MerchantPricing.split(100, 1.0, "minecraft:emerald"));
        var offers = MerchantPricing.build(Map.of(), "lacuna:ruby", 1.0, new Random(5), false, true);
        var essence = offers.stream().filter(o -> o.resultItem().equals("lacuna:lacunar_quintessence")).findFirst().orElseThrow();
        assertEquals(64, essence.costCount());
        assertEquals("lacuna:ruby_block", essence.cost2Item());
        assertEquals(4, essence.cost2Count());
        assertEquals(1, essence.maxUses());
    }

    @Test
    void priceIndexRaisesSellPrices() {
        assertEquals(3, MerchantPricing.scaled(2, 1.5));
        assertEquals(2, MerchantPricing.scaled(2, 1.0));
        assertEquals(1, MerchantPricing.scaled(1, 0.1));
    }
}
