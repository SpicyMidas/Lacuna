"""Banishment particles and the Exilite stone family, in the exilite palette of the user's hand-drawn textures.

The palette is sampled from tools/art/hand: block/exilite_debris_side + _top, item/exilite_shard, item/exilite_ingot
and item/totem_of_banishing (the "exilite set"): bone white, cream, sand and taupe stone, a warm umber outline
(#7C6C58), the darker umber of the buried debris, and pearl glints of pink, mint and lavender on the brightest
pixels. No reds anywhere (the ruby is the totem's alone).

Particles (textures/particle/, 8x8, pixel art):
  * banish_mote_0..2 - a stone chip tumbling (big chip, small chip, glint); tinted per particle.
  * rift_spark_0..2  - a pearl-white four-point spark with a lavender halo (additive), shrinking frame by frame.
Exilite (textures/block/): raw stone, polished, bricks, chiseled (the totem's open hand), the Block of Exilite.
The debris, shard and ingot textures are hand-drawn (tools/art/hand), never generated.
"""
import math

from texlib import c, from_ascii, lerp, new, noise_fill, rng, save

# ---------------------------------------------------------------------------------------- the exilite palette
PEARL_W = c("#F9F8F3")
PEARL_P = c("#FDF3F6")   # pink glint
PEARL_M = c("#F2FBF6")   # mint glint
PEARL_L = c("#F6F3FB")   # lavender glint
BONE_HI = c("#E8E2D6")
BONE = c("#DAD2BD")
CREAM = c("#D2C9B2")
TAN = c("#BFB399")
SAND = c("#A69E8B")
TAUPE = c("#988973")
TAUPE_D = c("#887D6A")
UMBER_L = c("#7C6C58")   # the outline of the shard, ingot and totem
UMBER = c("#766A58")
UMBER_D = c("#635646")   # the debris' darkest seams
PEARLS = [PEARL_P, PEARL_M, PEARL_L]

PAL = {"O": UMBER_L, "o": UMBER, "q": TAUPE_D, "k": TAUPE, "s": TAN, "n": CREAM, "m": BONE_HI, "h": PEARL_W,
       "p": PEARL_P, "g": PEARL_M, "l": PEARL_L}


# ---------------------------------------------------------------------------------------- particles
MOTE_FRAMES = [
    [
        "........",
        "...OO...",
        "..OhmO..",
        ".OhmmsO.",
        ".OmpsO..",
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
        "...l....",
        "..lhg...",
        "...p....",
        "........",
        "........",
        "........",
    ],
]


def banish_motes():
    for i, rows in enumerate(MOTE_FRAMES):
        save(from_ascii(rows, PAL), f"particle/banish_mote_{i}")


def rift_spark(arm):
    """Four-point star centred on the 8x8 sprite: pearl-white core, bone arms, a faint lavender pearl halo."""
    img = new(8, 8)
    px = img.load()
    for y in range(8):
        for x in range(8):
            dx, dy = abs(x + 0.5 - 4.0), abs(y + 0.5 - 4.0)
            d = math.hypot(dx, dy)
            col = None
            if min(dx, dy) < 0.6 and max(dx, dy) < arm:
                t = max(dx, dy) / arm
                base = lerp(PEARL_W, BONE_HI, min(1.0, t * 1.4))
                alpha = 255 if t < 0.45 else (190 if t < 0.75 else 110)
                col = (base[0], base[1], base[2], alpha)
            elif d < arm * 0.75:
                # the halo between the arms: quantized pearl glow, lavender inside, a touch of mint outside
                alpha = 90 if d < arm * 0.45 else 45
                tone = PEARL_L if d < arm * 0.45 else PEARL_M
                col = (tone[0], tone[1], tone[2], alpha)
            if col:
                px[x, y] = col
    return img


def rift_sparks():
    for i, arm in enumerate((4.0, 2.8, 1.6)):
        save(rift_spark(arm), f"particle/rift_spark_{i}")


# ---------------------------------------------------------------------------------------- exilite
STONE = [SAND, TAN, CREAM, BONE, BONE_HI]


def _veins(img, name, count, col, dark):
    """Thin wandering umber veins (like the seams of the debris), dark at the start, fading to taupe."""
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


