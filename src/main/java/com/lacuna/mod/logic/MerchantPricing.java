package com.lacuna.mod.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Last Merchant stock (spec M10), pure and unit-tested. The merchant buys raw ores/ingots for rubies and sells
 * materials (divine glass shards, chronal shards, echo shards...), two relics drawn at random, the Sun's Fury forge
 * and sometimes a Lacunar Quintessence, all for rubies: rubies are his only currency, and what relics cost. The ore
 * families the nearest player mined most get buy offers, and the top one an extra "bulk buy". Sell prices scale with
 * the nearest village's price index (M11d).
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
     * Relics he may carry (relics are never crafted, docs/PROGRESSION.md): item, price in rubies. Each visit he brings
     * {@link #RELIC_OFFERS} of them, drawn at random, one of each; a price above 64 is paid as 64 rubies plus ruby blocks
     * (9 rubies each, rounded up). Totems are never sold: only a Lacunar Quintessence makes one.
     */
    public static final Object[][] RELICS = {
            {"lacuna:divine_magnifying_glass", 32},
            {"lacuna:chronoshard_pocket_watch", 32},
            {"lacuna:whisperbone_flute", 32},
            {"lacuna:heartwood_bow", 40},
            {"lacuna:pyrrhic_scepter", 40},
            {"lacuna:echo_dagger", 40},
            {"lacuna:mirror_of_recursion", 40},
            {"lacuna:starcaller_trumpet", 40},
            {"lacuna:null_lantern", 48},
            {"lacuna:tidecaller_sickle", 48},
            {"lacuna:quickening_wand", 48},
            {"lacuna:eclipser", 82},
            {"lacuna:gravemaw_greatsword", 82},
            {"lacuna:ember_triangle", 82},
    };
    public static final int RELIC_OFFERS = 2;
    /** Always on offer: he sets a Solar Flare into Sun's Fury for this many rubies (Sun's Fury has no recipe). */
    public static final int SUNS_FURY_FORGE_PRICE = 32;
    /** One merchant in five carries a single Lacunar Quintessence, for 100 rubies (64 rubies + 4 ruby blocks). */
    public static final float QUINTESSENCE_CHANCE = 0.2F;
    public static final int QUINTESSENCE_PRICE = 100;
    public static final String RUBY = "lacuna:ruby";
    public static final String RUBY_BLOCK = "lacuna:ruby_block";
    public static final int RUBIES_PER_BLOCK = 9;

    /**
     * {@code price} rubies (scaled by the price index) as one or two stacks: up to 64 of the currency, the rest in ruby
     * blocks when the currency is the ruby (otherwise capped at 64). Returns {count, blockCount}.
     */
    public static int[] split(int price, double priceIndex, String currency) {
        int total = Math.max(1, (int) Math.ceil(price * Math.max(0.5, priceIndex)));
        if (total <= 64 || !RUBY.equals(currency)) {
            return new int[] {Math.min(64, total), 0};
        }
        return new int[] {64, Math.min(64, (total - 64 + RUBIES_PER_BLOCK - 1) / RUBIES_PER_BLOCK)};
    }

    private static Offer sale(String currency, int price, double priceIndex, String item, int count, int maxUses) {
        int[] cost = split(price, priceIndex, currency);
        return new Offer(currency, cost[0], cost[1] > 0 ? RUBY_BLOCK : null, cost[1], item, count, maxUses);
    }

    /** {@link #build(Map, String, double, Random, boolean, boolean)} without a Quintessence on offer. */
    public static List<Offer> build(Map<String, Integer> minedByFamily, String currency, double priceIndex, Random random,
                                    boolean solarFragment) {
        return build(minedByFamily, currency, priceIndex, random, solarFragment, false);
    }

    /**
     * The Last Merchant's offers: he only deals in {@code currency} (rubies). He buys the families the player mined most
     * (and a bulk lot of the top one) plus two ingots, paying in rubies, and sells his catalog for rubies; then, also for
     * rubies, a Solar Flare Fragment ({@code solarFragment}), the Sun's Fury forge, two relics drawn at random and, now and
     * then, a Lacunar Quintessence ({@code quintessence}). The currency itself is never bought or sold.
     */
    public static List<Offer> build(Map<String, Integer> minedByFamily, String currency, double priceIndex, Random random,
                                    boolean solarFragment, boolean quintessence) {
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
        // the forge: rubies and a Solar Flare (four fragments, assembled) for Sun's Fury
        offers.add(new Offer(currency, scaled(SUNS_FURY_FORGE_PRICE, priceIndex), "lacuna:solar_flare", 1, "lacuna:suns_fury", 1, 1));
        // two relics, drawn at random, one of each
        List<Object[]> relics = new ArrayList<>(List.of(RELICS));
        java.util.Collections.shuffle(relics, random);
        for (int i = 0; i < Math.min(RELIC_OFFERS, relics.size()); i++) {
            Object[] r = relics.get(i);
            offers.add(sale(currency, (Integer) r[1], priceIndex, (String) r[0], 1, 1));
        }
        if (quintessence) {
            offers.add(sale(currency, QUINTESSENCE_PRICE, priceIndex, "lacuna:lacunar_quintessence", 1, 1));
        }
        return offers;
    }
}
