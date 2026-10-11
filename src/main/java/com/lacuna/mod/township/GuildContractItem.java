package com.lacuna.mod.township;

import com.lacuna.mod.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A guild contract: hand it in at the guild board together with the requested items. */
public class GuildContractItem extends Item {
    public GuildContractItem(Properties properties) {
        super(properties);
    }

    public static ContractData data(ItemStack stack) {
        return stack.getOrDefault(ModComponents.GUILD_CONTRACT.get(), ContractData.EMPTY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ContractData d = data(stack);
        if (d.isEmpty()) {
            tooltip.add(Component.translatable("item.lacuna.guild_contract.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(d.itemId());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        Component name = item == null ? Component.literal(d.itemId()) : item.getDescription();
        tooltip.add(Component.translatable("item.lacuna.guild_contract.deliver", d.count(), name).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.lacuna.guild_contract.reward", d.rewardCoins()).withStyle(ChatFormatting.GRAY));
        if (!d.guild().isEmpty()) {
            tooltip.add(Component.translatable("lacuna.guild." + d.guild()).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
