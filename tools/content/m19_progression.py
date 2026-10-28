"""Progression pass (1.0.0-beta): the Lacunar Quintessence and where every relic comes from.

Relics (the special weapons and gadgets) are no longer crafted: their recipes are gone from their own modules and they
are found instead - Mirror Labyrinth hoards and Sunken Bell Towers (m10), ruin caches (here), village mints (m11),
fitting vanilla bosses and structures (here, through global loot modifiers) and the Last Merchant
(logic/MerchantPricing). The Lacunar Quintessence is the legendary essence every totem is crafted from (the Totems of
Banishing and Confusion in their modules, the vanilla Totem of Undying here). docs/PROGRESSION.md lists every item
and every source with its chance.
"""
from gen_resources import mod
from content import worldgen as wg
from content.m12_polish import adv, has

Q = "lacuna:lacunar_quintessence"


def build(g):
    quintessence(g)
    repair_kit(g)
    relic_tips(g)
    ruin_cache(g)
    vanilla_sources(g)


# ============================================================================================ the essence
def quintessence(g):
    g.item("lacunar_quintessence", "Lacunar Quintessence", "Quintessenza lacunare")
    g.name("item.lacuna.lacunar_quintessence.lore", "What is left when the world forgets: the exact shape of the gap.",
           "Ciò che resta quando il mondo dimentica: la forma esatta del vuoto.")
    g.name("item.lacuna.lacunar_quintessence.use",
           "Every totem is made from it: the Totems of Banishing, Confusion and Undying",
           "Ogni totem nasce da essa: i Totem dell'esilio, della Confusione e dell'immortalità")
    g.name("item.lacuna.lacunar_quintessence.tip",
           "Found where the world forgets: a defeated Reflection, a star nobody called, the deepest chests, "
           "the Last Merchant.",
           "Si trova dove il mondo dimentica: un Riflesso sconfitto, una stella che nessuno ha chiamato, i forzieri "
           "più profondi, l'Ultimo mercante.")
    # the legendary path to the vanilla totem: two emeralds for the eyes, a golden effigy around the essence
    g.shaped("totem_of_undying", ["EGE", "GQG", " G "],
             {"E": "minecraft:emerald", "G": "minecraft:gold_ingot", "Q": Q},
             "minecraft:totem_of_undying", category="equipment", unlock=Q)
    adv(g, "lacunar_quintessence", Q, {"quintessence": has(Q)}, "root", frame="goal",
        en=("The Shape of the Gap", "Obtain a Lacunar Quintessence, the essence every totem is made from."),
        it=("La forma del vuoto", "Ottieni una Quintessenza lacunare, l'essenza di cui è fatto ogni totem."))


# ============================================================================================ repair kit
def repair_kit(g):
    # metal for the patch, a gear for the mechanism, leather straps: a Smiths' Guild kit, also made at home
    g.shaped("repair_kit", ["UGU", "LIL"],
             {"U": "lacuna:umbral_steel_ingot", "G": "lacuna:clockwork_gear", "L": "minecraft:leather",
              "I": "minecraft:iron_ingot"}, "lacuna:repair_kit", category="misc", unlock="lacuna:clockwork_gear")
    # totems wear out for good (village/RepairKitItem refuses them)
    g.tag("item", "lacuna:repair_kit_refuses", "lacuna:totem_of_banishing", "lacuna:totem_of_confusion")
    g.name("item.lacuna.repair_kit.refuse", "Totems wear out for good: no kit mends them",
           "I totem si consumano per sempre: nessun kit li ripara")
    g.name("lacuna.repair_kit.refuses", "A totem wears out for good; the kit cannot mend it.",
           "Un totem si consuma per sempre; il kit non può ripararlo.")


