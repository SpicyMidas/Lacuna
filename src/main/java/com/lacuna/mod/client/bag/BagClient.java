package com.lacuna.mod.client.bag;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.bag.BagCapture;
import com.lacuna.mod.bag.BagContents;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client side of the Comically Large Bag: the bulging {@code lacuna:full} model and an always-visible contents line. */
public final class BagClient {
    private BagClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(BagClient::onClientSetup);
        // after GenericTooltips (normal priority) folded the usage lines: what is inside always shows
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, BagClient::onTooltip);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SafeRun.run("item_properties", () -> ItemProperties.register(ModItems.COMICALLY_LARGE_BAG.get(),
                Lacuna.id("full"), (stack, level, entity, seed) -> BagCapture.isFull(stack) ? 1.0F : 0.0F)));
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.COMICALLY_LARGE_BAG.get())) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        BagContents contents = BagCapture.contents(stack);
        Component line;
        if (contents == null) {
            line = Component.translatable("lacuna.bag.empty").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        } else {
            HolderLookup.Provider registries = event.getContext().registries();
            if (registries == null && Minecraft.getInstance().level != null) {
                registries = Minecraft.getInstance().level.registryAccess();
            }
            HolderLookup.Provider lookup = registries;
            Component what = SafeRun.call(BagCapture.FEATURE + "_tooltip", () -> BagCapture.describe(contents, lookup), Component.literal("?"));
            line = Component.translatable("lacuna.bag.contains", what.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GOLD);
        }
        tooltip.add(Math.min(1, tooltip.size()), line);
    }
}
