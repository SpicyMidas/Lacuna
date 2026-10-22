"""Starcaller Trumpet textures: the golden trumpet with a star-shaped bell, the Star Fragment, the three Star Rock stages
a falling star leaves (molten, prime with its full-bright glints overlay, extinguished) and the star flare mask used by
the falling star's light.

Gold follows the vanilla gold/bell ramp; outlines are a dark warm brown like vanilla gold items.
"""
import json
import math
import os

from texlib import TEXTURES, c, from_ascii, new, noise_fill, outline, ramp_pick, rng, save, value_noise

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


# ------------------------------------------------------------------ Star Rock: the three cooling stages (star/StarRock)
def _save_animated(img, rel, frametime, interpolate=False):
    save(img, rel)
    anim = {"frametime": frametime}
    if interpolate:
        anim["interpolate"] = True
    with open(os.path.join(TEXTURES, rel + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"animation": anim}, fh, indent=2, sort_keys=True)
        fh.write("\n")


def _wrap_d(a, b):
    d = abs(a - b) % 16
    return min(d, 16 - d)


CRUST = [c("#140C0A"), c("#1E120E"), c("#2A1811"), c("#372015")]
EMBER = [c("#4E1A0B"), c("#7A280C")]
MOLTEN = [c("#8E2208"), c("#C23E0E"), c("#E8661A"), c("#FF922C"), c("#FFBC42"), c("#FFE07A"), c("#FFF6C8")]
MOLTEN_FRAMES = 10


def molten_star_rock():
    """Molten Star Rock: dark charred crust plates split by flowing orange-gold cracks, white-hot star specks
    twinkling on it. A 10-frame strip (interpolated): the heat pulses along the cracks, so the molten veins seem to flow.
    Tiles seamlessly (wrapped Voronoi plates, tileable noise)."""
    r = rng("molten_star_rock")
    seeds = [(r.uniform(0, 16), r.uniform(0, 16)) for _ in range(6)]
    width = value_noise(16, 16, rng("molten_star_rock_width"), cell=8, octaves=1)
    phase = value_noise(16, 16, rng("molten_star_rock_phase"), cell=8, octaves=2)
    crust = value_noise(16, 16, rng("molten_star_rock_crust"), cell=4, octaves=2)
    border = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            ds = sorted(math.hypot(_wrap_d(x + 0.5, sx), _wrap_d(y + 0.5, sy)) for sx, sy in seeds)
            border[y][x] = ds[1] - ds[0]
    # white-hot specks: a few in the widest cracks, a few set in the crust
    specks = []
    for _ in range(4):
        specks.append((r.randint(0, 15), r.randint(0, 15), r.randint(0, MOLTEN_FRAMES - 1)))
    strip = new(16, 16 * MOLTEN_FRAMES)
    px = strip.load()
    for f in range(MOLTEN_FRAMES):
        t = f / MOLTEN_FRAMES
        for y in range(16):
            for x in range(16):
                b = border[y][x]
                w = 0.6 + width[y][x] * 0.9
                wave = math.sin((t + phase[y][x] * 1.6) * 2 * math.pi)
                if b < w:
                    heat = 0.3 + (1.0 - b / w) * 0.45 + wave * 0.18
                    col = ramp_pick(MOLTEN, heat)
                elif b < w + 0.9:
                    col = EMBER[1] if wave > 0.35 else EMBER[0]
                else:
                    v = crust[y][x] * 1.25 - 0.1 + (0.12 if (x * 7 + y * 3) % 11 == 0 else 0.0)
                    col = ramp_pick(CRUST, v)
                px[x, y + 16 * f] = col
        for i, (sx, sy, k) in enumerate(specks):
            lit = (f - k) % MOLTEN_FRAMES
            if lit < 3:
                px[sx, sy + 16 * f] = WHITE if lit == 1 else MOLTEN[5]
                if lit == 1 and i % 2 == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        px[(sx + dx) % 16, (sy + dy) % 16 + 16 * f] = MOLTEN[4]
    _save_animated(strip, "block/molten_star_rock", 5, interpolate=True)


STONE = [c("#16131D"), c("#201C28"), c("#2A2533"), c("#352F40"), c("#423A4E")]
SOCKET = c("#0E0C13")
CRYSTAL = {"W": WHITE, "h": c("#FFF6D6"), "l": c("#FFE08A"), "g": c("#F2B84A"), "d": c("#B77A2A"),
           "s": c("#C9B8FF"), "S": c("#8E7BD6")}
