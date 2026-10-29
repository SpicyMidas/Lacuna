"""Mod list logo (neoforge.mods.toml logoFile, shown by the Mods screen): an eclipse emblem and the LACUNA wordmark,
sunlit gold above and umbral violet below (light and shadow), painted at 104x30 and scaled x4 with nearest-neighbour
sampling to 416x120 (the mods.toml sets logoBlur=false so the pixels stay crisp).

Written to the jar root, src/main/resources/lacuna_logo.png (outside the textures folder gen_textures rebuilds).
Runs with gen_textures (ART_MODULES) or alone: python3 tools/art/m20_logo.py
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # standalone runs
from texlib import ROOT, c, lerp, new  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "lacuna_logo.png")
W, H = 104, 30
SCALE = 4

GLYPHS = {
    "L": ["###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "#########",
          "#########",
          "#########"],
    "A": ["...####...",
          "..######..",
          ".###..###.",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "##########",
          "##########",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###"],
    "C": ["..#######",
          ".########",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          "###......",
          ".########",
          "..#######"],
    "U": ["###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "####..####",
          ".########.",
          "..######.."],
}
GLYPH_H = 14
WORD = "LACUNA"
WORD_X = 33
WORD_Y = 8
GAP = 2

GOLD = [c("#FFF7DC"), c("#FBE6A6"), c("#F2CF74"), c("#E6B656")]
VIOLET = [c("#A784E6"), c("#8E68D6"), c("#7A52C4"), c("#6640AE"), c("#55339A")]
OUTLINE = c("#1A0F2B")
SHADOW = c("#0B0614", 210)
DISC = c("#140B22")
DISC_IN = c("#22143A")
RIM = c("#FFB84A")
CORONA_IN = c("#FFF3C4")
CORONA_OUT = c("#F2B94A")
HALO = c("#FFD36B")
SPARK = c("#E8FBFF")
SPARK_ARM = c("#8FDCFF", 200)


def glyph_n():
    """N with a real diagonal: stems at both sides, a 3 px band from the top left to the bottom right."""
    w = 10
    rows = []
    for r in range(GLYPH_H):
        centre = 1 + (w - 2) * r / (GLYPH_H - 1)
        row = ""
        for x in range(w):
            on = x < 3 or x >= w - 3 or abs(x - centre) <= 1.0
            row += "#" if on else "."
        rows.append(row)
    return rows


def word_mask():
    glyphs = dict(GLYPHS, N=glyph_n())
    mask = set()
    x = WORD_X
    for ch in WORD:
        rows = glyphs[ch]
        for gy, row in enumerate(rows):
            for gx, cell in enumerate(row):
                if cell == "#":
                    mask.add((x + gx, WORD_Y + gy))
        x += len(rows[0]) + GAP
    return mask, x - GAP


def fill_colour(x, y, mask):
    """Gold above, violet below, a one-row checker dither between; light top edges, shaded bottom edges."""
    row = y - WORD_Y
    if row < 6 or (row == 6 and (x + y) % 2 == 0):
        col = GOLD[min(len(GOLD) - 1, max(0, row - 1)) if row > 0 else 0]
    else:
        col = VIOLET[min(len(VIOLET) - 1, max(0, (row - 6) // 2))]
    if (x, y - 1) not in mask:
        col = lerp(col, c("#FFFFFF"), 0.35)
    elif (x, y + 1) not in mask:
        col = lerp(col, c("#2A1648"), 0.30)
    return col


def blend(px, x, y, col):
    if not (0 <= x < W and 0 <= y < H):
        return
    r, g, b, a = col
    if a >= 255:
        px[x, y] = col
        return
    br, bg, bb, ba = px[x, y]
    t = a / 255.0
    out_a = a + ba * (1 - t)
    if out_a <= 0:
        return
    mix = lambda s, d: int(round((s * a + d * ba * (1 - t)) / out_a))  # noqa: E731
    px[x, y] = (mix(r, br), mix(g, bg), mix(b, bb), int(round(out_a)))


def paint_emblem(px):
    cx, cy = 15.0, 15.0
    for y in range(H):
        for x in range(0, 31):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dy, dx))
            if d < 9.6:
                # the dark disc, a touch lighter towards its centre, rim light on the lower right
                col = lerp(DISC_IN, DISC, min(1.0, d / 9.6))
                if d > 8.4 and -40.0 <= ang <= 110.0:
                    col = RIM if d > 8.9 else lerp(DISC, RIM, 0.55)
                blend(px, x, y, col)
            elif d < 11.9:
                blend(px, x, y, lerp(CORONA_IN, CORONA_OUT, (d - 9.6) / 2.3))
            elif d < 14.1:
                # eight rays (long on the axes, short on the diagonals) inside a faint halo
                k = (ang + 360.0) % 45.0
                on_ray = k < 4.0 or k > 41.0
                axis = round(((ang + 360.0) % 360.0) / 45.0) % 2 == 0
                if on_ray and (axis or d < 13.1):
                    blend(px, x, y, lerp(c("#FFE9A0"), HALO, (d - 11.9) / 2.2))
                else:
                    blend(px, x, y, (HALO[0], HALO[1], HALO[2], int(90 * (1.0 - (d - 11.9) / 2.2))))
    # a spark of light held inside the void
    sx, sy = 15, 15
    blend(px, sx, sy, SPARK)
    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        blend(px, sx + ox, sy + oy, SPARK_ARM)
    for ox, oy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        blend(px, sx + ox, sy + oy, (SPARK_ARM[0], SPARK_ARM[1], SPARK_ARM[2], 90))


def paint_word(px):
    mask, end = word_mask()
    outline = set()
    for (x, y) in mask:
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                p = (x + ox, y + oy)
                if p not in mask:
                    outline.add(p)
    for (x, y) in sorted(outline | mask):
        blend(px, x + 1, y + 1, SHADOW)
    for (x, y) in sorted(outline):
        blend(px, x, y, OUTLINE)
    for (x, y) in sorted(mask):
        blend(px, x, y, fill_colour(x, y, mask))
    # a thin horizon under the word, gold to violet, fading at both ends
    y = WORD_Y + GLYPH_H + 3
    for x in range(WORD_X, end):
        t = (x - WORD_X) / max(1, end - WORD_X - 1)
        fade = min(1.0, min(t, 1.0 - t) * 6.0)
        col = lerp(GOLD[2], VIOLET[1], t)
        blend(px, x, y, (col[0], col[1], col[2], int(170 * fade)))


def paint_stars(px):
    for x, y, a in ((31, 3, 230), (57, 3, 160), (79, 2, 200), (101, 5, 220), (46, 26, 150), (70, 28, 190),
                    (95, 26, 170), (3, 27, 150), (27, 1, 120)):
        blend(px, x, y, (SPARK[0], SPARK[1], SPARK[2], a))
    for x, y in ((90, 3), (37, 27)):
        blend(px, x, y, SPARK)
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            blend(px, x + ox, y + oy, SPARK_ARM)


def paint():
    img = new(W, H)
    px = img.load()
    paint_emblem(px)
    paint_word(px)
    paint_stars(px)
    big = img.resize((W * SCALE, H * SCALE), Image.NEAREST)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    big.save(OUT, format="PNG", optimize=False)


if __name__ == "__main__":
    paint()
    print(f"m20_logo: wrote {os.path.relpath(OUT, ROOT)} ({W * SCALE}x{H * SCALE})")
