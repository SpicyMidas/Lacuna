package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** M12 tooltip polish: any Lacuna item with an {@code item.lacuna.<id>.tip} lang key shows it as a grey line. */
public final class GenericTooltips {
    private GenericTooltips() {}

    public static void onTooltip(ItemTooltipEvent event) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!Lacuna.MODID.equals(id.getNamespace())) {
            return;
        }
        String key = "item.lacuna." + id.getPath() + ".tip";
        if (I18n.exists(key)) {
            event.getToolTip().add(Math.min(1, event.getToolTip().size()), Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
