package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModTiers;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Umbral Steel tool set. Every tool is "Nightforged": +30 % mining speed while the light level at the wielder's eyes
 * is 7 or less. The pickaxe, shovel and hoe only carry that passive (sword and axe: {@link UmbralSwordItem},
 * {@link UmbralAxeItem}).
 */
public final class UmbralTools {
    private UmbralTools() {}

    /** True for any tool made on the Umbral Steel tier (sword, pickaxe, axe, shovel, hoe). */
    public static boolean isUmbralTool(ItemStack stack) {
        return stack.getItem() instanceof TieredItem tiered && tiered.getTier() == ModTiers.UMBRAL;
    }

    /** True where Nightforged is active: the light (block or sky) at the player's eyes is <= 7. */
    public static boolean inDarkness(Player player) {
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        return player.level().getMaxLocalRawBrightness(eyes) <= Balance.UMBRAL_NIGHTFORGED_LIGHT;
    }

    /** PlayerEvent.BreakSpeed (both sides, so the client's mining prediction matches): Nightforged. */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!isUmbralTool(player.getMainHandItem()) || !LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED) || !inDarkness(player)) {
            return;
        }
        event.setNewSpeed(event.getNewSpeed() * (1.0F + Balance.UMBRAL_NIGHTFORGED_BONUS));
    }

    static void appendNightforged(List<Component> tooltip) {
        tooltip.add(Component.translatable("item.lacuna.umbral_steel.nightforged",
                Math.round(Balance.UMBRAL_NIGHTFORGED_BONUS * 100) + "%", Balance.UMBRAL_NIGHTFORGED_LIGHT).withStyle(ChatFormatting.DARK_AQUA));
    }

    private static void appendTool(String id, List<Component> tooltip) {
        tooltip.add(Component.translatable("item.lacuna." + id + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        appendNightforged(tooltip);
    }

    public static class Pickaxe extends PickaxeItem {
        public Pickaxe(Tier tier, Properties properties) {
            super(tier, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            appendTool("umbral_steel_pickaxe", tooltip);
        }
    }

    public static class Shovel extends ShovelItem {
        public Shovel(Tier tier, Properties properties) {
            super(tier, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            appendTool("umbral_steel_shovel", tooltip);
        }
    }

    public static class Hoe extends HoeItem {
        public Hoe(Tier tier, Properties properties) {
            super(tier, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            appendTool("umbral_steel_hoe", tooltip);
        }
    }
}
