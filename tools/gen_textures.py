#!/usr/bin/env python3
"""Deterministic texture generator for Lacuna (Python 3 + Pillow).

Each module in tools/art/ paints the textures of one milestone into
src/main/resources/assets/lacuna/textures/. All randomness is seeded from texture names, so
running this twice yields byte-identical PNGs.

Usage:  python3 tools/gen_textures.py [--preview sheet.png]
"""
from __future__ import annotations

import importlib
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import texlib  # noqa: E402

ART_MODULES = ["m01_ruby_glass", "m02_forgotten_land", "m03_magnifier", "m04_eclipse_solar", "m05_pearl",
               "m06_utility", "m07_gear", "m08_wood", "m09_mobs", "m10_biomes_structures", "m11_villages", "m13_fx",
               "m14_ember", "m14_quickening",
               "m15_chrono_hourglass", "m15_bag"]


HAND = os.path.join(HERE, "art", "hand")


def apply_hand_drawn() -> int:
    """Copy tools/art/hand/** over the generated textures byte for byte (never re-encoded)."""
    count = 0
    for root, _dirs, files in os.walk(HAND):
        for f in sorted(files):
            if not (f.endswith(".png") or f.endswith(".png.mcmeta")):
                continue
            src = os.path.join(root, f)
            rel = os.path.relpath(src, HAND)
            dst = os.path.join(texlib.TEXTURES, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)
            if f.endswith(".png"):
                count += 1
                key = rel[:-4].replace(os.sep, "/")
                if key not in texlib.WRITTEN:
                    texlib.WRITTEN.append(key)
    return count


def main(argv) -> int:
    preview = None
    if "--preview" in argv:
        preview = argv[argv.index("--preview") + 1]
    if os.path.isdir(texlib.TEXTURES):
        shutil.rmtree(texlib.TEXTURES)
    painted = []
    for name in ART_MODULES:
        if not os.path.exists(os.path.join(HERE, "art", name + ".py")):
            continue
        module = importlib.import_module(f"art.{name}")
        before = len(texlib.WRITTEN)
        module.paint()
        painted.append((name, len(texlib.WRITTEN) - before))
    hand = apply_hand_drawn()
    for name, n in painted:
        print(f"  {name}: {n} textures")
    print(f"  hand-drawn overrides (copied verbatim): {hand}")
    print(f"gen_textures: wrote {len(texlib.WRITTEN)} textures")
    if preview:
        from PIL import Image
        imgs = []
        for rel in texlib.WRITTEN:
            im = Image.open(os.path.join(texlib.TEXTURES, rel + ".png")).convert("RGBA")
            if im.height > 64:
                im = im.crop((0, 0, im.width, im.width))
            imgs.append((rel, im))
        texlib.upscale_preview(imgs, preview, scale=4 if len(imgs) > 40 else 8, cols=16)
        print("preview:", preview)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
