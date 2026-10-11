"""Wood family painters: bark, log tops, planks, leaves, saplings, doors, trapdoors."""
from typing import Optional, Sequence

from texlib import Color, c, from_ascii, lerp, new, noise_fill, rng, value_noise, with_alpha


def bark(pal: Sequence[Color], name: str, accent: Optional[Sequence[Color]] = None, accent_rate: float = 0.0,
         alpha: int = 255) -> "Image":
    img = new()
    px = img.load()
    r = rng(name)
    cols = [r.random() for _ in range(16)]
    for x in range(16):
        stripe = 0.5 + 0.5 * ((x * 5 + int(cols[x] * 7)) % 4) / 3.0
        for y in range(16):
            v = stripe * 0.6 + r.random() * 0.4
            px[x, y] = with_alpha(pal[min(len(pal) - 1, int(v * len(pal)))], alpha)
        # vertical grooves
        if x % 4 == r.randint(0, 3):
            for y in range(16):
                if r.random() < 0.7:
                    px[x, y] = with_alpha(pal[0], alpha)
    if accent:
        for _ in range(int(accent_rate * 30)):
            x, y = r.randint(0, 15), r.randint(0, 13)
            length = r.randint(2, 4)
            for k in range(length):
                px[x, (y + k) % 16] = with_alpha(accent[k % len(accent)], 255)
    return img


def rune_bark(pal: Sequence[Color], rune: Color, name: str) -> "Image":
    img = bark(pal, name)
    px = img.load()
    r = rng(name + "_runes")
    for _ in range(3):
        x, y = r.randint(1, 13), r.randint(1, 12)
        shape = r.choice([[(0, 0), (0, 1), (0, 2), (1, 1)], [(0, 0), (1, 1), (2, 0)], [(0, 0), (1, 0), (1, 1), (1, 2)]])
        for dx, dy in shape:
            px[x + dx, y + dy] = rune
    # birch-like dark marks
    for _ in range(4):
        x, y = r.randint(0, 13), r.randint(0, 15)
        px[x, y] = c("#5A5448")
        px[x + 1, y] = c("#7A7464")
    return img


def log_top(bark_pal: Sequence[Color], inner: Sequence[Color], name: str, alpha: int = 255) -> "Image":
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                px[x, y] = with_alpha(bark_pal[1], alpha)
            else:
                ring = int(d) % 2
                px[x, y] = with_alpha(inner[ring + (1 if d < 2 else 0)] if len(inner) > 2 else inner[ring], alpha)
    return img


def planks(pal: Sequence[Color], name: str, alpha: int = 255) -> "Image":
    img = new()
    px = img.load()
    r = rng(name)
    for y in range(16):
        board = y // 4
        offset = (board * 5) % 16
        for x in range(16):
            v = r.random()
            base = pal[1] if v < 0.6 else pal[2] if v < 0.9 else pal[3 % len(pal)]
            if y % 4 == 3:
                base = pal[0]
            elif (x + offset) % 16 == 0:
                base = pal[0]
            elif (x * 3 + y) % 7 == 0 and r.random() < 0.4:
                base = pal[0] if len(pal) < 4 else lerp(pal[0], pal[1], 0.5)
            px[x, y] = with_alpha(base, alpha)
    return img


def leaves(pal: Sequence[Color], name: str, alpha: int = 255, hole_rate: float = 0.22) -> "Image":
    img = new()
    px = img.load()
    r = rng(name)
    n = value_noise(16, 16, r, cell=4, octaves=2)
    for y in range(16):
        for x in range(16):
            if r.random() < hole_rate:
                continue
            v = n[y][x] + (r.random() - 0.5) * 0.4
            col = pal[min(len(pal) - 1, max(0, int(v * len(pal))))]
            px[x, y] = with_alpha(col, alpha)
    return img


SAPLING = [
    "................",
    "................",
    ".......ll.......",
    "......lLLl......",
    "....l.lLLl.l....",
    "...lLllLLllLl...",
    "...lLLlLLlLLl...",
    "....lLLLLLLl....",
    "......lLLl......",
    ".......ss.......",
    ".......ss.......",
    "......lss.......",
    ".......ss.l.....",
    ".......ssll.....",
    ".......ss.......",
    "................",
]


def sapling(leaf: Sequence[Color], stem: Color) -> "Image":
    return from_ascii(SAPLING, {"l": leaf[0], "L": leaf[1], "s": stem})


def door_half(pal: Sequence[Color], name: str, top: bool, window: Optional[Color] = None, alpha: int = 255) -> "Image":
    img = planks(pal, name, alpha)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15):
                px[x, y] = with_alpha(pal[0], 255)
            if (top and y == 0) or (not top and y == 15):
                px[x, y] = with_alpha(pal[0], 255)
    if top and window:
        for y in range(3, 9):
            for x in range(3, 13):
                if x in (7, 8) or y == 6:
                    px[x, y] = with_alpha(pal[0], 255)
                else:
                    px[x, y] = window
    if not top:
        px[12, 4] = c("#3A3A3A")
        px[12, 5] = c("#5A5A5A")
    return img


def trapdoor(pal: Sequence[Color], name: str, alpha: int = 255) -> "Image":
    img = planks(pal, name, alpha)
    px = img.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = with_alpha(pal[0], 255)
    for (x0, y0) in ((3, 3), (9, 3), (3, 9), (9, 9)):
        for y in range(y0, y0 + 4):
            for x in range(x0, x0 + 4):
                px[x, y] = (0, 0, 0, 0)
    return img


DOOR_ITEM = [
    "................",
    "....oooooooo....",
    "....oPPPPPPo....",
    "....oPWWWWPo....",
    "....oPWooWPo....",
    "....oPWWWWPo....",
    "....oPPPPPPo....",
    "....oPpppPPo....",
    "....oPPPPPPo....",
    "....oPPPPPPo....",
    "....oPpppPho....",
    "....oPPPPPPo....",
    "....oPPPPPPo....",
    "....oPpppPPo....",
    "....oooooooo....",
    "................",
]


def door_item(pal: Sequence[Color], window: Color) -> "Image":
    return from_ascii(DOOR_ITEM, {"o": pal[0], "P": pal[2], "p": pal[1], "W": window, "h": c("#3A3A3A")})
