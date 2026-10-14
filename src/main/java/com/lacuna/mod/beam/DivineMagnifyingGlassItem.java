package com.lacuna.mod.beam;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Divine Magnifying Glass (spec M3): hold right click to focus a beam of sunlight. 500 durability, 1 per 20 ticks
 * of beam, 10-tick cooldown after release, repaired with Divine Glass Shards (+60 each), not enchantable.
 */
public class DivineMagnifyingGlassItem extends Item {
    public DivineMagnifyingGlassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.BEAM_ENABLED)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide()) {
            com.lacuna.mod.util.Sfx.at(player, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 0.45F, 1.9F);
            com.lacuna.mod.util.Sfx.at(player, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Balance.GLASS_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide()) {
            return;
        }
        int ticks = getUseDuration(stack, entity) - remainingUseDuration;
        BeamHandler.tick(level, entity, stack, ticks);
        if (ticks > 0 && ticks % 30 == 0) {
            com.lacuna.mod.util.Sfx.at(entity, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, 0.35F, 1.8F);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof Player player) {
            player.getCooldowns().addCooldown(this, Balance.BEAM_RELEASE_COOLDOWN);
        }
        if (!level.isClientSide()) {
            com.lacuna.mod.util.Sfx.at(entity, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 0.4F, 1.9F);
            com.lacuna.mod.util.Sfx.at(entity, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 0.15F, 1.8F);
        }
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return false; // fixed +60 per shard, see AnvilRepairs
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
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.divine_magnifying_glass.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.divine_magnifying_glass.use").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
