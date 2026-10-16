#!/usr/bin/env python3
"""Lacuna asset/data validator (spec section 4, gate 3).

Checks, without needing Minecraft:
  * every JSON file parses;
  * every id registered in Java (registry/*.java) has a lang key, and every block has a blockstate and an item model;
  * blockstate -> model -> parent/texture chains resolve (mod files or vanilla 1.21.1 index);
  * loot tables / recipes / tags / worldgen reference existing ids (mod registry, mod data files or vanilla);
  * no 'missingno' references; textures are RGBA PNGs of 16x16 or a documented size;
  * structure NBT palettes use known blocks/properties (when nbtlib is available).
Exit code 0 = pass.
"""
from __future__ import annotations

import glob
import gzip
import json
import os
import re
import sys
from typing import Dict, Iterable, List, Set

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIRS = [os.path.join(ROOT, "src", "main", "resources"), os.path.join(ROOT, "src", "generated", "resources")]
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "lacuna", "mod")
MODID = "lacuna"

VANILLA = json.load(open(os.path.join(ROOT, "tools", "vanilla_1.21.1.json"), encoding="utf-8"))

# Documented non-16x16 texture sizes (spec section 20)
SIZE_RULES = [
    (re.compile(r"^textures/models/armor/"), {(64, 32)}),
    (re.compile(r"^textures/painting/"), {(32, 32), (48, 32), (64, 32), (64, 64), (32, 16), (16, 16), (48, 48)}),
    (re.compile(r"^textures/entity/"), {(64, 32), (64, 64), (128, 64), (32, 32)}),
    (re.compile(r"^textures/mob_effect/"), {(18, 18)}),
    (re.compile(r"^textures/gui/"), None),
    # Revision 2 effects: particle sprites and render-effect masks
    (re.compile(r"^textures/particle/"), {(8, 8), (32, 32)}),
    (re.compile(r"^textures/effect/"), {(32, 32), (64, 64), (16, 64), (32, 128)}),
    # shader-sampled environment layers (the Forgotten Portal's screen-space fog and motes, like vanilla end_sky)
    (re.compile(r"^textures/environment/"), {(128, 128), (256, 256)}),
    # hand-drawn high-resolution items/blocks (tools/art/hand), e.g. the 32x32 Gravemaw Greatsword
    (re.compile(r"^textures/(item|block)/"), {(32, 32), (24, 24)}),
]
ANIMATED_OK = True  # textures with .mcmeta may be 16 x (16*frames)

errors: List[str] = []
warnings: List[str] = []


