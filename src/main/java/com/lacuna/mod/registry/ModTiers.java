package com.lacuna.mod.registry;

import com.lacuna.mod.Balance;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public final class ModTiers {
    /** Ruby: diamond mining level and damage bonus, 800 uses, speed 7, enchantability 15. */
    public static final Tier RUBY = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, Balance.RUBY_TOOL_DURABILITY,
            Balance.RUBY_TOOL_SPEED, Balance.RUBY_TOOL_DAMAGE_BONUS, Balance.RUBY_TOOL_ENCHANTABILITY,
            () -> Ingredient.of(ModItems.RUBY.get()));

    private ModTiers() {}
}
