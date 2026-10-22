package com.lacuna.mod.fl;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Exilite: the pale, pearl-glinting stone the Totem of Banishing was carved from. It is found the way ancient debris is
 * in the Nether: Exilite Debris, hidden veins (never touching air) deep in the Forgotten Land, below Y -8, one every
 * few chunks (worldgen in tools/content/m17_exile.py). The debris is as hard as ancient debris (30 / 1200, so no
 * explosion breaks it), needs a diamond pickaxe and drops itself; smelted or blasted it gives an Exilite Shard (like
 * netherite scrap), and four shards with four gold ingots make an Exilite Ingot. Ingots light the way home (an ingot
 * between two crying obsidian makes two Forgotten Portal Frames) and nine make a Block of Exilite (storage, beacon
 * base). One shard in a ring of calcite colours eight blocks of plain Exilite, the base of the decorative family
 * (polished, bricks, chiseled, brick stairs, slab and wall, also on the stonecutter). Debris, shard, ingot and block
 * never burn. Block properties for the family registered in {@code ModBlocks}; the old {@code exilite_ore} id loads as
 * the debris.
 */
public final class Exilite {
    private Exilite() {}

    /** Exilite Debris: ancient debris' toughness (strength 30, blast resistance 1200); needs a diamond pickaxe (tag). */
    public static BlockBehaviour.Properties debris() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(30.0F, 1200.0F).sound(SoundType.ANCIENT_DEBRIS);
    }

    /** Block of Exilite (nine ingots): a netherite block's toughness (50 / 1200); needs a diamond pickaxe (tag). */
    public static BlockBehaviour.Properties block() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(50.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK);
    }

    /** The crafted stone family: pale as bone, rings like calcite; needs an iron pickaxe (tag). */
    public static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(3.0F, 9.0F).sound(SoundType.CALCITE);
    }
}
