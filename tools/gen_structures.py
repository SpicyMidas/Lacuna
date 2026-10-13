#!/usr/bin/env python3
"""Structure template generator for Lacuna (Python 3 + nbtlib).

Writes gzipped structure NBT files (DataVersion 3955 = Minecraft 1.21.1) into
src/generated/resources/data/lacuna/structure/. Every builder is deterministic: randomness is
seeded from the structure name, so running the script twice yields identical files.

Usage:  python3 tools/gen_structures.py
"""
from __future__ import annotations

import gzip
import hashlib
import importlib
import io
import os
import random
import shutil
import sys
from typing import Dict, List, Optional, Tuple

import nbtlib
from nbtlib import Byte, Compound, Double, Float, Int, List as NList, String

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "src", "generated", "resources", "data", "lacuna", "structure")
DATA_VERSION = 3955

MODULES = ["m02_forgotten_land", "m10_biomes_structures", "m11_villages", "m00_gametest"]

Pos = Tuple[int, int, int]


def rng(name: str) -> random.Random:
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest()[:16], 16))


def state_key(name: str, props: Optional[Dict[str, str]]) -> Tuple[str, Tuple[Tuple[str, str], ...]]:
    if ":" not in name:
        name = "minecraft:" + name
    return name, tuple(sorted((props or {}).items()))


