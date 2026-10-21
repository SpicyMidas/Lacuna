"""Banishment particles (sprite lists) and Exilite, the Totem of Banishing's own stone (art: tools/art/m17_exile.py).

Exilite is super rare and only found in the Forgotten Land: tiny veins (1-3 blocks) deep below Y 0, about one every
four chunks, and now and then a shard in a ruin cache. Its ore needs a diamond pickaxe and drops Exilite Shards (fortune
applies, silk touch keeps the ore). Uses:
  * four shards around crying obsidian make a Forgotten Portal Frame without rubies (the ruby recipe needs four rubies),
    so a portal home can be built from what the Forgotten Land itself gives;
  * four shards make a block of Exilite, the base of a pale decorative family: polished, bricks, chiseled (the totem's
    open hand), brick stairs, slab and wall; crafting grid or stonecutter.
"""
from gen_resources import loot_ore, mod

PARTICLES = {
    "banish_mote": ["banish_mote_0", "banish_mote_1", "banish_mote_2"],
    "rift_spark": ["rift_spark_0", "rift_spark_1", "rift_spark_2"],
}

STONES = ["exilite", "polished_exilite", "exilite_bricks", "chiseled_exilite"]
BRICK_SHAPES = ["exilite_brick_stairs", "exilite_brick_slab", "exilite_brick_wall"]
FAMILY = STONES + BRICK_SHAPES


def build(g):
    for name, sprites in PARTICLES.items():
        g.asset(f"particles/{name}", {"textures": [f"lacuna:{s}" for s in sprites]})
    blocks(g)
    recipes(g)
    worldgen(g)


def blocks(g):
    g.cube("exilite_ore", "Exilite Ore", "Minerale di esilite", loot=loot_ore("exilite_ore", "lacuna:exilite_shard"))
    g.cube("exilite", "Exilite", "Esilite")
    g.cube("polished_exilite", "Polished Exilite", "Esilite levigata")
    g.cube("exilite_bricks", "Exilite Bricks", "Mattoni di esilite")
    g.cube("chiseled_exilite", "Chiseled Exilite", "Esilite cesellata")
    tex = "lacuna:block/exilite_bricks"
    g.stairs("exilite_brick_stairs", "Exilite Brick Stairs", "Scale di mattoni di esilite", tex)
    g.slab("exilite_brick_slab", "Exilite Brick Slab", "Lastra di mattoni di esilite", "exilite_bricks", tex)
    g.wall("exilite_brick_wall", "Exilite Brick Wall", "Muretto di mattoni di esilite", tex)
    g.item("exilite_shard", "Exilite Shard", "Frammento di esilite")

    g.name("item.lacuna.exilite_shard.tip",
           "The pale stone the Totem of Banishing was carved from. Found only deep in the Forgotten Land, and rarely. "
           "Four around crying obsidian: a Forgotten Portal Frame, no rubies needed. Four together: a block of Exilite.",
           "La pietra pallida da cui fu scolpito il Totem dell'esilio. Si trova solo in profondità nella Terra Dimenticata, "
           "e di rado. Quattro attorno all'ossidiana piangente: una cornice del portale dimenticato, senza rubini. "
           "Quattro insieme: un blocco di esilite.")
    g.name("item.lacuna.exilite_ore.tip",
           "Grows only in the Forgotten Land, deep below Y 0, a tiny vein every few chunks. Needs a diamond pickaxe.",
           "Cresce solo nella Terra Dimenticata, in profondità sotto Y 0, una piccola vena ogni pochi chunk. "
           "Richiede un piccone di diamante.")
    g.name("item.lacuna.exilite.tip", "Pale as the totem's stone hand. Polish it, cut it into bricks, carve the hand into it.",
           "Pallida come la mano di pietra del totem. Levigala, tagliala in mattoni, scolpiscici la mano.")
    g.name("item.lacuna.chiseled_exilite.tip", "The totem's open hand, carved into its own stone; lichen grows where the ruby was.",
           "La mano aperta del totem, scolpita nella sua stessa pietra; il lichene cresce dove c'era il rubino.")

    for b in ["exilite_ore"] + FAMILY:
        g.tag("block", "minecraft:mineable/pickaxe", mod(b))
    g.tag("block", "minecraft:needs_diamond_tool", mod("exilite_ore"))
    for b in FAMILY:
        g.tag("block", "minecraft:needs_iron_tool", mod(b))
    g.tag("block", "c:ores", "#c:ores/exilite")
    g.tag("block", "c:ores/exilite", mod("exilite_ore"))
    g.tag("block", "c:ore_rates/singular", mod("exilite_ore"))
    g.tag("item", "c:ores", "#c:ores/exilite")
    g.tag("item", "c:ores/exilite", mod("exilite_ore"))
    for kind in ("block", "item"):
        g.tag(kind, "minecraft:stairs", mod("exilite_brick_stairs"))
        g.tag(kind, "minecraft:slabs", mod("exilite_brick_slab"))
        g.tag(kind, "minecraft:walls", mod("exilite_brick_wall"))


