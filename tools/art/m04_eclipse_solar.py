"""M4 textures: Umbral Iron family, Eclipser (4 charge states), Solar Flare family, Sun's Fury, Shadow Walk icon.

The Eclipser is inspired by the provided 32x32 reference: a black thorn-blade with hooked barbs, a soft shadow
aura, a red ribbon crossguard flowing down-right and a black pommel with a red gem, redrawn at the vanilla 16x16.
"""
import math

from texlib import c, from_ascii, lerp, new, ramp, rng, save, with_alpha
from art.common import deepslate_base, ingot, nugget, ore, raw_block, raw_chunk, shards, stone_base, metal_block

UMBRAL = ramp("#1E2233", "#2E3350", "#444B73", "#7B4DFF", "#D8C8FF")
UMBRAL_METAL = ramp("#1E2233", "#2E3350", "#444B73", "#5A6290", "#D8C8FF")
UMBRAL_RAW = ramp("#1A1D2A", "#2A2E44", "#3C4262", "#5A4FA0", "#9B6BFF")
VEIN = [c("#7B4DFF"), c("#9B6BFF")]

# --------------------------------------------------------------------------------------------- eclipser
BLADE_OUTLINE = c("#0B0B10")
AURA = with_alpha(c("#0B0B10"), 80)
BODY = c("#1A1A22")
HIGHLIGHT = c("#2B2B38")
EDGE = c("#B3122E")
EDGE_BRIGHT = c("#E03A52")
CORE = c("#FFF0E0")
RIBBON = c("#B3122E")
RIBBON_LIGHT = c("#E03A52")
RIBBON_DARK = c("#6E0A1C")


def eclipser_design():
    """Returns (body, extra) dicts of (x, y) -> role; outline and aura are derived."""
    body = {}
    for y in range(1, 10):
        body[(14 - y, y)] = "H"
        body[(15 - y, y)] = "B"
    body[(14, 0)] = "B"
    for p, ch in [((7, 5), "B"), ((6, 4), "H"), ((6, 3), "B"), ((7, 2), "B")]:       # upper thorn
        body[p] = ch
    for p, ch in [((8, 9), "B"), ((9, 9), "B"), ((10, 8), "H"), ((11, 7), "B")]:     # lower thorn
        body[p] = ch
    extra = {}
    for p, ch in {(3, 8): "D", (4, 9): "R", (5, 10): "r", (6, 11): "R", (7, 12): "r", (8, 13): "R", (8, 14): "D"}.items():
        extra[p] = ch                                                                # ribbon crossguard
    for p, ch in {(4, 11): "P", (3, 12): "R", (2, 13): "P"}.items():
        extra[p] = ch                                                                # grip with red wrap
    for p, ch in {(1, 14): "G", (0, 14): "P", (1, 15): "P", (1, 13): "P", (2, 14): "P"}.items():
        extra[p] = ch                                                                # pommel + gem
    return body, extra


