package com.lacuna.mod.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * Fixed-amount anvil repairs ("repair at an anvil with X: +N durability each", spec M3/M5/M6). Items using this
 * must return false from isValidRepairItem so the vanilla 25 % repair does not apply as well.
 */
public final class AnvilRepairs {
    private record Rule(Supplier<? extends Item> item, Supplier<? extends Item> material, int perItem) {}

    private static final List<Rule> RULES = new ArrayList<>();

    private AnvilRepairs() {}

    public static void add(Supplier<? extends Item> item, Supplier<? extends Item> material, int perItem) {
        RULES.add(new Rule(item, material, perItem));
    }

    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        for (Rule rule : RULES) {
            if (!left.is(rule.item().get())) {
                continue;
            }
            if (right.is(rule.item().get())) {
                event.setCanceled(true); // item + item merging is disabled for Lacuna relics
                return;
            }
            if (!right.is(rule.material().get()) || !left.isDamaged()) {
                return;
            }
            int damage = left.getDamageValue();
            int needed = (int) Math.ceil(damage / (double) rule.perItem());
            int used = Math.min(needed, right.getCount());
            ItemStack out = left.copy();
            out.setDamageValue(Math.max(0, damage - used * rule.perItem()));
            event.setOutput(out);
            event.setMaterialCost(used);
            event.setCost(Math.max(1, used));
            return;
        }
    }
}