class Template:
    def __init__(self, name: str, sx: int, sy: int, sz: int) -> None:
        self.name = name
        self.size = (sx, sy, sz)
        self.blocks: Dict[Pos, Tuple[Tuple[str, Tuple[Tuple[str, str], ...]], Optional[dict]]] = {}
        self.entities: List[Tuple[Tuple[float, float, float], Pos, dict]] = []
        self.r = rng(name)
        self.strict = name.startswith("village/")  # civic village pieces must never lose blocks silently

    # ------------------------------------------------------------------------------------- editing
    def inside(self, x: int, y: int, z: int) -> bool:
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x: int, y: int, z: int, name: str, props: Optional[Dict[str, str]] = None,
            nbt: Optional[dict] = None) -> None:
        if not self.inside(x, y, z):
            if self.strict:
                raise ValueError(f"{self.name}: block {name} at {(x, y, z)} is outside {self.size}")
            return
        self.blocks[(x, y, z)] = (state_key(name, props), nbt)

    def get(self, x: int, y: int, z: int) -> Optional[str]:
        b = self.blocks.get((x, y, z))
        return b[0][0] if b else None

    def clear(self, x: int, y: int, z: int) -> None:
        self.blocks.pop((x, y, z), None)

    def fill(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, name: str,
             props: Optional[Dict[str, str]] = None) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def hollow(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, name: str,
               props: Optional[Dict[str, str]] = None, inner: Optional[str] = "air") -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    edge = x in (x0, x1) or y in (y0, y1) or z in (z0, z1)
                    if edge:
                        self.set(x, y, z, name, props)
                    elif inner:
                        self.set(x, y, z, inner)

    def chest(self, x: int, y: int, z: int, loot_table: str, facing: str = "north") -> None:
        self.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": "minecraft:chest", "LootTable": loot_table})

    def barrel(self, x: int, y: int, z: int, loot_table: str, facing: str = "up") -> None:
        self.set(x, y, z, "barrel", {"facing": facing, "open": "false"},
                 {"id": "minecraft:barrel", "LootTable": loot_table})

    def jigsaw(self, x: int, y: int, z: int, orientation: str, name: str, target: str, pool: str,
               final_state: str = "minecraft:air", joint: str = "rollable", selection_priority: int = 0) -> None:
        """selection_priority: a piece tries its jigsaws in descending priority (ties in random order)."""
        self.set(x, y, z, "jigsaw", {"orientation": orientation},
                 {"id": "minecraft:jigsaw", "name": name, "target": target, "pool": pool,
                  "final_state": final_state, "joint": joint,
                  "placement_priority": 0, "selection_priority": selection_priority})

    def sign(self, x: int, y: int, z: int, lines: List[str], wood: str = "oak", rotation: int = 0,
             wall_facing: Optional[str] = None) -> None:
        import json as _json
        messages = [_json.dumps({"text": t}) for t in (lines + ["", "", "", ""])[:4]]
        text = {"messages": messages, "color": "black", "has_glowing_text": False}
        nbt = {"id": "minecraft:sign", "is_waxed": True, "front_text": text,
               "back_text": {"messages": [_json.dumps({"text": ""})] * 4, "color": "black", "has_glowing_text": False}}
        if wall_facing:
            self.set(x, y, z, f"{wood}_wall_sign", {"facing": wall_facing, "waterlogged": "false"}, nbt)
        else:
            self.set(x, y, z, f"{wood}_sign", {"rotation": str(rotation), "waterlogged": "false"}, nbt)

    def entity(self, pos: Tuple[float, float, float], nbt: dict) -> None:
        bp = (int(pos[0] // 1), int(pos[1] // 1), int(pos[2] // 1))
        self.entities.append((pos, bp, nbt))

    # ------------------------------------------------------------------------------------- output
    def to_nbt(self) -> nbtlib.File:
        palette: List[Tuple[str, Tuple[Tuple[str, str], ...]]] = []
        index: Dict[Tuple[str, Tuple[Tuple[str, str], ...]], int] = {}
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            key = self.blocks[pos][0]
            if key not in index:
                index[key] = len(palette)
                palette.append(key)
        pal_tags = []
        for name, props in palette:
            entry = {"Name": String(name)}
            if props:
                entry["Properties"] = Compound({k: String(v) for k, v in props})
            pal_tags.append(Compound(entry))
        block_tags = []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            key, nbt = self.blocks[pos]
            entry = {"pos": NList[Int]([Int(pos[0]), Int(pos[1]), Int(pos[2])]), "state": Int(index[key])}
            if nbt is not None:
                entry["nbt"] = to_tag(nbt)
            block_tags.append(Compound(entry))
        ent_tags = []
        for pos, bp, nbt in self.entities:
            ent_tags.append(Compound({
                "pos": NList[Double]([Double(pos[0]), Double(pos[1]), Double(pos[2])]),
                "blockPos": NList[Int]([Int(bp[0]), Int(bp[1]), Int(bp[2])]),
                "nbt": to_tag(nbt)}))
        root = Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": NList[Int]([Int(s) for s in self.size]),
            "palette": NList[Compound](pal_tags),
            "blocks": NList[Compound](block_tags),
            "entities": NList[Compound](ent_tags),
        })
        return nbtlib.File(root, gzipped=True)

    def save(self, rel: Optional[str] = None) -> None:
        rel = rel or self.name
        path = os.path.join(OUT, rel + ".nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        f = self.to_nbt()
        # deterministic gzip: fixed mtime, no filename
        raw = io.BytesIO()
        f.write(raw, byteorder="big")
        data = raw.getvalue()
        # nbtlib writes gzipped data when gzipped=True; re-pack with mtime=0 for byte-identical output
        try:
            data = gzip.decompress(data)
        except OSError:
            pass
        with open(path, "wb") as fh:
            with gzip.GzipFile(filename="", mode="wb", fileobj=fh, mtime=0) as gz:
                gz.write(data)
        WRITTEN.append(rel)


WRITTEN: List[str] = []


def to_tag(v):
    if isinstance(v, bool):
        return Byte(1 if v else 0)
    if isinstance(v, int):
        return Int(v)
    if isinstance(v, float):
        return Float(v)
    if isinstance(v, str):
        return String(v)
    if isinstance(v, dict):
        return Compound({k: to_tag(x) for k, x in v.items()})
    if isinstance(v, (list, tuple)):
        if not v:
            return NList[String]([])
        tags = [to_tag(x) for x in v]
        return NList[type(tags[0])](tags)
    if isinstance(v, nbtlib.tag.Base):
        return v
    raise TypeError(type(v))


def main() -> int:
    sys.path.insert(0, HERE)
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    for name in MODULES:
        if not os.path.exists(os.path.join(HERE, "structures", name + ".py")):
            continue
        module = importlib.import_module(f"structures.{name}")
        module.build(Template)
    print(f"gen_structures: wrote {len(WRITTEN)} structure templates")
    return 0


if __name__ == "__main__":
    sys.exit(main())
