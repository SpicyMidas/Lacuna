"""M18 civic screens: one 256x256 GUI sheet (textures/gui/civic.png) for the village civic block screens.

Vanilla-container-like pieces in the village palette (oak frame, brass studs, parchment, ledger-red banner). The
regions below are mirrored by client/village/CivicUi.java; edges and centres are painted to tile seamlessly because
the screen draws them as 9-slices (corners fixed, edges and centre repeated).
"""
from texlib import c, lerp, new, rng, save, value_noise

SHEET = "gui/civic"

OUTLINE = c("#1E120A")
WOOD = [c("#3A2416"), c("#4A3220"), c("#5E4029"), c("#734F33"), c("#8A6040")]
WOOD_HI = c("#9C7048")
WOOD_LO = c("#2A1A10")
PARCH = [c("#D9CBA6"), c("#E2D5B2"), c("#E9DDBD"), c("#EFE6CF")]
PARCH_SHADOW = c("#C2B08A")
PARCH_DEEP = [c("#CDBD95"), c("#D3C49D"), c("#D9CBA6")]
BRASS = [c("#6E5426"), c("#A8843E"), c("#C9A869"), c("#E7C46A"), c("#F6E3A0")]
RED = [c("#4A1C16"), c("#6B2A22"), c("#8C3A2E"), c("#A84A38")]
INK = c("#3B2A1A")
GREEN = c("#4E8A3A")
GREEN_HI = c("#7CC05A")
CROSS_RED = c("#A8322A")
CROSS_HI = c("#D8584A")


def _pick(pal, v):
    v = min(0.9999, max(0.0, v))
    return pal[int(v * len(pal))]


def _parchment(px, x0, y0, w, h, name, pal=PARCH, period=32):
    """Tileable parchment fill (period 32 from the region origin)."""
    r = rng(name)
    n = value_noise(period, period, r, cell=8, octaves=3)
    for y in range(h):
        for x in range(w):
            v = n[y % period][x % period]
            if r.random() < 0.06:
                v += 0.18
            px[x0 + x, y0 + y] = _pick(pal, 0.25 + v * 0.75)


