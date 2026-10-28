"""M8: four wood families with full vanilla-like sets, saplings/trees and worldgen."""
import copy
import json
import os

from gen_resources import TEMPLATES, leaves_loot, mod, pool, SURVIVES

FAMILIES = {
    # id: (English name, Italian name, render type for log/planks, leaf render)
    "cindervine": ("Cindervine", "Vitecenere", None, "minecraft:cutout_mipped"),
    "crystal_willow": ("Crystal Willow", "Salice di cristallo", "minecraft:translucent", "minecraft:translucent"),
    "tidebloom": ("Tidebloom", "Fiordimarea", None, "minecraft:cutout_mipped"),
    "echo_birch": ("Echo Birch", "Betulla dell'eco", None, "minecraft:cutout_mipped"),
}

IT_SUFFIX = {"log": "Tronco di {}", "stripped_log": "Tronco scortecciato di {}", "wood": "Legno di {}",
             "stripped_wood": "Legno scortecciato di {}", "planks": "Assi di {}", "stairs": "Scale di {}",
             "slab": "Lastra di {}", "fence": "Staccionata di {}", "fence_gate": "Cancello di {}", "door": "Porta di {}",
             "trapdoor": "Botola di {}", "pressure_plate": "Placca a pressione di {}", "button": "Pulsante di {}",
             "leaves": "Foglie di {}", "sapling": "Arboscello di {}", "potted_sapling": "Arboscello di {} in vaso"}


def build(g):
    for f, (en, it, render, leaf_render) in FAMILIES.items():
        family(g, f, en, it, render, leaf_render)
    extras(g)
    worldgen(g)


