"""Shared painters: stone bases, ores, storage blocks, gems, ingots, tools, glass, wood, plants."""
from __future__ import annotations

import math
from typing import Dict, List, Optional, Sequence

from texlib import (Color, bevel, c, from_ascii, lerp, new, noise_fill, outline, ramp, ramp_pick, rng, value_noise,
                    with_alpha)

STONE = ramp("#5E5E5E", "#6E6E6E", "#7C7C7C", "#888888", "#969696")
DEEPSLATE = ramp("#2F2F35", "#3A3A41", "#45454D", "#505059", "#5B5B63")
STICK = {"a": c("#8A6234"), "b": c("#5C3F1E"), "c": c("#3A2712")}


def stone_base(name: str = "lacuna_stone") -> "Image":
    return noise_fill(STONE, name, cell=4, speckle=0.22, contrast=1.3)


def deepslate_base(name: str = "lacuna_deepslate") -> "Image":
    img = noise_fill(DEEPSLATE, name, cell=4, speckle=0.15, contrast=1.2)
    r = rng(name + "_rows")
    px = img.load()
    for y in range(16):
        if y % 4 == 3:
            for x in range(16):
                if r.random() < 0.7:
                    px[x, y] = DEEPSLATE[0]
    return img


def ore(base, gem: Sequence[Color], name: str, clusters: int = 4) -> "Image":
    """Gem clusters with a dark rim over a stone base. gem = ramp dark..light (>=4 colours)."""
    img = base.copy()
    px = img.load()
    r = rng(name)
    centers = []
    tries = 0
    while len(centers) < clusters and tries < 200:
        tries += 1
        cx, cy = r.randint(2, 13), r.randint(2, 13)
        if all(abs(cx - a) + abs(cy - b) > 5 for a, b in centers):
            centers.append((cx, cy))
    for cx, cy in centers:
        shape = [(0, 0), (1, 0), (0, 1), (1, 1)]
        extra = r.sample([(-1, 0), (2, 0), (0, -1), (1, -1), (0, 2), (1, 2), (-1, 1), (2, 1)], 3)
        cells = shape + extra
        for dx, dy in cells:
            x, y = (cx + dx) % 16, (cy + dy) % 16
            px[x, y] = gem[1]
        # rim on lower-right
        for dx, dy in cells:
            for ox, oy in ((1, 0), (0, 1)):
                x, y = (cx + dx + ox) % 16, (cy + dy + oy) % 16
                if (dx + ox, dy + oy) not in cells:
                    px[x, y] = gem[0]
        px[cx % 16, cy % 16] = gem[3]
        px[(cx + 1) % 16, cy % 16] = gem[2]
        px[cx % 16, (cy + 1) % 16] = gem[2]
        if len(gem) > 4:
            px[cx % 16, cy % 16] = gem[4]
    return img


def storage_block(pal: Sequence[Color], name: str) -> "Image":
    """Polished block: bevelled frame, inner facets (5-tone ramp dark..light)."""
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = pal[2]
    for i in range(1, 15):
        px[i, 1] = pal[3]
        px[1, i] = pal[3]
        px[i, 14] = pal[1]
        px[14, i] = pal[1]
    # diamond facet pattern
    for y in range(3, 13):
        for x in range(3, 13):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 3:
                px[x, y] = pal[4]
            elif d < 5:
                px[x, y] = pal[3] if (x + y) % 2 == 0 else pal[2]
    for i in range(3, 13):
        if (i % 3) == 0:
            px[i, 3] = pal[4]
    img = bevel(img, pal[4], pal[0])
    return img


def raw_block(pal: Sequence[Color], name: str) -> "Image":
    img = noise_fill(pal[:4], name, cell=4, speckle=0.12, contrast=1.6)
    px = img.load()
    r = rng(name + "_lumps")
    for _ in range(10):
        x, y = r.randint(0, 15), r.randint(0, 15)
        px[x, y] = pal[4] if len(pal) > 4 else pal[3]
        px[(x + 1) % 16, (y + 1) % 16] = pal[0]
    return img