# little star crystals set in the rock (rows of palette letters, "." = rock)
CRYSTALS = [
    ([".lW", "sgl", "dg."], (2, 2)),
    (["lh", "gs"], (11, 1)),
    ([".h.", "lWg", ".d."], (9, 7)),
    (["sl.", ".gW", "..g"], (3, 10)),
    (["Wl", "dS"], (12, 12)),
]
GLITTER = [(7, 1), (14, 6), (1, 7), (6, 6), (14, 9), (7, 13), (0, 14), (10, 4)]
PRIME_FRAMES = 6


def _crystal_pixels():
    out = []
    for rows, (ox, oy) in CRYSTALS:
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    out.append(((ox + x) % 16, (oy + y) % 16, ch))
    return out


def prime_star_rock():
    """Prime Star Rock: dark violet-grey star-stone studded with bright star crystals in dark sockets and fine glitter.
    The block model lays prime_star_rock_glints (crystals + glitter only) over it drawn full bright, so at night the
    crystals glow while the stone stays dark; the glints strip twinkles (6 frames, interpolated)."""
    img = noise_fill(STONE, "prime_star_rock", cell=4, speckle=0.16, contrast=1.15)
    px = img.load()
    crystal = _crystal_pixels()
    filled = {(x, y) for x, y, _ in crystal}
    for x, y, _ in crystal:
        for dx, dy in ((1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
            p = ((x + dx) % 16, (y + dy) % 16)
            if p not in filled:
                px[p] = SOCKET if (dx, dy) in ((1, 0), (0, 1), (1, 1)) else STONE[4]
    for x, y, ch in crystal:
        px[x, y] = CRYSTAL[ch]
    for i, (x, y) in enumerate(GLITTER):
        px[x, y] = CRYSTAL["S"] if i % 2 else STONE[4]
    save(img, "block/prime_star_rock")

    strip = new(16, 16 * PRIME_FRAMES)
    sp = strip.load()
    order = [CRYSTALS[i][1] for i in range(len(CRYSTALS))]
    for f in range(PRIME_FRAMES):
        for x, y, ch in crystal:
            sp[x, y + 16 * f] = CRYSTAL[ch]
        for i, (x, y) in enumerate(GLITTER):
            if (i + f) % 3 == 0:
                sp[x, y + 16 * f] = CRYSTAL["s"] if i % 2 else CRYSTAL["h"]
        # one crystal at a time flares into a small four-point twinkle
        ox, oy = order[f % len(order)]
        cx, cy = (ox + 1) % 16, (oy + 1) % 16
        if f < len(order):
            sp[cx, cy + 16 * f] = WHITE
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = ((cx + dx) % 16, (cy + dy) % 16)
                if q not in filled:
                    sp[q[0], q[1] + 16 * f] = CRYSTAL["h"]
    # the glints overlay is gone: the hand-drawn animated prime_star_rock replaces the whole look


MOON = [c("#46464B"), c("#55555A"), c("#646468"), c("#737376"), c("#838385"), c("#949495"), c("#A6A6A7")]
CRATERS = [(5.0, 5.0, 3.2), (11.5, 11.5, 2.4), (12.5, 3.5, 1.4), (3.5, 12.5, 1.2), (8.5, 9.0, 0.9)]


def extinguished_star_rock():
    """Extinguished Block of the Star: grey, dusty moon rock pocked with craters, lit from the top left (bright rims
    and inner lower-right walls, shadowed upper-left walls), with a few dull lavender flecks left of its crystals.
    Tiles seamlessly."""
    n = value_noise(16, 16, rng("extinguished_star_rock"), cell=4, octaves=2)
    r = rng("extinguished_star_rock_dust")
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 0.5 + (n[y][x] - 0.5) * 0.55
            if r.random() < 0.14:
                v += r.choice((-0.14, 0.14))
            for cx, cy, rad in CRATERS:
                dx = ((x + 0.5 - cx + 8) % 16) - 8
                dy = ((y + 0.5 - cy + 8) % 16) - 8
                d = math.hypot(dx, dy)
                lean = (dx + dy) / max(d, 0.01)
                if d <= rad - 0.35:
                    # the bowl: its upper-left wall in shadow, its lower-right wall catching the light
                    v -= 0.12
                    if d > rad - 1.25:
                        v += 0.34 * lean
                elif d <= rad + 0.7:
                    # the raised rim: lit on the upper left, shadowed on the lower right
                    v -= 0.26 * lean
            px[x, y] = ramp_pick(MOON, v)
    for x, y in ((7, 2), (14, 8), (1, 6), (9, 14)):
        px[x, y] = c("#6F6A7A")
    for _ in range(5):
        px[r.randint(0, 15), r.randint(0, 15)] = c("#A8A8A8")
    save(img, "block/extinguished_star_rock")


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
    molten_star_rock()
    prime_star_rock()
    extinguished_star_rock()
    star_flare()
