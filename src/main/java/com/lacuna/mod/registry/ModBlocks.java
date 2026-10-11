package com.lacuna.mod.registry;

import java.util.function.Supplier;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/** All Lacuna blocks. {@code block(...)} also registers a plain BlockItem; {@code blockNoItem(...)} does not. */
public final class ModBlocks {
    // ------------------------------------------------------------------ M1 ruby
    public static final DeferredBlock<DropExperienceBlock> RUBY_ORE = block("ruby_ore", () -> new DropExperienceBlock(
            UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE).strength(3.0F, 3.0F)));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_RUBY_ORE = block("deepslate_ruby_ore", () -> new DropExperienceBlock(
            UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE).strength(4.5F, 3.0F)));
    public static final DeferredBlock<Block> RUBY_BLOCK = block("ruby_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_BLOCK).mapColor(MapColor.COLOR_RED)));
    public static final DeferredBlock<Block> RAW_RUBY_BLOCK = block("raw_ruby_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK).mapColor(MapColor.COLOR_RED)));

    // ------------------------------------------------------------------ M1 divine glass
    public static final DeferredBlock<TransparentBlock> DIVINE_GLASS = block("divine_glass", () -> new TransparentBlock(
            divineGlassProps()));
    public static final DeferredBlock<IronBarsBlock> DIVINE_GLASS_PANE = block("divine_glass_pane", () -> new IronBarsBlock(
            divineGlassProps()));
    public static final DeferredBlock<AmethystClusterBlock> DIVINE_GLASS_CLUSTER = block("divine_glass_cluster", () -> new AmethystClusterBlock(
            7.0F, 3.0F, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).forceSolidOn().noOcclusion()
                    .sound(SoundType.AMETHYST_CLUSTER).strength(1.5F).lightLevel(s -> 5).pushReaction(PushReaction.DESTROY)));

    private ModBlocks() {}

    static void init() {
        // class-loading this holder registers every block above
    }

    private static BlockBehaviour.Properties divineGlassProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.GOLD)
                .strength(0.5F, 6.0F)
                .sound(SoundType.GLASS)
                .lightLevel(s -> 7)
                .noOcclusion()
                .isValidSpawn(Blocks::never)
                .isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false);
    }

    static <B extends Block> DeferredBlock<B> block(String id, Supplier<B> factory) {
        DeferredBlock<B> holder = ModRegistries.BLOCKS.register(id, factory);
        ModRegistries.ITEMS.registerSimpleBlockItem(id, holder);
        return holder;
    }

    static <B extends Block> DeferredBlock<B> blockNoItem(String id, Supplier<B> factory) {
        return ModRegistries.BLOCKS.register(id, factory);
    }
}
