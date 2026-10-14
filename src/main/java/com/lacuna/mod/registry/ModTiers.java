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

    /** Umbral Steel: netherite mining level, 1500 uses, speed 8.5, damage bonus 4, enchantability 15 (stronger than Ruby). */
    public static final Tier UMBRAL = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, Balance.UMBRAL_TOOL_DURABILITY,
            Balance.UMBRAL_TOOL_SPEED, Balance.UMBRAL_TOOL_DAMAGE_BONUS, Balance.UMBRAL_TOOL_ENCHANTABILITY,
            () -> Ingredient.of(ModItems.UMBRAL_STEEL_INGOT.get()));

    /** Eclipser: its own tier so TieredItem uses the Eclipser's 250 durability; repaired with Umbral Steel. */
    public static final Tier ECLIPSER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, Balance.ECLIPSER_DURABILITY,
            Balance.ECLIPSER_TOOL_SPEED, 0.0F, 10, () -> Ingredient.of(ModItems.UMBRAL_STEEL_INGOT.get()));

    /** Special weapons get their own tiers so TieredItem uses their exact durability and repair material. */
    public static final Tier GRAVEMAW = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, Balance.GRAVEMAW_DURABILITY,
            6.0F, 0.0F, 10, () -> Ingredient.of(ModItems.UMBRAL_STEEL_INGOT.get()));
    public static final Tier TIDECALLER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, Balance.SICKLE_DURABILITY,
            6.0F, 0.0F, 10, () -> Ingredient.of(net.minecraft.world.item.Items.PRISMARINE_SHARD));
    public static final Tier ECHO_DAGGER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, Balance.ECHO_DAGGER_DURABILITY,
            6.0F, 0.0F, 10, () -> Ingredient.of(ModItems.ZENITHITE_SHARD.get()));

    private ModTiers() {}
}
