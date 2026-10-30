"""The Illusioner (illusioner/**): vanilla's unused illager brought into the game, robed in starcloth (art/m21_illusioner).

* Raids: an extra Raid.RaiderType (META-INF/enumextensions.json, illusioner/IllusionerRaids), later waves on
  normal and hard. Woodland mansions: some of the evokers they place are illusioners (illusioner/Illusioners).
* Rare night spawns in dark forests (#lacuna:has_illusioner; placement and the night rule in Java).
* Drops the Totem of Confusion now and then, as the evoker drops the Totem of Undying: a global loot modifier adds
  lacuna:extra/illusioner_totem to minecraft:entities/illusioner (vanilla drops kept).
* Its duplicates (lacuna:illusioner_double) drop nothing, are never saved, and refuse bags, banishing, capture and
  teleporting like the other conjured creatures.
"""
from content.m19_progression import add_table_modifier
from gen_resources import mod

# killed by a player: 25 %, +5 % per Looting level
TOTEM_CHANCE = 0.25
TOTEM_LOOTING_BONUS = 0.05


def totem_drop(g):
    g.data("loot_table/extra/illusioner_totem", {
        "type": "minecraft:entity", "random_sequence": mod("extra/illusioner_totem"),
        "pools": [{"bonus_rolls": 0.0, "rolls": 1.0,
                   "conditions": [
                       {"condition": "minecraft:killed_by_player"},
                       {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                        "unenchanted_chance": TOTEM_CHANCE,
                        "enchanted_chance": {"type": "minecraft:linear", "base": round(TOTEM_CHANCE + TOTEM_LOOTING_BONUS, 4),
                                             "per_level_above_first": TOTEM_LOOTING_BONUS}}],
                   "entries": [{"type": "minecraft:item", "name": "lacuna:totem_of_confusion"}]}]})
    add_table_modifier(g, "illusioner_totem_drops", "minecraft:entities/illusioner", mod("extra/illusioner_totem"))
    # every modifier must be listed (m09 and m19 wrote the list before this one was added)
    g.override("data/neoforge/loot_modifiers/global_loot_modifiers.json",
               {"replace": False, "entries": list(g.shared["loot_modifiers"])})


def spawns(g):
    # a modpack can add its own dark woods to the tag
    g.tag("worldgen/biome", "lacuna:has_illusioner", "minecraft:dark_forest")
    g.data("neoforge/biome_modifier/spawn_illusioner", {
        "type": "neoforge:add_spawns", "biomes": "#lacuna:has_illusioner",
        "spawners": [{"type": "minecraft:illusioner", "weight": 2, "minCount": 1, "maxCount": 1}]})


def duplicates(g):
    g.name("entity.lacuna.illusioner_double", "Illusioner Duplicate", "Duplicato dell'illusionista")
    g.data("loot_table/entities/illusioner_double", {"type": "minecraft:entity",
                                                     "random_sequence": "lacuna:entities/illusioner_double"})
    for tag in ("lacuna:cannot_be_banished", "lacuna:bag_refuses", "c:capturing_not_supported", "c:teleporting_not_supported"):
        g.tag("entity_type", tag, mod("illusioner_double"))


def build(g):
    g.item("illusioner_spawn_egg", "Illusioner Spawn Egg", "Uovo generatore di illusionista", model="spawn_egg")
    totem_drop(g)
    spawns(g)
    duplicates(g)
