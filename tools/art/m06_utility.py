"""M6 textures: pocket watch, chronal shard, mirror, null lantern, whisperbone flute, echo fragment, orrery, echo skin."""
from PIL import Image

from texlib import c, from_ascii, new, noise_fill, ramp, rng, save, with_alpha
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
    save(from_ascii(FLUTE, {"o": c("#6E6450"), "W": c("#F2EAD6"), "B": c("#D8CCB0"), "H": c("#2A2A30"),
                            "b": c("#9C8E70")}), "item/whisperbone_flute")
    save(shards([c("#0A2A32"), c("#0E5A66"), c("#29DFEB"), c("#9EF6FF"), c("#E8FFFF")]), "item/echo_fragment")
    base, ring, core = orrery_textures()
    save(base, "block/orrery_base")
    save(ring, "block/orrery_ring")
    save(core, "block/orrery_core")
    save(humanoid_skin("echo", body=ramp("#0E3A44", "#1E5A66", "#29A0B0", "#7FE8E0"), alpha=150,
                       eyes=c("#E8FFFF"), seed="echo"), "entity/echo")
