package com.lacuna.mod.pearl;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Shared tooltip/anvil behaviour of the two shard halves. */
public abstract class ShardItem extends Item {
    protected ShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = getDescriptionId();
        tooltip.add(Component.translatable(key + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        PearlLink link = PearlLinks.link(stack);
        if (link == null) {
            tooltip.add(Component.translatable("lacuna.pearl.unlinked").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("lacuna.pearl.linked", link.shortId()).withStyle(ChatFormatting.DARK_AQUA));
            if (link.cracked()) {
                tooltip.add(Component.translatable("lacuna.pearl.cracked").withStyle(ChatFormatting.RED));
            }
        }
        tooltip.add(Component.translatable(key + ".use").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return false; // fixed +3 per ender pearl, see AnvilRepairs
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        return false;
    }
}
