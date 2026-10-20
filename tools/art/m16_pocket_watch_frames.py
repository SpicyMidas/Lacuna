"""Chronoshard Pocket Watch: plays the hand-drawn strip in its true frame order.

tools/art/hand/item/chronoshard_pocket_watch.png is a 16x9216 strip of 576 hand-drawn frames. The frames are all
correct, but the exporter named them 00..575 and stacked them in *text* order, not in number order:
00, 01, ..., 09, 10, 100, 101, ..., 109, 11, 110, ..., 119, 12, 120, ... So the strip is scrambled: every 11th frame
comes from a much earlier moment, and whole runs of ten sit far from where they belong, which is why the slow hand
jumped back and forth however the old frame list cut the strip.

In true order the animation is seamless: the bright fast hand takes one of its 24 drawn steps every frame (one turn
every 24 frames), the darker slow hand takes one step every 12 frames (one turn every 288 frames, so the hands loop
twice), and the gem at the bottom fades blue -> purple -> black -> red -> blue once over the 576 frames.

This module never touches the PNG (gen_textures still copies it byte for byte after every art module has run). It
only writes the .png.mcmeta frame list that plays the strip in true order, after checking, frame by frame, that the
order it picked really moves the hands one step at a time (see analyse()): the background face is the per-pixel
mode over all frames, each pixel of the dial shows exactly two hand colours (the brighter one belongs to the fast
hand), the fast hand's 24 drawn shapes are sorted clockwise, and the slow hand is matched to the same 24 shapes even
where the fast hand covers part of it. If a new strip is dropped in, the same check picks its order (or keeps the
strip as is when it is already in order).
"""
from __future__ import annotations

import json
import math
import os
from collections import Counter
from typing import Dict, List, Optional, Sequence, Tuple

from PIL import Image

from texlib import TEXTURES

HERE = os.path.dirname(os.path.abspath(__file__))
REL = "item/chronoshard_pocket_watch"
HAND_PNG = os.path.join(HERE, "hand", *REL.split("/")) + ".png"
CENTRE = (8, 8)       # dial centre: the hands' common pivot (never changes)
HAND_RADIUS = 3.5     # both hands are three pixels long
FRAMETIME = 1         # ticks per frame, as drawn: the fast hand turns in 24 ticks, the slow hand in 288

Pixel = Tuple[int, int, int, int]
Pos = Tuple[int, int]