def family(g, f, en, it, render, leaf_render):
    tex = lambda name: f"lacuna:block/{name}"
    # logs and wood
    g.column(f"{f}_log", f"{en} Log", IT_SUFFIX["log"].format(it), side=tex(f"{f}_log"), end=tex(f"{f}_log_top"), render=render)
    g.column(f"stripped_{f}_log", f"Stripped {en} Log", IT_SUFFIX["stripped_log"].format(it), side=tex(f"stripped_{f}_log"),
             end=tex(f"stripped_{f}_log_top"), render=render)
    g.wood_block(f"{f}_wood", f"{en} Wood", IT_SUFFIX["wood"].format(it), tex(f"{f}_log"), render=render)
    g.wood_block(f"stripped_{f}_wood", f"Stripped {en} Wood", IT_SUFFIX["stripped_wood"].format(it), tex(f"stripped_{f}_log"), render=render)
    g.cube(f"{f}_planks", f"{en} Planks", IT_SUFFIX["planks"].format(it), render=render)
    planks = tex(f"{f}_planks")
    g.stairs(f"{f}_stairs", f"{en} Stairs", IT_SUFFIX["stairs"].format(it), planks, render=render)
    g.slab(f"{f}_slab", f"{en} Slab", IT_SUFFIX["slab"].format(it), f"{f}_planks", planks, render=render)
    g.fence(f"{f}_fence", f"{en} Fence", IT_SUFFIX["fence"].format(it), planks)
    g.fence_gate(f"{f}_fence_gate", f"{en} Fence Gate", IT_SUFFIX["fence_gate"].format(it), planks)
    g.door(f"{f}_door", f"{en} Door", IT_SUFFIX["door"].format(it), render=render or "minecraft:cutout")
    g.trapdoor(f"{f}_trapdoor", f"{en} Trapdoor", IT_SUFFIX["trapdoor"].format(it), render=render or "minecraft:cutout")
    g.pressure_plate(f"{f}_pressure_plate", f"{en} Pressure Plate", IT_SUFFIX["pressure_plate"].format(it), planks)
    g.button(f"{f}_button", f"{en} Button", IT_SUFFIX["button"].format(it), planks)
    extra = []
    if f == "cindervine":
        extra = [("lacuna:ember", 0.05)]
    elif f == "crystal_willow":
        extra = [("lacuna:crystal_willow_shard", 0.05)]
    g.leaves(f"{f}_leaves", f"{en} Leaves", IT_SUFFIX["leaves"].format(it),
             leaves_loot(f"{f}_leaves", f"lacuna:{f}_sapling", extra), render=leaf_render)
    g.cross(f"{f}_sapling", f"{en} Sapling", IT_SUFFIX["sapling"].format(it))
    g.props(f"{f}_sapling", stage=["0", "1"])
    g.potted(f"potted_{f}_sapling", f"Potted {en} Sapling", IT_SUFFIX["potted_sapling"].format(it), tex(f"{f}_sapling"),
             f"lacuna:{f}_sapling")
    for b in (f"{f}_door",):
        g.props(b, facing=["north", "south", "west", "east"], half=["upper", "lower"], hinge=["left", "right"],
                open=["true", "false"], powered=["true", "false"])

    # recipes
    g.shapeless(f"{f}_planks", [f"#lacuna:{f}_logs"], f"lacuna:{f}_planks", 4, "building", unlock=f"#lacuna:{f}_logs")
    g.shaped(f"{f}_wood", ["##", "##"], {"#": f"lacuna:{f}_log"}, f"lacuna:{f}_wood", 3, "building")
    g.shaped(f"stripped_{f}_wood", ["##", "##"], {"#": f"lacuna:stripped_{f}_log"}, f"lacuna:stripped_{f}_wood", 3, "building")
    P = f"lacuna:{f}_planks"
    g.shaped(f"{f}_stairs", ["#  ", "## ", "###"], {"#": P}, f"lacuna:{f}_stairs", 4, "building")
    g.shaped(f"{f}_slab", ["###"], {"#": P}, f"lacuna:{f}_slab", 6, "building")
    g.shaped(f"{f}_fence", ["W#W", "W#W"], {"W": P, "#": "minecraft:stick"}, f"lacuna:{f}_fence", 3, "decorations")
    g.shaped(f"{f}_fence_gate", ["#W#", "#W#"], {"W": P, "#": "minecraft:stick"}, f"lacuna:{f}_fence_gate", 1, "redstone")
    g.shaped(f"{f}_door", ["##", "##", "##"], {"#": P}, f"lacuna:{f}_door", 3, "redstone")
    g.shaped(f"{f}_trapdoor", ["###", "###"], {"#": P}, f"lacuna:{f}_trapdoor", 2, "redstone")
    g.shaped(f"{f}_pressure_plate", ["##"], {"#": P}, f"lacuna:{f}_pressure_plate", 1, "redstone")
    g.shapeless(f"{f}_button", [P], f"lacuna:{f}_button", 1, "redstone")

    # tags
    logs = [mod(x) for x in (f"{f}_log", f"stripped_{f}_log", f"{f}_wood", f"stripped_{f}_wood")]
    for kind in ("block", "item"):
        g.tag(kind, f"lacuna:{f}_logs", *logs)
        g.tag(kind, "minecraft:logs", f"#lacuna:{f}_logs")
        if f != "cindervine":
            g.tag(kind, "minecraft:logs_that_burn", f"#lacuna:{f}_logs")
        g.tag(kind, "minecraft:planks", mod(f"{f}_planks"))
        g.tag(kind, "minecraft:wooden_stairs", mod(f"{f}_stairs"))
        g.tag(kind, "minecraft:wooden_slabs", mod(f"{f}_slab"))
        g.tag(kind, "minecraft:wooden_fences", mod(f"{f}_fence"))
        g.tag(kind, "minecraft:fence_gates", mod(f"{f}_fence_gate"))
        g.tag(kind, "minecraft:wooden_doors", mod(f"{f}_door"))
        g.tag(kind, "minecraft:wooden_trapdoors", mod(f"{f}_trapdoor"))
        g.tag(kind, "minecraft:wooden_pressure_plates", mod(f"{f}_pressure_plate"))
        g.tag(kind, "minecraft:wooden_buttons", mod(f"{f}_button"))
        g.tag(kind, "minecraft:leaves", mod(f"{f}_leaves"))
        g.tag(kind, "minecraft:saplings", mod(f"{f}_sapling"))
        g.tag(kind, "c:stripped_logs", mod(f"stripped_{f}_log"))
        g.tag(kind, "c:stripped_woods", mod(f"stripped_{f}_wood"))
    g.tag("block", "minecraft:flower_pots", mod(f"potted_{f}_sapling"))
    g.tag("block", "minecraft:mineable/axe", *logs, mod(f"{f}_planks"), mod(f"{f}_stairs"), mod(f"{f}_slab"), mod(f"{f}_fence"),
          mod(f"{f}_fence_gate"), mod(f"{f}_door"), mod(f"{f}_trapdoor"), mod(f"{f}_pressure_plate"), mod(f"{f}_button"))
    g.tag("block", "minecraft:mineable/hoe", mod(f"{f}_leaves"))

    # data maps (NeoForge): stripping, composting
    g.shared.setdefault("strippables", {})[mod(f"{f}_log")] = {"stripped_block": mod(f"stripped_{f}_log")}
    g.shared["strippables"][mod(f"{f}_wood")] = {"stripped_block": mod(f"stripped_{f}_wood")}
    g.shared.setdefault("compostables", {})[mod(f"{f}_sapling")] = {"chance": 0.3}
    g.shared["compostables"][mod(f"{f}_leaves")] = {"chance": 0.3}


