"""Starcaller Trumpet textures: the golden trumpet with a star-shaped bell, the Star Fragment, the Fallen Star block
(charred star-stone with glowing veins + its bright core) and the star flare mask used by the falling star's light.

Gold follows the vanilla gold/bell ramp; outlines are a dark warm brown like vanilla gold items.
"""
import math

from texlib import c, from_ascii, new, noise_fill, outline, rng, save

OUTLINE = c("#4A2508")
GOLD = {"d": c("#9C4F16"), "m": c("#D39632"), "g": c("#F5CC27"), "l": c("#FFEC4F"), "h": c("#FFFBD0")}
THROAT = c("#7A3A10")
WHITE = c("#FFFFFF")

# the bell seen from the front: a five-point star (drawn over the tube, which runs off behind it)
BELL = [
    "...h...",
    "..hlg..",
    "hllWlgd",
    ".lgtgd.",
    "..gmd..",
    ".gd.md.",
    ".d...d.",
]


def starcaller_trumpet():
    tube = new(16, 16)
    px = tube.load()
    # the leadpipe: two pixels wide, lit along its upper-right edge, running to the mouthpiece (bottom right)
    for t in range(5, 14):
        px[t, t] = GOLD["m"]
        px[t + 1, t] = GOLD["l"] if t % 3 else GOLD["g"]
    px[14, 14] = GOLD["h"]
    px[13, 14] = GOLD["m"]
    px[14, 13] = GOLD["l"]
    # three valve stems standing up from the tube
    for t in (8, 10, 12):
        px[t + 1, t - 1] = GOLD["g"]
        px[t + 1, t - 2] = GOLD["h"]
    img = outline(tube, OUTLINE)
    bell = new(16, 16)
    bell.alpha_composite(from_ascii(BELL, {**GOLD, "t": THROAT, "W": WHITE}), (1, 1))
    img.alpha_composite(outline(bell, OUTLINE))
    save(img, "item/starcaller_trumpet")


# a chipped little star: pale gold crystal, a lavender facet of starlight, one white glint, its lower-right
# point broken off along a jagged edge
FRAGMENT = [
    "................",
    "................",
    ".......O........",
    "......OhO.......",
    "......OhO.......",
    ".....OhlgO......",
    "..OOOOhlgOOOO...",
    "..OhlllWlggdO...",
    "...OhlglgsgdO...",
    "....OlgsggdO....",
    ".....OgsgmO.....",
    ".....OggdmdO....",
    "....OgdOOOmO....",
    "....OdO...OO....",
    "....OO..........",
    "................",
]


def star_fragment():
    pal = {"O": c("#5C3A10"), "o": c("#7A5418"), "h": c("#FFFBE6"), "l": c("#FFF0A8"), "g": c("#FFD866"),
           "m": c("#E8A93A"), "d": c("#B8741E"), "s": c("#C9B8FF"), "W": WHITE}
    save(from_ascii(FRAGMENT, pal), "item/star_fragment")


ROCK = [c("#231D1A"), c("#322925"), c("#423630"), c("#54463C"), c("#66574A")]
VEIN = [c("#B24E18"), c("#E8862A"), c("#FFC45A"), c("#FFE9A6")]


def fallen_star_rock():
    """Charred star-stone: dark pitted rock crossed by thin veins still glowing from the fall."""
    img = noise_fill(ROCK[0:4], "fallen_star_rock", cell=3, speckle=0.2, contrast=1.3)
    px = img.load()
    r = rng("fallen_star_veins")
    for _ in range(3):
        x, y = r.randint(1, 14), r.randint(1, 14)
        for step in range(r.randint(4, 7)):
            px[x % 16, y % 16] = VEIN[1] if step % 3 else VEIN[2]
            dx, dy = r.choice([(1, 0), (0, 1), (1, 1), (-1, 1), (1, -1)])
            x, y = x + dx, y + dy
    for _ in range(4):
        px[r.randint(0, 15), r.randint(0, 15)] = VEIN[0]
    for _ in range(6):
        px[r.randint(0, 15), r.randint(0, 15)] = ROCK[4]
    save(img, "block/fallen_star")


def fallen_star_core():
    """The glowing core: bright pale-gold crystal with lavender facets and white glints (drawn full bright)."""
    pal = [c("#F2B84A"), c("#FFD77A"), c("#FFEDB0"), c("#FFF9E4")]
    img = noise_fill(pal, "fallen_star_core", cell=2, speckle=0.15, contrast=1.1)
    px = img.load()
    r = rng("fallen_star_core_facets")
    for _ in range(10):
        px[r.randint(0, 15), r.randint(0, 15)] = c("#D6C8FF")
    for _ in range(6):
        px[r.randint(0, 15), r.randint(0, 15)] = WHITE
    for i in range(16):
        # a brighter diagonal facet edge
        px[i, (i * 3 // 2) % 16] = c("#FFFDF4")
    save(img, "block/fallen_star_core")


def star_flare():
    """Effect mask (white, alpha = shape): four long rays, four short diagonal ones and a soft core."""
    size = 64
    img = new(size, size)
    px = img.load()
    h = size / 2
    for y in range(size):
        for x in range(size):
            dx, dy = (x + 0.5 - h) / h, (y + 0.5 - h) / h
            d = math.hypot(dx, dy)
            if d >= 1.0:
                continue
            core = math.exp(-d * d * 30.0)
            glow = math.exp(-d * d * 6.0) * 0.35
            long_rays = (math.exp(-abs(dx) * 40.0) + math.exp(-abs(dy) * 40.0)) * (1.0 - d) ** 1.4
            u, v = (dx + dy) * 0.7071, (dx - dy) * 0.7071
            short_rays = (math.exp(-abs(u) * 30.0) + math.exp(-abs(v) * 30.0)) * max(0.0, 1.0 - d * 1.8) ** 1.6 * 0.7
            a = min(1.0, core + glow + long_rays + short_rays) * (1.0 - d ** 6)
            px[x, y] = (255, 255, 255, int(round(255 * a)))
    save(img, "effect/star_flare")


def paint():
    starcaller_trumpet()
    star_fragment()
    fallen_star_rock()
    fallen_star_core()
    star_flare()
