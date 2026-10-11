package com.lacuna.mod.registry;

import com.lacuna.mod.block.MuralBlock;
import com.lacuna.mod.pearl.ArrivalAnchorBlock;
import java.util.function.Supplier;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
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

    // ------------------------------------------------------------------ M2 forgotten land ruins (unbreakable in survival)
    public static final DeferredBlock<Block> RUIN_STONE = block("ruin_stone", () -> new Block(ruinProps()));
    public static final DeferredBlock<Block> RUIN_STONE_BRICKS = block("ruin_stone_bricks", () -> new Block(ruinProps()));
    public static final DeferredBlock<Block> CRACKED_RUIN_STONE_BRICKS = block("cracked_ruin_stone_bricks", () -> new Block(ruinProps()));
    public static final DeferredBlock<RotatedPillarBlock> RUIN_PILLAR = block("ruin_pillar", () -> new RotatedPillarBlock(ruinProps()));
    public static final DeferredBlock<Block> CHISELED_RUIN_GLYPH = block("chiseled_ruin_glyph", () -> new Block(ruinProps()));
    public static final DeferredBlock<SlabBlock> RUIN_SLAB = block("ruin_slab", () -> new SlabBlock(ruinProps()));
    public static final DeferredBlock<StairBlock> RUIN_STAIRS = block("ruin_stairs", () -> new StairBlock(
            RUIN_STONE_BRICKS.get().defaultBlockState(), ruinProps()));
    public static final DeferredBlock<WallBlock> RUIN_WALL = block("ruin_wall", () -> new WallBlock(ruinProps().forceSolidOn()));
    public static final DeferredBlock<MuralBlock> MURAL_BLOCK_1 = block("mural_block_1", () -> new MuralBlock(ruinProps()));
    public static final DeferredBlock<MuralBlock> MURAL_BLOCK_2 = block("mural_block_2", () -> new MuralBlock(ruinProps()));
    public static final DeferredBlock<MuralBlock> MURAL_BLOCK_3 = block("mural_block_3", () -> new MuralBlock(ruinProps()));
    public static final DeferredBlock<MuralBlock> MURAL_BLOCK_4 = block("mural_block_4", () -> new MuralBlock(ruinProps()));
    public static final DeferredBlock<MuralBlock> MURAL_BLOCK_5 = block("mural_block_5", () -> new MuralBlock(ruinProps()));

    // ------------------------------------------------------------------ M4 umbral iron
    public static final DeferredBlock<Block> UMBRAL_IRON_ORE = block("umbral_iron_ore", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE).strength(4.0F, 3.0F)));
    public static final DeferredBlock<Block> DEEPSLATE_UMBRAL_IRON_ORE = block("deepslate_umbral_iron_ore", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE).strength(5.5F, 3.0F)));
    public static final DeferredBlock<Block> UMBRAL_IRON_BLOCK = block("umbral_iron_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).mapColor(MapColor.COLOR_BLUE)));
    public static final DeferredBlock<Block> RAW_UMBRAL_IRON_BLOCK = block("raw_umbral_iron_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK).mapColor(MapColor.COLOR_BLUE)));

    // ------------------------------------------------------------------ M5 fractured pearl
    public static final DeferredBlock<ArrivalAnchorBlock> ARRIVAL_ANCHOR = blockNoItem("arrival_anchor", () -> new ArrivalAnchorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5F, 1200.0F).noOcclusion().noLootTable()
                    .lightLevel(s -> 6).sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never)));

    private ModBlocks() {}

    /** destroyTime -1 (unbreakable in survival/adventure, creative can still break), immovable, blast-proof. */
    private static BlockBehaviour.Properties ruinProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.CLAY)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(-1.0F, 3600000.0F)
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn(Blocks::never)
                .sound(SoundType.DEEPSLATE_BRICKS);
    }

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
