"""Lacunar Quintessence: a drop of iridescent violet light with a dark gap at its heart (the "lacuna"), animated.

Eight 16x16 frames (frametime 3): two arms of teal and rose light swirl around the dark core, the halo around the
gap breathes, and a four-point sparkle blinks on either side of the drop. The silhouette, the outline and the
highlight stay put in every frame, so in the inventory it reads as one steady object with moving light inside.
"""
import json
import math
import os

from texlib import TEXTURES, c, new, save

FRAMES = 8
FRAMETIME = 3

# x ranges (inclusive) of the drop per row: a pointed tip at the top, a round belly below
ROWS = {
    1: (8, 8), 2: (7, 8), 3: (7, 9), 4: (6, 9), 5: (6, 10), 6: (5, 10), 7: (4, 11), 8: (4, 11),
    9: (3, 12), 10: (3, 12), 11: (3, 12), 12: (3, 12), 13: (4, 11), 14: (5, 10),
}
CORE = (7.5, 10.5)          # the gap at the heart (a 2x2 hole)
OUTLINE = c("#1A0A2E")
BODY = [c("#2E1152"), c("#45207A"), c("#6431A6"), c("#8C52D4"), c("#B884F5")]   # dark -> light
TEAL = [c("#2FA8A8"), c("#5FE3D4"), c("#B4FFF4")]
ROSE = [c("#B83C8F"), c("#F070C2"), c("#FFC2E8")]
HALO = [c("#D9B8FF"), c("#F4E8FF"), c("#FFFFFF")]
GAP = c("#08030F")
SPARK = [c("#FFF6C9"), c("#FFFFFF")]


def inside(x, y):
    r = ROWS.get(y)
    return r is not None and r[0] <= x <= r[1]


def edge(x, y):
    return inside(x, y) and not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def body_shade(x, y):
    """Lit from the upper left: lighter toward the top-left rim, darker toward the bottom-right."""
    t = ((x - 3) * 0.55 + (y - 1) * 0.75) / 14.0
    i = 4 - int(min(4, max(0, t * 5.2)))
    return BODY[max(0, min(4, i))]


def frame(f):
    img = new(16, 16)
    px = img.load()
    phase = f / FRAMES * math.tau
    breath = 0.5 + 0.5 * math.cos(phase)
    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            if edge(x, y):
                px[x, y] = OUTLINE
                continue
            dx, dy = x - CORE[0], y - CORE[1]
            r = math.hypot(dx, dy)
            if r < 0.8:
                px[x, y] = GAP
                continue
            if r < 1.8:
                # the thin halo around the gap breathes: bright, then soft
                px[x, y] = HALO[2] if breath > 0.5 and (dx < 0 or dy < 0) else HALO[1] if breath > 0.25 else HALO[0]
                continue
            # two swirling arms of light winding outward from the halo: one teal, one rose
            theta = math.atan2(dy, dx)
            arm = (theta - 0.45 * r + phase / 2) % math.tau
            band = math.cos(2 * (theta - 0.45 * r) + phase)
            if band > 0.6 and r < 4.8:
                ramp = TEAL if arm < math.pi else ROSE
                px[x, y] = ramp[2] if band > 0.93 and r < 3.2 else ramp[1] if band > 0.8 else ramp[0]
            else:
                px[x, y] = body_shade(x, y)
    # a fixed glint on the upper-left shoulder and along the tip
    for gx, gy in ((5, 7), (5, 8), (7, 3)):
        if inside(gx, gy) and not edge(gx, gy):
            px[gx, gy] = HALO[1]
    # four-point sparkles, blinking on alternate sides
    sparks = {0: (13, 4, 1), 1: (13, 4, 0), 4: (2, 6, 1), 5: (2, 6, 0), 2: (12, 14, -1), 6: (2, 13, -1)}
    if f in sparks:
        sx, sy, size = sparks[f]
        px[sx, sy] = SPARK[1]
        if size >= 0:
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= sx + ox < 16 and 0 <= sy + oy < 16 and not inside(sx + ox, sy + oy):
                    px[sx + ox, sy + oy] = SPARK[0]
        if size >= 1:
            for ox, oy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
                if 0 <= sx + ox < 16 and 0 <= sy + oy < 16 and not inside(sx + ox, sy + oy):
                    px[sx + ox, sy + oy] = c("#FFF6C9", 150)
    return img


def paint():
    strip = new(16, 16 * FRAMES)
    for f in range(FRAMES):
        strip.alpha_composite(frame(f), (0, 16 * f))
    rel = "item/lacunar_quintessence"
    save(strip, rel)
    with open(os.path.join(TEXTURES, rel + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"animation": {"frametime": FRAMETIME}}, fh, indent=2, sort_keys=True)
        fh.write("\n")
