package com.lacuna.mod.merchant;

import com.lacuna.mod.Balance;
import com.lacuna.mod.logic.MerchantPricing;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The Last Merchant (spec M10): a wandering trader who trades in mined materials. Extends WanderingTrader for its
 * model, goals, despawn timer and trading UI; only the stock differs. Never saved (entity type is noSave).
 */
public class LastMerchant extends WanderingTrader {
    private double priceIndex = 1.0;

    public LastMerchant(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
    }

    public void setPriceIndex(double priceIndex) {
        this.priceIndex = priceIndex;
    }

    @Override
    protected void updateTrades() {
        SafeRun.run("last_merchant", this::buildOffers);
    }

    private void buildOffers() {
        MerchantOffers offers = getOffers();
        Map<String, Integer> mined = new HashMap<>();
        Player nearest = level().getNearestPlayer(this, 64.0);
        if (nearest instanceof ServerPlayer sp) {
            mined = recentlyMined(sp);
        }
        boolean fragment = random.nextFloat() < Balance.MERCHANT_FRAGMENT_CHANCE;
        String currency = BuiltInRegistries.ITEM.getKey(CurrencyService.currency()).toString();
        List<MerchantPricing.Offer> plan = MerchantPricing.build(mined, currency, priceIndex, new Random(random.nextLong()), fragment);
        for (MerchantPricing.Offer o : plan) {
            Item cost = item(o.costItem());
            Item result = item(o.resultItem());
            if (cost == null || result == null) {
                continue;
            }
            Optional<ItemCost> second = Optional.empty();
            if (o.hasSecondCost()) {
                Item cost2 = item(o.cost2Item());
                if (cost2 == null) {
                    continue;
                }
                second = Optional.of(new ItemCost(cost2, o.cost2Count()));
            }
            offers.add(new MerchantOffer(new ItemCost(cost, o.costCount()), second, new ItemStack(result, o.resultCount()),
                    o.maxUses(), 1, 0.05F));
        }
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    /**
     * Ores mined since the player's last merchant visit (from vanilla {@code Stats.BLOCK_MINED} over {@code #c:ores},
     * diffed against a snapshot stored on the player); falls back to lifetime totals on the first visit.
     */
    static Map<String, Integer> recentlyMined(ServerPlayer player) {
        Map<String, Integer> totals = new HashMap<>();
        BuiltInRegistries.BLOCK.getTag(ModTags.Blocks.C_ORES).ifPresent(set -> {
            for (Holder<Block> holder : set) {
                String family = MerchantPricing.familyOf(BuiltInRegistries.BLOCK.getKey(holder.value()).getPath());
                if (family != null) {
                    int mined = player.getStats().getValue(Stats.BLOCK_MINED.get(holder.value()));
                    totals.merge(family, mined, Integer::sum);
                }
            }
        });
        Map<String, Integer> snapshot = player.getData(ModAttachments.MINED_SNAPSHOT);
        Map<String, Integer> recent = new HashMap<>();
        totals.forEach((family, total) -> recent.put(family, Math.max(0, total - snapshot.getOrDefault(family, 0))));
        player.setData(ModAttachments.MINED_SNAPSHOT, Map.copyOf(totals));
        boolean any = recent.values().stream().anyMatch(v -> v > 0);
        return any ? recent : totals;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
