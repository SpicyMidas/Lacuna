"""The Lacuna Almanac: the in-game guide book (guide/AlmanacItem, client/guide; art: tools/art/m20_almanac.py).

The book's table of contents is data: CHAPTERS and ENTRIES below are written to assets/lacuna/almanac/entries.json, and
every text goes to the lang files (pages and their Italian in almanac_pages.py). Adding a page is one ENTRIES row plus
its PAGES text. Relic sources (and the Quintessence's) are not typed by hand: they are read from the loot tables the
other modules generate (Mirror Hoard, Sunken Bell Tower, ruin caches, village mints, vanilla bosses and chests) and from
the Last Merchant's prices in logic/MerchantPricing.java, so the book never disagrees with the game.

Every player gets an Almanac on first joining a world (config almanac.giveOnFirstJoin); a book and a ruby make another.
"""
import json
import os
import re

from gen_resources import ROOT
from content import m19_progression as m19
from content.almanac_pages import PAGES

ALMANAC = "lacuna:almanac"

# id, icon, title en, title it (book order)
CHAPTERS = [
    ("getting_started", "minecraft:compass", "Getting Started", "Primi passi"),
    ("forgotten_land", "lacuna:chiseled_ruin_glyph", "The Forgotten Land", "La Terra Dimenticata"),
    ("relics", "lacuna:eclipser", "Relics", "Reliquie"),
    ("totems", "lacuna:lacunar_quintessence", "Totems & Quintessence", "Totem e Quintessenza"),
    ("gear", "lacuna:umbral_steel_pickaxe", "Gear & Tools", "Equipaggiamento"),
    ("materials", "lacuna:ruby", "Materials & Ores", "Materiali e minerali"),
    ("blocks", "lacuna:cindervine_planks", "Blocks & Building", "Blocchi e costruzione"),
    ("villages", "lacuna:village_ledger", "Villages", "Villaggi"),
    ("creatures", "lacuna:lantern_moth_spawn_egg", "Creatures & Events", "Creature ed eventi"),
]

