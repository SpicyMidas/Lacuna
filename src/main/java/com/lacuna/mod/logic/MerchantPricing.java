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
        FAMILIES.put("ruby", new Object[] {"lacuna:ruby", 2});
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

    /** What the Last Merchant sells for rubies: item, count, price in rubies, max uses. */
    public static final Object[][] CATALOG = {
            {"lacuna:divine_glass_shard", 1, 4, 6},
            {"lacuna:chronal_shard", 1, 6, 3},
            {"minecraft:echo_shard", 1, 8, 3},
            {"lacuna:umbral_steel_ingot", 1, 4, 8},
            {"lacuna:zenithite_shard", 2, 3, 8},
            {"lacuna:ember", 4, 1, 12},
            {"minecraft:experience_bottle", 3, 2, 12},
            {"lacuna:fractured_pearl", 1, 10, 2},
    };

    /**
     * The Last Merchant's offers: he only deals in {@code currency} (rubies). He buys the families the player mined most
     * (and a bulk lot of the top one) plus two ingots, paying in rubies, and sells his catalog for rubies. The currency
     * itself is never bought or sold.
     */
    public static List<Offer> build(Map<String, Integer> minedByFamily, String currency, double priceIndex, Random random,
                                    boolean solarFragment) {
        List<Offer> offers = new ArrayList<>();
        List<String> ranked = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : FAMILIES.entrySet()) {
            if (!currency.equals(e.getValue()[0])) {
                ranked.add(e.getKey());
            }
        }
        ranked.sort(Comparator.comparingInt((String f) -> -minedByFamily.getOrDefault(f, 0)).thenComparing(f -> f));
        boolean minedAny = minedByFamily.values().stream().anyMatch(v -> v > 0);
        if (!minedAny) {
            java.util.Collections.shuffle(ranked, random);
        }
        // buys: the three most-mined families, the top one also in bulk at a better rate (rubies are dear: twice the goods)
        for (int i = 0; i < 3; i++) {
            String f = ranked.get(i);
            Object[] raw = FAMILIES.get(f);
            int count = Math.min(64, (Integer) raw[1] * 2);
            offers.add(new Offer((String) raw[0], count, null, 0, currency, f.equals("diamond") ? 2 : 1, 16));
        }
        if (minedAny) {
            Object[] top = FAMILIES.get(ranked.get(0));
            int bulk = Math.min(64, (Integer) top[1] * 8);
            offers.add(new Offer((String) top[0], bulk, null, 0, currency, ranked.get(0).equals("diamond") ? 10 : 5, 8));
        }
        // two ingot buys
        List<String> ingots = new ArrayList<>();
        for (String id : INGOTS.keySet()) {
            if (!currency.equals(id)) {
                ingots.add(id);
            }
        }
        java.util.Collections.shuffle(ingots, random);
        for (int i = 0; i < Math.min(2, ingots.size()); i++) {
            offers.add(new Offer(ingots.get(i), Math.min(64, INGOTS.get(ingots.get(i)) * 2), null, 0, currency, 1, 12));
        }
        // sells, priced in rubies
        for (Object[] c : CATALOG) {
            if (!currency.equals(c[0])) {
                offers.add(new Offer(currency, scaled((Integer) c[2], priceIndex), null, 0, (String) c[0], (Integer) c[1], (Integer) c[3]));
            }
        }
        if (solarFragment) {
            offers.add(new Offer(currency, 16, "lacuna:divine_glass_shard", 8, "lacuna:solar_flare_fragment", 1, 1));
        }
        return offers;
    }
}
