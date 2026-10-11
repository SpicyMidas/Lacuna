package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Lacuna.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LACUNA = TABS.register("lacuna", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lacuna"))
            .icon(() -> new ItemStack(Items.ECHO_SHARD))
            .displayItems((params, output) -> {
            })
            .build());

    private ModCreativeTabs() {}
}
