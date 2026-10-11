"""Small deterministic pixel-art toolkit used by gen_textures.py (Python 3 + Pillow).

Everything is seeded from the texture name, so running the generator twice produces byte-identical PNGs.
"""
from __future__ import annotations

import hashlib
import math
import os
import random
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from PIL import Image

Color = Tuple[int, int, int, int]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "lacuna", "textures")

WRITTEN: List[str] = []


def c(hexstr: str, alpha: int = 255) -> Color:
    h = hexstr.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def ramp(*hexes: str, alpha: int = 255) -> List[Color]:
    return [c(h, alpha) for h in hexes]


def rng(name: str) -> random.Random:
    seed = int(hashlib.sha256(name.encode("utf-8")).hexdigest()[:16], 16)
    return random.Random(seed)


def new(w: int = 16, h: int = 16) -> Image.Image:
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def from_ascii(rows: Sequence[str], palette: Dict[str, Optional[Color]]) -> Image.Image:
    h = len(rows)
    w = max(len(r) for r in rows)
    img = new(w, h)
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in (".", " "):
                continue
            if ch not in palette:
                raise KeyError(f"palette has no entry for {ch!r} (row {y}: {row!r})")
            col = palette[ch]
            if col is not None:
                px[x, y] = col
    return img


def save(img: Image.Image, rel: str) -> None:
    path = os.path.join(TEXTURES, rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, format="PNG", optimize=False)
    WRITTEN.append(rel)


def lerp(a: Color, b: Color, t: float) -> Color:
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))  # type: ignore


def with_alpha(col: Color, alpha: int) -> Color:
    return (col[0], col[1], col[2], alpha)


def recolor(img: Image.Image, mapping: Dict[Color, Color]) -> Image.Image:
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p in mapping:
                px[x, y] = mapping[p]
    return out


def overlay(base: Image.Image, top: Image.Image) -> Image.Image:
    out = base.copy()
    out.alpha_composite(top)
    return out


# --------------------------------------------------------------------------------------------- noise

def value_noise(w: int, h: int, r: random.Random, cell: int = 4, octaves: int = 2, tile: bool = True) -> List[List[float]]:
    """Tileable value noise in [0,1]."""
    total = [[0.0] * w for _ in range(h)]
    amp_sum = 0.0
    amp = 1.0
    size = cell
    for _ in range(octaves):
        gw = max(1, w // size)
        gh = max(1, h // size)
        grid = [[r.random() for _ in range(gw)] for _ in range(gh)]
        for y in range(h):
            for x in range(w):
                fx = x / size
                fy = y / size
                x0 = int(math.floor(fx)) % gw
                y0 = int(math.floor(fy)) % gh
                x1 = (x0 + 1) % gw if tile else min(x0 + 1, gw - 1)
                y1 = (y0 + 1) % gh if tile else min(y0 + 1, gh - 1)
                tx = fx - math.floor(fx)
                ty = fy - math.floor(fy)
                tx = tx * tx * (3 - 2 * tx)
                ty = ty * ty * (3 - 2 * ty)
                a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
                b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
                total[y][x] += (a * (1 - ty) + b * ty) * amp
        amp_sum += amp
        amp *= 0.5
        size = max(1, size // 2)
    return [[v / amp_sum for v in row] for row in total]


def ramp_pick(pal: Sequence[Color], v: float) -> Color:
    v = min(0.9999, max(0.0, v))
    return pal[int(v * len(pal))]


def noise_fill(pal: Sequence[Color], name: str, w: int = 16, h: int = 16, cell: int = 4, speckle: float = 0.18,
               contrast: float = 1.0) -> Image.Image:
    r = rng(name)
    n = value_noise(w, h, r, cell=cell, octaves=2)
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            v = (n[y][x] - 0.5) * contrast + 0.5
            if r.random() < speckle:
                v += r.choice((-0.25, 0.25))
            px[x, y] = ramp_pick(pal, v)
    return img


def outline(img: Image.Image, col: Color, diagonal: bool = False, only_bottom_right: bool = False) -> Image.Image:
    """Adds an outline in transparent pixels next to opaque ones."""
    out = img.copy()
    src = img.load()
    dst = out.load()
    w, h = img.size
    dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    if diagonal:
        dirs += [(1, 1), (1, -1), (-1, 1), (-1, -1)]
    for y in range(h):
        for x in range(w):
            if src[x, y][3] != 0:
                continue
            for dx, dy in dirs:
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 0:
                    if only_bottom_right and (dx > 0 or dy > 0):
                        continue
                    dst[x, y] = col
                    break
    return out


def count_colors(img: Image.Image) -> int:
    return len({p for p in img.getdata() if p[3] > 0})


def bevel(img: Image.Image, light: Color, dark: Color) -> Image.Image:
    """Light top/left border, dark bottom/right border (storage blocks)."""
    out = img.copy()
    px = out.load()
    w, h = out.size
    for i in range(w):
        px[i, 0] = light
        px[i, h - 1] = dark
    for j in range(h):
        px[0, j] = light
        px[w - 1, j] = dark
    px[w - 1, 0] = lerp(light, dark, 0.5)
    px[0, h - 1] = lerp(light, dark, 0.5)
    return out


def upscale_preview(images: Iterable[Tuple[str, Image.Image]], path: str, scale: int = 16, cols: int = 8,
                    bg: Color = (170, 180, 190, 255)) -> None:
    images = list(images)
    if not images:
        return
    cell_w = max(i.width for _, i in images) * scale + 8
    cell_h = max(i.height for _, i in images) * scale + 8
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cell_w * min(cols, len(images)), cell_h * rows), bg)
    for idx, (_, im) in enumerate(images):
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        sheet.alpha_composite(big, ((idx % cols) * cell_w + 4, (idx // cols) * cell_h + 4))
    sheet.save(path)
