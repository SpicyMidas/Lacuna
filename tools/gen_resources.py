#!/usr/bin/env python3
"""Deterministic resource generator for Lacuna (replaces Java datagen, see docs/PROGRESS.md).

Writes everything under src/generated/resources (except data/lacuna/structure, owned by gen_structures.py):
blockstates, block/item models, en_us + it_it lang, loot tables, recipes (+ unlock advancements), tags,
damage types, worldgen, dimension, biome modifiers, painting variants and so on.

Content is declared in tools/content/*.py (one module per milestone). Run:  python3 tools/gen_resources.py
Running it twice produces identical files (sorted keys, stable ordering).
"""
from __future__ import annotations

import copy
import importlib
import json
import os
import shutil
import sys
from collections import OrderedDict
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "generated", "resources")
TEMPLATES = os.path.join(ROOT, "tools", "templates")
MODID = "lacuna"
KEEP = [os.path.join("data", MODID, "structure")]

CONTENT_MODULES = [
    "m00_core", "m01_ruby_glass", "m02_forgotten_land", "m03_magnifier", "m04_eclipse_solar", "m05_pearl",
    "m06_utility", "m07_gear", "m08_wood", "m09_mobs", "m10_biomes_structures", "m11_villages", "m12_polish", "m13_fx",
    "m14_quickening",
    "m15_chrono_hourglass", "m15_bag", "m15_starcaller",
    "m16_confusion", "m16_natural_stars", "m16_starlace_bandage", "m16_bag_players",
    "m17_exile", "m17_shadow_possession", "m18_civic_gui",
    "m19_progression", "m19_wayfarers_chart",
    "m20_almanac", "m20_milestones",
]


def mod(path: str) -> str:
    return f"{MODID}:{path}"


def mc(path: str) -> str:
    return f"minecraft:{path}"


def ns(rl: str) -> str:
    return rl if ":" in rl else f"minecraft:{rl}"


