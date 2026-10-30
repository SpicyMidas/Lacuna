"""Structure maps and the Forgotten Land obelisk inscription (art: tools/art/m21_structure_maps.py).

Structure maps are vanilla filled maps with a target decoration, like buried treasure and explorer maps, that lead to the
Overworld's important places: the Sunken Bell Tower, the Obelisk of Unreadable Names and the ancient city (where relics
wait; vanilla cartographers already sell maps to mansions, monuments and trial chambers). exploration/StructureMaps
registers their decoration types; here are their destination tags, names (item_name) and lore, and the chests that hold
them: one extra roll per chest through a global loot modifier (add_table), the vanilla exploration_map function making
the map when the chest is filled. The Last Merchant sells one, one visit in two (exploration/MerchantMaps).

The Forgotten Land's broken obelisk bears a Forgotten Obelisk Inscription: the same block (reading, rewards, the
advancement) cut into grey ruin stone instead of the Overworld obelisk's black.
"""
import os
import re

from gen_resources import ROOT, mod
from content import m19_progression as m19

# kind: (destination structures, zoom, search radius in chunks, name en/it, lore en/it)
KINDS = {
    "sunken_bell_tower": (["lacuna:sunken_bell_tower"], 2, 50,
                          ("Sunken Bell Tower Map", "Mappa della torre campanaria sommersa"),
                          ("Where a drowned bell still rings.", "Dove una campana annegata suona ancora.")),
    "obelisk": (["lacuna:obelisk"], 1, 50,
                ("Obelisk Map", "Mappa dell'obelisco"),
                ("A black pillar of names nobody can read.", "Un pilastro nero di nomi che nessuno sa leggere.")),
    "ancient_city": (["minecraft:ancient_city"], 2, 50,
                     ("Ancient City Map", "Mappa della città antica"),
                     ("Deep below, where the silence listens.", "Laggiù nel profondo, dove il silenzio ascolta.")),
}

# chest loot table -> [(kind, chance in a hundred)]: one roll per chest, the rest is nothing
CHESTS = {
    "minecraft:chests/shipwreck_map": [("sunken_bell_tower", 15)],
    "minecraft:chests/underwater_ruin_big": [("sunken_bell_tower", 10)],
    "minecraft:chests/underwater_ruin_small": [("sunken_bell_tower", 5)],
    "minecraft:chests/village/village_cartographer": [("obelisk", 15), ("ancient_city", 5), ("sunken_bell_tower", 5)],
    "minecraft:chests/stronghold_library": [("ancient_city", 12)],
    "lacuna:chests/village_mint": [("obelisk", 6)],
    "lacuna:chests/sunken_bell_tower": [("ancient_city", 8), ("obelisk", 5)],
}
# short names for the Almanac's "where to find it" line
CHEST_NAMES = {
    "minecraft:chests/shipwreck_map": ("shipwreck maps", "mappe dei relitti"),
    "minecraft:chests/underwater_ruin_big": ("big ocean ruins", "grandi rovine oceaniche"),
    "minecraft:chests/underwater_ruin_small": ("small ocean ruins", "piccole rovine oceaniche"),
    "minecraft:chests/village/village_cartographer": ("cartographers' houses", "case dei cartografi"),
    "minecraft:chests/stronghold_library": ("stronghold libraries", "biblioteche delle roccaforti"),
    "lacuna:chests/village_mint": ("village mints", "zecche dei villaggi"),
    "lacuna:chests/sunken_bell_tower": ("bell tower chests", "forzieri delle torri campanarie"),
}
KIND_NAMES = {"sunken_bell_tower": ("Bell tower", "Torre campanaria"), "obelisk": ("Obelisk", "Obelisco"),
              "ancient_city": ("Ancient city", "Città antica")}


def build(g):
    forgotten_inscription(g)
    structure_maps(g)


