"""The Lacuna Almanac: its 16x16 item (a thick book bound in dark violet leather, gold corners, a ruby set in the
cover, a ruby ribbon) and the GUI sheet of the book screen (textures/gui/almanac.png, 256x256).

Sheet regions (mirrored by client/guide/AlmanacUi.java). Stretchable pieces are 9-slices whose edges and centres are
painted to tile seamlessly, because the screen repeats them (never stretches) at any book size:
  (0,0)    64x64  cover, border 12: leather with a ruby inlay line and gold corner caps (only the outer 7 px show)
  (64,0)   48x48  left page, border 6: parchment, page-stack lines on the outer (left) edge
  (112,0)  48x48  right page, mirrored
  (160,0)  16x48  spine fold, repeated down the seam between the pages (shadow with alpha)
  (176,0)  24x24  "where to find it" box, border 5: warm parchment, ruby bar on the left, gold rim
  (176,24) 24x12  hovered contents row, border 3 (translucent ruby wash, gold rim)
  (200,0)  20x20  chapter tab, border 4: normal; (220,0) hovered; (200,20) the current chapter
  (0,64)   18x10  previous-page arrow, (0,74) hovered; (18,64) / (18,74) next-page arrow
  (64,48)  64x7   ornament under titles
  (128,48) 9x9    glyphs, 10 px apart: where (compass), contents (list), bullet, star
"""
from texlib import c, from_ascii, lerp, new, rng, save, value_noise

SHEET = "gui/almanac"

# leather: the Departure Shard's violet, darkened into a binding
OUTLINE = c("#140A1E")
LEATHER = [c("#24122F"), c("#2E1840"), c("#3A2050"), c("#4A2766"), c("#5A3079")]
LEATHER_HI = c("#7A4FA0")
RUBY = [c("#4A0716"), c("#8E0F2A"), c("#B3122E"), c("#D6143F"), c("#FF5C7A"), c("#FFC2CC")]
GOLD = [c("#5A4220"), c("#8A6A2E"), c("#C9A869"), c("#E7C46A"), c("#F6E3A0")]
PARCH = [c("#D9CBA6"), c("#E2D5B2"), c("#E9DDBD"), c("#EFE6CF")]
PARCH_EDGE = [c("#B8A37A"), c("#CDBD95"), c("#D3C49D")]
INK = c("#3B2A1A")
INK_SOFT = c("#6B5A44")


def pick(pal, v):
    v = min(0.9999, max(0.0, v))
    return pal[int(v * len(pal))]


# ============================================================================================ the item

ITEM = [
    "................",
    "..oooooooooooo..",
    ".oGgLLLLLLLLgGo.",
    ".ogLlllllllllgop",
    ".obLlhllllllllop",
    ".oSLllllYllllLop",
    ".oSLlllYrYlllLop",
    ".obLllYrPRYllLop",
    ".oSLllYRRxYllLop",
    ".oSLlllYxYlllLop",
    ".obLllllYllllLop",
    ".oSLlllllllllLop",
    ".ogLLLLLLLLLLgoq",
    ".oGgddddddddgGoq",
    "..oooooooooRoooq",
    "...qqqqqqqqRqq..",
]


def item():
    pal = {
        "o": OUTLINE, "L": LEATHER[3], "l": LEATHER[4], "h": LEATHER_HI, "d": LEATHER[2],
        "S": LEATHER[1], "b": GOLD[2],
        "G": GOLD[3], "g": GOLD[1], "Y": GOLD[3],
        "r": RUBY[1], "R": RUBY[3], "P": RUBY[5], "x": RUBY[0],
        "p": PARCH[3], "q": PARCH_EDGE[1],
    }
    img = from_ascii(ITEM, pal)
    px = img.load()
    # a little leather grain on the cover, never on the trim
    r = rng("almanac_item_grain")
    for y in range(3, 12):
        for x in range(4, 13):
            if px[x, y] == LEATHER[4] and r.random() < 0.22:
                px[x, y] = LEATHER[3]
    # page edges: alternate light and shade like stacked leaves
    for y in range(3, 12):
        px[15, y] = PARCH[3] if y % 2 else PARCH_EDGE[2]
    save(img, "item/almanac")


# ============================================================================================ the sheet

def leather(px, ox, oy, w, h, name, pal=LEATHER[1:5], period=40, cell=5):
    """Tileable pebbled leather (period {@code period} from the region origin: the 9-slice centre of the piece)."""
    r = rng(name)
    n = value_noise(period, period, r, cell=cell, octaves=2)
    for y in range(h):
        for x in range(w):
            v = 0.3 + n[y % period][x % period] * 0.45
            roll = r.random()
            if roll < 0.06:
                v -= 0.3
            elif roll < 0.09:
                v += 0.25
            px[ox + x, oy + y] = pick(pal, v)