# ============================================================================================ relic tooltips
# where each relic is found (shown with the item details); keep in step with the loot below, m10, m11 and the merchant
MIRROR = ("Mirror hoards", "tesori del Labirinto degli specchi")
RUINS = ("Forgotten ruins", "rovine dimenticate")
BELL = ("Sunken Bell Towers", "torri della campana sommersa")
MERCHANT = ("the Last Merchant", "l'Ultimo mercante")
ANCIENT = ("ancient cities", "città antiche")
BASTION = ("bastion treasure", "tesori dei bastioni")
OUTPOST = ("pillager outposts", "avamposti dei predoni")
MANSION = ("woodland mansions", "magioni della foresta")
PYRAMID = ("desert pyramids", "piramidi del deserto")
END_CITY = ("End cities", "città dell'End")
MINT = ("village mints", "zecche dei villaggi")
SUNS_FURY_FORGE = ("the Last Merchant (forged from a Solar Flare)", "l'Ultimo mercante (forgiata da un Brillamento solare)")

RELIC_SOURCES = {
    "suns_fury": [MIRROR, BELL, SUNS_FURY_FORGE],
    "eclipser": [MIRROR, RUINS, ANCIENT, MERCHANT],
    "gravemaw_greatsword": [("the Wither", "il Wither"), MIRROR, RUINS, MERCHANT],
    "tidecaller_sickle": [("Elder Guardians", "Guardiani antichi"), BELL, MERCHANT],
    "ember_triangle": [BASTION, MIRROR, RUINS, MERCHANT],
    "quickening_wand": [MANSION, MIRROR, RUINS, MERCHANT],
    "starcaller_trumpet": [BELL, OUTPOST, MERCHANT],
    "mirror_of_recursion": [MIRROR, END_CITY, RUINS, MERCHANT],
    "chronoshard_pocket_watch": [BELL, MINT, MIRROR, RUINS, MERCHANT],
    "null_lantern": [ANCIENT, MIRROR, RUINS, MERCHANT],
    "divine_magnifying_glass": [PYRAMID, MINT, MIRROR, RUINS, MERCHANT],
    "echo_dagger": [("Wardens", "Sorveglianti"), ANCIENT, MIRROR, MERCHANT],
    "pyrrhic_scepter": [("Piglin Brutes", "Piglin bruti"), BASTION, MIRROR, RUINS, MERCHANT],
    "heartwood_bow": [OUTPOST, MANSION, RUINS, MERCHANT],
    "whisperbone_flute": [ANCIENT, BELL, MIRROR, RUINS, MERCHANT],
}


def relic_tips(g):
    for relic, sources in RELIC_SOURCES.items():
        en = "Relic, never crafted: " + ", ".join(s[0] for s in sources) + "."
        it = "Reliquia, non si fabbrica: " + ", ".join(s[1] for s in sources) + "."
        g.name(f"item.lacuna.{relic}.tip", en, it)
    g.name("item.lacuna.solar_flare.tip",
           "Four fragments of a lost noon, made whole again. The Last Merchant can set it into Sun's Fury.",
           "Quattro frammenti di un mezzogiorno perduto, di nuovo interi. L'Ultimo mercante sa forgiarne la Furia del Sole.")


# ============================================================================================ ruin caches
# the rare pool of every Forgotten Land ruin cache (one roll, weights out of 1000; m02 builds the table)
RUIN_CACHE_RARE = [
    (Q, 10),
    ("lacuna:divine_magnifying_glass", 6), ("lacuna:quickening_wand", 5), ("lacuna:mirror_of_recursion", 5),
    ("lacuna:chronoshard_pocket_watch", 5), ("lacuna:null_lantern", 5), ("lacuna:pyrrhic_scepter", 5),
    ("lacuna:heartwood_bow", 5), ("lacuna:whisperbone_flute", 5), ("lacuna:gravemaw_greatsword", 4),
    ("lacuna:eclipser", 3), ("lacuna:ember_triangle", 3),
]


