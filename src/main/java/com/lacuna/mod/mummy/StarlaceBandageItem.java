package com.lacuna.mod.mummy;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Starlace Bandage: used on a creature, it wraps it in starlace until it is a mummy. The wrapping itself happens in
 * {@link Mummification} (an interact event, so it wins over villager trading, riding horses and the like).
 */
public class StarlaceBandageItem extends Item {
    public StarlaceBandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.starlace_bandage.use").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.lacuna.starlace_bandage.wraps").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.starlace_bandage.hurry", Mummification.PARTIAL_TICKS / 20)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.starlace_bandage.mummy", (int) (100 * (1.0F - Mummification.DAMAGE_TAKEN)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.starlace_bandage.shears").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lacuna.starlace_bandage.refuse").withStyle(ChatFormatting.DARK_GRAY));
    }
}