def metal_block(pal: Sequence[Color], name: str) -> "Image":
    """Ingot-style storage block: horizontal plates with rivets."""
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            band = (y // 4) % 2
            px[x, y] = pal[2] if band == 0 else pal[3]
        if y % 4 == 0:
            for x in range(16):
                px[x, y] = pal[4]
        if y % 4 == 3:
            for x in range(16):
                px[x, y] = pal[1]
    for (x, y) in ((2, 1), (13, 1), (2, 9), (13, 9), (7, 5), (7, 13)):
        px[x, y] = pal[0]
        px[x + 1, y] = pal[4]
    return bevel(img, pal[4], pal[0])


# --------------------------------------------------------------------------------------------- items

GEM_SHAPE = [
    "................",
    "................",
    "................",
    "....oooooooo....",
    "...oh4h44433o...",
    "..o4h4444333do..",
    ".o44444433332do.",
    ".oooooooooooooo.",
    "..o4433332ddo...",
    "...o433322do....",
    "....o3322do.....",
    ".....o32do......",
    "......o2o.......",
    ".......o........",
    "................",
    "................",
]


def gem(pal: Sequence[Color], outline_col: Optional[Color] = None) -> "Image":
    """pal: [dark, mid-dark, mid, light, highlight]."""
    o = outline_col or pal[0]
    m = {"o": o, "d": pal[0], "2": pal[1], "3": pal[2], "4": pal[3], "h": pal[4]}
    img = from_ascii(GEM_SHAPE, m)
    # facet line across the girdle uses a mid tone instead of the outline
    px = img.load()
    for x in range(2, 14):
        if px[x, 7][3]:
            px[x, 7] = pal[1] if x % 3 else pal[2]
    px[1, 7] = o
    px[14, 7] = o
    return img


RAW_SHAPE = [
    "................",
    "................",
    "................",
    "......oooo......",
    "....oo3443o.....",
    "...o3344h43o....",
    "...o23344443o...",
    "..o2233344432o..",
    "..o22333444332o.",
    "..o1223333332o..",
    "...o11223322o...",
    "....oo11222o....",
    "......ooooo.....",
    "................",
    "................",
    "................",
]


def raw_chunk(pal: Sequence[Color], name: str) -> "Image":
    m = {"o": pal[0], "1": pal[0], "2": pal[1], "3": pal[2], "4": pal[3], "h": pal[4] if len(pal) > 4 else pal[3]}
    img = from_ascii(RAW_SHAPE, m)
    px = img.load()
    r = rng(name)
    for _ in range(4):
        x, y = r.randint(4, 11), r.randint(5, 10)
        if px[x, y][3] and px[x, y] not in (pal[0],):
            px[x, y] = pal[1]
    return img


INGOT_SHAPE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......oooooo...",
    ".....oo4hh443o..",
    "...oo44h44433do.",
    ".oo443444433ddo.",
    "o4h44444333ddo..",
    "o3444433322do...",
    "o2333332222o....",
    ".oo22222ddo.....",
    "...ooooooo......",
    "................",
    "................",
]


def ingot(pal: Sequence[Color]) -> "Image":
    m = {"o": pal[0], "d": pal[0], "2": pal[1], "3": pal[2], "4": pal[3], "h": pal[4]}
    return from_ascii(INGOT_SHAPE, m)


NUGGET_SHAPE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......ooo.......",
    ".....o4h3o......",
    "....o44332o.....",
    "....o43322o..o..",
    ".....oo22o..o4o.",
    "..o....oo...o3o.",
    ".o4o.........o..",
    ".o3o....ooo.....",
    "..o....o432o....",
    ".......o322o....",
    "........ooo.....",
]


def nugget(pal: Sequence[Color]) -> "Image":
    m = {"o": pal[0], "2": pal[1], "3": pal[2], "4": pal[3], "h": pal[4]}
    return from_ascii(NUGGET_SHAPE, m)


SHARD_SHAPE = [
    "................",
    "..........o.....",
    ".........oho....",
    "........oh4o....",
    "........o44o....",
    ".......oh43o....",
    "...o...o443o....",
    "..oho.oh433o....",
    "..o43oo4432o.o..",
    "...o3o44332oo4o.",
    "...o32o4322o432o",
    "....o2o3322o32o.",
    ".....oo322o.o2o.",
    "......o22o...o..",
    ".......oo.......",
    "................",
]