# One row per page, in book order: id, chapter, icon, extra icons[, title lang key]. A page without a title of its own
# (almanac_pages.py) is titled by the key given here, else by its icon's name.
ENTRIES = [
    ("welcome", "getting_started", "lacuna:almanac", []),
    ("path", "getting_started", "lacuna:totem_of_banishing", ["lacuna:ruby", "lacuna:lacunar_quintessence"]),
    ("mending", "getting_started", "minecraft:anvil", ["lacuna:repair_kit"]),

    ("forgotten_land", "forgotten_land", "lacuna:chiseled_ruin_glyph", []),
    ("forgotten_portal", "forgotten_land", "lacuna:forgotten_portal_frame", ["minecraft:crying_obsidian"],
     "block.lacuna.forgotten_portal"),
    ("forgotten_plains", "forgotten_land", "lacuna:ruin_stone", ["lacuna:echo_birch_sapling", "lacuna:divine_glass_cluster"],
     "biome.lacuna.forgotten_plains"),
    ("cinderfrost_flats", "forgotten_land", "lacuna:ashen_snow_block", ["lacuna:clear_ice", "lacuna:cindervine_sapling"],
     "biome.lacuna.cinderfrost_flats"),
    ("inverted_falls", "forgotten_land", "lacuna:updraft_spring", ["lacuna:zenithite_ore", "lacuna:crystal_willow_sapling"],
     "biome.lacuna.inverted_falls"),
    ("whispering_marsh", "forgotten_land", "lacuna:spore_mushroom", ["lacuna:tidebloom_roots", "lacuna:lantern_moth_spawn_egg"],
     "biome.lacuna.whispering_marsh"),
    ("clockwork_dunes", "forgotten_land", "lacuna:buried_watch", ["lacuna:rusted_gear", "lacuna:chronal_shard"],
     "biome.lacuna.clockwork_dunes"),
    ("ruins", "forgotten_land", "lacuna:ruin_stone_bricks", ["lacuna:cracked_ruin_stone_bricks", "lacuna:ruin_pillar",
                                                             "minecraft:chest"]),
    ("memory_murals", "forgotten_land", "minecraft:painting", ["lacuna:mural_block_1", "lacuna:mural_block_2",
                                                               "lacuna:mural_block_5"]),
    ("mirror_labyrinth", "forgotten_land", "lacuna:mirror_glass", ["lacuna:labyrinth_trigger", "lacuna:labyrinth_exit"]),

    ("about_relics", "relics", "minecraft:chest", []),
    ("treasure", "relics", "minecraft:wither_skeleton_skull", ["minecraft:trial_key"]),
    ("suns_fury", "relics", "lacuna:suns_fury", ["lacuna:solar_flare"]),
    ("eclipser", "relics", "lacuna:eclipser", []),
    ("shadow_walk", "relics", "minecraft:ender_eye", []),
    ("gravemaw_greatsword", "relics", "lacuna:gravemaw_greatsword", []),
    ("tidecaller_sickle", "relics", "lacuna:tidecaller_sickle", ["minecraft:prismarine_shard"]),
    ("ember_triangle", "relics", "lacuna:ember_triangle", ["lacuna:ember"]),
    ("quickening_wand", "relics", "lacuna:quickening_wand", ["lacuna:silk_dust"]),
    ("starcaller_trumpet", "relics", "lacuna:starcaller_trumpet", ["lacuna:star_fragment"]),
    ("mirror_of_recursion", "relics", "lacuna:mirror_of_recursion", []),
    ("chronoshard_pocket_watch", "relics", "lacuna:chronoshard_pocket_watch", ["lacuna:chronal_shard"]),
    ("null_lantern", "relics", "lacuna:null_lantern", ["minecraft:ender_pearl", "minecraft:echo_shard"]),
    ("divine_magnifying_glass", "relics", "lacuna:divine_magnifying_glass", []),
    ("echo_dagger", "relics", "lacuna:echo_dagger", []),
    ("pyrrhic_scepter", "relics", "lacuna:pyrrhic_scepter", []),
    ("heartwood_bow", "relics", "lacuna:heartwood_bow", ["lacuna:echo_birch_planks"]),
    ("whisperbone_flute", "relics", "lacuna:whisperbone_flute", ["lacuna:echo_fragment"]),

    ("lacunar_quintessence", "totems", "lacuna:lacunar_quintessence", []),
    ("totem_of_banishing", "totems", "lacuna:totem_of_banishing", []),
    ("totem_of_confusion", "totems", "lacuna:totem_of_confusion", []),
    ("totem_of_undying", "totems", "minecraft:totem_of_undying", []),

    ("umbral_steel_tools", "gear", "lacuna:umbral_steel_sword", ["lacuna:umbral_steel_pickaxe", "lacuna:umbral_steel_axe",
                                                                 "lacuna:umbral_steel_shovel", "lacuna:umbral_steel_hoe"]),
    ("starlace_armor", "gear", "lacuna:starlace_chestplate", ["lacuna:starlace_helmet", "lacuna:starlace_leggings",
                                                             "lacuna:starlace_boots", "lacuna:starlace_cloth"]),
    ("starlace_bandage", "gear", "lacuna:starlace_bandage", []),
    ("fractured_pearl", "gear", "lacuna:fractured_pearl", ["lacuna:arrival_shard", "lacuna:departure_shard"]),
    ("comically_large_bag", "gear", "lacuna:comically_large_bag", []),
    ("chrono_hourglass", "gear", "lacuna:chrono_hourglass", ["lacuna:chronal_shard"]),
    ("clockwork_decoy", "gear", "lacuna:clockwork_decoy", []),
    ("repair_kit", "gear", "lacuna:repair_kit", []),
    ("wayfarers_chart", "gear", "lacuna:wayfarers_chart", []),

    ("ruby", "materials", "lacuna:ruby", ["lacuna:ruby_ore", "lacuna:deepslate_ruby_ore", "lacuna:ruby_block"]),
    ("umbral_steel", "materials", "lacuna:umbral_steel_ingot", ["lacuna:umbral_steel_ore", "lacuna:raw_umbral_steel",
                                                                "lacuna:umbral_steel_nugget", "lacuna:umbral_steel_block"]),
    ("divine_glass", "materials", "lacuna:divine_glass_shard", ["lacuna:divine_glass_cluster", "lacuna:divine_glass",
                                                                "lacuna:divine_glass_pane"]),
    ("zenithite", "materials", "lacuna:zenithite_shard", ["lacuna:zenithite_ore", "lacuna:deepslate_zenithite_ore",
                                                          "lacuna:zenithite_block"]),
    ("exilite", "materials", "lacuna:exilite_ingot", ["lacuna:exilite_debris", "lacuna:exilite_shard", "lacuna:exilite_block"]),
    ("star_rock", "materials", "lacuna:star_fragment", ["lacuna:molten_star_rock", "lacuna:prime_star_rock",
                                                        "lacuna:extinguished_star_rock"]),
    ("solar_flare", "materials", "lacuna:solar_flare", ["lacuna:solar_flare_fragment"]),
    ("chronal_shard", "materials", "lacuna:chronal_shard", ["lacuna:buried_watch"]),
    ("echo_fragment", "materials", "lacuna:echo_fragment", []),
    ("ember", "materials", "lacuna:ember", []),
    ("crystal_willow_shard", "materials", "lacuna:crystal_willow_shard", []),
    ("silk_dust", "materials", "lacuna:silk_dust", []),
    ("clockwork_gear", "materials", "lacuna:clockwork_gear", []),

    ("cindervine", "blocks", "lacuna:cindervine_log", ["lacuna:cindervine_planks", "lacuna:cindervine_leaves",
                                                       "lacuna:cindervine_sapling", "lacuna:cindervine_door"]),
    ("crystal_willow", "blocks", "lacuna:crystal_willow_log", ["lacuna:crystal_willow_planks", "lacuna:crystal_willow_leaves",
                                                               "lacuna:crystal_willow_strands", "lacuna:crystal_willow_sapling"]),
    ("tidebloom", "blocks", "lacuna:tidebloom_log", ["lacuna:tidebloom_planks", "lacuna:tidebloom_leaves",
                                                     "lacuna:tidebloom_roots", "lacuna:tidebloom_sapling"]),
    ("echo_birch", "blocks", "lacuna:echo_birch_log", ["lacuna:echo_birch_planks", "lacuna:echo_birch_leaves",
                                                       "lacuna:echo_birch_sapling", "lacuna:echo_birch_door"]),
    ("exilite_stonework", "blocks", "lacuna:chiseled_exilite", ["lacuna:exilite", "lacuna:polished_exilite",
                                                                "lacuna:exilite_bricks", "lacuna:exilite_brick_stairs",
                                                                "lacuna:exilite_brick_wall"]),

    ("civic_villages", "villages", "minecraft:bell", []),
    ("village_ledger", "villages", "lacuna:village_ledger", []),
    ("reputation", "villages", "minecraft:emerald", []),
    ("laws", "villages", "minecraft:iron_sword", ["minecraft:bell"]),
    ("council", "villages", "lacuna:council_table", []),
    ("village_coin", "villages", "lacuna:village_coin", ["lacuna:mint_press"]),
    ("court", "villages", "lacuna:judge_bench", []),
    ("guilds", "villages", "lacuna:guild_board", ["lacuna:guild_contract"]),
    ("schools", "villages", "lacuna:school_desk", ["minecraft:lectern"]),

    ("lantern_moth", "creatures", "lacuna:lantern_moth_spawn_egg", ["lacuna:silk_dust"], "entity.lacuna.lantern_moth"),
    ("last_merchant", "creatures", "lacuna:last_merchant_spawn_egg", ["lacuna:ruby"], "entity.lacuna.last_merchant"),
    ("reflection", "creatures", "lacuna:labyrinth_trigger", ["lacuna:lacunar_quintessence"], "entity.lacuna.reflection"),
    ("illusioner", "creatures", "lacuna:illusioner_spawn_egg", ["lacuna:totem_of_confusion", "minecraft:bow"]),
    ("falling_stars", "creatures", "lacuna:prime_star_rock", ["lacuna:star_fragment"]),
    ("sunken_bell_tower", "creatures", "lacuna:sunken_bell", []),
    ("obelisk", "creatures", "lacuna:obelisk_inscription", []),
    ("structure_maps", "creatures", "minecraft:filled_map", ["minecraft:map", "lacuna:sunken_bell", "lacuna:obelisk_inscription",
                                                             "minecraft:sculk_shrieker"]),
    ("common_creatures", "creatures", "minecraft:husk_spawn_egg", ["minecraft:spider_spawn_egg", "minecraft:pillager_spawn_egg"]),
]

