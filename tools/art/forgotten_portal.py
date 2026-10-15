"""Forgotten Portal textures (painted from m02_forgotten_land.paint()).

* Frame: dark weathered ruin stone, 13/16 tall like the End portal frame (its sides only show rows 3..15), faint
  violet-grey runes round the sides and the Totem of Banishing's open stone hand carved into the top. Two overlays
  hold just the runes and the sigil, lit pale violet: the active frame model draws them emissive over the stone.
* Portal: a slow swirl of grey-violet fog with faint pale motes drifting through it. Every frame tiles (the 2x2
  opening reads as one surface) and the animation loops exactly: all motion is periodic over the loop.
"""
import json
import math
import os

from texlib import TEXTURES, c, lerp, new, noise_fill, ramp, rng, save

STONE = ramp("#2A2D35", "#31353E", "#393D48", "#424753", "#4C525F")
GROOVE = c("#1B1D23")
SHADOW = c("#24272E")
EDGE = c("#5C6372")
LICHEN = c("#4F5A4E")
RUNE = [c("#575168"), c("#655E78")]  # faint violet-grey, barely lighter than the stone
LIT = [c("#9E8AE6"), c("#C9B9FF"), c("#EDE6FF"), c("#FFFFFF")]  # lit runes, dim edge to white core

# three 3x5 runes cut into each side of the frame, between the lip and the plinth
GLYPHS = [
    ["#.#", "#.#", ".#.", ".#.", ".#."],
    [".#.", "#.#", ".#.", ".#.", ".#."],
    ["#..", "##.", "#.#", "##.", "#.."],
]

# the Totem of Banishing's open hand (four fingers, thumb out to the left), carved into the frame's top
HAND = [
    "................",
    "................",
    "........h.h.....",
    "......h.h.h.h...",
    "......h.h.h.h...",
    "......h.h.h.h...",
    "...h..hhhhhhh...",
    "....h.hhhhhhh...",
    "....hhhhhhhhh...",
    ".....hhhhhhhh...",
    ".....hhhhhhhh...",
    "......hhhhhh....",
    ".......hhhh.....",
    ".......hhhh.....",
    "................",
    "................",
]
PALM_GEM = (9, 9)  # where the totem's ruby sits: the brightest point of the lit sigil
# tiny notches round the hand: a broken ring of runes that lights up with it
NOTCHES = [(2, 3), (3, 2), (13, 3), (12, 2), (2, 12), (3, 13), (13, 12), (12, 13), (2, 8), (13, 8)]


def _weathered(name):
    """Dark ruin stone with a few lichen specks and hairline cracks."""
    img = noise_fill(STONE, name, cell=4, speckle=0.16, contrast=1.15)
    px = img.load()
    r = rng(name + "_weather")
    for _ in range(3):
        px[r.randint(0, 15), r.randint(0, 15)] = LICHEN
    for _ in range(2):
        x, y = r.randint(1, 14), r.randint(1, 10)
        for _ in range(r.randint(2, 4)):
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = SHADOW
            x += r.choice((-1, 0, 1))
            y += 1
    return img