def parchment(px, ox, oy, w, h, name, pal=PARCH, period=36, cell=6, octaves=3):
    r = rng(name)
    n = value_noise(period, period, r, cell=cell, octaves=octaves)
    for y in range(h):
        for x in range(w):
            v = 0.42 + (n[y % period][x % period] - 0.5) * 0.55
            roll = r.random()
            if roll < 0.03:
                v += 0.18
            elif roll < 0.05:
                v -= 0.14
            px[ox + x, oy + y] = pick(pal, v)


def cover(px, ox, oy, size=64):
    leather(px, ox, oy, size, size, "almanac_cover")
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d == 0:
                col = OUTLINE
            elif d == 1:
                col = LEATHER_HI if (x == 1 or y == 1) else LEATHER[1]
            elif d == 3:
                # the ruby inlay: a hairline with a glint every few pixels along the edge
                along = x if min(y, size - 1 - y) == 3 else y
                col = RUBY[3] if along % 6 == 0 else RUBY[2]
            elif d == 4:
                col = RUBY[0] if (x == 4 or y == 4) else None
            elif d == 6:
                col = LEATHER[0]
            else:
                col = None
            if col is not None and d < 7:
                px[ox + x, oy + y] = col
    # gold corner caps (triangles in the outer corners) with a bevel
    for cx, cy, sx, sy in ((0, 0, 1, 1), (size - 1, 0, -1, 1), (0, size - 1, 1, -1), (size - 1, size - 1, -1, -1)):
        for dy in range(8):
            for dx in range(8):
                if dx + dy > 8:
                    continue
                x, y = cx + sx * dx, cy + sy * dy
                if dx + dy == 8 or dx == 0 or dy == 0:
                    col = GOLD[0]
                elif dx + dy == 7:
                    col = GOLD[1]
                elif dx == 1 or dy == 1:
                    col = GOLD[4] if (sx > 0 and sy > 0) else GOLD[3]
                else:
                    col = GOLD[3] if (dx + dy) % 3 else GOLD[2]
                px[ox + x, oy + y] = col
        # a tiny ruby rivet in each cap
        px[ox + cx + sx * 2, oy + cy + sy * 2] = RUBY[3]
    for (x, y) in ((0, 0), (size - 1, 0), (0, size - 1), (size - 1, size - 1)):
        px[ox + x, oy + y] = (0, 0, 0, 0)


def page(px, ox, oy, right, size=48):
    parchment(px, ox, oy, size, size, "almanac_page")
    for y in range(size):
        for x in range(size):
            outer = (size - 1 - x) if right else x       # distance from the outer (fore-) edge
            inner = x if right else (size - 1 - x)       # distance from the spine
            top, bottom = y, size - 1 - y
            col = None
            if outer <= 3:
                # page-stack lines along the fore-edge
                col = [PARCH_EDGE[0], PARCH[3], PARCH_EDGE[1], PARCH[2]][outer]
            elif top == 0:
                col = PARCH_EDGE[2]
            elif bottom == 0:
                col = PARCH_EDGE[1]
            elif bottom == 1:
                col = PARCH_EDGE[2]
            elif inner <= 2:
                col = lerp(px[ox + x, oy + y], PARCH_EDGE[1], 0.45 - inner * 0.15)
            if col is not None:
                px[ox + x, oy + y] = col
    # the fore-edge corners are cut round
    for y in (0, size - 1):
        x = size - 1 if right else 0
        px[ox + x, oy + y] = (0, 0, 0, 0)


def spine(px, ox, oy, w=16, h=48):
    """The fold: shadow deepening toward the seam from both pages, a dark crease, a faint lit lip either side."""
    shade = c("#2A1A10")
    for y in range(h):
        for x in range(w):
            d = abs(x - 7.5)          # 0.5 at the seam
            if d < 1:
                col = (0x5A, 0x46, 0x30, 200)
            else:
                a = max(0, int(150 * (1 - (d - 1) / 6.5) ** 2))
                col = (shade[0], shade[1], shade[2], a)
            px[ox + x, oy + y] = col


def where_box(px, ox, oy, size=24):
    parchment(px, ox, oy, size, size, "almanac_where", pal=[c("#EFE3C4"), c("#F3E9CF"), c("#F6EDD7"), c("#F8F1DE")],
              period=14, cell=7, octaves=1)
    rim = c("#B08A4A")
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d == 0:
                px[ox + x, oy + y] = rim
            elif x in (1, 2) and 0 < y < size - 1:
                px[ox + x, oy + y] = RUBY[2] if x == 1 else RUBY[1]
            elif d == 1 and (y == 1 or y == size - 2 or x == size - 2):
                px[ox + x, oy + y] = lerp(px[ox + x, oy + y], rim, 0.25)
    for (x, y) in ((0, 0), (size - 1, 0), (0, size - 1), (size - 1, size - 1)):
        px[ox + x, oy + y] = (0, 0, 0, 0)


