package com.lacuna.mod.banish;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Totem of Banishing (spec 9.2): right click to enter/leave the Forgotten Land, melee hit to banish a target, right
 * click a ring of Forgotten Portal Frames to light the portal ({@link ForgottenPortals}).
 * 6 uses in total (1 per transfer or lit portal, never repaired, never enchantable); a short cooldown after banishing a
 * target and an even shorter one after crossing yourself or lighting a portal. Every use tolls the banishing bell.
 */
public class TotemOfBanishingItem extends Item implements ForgottenLandRules.NoAnvilMerge {
    public TotemOfBanishingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        if (BanishmentManager.useTotemSelf(serverPlayer)) {
            spend(stack, serverPlayer, hand, Balance.TOTEM_SELF_COOLDOWN);
            toll(serverPlayer);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    /** On a portal frame the totem lights the ring instead of crossing (any other block: the usual crossing). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!ForgottenPortals.isFrame(level.getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (ForgottenPortals.openWithTotem(player, serverLevel, context.getClickedPos())) {
            spend(context.getItemInHand(), player, context.getHand(), Balance.TOTEM_SELF_COOLDOWN);
            toll(player);
            com.lacuna.mod.util.LacunaAdvancements.award(player, "lacuna/open_portal");
        }
        return InteractionResult.CONSUME; // handled either way: never fall through to a crossing
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level().isClientSide() || !(attacker instanceof ServerPlayer player)) {
            return false;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (BanishmentManager.queueBanish(player, target)) {
            spend(stack, player, InteractionHand.MAIN_HAND, Balance.TOTEM_COOLDOWN);
            toll(player);
            return true;
        }
        return false;
    }

    private void spend(ItemStack stack, ServerPlayer player, InteractionHand hand, int cooldown) {
        player.getCooldowns().addCooldown(this, cooldown);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
    }

    /** The bell rings where the user stands now (after a crossing: on the other side, so they hear it arrive). */
    static void toll(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TOTEM_BELL.get(), SoundSource.PLAYERS,
                1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return false; // a limited number of uses: the totem wears out for good
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
        tooltip.add(Component.translatable("item.lacuna.totem_of_banishing.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.totem_of_banishing.use").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.lacuna.totem_of_banishing.portal").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.lacuna.totem_of_banishing.wear").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
