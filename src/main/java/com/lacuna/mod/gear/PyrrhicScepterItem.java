package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Pyrrhic Scepter (spec M7): spends 2 HP (not at <= 4 HP) to fire a homing bolt that deals
 * 5 + (1 - health / maxHealth) x 10 damage and burns for 2 s. Cooldown 20, 1 durability.
 */
public class PyrrhicScepterItem extends Item {
    public PyrrhicScepterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getHealth() <= Balance.SCEPTER_MIN_HEALTH && !player.getAbilities().instabuild) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("lacuna.scepter.too_weak"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide()) {
            if (!player.getAbilities().instabuild) {
                player.setHealth(player.getHealth() - Balance.SCEPTER_HEALTH_COST);
            }
            PyrrhicBoltEntity bolt = new PyrrhicBoltEntity(level, player, boltDamage(player));
            bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, Balance.BOLT_SPEED, 0.0F);
            level.addFreshEntity(bolt);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.3F);
            player.getCooldowns().addCooldown(this, Balance.SCEPTER_COOLDOWN);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** The bolt hits harder the more of its wielder's health is missing (after the price is paid). */
    public static float boltDamage(LivingEntity user) {
        return Balance.BOLT_BASE_DAMAGE + (1.0F - user.getHealth() / user.getMaxHealth()) * Balance.BOLT_MISSING_HEALTH_DAMAGE;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(net.minecraft.world.item.Items.GOLD_INGOT);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return Enchants.unbreakingOnly(enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return Enchants.unbreakingOnly(enchantment);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 10;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.pyrrhic_scepter.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.pyrrhic_scepter.use").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