UI = {
    "title_big": ("Lacuna", "Lacuna"),
    "title_small": ("Almanac", "Almanacco"),
    "tagline": ("What the world forgot, and where to find it.", "Ciò che il mondo ha dimenticato, e dove trovarlo."),
    "contents": ("Contents", "Indice"),
    "where": ("Where to find it", "Dove trovarlo"),
    "previous": ("Previous page", "Pagina precedente"),
    "next": ("Next page", "Pagina successiva"),
}


def build(g):
    g.item("almanac", "Lacuna Almanac", "Almanacco di Lacuna")
    g.name("item.lacuna.almanac.lore", "Everything the world forgot, written down before it is gone.",
           "Tutto ciò che il mondo ha dimenticato, scritto prima che svanisca.")
    g.name("item.lacuna.almanac.use", "Use: open it. What everything does, and where to find it.",
           "Usa: aprilo. A cosa serve ogni cosa, e dove trovarla.")
    g.name("item.lacuna.almanac.tip",
           "The tabs on its fore-edge open the chapters; Home goes back to the contents.",
           "Le linguette sul taglio aprono i capitoli; Inizio torna all'indice.")
    g.name("lacuna.configuration.almanac", "Almanac", "Almanacco")
    g.shapeless("almanac", ["minecraft:book", "lacuna:ruby"], ALMANAC, category="misc", unlock="lacuna:ruby")
    for key, (en, it) in UI.items():
        g.name(f"almanac.lacuna.ui.{key}", en, it)
    for cid, _icon, en, it in CHAPTERS:
        g.name(f"almanac.lacuna.chapter.{cid}", en, it)
    # the pages are written once every module has added its loot (relic sources are read from it)
    g.finalizer(write_book)


