"""64x64 humanoid skin painter matching the vanilla HumanoidModel (zombie/player) UV layout."""
from typing import Optional, Sequence

from texlib import Color, c, new, rng, with_alpha

# (u, v, w, h, d) for each cube; faces laid out like vanilla box UV: top/bottom then right/front/left/back.
CUBES = {
    "head": (0, 0, 8, 8, 8),
    "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "right_arm": (40, 16, 4, 12, 4),
    "right_leg": (0, 16, 4, 12, 4),
}


def face_rects(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


def humanoid_skin(name: str, body: Sequence[Color], alpha: int = 255, eyes: Optional[Color] = None, seed: str = "",
                  hat: bool = False, accent: Optional[Color] = None, legs: Optional[Sequence[Color]] = None,
                  head: Optional[Sequence[Color]] = None) -> "Image":
    img = new(64, 64)
    px = img.load()
    r = rng(seed or name)
    for part, (u, v, w, h, d) in CUBES.items():
        if part == "hat" and not hat:
            continue
        pal = body
        if part == "right_leg" and legs:
            pal = legs
        if part in ("head", "hat") and head:
            pal = head
        for face, (x0, y0, fw, fh) in face_rects(u, v, w, h, d).items():
            for y in range(y0, y0 + fh):
                for x in range(x0, x0 + fw):
                    t = (y - y0) / max(1, fh - 1)
                    idx = min(len(pal) - 1, int((1 - t) * (len(pal) - 1) + r.random() * 0.8))
                    col = pal[idx]
                    a = alpha if part != "hat" else min(alpha, 120)
                    px[x, y] = with_alpha(col, a)
    if eyes:
        fx, fy = 8, 8  # head front
        for (x, y) in ((fx + 2, fy + 4), (fx + 5, fy + 4)):
            px[x, y] = eyes
            px[x, y - 1] = with_alpha(eyes, min(255, alpha + 40))
    if accent:
        for x in range(20, 28):
            px[x, 29] = accent  # belt line on the body front
    return img
