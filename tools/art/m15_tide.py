"""Tidecaller waterspout sprites (block atlas, so they animate and batch with vanilla's water_still / water_flow).

Vanilla-style pixel art: a handful of flat colours, hard alpha, no gradients. Both are tileable 16x16 frames:
  block/tide_foam   white foam streaks drifting up and to the right (8 frames): swirls on the spout and whirlpool
  block/tide_spray  loose droplets and short drips falling (4 frames): the spray curtain and the geyser's crown
"""
import json
import os

from texlib import TEXTURES, c, new, rng, save

FOAM_HI = c("ffffff", 235)
FOAM = c("e2f4fa", 205)
FOAM_LO = c("b2deee", 150)
DROP_HI = c("f4fcff", 230)
DROP = c("bfe6f4", 190)
DROP_LO = c("8ccbe6", 140)


def _save_animated(img, rel, frametime):
    save(img, rel)
    with open(os.path.join(TEXTURES, rel + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"animation": {"frametime": frametime}}, fh, indent=2, sort_keys=True)
        fh.write("\n")


def _put(px, x, y, col):
    """Writes a pixel on the 16x16 torus, never over a brighter one."""
    x %= 16
    y %= 16
    if px[x, y][3] < col[3] or (px[x, y][3] == col[3] and sum(px[x, y][:3]) < sum(col[:3])):
        px[x, y] = col


def _foam_tile():
    """One 16x16 tile of foam: staircase streaks (2 across, 1 up per step) with a bright head and fading tail."""
    r = rng("tide_foam")
    img = new(16, 16)
    px = img.load()
    streaks = [(1, 5, 4), (9, 7, 3), (3, 13, 5), (12, 15, 3), (7, 1, 2)]
    for x0, y0, length in streaks:
        for i in range(length):
            x = x0 + i * 2
            y = y0 - i
            col = FOAM_HI if i >= length - 2 else FOAM if i >= length // 3 else FOAM_LO
            _put(px, x, y, col)
            _put(px, x + 1, y, FOAM if col is FOAM_HI else FOAM_LO)
        # a fleck of loose foam ahead of each streak
        _put(px, x0 + length * 2 + 1 + r.randrange(2), y0 - length - 1, FOAM_LO)
    for _ in range(5):
        _put(px, r.randrange(16), r.randrange(16), FOAM_LO)
    return img


def tide_foam():
    tile = _foam_tile()
    frames = 8
    img = new(16, 16 * frames)
    src = tile.load()
    px = img.load()
    for f in range(frames):
        # drift 2 right and 2 up per frame: after 8 frames the tile has moved a whole tile, so the loop is seamless
        for y in range(16):
            for x in range(16):
                px[(x + 2 * f) % 16, (y - 2 * f) % 16 + 16 * f] = src[x, y]
    _save_animated(img, "block/tide_foam", 2)


def _spray_tile():
    """Loose droplets and short drips, sparse enough that the water behind shows through."""
    r = rng("tide_spray")
    img = new(16, 16)
    px = img.load()
    for cell in range(9):
        # one droplet per cell of a jittered 3x3 grid: sparse but even
        x = (cell % 3) * 5 + r.randrange(5)
        y = (cell // 3) * 5 + r.randrange(5)
        kind = r.randrange(3)
        if kind == 0:
            # a drip: bright head under a short tail
            _put(px, x, y, DROP_HI)
            _put(px, x, y - 1, DROP)
            _put(px, x, y - 2, DROP_LO)
        elif kind == 1:
            # a round droplet
            _put(px, x, y, DROP_HI)
            _put(px, x + 1, y, DROP)
            _put(px, x, y + 1, DROP)
            _put(px, x + 1, y + 1, DROP_LO)
        else:
            _put(px, x, y, DROP)
    return img


def tide_spray():
    tile = _spray_tile()
    frames = 4
    img = new(16, 16 * frames)
    src = tile.load()
    px = img.load()
    for f in range(frames):
        # falls 4 pixels per frame: one tile per loop
        for y in range(16):
            for x in range(16):
                px[x, (y + 4 * f) % 16 + 16 * f] = src[x, y]
    _save_animated(img, "block/tide_spray", 2)


def paint():
    tide_foam()
    tide_spray()
