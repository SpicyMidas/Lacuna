package com.lacuna.mod.village;

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

/** Smiths' Guild repair kit: use it with a damaged item in the other hand to restore 25 % of its durability. */
public class RepairKitItem extends Item {
    public RepairKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack kit = player.getItemInHand(hand);
        ItemStack target = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!target.isDamageableItem() || !target.isDamaged()) {
            return InteractionResultHolder.pass(kit);
        }
        if (!level.isClientSide()) {
            int repair = Math.max(1, target.getMaxDamage() / 4);
            target.setDamageValue(Math.max(0, target.getDamageValue() - repair));
            kit.consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.1F);
        }
        return InteractionResultHolder.sidedSuccess(kit, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.repair_kit.use").withStyle(ChatFormatting.GRAY));
    }
}
