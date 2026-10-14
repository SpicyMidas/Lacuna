package com.lacuna.mod.pearl;

import com.lacuna.mod.LacunaConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
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
            // When the last pearl is spent its slot keeps the first half (set now, so nothing else lands there and
            // vanilla's "empty hand after use" never wipes it); every other half is placed where the player sees it.
            ItemStack result = stack;
            int first = 0;
            if (stack.isEmpty()) {
                result = pair[0];
                player.setItemInHand(hand, result);
                first = 1;
            }
            Placed worst = Placed.HOTBAR;
            for (int i = first; i < pair.length; i++) {
                Placed placed = place(player, pair[i]);
                if (placed.ordinal() > worst.ordinal()) {
                    worst = placed;
                }
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
            // the two halves ring apart, still tuned to each other
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.8F, 1.2F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.6F, 0.9F);
            player.displayClientMessage(Component.translatable(worst.message), true);
            com.lacuna.mod.util.LacunaAdvancements.award(player, "lacuna/link_pearl");
            return InteractionResultHolder.success(result);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Where a shard ended up, from most to least visible; the worst case picks the message. */
    private enum Placed {
        HOTBAR("lacuna.pearl.split"), INVENTORY("lacuna.pearl.split_inventory"), GROUND("lacuna.pearl.split_ground");

        final String message;

        Placed(String message) {
            this.message = message;
        }
    }

    /**
     * Puts a shard in the first free hotbar slot nearest the selected one, else the empty off hand, else the main
     * inventory, else at the player's feet. Slots are set directly: {@code Inventory.add} silently deletes items in
     * creative mode when it finds no room.
     */
    private static Placed place(Player player, ItemStack shard) {
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        for (int d = 0; d < Inventory.getSelectionSize(); d++) {
            for (int slot : new int[] {selected - d, selected + d}) {
                if (slot >= 0 && slot < Inventory.getSelectionSize() && inventory.getItem(slot).isEmpty()) {
                    inventory.setItem(slot, shard);
                    return Placed.HOTBAR;
                }
            }
        }
        if (player.getOffhandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, shard);
            return Placed.HOTBAR;
        }
        for (int slot = Inventory.getSelectionSize(); slot < inventory.items.size(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, shard);
                return Placed.INVENTORY;
            }
        }
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + 0.25, player.getZ(), shard, 0.0, 0.1, 0.0);
        dropped.setPickUpDelay(20);
        player.level().addFreshEntity(dropped);
        return Placed.GROUND;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.fractured_pearl.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.fractured_pearl.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