def extras(g):
    g.cross("crystal_willow_strands", "Crystal Willow Strands", "Fili di salice di cristallo")
    g.cube("tidebloom_roots", "Tidebloom Roots", "Radici di fiordimarea", render="minecraft:cutout",
           parent="minecraft:block/cube_all")
    g.props("tidebloom_roots", waterlogged=["true", "false"])
    g.tag("block", "minecraft:mineable/axe", mod("tidebloom_roots"))
    g.item("ember", "Ember", "Brace")
    g.item("crystal_willow_shard", "Crystal Willow Shard", "Scheggia di salice di cristallo")
    g.shaped("glass_from_crystal_willow_shards", ["##", "##"], {"#": "lacuna:crystal_willow_shard"}, "minecraft:glass", 1, "building")
    g.shared["fuels"] = {mod("ember"): {"burn_time": 1600}, mod("cindervine_planks"): {"burn_time": 400}}
    # the Heartwood Bow is a relic (no recipe, m19_progression); Echo Birch planks still repair it
    g.tag("item", "lacuna:heartwood_bow_repair", mod("echo_birch_planks"))

    def write_maps(g2):
        g2.data("data_maps/block/strippables", {"values": dict(sorted(g2.shared["strippables"].items()))}, namespace="neoforge")
        g2.data("data_maps/item/compostables", {"values": dict(sorted(g2.shared["compostables"].items()))}, namespace="neoforge")
        g2.data("data_maps/item/furnace_fuels", {"values": dict(sorted(g2.shared["fuels"].items()))}, namespace="neoforge")
    g.finalizer(write_maps)


def tree(template, replace, decorators=None):
    data = json.load(open(os.path.join(TEMPLATES, "features", template + ".json"), encoding="utf-8"))
    text = json.dumps(data)
    for old, new in replace:
        text = text.replace(old, new)
    data = json.loads(text)
    if decorators is not None:
        data["config"]["decorators"] = decorators
    return data


def worldgen(g):
    trees = {
        "cindervine": tree("acacia", [("minecraft:acacia_log", "lacuna:cindervine_log"), ("minecraft:acacia_leaves", "lacuna:cindervine_leaves")]),
        "crystal_willow": tree("cherry", [("minecraft:cherry_log", "lacuna:crystal_willow_log"),
                                          ("minecraft:cherry_leaves", "lacuna:crystal_willow_leaves")],
                               decorators=[{"type": "lacuna:hanging_strands", "probability": 0.12}]),
        "tidebloom": tree("mangrove", [("minecraft:mangrove_log", "lacuna:tidebloom_log"), ("minecraft:mangrove_leaves", "lacuna:tidebloom_leaves"),
                                       ("\"minecraft:mangrove_roots\"", "\"lacuna:tidebloom_roots\""),
                                       ("#minecraft:mangrove_logs_can_grow_through", "#lacuna:tidebloom_logs_can_grow_through"),
                                       ("#minecraft:mangrove_roots_can_grow_through", "#lacuna:tidebloom_roots_can_grow_through")],
                          decorators=[{"type": "minecraft:leave_vine", "probability": 0.125}]),
        "echo_birch": tree("birch", [("minecraft:birch_log", "lacuna:echo_birch_log"), ("minecraft:birch_leaves", "lacuna:echo_birch_leaves")]),
    }
    # the mangrove trunk/roots must be able to grow through our own blocks too
    g.tag("block", "lacuna:tidebloom_logs_can_grow_through", "#minecraft:mangrove_logs_can_grow_through", mod("tidebloom_roots"),
          mod("tidebloom_leaves"), mod("tidebloom_log"), mod("tidebloom_sapling"))
    g.tag("block", "lacuna:tidebloom_roots_can_grow_through", "#minecraft:mangrove_roots_can_grow_through", mod("tidebloom_roots"),
          mod("tidebloom_sapling"))
    biomes = {
        "cindervine": ["minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna", "minecraft:badlands",
                       "minecraft:wooded_badlands", "minecraft:windswept_hills"],
        "crystal_willow": ["minecraft:cherry_grove", "minecraft:meadow"],
        "tidebloom": ["minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:river"],
        "echo_birch": ["minecraft:birch_forest", "minecraft:old_growth_birch_forest"],
    }
    rarity = {"cindervine": 32, "crystal_willow": 40, "tidebloom": 24, "echo_birch": 28}
    for f, data in trees.items():
        g.data(f"worldgen/configured_feature/{f}_tree", data)
        g.data(f"worldgen/placed_feature/{f}_tree", {"feature": f"lacuna:{f}_tree", "placement": [
            {"type": "minecraft:rarity_filter", "chance": rarity[f]}, {"type": "minecraft:in_square"},
            {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 2 if f == "tidebloom" else 0},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, {"type": "minecraft:biome"},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": {
                "Name": f"lacuna:{f}_sapling", "Properties": {"stage": "0"}}}}]})
        g.tag("worldgen/biome", f"lacuna:has_tree/{f}", *biomes[f])
        g.data(f"neoforge/biome_modifier/add_{f}_tree", {"type": "neoforge:add_features", "biomes": f"#lacuna:has_tree/{f}",
                                                          "features": f"lacuna:{f}_tree", "step": "vegetal_decoration"})