# ============================================================================================ forgotten inscription
def forgotten_inscription(g):
    g.cube("forgotten_obelisk_inscription", "Forgotten Obelisk Inscription", "Iscrizione dell'obelisco dimenticato", loot=None)
    g.name("item.lacuna.forgotten_obelisk_inscription.tip", "Grey as the land that forgot them; the names still move.",
           "Grigia come la terra che li ha dimenticati; i nomi si muovono ancora.")
    # unbreakable ruin piece (dragon/wither immune, never relocated), structure-only: out of the recipe viewers
    g.tag("block", "lacuna:ruin_blocks", mod("forgotten_obelisk_inscription"))
    g.tag("block", "c:hidden_from_recipe_viewers", mod("forgotten_obelisk_inscription"))
    g.tag("item", "c:hidden_from_recipe_viewers", mod("forgotten_obelisk_inscription"))


# ============================================================================================ structure maps
def map_entry(kind, weight):
    structures, zoom, radius, _name, _lore = KINDS[kind]
    return {"type": "minecraft:item", "name": "minecraft:map", "weight": weight, "functions": [
        {"function": "minecraft:exploration_map", "destination": f"lacuna:on_{kind}_maps", "decoration": f"lacuna:{kind}",
         "zoom": zoom, "search_radius": radius, "skip_existing_chunks": True},
        {"function": "minecraft:set_name", "target": "item_name", "name": {"translate": f"filled_map.lacuna.{kind}"}},
        {"function": "minecraft:set_lore", "mode": "append",
         "lore": [{"translate": f"filled_map.lacuna.{kind}.lore", "color": "gray"}]}]}


def structure_maps(g):
    for kind, (structures, _zoom, _radius, name, lore) in KINDS.items():
        g.tag("worldgen/structure", f"lacuna:on_{kind}_maps", *structures)
        g.name(f"filled_map.lacuna.{kind}", *name)
        g.name(f"filled_map.lacuna.{kind}.lore", *lore)
    for table, maps in CHESTS.items():
        key = table.split(":", 1)[1].split("/")[-1]
        name = f"maps/{key}"
        empty = 100 - sum(w for _, w in maps)
        g.data(f"loot_table/extra/{name}", {"type": "minecraft:chest", "random_sequence": mod(f"extra/{name}"), "pools": [
            {"bonus_rolls": 0.0, "rolls": 1.0,
             "entries": [{"type": "minecraft:empty", "weight": empty}] + [map_entry(k, w) for k, w in maps]}]})
        m19.add_table_modifier(g, f"{key}_structure_maps", table, mod(f"extra/{name}"))
    g.finalizer(list_modifiers)


def list_modifiers(g):
    # every modifier must be listed: rewrite the list once every module has added its own
    g.override("data/neoforge/loot_modifiers/global_loot_modifiers.json",
               {"replace": False, "entries": list(g.shared["loot_modifiers"])})


# ============================================================================================ the Almanac's lines
def merchant_numbers():
    """(chance, {kind: price}) from exploration/MerchantMaps.java, so the book never disagrees with the merchant."""
    with open(os.path.join(ROOT, "src", "main", "java", "com", "lacuna", "mod", "exploration", "MerchantMaps.java"),
              encoding="utf-8") as fh:
        src = fh.read()
    chance = float(re.search(r"\bCHANCE\s*=\s*([0-9.]+)F;", src).group(1))
    prices = {k: int(re.search(r"\b" + k.upper() + r"_PRICE\s*=\s*(\d+);", src).group(1)) for k in KINDS}
    return chance, prices


def where():
    """The Almanac page's "where to find it" line (en, it): every chest and chance, and the Last Merchant."""
    en, it = [], []
    for kind in KINDS:
        found = sorted(((w, table) for table, maps in CHESTS.items() for k, w in maps if k == kind), key=lambda e: -e[0])
        en.append(f"{KIND_NAMES[kind][0]}: " + ", ".join(f"{CHEST_NAMES[t][0]} {w}%" for w, t in found) + ".")
        it.append(f"{KIND_NAMES[kind][1]}: " + ", ".join(f"{CHEST_NAMES[t][1]} {w}%" for w, t in found) + ".")
    chance, prices = merchant_numbers()
    lo, hi = min(prices.values()), max(prices.values())
    share = round(1 / chance)
    visits = ("half his visits", "metà delle sue visite") if share == 2 else (f"one visit in {share}", f"una visita su {share}")
    en.append(f"Last Merchant: one map, {visits[0]}, {lo}-{hi} rubies.")
    it.append(f"Ultimo mercante: una mappa, {visits[1]}, {lo}-{hi} rubini.")
    return " ".join(en), " ".join(it)
