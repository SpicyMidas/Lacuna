"""Ember Triangle effects: the pulverization dust (8x8 particle sprites) and the burning ground glyph (64x64 mask).

The grains, flakes and specks are drawn in pale, near-neutral tones on purpose: the particle (client/gear/EmberDust)
tints each one - charcoal, dark ash, ash grey or ember orange - and cools it from orange to grey over its life, so
the pixel shading here (lit top-left facets, shadowed bottom-right, cracks, translucent crumbling edges) survives
every tint. The specks have a white-hot core and a soft halo so they read as glowing embers while hot.
"""
import math

from texlib import from_ascii, new, save

# lit facet, light, mid, shadow, crack, translucent crumbling edge, faint loose dust
DUST = {
    "W": (252, 250, 246, 255), "L": (226, 222, 216, 255), "M": (192, 188, 182, 255), "D": (150, 146, 140, 255),
    "k": (116, 112, 108, 255), "d": (190, 186, 180, 140), "s": (212, 208, 202, 90),
}
# white-hot core, hot, glow, halo, faint halo
EMBER = {
    "H": (255, 255, 255, 255), "Y": (255, 246, 222, 255), "O": (250, 228, 194, 230), "o": (242, 212, 172, 130),
    "g": (232, 200, 160, 60),
}

GRAINS = [
    [  # chunky grain with a chipped corner
        "........",
        "........",
        "...LW...",
        "..LWLMd.",
        "..MLMDk.",
        "..dMDk..",
        "...d....",
        "........"],
    [  # thin flake lying on the diagonal
        "........",
        "......d.",
        "....dLW.",
        "...LWMd.",
        "..LMDd..",
        ".dMDd...",
        ".dd.....",
        "........"],
    [  # a pinch of scattered grains
        "........",
        ".s...W..",
        ".....Ls.",
        "..LM....",
        "..MD..s.",
        ".....d..",
        ".sW.....",
        "........"],
    [  # broad charred flake with a crack through it
        "........",
        "..dLLd..",
        ".dLWLMd.",
        ".LWLkMD.",
        ".dMLMkD.",
        "..dMDDd.",
        "...dd...",
        "........"],
    [  # curled flake
        "........",
        "...dLd..",
        "..LWMd..",
        ".dWM....",
        ".dLM....",
        "..MDd...",
        "...dDd..",
        "........"],
    [  # soft puff of fine powder
        "........",
        "...s....",
        "..sdMs..",
        ".sdLWds.",
        "..dMLd..",
        "..sdds..",
        "....s...",
        "........"],
]

SPECKS = [
    [  # round ember
        "........",
        "...gg...",
        "..goog..",
        ".goYHog.",
        ".goHYog.",
        "..goog..",
        "...gg...",
        "........"],
    [  # tiny spark
        "........",
        "........",
        "...go...",
        "..oHYg..",
        "..gYo...",
        "...g....",
        "........",
        "........"],
    [  # streaking spark
        "........",
        "......g.",
        ".....oo.",
        "....oYg.",
        "...oHo..",
        "..gYo...",
        ".go.....",
        "........"],
]


def dust():
    for i, rows in enumerate(GRAINS):
        save(from_ascii(rows, DUST), f"particle/ember_dust_{i}")
    for i, rows in enumerate(SPECKS):
        save(from_ascii(rows, EMBER), f"particle/ember_speck_{i}")


def _segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def _line(d, core, glow):
    """1 inside the core of a stroke, then a soft quadratic glow falling to 0 over `glow` pixels."""
    if d <= core:
        return 1.0
    if d >= core + glow:
        return 0.0
    t = 1.0 - (d - core) / glow
    return t * t * 0.75


def glyph():
    """The Ember Triangle seal (apex at the top = the caster's facing): a burning triangle with flaring corners,
    an inverted inner triangle, a seal ring with nine notches and an ember at the heart. White; alpha is the shape."""
    size, c = 64, 32.0
    thirds = (0.0, 2.0 * math.pi / 3.0, 4.0 * math.pi / 3.0)
    outer = [(c + 26.0 * math.sin(a), c - 26.0 * math.cos(a)) for a in thirds]
    inner = [(c - 10.5 * math.sin(a), c + 10.5 * math.cos(a)) for a in thirds]
    notches = []
    for k in range(9):
        a = math.radians(20.0 + 40.0 * k)
        notches.append((c + 27.2 * math.sin(a), c - 27.2 * math.cos(a), c + 30.6 * math.sin(a), c - 30.6 * math.cos(a)))
    img = new(size, size)
    px = img.load()
    for y in range(size):
        for x in range(size):
            fx, fy = x + 0.5, y + 0.5
            r = math.hypot(fx - c, fy - c)
            d_out = min(_segment(fx, fy, *outer[i], *outer[(i + 1) % 3]) for i in range(3))
            d_in = min(_segment(fx, fy, *inner[i], *inner[(i + 1) % 3]) for i in range(3))
            d_corner = min(math.hypot(fx - vx, fy - vy) for vx, vy in outer)
            d_notch = min(_segment(fx, fy, *n) for n in notches)
            a = _line(d_out, 1.3, 4.0)
            a = max(a, _line(d_corner, 2.0, 3.5))
            a = max(a, 0.7 * _line(d_in, 0.6, 2.2))
            a = max(a, 0.5 * _line(abs(r - 29.0), 0.5, 1.8))
            a = max(a, 0.6 * _line(d_notch, 0.45, 1.2))
            a = max(a, 0.9 * _line(r, 1.4, 3.0))
            if a > 0.0:
                px[x, y] = (255, 255, 255, int(round(255 * min(1.0, a))))
    save(img, "effect/ember_glyph")


def paint():
    dust()
    glyph()
