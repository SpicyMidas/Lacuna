"""Banishment particles and the Exilite stone family, in the Totem of Banishing's palette without its ruby.

The palette is read off the totem's texture (art/m02_forgotten_land.totem): bone highlights, cream and warm-grey
stone, a dark umber outline and a fleck of lichen green. No reds anywhere.

Particles (textures/particle/, 8x8, pixel art):
  * banish_mote_0..2 - a stone chip of the totem tumbling (big chip, small chip, glint); tinted per particle.
  * rift_spark_0..2  - a pale four-point spark with a lichen halo (additive), shrinking frame by frame.
Exilite (textures/block/, textures/item/): ore, raw stone, polished, bricks, chiseled (the totem's open hand), shard.
"""
import math

from texlib import c, from_ascii, lerp, new, noise_fill, ramp, rng, save
from art.common import ore

# ---------------------------------------------------------------------------------------- the totem's palette
BONE_HI = c("#ECE6D8")
BONE = c("#CFC6B3")
BONE_MID = c("#BDB4A2")
BONE_LO = c("#A39B8C")
GREY = c("#8C8780")
GREY_D = c("#6E6A64")
UMBER_L = c("#5E5247")
UMBER = c("#4E4338")
LICHEN = c("#9BA889")

PAL = {"O": UMBER, "o": UMBER_L, "q": GREY_D, "k": GREY, "s": BONE_LO, "n": BONE_MID, "m": BONE, "h": BONE_HI, "g": LICHEN}


# ---------------------------------------------------------------------------------------- particles
MOTE_FRAMES = [
    [
        "........",
        "...OO...",
        "..OhmO..",
        ".OhmmsO.",
        ".OmgsO..",
        "..OsO...",
        "...O....",
        "........",
    ],
    [
        "........",
        "........",
        "...OO...",
        "..OhmO..",
        "..OmsO..",
        "...OO...",
        "........",
        "........",
    ],
    [
        "........",
        "........",
        "...m....",
        "..mhm...",
        "...m....",
        "........",
        "........",
        "........",
    ],
]


def banish_motes():
    for i, rows in enumerate(MOTE_FRAMES):
        save(from_ascii(rows, PAL), f"particle/banish_mote_{i}")


def rift_spark(arm):
    """Four-point star centred on the 8x8 sprite: bone-white core, cream arms, a faint lichen halo."""
    img = new(8, 8)
    px = img.load()
    for y in range(8):
        for x in range(8):
            dx, dy = abs(x + 0.5 - 4.0), abs(y + 0.5 - 4.0)
            d = math.hypot(dx, dy)
            col = None
            if min(dx, dy) < 0.6 and max(dx, dy) < arm:
                t = max(dx, dy) / arm
                base = lerp(BONE_HI, BONE, min(1.0, t * 1.4))
                alpha = 255 if t < 0.45 else (190 if t < 0.75 else 110)
                col = (base[0], base[1], base[2], alpha)
            elif d < arm * 0.75:
                # the halo between the arms: quantized lichen glow
                alpha = 90 if d < arm * 0.45 else 45
                col = (LICHEN[0], LICHEN[1], LICHEN[2], alpha)
            if col:
                px[x, y] = col
    return img


def rift_sparks():
    for i, arm in enumerate((4.0, 2.8, 1.6)):
        save(rift_spark(arm), f"particle/rift_spark_{i}")


# ---------------------------------------------------------------------------------------- exilite
STONE = [BONE_LO, BONE_MID, BONE, BONE, BONE_HI]
MATRIX = ramp("#4E4338", "#5E5247", "#5E5247", "#6E6A64", "#6E6A64")
NODULE = [UMBER, BONE_LO, BONE, BONE_HI, BONE_HI]


def _veins(img, name, count, col, dark):
    """Thin wandering umber veins (like the totem's weathered cracks), dark at the start, fading to grey."""
    px = img.load()
    r = rng(name + "_veins")
    for v in range(count):
        x, y = r.randint(0, 15), r.randint(0, 15)
        dx = r.choice((-1, 1))
        for step in range(r.randint(6, 10)):
            px[x % 16, y % 16] = dark if step < 3 else col
            if r.random() < 0.55:
                x += dx
            else:
                y += 1


def _lichen(img, name, count):
    px = img.load()
    r = rng(name + "_lichen")
    for _ in range(count):
        px[r.randint(0, 15), r.randint(0, 15)] = LICHEN


