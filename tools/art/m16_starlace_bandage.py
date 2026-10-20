"""Starlace Bandage textures: the bandage roll item and the mummy wraps drawn over a mummified creature's model.

The wraps are re-drawn on the creature's own model with the creature's own UVs (client/mummy/MummyLayer), so they are
painted in model-UV space: rows of bandage strips, gently tilted, with seams and gaps that let the creature show
through. Box-UV cube faces keep "down the texture" as "down the cube", so horizontal strips in the texture become
bands wound round every cube of any vanilla model. One texture per common entity texture size keeps the strips on the
creature's own pixel grid; the humanoid variant (zombies, skeletons, piglins...) adds an eye slit, loose outer wraps
on the hat/jacket layer and starlit eyes. Each wrap has a matching glow texture (additive, full-bright): the faint
light of the cloth and its golden star threads.
"""
import math

from texlib import c, new, outline, rng, save

STAR = {"s": c("#5A6A8C"), "d": c("#8FA3C4"), "m": c("#B8C7E0"), "h": c("#E8F0FF"), "w": c("#FFFFFF")}
THREAD = [c("#E8A93A"), c("#FFD866"), c("#FFF0A8")]
GLINT = c("#C9B8FF")
OUTLINE = c("#3A4660")

# glow colours (added on top, full-bright): a faint blue light from the cloth, gold threads, lavender glints
GLOW_CLOTH = (30, 38, 58, 255)
GLOW_EDGE = (44, 54, 80, 255)
GLOW_THREAD = (255, 196, 92, 255)
GLOW_GLINT = (196, 176, 255, 255)
GLOW_EYE = (226, 214, 255, 255)

# the documented entity texture sizes; a 128x128 or 64x128 creature takes the 64x64 wraps, each wrap pixel then
# covering exactly 2x2 (or 1x2) of its own, still on its pixel grid
SIZES = [(32, 32), (64, 32), (64, 64), (128, 64)]
SPACING = 4  # one strip (highlight, body, shadow) and its seam, on average


