"""Worldgen JSON builders shared by the Forgotten Land milestones (M2, M10)."""
from typing import Any, Dict, List, Optional


def rgb(hexstr: str) -> int:
    return int(hexstr.lstrip("#"), 16)


FL_FOG = rgb("#8C9DB0")
FL_SKY = rgb("#7F90A6")
FL_WATER = rgb("#5E7F9C")
FL_WATER_FOG = rgb("#4A667F")

EMPTY_SPAWNERS = {k: [] for k in ("monster", "creature", "ambient", "axolotls", "underground_water_creature",
                                  "water_creature", "water_ambient", "misc")}


def biome(temperature: float, downfall: float, features: List[List[str]], spawners: Optional[Dict[str, list]] = None,
          grass: str = "#7F8F86", foliage: str = "#78887F", particle: Optional[str] = None, particle_p: float = 0.0,
          fog: int = FL_FOG, sky: int = FL_SKY, water: int = FL_WATER, water_fog: int = FL_WATER_FOG,
          precipitation: bool = False, additions: str = "minecraft:ambient.soul_sand_valley.additions",
          mood: str = "minecraft:ambient.cave") -> dict:
    effects: Dict[str, Any] = {
        "fog_color": fog, "sky_color": sky, "water_color": water, "water_fog_color": water_fog,
        "grass_color": rgb(grass), "foliage_color": rgb(foliage),
        "mood_sound": {"block_search_extent": 8, "offset": 2.0, "sound": mood, "tick_delay": 6000},
        "additions_sound": {"sound": additions, "tick_chance": 0.0011},
    }
    if particle:
        effects["particle"] = {"options": {"type": particle}, "probability": particle_p}
    steps = [[] for _ in range(11)]
    for i, step in enumerate(features[:11]):
        steps[i] = list(step)
    sp = dict(EMPTY_SPAWNERS)
    if spawners:
        sp.update(spawners)
    return {"has_precipitation": precipitation, "temperature": temperature, "downfall": downfall, "effects": effects,
            "spawners": sp, "spawn_costs": {}, "carvers": {"air": []}, "features": steps}


# Decoration step indices (GenerationStep.Decoration)
RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES, SURFACE_STRUCTURES, STRONGHOLDS, \
    UNDERGROUND_ORES, UNDERGROUND_DECORATION, FLUID_SPRINGS, VEGETAL_DECORATION, TOP_LAYER_MODIFICATION = range(11)


def block(name: str, props: Optional[Dict[str, str]] = None) -> dict:
    d: Dict[str, Any] = {"type": "minecraft:block", "result_state": {"Name": name}}
    if props:
        d["result_state"]["Properties"] = props
    return d


def cond(if_true: dict, then_run: dict) -> dict:
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then_run}


def seq(*rules: dict) -> dict:
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def on_floor(add_surface_depth: bool = False) -> dict:
    return {"type": "minecraft:stone_depth", "add_surface_depth": add_surface_depth, "offset": 0,
            "secondary_depth_range": 0, "surface_type": "floor"}


def above_water(offset: int = -1) -> dict:
    return {"type": "minecraft:water", "add_stone_depth": False, "offset": offset, "surface_depth_multiplier": 0}


def in_biomes(*names: str) -> dict:
    return {"type": "minecraft:biome", "biome_is": list(names)}


def default_surface(top: dict, under: dict, underwater: dict) -> dict:
    return seq(
        cond(on_floor(), seq(cond(above_water(), top), underwater)),
        cond(on_floor(True), under),
    )


def fl_surface_rule(biome_rules: List[dict]) -> dict:
    """Bedrock floor, per-biome surface rules (above the preliminary surface), deepslate below Y 0..8."""
    plains = default_surface(block("minecraft:grass_block", {"snowy": "false"}), block("minecraft:dirt"),
                             block("minecraft:gravel"))
    return seq(
        cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
              "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
             block("minecraft:bedrock")),
        cond({"type": "minecraft:above_preliminary_surface"}, seq(*biome_rules, plains)),
        cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:deepslate",
              "true_at_and_below": {"absolute": 0}, "false_at_and_above": {"absolute": 8}},
             block("minecraft:deepslate", {"axis": "y"})),
    )


