package com.lacuna.mod.lantern;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Null Lantern (spec M6, revision 4). Its durability is its <em>charge</em>.
 * <ul>
 * <li>Held in either hand while charged: threat mobs cannot target or hurt the holder at any distance, mobs hunting
 * them forget them, natural monster spawns within 24 blocks fail (see {@link LanternWard}). 1 charge / 200 ticks.</li>
 * <li>Placed like a vanilla lantern (standing or hanging): the charge moves into the block entity and the lit lantern
 * shelters every player within 12 blocks, burning charge at the same rate; breaking it gives the charge back.</li>
 * <li>Out of charge it goes dark instead of breaking. An Ender Pearl restores 50 %, an Echo Shard 100 %: click the
 * fuel onto the lantern in any inventory slot, or use it on the placed lantern.</li>
 * </ul>
 */
public class NullLanternItem extends BlockItem {
    private static final int BAR_COLOR = 0x8E45F2;

    public NullLanternItem(Block block, Properties properties) {
        super(block, properties);
    }

    // ------------------------------------------------------------------ charge

    public static int charge(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    public static boolean isLit(ItemStack stack) {
        return stack.getItem() instanceof NullLanternItem && charge(stack) > 0;
    }

    /** A charged lantern in either hand. */
    public static boolean holdsLit(Player player) {
        return isLit(player.getMainHandItem()) || isLit(player.getOffhandItem());
    }

    // ------------------------------------------------------------------ held behaviour

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof Player player) || !LanternWard.active()) {
            return;
        }
        InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND
                : player.getOffhandItem() == stack ? InteractionHand.OFF_HAND : null;
        if (hand == null || charge(stack) <= 0) {
            return; // a dark lantern does nothing at all
        }
        SafeRun.run(LanternWard.FEATURE, () -> heldTick(stack, (ServerLevel) level, player));
    }

    private static void heldTick(ItemStack stack, ServerLevel level, Player player) {
        long now = level.getGameTime();
        if (now % Balance.LANTERN_TICKS_PER_DURABILITY == 0 && !player.hasInfiniteMaterials()) {
            // never hurtAndBreak: the lantern goes dark at 0 charge, it does not break
            stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + 1));
            if (charge(stack) <= 0) {
                LanternWard.fizzle(level, player.position().add(0, player.getBbHeight() * 0.6, 0));
                player.displayClientMessage(Component.translatable("lacuna.lantern.out").withStyle(ChatFormatting.DARK_PURPLE), true);
                return;
            }
        }
        if (now % 100 == 0) {
            Sfx.at(player, SoundEvents.BEACON_AMBIENT, 0.25F, 0.5F); // the faint hum
        }
        if (now % 5 != 0) {
            // between the wide sweeps, mobs right next to the holder are checked every tick
            LanternWard.sweep(level, player, player.position(), 6.0);
            return;
        }
        LanternWard.sweep(level, player, player.position(), LanternWard.sweepRadius());
        if (now % 10 == 0) {
            // a faint veil of shadow around the holder, so everyone can tell the lantern is working
            level.sendParticles(ModParticles.SHADOW_MOTE.get(), player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.45, 0.6, 0.45, 0.005);
        }
    }

    // ------------------------------------------------------------------ recharging in an inventory

    /** Clicking an Ender Pearl or Echo Shard held on the cursor onto the lantern (any slot) feeds it one fuel item. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack lantern, ItemStack fuel, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (lantern.getCount() != 1 || fuel.isEmpty() || !slot.allowModification(player) || !LanternWard.active()) {
            return false;
        }
        int restore = LanternWard.fuelValue(fuel, lantern.getMaxDamage());
        if (restore <= 0 || lantern.getDamageValue() <= 0) {
            return false; // not fuel, or already full: normal click behaviour
        }
        return SafeRun.call(LanternWard.FEATURE, () -> {
            lantern.setDamageValue(Math.max(0, lantern.getDamageValue() - restore));
            fuel.shrink(1);
            LanternWard.rechargeCue(player);
            if (!player.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("lacuna.lantern.recharged",
                        LanternWard.percent(charge(lantern), lantern.getMaxDamage())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return true;
        }, false);
    }

    // ------------------------------------------------------------------ item details

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(ModItems.UMBRAL_STEEL_INGOT.get());
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.null_lantern.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.null_lantern.use").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.lacuna.null_lantern.recharge").withStyle(ChatFormatting.BLUE));
        int charge = charge(stack);
        if (charge > 0) {
            tooltip.add(Component.translatable("lacuna.lantern.charge", LanternWard.percent(charge, stack.getMaxDamage()), charge,
                    stack.getMaxDamage()).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable("lacuna.lantern.charge_out").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
