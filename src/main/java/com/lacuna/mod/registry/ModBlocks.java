package com.lacuna.mod.registry;

import com.lacuna.mod.fl.AshenSnowBlock;
import com.lacuna.mod.gear.GraspingRootsBlock;
import com.lacuna.mod.fl.FlatDecorBlock;
import com.lacuna.mod.fl.SporeMushroomBlock;
import com.lacuna.mod.fl.UpdraftSpringBlock;
import com.lacuna.mod.mob.SilkDustGlowBlock;
import com.lacuna.mod.structure.LabyrinthTriggerBlock;
import com.lacuna.mod.village.CivicBlock;
import com.lacuna.mod.village.GuildBoardBlock;
import com.lacuna.mod.village.JudgeBenchBlock;
import com.lacuna.mod.village.VillageLedgerBlock;
import net.minecraft.world.phys.shapes.Shapes;
import com.lacuna.mod.structure.ObeliskInscriptionBlock;
import com.lacuna.mod.structure.SunkenBellBlock;
import net.minecraft.world.level.block.IceBlock;

import com.lacuna.mod.Balance;
import com.lacuna.mod.block.MuralBlock;
import com.lacuna.mod.lantern.NullLanternBlock;
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

    // ------------------------------------------------------------------ M4 umbral steel
    public static final DeferredBlock<Block> UMBRAL_STEEL_ORE = block("umbral_steel_ore", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE).strength(4.0F, 3.0F)));
    public static final DeferredBlock<Block> DEEPSLATE_UMBRAL_STEEL_ORE = block("deepslate_umbral_steel_ore", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE).strength(5.5F, 3.0F)));
    public static final DeferredBlock<Block> UMBRAL_STEEL_BLOCK = block("umbral_steel_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).mapColor(MapColor.COLOR_BLUE)));
    public static final DeferredBlock<Block> RAW_UMBRAL_STEEL_BLOCK = block("raw_umbral_steel_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK).mapColor(MapColor.COLOR_BLUE)));

    // ------------------------------------------------------------------ M5 fractured pearl
    public static final DeferredBlock<ArrivalAnchorBlock> ARRIVAL_ANCHOR = blockNoItem("arrival_anchor", () -> new ArrivalAnchorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5F, 1200.0F).noOcclusion().noLootTable()
                    .lightLevel(s -> 6).sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never)));

    // ------------------------------------------------------------------ M6 null lantern (its item is ModItems.NULL_LANTERN)
    public static final DeferredBlock<NullLanternBlock> NULL_LANTERN = blockNoItem("null_lantern", () -> new NullLanternBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).forceSolidOn().strength(1.5F, 3.5F).sound(SoundType.LANTERN)
                    .lightLevel(s -> s.getValue(NullLanternBlock.LIT) ? Balance.LANTERN_LIGHT : 0).noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    // ------------------------------------------------------------------ M7 zenithite
    public static final DeferredBlock<DropExperienceBlock> ZENITHITE_ORE = block("zenithite_ore", () -> new DropExperienceBlock(
            UniformInt.of(2, 5), BlockBehaviour.Properties.ofFullCopy(Blocks.LAPIS_ORE).mapColor(MapColor.COLOR_LIGHT_BLUE)));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_ZENITHITE_ORE = block("deepslate_zenithite_ore", () -> new DropExperienceBlock(
            UniformInt.of(2, 5), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_LAPIS_ORE).mapColor(MapColor.COLOR_LIGHT_BLUE)));
    public static final DeferredBlock<Block> ZENITHITE_BLOCK = block("zenithite_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).mapColor(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 3)));

    // ------------------------------------------------------------------ M7 heartwood bow: temporary grasping roots (no item)
    public static final DeferredBlock<GraspingRootsBlock> GRASPING_ROOTS = blockNoItem("grasping_roots", () -> new GraspingRootsBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).noCollission().instabreak().noOcclusion().noLootTable()
                    .sound(SoundType.HANGING_ROOTS).replaceable().randomTicks().ignitedByLava().pushReaction(PushReaction.DESTROY)
                    .isValidSpawn(Blocks::never)));

    // ------------------------------------------------------------------ M9 silk dust decal
    public static final DeferredBlock<SilkDustGlowBlock> SILK_DUST_GLOW = blockNoItem("silk_dust_glow", () -> new SilkDustGlowBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).noCollission().instabreak().noOcclusion().noLootTable()
                    .lightLevel(s -> 9).sound(SoundType.AMETHYST_CLUSTER).replaceable().pushReaction(PushReaction.DESTROY)
                    .isValidSpawn(Blocks::never)));

    // ------------------------------------------------------------------ M10 Forgotten Land biomes
    public static final DeferredBlock<AshenSnowBlock> ASHEN_SNOW_BLOCK = block("ashen_snow_block", () -> new AshenSnowBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW_BLOCK).mapColor(MapColor.COLOR_LIGHT_GRAY)));
    public static final DeferredBlock<IceBlock> CLEAR_ICE = block("clear_ice", () -> new IceBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.ICE).mapColor(MapColor.ICE)));
    public static final DeferredBlock<UpdraftSpringBlock> UPDRAFT_SPRING = block("updraft_spring", () -> new UpdraftSpringBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5F, 6.0F).noCollission().noOcclusion()
                    .lightLevel(s -> 5).sound(SoundType.WET_GRASS).pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<SporeMushroomBlock> SPORE_MUSHROOM = block("spore_mushroom", () -> new SporeMushroomBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.BROWN_MUSHROOM).mapColor(MapColor.COLOR_CYAN).lightLevel(s -> 7)
                    .randomTicks()));
    public static final DeferredBlock<FlatDecorBlock> BURIED_WATCH = block("buried_watch", () -> new FlatDecorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.8F).sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.DESTROY), 6.0));
    public static final DeferredBlock<FlatDecorBlock> RUSTED_GEAR = block("rusted_gear", () -> new FlatDecorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.0F).sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.DESTROY), 2.0));

    // ------------------------------------------------------------------ M10 structures
    public static final DeferredBlock<SunkenBellBlock> SUNKEN_BELL = block("sunken_bell", () -> new SunkenBellBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_STEM).strength(5.0F, 6.0F).sound(SoundType.ANVIL)
                    .noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<ObeliskInscriptionBlock> OBELISK_INSCRIPTION = block("obelisk_inscription",
            () -> new ObeliskInscriptionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F)
                    .noLootTable().lightLevel(s -> 3).sound(SoundType.POLISHED_DEEPSLATE).pushReaction(PushReaction.BLOCK)
                    .isValidSpawn(Blocks::never)));

    public static final DeferredBlock<TransparentBlock> MIRROR_GLASS = block("mirror_glass", () -> new TransparentBlock(
            mirrorProps()));
    public static final DeferredBlock<TransparentBlock> LABYRINTH_EXIT = block("labyrinth_exit", () -> new TransparentBlock(
            mirrorProps().lightLevel(s -> 4)));
    public static final DeferredBlock<LabyrinthTriggerBlock> LABYRINTH_TRIGGER = block("labyrinth_trigger", () -> new LabyrinthTriggerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(-1.0F, 3600000.0F).noCollission()
                    .noLootTable().lightLevel(s -> 6).sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK)));

    // ------------------------------------------------------------------ M11 village civic system
    public static final DeferredBlock<VillageLedgerBlock> VILLAGE_LEDGER = block("village_ledger", () -> new VillageLedgerBlock(
            civicProps(MapColor.WOOD, SoundType.WOOD)));
    public static final DeferredBlock<CivicBlock> COUNCIL_TABLE = block("council_table", () -> new CivicBlock(
            civicProps(MapColor.WOOD, SoundType.WOOD), Shapes.or(Block.box(0, 12, 0, 16, 16, 16), Block.box(6, 0, 6, 10, 12, 10))));
    public static final DeferredBlock<JudgeBenchBlock> JUDGE_BENCH = block("judge_bench", () -> new JudgeBenchBlock(
            civicProps(MapColor.COLOR_BROWN, SoundType.WOOD), Block.box(0, 0, 0, 16, 14, 16)));
    public static final DeferredBlock<GuildBoardBlock> GUILD_BOARD = block("guild_board", () -> new GuildBoardBlock(
            civicProps(MapColor.WOOD, SoundType.WOOD), Block.box(0, 0, 6, 16, 16, 10)));
    public static final DeferredBlock<CivicBlock> SCHOOL_DESK = block("school_desk", () -> new CivicBlock(
            civicProps(MapColor.WOOD, SoundType.WOOD), Shapes.or(Block.box(0, 10, 0, 16, 13, 16), Block.box(1, 0, 1, 15, 10, 3))));
    public static final DeferredBlock<CivicBlock> MINT_PRESS = block("mint_press", () -> new CivicBlock(
            civicProps(MapColor.METAL, SoundType.ANVIL).requiresCorrectToolForDrops(), Shapes.or(Block.box(1, 0, 1, 15, 6, 15),
                    Block.box(6, 6, 6, 10, 16, 10))));

    // ------------------------------------------------------------------ M2 Forgotten Portal (lit with the Totem of Banishing)
    public static final DeferredBlock<com.lacuna.mod.banish.ForgottenPortalFrameBlock> FORGOTTEN_PORTAL_FRAME = block("forgotten_portal_frame",
            () -> new com.lacuna.mod.banish.ForgottenPortalFrameBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS).pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(com.lacuna.mod.banish.ForgottenPortalFrameBlock.ACTIVE) ? 7 : 0)));
    public static final DeferredBlock<com.lacuna.mod.banish.ForgottenPortalBlock> FORGOTTEN_PORTAL = blockNoItem("forgotten_portal",
            () -> new com.lacuna.mod.banish.ForgottenPortalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .noCollission().noOcclusion().strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> 11)
                    .sound(SoundType.GLASS).pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never)));

    // ------------------------------------------------------------------ Chrono Hourglass (two blocks tall, chrono/)
    public static final DeferredBlock<com.lacuna.mod.chrono.ChronoHourglassBlock> CHRONO_HOURGLASS = block("chrono_hourglass",
            () -> new com.lacuna.mod.chrono.ChronoHourglassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD)
                    .strength(1.5F, 6.0F).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn(Blocks::never).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));
    // ------------------------------------------------------------------ Star Rock: what a falling star leaves behind, molten -> prime -> extinguished (star/StarRock)
    public static final DeferredBlock<com.lacuna.mod.star.MoltenStarRockBlock> MOLTEN_STAR_ROCK = block("molten_star_rock",
            () -> new com.lacuna.mod.star.MoltenStarRockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F).sound(SoundType.BASALT).lightLevel(s -> 15).requiresCorrectToolForDrops()
                    .emissiveRendering((s, l, p) -> true).isValidSpawn((s, l, p, t) -> t.fireImmune())));
    public static final DeferredBlock<com.lacuna.mod.star.PrimeStarRockBlock> PRIME_STAR_ROCK = block("prime_star_rock",
            () -> new com.lacuna.mod.star.PrimeStarRockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD)
                    .strength(1.5F, 6.0F).sound(SoundType.AMETHYST).lightLevel(s -> 10).requiresCorrectToolForDrops()
                    .isValidSpawn(Blocks::never)));
    public static final DeferredBlock<Block> EXTINGUISHED_STAR_ROCK = block("extinguished_star_rock", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.5F, 6.0F).sound(SoundType.TUFF)
                    .requiresCorrectToolForDrops()));
    static {
        // the old 3D Fallen Star lump (block and item) loads as Prime Star Rock in existing worlds
        ModRegistries.BLOCKS.addAlias(com.lacuna.mod.Lacuna.id("fallen_star"), com.lacuna.mod.Lacuna.id("prime_star_rock"));
        ModRegistries.ITEMS.addAlias(com.lacuna.mod.Lacuna.id("fallen_star"), com.lacuna.mod.Lacuna.id("prime_star_rock"));
    }
    // ------------------------------------------------------------------ Exilite: the totem's own stone, deep in the Forgotten Land (fl/Exilite)
    // debris and Block of Exilite never burn: their items (fire resistant, like ancient debris) are in ModItems
    public static final DeferredBlock<Block> EXILITE_DEBRIS = blockNoItem("exilite_debris", () -> new Block(
            com.lacuna.mod.fl.Exilite.debris()));
    public static final DeferredBlock<Block> EXILITE_BLOCK = blockNoItem("exilite_block", () -> new Block(
            com.lacuna.mod.fl.Exilite.block()));
    static {
        // the old Exilite Ore (block and item) loads as Exilite Debris in existing worlds
        ModRegistries.BLOCKS.addAlias(com.lacuna.mod.Lacuna.id("exilite_ore"), com.lacuna.mod.Lacuna.id("exilite_debris"));
        ModRegistries.ITEMS.addAlias(com.lacuna.mod.Lacuna.id("exilite_ore"), com.lacuna.mod.Lacuna.id("exilite_debris"));
    }
    public static final DeferredBlock<Block> EXILITE = block("exilite", () -> new Block(com.lacuna.mod.fl.Exilite.stone()));
    public static final DeferredBlock<Block> POLISHED_EXILITE = block("polished_exilite", () -> new Block(com.lacuna.mod.fl.Exilite.stone()));
    public static final DeferredBlock<Block> EXILITE_BRICKS = block("exilite_bricks", () -> new Block(com.lacuna.mod.fl.Exilite.stone()));
    public static final DeferredBlock<Block> CHISELED_EXILITE = block("chiseled_exilite", () -> new Block(com.lacuna.mod.fl.Exilite.stone()));
    public static final DeferredBlock<StairBlock> EXILITE_BRICK_STAIRS = block("exilite_brick_stairs", () -> new StairBlock(
            EXILITE_BRICKS.get().defaultBlockState(), com.lacuna.mod.fl.Exilite.stone()));
    public static final DeferredBlock<SlabBlock> EXILITE_BRICK_SLAB = block("exilite_brick_slab", () -> new SlabBlock(
            com.lacuna.mod.fl.Exilite.stone()));
    public static final DeferredBlock<WallBlock> EXILITE_BRICK_WALL = block("exilite_brick_wall", () -> new WallBlock(
            com.lacuna.mod.fl.Exilite.stone().forceSolidOn()));

    private static BlockBehaviour.Properties civicProps(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.5F, 6.0F).sound(sound).noOcclusion()
                .pushReaction(PushReaction.BLOCK).ignitedByLava();
    }

    /** Labyrinth mirrors: unbreakable in survival (the maze would be pointless otherwise), see-through, no drops. */
    private static BlockBehaviour.Properties mirrorProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
                .sound(SoundType.GLASS).pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never)
                .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

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