def _wood(px, x, y, along, name):
    """Wood frame pixel with grain running `along` ('h' or 'v'), tileable with period 32."""
    r = rng(f"{name}_{along}")
    if not hasattr(_wood, "grain"):
        _wood.grain = value_noise(32, 32, rng("civic_wood_grain"), cell=4, octaves=2)
    g = _wood.grain
    v = g[(y * 5) % 32][(x // 4) % 32] if along == "h" else g[(x * 5) % 32][(y // 4) % 32]
    return _pick(WOOD[1:4], v)


def panel(px, ox, oy, size=48, border=8):
    """Oak frame (1 outline, 5 wood, 1 dark seam) around parchment; brass studs in the corners."""
    _parchment(px, ox + border, oy + border, size - 2 * border, size - 2 * border, "civic_panel_parchment")
    for y in range(size):
        for x in range(size):
            inner = border <= x < size - border and border <= y < size - border
            if inner:
                continue
            d = min(x, y, size - 1 - x, size - 1 - y)  # distance from the outer edge
            if d == 0:
                col = OUTLINE
            elif d <= 5:
                along = "h" if min(y, size - 1 - y) <= min(x, size - 1 - x) else "v"
                col = _wood(px, x, y, along, "panel")
                if d == 1:
                    col = WOOD_HI if (x == 1 or y == 1) and x < size - 2 and y < size - 2 else WOOD[0]
                elif d == 5:
                    col = WOOD[0] if (x == 5 or y == 5) else WOOD[3]
            elif d == 6:
                col = WOOD_LO
            else:  # d == 7: parchment seam, shadowed under the top/left frame
                col = PARCH_SHADOW if (x == 7 or y == 7) else PARCH[1]
            px[ox + x, oy + y] = col
    # rounded outer corners
    for (x, y) in ((0, 0), (size - 1, 0), (0, size - 1), (size - 1, size - 1)):
        px[ox + x, oy + y] = (0, 0, 0, 0)
    for (x, y) in ((1, 1), (size - 2, 1), (1, size - 2), (size - 2, size - 2)):
        px[ox + x, oy + y] = OUTLINE
    # brass studs
    for (cx, cy) in ((3, 3), (size - 4, 3), (3, size - 4), (size - 4, size - 4)):
        stud = {(-1, -1): BRASS[4], (0, -1): BRASS[3], (1, -1): BRASS[2], (-1, 0): BRASS[3], (0, 0): BRASS[2],
                (1, 0): BRASS[1], (-1, 1): BRASS[2], (0, 1): BRASS[1], (1, 1): BRASS[0]}
        for (dx, dy), col in stud.items():
            px[ox + cx + dx, oy + cy + dy] = col


def inset(px, ox, oy, size=24, pal=PARCH_DEEP, name="civic_inset"):
    """Recessed list area: dark top/left, light bottom/right (like a vanilla slot)."""
    _parchment(px, ox, oy, size, size, name, pal=pal, period=8)
    for i in range(size):
        px[ox + i, oy] = WOOD[1]
        px[ox, oy + i] = WOOD[1]
        px[ox + i, oy + 1] = PARCH_SHADOW
        px[ox + 1, oy + i] = PARCH_SHADOW
        px[ox + i, oy + size - 1] = PARCH[3]
        px[ox + size - 1, oy + i] = PARCH[3]
    px[ox + size - 1, oy] = PARCH[1]
    px[ox, oy + size - 1] = PARCH[1]


def row(px, ox, oy, hover, size=24):
    """A list card: raised parchment with a thin ink frame (gold when hovered)."""
    base = [c("#EDE2C6"), c("#F1E8D0"), c("#F4EDD8")] if not hover else [c("#F3E2B0"), c("#F6E8BE"), c("#F9EFCC")]
    _parchment(px, ox, oy, size, size, "civic_row" + ("_hover" if hover else ""), pal=base, period=8)
    edge = BRASS[1] if hover else c("#B9A783")
    for i in range(size):
        for (x, y) in ((i, 0), (0, i), (i, size - 1), (size - 1, i)):
            px[ox + x, oy + y] = edge
    for i in range(1, size - 1):
        px[ox + i, oy + size - 2] = lerp(base[0], edge, 0.35)
        px[ox + size - 2, oy + i] = lerp(base[0], edge, 0.35)
    for (x, y) in ((0, 0), (size - 1, 0), (0, size - 1), (size - 1, size - 1)):
        px[ox + x, oy + y] = (0, 0, 0, 0)


def banner(px, ox, oy, w=64, h=18):
    """Ledger-red ribbon with gold trim and notched swallow-tail ends (3-slice: 10 | 44 tiled | 10)."""
    for y in range(h):
        for x in range(w):
            end = min(x, w - 1 - x)
            # swallow-tail notch: the outermost 4 px carve a V into the middle rows
            notch = end < 4 and abs(y - (h - 1) / 2) < (4 - end) * 0.9
            if notch:
                continue
            if y in (1, h - 2):
                col = BRASS[3] if (x // 2) % 2 == 0 else BRASS[2]
            elif y in (0, h - 1):
                col = BRASS[0]
            elif y == 2:
                col = RED[3]
            elif y == h - 3:
                col = RED[0]
            else:
                col = RED[2] if (x + y * 3) % 11 else RED[1]
                if (y - 3) % 4 == 0 and x % 2 == 0:
                    col = RED[1]  # woven texture
            if end == 0 or (end < 4 and abs(y - (h - 1) / 2) < (4 - end) * 0.9 + 1):
                col = OUTLINE if end == 0 or col in (BRASS[3], BRASS[2]) else RED[0]
            px[ox + x, oy + y] = col


def tab(px, ox, oy, selected, w=28):
    h = 27 if selected else 24
    for y in range(h):
        for x in range(w):
            d = min(x, w - 1 - x, y)
            if selected and y >= h - 3:
                d = min(x, w - 1 - x)  # open bottom: joins the panel frame
            if d == 0:
                col = OUTLINE
            elif d <= 3:
                col = _wood(px, x, y, "h" if y <= min(x, w - 1 - x) else "v", "tab")
                if d == 1:
                    col = WOOD_HI if selected else WOOD[2]
                if not selected:
                    col = lerp(col, c("#2A1A10"), 0.25)
            else:
                col = PARCH[2] if selected else c("#C8B994")
                if not selected and y >= h - 2:
                    col = c("#B8A884")
            px[ox + x, oy + y] = col
    px[ox, oy] = (0, 0, 0, 0)
    px[ox + w - 1, oy] = (0, 0, 0, 0)
    if not selected:
        for x in range(w):
            px[ox + x, oy + h - 1] = OUTLINE


def rep_bar(px, ox, oy, full):
    """Reputation bar -100..100 (102 x 7): hostile red, wary amber, neutral stone, friendly green, honored gold."""
    stops = [(0.0, c("#7A1F1A")), (0.24, c("#B23A2A")), (0.30, c("#C07A2E")), (0.45, c("#C9A040")),
             (0.47, c("#9A9488")), (0.54, c("#A8A296")), (0.56, c("#5E9A3E")), (0.74, c("#7CC05A")),
             (0.76, c("#D9B24A")), (1.0, c("#F6E3A0"))]

    def grad(t):
        for i in range(len(stops) - 1):
            a, ca = stops[i]
            b, cb = stops[i + 1]
            if t <= b:
                return lerp(ca, cb, 0 if b == a else (t - a) / (b - a))
        return stops[-1][1]

    for y in range(7):
        for x in range(102):
            if x in (0, 101) or y in (0, 6):
                col = OUTLINE if (x, y) not in ((0, 0), (101, 0), (0, 6), (101, 6)) else (0, 0, 0, 0)
            elif not full:
                col = c("#4A3A2A") if y == 1 else c("#2E241A")
            else:
                col = grad((x - 1) / 99)
                if y == 1:
                    col = lerp(col, c("#FFFFFF"), 0.35)
                elif y == 5:
                    col = lerp(col, c("#000000"), 0.25)
            px[ox + x, oy + y] = col
    if not full:  # tier ticks at -50, -10, 10, 50
        for t in (-50, -10, 10, 50):
            x = 1 + round((t + 100) / 200 * 99)
            for y in (1, 5):
                px[ox + x, oy + y] = c("#6A5A48")


def small_bar(px, ox, oy, fill):
    for y in range(5):
        for x in range(52):
            if x in (0, 51) or y in (0, 4):
                col = c("#5A4630")
            elif not fill:
                col = c("#C8B994") if y > 1 else c("#B8A884")
            else:
                col = BRASS[4] if y == 1 else (BRASS[3] if y == 2 else BRASS[2])
            px[ox + x, oy + y] = col
    for (x, y) in ((0, 0), (51, 0), (0, 4), (51, 4)):
        px[ox + x, oy + y] = (0, 0, 0, 0)


def scroll(px, ox, oy):
    # track 8 x 24, 9-slice border 2
    for y in range(24):
        for x in range(8):
            if x == 0 or y == 0:
                col = WOOD[1]
            elif x == 7 or y == 23:
                col = PARCH[3]
            else:
                col = c("#B8A884")
            px[ox + x, oy + y] = col
    # knobs 6 x 15: normal and hovered
    for k, pal in enumerate(([WOOD_LO, WOOD[1], WOOD[3], WOOD_HI], [BRASS[0], BRASS[1], BRASS[3], BRASS[4]])):
        kx = ox + 8 + k * 6
        for y in range(15):
            for x in range(6):
                if x == 0 or y == 0 or x == 5 or y == 14:
                    col = pal[0]
                elif x == 1 or y == 1:
                    col = pal[3]
                elif x == 4 or y == 13:
                    col = pal[1]
                else:
                    col = pal[2]
                px[kx + x, oy + y] = col
        for y in (5, 7, 9):
            px[kx + 2, oy + y] = pal[1]
            px[kx + 3, oy + y] = pal[1]


ICONS = {
    "check": [".........", "........g", ".......gG", "......gG.", "g....gG..", "Gg..gG...", ".GggG....", "..GG.....", "........."],
    "cross": [".........", ".r.....r.", "rRr...rRr", ".rRr.rRr.", "..rRrRr..", ".rRr.rRr.", "rRr...rRr", ".r.....r.", "........."],
    "seal": ["...ooo...", ".ooRRRoo.", ".oRrrrRo.", "oRrRRRrRo", "oRrRrRrRo", "oRrRRRrRo", ".oRrrrRo.", ".ooRRRoo.", "..o...o.."],
    "clock": ["..ooooo..", ".oPPPPPo.", "oPPPiPPPo", "oPPPiPPPo", "oPPPiiiPo", "oPPPPPPPo", "oPPPPPPPo", ".oPPPPPo.", "..ooooo.."],
    "scales": ["....b....", ".bbbbbbb.", ".b..b..b.", "b.b.b.b.b", "BBB.b.BBB", "....b....", "....b....", "..bbbbb..", "........."],
    "quill": [".......ww", "......wWw", ".....wWw.", "....wWw..", "...wWw...", "..wWw....", "..ww.....", ".i.......", "i........"],
    "star": ["....b....", "....b....", "...bBb...", "bbbBBBbbb", ".bBBBBBb.", "..bBBBb..", "..bB.Bb..", ".bb...bb.", "........."],
    "people": [".oo...oo.", "oPPo.oPPo", "oPPo.oPPo", ".oo...oo.", "oRRo.oBBo", "RRRRoBBBB", "RRRRoBBBB", "RRRR.BBBB", "........."],
}
ICON_PAL = {"g": GREEN, "G": GREEN_HI, "r": CROSS_RED, "R": CROSS_HI, "o": OUTLINE, "P": PARCH[3], "i": INK,
            "b": BRASS[1], "B": BRASS[3], "w": c("#E8E8E0"), "W": c("#B0B0A8")}


def icons(px, ox, oy):
    pal = dict(ICON_PAL)
    for k, (name, rows) in enumerate(ICONS.items()):
        local = dict(pal)
        if name == "seal":
            local.update({"o": RED[0], "R": RED[2], "r": RED[3]})
        if name == "people":
            local.update({"R": RED[2], "B": c("#3E5A8A")})
        for y, line in enumerate(rows):
            for x, ch in enumerate(line):
                if ch != ".":
                    px[ox + k * 10 + x, oy + y] = local[ch]


def marker(px, ox, oy):
    """Reputation pin 5 x 11: brass needle with an ink outline."""
    rows = [".ooo.", "oBBBo", "oBbBo", ".oBo.", ".oBo.", ".oBo.", ".oBo.", ".oBo.", ".oBo.", "oBBBo", ".ooo."]
    for y, line in enumerate(rows):
        for x, ch in enumerate(line):
            if ch != ".":
                px[ox + x, oy + y] = {"o": OUTLINE, "B": BRASS[3], "b": BRASS[4]}[ch]


def paint():
    img = new(256, 256)
    px = img.load()
    panel(px, 0, 0)
    inset(px, 48, 0)
    row(px, 72, 0, False)
    row(px, 96, 0, True)
    banner(px, 120, 0)
    tab(px, 184, 0, False)
    tab(px, 212, 0, True)
    rep_bar(px, 0, 48, False)
    rep_bar(px, 0, 56, True)
    small_bar(px, 0, 64, False)
    small_bar(px, 0, 70, True)
    scroll(px, 104, 48)
    icons(px, 128, 48)
    marker(px, 0, 76)
    save(img, SHEET)