def fl_density() -> dict:
    """Gentle low-frequency hills: surface between Y 62 and 80 (zero crossing at 71, +-9 from clamped noise)."""
    return {"type": "minecraft:add",
            "argument1": {"type": "minecraft:y_clamped_gradient", "from_y": 55, "to_y": 87, "from_value": 1.0, "to_value": -1.0},
            "argument2": {"type": "minecraft:clamp", "min": -0.5625, "max": 0.5625,
                          "input": {"type": "minecraft:mul", "argument1": 0.8,
                                    "argument2": {"type": "minecraft:noise", "noise": "minecraft:surface",
                                                  "xz_scale": 0.6, "y_scale": 0.0}}}}


def shifted(noise: str) -> dict:
    return {"type": "minecraft:shifted_noise", "noise": noise, "shift_x": "minecraft:shift_x", "shift_y": 0.0,
            "shift_z": "minecraft:shift_z", "xz_scale": 0.25, "y_scale": 0.0}


def fl_noise_settings(surface_rule: dict) -> dict:
    density = fl_density()
    full = [-1.0, 1.0]
    return {
        "sea_level": 63, "disable_mob_generation": False, "aquifers_enabled": False, "ore_veins_enabled": False,
        "legacy_random_source": False,
        "default_block": {"Name": "minecraft:stone"}, "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "noise": {"min_y": -64, "height": 384, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": {
            "barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
            "temperature": shifted("minecraft:temperature"), "vegetation": shifted("minecraft:vegetation"),
            "continents": 0.0, "erosion": 0.0, "depth": 0.0, "ridges": 0.0,
            "initial_density_without_jaggedness": density, "final_density": density,
            "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0,
        },
        "spawn_target": [{"continentalness": full, "depth": 0.0, "erosion": full, "humidity": full, "offset": 0.0,
                          "temperature": full, "weirdness": full}],
        "surface_rule": surface_rule,
    }


def climate(temperature, humidity) -> dict:
    full = [-1.0, 1.0]
    return {"continentalness": full, "depth": 0.0, "erosion": full, "humidity": humidity, "offset": 0.0,
            "temperature": temperature, "weirdness": full}


def fl_dimension(biomes: List[dict]) -> dict:
    return {"type": "lacuna:forgotten_land", "generator": {
        "type": "minecraft:noise", "settings": "lacuna:forgotten_land",
        "biome_source": {"type": "minecraft:multi_noise", "biomes": biomes}}}


def jigsaw_structure(start_pool: str, biomes: str, size: int = 1, adaptation: str = "beard_thin", start_y: int = 0,
                     step: str = "surface_structures", max_distance: int = 80, heightmap: Optional[str] = "WORLD_SURFACE_WG") -> dict:
    d: Dict[str, Any] = {"type": "minecraft:jigsaw", "biomes": biomes, "max_distance_from_center": max_distance,
                         "size": size, "spawn_overrides": {}, "start_height": {"absolute": start_y}, "start_pool": start_pool,
                         "step": step, "terrain_adaptation": adaptation, "use_expansion_hack": False}
    if heightmap:
        d["project_start_to_heightmap"] = heightmap
    return d


def pool(elements: List[str], fallback: str = "minecraft:empty", processors: str = "minecraft:empty",
         projection: str = "rigid", weights: Optional[List[int]] = None) -> dict:
    return {"fallback": fallback, "elements": [
        {"element": {"element_type": "minecraft:single_pool_element", "location": loc, "processors": processors,
                     "projection": projection}, "weight": (weights[i] if weights else 1)}
        for i, loc in enumerate(elements)]}


def structure_set(structures: List[str], spacing: int, separation: int, salt: int) -> dict:
    return {"placement": {"type": "minecraft:random_spread", "salt": salt, "separation": separation, "spacing": spacing},
            "structures": [{"structure": s, "weight": 1} for s in structures]}


def loot_entry(item: str, weight: int, lo: int = 1, hi: int = 1) -> dict:
    e: Dict[str, Any] = {"type": "minecraft:item", "name": item, "weight": weight}
    if (lo, hi) != (1, 1):
        e["functions"] = [{"function": "minecraft:set_count", "add": False,
                           "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}}]
    return e


def chest_table(pools: List[dict]) -> dict:
    return {"type": "minecraft:chest", "pools": pools}


def chest_pool(entries: List[dict], lo: int, hi: int) -> dict:
    return {"bonus_rolls": 0.0, "entries": entries, "rolls": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}}
