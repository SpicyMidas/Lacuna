"""Forgotten Portal textures (painted from m02_forgotten_land.paint()).

* Frame: dark weathered ruin stone, 13/16 tall like the End portal frame (its sides only show rows 3..15), faint
  violet-grey runes round the sides and the Totem of Banishing's open stone hand carved into the top. Two overlays
  hold just the runes and the sigil, lit pale violet: the active frame model draws them emissive over the stone.
* Portal: the surface is the lacuna:forgotten_portal core shader (screen-space layers like the End portal). It samples
  environment/forgotten_portal_sky (tiling grey-violet fog swept into soft swirls) and
  environment/forgotten_portal_motes (sparse soft pale dots on black, drawn as drifting parallax layers). The block
  texture is only its particle icon.
"""
import math

from texlib import c, lerp, new, noise_fill, ramp, rng, save

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
# The surface itself is drawn by the lacuna:forgotten_portal core shader (client/portal): screen-space layers of the
# two environment textures below, like the End portal, so the 2x2 opening reads as one continuous field. The block
# texture is only the particle icon (the portal's collapse shards), one still frame of the same fog.

FOG = [c("#100D17"), c("#1A1624"), c("#262033"), c("#342D45"), c("#463E5A"), c("#5A5170"), c("#716888"),
       c("#8C84A2"), c("#A9A2BE")]
BAYER = [[0, 2], [3, 1]]
TAU = math.tau
SKY = 128  # environment/forgotten_portal_sky: the fog base layer
MOTES = 256  # environment/forgotten_portal_motes: the parallax mote / star layers


def _periodic_noise(name, cell, size=16):
    """Value noise on the size x size torus, sampled anywhere with wrap-around smooth interpolation."""
    r = rng(name)
    n = size // cell
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


def _fog_tone(d):
    """Continuous grey-violet ramp through the fog tones (d in 0..1)."""
    v = max(0.0, min(1.0, d)) * (len(FOG) - 1)
    lo = min(len(FOG) - 2, int(v))
    return lerp(FOG[lo], FOG[lo + 1], v - lo)


def portal_particle():
    """The block's particle icon: a still 16x16 of dithered grey-violet fog with one pale mote."""
    big = _periodic_noise("forgotten_portal_fog_big", 4)
    small = _periodic_noise("forgotten_portal_fog_small", 2)
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = max(0.0, min(1.0, 0.7 * big(x, y) + 0.3 * small(x, y))) ** 1.2
            v = d * (len(FOG) - 2)
            lo = int(v)
            px[x, y] = FOG[lo + 1] if (v - lo) > (BAYER[y % 2][x % 2] + 0.5) / 4 else FOG[lo]
    px[11, 4] = c("#D9D3EE")
    px[4, 10] = c("#8C84A2")
    return img


def portal_sky():
    """Fog base layer: tiling grey-violet noise, swept into soft swirls by a periodic domain warp. Mostly deep
    violet-black with brighter grey drifts; the shader scales, rotates and drifts two copies of it."""
    size = SKY
    n1 = _periodic_noise("forgotten_portal_sky_large", 32, size)
    n2 = _periodic_noise("forgotten_portal_sky_mid", 16, size)
    n3 = _periodic_noise("forgotten_portal_sky_fine", 8, size)
    n4 = _periodic_noise("forgotten_portal_sky_grain", 4, size)
    turn = _periodic_noise("forgotten_portal_sky_turn", 32, size)
    reach = _periodic_noise("forgotten_portal_sky_reach", 64, size)
    grain = rng("forgotten_portal_sky_speck")
    img = new(size, size)
    px = img.load()
    for y in range(size):
        for x in range(size):
            # swirl: every point is pulled along a slowly turning direction field (periodic, so it still tiles)
            a = turn(x, y) * TAU * 1.6
            k = 6.0 + 10.0 * reach(x, y)
            wx, wy = x + math.cos(a) * k, y + math.sin(a) * k
            a2 = n2(wx, wy) * TAU
            wx2, wy2 = wx + math.cos(a2) * 4.0, wy + math.sin(a2) * 4.0
            d = 0.52 * n1(wx2, wy2) + 0.28 * n2(wx2 + 7, wy2 - 5) + 0.13 * n3(wx, wy) + 0.07 * n4(x, y)
            d = max(0.0, min(1.0, (d - 0.22) / 0.62)) ** 1.55
            col = _fog_tone(d * 0.92)
            j = grain.randint(-3, 3)
            px[x, y] = (max(0, min(255, col[0] + j)), max(0, min(255, col[1] + j)), max(0, min(255, col[2] + j + 1)), 255)
    return img


def portal_motes():
    """Parallax layers: sparse soft pale dots on black (the shader tints, scales and drifts each layer). Mostly tiny
    star points, some softer motes and a few faint glows; every dot wraps round the edges so the layer tiles."""
    size = MOTES
    r = rng("forgotten_portal_motes")
    acc = [[[0.0, 0.0, 0.0] for _ in range(size)] for _ in range(size)]
    tints = [(1.0, 0.98, 1.0), (0.93, 0.9, 1.0), (0.86, 0.82, 1.0), (1.0, 0.96, 0.94)]
    dots = []
    for _ in range(46):  # star points
        dots.append((r.random() * size, r.random() * size, 0.55 + r.random() * 0.35, 0.65 + r.random() * 0.35))
    for _ in range(16):  # soft motes
        dots.append((r.random() * size, r.random() * size, 1.3 + r.random() * 0.9, 0.45 + r.random() * 0.3))
    for _ in range(5):  # faint glows
        dots.append((r.random() * size, r.random() * size, 3.2 + r.random() * 1.6, 0.16 + r.random() * 0.08))
    for (cx, cy, sigma, bright) in dots:
        tint = tints[r.randrange(len(tints))]
        reach = int(math.ceil(sigma * 3.2))
        for oy in range(-reach, reach + 1):
            for ox in range(-reach, reach + 1):
                x, y = int(cx) + ox, int(cy) + oy
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                v = bright * math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma))
                if v < 0.004:
                    continue
                cell = acc[y % size][x % size]
                for k in range(3):
                    cell[k] += v * tint[k]
    img = new(size, size)
    px = img.load()
    for y in range(size):
        for x in range(size):
            cell = acc[y][x]
            px[x, y] = tuple(min(255, int(round(255 * cell[k]))) for k in range(3)) + (255,)
    return img


def paint():
    save(frame_side(), "block/forgotten_portal_frame_side")
    save(frame_side_glow(), "block/forgotten_portal_frame_side_glow")
    save(frame_top(), "block/forgotten_portal_frame_top")
    save(frame_top_glow(), "block/forgotten_portal_frame_top_glow")
    save(frame_bottom(), "block/forgotten_portal_frame_bottom")
    save(portal_particle(), "block/forgotten_portal")
    save(portal_sky(), "environment/forgotten_portal_sky")
    save(portal_motes(), "environment/forgotten_portal_motes")
