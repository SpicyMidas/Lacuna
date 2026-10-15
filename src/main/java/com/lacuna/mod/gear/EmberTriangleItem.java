package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Ember Triangle: use to make the triangle flare ({@link Balance#EMBER_TRIANGLE_WINDUP} ticks) and release a circular
 * thermal wave that runs {@link Balance#EMBER_TRIANGLE_RADIUS} blocks out along the ground and pulverizes every
 * hostile creature, everything hunting the wielder and (with PvP on) other players it catches; bosses are scorched
 * instead. 16 uses, 60 s cooldown, repaired at an anvil with Ember (util/AnvilRepairs, so no vanilla repair material).
 * The wave lives in {@link EmberWave}; the visuals in client/gear/EmberTriangleFx and client/gear/EmberDust.
 */
public class EmberTriangleItem extends Item {
    public EmberTriangleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return InteractionResultHolder.pass(stack);
        }
        player.getCooldowns().addCooldown(this, Balance.EMBER_TRIANGLE_COOLDOWN);
        if (level instanceof ServerLevel server) {
            SafeRun.run(EmberWave.FEATURE, () -> {
                EmberWave.start(server, player);
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.ember_triangle.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.ember_triangle.use", (int) Balance.EMBER_TRIANGLE_RADIUS)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.lacuna.ember_triangle.use_detail").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.ember_triangle.spare").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.ember_triangle.pvp").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lacuna.ember_triangle.boss", (int) Balance.EMBER_TRIANGLE_BOSS_DAMAGE)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lacuna.ember_triangle.cooldown", Balance.EMBER_TRIANGLE_COOLDOWN / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lacuna.ember_triangle.repair", Balance.EMBER_TRIANGLE_REPAIR_PER_EMBER)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
