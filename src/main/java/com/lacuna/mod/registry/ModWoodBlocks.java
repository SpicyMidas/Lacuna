package com.lacuna.mod.registry;

import com.lacuna.mod.wood.CrystalStrandsBlock;
import com.lacuna.mod.wood.EchoBirchSaplingBlock;
import com.lacuna.mod.wood.SmolderingLeavesBlock;
import com.lacuna.mod.wood.TidebloomRootsBlock;
import com.lacuna.mod.wood.WoodFamily;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * The four wood families (spec M8), registered explicitly so every id is greppable (tools/validate_assets.py).
 * Signs, hanging signs and boats are intentionally absent (spec: optional in M12).
 */
public final class ModWoodBlocks {
    // ------------------------------------------------------------------ cindervine
    public static final DeferredBlock<RotatedPillarBlock> CINDERVINE_LOG = block("cindervine_log", () -> new RotatedPillarBlock(log(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_CINDERVINE_LOG = block("stripped_cindervine_log", () -> new RotatedPillarBlock(log(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK)));
    public static final DeferredBlock<RotatedPillarBlock> CINDERVINE_WOOD = block("cindervine_wood", () -> new RotatedPillarBlock(log(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_CINDERVINE_WOOD = block("stripped_cindervine_wood", () -> new RotatedPillarBlock(log(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK)));
    public static final DeferredBlock<Block> CINDERVINE_PLANKS = block("cindervine_planks", () -> new Block(planks(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK)));
    public static final DeferredBlock<StairBlock> CINDERVINE_STAIRS = block("cindervine_stairs", () -> new StairBlock(CINDERVINE_PLANKS.get().defaultBlockState(), planks(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK)));
    public static final DeferredBlock<SlabBlock> CINDERVINE_SLAB = block("cindervine_slab", () -> new SlabBlock(planks(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK)));
    public static final DeferredBlock<FenceBlock> CINDERVINE_FENCE = block("cindervine_fence", () -> new FenceBlock(planks(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK)));
    public static final DeferredBlock<FenceGateBlock> CINDERVINE_FENCE_GATE = block("cindervine_fence_gate", () -> new FenceGateBlock(WoodFamily.CINDERVINE.woodType, planks(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK).forceSolidOn()));
    public static final DeferredBlock<DoorBlock> CINDERVINE_DOOR = block("cindervine_door", () -> new DoorBlock(WoodFamily.CINDERVINE.setType, planks(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<TrapDoorBlock> CINDERVINE_TRAPDOOR = block("cindervine_trapdoor", () -> new TrapDoorBlock(WoodFamily.CINDERVINE.setType, planks(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never)));
    public static final DeferredBlock<PressurePlateBlock> CINDERVINE_PRESSURE_PLATE = block("cindervine_pressure_plate", () -> new PressurePlateBlock(WoodFamily.CINDERVINE.setType, planks(WoodFamily.CINDERVINE, MapColor.COLOR_BLACK).forceSolidOn().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<ButtonBlock> CINDERVINE_BUTTON = block("cindervine_button", () -> new ButtonBlock(WoodFamily.CINDERVINE.setType, 30, BlockBehaviour.Properties.of().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY).sound(WoodFamily.CINDERVINE.woodSound())));
    public static final DeferredBlock<SmolderingLeavesBlock> CINDERVINE_LEAVES = block("cindervine_leaves", () -> new SmolderingLeavesBlock(leaves(MapColor.COLOR_ORANGE)));
    public static final DeferredBlock<SaplingBlock> CINDERVINE_SAPLING = block("cindervine_sapling", () -> new SaplingBlock(WoodFamily.CINDERVINE.grower, sapling()));
    public static final DeferredBlock<FlowerPotBlock> POTTED_CINDERVINE_SAPLING = blockNoItem("potted_cindervine_sapling", () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, CINDERVINE_SAPLING, BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_OAK_SAPLING)));

    // ------------------------------------------------------------------ crystal_willow
    public static final DeferredBlock<RotatedPillarBlock> CRYSTAL_WILLOW_LOG = block("crystal_willow_log", () -> new RotatedPillarBlock(log(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_CRYSTAL_WILLOW_LOG = block("stripped_crystal_willow_log", () -> new RotatedPillarBlock(log(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ)));
    public static final DeferredBlock<RotatedPillarBlock> CRYSTAL_WILLOW_WOOD = block("crystal_willow_wood", () -> new RotatedPillarBlock(log(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_CRYSTAL_WILLOW_WOOD = block("stripped_crystal_willow_wood", () -> new RotatedPillarBlock(log(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ)));
    public static final DeferredBlock<Block> CRYSTAL_WILLOW_PLANKS = block("crystal_willow_planks", () -> new Block(planks(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ)));
    public static final DeferredBlock<StairBlock> CRYSTAL_WILLOW_STAIRS = block("crystal_willow_stairs", () -> new StairBlock(CRYSTAL_WILLOW_PLANKS.get().defaultBlockState(), planks(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ)));
    public static final DeferredBlock<SlabBlock> CRYSTAL_WILLOW_SLAB = block("crystal_willow_slab", () -> new SlabBlock(planks(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ)));
    public static final DeferredBlock<FenceBlock> CRYSTAL_WILLOW_FENCE = block("crystal_willow_fence", () -> new FenceBlock(planks(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ)));
    public static final DeferredBlock<FenceGateBlock> CRYSTAL_WILLOW_FENCE_GATE = block("crystal_willow_fence_gate", () -> new FenceGateBlock(WoodFamily.CRYSTAL_WILLOW.woodType, planks(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ).forceSolidOn()));
    public static final DeferredBlock<DoorBlock> CRYSTAL_WILLOW_DOOR = block("crystal_willow_door", () -> new DoorBlock(WoodFamily.CRYSTAL_WILLOW.setType, planks(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<TrapDoorBlock> CRYSTAL_WILLOW_TRAPDOOR = block("crystal_willow_trapdoor", () -> new TrapDoorBlock(WoodFamily.CRYSTAL_WILLOW.setType, planks(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never)));
    public static final DeferredBlock<PressurePlateBlock> CRYSTAL_WILLOW_PRESSURE_PLATE = block("crystal_willow_pressure_plate", () -> new PressurePlateBlock(WoodFamily.CRYSTAL_WILLOW.setType, planks(WoodFamily.CRYSTAL_WILLOW, MapColor.QUARTZ).forceSolidOn().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<ButtonBlock> CRYSTAL_WILLOW_BUTTON = block("crystal_willow_button", () -> new ButtonBlock(WoodFamily.CRYSTAL_WILLOW.setType, 30, BlockBehaviour.Properties.of().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY).sound(WoodFamily.CRYSTAL_WILLOW.woodSound())));
    public static final DeferredBlock<LeavesBlock> CRYSTAL_WILLOW_LEAVES = block("crystal_willow_leaves", () -> new LeavesBlock(leaves(MapColor.COLOR_LIGHT_GREEN).sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 3)));
    public static final DeferredBlock<SaplingBlock> CRYSTAL_WILLOW_SAPLING = block("crystal_willow_sapling", () -> new SaplingBlock(WoodFamily.CRYSTAL_WILLOW.grower, sapling()));
    public static final DeferredBlock<FlowerPotBlock> POTTED_CRYSTAL_WILLOW_SAPLING = blockNoItem("potted_crystal_willow_sapling", () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, CRYSTAL_WILLOW_SAPLING, BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_OAK_SAPLING)));

    // ------------------------------------------------------------------ tidebloom
    public static final DeferredBlock<RotatedPillarBlock> TIDEBLOOM_LOG = block("tidebloom_log", () -> new RotatedPillarBlock(log(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_TIDEBLOOM_LOG = block("stripped_tidebloom_log", () -> new RotatedPillarBlock(log(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED)));
    public static final DeferredBlock<RotatedPillarBlock> TIDEBLOOM_WOOD = block("tidebloom_wood", () -> new RotatedPillarBlock(log(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_TIDEBLOOM_WOOD = block("stripped_tidebloom_wood", () -> new RotatedPillarBlock(log(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED)));
    public static final DeferredBlock<Block> TIDEBLOOM_PLANKS = block("tidebloom_planks", () -> new Block(planks(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED)));
    public static final DeferredBlock<StairBlock> TIDEBLOOM_STAIRS = block("tidebloom_stairs", () -> new StairBlock(TIDEBLOOM_PLANKS.get().defaultBlockState(), planks(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED)));
    public static final DeferredBlock<SlabBlock> TIDEBLOOM_SLAB = block("tidebloom_slab", () -> new SlabBlock(planks(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED)));
    public static final DeferredBlock<FenceBlock> TIDEBLOOM_FENCE = block("tidebloom_fence", () -> new FenceBlock(planks(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED)));
    public static final DeferredBlock<FenceGateBlock> TIDEBLOOM_FENCE_GATE = block("tidebloom_fence_gate", () -> new FenceGateBlock(WoodFamily.TIDEBLOOM.woodType, planks(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED).forceSolidOn()));
    public static final DeferredBlock<DoorBlock> TIDEBLOOM_DOOR = block("tidebloom_door", () -> new DoorBlock(WoodFamily.TIDEBLOOM.setType, planks(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<TrapDoorBlock> TIDEBLOOM_TRAPDOOR = block("tidebloom_trapdoor", () -> new TrapDoorBlock(WoodFamily.TIDEBLOOM.setType, planks(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never)));
    public static final DeferredBlock<PressurePlateBlock> TIDEBLOOM_PRESSURE_PLATE = block("tidebloom_pressure_plate", () -> new PressurePlateBlock(WoodFamily.TIDEBLOOM.setType, planks(WoodFamily.TIDEBLOOM, MapColor.COLOR_RED).forceSolidOn().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<ButtonBlock> TIDEBLOOM_BUTTON = block("tidebloom_button", () -> new ButtonBlock(WoodFamily.TIDEBLOOM.setType, 30, BlockBehaviour.Properties.of().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY).sound(WoodFamily.TIDEBLOOM.woodSound())));
    public static final DeferredBlock<LeavesBlock> TIDEBLOOM_LEAVES = block("tidebloom_leaves", () -> new LeavesBlock(leaves(MapColor.COLOR_CYAN)));
    public static final DeferredBlock<SaplingBlock> TIDEBLOOM_SAPLING = block("tidebloom_sapling", () -> new SaplingBlock(WoodFamily.TIDEBLOOM.grower, sapling()));
    public static final DeferredBlock<FlowerPotBlock> POTTED_TIDEBLOOM_SAPLING = blockNoItem("potted_tidebloom_sapling", () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, TIDEBLOOM_SAPLING, BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_OAK_SAPLING)));

    // ------------------------------------------------------------------ echo_birch
    public static final DeferredBlock<RotatedPillarBlock> ECHO_BIRCH_LOG = block("echo_birch_log", () -> new RotatedPillarBlock(log(WoodFamily.ECHO_BIRCH, MapColor.SAND)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_ECHO_BIRCH_LOG = block("stripped_echo_birch_log", () -> new RotatedPillarBlock(log(WoodFamily.ECHO_BIRCH, MapColor.SAND)));
    public static final DeferredBlock<RotatedPillarBlock> ECHO_BIRCH_WOOD = block("echo_birch_wood", () -> new RotatedPillarBlock(log(WoodFamily.ECHO_BIRCH, MapColor.SAND)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_ECHO_BIRCH_WOOD = block("stripped_echo_birch_wood", () -> new RotatedPillarBlock(log(WoodFamily.ECHO_BIRCH, MapColor.SAND)));
    public static final DeferredBlock<Block> ECHO_BIRCH_PLANKS = block("echo_birch_planks", () -> new Block(planks(WoodFamily.ECHO_BIRCH, MapColor.SAND)));
    public static final DeferredBlock<StairBlock> ECHO_BIRCH_STAIRS = block("echo_birch_stairs", () -> new StairBlock(ECHO_BIRCH_PLANKS.get().defaultBlockState(), planks(WoodFamily.ECHO_BIRCH, MapColor.SAND)));
    public static final DeferredBlock<SlabBlock> ECHO_BIRCH_SLAB = block("echo_birch_slab", () -> new SlabBlock(planks(WoodFamily.ECHO_BIRCH, MapColor.SAND)));
    public static final DeferredBlock<FenceBlock> ECHO_BIRCH_FENCE = block("echo_birch_fence", () -> new FenceBlock(planks(WoodFamily.ECHO_BIRCH, MapColor.SAND)));
    public static final DeferredBlock<FenceGateBlock> ECHO_BIRCH_FENCE_GATE = block("echo_birch_fence_gate", () -> new FenceGateBlock(WoodFamily.ECHO_BIRCH.woodType, planks(WoodFamily.ECHO_BIRCH, MapColor.SAND).forceSolidOn()));
    public static final DeferredBlock<DoorBlock> ECHO_BIRCH_DOOR = block("echo_birch_door", () -> new DoorBlock(WoodFamily.ECHO_BIRCH.setType, planks(WoodFamily.ECHO_BIRCH, MapColor.SAND).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<TrapDoorBlock> ECHO_BIRCH_TRAPDOOR = block("echo_birch_trapdoor", () -> new TrapDoorBlock(WoodFamily.ECHO_BIRCH.setType, planks(WoodFamily.ECHO_BIRCH, MapColor.SAND).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never)));
    public static final DeferredBlock<PressurePlateBlock> ECHO_BIRCH_PRESSURE_PLATE = block("echo_birch_pressure_plate", () -> new PressurePlateBlock(WoodFamily.ECHO_BIRCH.setType, planks(WoodFamily.ECHO_BIRCH, MapColor.SAND).forceSolidOn().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<ButtonBlock> ECHO_BIRCH_BUTTON = block("echo_birch_button", () -> new ButtonBlock(WoodFamily.ECHO_BIRCH.setType, 30, BlockBehaviour.Properties.of().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY).sound(WoodFamily.ECHO_BIRCH.woodSound())));
    public static final DeferredBlock<LeavesBlock> ECHO_BIRCH_LEAVES = block("echo_birch_leaves", () -> new LeavesBlock(leaves(MapColor.COLOR_LIGHT_BLUE)));
    public static final DeferredBlock<EchoBirchSaplingBlock> ECHO_BIRCH_SAPLING = block("echo_birch_sapling", () -> new EchoBirchSaplingBlock(WoodFamily.ECHO_BIRCH.grower, sapling()));
    public static final DeferredBlock<FlowerPotBlock> POTTED_ECHO_BIRCH_SAPLING = blockNoItem("potted_echo_birch_sapling", () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, ECHO_BIRCH_SAPLING, BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_OAK_SAPLING)));

    // ------------------------------------------------------------------ family extras
    public static final DeferredBlock<CrystalStrandsBlock> CRYSTAL_WILLOW_STRANDS = block("crystal_willow_strands", () -> new CrystalStrandsBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).noCollission().instabreak().noOcclusion()
                    .sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 4).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<TidebloomRootsBlock> TIDEBLOOM_ROOTS = block("tidebloom_roots", () -> new TidebloomRootsBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.MANGROVE_ROOTS).mapColor(MapColor.COLOR_CYAN)));

    private ModWoodBlocks() {}

    static void init() {
        // class-loading this holder registers every wood block above
    }

    public static List<DeferredBlock<FlowerPotBlock>> pots() {
        return List.of(POTTED_CINDERVINE_SAPLING, POTTED_CRYSTAL_WILLOW_SAPLING, POTTED_TIDEBLOOM_SAPLING, POTTED_ECHO_BIRCH_SAPLING);
    }

    public static List<DeferredBlock<? extends SaplingBlock>> saplings() {
        return List.of(CINDERVINE_SAPLING, CRYSTAL_WILLOW_SAPLING, TIDEBLOOM_SAPLING, ECHO_BIRCH_SAPLING);
    }

    private static BlockBehaviour.Properties log(WoodFamily family, MapColor color) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).strength(2.0F).sound(family.woodSound());
        if (family == WoodFamily.CINDERVINE) {
            p = p.lightLevel(s -> 4);
        } else if (family == WoodFamily.CRYSTAL_WILLOW) {
            p = p.lightLevel(s -> 5).noOcclusion();
        }
        return family.fireproof() ? p : p.ignitedByLava();
    }

    private static BlockBehaviour.Properties planks(WoodFamily family, MapColor color) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).strength(2.0F, 3.0F).sound(family.woodSound());
        if (family == WoodFamily.CRYSTAL_WILLOW) {
            p = p.lightLevel(s -> 5).noOcclusion();
        }
        return family.fireproof() ? p : p.ignitedByLava();
    }

    private static BlockBehaviour.Properties leaves(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
                .isValidSpawn(Blocks::ocelotOrParrot).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                .ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor((s, l, p) -> false);
    }

    private static BlockBehaviour.Properties sapling() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak()
                .sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY);
    }

    /** Cindervine wood is fire-proof: its block items survive lava, and its planks burn for 400 ticks. */
    private static <B extends Block> DeferredBlock<B> block(String id, Supplier<B> factory) {
        DeferredBlock<B> holder = ModRegistries.BLOCKS.register(id, factory);
        boolean cinder = id.contains("cindervine") && !id.contains("leaves") && !id.contains("sapling");
        if (id.equals("cindervine_planks")) {
            ModRegistries.ITEMS.register(id, () -> new BlockItem(holder.get(), new Item.Properties().fireResistant()) {
                @Override
                public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
                    return 400;
                }
            });
        } else if (cinder) {
            ModRegistries.ITEMS.register(id, () -> new BlockItem(holder.get(), new Item.Properties().fireResistant()));
        } else {
            ModRegistries.ITEMS.registerSimpleBlockItem(id, holder);
        }
        return holder;
    }

    private static <B extends Block> DeferredBlock<B> blockNoItem(String id, Supplier<B> factory) {
        return ModRegistries.BLOCKS.register(id, factory);
    }
}