def ruin_cache(g):
    rare_pool = g.shared["ruin_cache_rare"]
    for item, weight in RUIN_CACHE_RARE:
        rare_pool.append(wg.loot_entry(item, weight))
    # the empty entry (first) takes whatever is left of 1000, so each weight reads as a chance in a thousand
    rare_pool[0]["weight"] = 1000 - sum(e["weight"] for e in rare_pool[1:])
    assert rare_pool[0]["type"] == "minecraft:empty" and rare_pool[0]["weight"] > 0


# ============================================================================================ vanilla sources
# bosses that fit a relic: a player kill rolls the chance (+1 % per Looting level)
BOSS_RELICS = [
    ("wither", "lacuna:gravemaw_greatsword", 0.20),
    ("elder_guardian", "lacuna:tidecaller_sickle", 0.33),
    ("warden", "lacuna:echo_dagger", 0.33),
    ("piglin_brute", "lacuna:pyrrhic_scepter", 0.08),
]
# vanilla chests that fit a relic: one extra roll per chest, weights out of 100 (the rest is nothing)
CHEST_RELICS = {
    "ancient_city": [("lacuna:null_lantern", 6), ("lacuna:whisperbone_flute", 6), ("lacuna:echo_dagger", 4),
                     ("lacuna:eclipser", 2)],
    "bastion_treasure": [("lacuna:ember_triangle", 12), ("lacuna:pyrrhic_scepter", 8)],
    "pillager_outpost": [("lacuna:heartwood_bow", 7), ("lacuna:starcaller_trumpet", 5)],
    "woodland_mansion": [("lacuna:quickening_wand", 8), ("lacuna:heartwood_bow", 7)],
    "desert_pyramid": [("lacuna:divine_magnifying_glass", 8)],
    "end_city_treasure": [("lacuna:mirror_of_recursion", 8)],
}


def player_kill_chance(chance):
    """A player kill, then the chance (+1 % per Looting level), like the m09 extra drops."""
    return [{"condition": "minecraft:killed_by_player"},
            {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
             "unenchanted_chance": chance,
             "enchanted_chance": {"type": "minecraft:linear", "base": round(chance + 0.01, 4), "per_level_above_first": 0.01}}]


def add_table_modifier(g, key, target_table, table):
    g.data(f"loot_modifiers/{key}", {
        "type": "neoforge:add_table",
        "conditions": [{"condition": "neoforge:loot_table_id", "loot_table_id": target_table}],
        "table": table})
    g.shared.setdefault("loot_modifiers", []).append(mod(key))


def vanilla_sources(g):
    for entity, relic, chance in BOSS_RELICS:
        name = f"{entity}_relic"
        g.data(f"loot_table/extra/{name}", {"type": "minecraft:entity", "random_sequence": mod(f"extra/{name}"), "pools": [
            {"bonus_rolls": 0.0, "rolls": 1.0, "conditions": player_kill_chance(chance),
             "entries": [{"type": "minecraft:item", "name": relic}]}]})
        add_table_modifier(g, f"{entity}_relic_drops", f"minecraft:entities/{entity}", mod(f"extra/{name}"))
    for chest, relics in CHEST_RELICS.items():
        name = f"chests/{chest}_relics"
        empty = 100 - sum(w for _, w in relics)
        g.data(f"loot_table/extra/{name}", {"type": "minecraft:chest", "random_sequence": mod(f"extra/{name}"), "pools": [
            {"bonus_rolls": 0.0, "rolls": 1.0,
             "entries": [{"type": "minecraft:empty", "weight": empty}] + [wg.loot_entry(i, w) for i, w in relics]}]})
        add_table_modifier(g, f"{chest}_relics", f"minecraft:chests/{chest}", mod(f"extra/{name}"))
    # every modifier must be listed (m09 wrote the list before these were added)
    g.override("data/neoforge/loot_modifiers/global_loot_modifiers.json",
               {"replace": False, "entries": list(g.shared["loot_modifiers"])})