def row_hover(px, ox, oy, w=24, h=12):
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            if d == 0:
                col = (GOLD[2][0], GOLD[2][1], GOLD[2][2], 150)
            else:
                col = (RUBY[1][0], RUBY[1][1], RUBY[1][2], 34)
            px[ox + x, oy + y] = col
    for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        px[ox + x, oy + y] = (0, 0, 0, 0)


def tab(px, ox, oy, state, size=20):
    """state 0 normal leather, 1 hovered (lighter, gold rim), 2 current chapter (parchment, ruby rim)."""
    if state == 2:
        parchment(px, ox, oy, size, size, "almanac_tab_sel", period=12, cell=4, octaves=2)
    else:
        leather(px, ox, oy, size, size, f"almanac_tab_{state}", pal=LEATHER[2:] + [LEATHER_HI] if state == 1 else LEATHER[1:4],
                period=12, cell=4)
    for y in range(size):
        for x in range(size):
            d = min(y, size - 1 - y, size - 1 - x)       # the left side is hidden under the cover
            if d == 0:
                col = OUTLINE
            elif d == 1:
                col = {0: LEATHER[3], 1: GOLD[3], 2: RUBY[2]}[state]
            else:
                continue
            px[ox + x, oy + y] = col
    for y in (0, size - 1):
        px[ox + size - 1, oy + y] = (0, 0, 0, 0)


ARROW_NEXT = [
    "..................",
    "...........oo.....",
    "...........oAo....",
    ".ooooooooooooAAo..",
    ".oaaaaaaaaaaaaAAo.",
    ".oaaaaaaaaaaaaAAo.",
    ".ooooooooooooAAo..",
    "...........oAo....",
    "...........oo.....",
    "..................",
]


def arrows(px, ox, oy):
    for k, (shaft, head, outline) in enumerate(((INK_SOFT, INK, c("#2A1A10")), (RUBY[2], RUBY[3], RUBY[0]))):
        img = from_ascii(ARROW_NEXT, {"o": outline, "a": shaft, "A": head})
        p = img.load()
        for y in range(10):
            for x in range(18):
                col = p[x, y]
                if col[3]:
                    px[ox + 18 + x, oy + k * 10 + y] = col          # next: points right
                    px[ox + 17 - x, oy + k * 10 + y] = col          # previous: mirrored


ORNAMENT = [
    "................................................................",
    "..............iii..........gg.ggg.gg..........iii...............",
    "............ii...ii.....ggg..rRRr..ggg.....ii...ii..............",
    "iiiiiiiiiiii......iiigggg...rRWRRr...ggggiii......iiiiiiiiiiiiii",
    "............ii...ii.....ggg..rRRr..ggg.....ii...ii..............",
    "..............iii..........gg.ggg.gg..........iii...............",
    "................................................................",
]


def ornament(px, ox, oy):
    img = from_ascii(ORNAMENT, {"i": c("#5A4630"), "g": GOLD[2], "r": RUBY[1], "R": RUBY[3], "W": RUBY[5]})
    p = img.load()
    for y in range(7):
        for x in range(64):
            if p[x, y][3]:
                px[ox + x, oy + y] = p[x, y]


GLYPHS = {
    "where": ["....g....", "...gYg...", "...rYr...", ".o.rRr.o.", "gYYRWRYYg", ".o.rRr.o.", "...rYr...", "...gYg...", "....g...."],
    "contents": [".........", "Rr.iiiii.", "rR.......", ".........", "Rr.iiiii.", "rR.......", ".........", "Rr.iiiii.", "rR......."],
    "bullet": [".........", ".........", "....r....", "...rRr...", "..rRWRr..", "...rRr...", "....r....", ".........", "........."],
    "star": ["....Y....", "....Y....", "...YgY...", "YYYgWgYYY", ".YgWWWgY.", "..YgWgY..", ".Yg...gY.", "Yg.....gY", "........."],
}


def glyphs(px, ox, oy):
    pal = {"g": GOLD[1], "Y": GOLD[3], "r": RUBY[1], "R": RUBY[3], "W": RUBY[5], "o": INK_SOFT, "i": INK}
    for k, rows in enumerate(GLYPHS.values()):
        img = from_ascii(rows, pal)
        p = img.load()
        for y in range(9):
            for x in range(9):
                if p[x, y][3]:
                    px[ox + k * 10 + x, oy + y] = p[x, y]


def sheet():
    img = new(256, 256)
    px = img.load()
    cover(px, 0, 0)
    page(px, 64, 0, right=False)
    page(px, 112, 0, right=True)
    spine(px, 160, 0)
    where_box(px, 176, 0)
    row_hover(px, 176, 24)
    tab(px, 200, 0, 0)
    tab(px, 220, 0, 1)
    tab(px, 200, 20, 2)
    arrows(px, 0, 64)
    ornament(px, 64, 48)
    glyphs(px, 128, 48)
    save(img, SHEET)


def paint():
    item()
    sheet()