def err(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


def rl(s: str) -> (str, str):
    if ":" in s:
        ns, p = s.split(":", 1)
        return ns, p
    return "minecraft", s


# ------------------------------------------------------------------------------------------- load files

def all_files(sub: str, ext: str) -> Dict[str, str]:
    """Map 'namespace:path' -> absolute file, for assets/<ns>/<sub>/**.ext or data/<ns>/<sub>/**.ext."""
    out: Dict[str, str] = {}
    for base in RES_DIRS:
        for kind in ("assets", "data"):
            for ns_dir in glob.glob(os.path.join(base, kind, "*")):
                ns = os.path.basename(ns_dir)
                root = os.path.join(ns_dir, sub)
                if not os.path.isdir(root):
                    continue
                for f in glob.glob(os.path.join(root, "**", "*" + ext), recursive=True):
                    rel = os.path.relpath(f, root)[: -len(ext)].replace(os.sep, "/")
                    out[f"{ns}:{rel}"] = f
    return out


def load_json(path: str):
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except Exception as e:  # noqa: BLE001
        err(f"invalid JSON {os.path.relpath(path, ROOT)}: {e}")
        return None


def check_all_json() -> None:
    for base in RES_DIRS:
        for f in glob.glob(os.path.join(base, "**", "*.json"), recursive=True):
            load_json(f)
        for f in glob.glob(os.path.join(base, "**", "*.mcmeta"), recursive=True):
            load_json(f)


# ------------------------------------------------------------------------------------------- java ids

REG_PATTERN = re.compile(r"\b(?:block|blockNoItem|item|entity|effect|sound|tab|blockEntity|profession|poi|feature|attachment|component|register\w*)\(\s*\"([a-z0-9_/]+)\"")


def java_ids() -> Dict[str, Set[str]]:
    kinds = {
        "ModBlocks.java": "block",
        "ModWoodBlocks.java": "block",
        "ModItems.java": "item",
        "ModEntities.java": "entity",
        "ModEffects.java": "effect",
        "ModBlockEntities.java": "block_entity",
        "ModVillagers.java": "profession",
        "ModFeatures.java": "feature",
    }
    out: Dict[str, Set[str]] = {v: set() for v in kinds.values()}
    out["block_item"] = set()
    out.setdefault("poi", set())
    reg = os.path.join(JAVA, "registry")
    for fname, kind in kinds.items():
        path = os.path.join(reg, fname)
        if not os.path.exists(path):
            continue
        src = open(path, encoding="utf-8").read()
        # strip comments
        src = re.sub(r"//[^\n]*", "", src)
        src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
        if kind == "block":
            for m in re.finditer(r"\b(block|blockNoItem)\(\s*\"([a-z0-9_]+)\"", src):
                out["block"].add(m.group(2))
                if m.group(1) == "block":
                    out["block_item"].add(m.group(2))
        elif kind == "profession":
            for m in re.finditer(r"\bprofession\(\s*\"([a-z0-9_]+)\"", src):
                out["profession"].add(m.group(1))
            for m in re.finditer(r"\bpoi\(\s*\"([a-z0-9_]+)\"", src):
                out.setdefault("poi", set()).add(m.group(1))
        else:
            for m in REG_PATTERN.finditer(src):
                out[kind].add(m.group(1))
    return out


# ------------------------------------------------------------------------------------------- checks

def texture_exists(ref: str, textures: Dict[str, str]) -> bool:
    ns, p = rl(ref)
    if ns == "minecraft":
        return p in set(VANILLA["texture"]) or p.startswith("entity/") or p.startswith("item/") and p in set(VANILLA["texture"])
    return f"{ns}:{p}" in textures


def check_textures(textures: Dict[str, str]) -> None:
    for key, path in textures.items():
        ns, rel = rl(key)
        if ns != MODID:
            continue
        try:
            im = Image.open(path)
            im.load()
        except Exception as e:  # noqa: BLE001
            err(f"unreadable PNG {key}: {e}")
            continue
        if im.mode != "RGBA":
            err(f"texture {key} must be RGBA (is {im.mode})")
        size = im.size
        relp = "textures/" + rel
        allowed = {(16, 16)}
        documented = False
        for pat, sizes in SIZE_RULES:
            if pat.search(relp):
                documented = True
                if sizes is None:
                    allowed = None
                else:
                    allowed = sizes | {(16, 16)}
                break
        if os.path.exists(path + ".mcmeta"):
            if size[0] == 16 and size[1] % 16 == 0:
                continue
            err(f"animated texture {key} must be 16 x 16n, is {size}")
            continue
        if allowed is not None and size not in allowed:
            err(f"texture {key} has size {size}, allowed {sorted(allowed)}{' (documented)' if documented else ''}")


def resolve_model(ref: str, models: Dict[str, str], textures: Dict[str, str], seen: Set[str], ctx: str) -> None:
    ns, p = rl(ref)
    key = f"{ns}:{p}"
    if key in seen:
        return
    seen.add(key)
    if ns == "minecraft":
        if p.startswith("builtin/"):
            return
        if p not in set(VANILLA["model"]):
            err(f"{ctx}: unknown vanilla model {key}")
        return
    if key not in models:
        if ns == "neoforge":
            return
        err(f"{ctx}: missing model {key}")
        return
    data = load_json(models[key])
    if not isinstance(data, dict):
        return
    if "parent" in data:
        resolve_model(data["parent"], models, textures, seen, f"model {key}")
    for tkey, tval in (data.get("textures") or {}).items():
        if not isinstance(tval, str) or tval.startswith("#"):
            continue
        if "missingno" in tval:
            err(f"model {key} references missingno")
        if not texture_exists(tval, textures):
            err(f"model {key}: texture '{tkey}' -> {tval} does not exist")
    for ov in data.get("overrides") or []:
        resolve_model(ov.get("model", ""), models, textures, seen, f"override in {key}")
    if data.get("loader") == "neoforge:composite":
        for child in (data.get("children") or {}).values():
            if isinstance(child, dict) and "parent" in child:
                resolve_model(child["parent"], models, textures, seen, f"composite {key}")


def collect_variant_models(bs: dict) -> Iterable[str]:
    def models_of(v):
        if isinstance(v, list):
            for e in v:
                yield from models_of(e)
        elif isinstance(v, dict) and "model" in v:
            yield v["model"]
    for v in (bs.get("variants") or {}).values():
        yield from models_of(v)
    for part in bs.get("multipart") or []:
        yield from models_of(part.get("apply"))


def item_known(i: str, ids) -> bool:
    ns, p = rl(i)
    if ns == "minecraft":
        return p in set(VANILLA["item"])
    if ns == MODID:
        return p in ids["item"] or p in ids["block_item"]
    return ns in ("c", "neoforge")  # tolerated external


def block_known(b: str, ids) -> bool:
    ns, p = rl(b)
    if ns == "minecraft":
        return p in set(VANILLA["block"])
    if ns == MODID:
        return p in ids["block"]
    return False


def entity_known(e: str, ids) -> bool:
    ns, p = rl(e)
    if ns == "minecraft":
        return p in set(VANILLA["entity_type"])
    if ns == MODID:
        return p in ids["entity"]
    return False


def tag_known(kind: str, t: str, tags: Dict[str, str]) -> bool:
    ns, p = rl(t)
    vanilla_key = {"item": "tag/item", "block": "tag/block", "entity_type": "tag/entity_type",
                   "worldgen/biome": "tag/worldgen/biome", "worldgen/structure": "tag/worldgen/structure",
                   "damage_type": "tag/damage_type", "fluid": "tag/fluid", "painting_variant": "tag/painting_variant",
                   "point_of_interest_type": "tag/point_of_interest_type"}.get(kind)
    if ns == "minecraft" and vanilla_key and p in set(VANILLA.get(vanilla_key, [])):
        return True
    if f"{ns}:{p}" in tags:
        return True
    if ns == "c" and p in set(VANILLA.get("c_tags", {}).get(kind, [])):
        return True  # common tags provided by NeoForge 21.1
    return False


def walk_ids(obj, out: List[tuple], path: str = "") -> None:
    if isinstance(obj, dict):
        for k, v in obj.items():
            walk_ids(v, out, path + "/" + k)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            walk_ids(v, out, path + f"[{i}]")
    elif isinstance(obj, str):
        out.append((path, obj))


def check_lang(ids, lang: dict) -> None:
    for kind, prefix in (("item", "item"), ("block", "block"), ("entity", "entity"), ("effect", "effect")):
        for i in sorted(ids.get(kind, ())):
            key = f"{prefix}.{MODID}.{i}"
            if key not in lang:
                err(f"missing lang key {key}")
    if "itemGroup.lacuna" not in lang:
        err("missing lang key itemGroup.lacuna")
    for prof in sorted(ids.get("profession", ())):
        if f"entity.minecraft.villager.{MODID}.{prof}" not in lang:
            err(f"missing lang key entity.minecraft.villager.{MODID}.{prof}")
        for kind in ("villager", "zombie_villager"):
            tex = os.path.join(ROOT, "src", "main", "resources", "assets", MODID, "textures", "entity", kind, "profession", prof + ".png")
            if not os.path.isfile(tex):
                err(f"profession {prof}: missing texture entity/{kind}/profession/{prof}.png")


def check_file_names() -> None:
    """Resource paths may only contain [a-z0-9_./-]; anything else is silently ignored by the game."""
    for base in (os.path.join(ROOT, "src", "generated", "resources"), os.path.join(ROOT, "src", "main", "resources")):
        for dirpath, _, files in os.walk(base):
            for f in files:
                rel = os.path.relpath(os.path.join(dirpath, f), base).replace(os.sep, "/")
                if rel.startswith(("assets/", "data/")) and not re.fullmatch(r"[a-z0-9_./-]+", rel):
                    err(f"invalid resource path {rel}")


def check_java_texture_refs() -> None:
    """Renderers reference textures by path in Java (Lacuna.id("textures/...png")); each must exist."""
    pat = re.compile(r'id\("(textures/[a-z0-9_/]+\.png)"\)')
    for dirpath, _, files in os.walk(os.path.join(ROOT, "src", "main", "java")):
        for f in files:
            if not f.endswith(".java"):
                continue
            with open(os.path.join(dirpath, f), encoding="utf-8") as fh:
                for ref in pat.findall(fh.read()):
                    if not os.path.isfile(os.path.join(ROOT, "src", "main", "resources", "assets", MODID, ref)):
                        err(f"{f}: referenced texture {ref} does not exist")


def main() -> int:
    check_all_json()
    check_java_texture_refs()
    check_file_names()
    ids = java_ids()
    textures = all_files("textures", ".png")
    models = all_files("models", ".json")
    blockstates = all_files("blockstates", ".json")
    check_textures(textures)

    # lang
    lang_files = [f for f in all_files("lang", ".json").items() if f[0] == f"{MODID}:en_us"]
    lang = load_json(lang_files[0][1]) if lang_files else {}
    if not lang_files:
        err("missing assets/lacuna/lang/en_us.json")
    check_lang(ids, lang or {})

    # blockstates and models
    seen: Set[str] = set()
    for b in sorted(ids["block"]):
        key = f"{MODID}:{b}"
        if key not in blockstates:
            err(f"block {b} has no blockstate")
            continue
        bs = load_json(blockstates[key])
        if isinstance(bs, dict):
            for m in collect_variant_models(bs):
                resolve_model(m, models, textures, seen, f"blockstate {b}")
    item_like = ids["item"] | ids["block_item"]
    for i in sorted(item_like):
        key = f"{MODID}:item/{i}"
        if key not in models:
            err(f"item {i} has no item model")
            continue
        resolve_model(key, models, textures, seen, f"item {i}")
    for bs_key in blockstates:
        ns, p = rl(bs_key)
        if ns == MODID and p not in ids["block"]:
            warn(f"stray blockstate {bs_key} (no registered block)")

    # tags
    tag_files: Dict[str, Dict[str, str]] = {}
    for kind in ("item", "block", "entity_type", "worldgen/biome", "worldgen/structure", "damage_type", "fluid",
                 "painting_variant", "point_of_interest_type"):
        tag_files[kind] = all_files(f"tags/{kind}", ".json")
    for kind, files in tag_files.items():
        for key, path in files.items():
            data = load_json(path)
            if not isinstance(data, dict):
                continue
            for v in data.get("values", []):
                entry = v["id"] if isinstance(v, dict) else v
                required = not (isinstance(v, dict) and v.get("required") is False)
                if entry.startswith("#"):
                    if not tag_known(kind, entry[1:], files) and required:
                        err(f"tag {kind} {key}: unknown tag {entry}")
                    continue
                ok = True
                if kind == "item":
                    ok = item_known(entry, ids)
                elif kind == "block":
                    ok = block_known(entry, ids)
                elif kind == "entity_type":
                    ok = entity_known(entry, ids)
                if not ok and required:
                    err(f"tag {kind} {key}: unknown entry {entry}")

    # recipes / loot tables: check item and block ids
    data_files = []
    for sub in ("recipe", "loot_table", "advancement"):
        data_files += [(sub, k, p) for k, p in all_files(sub, ".json").items()]
    for sub, key, path in data_files:
        data = load_json(path)
        if data is None:
            continue
        strings: List[tuple] = []
        walk_ids(data, strings)
        for jpath, s in strings:
            last = jpath.rsplit("/", 1)[-1]
            if "missingno" in s:
                err(f"{sub} {key} references missingno")
            if last in ("item", "id", "name") and ":" in s and not s.startswith("#"):
                if "/function" in jpath or "/condition" in jpath and last != "item":
                    continue
                if last == "name" and ("/entries" not in jpath):
                    continue
                if "/criteria" in jpath and last != "items":
                    pass
                if last == "name" and jpath.endswith("/name") and "/entries" in jpath:
                    # loot entries: item or loot_table reference
                    if s.startswith("minecraft:chests/") or "/loot_table" in s:
                        continue
                if not item_known(s, ids):
                    # might be a block id (block loot 'block' conditions) or loot table reference
                    if block_known(s, ids) or entity_known(s, ids):
                        continue
                    err(f"{sub} {key}: unknown id {s} at {jpath}")
            if last == "tag" and not s.startswith("#"):
                if not tag_known("item", s, tag_files["item"]) and not tag_known("block", s, tag_files["block"]):
                    err(f"{sub} {key}: unknown tag {s} at {jpath}")
            if last == "block" and ":" in s:
                if not block_known(s, ids):
                    err(f"{sub} {key}: unknown block {s} at {jpath}")

    # worldgen / structure references (template pools -> structure files)
    structures = all_files("structure", ".nbt")
    pools = all_files("worldgen/template_pool", ".json")
    for key, path in pools.items():
        data = load_json(path)
        if not isinstance(data, dict):
            continue
        fallback = data.get("fallback")
        if fallback and fallback != "minecraft:empty" and fallback not in pools:
            err(f"template pool {key}: unknown fallback {fallback}")
        for el in data.get("elements", []):
            e = el.get("element", {})
            loc = e.get("location")
            if loc and rl(loc)[0] == MODID and f"{rl(loc)[0]}:{rl(loc)[1]}" not in structures:
                err(f"template pool {key}: missing structure file {loc}")
    check_jigsaws(structures, pools, ids)
    for key, path in all_files("worldgen/structure", ".json").items():
        data = load_json(path)
        if isinstance(data, dict) and data.get("start_pool"):
            sp = data["start_pool"]
            if rl(sp)[0] == MODID and sp not in pools:
                err(f"structure {key}: unknown start_pool {sp}")

    check_worldgen(ids, tag_files)

    # lang values sanity
    for k, v in (lang or {}).items():
        if not isinstance(v, str) or not v.strip():
            err(f"lang key {k} has empty value")

    # structure NBT palettes
    try:
        import nbtlib  # noqa: F401
        check_structures(structures, ids)
    except ImportError:
        warn("nbtlib not installed: structure palettes not validated (pip install nbtlib)")

    for w in warnings:
        print("WARN ", w)
    for e in errors:
        print("ERROR", e)
    print(f"validate_assets: {len(errors)} error(s), {len(warnings)} warning(s); "
          f"{len(ids['block'])} blocks, {len(ids['item'])} items, {len(ids['entity'])} entities, "
          f"{len(textures)} textures, {len(models)} models")
    return 1 if errors else 0


def exists(kind: str, ref: str) -> bool:
    """kind is a data folder such as 'worldgen/placed_feature'; vanilla ids come from the 1.21.1 index."""
    n, p = rl(ref)
    if n == "minecraft":
        return p in set(VANILLA.get(kind, []))
    return f"{n}:{p}" in all_files(kind, ".json")


def check_worldgen(ids, tag_files) -> None:
    for key, path in all_files("dimension", ".json").items():
        if rl(key)[0] != MODID:
            continue
        d = load_json(path) or {}
        if not exists("dimension_type", d.get("type", "")):
            err(f"dimension {key}: unknown dimension_type {d.get('type')}")
        gen = d.get("generator", {})
        if gen.get("settings") and not exists("worldgen/noise_settings", gen["settings"]):
            err(f"dimension {key}: unknown noise settings {gen['settings']}")
        for b in gen.get("biome_source", {}).get("biomes", []):
            if not exists("worldgen/biome", b["biome"]):
                err(f"dimension {key}: unknown biome {b['biome']}")
    for key, path in all_files("worldgen/biome", ".json").items():
        d = load_json(path) or {}
        for step in d.get("features", []):
            for f in step:
                if not exists("worldgen/placed_feature", f):
                    err(f"biome {key}: unknown placed feature {f}")
        for group in d.get("spawners", {}).values():
            for sp in group:
                if not entity_known(sp["type"], ids):
                    err(f"biome {key}: unknown spawn entity {sp['type']}")
        eff = d.get("effects", {})
        for snd in (eff.get("additions_sound", {}).get("sound"), eff.get("mood_sound", {}).get("sound"), eff.get("ambient_sound")):
            if isinstance(snd, str) and snd.startswith("minecraft:") and snd[10:] not in VANILLA.get("sound_event", []):
                err(f"biome {key}: unknown sound event {snd}")
        if len(d.get("features", [])) != 11:
            err(f"biome {key}: features must have 11 decoration steps")
    for key, path in all_files("worldgen/placed_feature", ".json").items():
        d = load_json(path) or {}
        f = d.get("feature")
        if isinstance(f, str) and not exists("worldgen/configured_feature", f):
            err(f"placed feature {key}: unknown configured feature {f}")
        for mod_ in d.get("placement", []):
            t = mod_.get("type", "")
            if t.split(":", 1)[-1] not in VANILLA.get("worldgen/placement_modifier_type", []):
                err(f"placed feature {key}: unknown placement modifier {t}")
    for key, path in all_files("worldgen/configured_feature", ".json").items():
        d = load_json(path) or {}
        n, p_ = rl(d.get("type", ""))
        known = p_ in ids.get("feature", set()) if n == MODID else (n == "minecraft" and p_ in VANILLA.get("worldgen/feature", []))
        if not known:
            err(f"configured feature {key}: unknown feature type {d.get('type')}")
    for key, path in all_files("worldgen/structure_set", ".json").items():
        d = load_json(path) or {}
        for st in d.get("structures", []):
            if not exists("worldgen/structure", st["structure"]):
                err(f"structure set {key}: unknown structure {st['structure']}")
    for key, path in all_files("worldgen/structure", ".json").items():
        d = load_json(path) or {}
        if d.get("type") == "minecraft:jigsaw":
            pad = 12 if d.get("terrain_adaptation", "none") != "none" else 0
            if d.get("max_distance_from_center", 80) + pad > 128:
                err(f"structure {key}: max_distance_from_center + terrain adaptation padding exceeds 128")
        b = d.get("biomes", "")
        if isinstance(b, str) and b.startswith("#") and not tag_known("worldgen/biome", b[1:], tag_files["worldgen/biome"]):
            err(f"structure {key}: unknown biome tag {b}")
    for key, path in all_files("neoforge/biome_modifier", ".json").items():
        d = load_json(path) or {}
        feats = d.get("features")
        for f in ([feats] if isinstance(feats, str) else feats or []):
            if not exists("worldgen/placed_feature", f):
                err(f"biome modifier {key}: unknown placed feature {f}")
        b = d.get("biomes")
        if isinstance(b, str) and b.startswith("#") and not tag_known("worldgen/biome", b[1:], tag_files["worldgen/biome"]):
            err(f"biome modifier {key}: unknown biome tag {b}")
        for sp in d.get("spawners", []) if isinstance(d.get("spawners"), list) else ([d["spawners"]] if "spawners" in d else []):
            if not entity_known(sp["type"], ids):
                err(f"biome modifier {key}: unknown entity {sp['type']}")
    for key, path in all_files("painting_variant", ".json").items():
        d = load_json(path) or {}
        a = d.get("asset_id", "")
        n, p = rl(a)
        if n == MODID and f"{n}:painting/{p}" not in all_files("textures", ".png"):
            err(f"painting {key}: missing texture painting/{p}")
        tex = all_files("textures", ".png").get(f"{n}:painting/{p}")
        if tex:
            im = Image.open(tex)
            if im.size != (16 * d.get("width", 1), 16 * d.get("height", 1)):
                err(f"painting {key}: texture size {im.size} does not match {d.get('width')}x{d.get('height')} blocks")


def check_structures(structures: Dict[str, str], ids) -> None:
    import nbtlib
    states = VANILLA["block_states"]
    mod_props = load_mod_block_props()
    for key, path in structures.items():
        if rl(key)[0] != MODID:
            continue
        try:
            nbt = nbtlib.load(path)
        except Exception as e:  # noqa: BLE001
            err(f"structure {key}: unreadable NBT ({e})")
            continue
        root = nbt
        if "DataVersion" not in root:
            err(f"structure {key}: missing DataVersion")
        elif int(root["DataVersion"]) != 3955:
            err(f"structure {key}: DataVersion {int(root['DataVersion'])} != 3955 (1.21.1)")
        palette = root.get("palette", [])
        for entry in palette:
            name = str(entry["Name"])
            ns, p = rl(name)
            props = {str(k): str(v) for k, v in entry.get("Properties", {}).items()}
            if ns == "minecraft":
                if p not in states:
                    err(f"structure {key}: unknown block {name}")
                    continue
                allowed = states[p]["props"]
                for pk, pv in props.items():
                    if pk not in allowed or pv not in allowed[pk]:
                        err(f"structure {key}: block {name} has invalid property {pk}={pv}")
            elif ns == MODID:
                if p not in ids["block"]:
                    err(f"structure {key}: unknown mod block {name}")
                    continue
                allowed = mod_props.get(p)
                if allowed is not None:
                    for pk, pv in props.items():
                        if pk not in allowed or pv not in allowed[pk]:
                            err(f"structure {key}: block {name} has invalid property {pk}={pv}")
            else:
                err(f"structure {key}: foreign block {name}")
        loot_tables = all_files("loot_table", ".json")
        n = len(palette)
        for b in root.get("blocks", []):
            if "nbt" in b and "LootTable" in b["nbt"]:
                lt = str(b["nbt"]["LootTable"])
                ln, lp = rl(lt)
                if ln == MODID and f"{ln}:{lp}" not in loot_tables:
                    err(f"structure {key}: chest loot table {lt} does not exist")
            st = int(b["state"])
            if st < 0 or st >= n:
                err(f"structure {key}: block state index {st} out of palette range {n}")
                break


def check_jigsaws(structures: Dict[str, str], pools: Dict[str, str], ids) -> None:
    """Every jigsaw block must name an existing pool, whose pieces must offer a connector named like its target."""
    import nbtlib
    connectors: Dict[str, List[dict]] = {}
    for key, path in structures.items():
        if rl(key)[0] != MODID:
            continue
        try:
            root = nbtlib.load(path)
        except Exception:  # noqa: BLE001  (reported by check_structures)
            continue
        found = []
        for b in root.get("blocks", []):
            nbt = b.get("nbt")
            if nbt is not None and str(nbt.get("id", "")) == "minecraft:jigsaw":
                found.append({k: str(nbt.get(k, "")) for k in ("name", "target", "pool", "final_state")})
        connectors[key] = found
    pool_names: Dict[str, Set[str]] = {}
    for pkey, ppath in pools.items():
        names: Set[str] = set()
        for el in (load_json(ppath) or {}).get("elements", []):
            loc = el.get("element", {}).get("location")
            if loc:
                names |= {c["name"] for c in connectors.get(f"{rl(loc)[0]}:{rl(loc)[1]}", [])}
        pool_names[pkey] = names
    for key, found in connectors.items():
        for j in found:
            pool = j["pool"]
            if pool != "minecraft:empty":
                pns, ppath = rl(pool)
                if pns == "minecraft" and pool not in pools:
                    # vanilla pool (e.g. minecraft:village/plains/villagers for the civic buildings' villagers)
                    if ppath not in VANILLA.get("worldgen/template_pool", []):
                        err(f"structure {key}: vanilla jigsaw pool {pool} does not exist")
                    elif ppath.endswith("/villagers") and j["target"] != "minecraft:bottom":
                        err(f"structure {key}: vanilla villager pools only offer minecraft:bottom, not {j['target']}")
                elif pool not in pools:
                    err(f"structure {key}: jigsaw pool {pool} does not exist")
                elif j["target"] not in pool_names.get(pool, set()):
                    err(f"structure {key}: no piece in {pool} has a connector named {j['target']}")
            fs = j["final_state"].split("[", 1)[0]
            if fs and not block_known(fs, ids):
                err(f"structure {key}: jigsaw final_state {j['final_state']} is not a known block")


def load_mod_block_props() -> Dict[str, Dict[str, List[str]]]:
    """Reads the property table written by gen_resources.py (block id -> property -> allowed values)."""
    path = os.path.join(ROOT, "tools", "mod_block_props.json")
    if os.path.exists(path):
        return json.load(open(path, encoding="utf-8"))
    return {}


if __name__ == "__main__":
    sys.exit(main())
