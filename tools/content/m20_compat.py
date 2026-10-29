"""Cross-mod compatibility (beta release pass): NeoForge common (c:) convention tags, a few vanilla gameplay tags and
recipe-viewer hiding, so other mods' recipes, ore processors, tool belts, mob catchers, block movers, recipe viewers
(JEI / EMI / REI) and modded biomes (Terralith, Biomes O' Plenty...) treat Lacuna's content like their own.

Append-only: every call adds values to tags that earlier modules may already fill (materials in m01/m04/m07/m17, wood
in m08). Enchantability is NOT changed here: the special weapons and relics enforce their own rules in code
(supportsEnchantment / isPrimaryItemFor: Unbreaking only, or nothing), so they only join the c:tools/* recognition
tags, never the minecraft:swords / enchantable/* tags that would advertise enchantments they refuse.
"""
from gen_resources import mod

WOODS = ["cindervine", "crystal_willow", "tidebloom", "echo_birch"]


def opt(tag: str) -> dict:
    """An optional tag reference: a tag missing from the pack never breaks the tag that names it."""
    return {"id": tag, "required": False}


def both(g, tag: str, *values) -> None:
    """Block tag and its item twin (convention tags exist in both registries)."""
    g.tag("block", tag, *values)
    g.tag("item", tag, *values)


def build(g):
    materials(g)
    tools(g)
    blocks(g)
    entities(g)
    hidden(g)
    biomes(g)


def materials(g):
    # Zenithite: lapis-like ore (1-2 shards per ore -> "dense"), stone and deepslate variants, a 9-shard storage block
    both(g, "c:ores_in_ground/stone", mod("ruby_ore"), mod("umbral_steel_ore"), mod("zenithite_ore"))
    both(g, "c:ores_in_ground/deepslate", mod("deepslate_ruby_ore"), mod("deepslate_umbral_steel_ore"),
         mod("deepslate_zenithite_ore"))
    both(g, "c:ore_rates/singular", mod("ruby_ore"), mod("deepslate_ruby_ore"), mod("umbral_steel_ore"),
         mod("deepslate_umbral_steel_ore"), mod("exilite_debris"))
    both(g, "c:ore_rates/dense", mod("zenithite_ore"), mod("deepslate_zenithite_ore"))
    both(g, "c:storage_blocks", "#c:storage_blocks/zenithite")
    both(g, "c:storage_blocks/zenithite", mod("zenithite_block"))
    # Umbral Steel is a metal like iron: its ingot pays a beacon, its block builds the pyramid (ruby and exilite already do)
    g.tag("item", "minecraft:beacon_payment_items", mod("umbral_steel_ingot"))
    g.tag("block", "minecraft:beacon_base_blocks", mod("umbral_steel_block"))


def tools(g):
    # recognition only (tool belts, loot filters, "any melee weapon" recipes); see the module docstring for enchantments
    g.tag("item", "c:tools/melee_weapon", mod("umbral_steel_sword"), mod("umbral_steel_axe"), mod("eclipser"),
          mod("gravemaw_greatsword"), mod("tidecaller_sickle"), mod("echo_dagger"))
    g.tag("item", "c:tools/mining_tool", mod("umbral_steel_pickaxe"))
    g.tag("item", "c:tools/bow", mod("heartwood_bow"))
    g.tag("item", "c:tools/spear", mod("suns_fury"))  # a spear of sunlight thrown like a trident
    g.tag("item", "c:tools/ranged_weapon", mod("heartwood_bow"), mod("pyrrhic_scepter"), mod("suns_fury"))


def blocks(g):
    # job sites of the Coiner, Magistrate and Guildmaster professions (village/ModVillagers)
    both(g, "c:villager_job_sites", mod("mint_press"), mod("judge_bench"), mod("guild_board"))
    both(g, "c:fence_gates/wooden", *[mod(f"{w}_fence_gate") for w in WOODS])
    # hanging glassy strands under willow leaves: other trees may grow through them like vines and hanging roots
    g.tag("block", "minecraft:replaceable_by_trees", mod("crystal_willow_strands"))
    # blocks that refuse pistons on purpose (PushReaction.BLOCK) must not be carried off by block movers either
    # (Carry On, contraptions, packing tape...): portals and their frames, the arrival anchor bound to its pearl,
    # the two-block hourglass, the updraft spring, the labyrinth and obelisk pieces, the ruins and the civic blocks
    # (a ledger's position keys its village record)
    g.tag("block", "c:relocation_not_supported", "#lacuna:ruin_blocks", mod("forgotten_portal"),
          mod("forgotten_portal_frame"), mod("arrival_anchor"), mod("chrono_hourglass"), mod("updraft_spring"),
          mod("village_ledger"), mod("council_table"), mod("judge_bench"), mod("guild_board"), mod("school_desk"),
          mod("mint_press"))


def entities(g):
    # summoned, bound or session-only creatures: a mob catcher must not copy them, nor carry them off
    g.tag("entity_type", "c:capturing_not_supported", mod("echo"), mod("reflection"), mod("clockwork_decoy"),
          mod("animated_item"), mod("last_merchant"), mod("confusion_copy"))
    g.tag("entity_type", "c:teleporting_not_supported", mod("echo"), mod("reflection"), mod("confusion_copy"))


def hidden(g):
    # technical blocks (no item, structure-only or internal state) stay out of JEI / EMI / REI
    g.tag("block", "c:hidden_from_recipe_viewers", mod("forgotten_portal"), mod("arrival_anchor"), mod("silk_dust_glow"),
          mod("grasping_roots"), mod("labyrinth_trigger"), mod("labyrinth_exit"), mod("obelisk_inscription"))
    g.tag("item", "c:hidden_from_recipe_viewers", mod("labyrinth_trigger"), mod("labyrinth_exit"),
          mod("obelisk_inscription"))


def biomes(g):
    # the tree biome lists name vanilla biomes; the convention tags (optional) add the matching modded biomes
    g.tag("worldgen/biome", "lacuna:has_tree/cindervine", opt("#c:is_savanna"))
    g.tag("worldgen/biome", "lacuna:has_tree/crystal_willow", opt("#c:is_magical"))
    g.tag("worldgen/biome", "lacuna:has_tree/tidebloom", opt("#c:is_swamp"))
    g.tag("worldgen/biome", "lacuna:has_tree/echo_birch", opt("#c:is_birch_forest"))
