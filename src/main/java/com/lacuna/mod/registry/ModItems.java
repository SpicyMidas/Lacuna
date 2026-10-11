package com.lacuna.mod.registry;

import com.lacuna.mod.Balance;
import com.lacuna.mod.banish.TotemOfBanishingItem;
import com.lacuna.mod.beam.DivineMagnifyingGlassItem;
import com.lacuna.mod.chrono.ChronoshardWatchItem;
import com.lacuna.mod.flute.WhisperboneFluteItem;
import com.lacuna.mod.gear.EchoDaggerItem;
import com.lacuna.mod.gear.GravemawGreatswordItem;
import com.lacuna.mod.gear.HeartwoodBowItem;
import com.lacuna.mod.gear.PyrrhicScepterItem;
import com.lacuna.mod.gear.TidecallerSickleItem;
import net.minecraft.world.item.ArmorItem;
import com.lacuna.mod.lantern.NullLanternItem;
import com.lacuna.mod.mirror.MirrorOfRecursionItem;
import com.lacuna.mod.mob.ClockworkDecoyItem;
import com.lacuna.mod.mob.SilkDustItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
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

    // ------------------------------------------------------------------ M6 utility items
    public static final DeferredItem<ChronoshardWatchItem> CHRONOSHARD_POCKET_WATCH = item("chronoshard_pocket_watch",
            ChronoshardWatchItem::new, new Item.Properties().durability(Balance.WATCH_DURABILITY).rarity(Rarity.RARE));
    public static final DeferredItem<Item> CHRONAL_SHARD = item("chronal_shard", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<MirrorOfRecursionItem> MIRROR_OF_RECURSION = item("mirror_of_recursion",
            MirrorOfRecursionItem::new, new Item.Properties().durability(Balance.MIRROR_DURABILITY).rarity(Rarity.RARE));
    public static final DeferredItem<NullLanternItem> NULL_LANTERN = item("null_lantern", NullLanternItem::new,
            new Item.Properties().durability(Balance.LANTERN_DURABILITY).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<WhisperboneFluteItem> WHISPERBONE_FLUTE = item("whisperbone_flute", WhisperboneFluteItem::new,
            new Item.Properties().durability(Balance.FLUTE_DURABILITY).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ECHO_FRAGMENT = item("echo_fragment", Item::new, new Item.Properties());

    // ------------------------------------------------------------------ M7 gear
    public static final DeferredItem<Item> ZENITHITE_SHARD = item("zenithite_shard", Item::new, new Item.Properties());
    public static final DeferredItem<Item> STARLACE_CLOTH = item("starlace_cloth", Item::new, new Item.Properties());
    public static final DeferredItem<ArmorItem> STARLACE_HELMET = item("starlace_helmet",
            p -> new ArmorItem(ModArmorMaterials.STARLACE, ArmorItem.Type.HELMET, p),
            new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(Balance.STARLACE_DURABILITY_FACTOR)));
    public static final DeferredItem<ArmorItem> STARLACE_CHESTPLATE = item("starlace_chestplate",
            p -> new ArmorItem(ModArmorMaterials.STARLACE, ArmorItem.Type.CHESTPLATE, p),
            new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(Balance.STARLACE_DURABILITY_FACTOR)));
    public static final DeferredItem<ArmorItem> STARLACE_LEGGINGS = item("starlace_leggings",
            p -> new ArmorItem(ModArmorMaterials.STARLACE, ArmorItem.Type.LEGGINGS, p),
            new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(Balance.STARLACE_DURABILITY_FACTOR)));
    public static final DeferredItem<ArmorItem> STARLACE_BOOTS = item("starlace_boots",
            p -> new ArmorItem(ModArmorMaterials.STARLACE, ArmorItem.Type.BOOTS, p),
            new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(Balance.STARLACE_DURABILITY_FACTOR)));

    public static final DeferredItem<SwordItem> UMBRAL_IRON_SWORD = item("umbral_iron_sword", p -> new SwordItem(ModTiers.UMBRAL, p),
            new Item.Properties().attributes(SwordItem.createAttributes(ModTiers.UMBRAL, 3, -2.4F)));
    public static final DeferredItem<PickaxeItem> UMBRAL_IRON_PICKAXE = item("umbral_iron_pickaxe", p -> new PickaxeItem(ModTiers.UMBRAL, p),
            new Item.Properties().attributes(PickaxeItem.createAttributes(ModTiers.UMBRAL, 1.0F, -2.8F)));
    public static final DeferredItem<AxeItem> UMBRAL_IRON_AXE = item("umbral_iron_axe", p -> new AxeItem(ModTiers.UMBRAL, p),
            new Item.Properties().attributes(AxeItem.createAttributes(ModTiers.UMBRAL, 5.5F, -3.05F)));
    public static final DeferredItem<ShovelItem> UMBRAL_IRON_SHOVEL = item("umbral_iron_shovel", p -> new ShovelItem(ModTiers.UMBRAL, p),
            new Item.Properties().attributes(ShovelItem.createAttributes(ModTiers.UMBRAL, 1.5F, -3.0F)));
    public static final DeferredItem<HoeItem> UMBRAL_IRON_HOE = item("umbral_iron_hoe", p -> new HoeItem(ModTiers.UMBRAL, p),
            new Item.Properties().attributes(HoeItem.createAttributes(ModTiers.UMBRAL, -2.5F, -1.0F)));

    public static final DeferredItem<GravemawGreatswordItem> GRAVEMAW_GREATSWORD = item("gravemaw_greatsword",
            p -> new GravemawGreatswordItem(ModTiers.GRAVEMAW, p),
            new Item.Properties().rarity(Rarity.EPIC).attributes(weapon(Balance.GRAVEMAW_DAMAGE, Balance.GRAVEMAW_SPEED)));
    public static final DeferredItem<TidecallerSickleItem> TIDECALLER_SICKLE = item("tidecaller_sickle",
            p -> new TidecallerSickleItem(ModTiers.TIDECALLER, p.component(ModComponents.TIDE_CHARGES.get(), 0)),
            new Item.Properties().rarity(Rarity.RARE).attributes(weapon(Balance.SICKLE_DAMAGE, Balance.SICKLE_SPEED)));
    public static final DeferredItem<HeartwoodBowItem> HEARTWOOD_BOW = item("heartwood_bow", HeartwoodBowItem::new,
            new Item.Properties().durability(Balance.HEARTWOOD_DURABILITY).rarity(Rarity.RARE));
    public static final DeferredItem<EchoDaggerItem> ECHO_DAGGER = item("echo_dagger", p -> new EchoDaggerItem(ModTiers.ECHO_DAGGER, p),
            new Item.Properties().rarity(Rarity.RARE).attributes(weapon(Balance.ECHO_DAGGER_DAMAGE, Balance.ECHO_DAGGER_SPEED)));
    public static final DeferredItem<PyrrhicScepterItem> PYRRHIC_SCEPTER = item("pyrrhic_scepter", PyrrhicScepterItem::new,
            new Item.Properties().durability(Balance.SCEPTER_DURABILITY).rarity(Rarity.RARE));

    // ------------------------------------------------------------------ M8 wood extras
    public static final DeferredItem<Item> EMBER = item("ember", Item::new, new Item.Properties().fireResistant());
    public static final DeferredItem<Item> CRYSTAL_WILLOW_SHARD = item("crystal_willow_shard", Item::new, new Item.Properties());

    // ------------------------------------------------------------------ M9 mobs
    public static final DeferredItem<SilkDustItem> SILK_DUST = item("silk_dust", SilkDustItem::new, new Item.Properties());
    public static final DeferredItem<Item> CLOCKWORK_GEAR = item("clockwork_gear", Item::new, new Item.Properties());
    public static final DeferredItem<ClockworkDecoyItem> CLOCKWORK_DECOY = item("clockwork_decoy", ClockworkDecoyItem::new,
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<DeferredSpawnEggItem> LANTERN_MOTH_SPAWN_EGG = item("lantern_moth_spawn_egg",
            p -> new DeferredSpawnEggItem(ModEntities.LANTERN_MOTH, 0xF3E9B5, 0xFFC94A, p), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> GLOOMCRAWLER_SPAWN_EGG = item("gloomcrawler_spawn_egg",
            p -> new DeferredSpawnEggItem(ModEntities.GLOOMCRAWLER, 0x23301F, 0x7A1E2A, p), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> CLOCKWORK_HOUND_SPAWN_EGG = item("clockwork_hound_spawn_egg",
            p -> new DeferredSpawnEggItem(ModEntities.CLOCKWORK_HOUND, 0xB08D57, 0x6B3A22, p), new Item.Properties());

    public static final DeferredItem<DeferredSpawnEggItem> LAST_MERCHANT_SPAWN_EGG = item("last_merchant_spawn_egg",
            p -> new DeferredSpawnEggItem(ModEntities.LAST_MERCHANT, 0x3B4A6B, 0xC9A869, p), new Item.Properties());

    // ------------------------------------------------------------------ M11 townships
    public static final DeferredItem<Item> TOWNSHIP_COIN = item("township_coin", Item::new, new Item.Properties());

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
