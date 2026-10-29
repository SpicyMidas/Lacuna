package com.lacuna.mod.guide;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * The Lacuna Almanac: right click opens the guide, a client-only book screen ({@code client/guide/AlmanacScreen}) that
 * reads its chapters and entries from {@code assets/lacuna/almanac/entries.json}. Nothing happens on the server.
 */
public class AlmanacItem extends Item {
    public AlmanacItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && FMLEnvironment.dist.isClient()) {
            com.lacuna.mod.client.guide.AlmanacClient.open();
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.almanac.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.almanac.use").withStyle(ChatFormatting.GRAY));
    }
}
