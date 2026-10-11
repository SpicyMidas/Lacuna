package com.lacuna.mod.registry;

import com.lacuna.mod.Balance;
import com.lacuna.mod.banish.TotemOfBanishingItem;
import com.lacuna.mod.beam.DivineMagnifyingGlassItem;
import com.lacuna.mod.eclipse.EclipseCharge;
import com.lacuna.mod.eclipse.EclipserItem;
import com.lacuna.mod.pearl.ArrivalShardItem;
import com.lacuna.mod.pearl.DepartureShardItem;
import com.lacuna.mod.pearl.FracturedPearlItem;
import com.lacuna.mod.solar.SunsFuryItem;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

    // ------------------------------------------------------------------ M4 umbral iron, eclipser, solar flare
    public static final DeferredItem<Item> RAW_UMBRAL_IRON = item("raw_umbral_iron", Item::new, new Item.Properties());
    public static final DeferredItem<Item> UMBRAL_IRON_INGOT = item("umbral_iron_ingot", Item::new, new Item.Properties());
    public static final DeferredItem<Item> UMBRAL_IRON_NUGGET = item("umbral_iron_nugget", Item::new, new Item.Properties());
    // components are attached inside the factory: it runs at registration time, after data components exist
    public static final DeferredItem<EclipserItem> ECLIPSER = item("eclipser",
            p -> new EclipserItem(ModTiers.ECLIPSER, p.component(ModComponents.ECLIPSE_CHARGE.get(), EclipseCharge.EMPTY)),
            new Item.Properties().rarity(Rarity.EPIC).attributes(weapon(Balance.ECLIPSER_ATTACK_DAMAGE, Balance.ECLIPSER_ATTACK_SPEED)));
    public static final DeferredItem<Item> SOLAR_FLARE_FRAGMENT = item("solar_flare_fragment", Item::new,
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> SOLAR_FLARE = item("solar_flare", Item::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<SunsFuryItem> SUNS_FURY = item("suns_fury", SunsFuryItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    // ------------------------------------------------------------------ M5 fractured pearl
    public static final DeferredItem<FracturedPearlItem> FRACTURED_PEARL = item("fractured_pearl", FracturedPearlItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<ArrivalShardItem> ARRIVAL_SHARD = item("arrival_shard", ArrivalShardItem::new,
            new Item.Properties().durability(Balance.ARRIVAL_SHARD_DURABILITY).rarity(Rarity.RARE));
    public static final DeferredItem<DepartureShardItem> DEPARTURE_SHARD = item("departure_shard", DepartureShardItem::new,
            new Item.Properties().durability(Balance.DEPARTURE_SHARD_DURABILITY).rarity(Rarity.RARE));

    private ModItems() {}

    /**
     * Main-hand attack attributes for a weapon with the given TOTAL damage (player base 1 included) and attack speed.
     */
    public static ItemAttributeModifiers weapon(double totalDamage, double attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, totalDamage - 1.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed - 4.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    static void init() {
        // class-loading this holder registers every item above
    }

    static <I extends Item> DeferredItem<I> item(String id, Function<Item.Properties, ? extends I> factory, Item.Properties props) {
        return ModRegistries.ITEMS.registerItem(id, factory, props);
    }
}
