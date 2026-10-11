package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The single "Lacuna" creative tab; items appear in registration order, which is grouped by feature. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Lacuna.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LACUNA = TABS.register("lacuna", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lacuna"))
            .icon(() -> new ItemStack(ModItems.TOTEM_OF_BANISHING.get()))
            .displayItems((params, output) -> ModRegistries.ITEMS.getEntries().forEach(holder -> output.accept(holder.get())))
            .build());

    private ModCreativeTabs() {}
}
