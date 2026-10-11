package com.lacuna.mod.township;

import com.lacuna.mod.logic.CouncilMath;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModVillagers;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * Trades of the township professions (spec 18.3). Base prices assume coin value 1.0; the live coin value and the
 * township's other price factors are applied when the trading screen opens ({@link TownshipEvents}).
 */
public final class TownshipTrades {
    private TownshipTrades() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == ModVillagers.COINER.get()) {
            add(event, 1, buy(Items.RAW_IRON, 6), buy(Items.RAW_COPPER, 12), buy(Items.RAW_GOLD, 4));
            add(event, 2, buy(ModItems.RAW_RUBY.get(), 3), buy(ModItems.RAW_UMBRAL_IRON.get(), 3), buy(Items.IRON_INGOT, 5));
            add(event, 3, buy(Items.GOLD_INGOT, 3), buy(ModItems.UMBRAL_IRON_INGOT.get(), 2), buy(ModItems.RUBY.get(), 2));
            add(event, 4, sell(Items.EMERALD, 1, 4), buy(Items.COPPER_INGOT, 10));
            add(event, 5, sell(ModItems.RUBY.get(), 1, 3));
        } else if (event.getType() == ModVillagers.GUILDMASTER.get()) {
            for (int tier = 1; tier <= 3; tier++) {
                event.getTrades().computeIfAbsent(tier, k -> new java.util.ArrayList<>()).addAll(Guilds.listings(tier));
            }
        } else if (event.getType() == ModVillagers.MAGISTRATE.get()) {
            add(event, 1, sell(Items.PAPER, 8, 1), sell(Items.WRITABLE_BOOK, 1, 4));
            add(event, 2, sell(Items.NAME_TAG, 1, 8));
            add(event, 3, sell(Items.BELL, 1, 12));
        }
    }

    @SafeVarargs
    private static void add(VillagerTradesEvent event, int level, VillagerTrades.ItemListing... listings) {
        event.getTrades().computeIfAbsent(level, k -> new java.util.ArrayList<>()).addAll(List.of(listings));
    }

    /** The villager buys {@code count} of an ore/ingot for one coin. */
    static VillagerTrades.ItemListing buy(ItemLike item, int count) {
        return (trader, random) -> new MerchantOffer(new ItemCost(item, count), new ItemStack(ModItems.TOWNSHIP_COIN.get()), 16, 2, 0.05F);
    }

    /** The villager sells {@code count} of an item for {@code coins} coins. */
    static VillagerTrades.ItemListing sell(ItemLike item, int count, int coins) {
        return (trader, random) -> new MerchantOffer(new ItemCost(ModItems.TOWNSHIP_COIN.get(), coins), new ItemStack(item, count), 12, 3, 0.05F);
    }

    /** Ore handed to a Coiner lowers the township's coin value (2 % per 16). */
    public static void onTrade(TradeWithVillagerEvent event) {
        MerchantOffer offer = event.getMerchantOffer();
        if (!offer.getResult().is(ModItems.TOWNSHIP_COIN.get()) || !(event.getAbstractVillager().level() instanceof ServerLevel level)) {
            return;
        }
        int delivered = offer.getBaseCostA().getCount();
        SafeRun.run(TownshipService.FEATURE, () -> TownshipService.at(level, event.getAbstractVillager().blockPosition()).ifPresent(t -> {
            int before = t.oreDelivered;
            t.oreDelivered += delivered;
            t.coinValue = CouncilMath.afterDelivery(t.coinValue, before, t.oreDelivered);
            TownshipSavedData.get(level.getServer()).touch();
        }));
    }

    /** Is this a coin-priced trade (cost) or an ore-for-coin trade (result)? Used by the price adjustment. */
    static boolean costsCoins(MerchantOffer offer) {
        return offer.getBaseCostA().is(ModItems.TOWNSHIP_COIN.get());
    }

    static boolean paysCoins(MerchantOffer offer) {
        return offer.getResult().is(ModItems.TOWNSHIP_COIN.get());
    }

    static Item coin() {
        return ModItems.TOWNSHIP_COIN.get();
    }
}
