package com.lacuna.mod.exploration;

import com.lacuna.mod.logic.MerchantPricing;
import com.lacuna.mod.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * The Last Merchant's structure map: one visit in two he carries one {@link StructureMaps} map, its place drawn at
 * random, for rubies (scaled by the nearest village's price index, like his other sales). The map is made when his
 * offers are built (the first time a player opens them): one bounded structure search per merchant, as the vanilla
 * cartographer's maps; nothing found within {@link #SEARCH_RADIUS} chunks, no offer.
 */
public final class MerchantMaps {
    /** Share of visits with a map on offer. */
    public static final float CHANCE = 0.5F;
    /** Search radius in chunks (vanilla's cartographer maps use 100). */
    public static final int SEARCH_RADIUS = 100;
    /** Base prices in rubies, by {@link StructureMaps.Kind}. */
    public static final int SUNKEN_BELL_TOWER_PRICE = 12;
    public static final int OBELISK_PRICE = 8;
    public static final int ANCIENT_CITY_PRICE = 16;

    private MerchantMaps() {}

    public static int price(StructureMaps.Kind kind) {
        return switch (kind) {
            case SUNKEN_BELL_TOWER -> SUNKEN_BELL_TOWER_PRICE;
            case OBELISK -> OBELISK_PRICE;
            case ANCIENT_CITY -> ANCIENT_CITY_PRICE;
        };
    }

    /** Adds the map offer to {@code offers} (or nothing). Overworld only: the three places are Overworld structures. */
    public static void addOffer(AbstractVillager merchant, MerchantOffers offers, double priceIndex) {
        if (!(merchant.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        RandomSource random = merchant.getRandom();
        if (random.nextFloat() >= CHANCE) {
            return;
        }
        StructureMaps.Kind[] kinds = StructureMaps.Kind.values();
        StructureMaps.Kind kind = kinds[random.nextInt(kinds.length)];
        ItemStack map = StructureMaps.create(level, merchant.blockPosition(), kind, SEARCH_RADIUS);
        if (map == null) {
            return;
        }
        int price = MerchantPricing.scaled(price(kind), priceIndex);
        offers.add(new MerchantOffer(new ItemCost(ModItems.RUBY.get(), price), map, 1, 1, 0.05F));
    }
}
