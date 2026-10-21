package com.lacuna.mod.fl;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Exilite: the pale stone the Totem of Banishing was carved from, in the totem's own colours (bone, cream, warm grey,
 * umber, a fleck of lichen; never its ruby). It grows only deep in the Forgotten Land (below Y 0, a tiny vein every few
 * chunks, worldgen in tools/content/m17_exile.py) and now and then turns up in a ruin cache. Mined with a diamond
 * pickaxe, the ore gives Exilite Shards: four shards make a block of Exilite (then polished, bricks, chiseled, stairs,
 * slab and wall, also on the stonecutter), and four shards around crying obsidian make a Forgotten Portal Frame
 * without rubies. Block properties for the family registered in {@code ModBlocks}.
 */
public final class Exilite {
    private Exilite() {}

    /** The ore: an umber matrix with bone nodules; needs a diamond pickaxe (tag), sturdier than deepslate ores. */
    public static BlockBehaviour.Properties ore() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(5.0F, 9.0F).sound(SoundType.DEEPSLATE);
    }

    /** The crafted stone family: pale as bone, rings like calcite; needs an iron pickaxe (tag). */
    public static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(3.0F, 9.0F).sound(SoundType.CALCITE);
    }
}
