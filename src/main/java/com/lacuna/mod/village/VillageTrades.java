package com.lacuna.mod.village;

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
 * Trades of the village professions (spec 18.3). Base prices assume coin value 1.0; the live coin value and the
 * village's other price factors are applied when the trading screen opens ({@link VillageEvents}).
 */
public final class VillageTrades {
    private VillageTrades() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == ModVillagers.COINER.get()) {
            for (MintRate rate : coinerRates()) {
                add(event, rate.level(), rate.buy() ? buy(rate.item(), rate.count()) : sell(rate.item(), rate.count(), rate.coins()));
            }
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

    /**
     * One Coiner offer: at villager {@code level}, {@code buy} = the Coiner mints one coin from {@code count} of the
     * item, otherwise it sells {@code count} of the item for {@code coins}. The mint press screen shows this table.
     */
    public record MintRate(int level, ItemLike item, int count, int coins, boolean buy) {}

    static List<MintRate> coinerRates() {
        return List.of(new MintRate(1, Items.RAW_IRON, 6, 1, true), new MintRate(1, Items.RAW_COPPER, 12, 1, true),
                new MintRate(1, Items.RAW_GOLD, 4, 1, true),
                new MintRate(2, ModItems.RUBY.get(), 2, 1, true), new MintRate(2, ModItems.RAW_UMBRAL_STEEL.get(), 3, 1, true),
                new MintRate(2, Items.IRON_INGOT, 5, 1, true),
                new MintRate(3, Items.GOLD_INGOT, 3, 1, true), new MintRate(3, ModItems.UMBRAL_STEEL_INGOT.get(), 2, 1, true),
                new MintRate(3, ModItems.RUBY.get(), 2, 1, true),
                new MintRate(4, Items.EMERALD, 1, 4, false), new MintRate(4, Items.COPPER_INGOT, 10, 1, true),
                new MintRate(5, ModItems.RUBY.get(), 1, 3, false));
    }

    @SafeVarargs
    private static void add(VillagerTradesEvent event, int level, VillagerTrades.ItemListing... listings) {
        event.getTrades().computeIfAbsent(level, k -> new java.util.ArrayList<>()).addAll(List.of(listings));
    }

    /** The villager buys {@code count} of an ore/ingot for one coin. */
    static VillagerTrades.ItemListing buy(ItemLike item, int count) {
        return (trader, random) -> new MerchantOffer(new ItemCost(item, count), new ItemStack(ModItems.VILLAGE_COIN.get()), 16, 2, 0.05F);
    }

    /** The villager sells {@code count} of an item for {@code coins} coins. */
    static VillagerTrades.ItemListing sell(ItemLike item, int count, int coins) {
        return (trader, random) -> new MerchantOffer(new ItemCost(ModItems.VILLAGE_COIN.get(), coins), new ItemStack(item, count), 12, 3, 0.05F);
    }

    /** Ore handed to a Coiner lowers the village's coin value (2 % per 16). */
    public static void onTrade(TradeWithVillagerEvent event) {
        MerchantOffer offer = event.getMerchantOffer();
        if (!offer.getResult().is(ModItems.VILLAGE_COIN.get()) || !(event.getAbstractVillager().level() instanceof ServerLevel level)) {
            return;
        }
        int delivered = offer.getBaseCostA().getCount();
        SafeRun.run(VillageService.FEATURE, () -> VillageService.at(level, event.getAbstractVillager().blockPosition()).ifPresent(t -> {
            int before = t.oreDelivered;
            t.oreDelivered += delivered;
            t.coinValue = CouncilMath.afterDelivery(t.coinValue, before, t.oreDelivered);
            VillageSavedData.get(level.getServer()).touch();
        }));
    }

    /** Is this a coin-priced trade (cost) or an ore-for-coin trade (result)? Used by the price adjustment. */
    static boolean costsCoins(MerchantOffer offer) {
        return offer.getBaseCostA().is(ModItems.VILLAGE_COIN.get());
    }

    static boolean paysCoins(MerchantOffer offer) {
        return offer.getResult().is(ModItems.VILLAGE_COIN.get());
    }

    static Item coin() {
        return ModItems.VILLAGE_COIN.get();
    }
}
