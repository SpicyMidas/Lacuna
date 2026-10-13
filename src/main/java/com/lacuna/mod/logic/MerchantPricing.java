package com.lacuna.mod.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Last Merchant stock (spec M10), pure and unit-tested. The merchant buys raw ores/ingots for currency and sells
 * divine glass shards, rubies, chronal shards and echo shards. The ore families the nearest player mined most get
 * buy offers, and the top one an extra "bulk buy". Sell prices scale with the nearest village's price index (M11d).
 */
public final class MerchantPricing {
    /** An offer: {@code costItem x costCount (+ cost2) -> result x resultCount}, ids are "namespace:path". */
    public record Offer(String costItem, int costCount, String cost2Item, int cost2Count, String resultItem, int resultCount,
                        int maxUses) {
        public boolean hasSecondCost() {
            return cost2Item != null && cost2Count > 0;
        }
    }

    /** family -> raw item sold to the merchant and how many of it buy one currency. */
    public static final Map<String, Object[]> FAMILIES = new LinkedHashMap<>();
    /** ingots and gems the merchant also buys: item -> count per currency. */
    public static final Map<String, Integer> INGOTS = new LinkedHashMap<>();

    static {
        FAMILIES.put("iron", new Object[] {"minecraft:raw_iron", 6});
        FAMILIES.put("copper", new Object[] {"minecraft:raw_copper", 12});
        FAMILIES.put("gold", new Object[] {"minecraft:raw_gold", 4});
        FAMILIES.put("coal", new Object[] {"minecraft:coal", 15});
        FAMILIES.put("redstone", new Object[] {"minecraft:redstone", 16});
        FAMILIES.put("lapis", new Object[] {"minecraft:lapis_lazuli", 12});
        FAMILIES.put("diamond", new Object[] {"minecraft:diamond", 1});
        FAMILIES.put("quartz", new Object[] {"minecraft:quartz", 12});
        FAMILIES.put("ruby", new Object[] {"lacuna:raw_ruby", 3});
        FAMILIES.put("umbral_steel", new Object[] {"lacuna:raw_umbral_steel", 3});
        FAMILIES.put("zenithite", new Object[] {"lacuna:zenithite_shard", 4});
        INGOTS.put("minecraft:iron_ingot", 5);
        INGOTS.put("minecraft:gold_ingot", 3);
        INGOTS.put("minecraft:copper_ingot", 10);
        INGOTS.put("lacuna:umbral_steel_ingot", 2);
        INGOTS.put("lacuna:ruby", 2);
    }

    private MerchantPricing() {}

    /** Ore family of a block id path ("deepslate_iron_ore" -> "iron"), or null if it is not a known family. */
    public static String familyOf(String blockPath) {
        if (blockPath.contains("umbral")) {
            return "umbral_steel";
        }
        for (String family : FAMILIES.keySet()) {
            if (!family.equals("umbral_steel") && blockPath.contains(family)) {
                return family;
            }
        }
        return null;
    }

    public static int scaled(int price, double priceIndex) {
        return Math.max(1, Math.min(64, (int) Math.ceil(price * Math.max(0.5, priceIndex))));
    }

    public static List<Offer> build(Map<String, Integer> minedByFamily, String currency, double priceIndex, Random random,
                                    boolean solarFragment) {
        List<Offer> offers = new ArrayList<>();
        List<String> ranked = new ArrayList<>(FAMILIES.keySet());
        ranked.sort(Comparator.comparingInt((String f) -> -minedByFamily.getOrDefault(f, 0)).thenComparing(f -> f));
        boolean minedAny = minedByFamily.values().stream().anyMatch(v -> v > 0);
        if (!minedAny) {
            java.util.Collections.shuffle(ranked, random);
        }
        // buys: the three most-mined families, the top one also in bulk at a better rate
        for (int i = 0; i < 3; i++) {
            String f = ranked.get(i);
            Object[] raw = FAMILIES.get(f);
            offers.add(new Offer((String) raw[0], (Integer) raw[1], null, 0, currency, f.equals("diamond") ? 4 : 1, 16));
        }
        if (minedAny) {
            Object[] top = FAMILIES.get(ranked.get(0));
            int count = (Integer) top[1];
            int bulk = Math.min(64, count * 4);
            offers.add(new Offer((String) top[0], bulk, null, 0, currency, ranked.get(0).equals("diamond") ? 20 : 5, 8));
        }
        // two ingot/gem buys
        List<String> ingots = new ArrayList<>(INGOTS.keySet());
        java.util.Collections.shuffle(ingots, random);
        for (int i = 0; i < 2; i++) {
            offers.add(new Offer(ingots.get(i), INGOTS.get(ingots.get(i)), null, 0, currency, 1, 12));
        }
        // sells
        offers.add(new Offer(currency, scaled(2, priceIndex), null, 0, "lacuna:divine_glass_shard", 1, 12));
        offers.add(new Offer(currency, scaled(3, priceIndex), null, 0, "lacuna:ruby", 1, 12));
        offers.add(new Offer(currency, scaled(8, priceIndex), null, 0, "lacuna:chronal_shard", 1, 3));
        offers.add(new Offer(currency, scaled(10, priceIndex), null, 0, "minecraft:echo_shard", 1, 3));
        if (solarFragment) {
            offers.add(new Offer("lacuna:ruby", 16, "lacuna:divine_glass_shard", 24, "lacuna:solar_flare_fragment", 1, 1));
        }
        return offers;
    }
}
