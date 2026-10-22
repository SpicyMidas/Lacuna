package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Lacuna item descriptions stay folded until asked for: the lore, usage and stat lines an item adds (and its optional
 * {@code item.lacuna.<id>.tip} line) only show while the "Item details" key (Shift by default) is held over the item;
 * otherwise a single grey hint line takes their place. Vanilla lines (attributes, enchantments...) are never touched.
 */
public final class GenericTooltips {
    public static final KeyMapping DETAILS = new KeyMapping("key.lacuna.item_details", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT, "key.categories.lacuna");

    private GenericTooltips() {}

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(DETAILS);
    }

    /** Key state read straight from the window: key mappings are not updated while a screen is open. */
    public static boolean detailsDown() {
        if (DETAILS.isUnbound()) {
            return true;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        InputConstants.Key key = DETAILS.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        List<Component> tooltip = event.getToolTip();
        if (!Lacuna.MODID.equals(id.getNamespace()) || tooltip.isEmpty()) {
            return;
        }
        String tipKey = "item.lacuna." + id.getPath() + ".tip";
        boolean hasTip = I18n.exists(tipKey);
        // vanilla adds the item's own lines right after the name; rebuild them to know which lines to fold
        List<Component> own = new ArrayList<>();
        if (!stack.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
            stack.getItem().appendHoverText(stack, event.getContext(), own, event.getFlags());
        }
        if (own.isEmpty() && !hasTip) {
            return;
        }
        if (detailsDown()) {
            if (hasTip) {
                tooltip.add(Math.min(1, tooltip.size()), Component.translatable(tipKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            return;
        }
        fold(tooltip, own);
        tooltip.add(Math.min(1, tooltip.size()), Component.translatable("lacuna.tooltip.hold_details",
                DETAILS.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Removes the item's own lines: the block right after the name, or else each line where it is found. */
    private static void fold(List<Component> tooltip, List<Component> own) {
        int n = own.size();
        if (n == 0) {
            return;
        }
        if (tooltip.size() > n && tooltip.subList(1, 1 + n).equals(own)) {
            tooltip.subList(1, 1 + n).clear();
            return;
        }
        for (Component line : own) {
            int at = tooltip.indexOf(line);
            if (at > 0) {
                tooltip.remove(at);
            }
        }
    }
}
