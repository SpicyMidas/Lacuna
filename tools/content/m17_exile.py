"""Banishment particles (sprite lists) and Exilite, the Totem of Banishing's own stone (art: tools/art/m17_exile.py).

Exilite is found like ancient debris: Exilite Debris, in hidden veins (1-3 blocks that never touch air) deep below
Y -8 in the Forgotten Land only, about one every four chunks; now and then a shard turns up in a ruin cache. The
debris has ancient debris' toughness (30 / 1200: no explosion breaks it), needs a diamond pickaxe, drops itself and
never burns. Uses:
  * smelting or blasting the debris gives an Exilite Shard (like netherite scrap, 2 xp);
  * four shards and four gold ingots make an Exilite Ingot (shapeless, like netherite);
  * an ingot between two crying obsidian makes two Forgotten Portal Frames (the ruby recipe costs four rubies per
    frame), so a portal home (eight frames: four ingots) can be built from what the Forgotten Land itself gives;
  * nine ingots make a Block of Exilite, a netherite-like storage block (beacon base, back to nine ingots);
  * decoration stays affordable: one shard in a ring of eight calcite gives eight Exilite, the base of the pale family
    (polished, bricks, chiseled, brick stairs, slab and wall; crafting grid or stonecutter). Nine ingots per block of
    decoration would put a whole wall out of reach, so only the storage block pays the ingot price.
The old Exilite Ore id loads as the debris (alias in ModBlocks).
"""
from gen_resources import mod

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
    g.column("exilite_debris", "Exilite Debris", "Detriti di esilite", side="lacuna:block/exilite_debris_side",
             end="lacuna:block/exilite_debris_top", axis=False)
    g.cube("exilite_block", "Block of Exilite", "Blocco di esilite")
    g.cube("exilite", "Exilite", "Esilite")
    g.cube("polished_exilite", "Polished Exilite", "Esilite levigata")
    g.cube("exilite_bricks", "Exilite Bricks", "Mattoni di esilite")
    g.cube("chiseled_exilite", "Chiseled Exilite", "Esilite cesellata")
    tex = "lacuna:block/exilite_bricks"
    g.stairs("exilite_brick_stairs", "Exilite Brick Stairs", "Scale di mattoni di esilite", tex)
    g.slab("exilite_brick_slab", "Exilite Brick Slab", "Lastra di mattoni di esilite", "exilite_bricks", tex)
    g.wall("exilite_brick_wall", "Exilite Brick Wall", "Muretto di mattoni di esilite", tex)
    g.item("exilite_shard", "Exilite Shard", "Frammento di esilite")
    g.item("exilite_ingot", "Exilite Ingot", "Lingotto di esilite")
    # debris and block have their own (fire resistant) BlockItems registered as items: name those ids too
    g.name("item.lacuna.exilite_debris", "Exilite Debris", "Detriti di esilite")
    g.name("item.lacuna.exilite_block", "Block of Exilite", "Blocco di esilite")

    g.name("item.lacuna.exilite_debris.tip",
           "The buried remains of the stone the Totem of Banishing was carved from. Hidden deep in the Forgotten Land, "
           "never in the open, and rarely. Needs a diamond pickaxe; no explosion breaks it. Smelt it for a shard.",
           "I resti sepolti della pietra da cui fu scolpito il Totem dell'esilio. Nascosti in profondità nella Terra "
           "Dimenticata, mai all'aperto, e di rado. Richiede un piccone di diamante; nessuna esplosione lo rompe. "
           "Fondilo per ottenere un frammento.")
    g.name("item.lacuna.exilite_shard.tip",
           "Smelted from Exilite Debris. Four shards and four gold ingots: an Exilite Ingot. "
           "One in a ring of calcite: eight blocks of Exilite.",
           "Fuso dai detriti di esilite. Quattro frammenti e quattro lingotti d'oro: un lingotto di esilite. "
           "Uno in un anello di calcite: otto blocchi di esilite.")
    g.name("item.lacuna.exilite_ingot.tip",
           "Between two crying obsidian: two Forgotten Portal Frames, no rubies needed. Nine: a Block of Exilite. "
           "Never burns.",
           "Tra due ossidiane piangenti: due cornici del portale dimenticato, senza rubini. Nove: un blocco di esilite. "
           "Non brucia mai.")
    g.name("item.lacuna.exilite_block.tip", "Nine Exilite Ingots, pressed together. A beacon base; never burns.",
           "Nove lingotti di esilite pressati insieme. Base per un faro; non brucia mai.")
    g.name("item.lacuna.exilite.tip", "Calcite stained by a single shard. Polish it, cut it into bricks, carve the hand into it.",
           "Calcite tinta da un solo frammento. Levigala, tagliala in mattoni, scolpiscici la mano.")
    g.name("item.lacuna.chiseled_exilite.tip", "The totem's open hand, carved into its own stone; a pearl glints where the ruby was.",
           "La mano aperta del totem, scolpita nella sua stessa pietra; una perla brilla dove c'era il rubino.")

    for b in ["exilite_debris", "exilite_block"] + FAMILY:
        g.tag("block", "minecraft:mineable/pickaxe", mod(b))
    g.tag("block", "minecraft:needs_diamond_tool", mod("exilite_debris"), mod("exilite_block"))
    for b in FAMILY:
        g.tag("block", "minecraft:needs_iron_tool", mod(b))
    # like ancient debris: an ore of its own kind (c:ores/exilite), one drop per block
    g.tag("block", "c:ores", "#c:ores/exilite")
    g.tag("block", "c:ores/exilite", mod("exilite_debris"))
    g.tag("block", "c:ore_rates/singular", mod("exilite_debris"))
    g.tag("item", "c:ores", "#c:ores/exilite")
    g.tag("item", "c:ores/exilite", mod("exilite_debris"))
    # like netherite: ingot, storage block, beacon
    g.tag("item", "c:ingots", "#c:ingots/exilite")
    g.tag("item", "c:ingots/exilite", mod("exilite_ingot"))
    g.tag("item", "minecraft:beacon_payment_items", mod("exilite_ingot"))
    for kind in ("block", "item"):
        g.tag(kind, "c:storage_blocks", "#c:storage_blocks/exilite")
        g.tag(kind, "c:storage_blocks/exilite", mod("exilite_block"))
    g.tag("block", "minecraft:beacon_base_blocks", mod("exilite_block"))
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
    g.shaped("forgotten_portal_frame_from_exilite", ["OIO"],
             {"I": "lacuna:exilite_ingot", "O": "minecraft:crying_obsidian"}, "lacuna:forgotten_portal_frame", 2,
             category="building", unlock="lacuna:exilite_ingot")
    g.cooking("exilite_shard_from_smelting_exilite_debris", "smelting", "lacuna:exilite_debris", "lacuna:exilite_shard", 2.0, 200)
    g.cooking("exilite_shard_from_blasting_exilite_debris", "blasting", "lacuna:exilite_debris", "lacuna:exilite_shard", 2.0, 100)
    g.shapeless("exilite_ingot", ["lacuna:exilite_shard"] * 4 + ["minecraft:gold_ingot"] * 4, "lacuna:exilite_ingot", 1, "misc")
    g.storage("lacuna:exilite_block", "lacuna:exilite_ingot")
    g.shaped("exilite", ["CCC", "CEC", "CCC"], {"C": "minecraft:calcite", "E": "lacuna:exilite_shard"}, "lacuna:exilite", 8,
             "building")
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
    # hidden like ancient debris: a vein that would touch air (a cave, a ravine) is never placed
    replace = [("minecraft:stone_ore_replaceables"), ("minecraft:deepslate_ore_replaceables")]
    g.data("worldgen/configured_feature/ore_exilite", {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 1.0, "size": 3, "targets": [
            {"state": {"Name": "lacuna:exilite_debris"}, "target": {"predicate_type": "minecraft:tag_match", "tag": t}}
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
