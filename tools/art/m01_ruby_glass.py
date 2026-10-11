"""M1 textures: ruby family, divine glass family."""
from texlib import c, ramp, save, with_alpha
from art.common import (deepslate_base, gem, glass, ore, pane_top, raw_block, raw_chunk, shards, stone_base,
                        storage_block, tool)

RUBY = ramp("#4A0716", "#8E0F2A", "#D6143F", "#FF5C7A", "#FFC2CC")
RAW_RUBY = ramp("#3A1016", "#6E1D2A", "#9E2E40", "#C4566A", "#E48A98")
DIVINE = ramp("#B8862A", "#FFD35C", "#FFE9A0", "#FFF7D6", "#FFFFFF")
FRINGE = [c("#B8E8FF"), c("#FFC8F0")]


def paint():
    save(ore(stone_base(), RUBY, "ruby_ore"), "block/ruby_ore")
    save(ore(deepslate_base(), RUBY, "deepslate_ruby_ore"), "block/deepslate_ruby_ore")
    save(storage_block(RUBY, "ruby_block"), "block/ruby_block")
    save(raw_block(RAW_RUBY, "raw_ruby_block"), "block/raw_ruby_block")
    save(gem(RUBY), "item/ruby")
    save(raw_chunk(RAW_RUBY, "raw_ruby"), "item/raw_ruby")
    for kind in ("sword", "pickaxe", "axe", "shovel", "hoe"):
        save(tool(kind, [RUBY[0], RUBY[1], RUBY[2], RUBY[3], RUBY[4]]), f"item/ruby_{kind}")

    # Divine glass: white-gold, translucent (alpha 150-210), prism fringe pixels
    frame = [with_alpha(c("#C9A24A"), 230), with_alpha(c("#FFE9A0"), 230), with_alpha(c("#FFF7D6"), 240)]
    save(glass(frame, with_alpha(c("#FFF7D6"), 70), with_alpha(c("#FFFFFF"), 200), "divine_glass",
               [with_alpha(f, 210) for f in FRINGE]), "block/divine_glass")
    save(pane_top(frame), "block/divine_glass_pane_top")
    save(shards([DIVINE[0], DIVINE[1], DIVINE[2], DIVINE[3], DIVINE[4]]), "item/divine_glass_shard")
    save(cluster(), "block/divine_glass_cluster")


def cluster():
    from texlib import from_ascii
    rows = [
        "................",
        "................",
        ".......o........",
        "......oho.......",
        "......o4o.......",
        "..o...o43o..o...",
        ".oho..o43o.oho..",
        ".o4o..o43o.o4o..",
        ".o43o.o432oo43o.",
        "..o3o.o432o.o3o.",
        "..o32oo432o.o32o",
        "...o32o432oo32o.",
        "...o32o432o32o..",
        "....o3o432o3o...",
        ".....o2o32o2o...",
        "......oooooo....",
    ]
    m = {"o": c("#B8862A"), "2": c("#FFD35C"), "3": c("#FFE9A0"), "4": c("#FFF7D6"), "h": c("#FFFFFF")}
    img = from_ascii(rows, m)
    px = img.load()
    px[7, 7] = c("#B8E8FF")
    px[2, 9] = c("#FFC8F0")
    return img