class Gen:
    def __init__(self) -> None:
        self.files: Dict[str, Any] = OrderedDict()
        self.lang: Dict[str, str] = {}
        self.lang_it: Dict[str, str] = {}
        self.tags: Dict[Tuple[str, str], List[Any]] = {}
        self.block_props: Dict[str, Dict[str, List[str]]] = {}
        self.items: List[str] = []
        self.blocks: List[str] = []
        self.shared: Dict[str, Any] = {}
        self.finalizers: List[Any] = []

    # ------------------------------------------------------------------------------------- raw output
    def put(self, rel: str, data: Any) -> None:
        if rel in self.files and self.files[rel] != data:
            raise ValueError(f"conflicting definitions for {rel}")
        self.files[rel] = data

    def override(self, rel: str, data: Any) -> None:
        """Replaces a file written by an earlier milestone module (e.g. the dimension gaining biomes)."""
        self.files[rel] = data

    def finalizer(self, fn) -> None:
        """Runs fn(g) after every content module (for tables that several milestones extend)."""
        self.finalizers.append(fn)

    def asset(self, sub: str, data: Any) -> None:
        self.put(f"assets/{MODID}/{sub}.json", data)

    def data(self, sub: str, data: Any, namespace: str = MODID) -> None:
        self.put(f"data/{namespace}/{sub}.json", data)

    def name(self, key: str, en: str, it: Optional[str] = None) -> None:
        if key in self.lang and self.lang[key] != en:
            raise ValueError(f"lang key {key} defined twice")
        self.lang[key] = en
        if it:
            self.lang_it[key] = it

    def tag(self, kind: str, tag: str, *values: Any) -> None:
        """kind: 'item', 'block', 'entity_type', 'worldgen/biome', ...; tag: 'ns:path'."""
        lst = self.tags.setdefault((kind, ns(tag)), [])
        for v in values:
            if v not in lst:
                lst.append(v)

    def props(self, block_id: str, **props: Sequence[str]) -> None:
        self.block_props[block_id] = {k: [str(x) for x in v] for k, v in props.items()}

    # ------------------------------------------------------------------------------------- templates
    def clone(self, template: str, pairs: Sequence[Tuple[str, str]], blockstate_name: str,
              extra_models: Sequence[str] = (), render_type: Optional[str] = None) -> None:
        """Copies a vanilla blockstate and its models, rewriting resource locations with ordered replacements."""
        def rw(s: str) -> str:
            full = s if ":" in s else "minecraft:" + s
            for old, new in pairs:
                if full.startswith(old) or old in full:
                    full = full.replace(old, new)
                    return full
            return s

        def walk(obj):
            if isinstance(obj, dict):
                return {k: walk(v) for k, v in obj.items()}
            if isinstance(obj, list):
                return [walk(v) for v in obj]
            if isinstance(obj, str) and ("block/" in obj or "item/" in obj):
                return rw(obj)
            return obj

        bs = json.load(open(os.path.join(TEMPLATES, "blockstates", template + ".json"), encoding="utf-8"))
        self.asset(f"blockstates/{blockstate_name}", walk(bs))
        models = set(extra_models)
        collect_models(bs, models)
        for m in sorted(models):
            src_path = m.split(":", 1)[1]
            tpl = json.load(open(os.path.join(TEMPLATES, "models", src_path + ".json"), encoding="utf-8"))
            new = walk(tpl)
            new_name = rw(m)
            if not new_name.startswith(MODID + ":"):
                raise ValueError(f"clone of {template}: model {m} was not renamed ({new_name})")
            if render_type:
                new["render_type"] = render_type
            self.asset(f"models/{new_name.split(':', 1)[1]}", new)

    # ------------------------------------------------------------------------------------- items
    def item(self, iid: str, en: str, it: Optional[str] = None, model: str = "generated",
             texture: Optional[str] = None, layers: Optional[List[str]] = None, overrides: Optional[list] = None,
             extra: Optional[dict] = None) -> None:
        self.items.append(iid)
        self.name(f"item.{MODID}.{iid}", en, it)
        if model == "none":
            return
        if model in ("generated", "handheld", "handheld_rod"):
            tex_layers = layers or [texture or f"{MODID}:item/{iid}"]
            data: Dict[str, Any] = {"parent": f"minecraft:item/{model}",
                                    "textures": {f"layer{i}": t for i, t in enumerate(tex_layers)}}
        elif model == "spawn_egg":
            data = {"parent": "minecraft:item/template_spawn_egg"}
        elif model.startswith("parent:"):
            data = {"parent": model.split(":", 1)[1]}
        else:
            raise ValueError(model)
        if overrides:
            data["overrides"] = overrides
        if extra:
            data.update(extra)
        self.asset(f"models/item/{iid}", data)

    def item_model(self, name: str, data: dict) -> None:
        self.asset(f"models/item/{name}", data)

    # ------------------------------------------------------------------------------------- blocks
    def block_lang(self, bid: str, en: str, it: Optional[str]) -> None:
        self.blocks.append(bid)
        self.name(f"block.{MODID}.{bid}", en, it)

    def block_item_parent(self, bid: str, model: Optional[str] = None) -> None:
        self.asset(f"models/item/{bid}", {"parent": model or f"{MODID}:block/{bid}"})

    def cube(self, bid: str, en: str, it: Optional[str] = None, texture: Optional[str] = None,
             render: Optional[str] = None, item: bool = True, loot: Any = "self", parent: str = "minecraft:block/cube_all") -> None:
        self.block_lang(bid, en, it)
        model: Dict[str, Any] = {"parent": parent, "textures": {"all": texture or f"{MODID}:block/{bid}"}}
        if parent == "minecraft:block/leaves":
            pass
        if render:
            model["render_type"] = render
        self.asset(f"models/block/{bid}", model)
        self.asset(f"blockstates/{bid}", {"variants": {"": {"model": f"{MODID}:block/{bid}"}}})
        if item:
            self.block_item_parent(bid)
        self.loot(bid, loot)

    def single(self, bid: str, en: str, it: Optional[str], model: dict, item: Any = True, loot: Any = "self",
               blockstate: Optional[dict] = None, model_name: Optional[str] = None) -> None:
        """Block with one custom model (or a given blockstate)."""
        self.block_lang(bid, en, it)
        mname = model_name or bid
        if model is not None:
            self.asset(f"models/block/{mname}", model)
        self.asset(f"blockstates/{bid}", blockstate or {"variants": {"": {"model": f"{MODID}:block/{mname}"}}})
        if item is True:
            self.block_item_parent(bid, f"{MODID}:block/{mname}")
        elif isinstance(item, dict):
            self.asset(f"models/item/{bid}", item)
        self.loot(bid, loot)

    def column(self, bid: str, en: str, it: Optional[str] = None, side: Optional[str] = None,
               end: Optional[str] = None, render: Optional[str] = None, loot: Any = "self", axis: bool = True) -> None:
        self.block_lang(bid, en, it)
        side = side or f"{MODID}:block/{bid}"
        end = end or f"{MODID}:block/{bid}_top"
        m = {"parent": "minecraft:block/cube_column", "textures": {"end": end, "side": side}}
        mh = {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": end, "side": side}}
        if render:
            m["render_type"] = render
            mh["render_type"] = render
        self.asset(f"models/block/{bid}", m)
        if axis:
            self.asset(f"models/block/{bid}_horizontal", mh)
            self.asset(f"blockstates/{bid}", {"variants": {
                "axis=x": {"model": f"{MODID}:block/{bid}_horizontal", "x": 90, "y": 90},
                "axis=y": {"model": f"{MODID}:block/{bid}"},
                "axis=z": {"model": f"{MODID}:block/{bid}_horizontal", "x": 90}}})
            self.props(bid, axis=["x", "y", "z"])
        else:
            self.asset(f"blockstates/{bid}", {"variants": {"": {"model": f"{MODID}:block/{bid}"}}})
        self.block_item_parent(bid)
        self.loot(bid, loot)

    def wood_block(self, bid: str, en: str, it: Optional[str], texture: str, render: Optional[str] = None) -> None:
        """'wood' = log texture on all six sides, with axis."""
        self.column(bid, en, it, side=texture, end=texture, render=render)

    def slab(self, bid: str, en: str, it: Optional[str], full: str, texture: str, render: Optional[str] = None) -> None:
        self.block_lang(bid, en, it)
        t = {"bottom": texture, "side": texture, "top": texture}
        for suffix, parent in (("", "slab"), ("_top", "slab_top")):
            m = {"parent": f"minecraft:block/{parent}", "textures": t}
            if render:
                m["render_type"] = render
            self.asset(f"models/block/{bid}{suffix}", m)
        self.asset(f"blockstates/{bid}", {"variants": {
            "type=bottom": {"model": f"{MODID}:block/{bid}"},
            "type=double": {"model": f"{MODID}:block/{full}"},
            "type=top": {"model": f"{MODID}:block/{bid}_top"}}})
        self.props(bid, type=["top", "bottom", "double"], waterlogged=["true", "false"])
        self.block_item_parent(bid)
        self.loot(bid, "slab")

    def stairs(self, bid: str, en: str, it: Optional[str], texture: str, render: Optional[str] = None) -> None:
        self.block_lang(bid, en, it)
        self.clone("oak_stairs", [("minecraft:block/oak_stairs", f"{MODID}:block/{bid}"),
                                  ("minecraft:block/oak_planks", texture)], bid, render_type=render)
        self.props(bid, facing=["north", "south", "west", "east"], half=["top", "bottom"],
                   shape=["straight", "inner_left", "inner_right", "outer_left", "outer_right"], waterlogged=["true", "false"])
        self.block_item_parent(bid)
        self.loot(bid, "self")

    def wall(self, bid: str, en: str, it: Optional[str], texture: str) -> None:
        self.block_lang(bid, en, it)
        self.clone("cobblestone_wall", [("minecraft:block/cobblestone_wall", f"{MODID}:block/{bid}"),
                                        ("minecraft:block/cobblestone", texture)], bid,
                   extra_models=["minecraft:block/cobblestone_wall_inventory"])
        self.block_item_parent(bid, f"{MODID}:block/{bid}_inventory")
        self.props(bid, up=["true", "false"], north=["none", "low", "tall"], east=["none", "low", "tall"],
                   south=["none", "low", "tall"], west=["none", "low", "tall"], waterlogged=["true", "false"])
        self.loot(bid, "self")

    def fence(self, bid: str, en: str, it: Optional[str], texture: str) -> None:
        self.block_lang(bid, en, it)
        self.clone("oak_fence", [("minecraft:block/oak_fence", f"{MODID}:block/{bid}"),
                                 ("minecraft:block/oak_planks", texture)], bid,
                   extra_models=["minecraft:block/oak_fence_inventory"])
        self.block_item_parent(bid, f"{MODID}:block/{bid}_inventory")
        self.loot(bid, "self")

    def fence_gate(self, bid: str, en: str, it: Optional[str], texture: str) -> None:
        self.block_lang(bid, en, it)
        self.clone("oak_fence_gate", [("minecraft:block/oak_fence_gate", f"{MODID}:block/{bid}"),
                                      ("minecraft:block/oak_planks", texture)], bid)
        self.block_item_parent(bid)
        self.loot(bid, "self")

    def door(self, bid: str, en: str, it: Optional[str], render: str = "minecraft:cutout") -> None:
        self.block_lang(bid, en, it)
        self.clone("oak_door", [("minecraft:block/oak_door", f"{MODID}:block/{bid}")], bid, render_type=render)
        self.asset(f"models/item/{bid}", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{bid}"}})
        self.loot(bid, "door")

    def trapdoor(self, bid: str, en: str, it: Optional[str], render: str = "minecraft:cutout") -> None:
        self.block_lang(bid, en, it)
        self.clone("oak_trapdoor", [("minecraft:block/oak_trapdoor", f"{MODID}:block/{bid}")], bid, render_type=render)
        self.block_item_parent(bid, f"{MODID}:block/{bid}_bottom")
        self.loot(bid, "self")

    def pressure_plate(self, bid: str, en: str, it: Optional[str], texture: str) -> None:
        self.block_lang(bid, en, it)
        self.clone("oak_pressure_plate", [("minecraft:block/oak_pressure_plate", f"{MODID}:block/{bid}"),
                                          ("minecraft:block/oak_planks", texture)], bid)
        self.block_item_parent(bid)
        self.loot(bid, "self")

    def button(self, bid: str, en: str, it: Optional[str], texture: str) -> None:
        self.block_lang(bid, en, it)
        self.clone("oak_button", [("minecraft:block/oak_button", f"{MODID}:block/{bid}"),
                                  ("minecraft:block/oak_planks", texture)], bid,
                   extra_models=["minecraft:block/oak_button_inventory"])
        self.block_item_parent(bid, f"{MODID}:block/{bid}_inventory")
        self.loot(bid, "self")

    def pane(self, bid: str, en: str, it: Optional[str], pane_tex: str, edge_tex: str, render: str) -> None:
        self.block_lang(bid, en, it)
        self.clone("glass_pane", [("minecraft:block/glass_pane", f"{MODID}:block/{bid}"),
                                  ("minecraft:block/glass", pane_tex)], bid, render_type=render)
        # template replaced 'glass_pane_top' edge via first pair; fix the edge texture explicitly
        for suffix in ("post", "side", "side_alt", "noside", "noside_alt"):
            key = f"assets/{MODID}/models/block/{bid}_{suffix}.json"
            m = self.files[key]
            tex = dict(m.get("textures", {}))
            if "edge" in tex:
                tex["edge"] = edge_tex
            if "pane" in tex:
                tex["pane"] = pane_tex
            m = dict(m)
            m["textures"] = tex
            self.files[key] = m
        self.asset(f"models/item/{bid}", {"parent": "minecraft:item/generated", "textures": {"layer0": pane_tex}})
        self.props(bid, north=["true", "false"], east=["true", "false"], south=["true", "false"],
                   west=["true", "false"], waterlogged=["true", "false"])
        self.loot(bid, "self")

    def cross(self, bid: str, en: str, it: Optional[str], texture: Optional[str] = None, item_tex: Optional[str] = None,
              loot: Any = "self", facing: bool = False, tinted: bool = False) -> None:
        self.block_lang(bid, en, it)
        tex = texture or f"{MODID}:block/{bid}"
        self.asset(f"models/block/{bid}", {"parent": "minecraft:block/tinted_cross" if tinted else "minecraft:block/cross",
                                            "textures": {"cross": tex}, "render_type": "minecraft:cutout"})
        if facing:
            bs = json.load(open(os.path.join(TEMPLATES, "blockstates", "amethyst_cluster.json"), encoding="utf-8"))
            bs = json.loads(json.dumps(bs).replace("minecraft:block/amethyst_cluster", f"{MODID}:block/{bid}"))
            self.asset(f"blockstates/{bid}", bs)
            self.props(bid, facing=["north", "east", "south", "west", "up", "down"], waterlogged=["true", "false"])
        else:
            self.asset(f"blockstates/{bid}", {"variants": {"": {"model": f"{MODID}:block/{bid}"}}})
        self.asset(f"models/item/{bid}", {"parent": "minecraft:item/generated", "textures": {"layer0": item_tex or tex}})
        self.loot(bid, loot)

    def potted(self, bid: str, en: str, it: Optional[str], plant_tex: str, plant_item: str) -> None:
        self.block_lang(bid, en, it)
        self.asset(f"models/block/{bid}", {"parent": "minecraft:block/flower_pot_cross", "textures": {"plant": plant_tex},
                                            "render_type": "minecraft:cutout"})
        self.asset(f"blockstates/{bid}", {"variants": {"": {"model": f"{MODID}:block/{bid}"}}})
        self.data(f"loot_table/blocks/{bid}", loot_pot(bid, plant_item))

    def leaves(self, bid: str, en: str, it: Optional[str], loot: dict, render: str = "minecraft:cutout_mipped") -> None:
        self.block_lang(bid, en, it)
        self.asset(f"models/block/{bid}", {"parent": "minecraft:block/leaves", "textures": {"all": f"{MODID}:block/{bid}"},
                                            "render_type": render})
        self.asset(f"blockstates/{bid}", {"variants": {"": {"model": f"{MODID}:block/{bid}"}}})
        self.block_item_parent(bid)
        self.props(bid, distance=[str(i) for i in range(1, 8)], persistent=["true", "false"], waterlogged=["true", "false"])
        self.data(f"loot_table/blocks/{bid}", loot)

    # ------------------------------------------------------------------------------------- loot
    def loot(self, bid: str, kind: Any) -> None:
        if kind is None or kind == "none":
            return
        if kind == "self":
            self.data(f"loot_table/blocks/{bid}", loot_self(bid))
        elif kind == "slab":
            self.data(f"loot_table/blocks/{bid}", loot_slab(bid))
        elif kind == "door":
            self.data(f"loot_table/blocks/{bid}", loot_door(bid))
        elif isinstance(kind, dict):
            self.data(f"loot_table/blocks/{bid}", kind)
        else:
            raise ValueError(kind)

    # ------------------------------------------------------------------------------------- recipes
    def shaped(self, rid: str, pattern: List[str], key: Dict[str, str], result: str, count: int = 1,
               category: str = "misc", unlock: Optional[str] = None) -> None:
        data = {"type": "minecraft:crafting_shaped", "category": category,
                "key": {k: ingredient(v) for k, v in sorted(key.items())}, "pattern": pattern,
                "result": {"count": count, "id": ns(result)}}
        self.data(f"recipe/{rid}", data)
        self.unlock(rid, unlock or first_item(key), category)

    def shapeless(self, rid: str, ingredients: List[str], result: str, count: int = 1, category: str = "misc",
                  unlock: Optional[str] = None) -> None:
        data = {"type": "minecraft:crafting_shapeless", "category": category,
                "ingredients": [ingredient(i) for i in ingredients], "result": {"count": count, "id": ns(result)}}
        self.data(f"recipe/{rid}", data)
        self.unlock(rid, unlock or ingredients[0], category)

    def cooking(self, rid: str, kind: str, ingredient_id: str, result: str, xp: float, time: int, category: str = "misc") -> None:
        data = {"type": f"minecraft:{kind}", "category": category, "cookingtime": time, "experience": xp,
                "ingredient": ingredient(ingredient_id), "result": {"id": ns(result)}}
        self.data(f"recipe/{rid}", data)
        self.unlock(rid, ingredient_id, category)

    def smelt_and_blast(self, base: str, ingredient_id: str, result: str, xp: float) -> None:
        self.cooking(f"{base}_from_smelting", "smelting", ingredient_id, result, xp, 200)
        self.cooking(f"{base}_from_blasting", "blasting", ingredient_id, result, xp, 100)

    def storage(self, block: str, item: str, category: str = "building") -> None:
        self.shaped(strip(block), ["###", "###", "###"], {"#": item}, block, 1, category)
        self.shapeless(f"{strip(item)}_from_{strip(block)}", [block], item, 9, "misc")

    def unlock(self, rid: str, trigger_item: str, category: str) -> None:
        cat = {"building": "building_blocks", "equipment": "combat", "redstone": "redstone"}.get(category, category)
        crit_has = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [
            {"items": trigger_item} if trigger_item.startswith("#") else {"items": ns(trigger_item)}]}}
        data = {"parent": "minecraft:recipes/root",
                "criteria": {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": mod(rid)}},
                             "has_item": crit_has},
                "requirements": [["has_the_recipe", "has_item"]],
                "rewards": {"recipes": [mod(rid)]}}
        self.data(f"advancement/recipes/{cat}/{rid}", data)

    # ------------------------------------------------------------------------------------- output
    def write(self) -> None:
        for fn in self.finalizers:
            fn(self)
        for (kind, tag), values in sorted(self.tags.items()):
            tns, tpath = tag.split(":", 1)
            self.put(f"data/{tns}/tags/{kind}/{tpath}.json", {"values": values})
        self.put(f"assets/{MODID}/lang/en_us.json", dict(sorted(self.lang.items())))
        if self.lang_it:
            merged = dict(sorted(self.lang.items()))
            merged.update(self.lang_it)
            self.put(f"assets/{MODID}/lang/it_it.json", dict(sorted(merged.items())))
        clean_output()
        for rel, data in self.files.items():
            path = os.path.join(OUT, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8", newline="\n") as fh:
                json.dump(data, fh, indent=2, sort_keys=True, ensure_ascii=False)
                fh.write("\n")
        props_path = os.path.join(ROOT, "tools", "mod_block_props.json")
        with open(props_path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(dict(sorted(self.block_props.items())), fh, indent=1, sort_keys=True)
            fh.write("\n")
        print(f"gen_resources: wrote {len(self.files)} files ({len(self.blocks)} blocks, {len(self.items)} items, "
              f"{len(self.lang)} lang keys)")


# ----------------------------------------------------------------------------------------- helpers

def strip(rl: str) -> str:
    return rl.split(":", 1)[-1]


def ingredient(v: str) -> dict:
    if v.startswith("#"):
        return {"tag": v[1:]}
    return {"item": ns(v)}


def first_item(key: Dict[str, str]) -> str:
    for k in sorted(key):
        return key[k]
    raise ValueError("empty key")


def collect_models(obj: Any, acc: set) -> None:
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "model" and isinstance(v, str):
                acc.add(v)
            else:
                collect_models(v, acc)
    elif isinstance(obj, list):
        for v in obj:
            collect_models(v, acc)


SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
SURVIVES = {"condition": "minecraft:survives_explosion"}


def pool(entries: List[dict], rolls: float = 1.0, conditions: Optional[List[dict]] = None) -> dict:
    p = {"bonus_rolls": 0.0, "entries": entries, "rolls": rolls}
    if conditions:
        p["conditions"] = conditions
    return p


def loot_self(bid: str) -> dict:
    return {"type": "minecraft:block", "pools": [pool([{"type": "minecraft:item", "name": mod(bid)}], conditions=[SURVIVES])],
            "random_sequence": mod(f"blocks/{bid}")}


def loot_slab(bid: str) -> dict:
    return {"type": "minecraft:block", "pools": [pool([{"type": "minecraft:item", "name": mod(bid), "functions": [
        {"function": "minecraft:set_count", "add": False, "count": 2.0, "conditions": [
            {"condition": "minecraft:block_state_property", "block": mod(bid), "properties": {"type": "double"}}]},
        {"function": "minecraft:explosion_decay"}]}])], "random_sequence": mod(f"blocks/{bid}")}


def loot_door(bid: str) -> dict:
    return {"type": "minecraft:block", "pools": [pool([{"type": "minecraft:item", "name": mod(bid), "conditions": [
        {"condition": "minecraft:block_state_property", "block": mod(bid), "properties": {"half": "lower"}}]}],
        conditions=[SURVIVES])], "random_sequence": mod(f"blocks/{bid}")}


def loot_pot(bid: str, plant: str) -> dict:
    return {"type": "minecraft:block", "pools": [
        pool([{"type": "minecraft:item", "name": "minecraft:flower_pot"}], conditions=[SURVIVES]),
        pool([{"type": "minecraft:item", "name": ns(plant)}], conditions=[SURVIVES])],
        "random_sequence": mod(f"blocks/{bid}")}


def loot_ore(bid: str, drop: str, lo: float = 1.0, hi: float = 1.0, fortune: str = "ore_drops") -> dict:
    fns: List[dict] = []
    if (lo, hi) != (1.0, 1.0):
        fns.append({"function": "minecraft:set_count", "add": False,
                    "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if fortune == "ore_drops":
        fns.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"})
    elif fortune == "uniform":
        fns.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                    "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}})
    fns.append({"function": "minecraft:explosion_decay"})
    return {"type": "minecraft:block", "pools": [pool([{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "conditions": [SILK], "name": mod(bid)},
        {"type": "minecraft:item", "functions": fns, "name": ns(drop)}]}])], "random_sequence": mod(f"blocks/{bid}")}


def loot_silk_only(bid: str) -> dict:
    return {"type": "minecraft:block", "pools": [pool([{"type": "minecraft:item", "name": mod(bid)}], conditions=[SILK])],
            "random_sequence": mod(f"blocks/{bid}")}


def leaves_loot(bid: str, sapling: Optional[str], extras: Sequence[Tuple[str, float]] = (), sticks: bool = True) -> dict:
    """Vanilla-like leaves: silk/shears drop leaves, else sapling 5% (+fortune), sticks 2%, extra drops."""
    shears_or_silk = {"condition": "minecraft:any_of", "terms": [
        {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}, SILK]}
    no_shears = {"condition": "minecraft:inverted", "term": shears_or_silk}
    children = [{"type": "minecraft:item", "conditions": [shears_or_silk], "name": mod(bid)}]
    if sapling:
        children.append({"type": "minecraft:item", "name": ns(sapling), "conditions": [
            SURVIVES, {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                       "chances": [0.05, 0.0625, 0.083333336, 0.1]}]})
    pools = [pool([{"type": "minecraft:alternatives", "children": children}])]
    if sticks:
        pools.append(pool([{"type": "minecraft:item", "name": "minecraft:stick", "functions": [
            {"function": "minecraft:set_count", "add": False, "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}},
            {"function": "minecraft:explosion_decay"}], "conditions": [
            {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
             "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]}]}], conditions=[no_shears]))
    for item, chance in extras:
        pools.append(pool([{"type": "minecraft:item", "name": ns(item), "conditions": [
            SURVIVES, {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                       "chances": [chance, chance * 1.25, chance * 1.6, chance * 2.0]}]}], conditions=[no_shears]))
    return {"type": "minecraft:block", "pools": pools, "random_sequence": mod(f"blocks/{bid}")}


def clean_output() -> None:
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
        return
    keep_abs = [os.path.join(OUT, k) for k in KEEP]
    for dirpath, dirnames, filenames in os.walk(OUT, topdown=False):
        if any(dirpath == k or dirpath.startswith(k + os.sep) for k in keep_abs):
            continue
        for f in filenames:
            os.remove(os.path.join(dirpath, f))
        if dirpath != OUT and not os.listdir(dirpath):
            os.rmdir(dirpath)


def main() -> int:
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    g = Gen()
    for name in CONTENT_MODULES:
        path = os.path.join(ROOT, "tools", "content", name + ".py")
        if not os.path.exists(path):
            continue
        module = importlib.import_module(f"content.{name}")
        module.build(g)
    g.write()
    return 0


if __name__ == "__main__":
    sys.exit(main())