def eclipser(stage: int):
    body, extra = eclipser_design()
    solid = dict(body)
    solid.update(extra)
    grid = dict(solid)
    blade_outline = set()
    for (x, y) in list(solid):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in solid and 0 <= p[0] < 16 and 0 <= p[1] < 16:
                grid.setdefault(p, "O")
                if (x, y) in body:
                    blade_outline.add(p)
    filled = dict(grid)
    for (x, y) in list(filled):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                p = (x + dx, y + dy)
                if p not in filled and 0 <= p[0] < 16 and 0 <= p[1] < 16:
                    grid.setdefault(p, "a")
    # Red edge pixels on the lower-right (cutting) edge of the main blade, more with each charge stage.
    cutting = sorted([p for p in blade_outline if (p[0] + p[1]) >= 16 and p[1] <= 9], key=lambda p: p[1])
    if stage == 0:
        red = {cutting[i] for i in (1, 3) if i < len(cutting)}
    elif stage == 1:
        red = set(cutting[: len(cutting) // 2])
    else:
        red = set(cutting)
    pal = {"a": AURA, "O": BLADE_OUTLINE, "B": BODY, "H": HIGHLIGHT, "R": RIBBON, "r": RIBBON_LIGHT, "D": RIBBON_DARK,
           "G": EDGE_BRIGHT, "P": BODY}
    img = new()
    px = img.load()
    for (x, y), ch in grid.items():
        col = pal[ch]
        if ch == "O" and (x, y) in red:
            col = EDGE if stage < 3 else EDGE_BRIGHT
        if stage >= 2 and ch == "O" and (x, y) in blade_outline and (x, y) not in red:
            col = RIBBON_DARK if stage == 2 else EDGE
        if stage == 3 and ch == "H":
            col = CORE                       # white-hot core along the spine
        if stage == 3 and ch == "B":
            col = c("#7A0F22")
        if stage >= 2 and ch == "a":
            col = with_alpha(c("#3A0610") if stage == 2 else c("#E03A52"), 70 if stage == 2 else 60)
        px[x, y] = col
    return img


# --------------------------------------------------------------------------------------------- solar
SUN = [c("#8A3A00"), c("#FF6A00"), c("#FFB000"), c("#FFF2A0"), c("#FFFFFF")]

FLARE = [
    "................",
    ".......y........",
    "...y...o....y...",
    "....o.oOo..o....",
    ".....oOOOoo.....",
    "....oOOYYOOo....",
    "..yoOOYwwYOOoy..",
    "...oOYwWWwYOo...",
    "...oOYwWWwYOo...",
    "..yoOOYwwYOOoy..",
    "....oOOYYOOo....",
    ".....ooOOOo.....",
    "....o..oOo.o....",
    "...y...o....y...",
    ".......y........",
    "................",
]

SPEAR = [
    "............wyo.",
    "...........wYYo.",
    "..........yYOOd.",
    ".........yYOOd..",
    "........oYOOd...",
    ".......sdOOd....",
    "......sbdd......",
    ".....sb.........",
    "....sb..........",
    "...sb...........",
    "..sb............",
    ".sb.............",
    "sb..............",
    "bb..............",
    "................",
    "................",
]


def solar_flare():
    pal = {"y": with_alpha(c("#FFF2A0"), 200), "o": SUN[1], "O": SUN[2], "Y": SUN[3], "w": SUN[3], "W": SUN[4]}
    return from_ascii(FLARE, pal)


def solar_fragment():
    return shards([SUN[0], SUN[1], SUN[2], SUN[3], SUN[4]])


def suns_fury():
    pal = {"w": c("#FFFFFF"), "y": with_alpha(c("#FFF2A0"), 210), "Y": c("#FFF2A0"), "O": c("#FFB000"),
           "o": c("#FF7A00"), "d": c("#B04A00"), "s": c("#7A5230"), "b": c("#5A3A1C")}
    return from_ascii(SPEAR, pal)


def shadow_walk_icon():
    img = new(18, 18)
    px = img.load()
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - 8.5, y - 8.5)
            if d < 7.5:
                px[x, y] = c("#0B0B10") if d < 5.5 else c("#2B2B38")
    for (x, y) in ((6, 7), (11, 7)):
        px[x, y] = c("#E03A52")
        px[x + 1, y] = c("#E03A52")
    for k in range(5):
        px[13 + k // 2, 3 + k] = with_alpha(c("#B3122E"), 200)
    return img


def umbral_ore(base, name):
    img = ore(base, [UMBRAL[0], UMBRAL[2], UMBRAL[3], UMBRAL[4]], name, clusters=4)
    px = img.load()
    r = rng(name + "_veins")
    for _ in range(5):  # thin violet veins
        x, y = r.randint(0, 15), r.randint(0, 15)
        for _ in range(3):
            px[x % 16, y % 16] = VEIN[r.randint(0, 1)]
            x += r.choice((-1, 1))
            y += 1
    return img


def paint():
    save(umbral_ore(stone_base(), "umbral_iron_ore"), "block/umbral_iron_ore")
    save(umbral_ore(deepslate_base(), "deepslate_umbral_iron_ore"), "block/deepslate_umbral_iron_ore")
    blk = metal_block(ramp("#161927", "#242A42", "#343B5E", "#444B73", "#6A72A0"), "umbral_iron_block")
    px = blk.load()
    for (x, y) in ((4, 6), (5, 7), (11, 10), (10, 11), (9, 2)):
        px[x, y] = VEIN[1]
    save(blk, "block/umbral_iron_block")
    save(raw_block(UMBRAL_RAW, "raw_umbral_iron_block"), "block/raw_umbral_iron_block")
    save(ingot(UMBRAL_METAL), "item/umbral_iron_ingot")
    save(nugget(UMBRAL_METAL), "item/umbral_iron_nugget")
    save(raw_chunk(UMBRAL_RAW, "raw_umbral_iron"), "item/raw_umbral_iron")
    save(eclipser(0), "item/eclipser")
    for stage in (1, 2, 3):
        save(eclipser(stage), f"item/eclipser_charge_{stage}")
    save(solar_fragment(), "item/solar_flare_fragment")
    save(solar_flare(), "item/solar_flare")
    save(suns_fury(), "item/suns_fury")
    save(shadow_walk_icon(), "mob_effect/shadow_walk")