def load_frames(path: str = HAND_PNG) -> Tuple[int, List[List[Pixel]]]:
    """The strip as a list of frames, each a row-major list of w*w RGBA pixels."""
    with Image.open(path) as im:
        img = im.convert("RGBA")
    w, h = img.size
    px = img.load()
    return w, [[px[x, f * w + y] for y in range(w) for x in range(w)] for f in range(h // w)]


def _lum(p: Pixel) -> float:
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def _angle(cells: Sequence[Pos]) -> float:
    """Clockwise angle from 12 o'clock of a hand drawn on these cells."""
    vx = sum(x - CENTRE[0] for x, _ in cells)
    vy = sum(y - CENTRE[1] for _, y in cells)
    return math.degrees(math.atan2(vx, -vy)) % 360.0


def analyse(w: int, frames: List[List[Pixel]]) -> Dict[str, object]:
    """Splits every frame into background face, fast hand and slow hand.

    Returns {"fast": [step per frame], "slow": [step per frame], "steps": 24, "angles": [deg per step],
    "bg": background pixels, "dial": [hand cells]}. Raises ValueError when the strip does not split cleanly."""
    n = len(frames)
    bg = [Counter(f[i] for f in frames).most_common(1)[0][0] for i in range(w * w)]
    dial = [(x, y) for y in range(w) for x in range(w)
            if (x, y) != CENTRE and math.hypot(x - CENTRE[0], y - CENTRE[1]) <= HAND_RADIUS
            and any(f[y * w + x] != bg[y * w + x] for f in frames)]
    fast_col: Dict[Pos, Pixel] = {}
    for x, y in dial:
        cols = sorted({f[y * w + x] for f in frames} - {bg[y * w + x]}, key=_lum)
        if len(cols) != 2:
            raise ValueError(f"dial pixel {(x, y)} shows {len(cols)} hand colours, expected 2 (fast + slow)")
        fast_col[(x, y)] = cols[1]
    fast_sets, slow_sets = [], []
    for f in frames:
        fs, ss = [], []
        for x, y in dial:
            p = f[y * w + x]
            if p != bg[y * w + x]:
                (fs if p == fast_col[(x, y)] else ss).append(((x, y), p))
        if not fs:
            raise ValueError("a frame has no fast hand")
        fast_sets.append(frozenset(fs))
        slow_sets.append(frozenset(ss))
    shapes = sorted(set(fast_sets), key=lambda s: _angle([c for c, _ in s]))
    steps = len(shapes)
    cells = [frozenset(c for c, _ in s) for s in shapes]
    fast = [shapes.index(s) for s in fast_sets]
    slow_shapes: List[Optional[frozenset]] = [None] * steps
    for s in set(slow_sets):
        c = frozenset(c for c, _ in s)
        if c in cells:
            slow_shapes[cells.index(c)] = s
    if any(s is None for s in slow_shapes):
        raise ValueError("the slow hand is never fully visible at some of its steps")
    slow = []
    for i in range(n):
        seen = slow_sets[i]
        covered = cells[fast[i]]
        cand = [k for k in range(steps) if seen <= slow_shapes[k] and (cells[k] - {c for c, _ in seen}) <= covered]
        if len(cand) > 1 and fast[i] in cand:
            cand = [fast[i]]  # fully hidden under the fast hand: both hands point the same way
        if len(cand) != 1:
            raise ValueError(f"cannot place the slow hand in strip frame {i}")
        slow.append(cand[0])
    return {"fast": fast, "slow": slow, "steps": steps, "angles": [_angle(list(c)) for c in cells],
            "bg": bg, "dial": dial}


def order_problems(order: Sequence[int], info: Dict[str, object], frames: List[List[Pixel]], w: int) -> List[str]:
    """What is wrong with playing the strip frames in this order, as a loop (empty list = seamless)."""
    fast, slow, steps = info["fast"], info["slow"], info["steps"]
    dial = {y * w + x for x, y in info["dial"]}
    problems, slow_moves = [], []
    for t, a in enumerate(order):
        b = order[(t + 1) % len(order)]
        df = (fast[b] - fast[a]) % steps
        ds = (slow[b] - slow[a]) % steps
        if df != 1:
            problems.append(f"{t}->{t + 1}: fast hand moves {df} steps")
        if ds not in (0, 1):
            problems.append(f"{t}->{t + 1}: slow hand moves {ds} steps")
        elif ds:
            slow_moves.append(t)
        other = sum(1 for i in range(w * w) if i not in dial and frames[a][i] != frames[b][i])
        if other > 2:
            problems.append(f"{t}->{t + 1}: {other} pixels outside the dial change at once")
    gaps = {(slow_moves[(i + 1) % len(slow_moves)] - slow_moves[i]) % len(order) for i in range(len(slow_moves))}
    if len(gaps) > 1:
        problems.append(f"slow hand steps unevenly (gaps {sorted(gaps)})")
    return problems


def export_order(n: int, digits: int) -> List[int]:
    """order[t] = strip index of true frame t, for frames named t zero-padded to `digits` and stacked in text order."""
    names = sorted(range(n), key=lambda t: f"{t:0{digits}d}")
    order = [0] * n
    for k, t in enumerate(names):
        order[t] = k
    return order


def true_order(w: int, frames: List[List[Pixel]]) -> Tuple[List[int], str]:
    n = len(frames)
    info = analyse(w, frames)
    tried = []
    for digits in range(len(str(n - 1)), 0, -1):  # the widest padding is the strip's own order
        order = export_order(n, digits)
        problems = order_problems(order, info, frames, w)
        if not problems:
            how = "already in order" if order == list(range(n)) else f"stacked in text order of {digits}-digit names"
            return order, how
        tried.append(f"{digits}-digit names: {len(problems)} problems, first {problems[0]}")
    raise ValueError("no export order plays the hands smoothly (" + "; ".join(tried) + ")")


def paint() -> None:
    w, frames = load_frames()
    try:
        order, how = true_order(w, frames)
    except ValueError as e:
        print(f"  WARNING {REL}: {e}; playing the strip as stored")
        order, how = list(range(len(frames))), "unchecked"
    if os.path.exists(HAND_PNG + ".mcmeta"):
        print(f"  WARNING {REL}: a hand-made .mcmeta in tools/art/hand overrides the generated frame order")
    anim: Dict[str, object] = {"frametime": FRAMETIME}
    if order != list(range(len(frames))):
        anim["frames"] = order
    path = os.path.join(TEXTURES, REL + ".png.mcmeta")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps({"animation": anim}, separators=(",", ":")) + "\n")
    print(f"  {REL}: {len(frames)} frames {how}; frame list plays them in true order")