# ============================================================================================ the book

def write_book(g):
    chapters = {c[0] for c in CHAPTERS}
    ids = [row[0] for row in ENTRIES]
    assert len(ids) == len(set(ids)), "duplicate almanac entry"
    assert set(ids) == set(PAGES), f"entries without pages / pages without entries: {set(ids) ^ set(PAGES)}"
    sources = relic_sources(g)
    entries = []
    for row in ENTRIES:
        eid, chapter, icon, extras = row[:4]
        assert chapter in chapters, f"almanac entry {eid}: unknown chapter {chapter}"
        for item in [icon, *extras]:
            check_item(g, item, eid)
        (title_en, text_en, where_en), (title_it, text_it, where_it) = PAGES[eid]
        if title_en is not None:
            title_key = f"almanac.lacuna.{eid}"
            g.name(title_key, title_en, title_it)
        else:
            title_key = row[4] if len(row) > 4 else name_key(g, icon)
            assert title_key in g.lang, f"almanac entry {eid}: no lang key {title_key}"
        if text_en is None:
            text_en, text_it = generated_text(g, eid, sources)
        g.name(f"almanac.lacuna.{eid}.text", text_en, text_it)
        entry = {"id": eid, "chapter": chapter, "icon": icon, "title": title_key, "text": f"almanac.lacuna.{eid}.text"}
        if where_en is None and eid == "lacunar_quintessence":
            where_en, where_it = quintessence_where(g)
        elif where_en is None and eid in sources:
            where_en, where_it = where_line(sources[eid])
        if where_en is not None:
            g.name(f"almanac.lacuna.{eid}.where", where_en, where_it)
            entry["where"] = f"almanac.lacuna.{eid}.where"
        if extras:
            entry["icons"] = list(extras)
        entries.append(entry)
    g.asset("almanac/entries", {
        "chapters": [{"id": cid, "icon": icon, "title": f"almanac.lacuna.chapter.{cid}"} for cid, icon, _en, _it in CHAPTERS],
        "entries": entries})


def name_key(g, item):
    path = item.split(":", 1)[1]
    for key in (f"item.lacuna.{path}", f"block.lacuna.{path}"):
        if key in g.lang:
            return key
    raise ValueError(f"almanac: {item} has no name to title its page with")


_VANILLA_ITEMS = None


def check_item(g, item, eid):
    global _VANILLA_ITEMS
    ns, path = item.split(":", 1)
    if ns == "lacuna":
        assert f"assets/lacuna/models/item/{path}.json" in g.files, f"almanac entry {eid}: {item} is not an item"
        return
    if _VANILLA_ITEMS is None:
        with open(os.path.join(ROOT, "tools", "vanilla_1.21.1.json"), encoding="utf-8") as fh:
            _VANILLA_ITEMS = set(json.load(fh)["item"])
    assert ns == "minecraft" and path in _VANILLA_ITEMS, f"almanac entry {eid}: unknown item {item}"


# ============================================================================================ where relics come from

