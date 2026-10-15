"""Wand of Quickening: a palette swap of the hand-drawn Pyrrhic Scepter, and the Animated Item's twig legs.

The wand keeps every pixel of tools/art/hand/item/pyrrhic_scepter.png where it is (the hand-drawn file is only
read, never modified): the orange shaft becomes living emerald wood, the red gem a pink-magenta heart, the gold
fittings pale bone. Each colour maps to one with the same place in its light/dark ramp.
"""
import os

from PIL import Image

from texlib import c, new, rng, save

HAND_SCEPTER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hand", "item", "pyrrhic_scepter.png")

# original colour -> new colour, ramp by ramp (darkest first)
WAND_PALETTE = {
    # shaft: orange -> emerald green
    "#A8400A": "#17663F",
    "#D45F00": "#239A57",
    "#FF7A00": "#3CCB72",
    "#FFB000": "#8EF0A6",
    # gem: crimson -> pink-magenta heart
    "#8E0C2A": "#760A5C",
    "#B0102F": "#9E0E78",
    "#D6143F": "#C8148F",
    "#F03A58": "#EE34AE",
    "#FF5C7A": "#FF5EC8",
    "#FF9AAA": "#FF9DDF",
    "#FFC2CC": "#FFD2F2",
    # fittings: gold -> pale bone
    "#B07A18": "#9C8F6E",
    "#F2C94C": "#DCD3B4",
    "#FFE48A": "#F7F2E1",
}


def quickening_wand():
    src = Image.open(HAND_SCEPTER).convert("RGBA")
    mapping = {c(k)[:3]: c(v)[:3] for k, v in WAND_PALETTE.items()}
    out = new(src.width, src.height)
    spx, opx = src.load(), out.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = spx[x, y]
            if a == 0:
                continue
            if (r, g, b) not in mapping:
                raise KeyError(f"pyrrhic_scepter.png colour #{r:02X}{g:02X}{b:02X} at {x},{y} has no quickening mapping")
            nr, ng, nb = mapping[(r, g, b)]
            opx[x, y] = (nr, ng, nb, a)
    save(out, "item/quickening_wand")


# ---------------------------------------------------------------------------------------------- legs
# Box UV nets (vanilla CubeListBuilder layout) used by client/gear/AnimatedItemRenderer:
#   thigh 1x3x1 at (0,0), shin 1x3x1 at (4,0), knee knot 1x1x1 at (8,0), foot 2x1x3 at (0,4).
# The model is built y-up, so every side face shows its texture upside down: row 0 is the bottom of the limb.
BARK = [c("#3B2414"), c("#55351D"), c("#6F4727"), c("#8C6036")]
MOSS = [c("#2C6B3A"), c("#3E8F4A"), c("#62B865"), c("#9BE58E")]
GLOW = c("#8CFFAE")


def _net(u, v, w, h, d):
    return {"bottom": (u + d, v, w, d), "top": (u + d + w, v, w, d),
            "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "east": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h)}


def _fill(px, rect, col):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = col


def _limb(px, r, u, v, h, dark_end):
    """A 1x h x1 twig: bark ramp along its length, a light edge on two faces, knots of moss."""
    for face, (x0, y0, w, fh) in _net(u, v, 1, h, 1).items():
        for y in range(y0, y0 + fh):
            for x in range(x0, x0 + w):
                if face in ("top", "bottom"):
                    px[x, y] = BARK[1]
                    continue
                t = (y - y0) / max(1, fh - 1)          # 0 = bottom of the limb (model is y-up)
                if dark_end != "bottom":
                    t = 1.0 - t
                idx = 1 + int(round(t * 2))
                if face in ("north", "east"):
                    idx = min(3, idx + 1)
                elif face == "south":
                    idx = max(0, idx - 1)
                px[x, y] = BARK[max(0, min(3, idx + (1 if r.random() < 0.15 else 0)))]


def animated_item_legs():
    img = new(16, 16)
    glow = new(16, 16)
    px, gx = img.load(), glow.load()
    r = rng("animated_item_legs")
    _limb(px, r, 0, 0, 3, "bottom")   # thigh
    _limb(px, r, 4, 0, 3, "top")      # shin
    # a living green vein down the front of each limb (and its glow)
    px[1, 2] = MOSS[2]
    px[5, 1] = MOSS[1]
    px[5, 2] = MOSS[2]
    gx[1, 2] = (GLOW[0], GLOW[1], GLOW[2], 150)
    gx[5, 2] = (GLOW[0], GLOW[1], GLOW[2], 170)
    # knee knot: mossy bud with a bright core
    for face, rect in _net(8, 0, 1, 1, 1).items():
        _fill(px, rect, MOSS[1] if face in ("west", "south", "bottom") else MOSS[2])
        _fill(gx, rect, (GLOW[0], GLOW[1], GLOW[2], 210 if face in ("north", "top") else 140))
    px[9, 0] = MOSS[3]
    # feet: dark wood soles, lighter toe caps, one moss toe
    for face, rect in _net(0, 4, 2, 1, 3).items():
        col = {"bottom": BARK[0], "top": BARK[2], "north": BARK[3], "south": BARK[1]}.get(face, BARK[2])
        _fill(px, rect, col)
    px[3, 5] = MOSS[2]
    px[4, 6] = BARK[3]
    save(img, "entity/animated_item_legs")
    save(glow, "entity/animated_item_legs_glow")


def paint():
    quickening_wand()
    animated_item_legs()
