package com.lacuna.mod.wood;

import com.lacuna.mod.registry.ModWoodBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.neoforged.neoforge.registries.DeferredBlock;

/** Common-setup wiring for the wood families: flammability like vanilla (Cindervine excluded) and flower pots. */
public final class WoodSetup {
    private WoodSetup() {}

    public static void setup() {
        FireBlock fire = (FireBlock) Blocks.FIRE;
        flammable(fire, 5, 5, ModWoodBlocks.CRYSTAL_WILLOW_LOG, ModWoodBlocks.STRIPPED_CRYSTAL_WILLOW_LOG, ModWoodBlocks.CRYSTAL_WILLOW_WOOD,
                ModWoodBlocks.STRIPPED_CRYSTAL_WILLOW_WOOD, ModWoodBlocks.TIDEBLOOM_LOG, ModWoodBlocks.STRIPPED_TIDEBLOOM_LOG,
                ModWoodBlocks.TIDEBLOOM_WOOD, ModWoodBlocks.STRIPPED_TIDEBLOOM_WOOD, ModWoodBlocks.ECHO_BIRCH_LOG,
                ModWoodBlocks.STRIPPED_ECHO_BIRCH_LOG, ModWoodBlocks.ECHO_BIRCH_WOOD, ModWoodBlocks.STRIPPED_ECHO_BIRCH_WOOD);
        flammable(fire, 5, 20, ModWoodBlocks.CRYSTAL_WILLOW_PLANKS, ModWoodBlocks.CRYSTAL_WILLOW_STAIRS, ModWoodBlocks.CRYSTAL_WILLOW_SLAB,
                ModWoodBlocks.CRYSTAL_WILLOW_FENCE, ModWoodBlocks.CRYSTAL_WILLOW_FENCE_GATE, ModWoodBlocks.TIDEBLOOM_PLANKS,
                ModWoodBlocks.TIDEBLOOM_STAIRS, ModWoodBlocks.TIDEBLOOM_SLAB, ModWoodBlocks.TIDEBLOOM_FENCE, ModWoodBlocks.TIDEBLOOM_FENCE_GATE,
                ModWoodBlocks.ECHO_BIRCH_PLANKS, ModWoodBlocks.ECHO_BIRCH_STAIRS, ModWoodBlocks.ECHO_BIRCH_SLAB, ModWoodBlocks.ECHO_BIRCH_FENCE,
                ModWoodBlocks.ECHO_BIRCH_FENCE_GATE);
        flammable(fire, 30, 60, ModWoodBlocks.CRYSTAL_WILLOW_LEAVES, ModWoodBlocks.TIDEBLOOM_LEAVES, ModWoodBlocks.ECHO_BIRCH_LEAVES);
        // Cindervine leaves smoulder but never catch fire; Cindervine wood is fire-proof.

        FlowerPotBlock pot = (FlowerPotBlock) Blocks.FLOWER_POT;
        for (DeferredBlock<FlowerPotBlock> potted : ModWoodBlocks.pots()) {
            Block plant = potted.get().getPotted();
            pot.addPlant(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(plant), potted);
        }
    }

    @SafeVarargs
    private static void flammable(FireBlock fire, int encouragement, int flammability, DeferredBlock<? extends Block>... blocks) {
        for (DeferredBlock<? extends Block> b : blocks) {
            fire.setFlammable(b.get(), encouragement, flammability);
        }
    }
}
