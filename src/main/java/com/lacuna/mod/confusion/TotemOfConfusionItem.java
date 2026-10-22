package com.lacuna.mod.confusion;

import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Totem of Confusion: right click (like the Totem of Banishing) and five copies of the player appear around them
 * ({@link Confusion#activate}). {@link Confusion#USES} uses per totem, {@link Confusion#COOLDOWN} ticks apart; it cannot be repaired.
 */
public class TotemOfConfusionItem extends Item {
    public TotemOfConfusionItem(Properties properties) {
        super(properties);
    }

    /** Right click, like the Totem of Banishing: the copies appear at once. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            if (!SafeRun.call(Confusion.FEATURE, () -> Confusion.activate(sp), false)) {
                return InteractionResultHolder.fail(stack);
            }
            Sfx.at(player, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.7F, 1.3F);
            player.getCooldowns().addCooldown(this, Confusion.COOLDOWN);
            player.awardStat(Stats.ITEM_USED.get(this));
            level.gameEvent(GameEvent.ITEM_INTERACT_FINISH, player.position(), GameEvent.Context.of(player));
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.totem_of_confusion.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.totem_of_confusion.use", Confusion.COPIES).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.lacuna.totem_of_confusion.swap", Math.round(Confusion.SWAP_CHANCE * 100.0F))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.totem_of_confusion.mimic").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.totem_of_confusion.copies", Confusion.HITS, Confusion.LIFETIME / 20)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.totem_of_confusion.lure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.totem_of_confusion.cooldown", Confusion.COOLDOWN / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lacuna.totem_of_confusion.uses", stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