def _bands(w, h, name):
    """Strip paths across the texture: each strip steps up or down a pixel now and then (never more than one off its
    row), so neighbouring strips sometimes overlap and sometimes part, like real wraps."""
    r = rng(name + "_bands")
    bands = []
    for i in range(-2, h // SPACING + 3):
        base = i * SPACING + r.choice((0, 0, 1))
        drift = r.choice((-1, 0, 0, 1))
        path = []
        for x in range(w):
            if r.random() < 1.0 / 7.0:
                # a one-pixel step now and then (never more than one off its row): the strip tilts a little
                drift += r.choice((-1, 1)) if drift == 0 else -drift
            path.append(base + drift)
        thick = 3 if r.random() < 0.9 else 2
        thread = r.random() < 0.3
        ends = {x for x in range(w) if r.random() < 1.0 / 26.0}
        bands.append((path, thick, thread, ends))
    return bands


def _paint(w, h, name, sparse=None):
    """The wrap pattern over a w x h texture; sparse(x, y) marks loose outer layers (fewer strips, more gaps)."""
    r = rng(name)
    img = new(w, h)
    glow = new(w, h)
    px = img.load()
    gx = glow.load()

    def put(x, y, col, gcol):
        if 0 <= x < w and 0 <= y < h:
            px[x, y] = col
            gx[x, y] = gcol if gcol else (0, 0, 0, 0)

    for n, (path, thick, thread, ends) in enumerate(_bands(w, h, name)):
        for x in range(w):
            top = path[x]
            loose = sparse is not None and sparse(x, min(h - 1, max(0, top)))
            if loose and n % 2 == 1:
                continue  # loose outer wraps: every other strip only
            # the seam: a dark line under the strip, where it sits on the one below
            if not loose or r.random() < 0.5:
                put(x, top + thick, STAR["s"], None)
            for k in range(thick):
                if k == 0:
                    col = STAR["w"] if r.random() < 0.05 else STAR["h"]
                elif k == thick - 1:
                    col = STAR["d"] if r.random() < 0.85 else STAR["m"]
                else:
                    col = STAR["m"] if r.random() < 0.88 else STAR["h"]
                g = GLOW_EDGE if k == 0 else GLOW_CLOTH
                if thread and k == 1 and thick == 3 and (x + n) % 3 == 0:
                    col = THREAD[1] if (x + n) % 6 == 0 else THREAD[0]
                    g = GLOW_THREAD
                elif k == 1 and r.random() < 0.015:
                    col = GLINT
                    g = GLOW_GLINT
                put(x, top + k, col, g)
            if x in ends:
                # the end of a wrap tucked over the next: a short fold with its shadow
                for k in range(thick):
                    put(x, top + k, STAR["w"] if k == 0 else STAR["h"], GLOW_EDGE)
                    if x + 1 < w:
                        put(x + 1, top + k, STAR["s"], None)
    return img, glow


def humanoid(name):
    """64x64 humanoid layout: hat and second-skin layers are loose outer wraps, and the face keeps an eye slit."""

    def sparse(x, y):
        if y < 16 and x >= 32:
            return True  # hat
        if 32 <= y < 48 and x < 56:
            return True  # right pants, jacket, right sleeve
        return y >= 48 and (x < 16 or x >= 48)  # left pants, left sleeve

    img, glow = _paint(64, 64, name, sparse)
    px = img.load()
    gx = glow.load()
    for x0 in (8, 40):  # head front, hat front
        for x in range(x0 + 1, x0 + 7):
            px[x, 12] = (0, 0, 0, 0)
            gx[x, 12] = (0, 0, 0, 0)
        if x0 == 8:
            # a clean wrap edge above and below the slit
            for x in range(x0 + 1, x0 + 7):
                px[x, 11] = STAR["s"]
                px[x, 13] = STAR["h"]
    for x in (9, 10, 13, 14):  # starlit eyes, glowing through the slit
        gx[x, 12] = GLOW_EYE
    return img, glow


# ------------------------------------------------------------------------------------------- item

# the loose end, unwinding from the bottom of the roll and running off to the right (row, first x, colours)
TAIL = [(10, 11, "hhhh"), (11, 9, "hhmmmmh"), (12, 7, "mmgmmmgmh"), (13, 8, "ddddddm")]


def bandage_item():
    """The bandage roll seen end-on: its wound layers in rings round a hollow core, a gold star thread wound among
    them, and its loose end unwinding from the bottom; outlined like vanilla items."""
    img = new(16, 16)
    px = img.load()
    cx, cy = 6, 7
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d > 5.3:
                continue
            lit = dx + dy <= 0
            if d >= 4.4:
                col = STAR["h"] if lit else STAR["m"]  # the outer layer's rim
            elif d >= 3.6:
                col = STAR["d"]  # the edge between two wound layers
            elif d >= 2.6:
                col = STAR["h"] if lit else STAR["m"]
            elif d >= 1.6:
                col = STAR["d"]
            elif d >= 0.8:
                col = STAR["m"]
            else:
                col = STAR["s"]  # the hollow core
            px[x, y] = col
    for y, x0, cols in TAIL:
        for i, k in enumerate(cols):
            px[x0 + i, y] = {"h": STAR["h"], "m": STAR["m"], "d": STAR["d"], "g": THREAD[1]}[k]
    for x, y in ((9, 4), (3, 9), (8, 10)):
        px[x, y] = THREAD[1]
    px[3, 4] = STAR["w"]
    px[10, 6] = GLINT
    return outline(img, OUTLINE)


def paint():
    save(bandage_item(), "item/starlace_bandage")
    for w, h in SIZES:
        wrap, glow = _paint(w, h, f"mummy_wrap_{w}x{h}")
        save(wrap, f"entity/mummy/wrap_{w}x{h}")
        save(glow, f"entity/mummy/glow_{w}x{h}")
    wrap, glow = humanoid("mummy_wrap_humanoid")
    save(wrap, "entity/mummy/wrap_humanoid_64x64")
    save(glow, "entity/mummy/glow_humanoid_64x64")
    save(wrap.crop((0, 0, 64, 32)), "entity/mummy/wrap_humanoid_64x32")
    save(glow.crop((0, 0, 64, 32)), "entity/mummy/glow_humanoid_64x32")