def stonecut(g, src, result, count=1):
    rid = f"{result}_from_{src}_stonecutting"
    g.data(f"recipe/{rid}", {"type": "minecraft:stonecutting", "ingredient": {"item": mod(src)},
                             "result": {"count": count, "id": mod(result)}})
    g.unlock(rid, mod(src), "building")


def recipes(g):
    g.shaped("forgotten_portal_frame_from_exilite", [" E ", "EOE", " E "],
             {"E": "lacuna:exilite_shard", "O": "minecraft:crying_obsidian"}, "lacuna:forgotten_portal_frame",
             category="building", unlock="lacuna:exilite_shard")
    g.cooking("exilite_shard_from_smelting_exilite_ore", "smelting", "lacuna:exilite_ore", "lacuna:exilite_shard", 1.0, 200)
    g.cooking("exilite_shard_from_blasting_exilite_ore", "blasting", "lacuna:exilite_ore", "lacuna:exilite_shard", 1.0, 100)
    g.shaped("exilite", ["##", "##"], {"#": "lacuna:exilite_shard"}, "lacuna:exilite", category="building")
    g.shaped("polished_exilite", ["##", "##"], {"#": "lacuna:exilite"}, "lacuna:polished_exilite", 4, "building")
    g.shaped("exilite_bricks", ["##", "##"], {"#": "lacuna:polished_exilite"}, "lacuna:exilite_bricks", 4, "building")
    g.shaped("chiseled_exilite", ["#", "#"], {"#": "lacuna:exilite_brick_slab"}, "lacuna:chiseled_exilite", 1, "building")
    g.shaped("exilite_brick_stairs", ["#  ", "## ", "###"], {"#": "lacuna:exilite_bricks"}, "lacuna:exilite_brick_stairs", 4,
             "building")
    g.shaped("exilite_brick_slab", ["###"], {"#": "lacuna:exilite_bricks"}, "lacuna:exilite_brick_slab", 6, "building")
    g.shaped("exilite_brick_wall", ["###", "###"], {"#": "lacuna:exilite_bricks"}, "lacuna:exilite_brick_wall", 6, "building")
    # stonecutter: every shape from any less worked stone, one to one (two slabs)
    order = ["exilite", "polished_exilite", "exilite_bricks"]
    for i, src in enumerate(order):
        for result in order[i + 1:] + ["chiseled_exilite", "exilite_brick_stairs", "exilite_brick_wall"]:
            stonecut(g, src, result)
        stonecut(g, src, "exilite_brick_slab", 2)


def worldgen(g):
    replace = [("minecraft:stone_ore_replaceables"), ("minecraft:deepslate_ore_replaceables")]
    g.data("worldgen/configured_feature/ore_exilite", {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 0.7, "size": 3, "targets": [
            {"state": {"Name": "lacuna:exilite_ore"}, "target": {"predicate_type": "minecraft:tag_match", "tag": t}}
            for t in replace]}})
    g.data("worldgen/placed_feature/ore_exilite", {"feature": "lacuna:ore_exilite", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"above_bottom": 6},
                                                      "max_inclusive": {"absolute": -8}}},
        {"type": "minecraft:biome"}]})
    g.data("neoforge/biome_modifier/add_ore_exilite", {"type": "neoforge:add_features", "biomes": "#lacuna:is_forgotten_land",
                                                       "features": "lacuna:ore_exilite", "step": "underground_ores"})
    # a ruin cache now and then holds a shard (the rare pool: 1 roll, mostly empty)
    g.shared.setdefault("ruin_cache_rare", []).append(
        {"type": "minecraft:item", "name": "lacuna:exilite_shard", "weight": 12})