def exilite():
    img = noise_fill(STONE, "exilite", cell=4, speckle=0.16, contrast=1.1)
    _veins(img, "exilite", 3, GREY, UMBER_L)
    _lichen(img, "exilite", 3)
    return img


def exilite_ore():
    base = noise_fill(MATRIX, "exilite_ore_matrix", cell=4, speckle=0.2, contrast=1.2)
    img = ore(base, NODULE, "exilite_ore", clusters=4)
    _lichen(img, "exilite_ore", 2)
    return img


def _bevelled(name, contrast=0.55):
    img = noise_fill([BONE_MID, BONE, BONE, BONE_HI], name, cell=8, speckle=0.06, contrast=contrast)
    px = img.load()
    for i in range(16):
        px[i, 0] = BONE_HI
        px[0, i] = BONE_HI
        px[i, 15] = BONE_LO
        px[15, i] = BONE_LO
        px[i, 14] = lerp(px[i, 14], BONE_LO, 0.4) if 0 < i < 15 else px[i, 14]
        px[14, i] = lerp(px[14, i], BONE_LO, 0.4) if 0 < i < 15 else px[14, i]
    px[15, 0] = BONE_MID
    px[0, 15] = BONE_MID
    return img


def polished_exilite():
    return _bevelled("polished_exilite")


def exilite_bricks():
    img = noise_fill([BONE_MID, BONE, BONE, BONE_HI], "exilite_bricks", cell=4, speckle=0.1, contrast=0.9)
    px = img.load()
    for y in range(16):
        for x in range(16):
            row = y // 4
            offset = 4 if row % 2 else 0
            if y % 4 == 3 or (x + offset) % 8 == 7:
                px[x, y] = GREY_D
            elif y % 4 == 2 or (x + offset) % 8 == 6:
                px[x, y] = lerp(px[x, y], BONE_LO, 0.5)  # lower-right shade of each brick
            elif y % 4 == 0:
                px[x, y] = lerp(px[x, y], BONE_HI, 0.5)
    _lichen(img, "exilite_bricks", 2)
    return img


# the Totem of Banishing's open stone hand, carved in relief: H = lit edge, s = face, o = shadowed edge
HAND = [
    "....h.h.h...",
    "..h.s.s.s.h.",
    "..s.s.s.s.s.",
    "..s.s.s.s.so",
    "..sssssssso.",
    "h.sssssssso.",
    "sssssgsssso.",
    ".sssssssso..",
    "..ssssssso..",
    "...ssssso...",
    "....ooooo...",
]


def chiseled_exilite():
    img = _bevelled("chiseled_exilite", contrast=0.4)
    px = img.load()
    ox, oy = 2, 2
    for y, row in enumerate(HAND):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            X, Y = ox + x, oy + y
            if ch == "h":
                px[X, Y] = BONE_HI
            elif ch == "s":
                px[X, Y] = BONE_LO
            elif ch == "g":
                px[X, Y] = LICHEN  # the totem's palm socket, empty of its ruby: lichen has grown in it
            elif ch == "o":
                px[X, Y] = UMBER_L
    # a carved groove frame inside the bevel
    for i in range(1, 15):
        for (x, y) in ((i, 1), (1, i)):
            px[x, y] = lerp(px[x, y], UMBER_L, 0.45)
        for (x, y) in ((i, 14), (14, i)):
            px[x, y] = lerp(px[x, y], BONE_HI, 0.5)
    return img


SHARD = [
    "................",
    "...........OO...",
    "..........OhmO..",
    ".........OhmmsO.",
    "........OhmmnsO.",
    ".......OhmmnssO.",
    "......OhhmnsskO.",
    ".....OhmmnsgskO.",
    "....OhmmnsssskO.",
    "...OhmmnssskkqO.",
    "...OmmnsskkkqO..",
    "..OsnsskkkqoO...",
    "..OksskkqqoO....",
    "...OOkkqooO.....",
    ".....OOOOO......",
    "................",
]


def exilite_shard():
    return from_ascii(SHARD, PAL)


def paint():
    banish_motes()
    rift_sparks()
    save(exilite_ore(), "block/exilite_ore")
    save(exilite(), "block/exilite")
    save(polished_exilite(), "block/polished_exilite")
    save(exilite_bricks(), "block/exilite_bricks")
    save(chiseled_exilite(), "block/chiseled_exilite")
    save(exilite_shard(), "item/exilite_shard")
