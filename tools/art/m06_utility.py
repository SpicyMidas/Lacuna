"""M6 textures: pocket watch, chronal shard, mirror, null lantern (item + placed block), whisperbone flute, echo
fragment, orrery, echo skin."""
import json
import os

from PIL import Image

from texlib import ROOT, TEXTURES, c, from_ascii, new, noise_fill, ramp, recolor, rng, save, with_alpha
from art.common import shards
from art.humanoid import humanoid_skin

WATCH = [
    "................",
    ".......oo.......",
    "......oGGo......",
    ".......oo.......",
    ".....oooooo.....",
    "....oGgggggGo...",
    "...oGgCCCCCgGo..",
    "...ogCCWhCCCgo..",
    "...ogCCChCCCgo..",
    "...ogCCCHHHCgo..",
    "...ogCCCCCCCgo..",
    "...oGgCCCCCgGo..",
    "....oGgggggGo...",
    ".....oooooo.....",
    "................",
    "................",
]

MIRROR = [
    "................",
    ".....aaaaaa.....",
    "....aASSSSAa....",
    "...aASsWssSAa...",
    "...aSsWsssbSa...",
    "...aSWssssbSa...",
    "...aSsssbbbSa...",
    "...aASsbbbSAa...",
    "....aASSSSAa....",
    ".....aaGGaa.....",
    ".......GG.......",
    ".......Gg.......",
    ".......Gg.......",
    "......gGGg......",
    ".......gg.......",
    "................",
]