SOURCE_NAMES = {
    "hoard": ("Mirror Hoards", "tesori del Labirinto degli specchi"),
    "bell": ("Sunken Bell Towers", "torri della campana sommersa"),
    "ruins": ("ruin caches", "forzieri delle rovine"),
    "mint": ("village mints", "zecche dei villaggi"),
    "ancient_city": ("ancient city chests", "forzieri delle città antiche"),
    "bastion_treasure": ("bastion treasure", "tesori dei bastioni"),
    "pillager_outpost": ("pillager outposts", "avamposti dei predoni"),
    "woodland_mansion": ("woodland mansions", "magioni della foresta"),
    "desert_pyramid": ("desert pyramids", "piramidi del deserto"),
    "end_city_treasure": ("End city treasure", "tesori delle città dell'End"),
    "wither": ("the Wither", "il Wither"),
    "elder_guardian": ("Elder Guardians", "Guardiani antichi"),
    "warden": ("the Warden", "il Sorvegliante"),
    "piglin_brute": ("Piglin Brutes", "Piglin bruti"),
}
CHESTS = {"hoard": "chests/mirror_hoard", "bell": "chests/sunken_bell_tower", "ruins": "chests/ruin_cache",
          "mint": "chests/village_mint"}


def single_roll_chances(table):
    """{item: chance} over the table's one-roll pools (weight / total weight of the pool)."""
    out = {}
    for pool in table.get("pools", []):
        if pool.get("rolls") != 1.0:
            continue
        total = sum(e.get("weight", 1) for e in pool["entries"])
        for e in pool["entries"]:
            if e.get("type") == "minecraft:item":
                out[e["name"]] = out.get(e["name"], 0.0) + e.get("weight", 1) / total
    return out


def java_source(rel):
    with open(os.path.join(ROOT, "src", "main", "java", "com", "lacuna", "mod", rel), encoding="utf-8") as fh:
        return fh.read()


def java_number(rel, name):
    m = re.search(r"\b" + name + r"\s*=\s*([0-9.]+)F?;", java_source(rel))
    assert m, f"{rel}: constant {name} not found"
    return float(m.group(1))


def merchant_relics():
    src = java_source("logic/MerchantPricing.java")
    block = re.search(r"RELICS\s*=\s*\{(.*?)\};", src, re.S)
    assert block, "MerchantPricing.RELICS not found"
    return {item: int(price) for item, price in re.findall(r'\{"(lacuna:[a-z_]+)",\s*(\d+)\}', block.group(1))}