def _pearls(img, name, count):
    """Pearl glints (pink, mint, lavender) like the brightest pixels of the hand-drawn shard and ingot."""
    px = img.load()
    r = rng(name + "_pearls")
    for _ in range(count):
        px[r.randint(0, 15), r.randint(0, 15)] = r.choice(PEARLS)


def exilite():
    img = noise_fill(STONE, "exilite", cell=4, speckle=0.16, contrast=1.1)
    _veins(img, "exilite", 3, TAUPE, UMBER_L)
    _pearls(img, "exilite", 3)
    return img


def _bevelled(name, contrast=0.55):
    img = noise_fill([CREAM, BONE, BONE, BONE_HI], name, cell=8, speckle=0.06, contrast=contrast)
    px = img.load()
    for i in range(16):
        px[i, 0] = PEARL_W
        px[0, i] = PEARL_W
        px[i, 15] = TAN
        px[15, i] = TAN
        px[i, 14] = lerp(px[i, 14], TAN, 0.4) if 0 < i < 15 else px[i, 14]
        px[14, i] = lerp(px[14, i], TAN, 0.4) if 0 < i < 15 else px[14, i]
    px[15, 0] = CREAM
    px[0, 15] = CREAM
    return img


def polished_exilite():
    img = _bevelled("polished_exilite")
    _pearls(img, "polished_exilite", 2)
    return img


def exilite_bricks():
    img = noise_fill([CREAM, BONE, BONE, BONE_HI], "exilite_bricks", cell=4, speckle=0.1, contrast=0.9)
    px = img.load()
    for y in range(16):
        for x in range(16):
            row = y // 4
            offset = 4 if row % 2 else 0
            if y % 4 == 3 or (x + offset) % 8 == 7:
                px[x, y] = UMBER_L
            elif y % 4 == 2 or (x + offset) % 8 == 6:
                px[x, y] = lerp(px[x, y], TAN, 0.5)  # lower-right shade of each brick
            elif y % 4 == 0:
                px[x, y] = lerp(px[x, y], PEARL_W, 0.5)
    _pearls(img, "exilite_bricks", 2)
    return img


# the Totem of Banishing's open stone hand, carved in relief: h = lit edge, s = face, o = shadowed edge, g = the palm
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
                px[X, Y] = PEARL_W
            elif ch == "s":
                px[X, Y] = TAN
            elif ch == "g":
                px[X, Y] = PEARL_P  # the totem's palm socket, empty of its ruby: a pearl glints in it
            elif ch == "o":
                px[X, Y] = UMBER_L
    # a carved groove frame inside the bevel
    for i in range(1, 15):
        for (x, y) in ((i, 1), (1, i)):
            px[x, y] = lerp(px[x, y], UMBER_L, 0.45)
        for (x, y) in ((i, 14), (14, i)):
            px[x, y] = lerp(px[x, y], PEARL_W, 0.5)
    return img


def exilite_block():
    """Block of Exilite: nine ingots pressed into one, like a netherite block. A smooth bone-white face with the
    ingot's bevel (pearl top-left, tan bottom-right), an umber rim, four faint ingot seams and pearl glints."""
    img = noise_fill([CREAM, BONE, BONE_HI, BONE_HI], "exilite_block", cell=8, speckle=0.05, contrast=0.45)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0 or x == 15 or y == 15:
                px[x, y] = UMBER_L
            elif x == 1 or y == 1:
                px[x, y] = PEARL_W
            elif x == 14 or y == 14:
                px[x, y] = TAN
            elif x in (5, 10) or y in (5, 10):
                px[x, y] = lerp(px[x, y], TAN, 0.35)  # the seams of the pressed ingots
            elif x in (6, 11) or y in (6, 11):
                px[x, y] = lerp(px[x, y], PEARL_W, 0.45)
    px[1, 14] = CREAM
    px[14, 1] = CREAM
    for (x, y), col in zip(((3, 3), (8, 2), (12, 8), (3, 12), (8, 8)), (PEARL_L, PEARL_P, PEARL_M, PEARL_P, PEARL_L)):
        px[x, y] = col
    return img


def paint():
    banish_motes()
    rift_sparks()
    save(exilite(), "block/exilite")
    save(polished_exilite(), "block/polished_exilite")
    save(exilite_bricks(), "block/exilite_bricks")
    save(chiseled_exilite(), "block/chiseled_exilite")
    save(exilite_block(), "block/exilite_block")
