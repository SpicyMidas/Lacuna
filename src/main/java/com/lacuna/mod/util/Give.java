package com.lacuna.mod.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Handing items to players without ever losing them. */
public final class Give {
    private Give() {}

    /**
     * Puts {@code stack} into the inventory and drops whatever does not fit at the player's feet. Vanilla's
     * {@code Inventory.add} silently deletes items in creative mode when nothing fits, so it is only called when there
     * is room.
     */
    public static void toPlayer(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getFreeSlot() >= 0 || inventory.getSlotWithRemainingSpace(stack) >= 0) {
            inventory.add(stack);
        }
        if (!stack.isEmpty()) {
            player.drop(stack.copyAndClear(), false);
        }
    }
}
