"""M9: Lantern Moth, Silk Dust (+ glow decal), Clockwork Decoy, and extra drops for vanilla mobs (global loot
modifiers: spiders drop Umbral Steel nuggets, husks and pillagers Clockwork Gears)."""
from gen_resources import mod, pool

FACINGS = {"up": (0, 0), "down": (180, 0), "north": (90, 0), "east": (90, 90), "south": (90, 180), "west": (90, 270)}


def build(g):
    entities(g)
    items(g)
    silk_glow(g)
    loot(g)
    vanilla_drops(g)
    tags(g)
    spawns(g)
    g.shapeless("clockwork_decoy", ["lacuna:clockwork_gear", "lacuna:clockwork_gear", "lacuna:clockwork_gear",
                                    "minecraft:redstone", "minecraft:white_wool"], "lacuna:clockwork_decoy")


def entities(g):
    g.name("entity.lacuna.lantern_moth", "Lantern Moth", "Falena lanterna")
    g.name("entity.lacuna.clockwork_decoy", "Clockwork Decoy", "Esca a orologeria")
    g.name("entity.lacuna.thrown_clockwork_decoy", "Thrown Clockwork Decoy", "Esca a orologeria lanciata")


def items(g):
    g.item("silk_dust", "Silk Dust", "Polvere di seta")
    g.item("clockwork_gear", "Clockwork Gear", "Ingranaggio a orologeria")
    g.item("clockwork_decoy", "Clockwork Decoy", "Esca a orologeria")
    g.item("lantern_moth_spawn_egg", "Lantern Moth Spawn Egg", "Uovo generatore di falena lanterna", model="spawn_egg")


def silk_glow(g):
    model = {
        "parent": "minecraft:block/block", "ambientocclusion": False, "render_type": "minecraft:translucent",
        "textures": {"particle": "lacuna:block/silk_dust_glow", "glow": "lacuna:block/silk_dust_glow"},
        "elements": [{"from": [0, 0.1, 0], "to": [16, 0.1, 16], "shade": False,
                      "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#glow"},
                                "down": {"uv": [0, 0, 16, 16], "texture": "#glow"}}}],
    }
    variants = {}
    for facing, (x, y) in FACINGS.items():
        v = {"model": "lacuna:block/silk_dust_glow"}
        if x:
            v["x"] = x
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    g.single("silk_dust_glow", "Silk Dust Glow", "Bagliore di polvere di seta", model, item=False, loot=None,
             blockstate={"variants": variants})
    g.props("silk_dust_glow", facing=list(FACINGS))


def count(lo, hi, looting=True):
    fns = [{"function": "minecraft:set_count", "add": False, "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if looting:
        fns.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                    "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0}})
    return fns


def rare(chance):
    return [{"condition": "minecraft:killed_by_player"},
            {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
             "unenchanted_chance": chance,
             "enchanted_chance": {"type": "minecraft:linear", "base": chance + 0.01, "per_level_above_first": 0.01}}]


def entity_loot(g, eid, pools):
    data = {"type": "minecraft:entity", "random_sequence": mod(f"entities/{eid}")}
    if pools:
        data["pools"] = pools
    g.data(f"loot_table/entities/{eid}", data)


def loot(g):
    entity_loot(g, "lantern_moth", [pool([{"type": "minecraft:item", "name": "lacuna:silk_dust", "functions": count(0.0, 2.0)}])])
    entity_loot(g, "clockwork_decoy", [])


def extra_loot(g, name, targets, pools):
    """An extra table (loot_table/extra/<name>) that a neoforge:add_table global loot modifier (<target>_drops) rolls
    on top of each vanilla entity table in `targets`; every modifier is listed in neoforge's global_loot_modifiers."""
    g.data(f"loot_table/extra/{name}", {"type": "minecraft:entity", "pools": pools, "random_sequence": mod(f"extra/{name}")})
    for target in targets:
        key = f"{target}_drops"
        g.data(f"loot_modifiers/{key}", {
            "type": "neoforge:add_table",
            "conditions": [{"condition": "neoforge:loot_table_id", "loot_table_id": f"minecraft:entities/{target}"}],
            "table": mod(f"extra/{name}")})
        g.shared.setdefault("loot_modifiers", []).append(mod(key))


def drop(iid, functions=None):
    entry = {"type": "minecraft:item", "name": iid}
    if functions:
        entry["functions"] = functions
    return entry


def vanilla_drops(g):
    # the drops of the two removed M9 hostile mobs (spider-like and hound-like) now come from vanilla mobs
    extra_loot(g, "spider", ["spider", "cave_spider"], [
        pool([drop("lacuna:umbral_steel_nugget")], conditions=rare(0.05))])
    extra_loot(g, "husk", ["husk"], [
        pool([drop("lacuna:clockwork_gear", count(0.0, 2.0))]),
        pool([drop("lacuna:chronal_shard")], conditions=rare(0.04))])
    extra_loot(g, "pillager", ["pillager"], [pool([drop("lacuna:clockwork_gear", count(0.0, 1.0))])])
    g.data("loot_modifiers/global_loot_modifiers", {"replace": False, "entries": g.shared["loot_modifiers"]},
           namespace="neoforge")


def tags(g):
    g.tag("item", "lacuna:moth_attractor", "minecraft:torch", "minecraft:soul_torch", "minecraft:lantern",
          "minecraft:soul_lantern", "minecraft:glowstone", "#minecraft:candles")
    g.tag("entity_type", "minecraft:arthropod", "lacuna:lantern_moth")
    g.tag("entity_type", "minecraft:sensitive_to_bane_of_arthropods", "lacuna:lantern_moth")
    g.tag("entity_type", "minecraft:fall_damage_immune", "lacuna:lantern_moth")
    # a decoy is a temporary, unsaved construct: banishing it would only lose it
    g.tag("entity_type", "lacuna:cannot_be_banished", "lacuna:clockwork_decoy")


def add_spawns(g, name, biomes, entity, weight, lo, hi):
    g.data(f"neoforge/biome_modifier/spawn_{name}", {
        "type": "neoforge:add_spawns", "biomes": biomes,
        "spawners": [{"type": entity, "weight": weight, "minCount": lo, "maxCount": hi}]})


def spawns(g):
    # Forgotten Land biomes (Whispering Marsh, Forgotten Plains) list the moth directly (M10)
    add_spawns(g, "lantern_moth_forests", "#c:is_forest", "lacuna:lantern_moth", 8, 1, 3)
    add_spawns(g, "lantern_moth_swamps", "#c:is_swamp", "lacuna:lantern_moth", 8, 1, 3)
