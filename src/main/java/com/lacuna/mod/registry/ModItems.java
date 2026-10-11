package com.lacuna.mod.registry;

import com.lacuna.mod.Balance;
import com.lacuna.mod.banish.TotemOfBanishingItem;
import com.lacuna.mod.beam.DivineMagnifyingGlassItem;
import java.util.function.Function;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.registries.DeferredItem;

/** All Lacuna items (block items are registered by {@link ModBlocks}). */
public final class ModItems {
    // ------------------------------------------------------------------ M2 totem of banishing
    public static final DeferredItem<TotemOfBanishingItem> TOTEM_OF_BANISHING = item("totem_of_banishing", TotemOfBanishingItem::new,
            new Item.Properties().durability(Balance.TOTEM_DURABILITY).rarity(Rarity.RARE));

    // ------------------------------------------------------------------ M1 ruby & divine glass
    public static final DeferredItem<Item> RUBY = item("ruby", Item::new, new Item.Properties());
    public static final DeferredItem<Item> RAW_RUBY = item("raw_ruby", Item::new, new Item.Properties());
    public static final DeferredItem<Item> DIVINE_GLASS_SHARD = item("divine_glass_shard", Item::new, new Item.Properties());

    public static final DeferredItem<SwordItem> RUBY_SWORD = item("ruby_sword", p -> new SwordItem(ModTiers.RUBY, p),
            new Item.Properties().attributes(SwordItem.createAttributes(ModTiers.RUBY, 3, -2.4F)));
    public static final DeferredItem<PickaxeItem> RUBY_PICKAXE = item("ruby_pickaxe", p -> new PickaxeItem(ModTiers.RUBY, p),
            new Item.Properties().attributes(PickaxeItem.createAttributes(ModTiers.RUBY, 1.0F, -2.8F)));
    public static final DeferredItem<AxeItem> RUBY_AXE = item("ruby_axe", p -> new AxeItem(ModTiers.RUBY, p),
            new Item.Properties().attributes(AxeItem.createAttributes(ModTiers.RUBY, 5.0F, -3.0F)));
    public static final DeferredItem<ShovelItem> RUBY_SHOVEL = item("ruby_shovel", p -> new ShovelItem(ModTiers.RUBY, p),
            new Item.Properties().attributes(ShovelItem.createAttributes(ModTiers.RUBY, 1.5F, -3.0F)));
    public static final DeferredItem<HoeItem> RUBY_HOE = item("ruby_hoe", p -> new HoeItem(ModTiers.RUBY, p),
            new Item.Properties().attributes(HoeItem.createAttributes(ModTiers.RUBY, -3.0F, 0.0F)));

    // ------------------------------------------------------------------ M3 divine magnifying glass
    public static final DeferredItem<DivineMagnifyingGlassItem> DIVINE_MAGNIFYING_GLASS = item("divine_magnifying_glass",
            DivineMagnifyingGlassItem::new, new Item.Properties().durability(Balance.GLASS_DURABILITY).rarity(Rarity.UNCOMMON));

    private ModItems() {}

    static void init() {
        // class-loading this holder registers every item above
    }

    static <I extends Item> DeferredItem<I> item(String id, Function<Item.Properties, ? extends I> factory, Item.Properties props) {
        return ModRegistries.ITEMS.registerItem(id, factory, props);
    }
}