def shards(pal: Sequence[Color]) -> "Image":
    m = {"o": pal[0], "2": pal[1], "3": pal[2], "4": pal[3], "h": pal[4]}
    return from_ascii(SHARD_SHAPE, m)


# --------------------------------------------------------------------------------------------- tools
# o outline (material dark), d dark, m mid, l light, h highlight; a/b stick light/dark, c stick outline

TOOL_SHAPES: Dict[str, List[str]] = {
    "sword": [
        ".............ooo",
        "............ohlo",
        "...........ohlmo",
        "..........ohlmo.",
        ".........ohlmo..",
        "........ohlmo...",
        ".......ohlmo....",
        "......ohlmo.....",
        "..oo.ohlmo......",
        "..odohlmo.......",
        "...odlmo........",
        "...cdmdo........",
        "..cac.odo.......",
        ".cac...oo.......",
        "cbc.............",
        "cc..............",
    ],
    "pickaxe": [
        "................",
        ".....oooooo.....",
        "....ohllllmo....",
        ".....oooomlmo...",
        "........ocmdmo..",
        ".......cac.odmo.",
        "......cac...odo.",
        ".....cac....odo.",
        "....cac......oo.",
        "...cac..........",
        "..cac...........",
        ".cac............",
        "cac.............",
        "cbc.............",
        "cc..............",
        "................",
    ],
    "axe": [
        "................",
        "....oooo...cc...",
        "...ohllmo.cac...",
        "..ollmmmdoac....",
        "..olmmmddoc.....",
        "..ommmddoc......",
        "...ommdoc.......",
        "....oooc........",
        "....cac.........",
        "...cac..........",
        "..cac...........",
        ".cac............",
        "cac.............",
        "cbc.............",
        "cc..............",
        "................",
    ],
    "shovel": [
        "................",
        "...........ooo..",
        "..........ohlmo.",
        ".........ohlmmo.",
        "........ohlmmdo.",
        ".........olmdo..",
        "........cooo....",
        ".......cac......",
        "......cac.......",
        ".....cac........",
        "....cac.........",
        "...cac..........",
        "..cac...........",
        ".cac............",
        "cbc.............",
        "cc..............",
    ],
    "hoe": [
        "................",
        "......oooo......",
        ".....ohllmo.....",
        "......oooddo....",
        ".........cdo....",
        "........cac.....",
        ".......cac......",
        "......cac.......",
        ".....cac........",
        "....cac.........",
        "...cac..........",
        "..cac...........",
        ".cac............",
        "cbc.............",
        "cc..............",
        "................",
    ],
}


def tool(kind: str, pal: Sequence[Color], stick: Optional[Dict[str, Color]] = None) -> "Image":
    """pal: [outline, dark, mid, light, highlight]."""
    st = stick or STICK
    m = {"o": pal[0], "d": pal[1], "m": pal[2], "l": pal[3], "h": pal[4], "a": st["a"], "b": st["b"], "c": st["c"]}
    return from_ascii(TOOL_SHAPES[kind], m)


# --------------------------------------------------------------------------------------------- glass

def glass(frame: Sequence[Color], fill: Color, streak: Color, name: str, fringe: Sequence[Color] = ()) -> "Image":
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = fill
    for i in range(16):
        px[i, 0] = frame[1]
        px[0, i] = frame[1]
        px[i, 15] = frame[0]
        px[15, i] = frame[0]
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        px[x, y] = frame[2] if len(frame) > 2 else frame[1]
    for k in range(3):
        px[3 + k, 4 - k] = streak
        px[10 + k, 12 - k] = streak
    px[4, 5] = streak
    r = rng(name)
    for col in fringe:
        x, y = r.randint(2, 13), r.randint(2, 13)
        px[x, y] = col
    return img


def pane_top(frame: Sequence[Color]) -> "Image":
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(7, 9):
            px[x, y] = frame[1] if x == 7 else frame[0]
    return img


# --------------------------------------------------------------------------------------------- misc

def shift(img, dx: int, dy: int):
    out = new(img.width, img.height)
    out.paste(img, (dx, dy))
    return out


def darken(col: Color, f: float) -> Color:
    return (int(col[0] * f), int(col[1] * f), int(col[2] * f), col[3])


def tint_ramp(base: Sequence[Color], tint: Color, t: float) -> List[Color]:
    return [lerp(b, tint, t) for b in base]
