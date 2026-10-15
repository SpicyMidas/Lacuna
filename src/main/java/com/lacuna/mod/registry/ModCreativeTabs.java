package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The single "Lacuna" creative tab (spec M12: curated order): relics first, then weapons and armour, materials,
 * village and mob items, then every block in registration order. Anything not listed is appended at the end, so a
 * new registration can never be missing from the tab.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Lacuna.MODID);

    private static final List<Supplier<? extends ItemLike>> CURATED = List.of(
            // relics
            ModItems.TOTEM_OF_BANISHING, ModBlocks.FORGOTTEN_PORTAL_FRAME, ModItems.ECLIPSER, ModItems.SUNS_FURY, ModItems.DIVINE_MAGNIFYING_GLASS,
            ModItems.FRACTURED_PEARL, ModItems.ARRIVAL_SHARD, ModItems.DEPARTURE_SHARD, ModItems.CHRONOSHARD_POCKET_WATCH,
            ModItems.MIRROR_OF_RECURSION, ModItems.NULL_LANTERN, ModItems.WHISPERBONE_FLUTE, ModBlocks.ORRERY_OF_THRESHOLDS,
            // special weapons, tools, armour
            ModItems.GRAVEMAW_GREATSWORD, ModItems.TIDECALLER_SICKLE, ModItems.HEARTWOOD_BOW, ModItems.ECHO_DAGGER, ModItems.PYRRHIC_SCEPTER,
            ModItems.UMBRAL_STEEL_SWORD, ModItems.UMBRAL_STEEL_PICKAXE, ModItems.UMBRAL_STEEL_AXE, ModItems.UMBRAL_STEEL_SHOVEL, ModItems.UMBRAL_STEEL_HOE,
            ModItems.RUBY_SWORD, ModItems.RUBY_PICKAXE, ModItems.RUBY_AXE, ModItems.RUBY_SHOVEL, ModItems.RUBY_HOE,
            ModItems.STARLACE_HELMET, ModItems.STARLACE_CHESTPLATE, ModItems.STARLACE_LEGGINGS, ModItems.STARLACE_BOOTS,
            // materials
            ModItems.RUBY, ModItems.DIVINE_GLASS_SHARD, ModItems.RAW_UMBRAL_STEEL, ModItems.UMBRAL_STEEL_INGOT,
            ModItems.UMBRAL_STEEL_NUGGET, ModItems.ZENITHITE_SHARD, ModItems.STARLACE_CLOTH, ModItems.SOLAR_FLARE_FRAGMENT, ModItems.SOLAR_FLARE,
            ModItems.CHRONAL_SHARD, ModItems.ECHO_FRAGMENT, ModItems.EMBER, ModItems.CRYSTAL_WILLOW_SHARD, ModItems.SILK_DUST, ModItems.CLOCKWORK_GEAR,
            // villages and creatures
            ModItems.VILLAGE_COIN, ModBlocks.VILLAGE_LEDGER, ModItems.GUILD_CONTRACT, ModItems.REPAIR_KIT, ModItems.CLOCKWORK_DECOY,
            ModItems.LANTERN_MOTH_SPAWN_EGG, ModItems.LAST_MERCHANT_SPAWN_EGG);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LACUNA = TABS.register("lacuna", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lacuna"))
            .icon(() -> new ItemStack(ModItems.TOTEM_OF_BANISHING.get()))
            .displayItems((params, output) -> ordered().forEach(output::accept))
            .build());

    private ModCreativeTabs() {}

    public static Set<Item> ordered() {
        Set<Item> items = new LinkedHashSet<>();
        CURATED.forEach(s -> items.add(s.get().asItem()));
        ModRegistries.ITEMS.getEntries().forEach(h -> items.add(h.get()));
        return items;
    }
}