LANTERN = [
    "................",
    ".......oo.......",
    "......o..o......",
    ".......oo.......",
    ".....oooooo.....",
    "....oUUUUUUo....",
    "....oUoooooUo...",
    "....oUdVvVdUo...",
    "....oUdvWvdUo...",
    "....oUdVvVdUo...",
    "....oUoooooUo...",
    "....oUUUUUUo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]

# Placed Null Lantern: one 16x16 frame in the UV layout of the vanilla lantern templates
# (minecraft:block/template_lantern and template_hanging_lantern, texture variable #lantern):
#   cols 1-4   rows 0-1   cap sides               cols 0-5 rows 2-8    body sides (6 x 7)
#   cols 0-5   rows 9-14  body top and bottom     cols 1-4 rows 10-13  cap top (inside the body top)
#   cols 11-13 rows 1-4   handle (standing: rows 1-2 only)
#   cols 11-13 rows 6-11  chain (hanging; standing shows rows 10-11 as the handle seen side-on)
# Painted with the colours of the hand-drawn item (tools/art/hand/item/null_lantern.png): a riveted dark iron frame,
# a violet glow with a white-hot heart, light leaking up through a vent in the cap. Upper-case letters in the body
# rows are glow slots filled per animation frame (G = glass, V = cap vent).
LANTERN_BLOCK = [
    ".beeb...........",
    ".daad......ebe..",
    "dbeebd.....a.a..",
    "eGGGGe.....a.a..",
    "aGGGGa.....dad..",
    "bGGGGb..........",
    "aGGGGa.....a.a..",
    "eGGGGe.....ebe..",
    "dbeebd......a...",
    "daaaad......a...",
    "aebbea.....ebe..",
    "abVVba.....a.a..",
    "abVVba..........",
    "aebbea..........",
    "daaaad..........",
    "................",
]
# 4x5 glass (body rows 3-7) and the 2x2 cap vent for each frame: a slow violet flicker
LANTERN_FRAMES = [
    (["gffg", "fccf", "cihc", "fccf", "gffg"], ["gg", "gg"]),
    (["gffg", "fcif", "cihc", "fiif", "gffg"], ["fg", "gf"]),
    (["gffg", "gccf", "fcic", "fccg", "gggg"], ["gg", "gg"]),
]
# hand-drawn item colours: iron (darkest to light), then the glow (deep violet to white)
LANTERN_IRON = {"d": "#131217", "a": "#211F27", "e": "#312E39", "b": "#46424F"}
LANTERN_GLOW = {"g": "#6A22D0", "f": "#8E45F2", "c": "#C08AFF", "i": "#ECDCFF", "h": "#FFFFFF"}
# the same glow once the charge is spent: very, very dark purples (same order, so the shading survives)
LANTERN_GLOW_OFF = {"g": "#170D20", "f": "#1C1028", "c": "#221330", "i": "#271636", "h": "#2B183B"}


def lantern_block_frame(glass, vent, glow):
    rows, g, v = [], 0, 0
    for row in LANTERN_BLOCK:
        if "G" in row:
            row = row.replace("GGGG", glass[g])
            g += 1
        if "V" in row:
            row = row.replace("VV", vent[v])
            v += 1
        rows.append(row)
    pal = {k: c(h) for k, h in LANTERN_IRON.items()}
    pal.update({k: c(h) for k, h in glow.items()})
    img = from_ascii(rows, pal)
    assert img.size == (16, 16), img.size
    return img


def null_lantern_block_textures():
    """Lit (3-frame flicker, animated like the vanilla lantern) and dark variants of the placed lantern."""
    lit = new(16, 16 * len(LANTERN_FRAMES))
    for i, (glass, vent) in enumerate(LANTERN_FRAMES):
        lit.alpha_composite(lantern_block_frame(glass, vent, LANTERN_GLOW), (0, 16 * i))
    off = lantern_block_frame(*LANTERN_FRAMES[0], LANTERN_GLOW_OFF)
    return lit, off


def null_lantern_item_off():
    """The hand-drawn item (only read, never modified) with its violet glow turned to very dark purples."""
    src = os.path.join(ROOT, "tools", "art", "hand", "item", "null_lantern.png")
    with Image.open(src) as im:
        item = im.convert("RGBA")
    return recolor(item, {c(LANTERN_GLOW[k]): c(LANTERN_GLOW_OFF[k]) for k in LANTERN_GLOW})


def save_animated(img, rel, frametime):
    save(img, rel)
    with open(os.path.join(TEXTURES, rel + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"animation": {"frametime": frametime}}, fh, indent=2, sort_keys=True)
        fh.write("\n")


FLUTE = [
    "................",
    "............obo.",
    "...........oWBo.",
    "..........oWBo..",
    ".........oWHo...",
    "........oWBo....",
    ".......oWHo.....",
    "......oWBo......",
    ".....oWHo.......",
    "....oWBo........",
    "...oWBo.........",
    "..oWBo..........",
    ".oBBo...........",
    ".obo............",
    "................",
    "................",
]


def orrery_textures():
    base = noise_fill(ramp("#4A4A52", "#5A5A64", "#6A6A76", "#7A7A88"), "orrery_base", cell=4, speckle=0.1)
    px = base.load()
    for i in range(16):
        px[i, 0] = c("#D49A22")
        px[i, 15] = c("#8A5A10")
    ring = noise_fill(ramp("#8A5A10", "#D49A22", "#F2C94C", "#FFF2A0"), "orrery_ring", cell=2, speckle=0.2)
    core = noise_fill(ramp("#3C8CFF", "#7FE8E0", "#B8E8FF", "#FFFFFF"), "orrery_core", cell=2, speckle=0.3, contrast=1.5)
    return base, ring, core


def paint():
    save(from_ascii(WATCH, {"o": c("#5A3A10"), "G": c("#F2C94C"), "g": c("#D49A22"), "C": c("#BFE8F8"),
                            "W": c("#FFFFFF"), "h": c("#2E3350"), "H": c("#7B4DFF")}), "item/chronoshard_pocket_watch")
    save(shards([c("#2E2858"), c("#4A6AD8"), c("#7FB8F0"), c("#C8F0FF"), c("#FFFFFF")]), "item/chronal_shard")
    save(from_ascii(MIRROR, {"a": c("#3A2468"), "A": c("#8A6AD8"), "S": c("#C8CCD8"), "s": c("#DDE4F0"),
                             "W": c("#FFFFFF"), "b": c("#9CA8C0"), "G": c("#D49A22"), "g": c("#8A5A10")}),
         "item/mirror_of_recursion")
    save(from_ascii(LANTERN, {"o": c("#12131C"), "U": c("#2E3350"), "d": c("#1A1A22"), "V": c("#7B4DFF"),
                              "v": c("#4A2E78"), "W": c("#D8C8FF")}), "item/null_lantern")
    lit, off = null_lantern_block_textures()
    save_animated(lit, "block/null_lantern", 8)
    save(off, "block/null_lantern_off")
    save(null_lantern_item_off(), "item/null_lantern_off")
    save(from_ascii(FLUTE, {"o": c("#6E6450"), "W": c("#F2EAD6"), "B": c("#D8CCB0"), "H": c("#2A2A30"),
                            "b": c("#9C8E70")}), "item/whisperbone_flute")
    save(shards([c("#0A2A32"), c("#0E5A66"), c("#29DFEB"), c("#9EF6FF"), c("#E8FFFF")]), "item/echo_fragment")
    base, ring, core = orrery_textures()
    save(base, "block/orrery_base")
    save(ring, "block/orrery_ring")
    save(core, "block/orrery_core")
    save(humanoid_skin("echo", body=ramp("#0E3A44", "#1E5A66", "#29A0B0", "#7FE8E0"), alpha=150,
                       eyes=c("#E8FFFF"), seed="echo"), "entity/echo")