def _carve(img, mask, fill):
    """Cuts mask pixels into the stone: dark recess, lit lip on the lower-right edges (light from the top-left)."""
    px = img.load()
    for (x, y), col in mask.items():
        px[x, y] = fill(x, y, col)
    for (x, y) in mask:
        for dx, dy in ((1, 0), (0, 1), (1, 1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in mask:
                px[nx, ny] = lerp(px[nx, ny], EDGE, 0.45)


def _side_runes():
    """Rune pixels of the side texture: three glyphs on the band between the lip and the plinth."""
    pts = {}
    for glyph, gx in zip(GLYPHS, (2, 7, 12)):
        for dy, row in enumerate(glyph):
            for dx, ch in enumerate(row):
                if ch == "#":
                    pts[(gx + dx - 1, 6 + dy)] = None
    return pts


def frame_side():
    img = _weathered("forgotten_portal_frame_side")
    px = img.load()
    for x in range(16):
        px[x, 3] = lerp(px[x, 3], EDGE, 0.55)  # the lip under the top face
        px[x, 4] = lerp(px[x, 4], GROOVE, 0.55)
        px[x, 12] = GROOVE  # plinth joint
        px[x, 13] = lerp(px[x, 13], EDGE, 0.25)
        px[x, 15] = lerp(px[x, 15], GROOVE, 0.35)
    for y in range(13, 16):
        px[7, y] = GROOVE  # plinth blocks
        px[15, y] = lerp(px[15, y], GROOVE, 0.6)
    for y in range(5, 12):
        px[0, y] = lerp(px[0, y], SHADOW, 0.5)
    runes = _side_runes()
    _carve(img, runes, lambda x, y, _: RUNE[(x + y) % 2])
    return img


def frame_side_glow():
    img = new()
    px = img.load()
    for (x, y) in _side_runes():
        px[x, y] = LIT[1] if (x + y) % 3 else LIT[2]
    return img


def _hand():
    return {(x, y): None for y, row in enumerate(HAND) for x, ch in enumerate(row) if ch == "h"}


def frame_top():
    img = _weathered("forgotten_portal_frame_top")
    px = img.load()
    # raised rim: dark outer edge, lit bevel on the top-left, shadow on the bottom-right
    for i in range(16):
        px[i, 0] = GROOVE
        px[0, i] = GROOVE
        px[i, 15] = GROOVE
        px[15, i] = GROOVE
    for i in range(1, 15):
        px[i, 1] = lerp(px[i, 1], EDGE, 0.5)
        px[1, i] = lerp(px[1, i], EDGE, 0.5)
        px[i, 14] = lerp(px[i, 14], GROOVE, 0.5)
        px[14, i] = lerp(px[14, i], GROOVE, 0.5)
    hand = _hand()

    def recess(x, y, _):
        edge = any((x + dx, y + dy) not in hand for dx, dy in ((-1, 0), (0, -1)))
        return SHADOW if edge else GROOVE

    _carve(img, hand, recess)
    gx, gy = PALM_GEM
    px[gx, gy] = RUNE[1]
    _carve(img, {p: None for p in NOTCHES}, lambda x, y, _: RUNE[0])
    return img


def frame_top_glow():
    img = new()
    px = img.load()
    hand = _hand()
    for (x, y) in hand:
        inner = all((x + dx, y + dy) in hand for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        d = math.hypot(x - PALM_GEM[0], y - PALM_GEM[1])
        px[x, y] = LIT[2] if d < 1.1 else LIT[1] if inner or y < 6 else LIT[0]
    px[PALM_GEM[0], PALM_GEM[1]] = LIT[3]
    for (x, y) in NOTCHES:
        px[x, y] = LIT[1]
    return img


def frame_bottom():
    return _weathered("forgotten_portal_frame_bottom")


# --------------------------------------------------------------------------------------------- portal

FRAMES = 64
FOG = [c("#100D17"), c("#1A1624"), c("#262033"), c("#342D45"), c("#463E5A"), c("#5A5170"), c("#716888"),
       c("#8C84A2"), c("#A9A2BE")]
MOTE = (236, 232, 248)
BAYER = [[0, 2], [3, 1]]
TAU = math.tau


def _periodic_noise(name, cell):
    """Value noise on the 16x16 torus, sampled anywhere with wrap-around smooth interpolation."""
    r = rng(name)
    n = 16 // cell
    grid = [[r.random() for _ in range(n)] for _ in range(n)]

    def sample(x, y):
        fx, fy = (x / cell) % n, (y / cell) % n
        tx, ty = fx - math.floor(fx), fy - math.floor(fy)
        x0, y0 = int(fx) % n, int(fy) % n  # fmod can round up to exactly n
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
        b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
        return a * (1 - ty) + b * ty
    return sample


def _eddies(x, y, t):
    """Two counter-rotating spiral eddies per tile, one on the block centre and one on its corners (so a 2x2 portal
    turns round its middle too). Each is windowed to its own cell, which keeps the tile seamless; one turn per loop."""
    s = 0.0
    for k in range(2):
        o = 8.0 * k
        dx = ((x + 0.5 - o) / 8.0 + 1.0) % 2.0 - 1.0
        dy = ((y + 0.5 - o) / 8.0 + 1.0) % 2.0 - 1.0
        d2 = dx * dx + dy * dy
        window = math.sqrt((1.0 - dx * dx) * (1.0 - dy * dy))
        sign = 1 if k else -1
        ang = math.atan2(dy, dx) + (TAU * t - d2 * 6.0 + 2 * k) * sign
        s += window * (math.sin(ang) + 1.0) / 2.0 / (2.0 * d2 + 1.0)
    return s


def _fog_density(big, small, x, y, t):
    """Mostly dark void: the eddies gather the fog, periodic noise drifting one tile per loop breaks it into wisps."""
    d = 0.65 * _eddies(x, y, t) + 0.35 * (0.7 * big(x + 16 * t, y - 16 * t) + 0.3 * small(x - 16 * t, y))
    return max(0.0, min(1.0, d)) ** 1.35


def _motes():
    """(x0, y0, vx, vy, phase, cycles): each mote drifts while it fades in and out, `cycles` times per loop."""
    r = rng("forgotten_portal_motes")
    out = []
    for i in range(7):
        ang = r.random() * TAU
        speed = 2.5 + r.random() * 3.0
        out.append((r.random() * 16, r.random() * 16, math.cos(ang) * speed, math.sin(ang) * speed - 1.5,
                    i / 7.0, 1 + (i % 2)))
    return out


def portal():
    big = _periodic_noise("forgotten_portal_fog_big", 4)
    small = _periodic_noise("forgotten_portal_fog_small", 2)
    motes = _motes()
    sheet = new(16, 16 * FRAMES)
    px = sheet.load()
    for f in range(FRAMES):
        t = f / FRAMES
        layer = [[[0.0, 0.0, 0.0] for _ in range(16)] for _ in range(16)]
        for (x0, y0, vx, vy, phase, cycles) in motes:
            local = (t * cycles + phase) % 1.0
            glow = math.sin(math.pi * local) ** 2 * 0.65
            mx, my = (x0 + vx * local) % 16, (y0 + vy * local) % 16
            ix, iy = int(mx), int(my)
            fx, fy = mx - ix, my - iy
            for ox, oy, w in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)), (0, 1, (1 - fx) * fy), (1, 1, fx * fy)):
                cell = layer[(iy + oy) % 16][(ix + ox) % 16]
                for k in range(3):
                    cell[k] += MOTE[k] * glow * w
        for y in range(16):
            for x in range(16):
                d = _fog_density(big, small, x, y, t)
                # a fixed ordered dither between neighbouring fog tones keeps the pixel-art look while it flows
                v = d * (len(FOG) - 1)
                lo = int(v)
                hi = min(len(FOG) - 1, lo + 1)
                col = FOG[hi] if (v - lo) > (BAYER[y % 2][x % 2] + 0.5) / 4 else FOG[lo]
                add = layer[y][x]
                rgb = [min(255, int(col[k] + add[k])) for k in range(3)]
                alpha = int(round(212 + 36 * d + min(7.0, sum(add) / 30)))
                px[x, y + 16 * f] = (rgb[0], rgb[1], rgb[2], min(255, alpha))
    return sheet


def save_animated(img, rel, frametime):
    save(img, rel)
    with open(os.path.join(TEXTURES, rel + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"animation": {"frametime": frametime}}, fh, indent=2, sort_keys=True)
        fh.write("\n")


def paint():
    save(frame_side(), "block/forgotten_portal_frame_side")
    save(frame_side_glow(), "block/forgotten_portal_frame_side_glow")
    save(frame_top(), "block/forgotten_portal_frame_top")
    save(frame_top_glow(), "block/forgotten_portal_frame_top_glow")
    save(frame_bottom(), "block/forgotten_portal_frame_bottom")
    save_animated(portal(), "block/forgotten_portal", 2)
