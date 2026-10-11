package com.lacuna.mod.gear;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** "None enchantable except Unbreaking" (spec M7) shared check. */
public final class Enchants {
    private Enchants() {}

    public static boolean unbreakingOnly(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.UNBREAKING);
    }
}
