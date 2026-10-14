package com.lacuna.mod.pearl;

import com.lacuna.mod.LacunaConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Right click: the pearl splits into a matched Arrival Shard (right half) and Departure Shard (left half). */
public class FracturedPearlItem extends Item {
    public FracturedPearlItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.PEARL_ENABLED)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide()) {
            ItemStack[] pair = PearlLinks.split();
            stack.consume(1, player);
            // Vanilla empties the hand after a use that returns an empty stack, which would wipe a half given to that
            // (now free) slot: when the last pearl is spent, the hand keeps the first half and the second is handed over.
            ItemStack result = stack;
            int next = 0;
            if (stack.isEmpty()) {
                result = pair[0];
                next = 1;
            }
            for (int i = next; i < pair.length; i++) {
                com.lacuna.mod.util.Give.toPlayer(player, pair[i]);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
            // the two halves ring apart, still tuned to each other
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.8F, 1.2F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.6F, 0.9F);
            player.displayClientMessage(Component.translatable("lacuna.pearl.split"), true);
            com.lacuna.mod.util.LacunaAdvancements.award(player, "lacuna/link_pearl");
            return InteractionResultHolder.success(result);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.fractured_pearl.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.fractured_pearl.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