def rubies(price):
    """A merchant price: up to 64 rubies, the rest in ruby blocks (9 each)."""
    if price <= 64:
        return f"{price} rubies", f"{price} rubini"
    blocks = -(-(price - 64) // 9)
    return f"64 rubies + {blocks} ruby blocks", f"64 rubini + {blocks} blocchi di rubino"


def pct(chance, it=False):
    s = f"{chance * 100:.1f}".rstrip("0").rstrip(".")
    return (s.replace(".", ",") if it else s) + "%"


def relic_sources(g):
    """{relic id: [(chance or None, en, it), ...]} from the generated loot tables and the merchant's price list."""
    found = {}

    def add(item, chance, key, looting=False):
        if not item.startswith("lacuna:"):
            return
        en, it = SOURCE_NAMES[key]
        found.setdefault(item.split(":", 1)[1], []).append(
            (chance, f"{en} {pct(chance)}" + (" (+Looting)" if looting else ""),
             f"{it} {pct(chance, True)}" + (" (+Saccheggio)" if looting else "")))

    for key, table in CHESTS.items():
        for item, chance in single_roll_chances(g.files[f"data/lacuna/loot_table/{table}.json"]).items():
            add(item, chance, key)
    for chest in m19.CHEST_RELICS:
        for item, chance in single_roll_chances(g.files[f"data/lacuna/loot_table/extra/chests/{chest}_relics.json"]).items():
            add(item, chance, chest)
    for entity, item, chance in m19.BOSS_RELICS:
        add(item, chance, entity, looting=True)
    for item, price in merchant_relics().items():
        en, it = rubies(price)
        found.setdefault(item.split(":", 1)[1], []).append((None, f"the Last Merchant, {en}", f"l'Ultimo mercante, {it}"))
    forge = int(java_number("logic/MerchantPricing.java", "SUNS_FURY_FORGE_PRICE"))
    found.setdefault("suns_fury", []).append(
        (None, f"the Last Merchant forges one from a Solar Flare for {forge} rubies",
         f"l'Ultimo mercante ne forgia una da un Brillamento solare per {forge} rubini"))
    # only the relics keep generated lines (rubies, shards and the bag also turn up in these tables)
    relics = set(m19.RELIC_SOURCES)
    return {k: v for k, v in found.items() if k in relics}


def capital(s):
    return s[0].upper() + s[1:]


def where_line(sources):
    ordered = sorted(sources, key=lambda s: (s[0] is None, -(s[0] or 0.0)))
    return capital(" · ".join(s[1] for s in ordered)) + ".", capital(" · ".join(s[2] for s in ordered)) + "."


def quintessence_where(g):
    """Every source of the Lacunar Quintessence, with the chances read from the loot tables and the Java constants."""
    q = "lacuna:lacunar_quintessence"
    reflection = g.files["data/lacuna/loot_table/entities/reflection.json"]
    assert q in json.dumps(reflection), "the Reflection no longer drops the Quintessence"
    star = java_number("essence/Quintessence.java", "NATURAL_STAR_CHANCE")
    bell = single_roll_chances(g.files["data/lacuna/loot_table/chests/sunken_bell_tower.json"])[q]
    ruins = single_roll_chances(g.files["data/lacuna/loot_table/chests/ruin_cache.json"])[q]
    prime = None
    for pool in g.files["data/lacuna/loot_table/blocks/prime_star_rock.json"]["pools"]:
        if any(e.get("name") == q for e in pool["entries"]):
            prime = next(c["chance"] for c in pool["conditions"] if c["condition"] == "minecraft:random_chance")
    assert prime is not None, "Prime Star Rock no longer drops the Quintessence"
    visits = round(1.0 / java_number("logic/MerchantPricing.java", "QUINTESSENCE_CHANCE"))
    price_en, price_it = rubies(int(java_number("logic/MerchantPricing.java", "QUINTESSENCE_PRICE")))
    en = (f"A defeated Reflection, always (once per labyrinth) · natural falling stars {pct(star)} · Sunken Bell Towers "
          f"{pct(bell)} · Prime Star Rock {pct(prime)} (without Silk Touch) · ruin caches {pct(ruins)} · the Last "
          f"Merchant, one visit in {visits}, for {price_en}.")
    it = (f"Un Riflesso sconfitto, sempre (una volta per labirinto) · stelle cadenti naturali {pct(star, True)} · torri "
          f"della campana sommersa {pct(bell, True)} · Roccia stellare viva {pct(prime, True)} (senza Tocco di velluto) · "
          f"forzieri delle rovine {pct(ruins, True)} · l'Ultimo mercante, una visita su {visits}, per {price_it}.")
    return en, it


def generated_text(g, eid, sources):
    if eid == "lacunar_quintessence":
        raise AssertionError("the Quintessence page has its own text")
    if eid == "treasure":
        return treasure_text(g)
    raise AssertionError(f"almanac entry {eid} has no text")


def treasure_text(g):
    """The vanilla bosses and chests that hold relics (m19_progression), one bullet each."""
    lines_en = ["Some relics went where the old powers sleep, or lie in treasure that suits them:"]
    lines_it = ["Alcune reliquie sono andate dove dormono gli antichi poteri, o giacciono in tesori adatti a loro:"]

    def names(item):
        key = f"item.lacuna.{item.split(':', 1)[1]}"
        return g.lang[key], g.lang_it.get(key, g.lang[key])

    for entity, item, chance in m19.BOSS_RELICS:
        en, it = SOURCE_NAMES[entity]
        n_en, n_it = names(item)
        lines_en.append(f"- **{en[0].upper() + en[1:]}:** {n_en} {pct(chance)}")
        lines_it.append(f"- **{it[0].upper() + it[1:]}:** {n_it} {pct(chance, True)}")
    for chest, relics in m19.CHEST_RELICS.items():
        en, it = SOURCE_NAMES[chest]
        chances = single_roll_chances(g.files[f"data/lacuna/loot_table/extra/chests/{chest}_relics.json"])
        parts_en = [f"{names(i)[0]} {pct(chances[i])}" for i, _w in relics]
        parts_it = [f"{names(i)[1]} {pct(chances[i], True)}" for i, _w in relics]
        lines_en.append(f"- **{en[0].upper() + en[1:]}:** " + ", ".join(parts_en))
        lines_it.append(f"- **{it[0].upper() + it[1:]}:** " + ", ".join(parts_it))
    return "\n".join(lines_en), "\n".join(lines_it)
