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
               "m06_utility", "m07_gear", "m08_wood", "m09_mobs", "m10_biomes_structures", "m11_townships"]


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
    for name, n in painted:
        print(f"  {name}: {n} textures")
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
