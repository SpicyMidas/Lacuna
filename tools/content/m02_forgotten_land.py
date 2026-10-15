"""M2: Forgotten Land dimension, ruins, murals, Totem of Banishing, banishment, relics."""
from gen_resources import mod
from content import worldgen as wg

RUIN_STRUCTURES = {
    # name: (variants, structure set)
    "broken_arch": (3, "ruin_clusters"),
    "fallen_colonnade": (2, "ruin_clusters"),
    "ruined_hall": (2, "forgotten_ruins"),
    "sunken_plaza": (1, "forgotten_ruins"),
    "memory_shrine": (2, "forgotten_ruins"),
    "obelisk_ruin": (1, "forgotten_ruins"),
}

MURALS = [
    ("memory_dawn", 2, 2, "Memory: Dawn over the Spires", "Memoria: alba sulle guglie"),
    ("memory_spire", 3, 2, "Memory: The Bright Spire", "Memoria: la guglia luminosa"),
    ("memory_harvest", 2, 2, "Memory: Harvest Day", "Memoria: giorno del raccolto"),
    ("memory_festival", 3, 2, "Memory: Festival of Lanterns", "Memoria: festa delle lanterne"),
    ("memory_fall", 2, 2, "Memory: The Fall", "Memoria: la caduta"),
]


def build(g):
    ruins(g)
    murals(g)
    totem(g)
    dimension(g)
    structures(g)
    messages(g)
    portal(g)


def ruins(g):
    g.cube("ruin_stone", "Ruin Stone", "Pietra delle rovine")
    g.cube("ruin_stone_bricks", "Ruin Stone Bricks", "Mattoni di pietra delle rovine")
    g.cube("cracked_ruin_stone_bricks", "Cracked Ruin Stone Bricks", "Mattoni di pietra delle rovine incrinati")
    g.column("ruin_pillar", "Ruin Pillar", "Pilastro delle rovine")
    g.cube("chiseled_ruin_glyph", "Chiseled Ruin Glyph", "Glifo delle rovine cesellato")
    g.slab("ruin_slab", "Ruin Slab", "Lastra delle rovine", "ruin_stone_bricks", "lacuna:block/ruin_stone_bricks")
    g.stairs("ruin_stairs", "Ruin Stairs", "Scale delle rovine", "lacuna:block/ruin_stone_bricks")
    g.wall("ruin_wall", "Ruin Wall", "Muretto delle rovine", "lacuna:block/ruin_stone_bricks")
    ruin_ids = ["ruin_stone", "ruin_stone_bricks", "cracked_ruin_stone_bricks", "ruin_pillar", "chiseled_ruin_glyph",
                "ruin_slab", "ruin_stairs", "ruin_wall"]
    for i in range(1, 6):
        ruin_ids.append(f"mural_block_{i}")
    g.tag("block", "lacuna:ruin_blocks", *[mod(b) for b in ruin_ids])
    for t in ("minecraft:dragon_immune", "minecraft:wither_immune", "minecraft:features_cannot_replace"):
        g.tag("block", t, "#lacuna:ruin_blocks")
    g.tag("block", "minecraft:walls", mod("ruin_wall"))
    g.tag("item", "minecraft:walls", mod("ruin_wall"))
    g.tag("block", "minecraft:slabs", mod("ruin_slab"))
    g.tag("item", "minecraft:slabs", mod("ruin_slab"))
    g.tag("block", "minecraft:stairs", mod("ruin_stairs"))
    g.tag("item", "minecraft:stairs", mod("ruin_stairs"))
    g.tag("block", "lacuna:solar_immune", "#lacuna:ruin_blocks")


def murals(g):
    for idx, (name, w, h, en, it) in enumerate(MURALS, start=1):
        g.data(f"painting_variant/{name}", {"asset_id": mod(name), "width": w, "height": h})
        g.tag("painting_variant", "minecraft:placeable", mod(name))
        g.name(f"painting.lacuna.{name}.title", en, it)
        g.name(f"painting.lacuna.{name}.author", "Unknown hand", "Mano sconosciuta")
        bid = f"mural_block_{idx}"
        g.single(bid, f"Mural Fragment ({en.split(': ')[1]})", f"Frammento di affresco ({it.split(': ')[1]})",
                 {"parent": "minecraft:block/orientable", "textures": {
                     "front": f"lacuna:block/{bid}", "side": "lacuna:block/ruin_stone_bricks",
                     "top": "lacuna:block/ruin_stone_bricks"}},
                 blockstate={"variants": {
                     "facing=north": {"model": f"lacuna:block/{bid}"},
                     "facing=east": {"model": f"lacuna:block/{bid}", "y": 90},
                     "facing=south": {"model": f"lacuna:block/{bid}", "y": 180},
                     "facing=west": {"model": f"lacuna:block/{bid}", "y": 270}}})
        g.props(bid, facing=["north", "south", "west", "east"])


def totem(g):
    g.item("totem_of_banishing", "Totem of Banishing", "Totem dell'esilio")
    g.name("item.lacuna.totem_of_banishing.lore", "A stone hand that remembers a land no one else does.",
           "Una mano di pietra che ricorda una terra che nessun altro ricorda.")
    g.name("item.lacuna.totem_of_banishing.use", "Use: enter or leave the Forgotten Land. Strike: banish the target.",
           "Usa: entra o esci dalla Terra Dimenticata. Colpisci: esilia il bersaglio.")
    g.name("item.lacuna.totem_of_banishing.portal", "Use on a ring of Forgotten Portal Frames: light the portal.",
           "Usa su un anello di cornici del portale dimenticato: accende il portale.")
    g.name("item.lacuna.totem_of_banishing.wear", "Wears out after 6 uses; it cannot be repaired.",
           "Si consuma dopo 6 usi; non può essere riparato.")
    g.shaped("totem_of_banishing", ["RGR", "GTG", " R "],
             {"R": "lacuna:ruby", "G": "minecraft:gold_ingot", "T": "minecraft:totem_of_undying"},
             "lacuna:totem_of_banishing", category="misc", unlock="minecraft:totem_of_undying")
    g.tag("entity_type", "lacuna:cannot_be_banished", "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden",
          "minecraft:elder_guardian")


def dimension(g):
    g.data("dimension_type/forgotten_land", {
        "ultrawarm": False, "natural": False, "piglin_safe": False, "respawn_anchor_works": False, "bed_works": False,
        "has_raids": False, "has_skylight": True, "has_ceiling": False, "coordinate_scale": 12.0, "ambient_light": 0.1,
        "fixed_time": 9000, "logical_height": 384, "effects": "minecraft:overworld",
        "infiniburn": "#minecraft:infiniburn_overworld", "min_y": -64, "height": 384,
        "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0})
    g.data("worldgen/noise_settings/forgotten_land", wg.fl_noise_settings(wg.fl_surface_rule([])))
    g.data("dimension/forgotten_land", wg.fl_dimension([
        {"biome": "lacuna:forgotten_plains", "parameters": wg.climate([-1.0, 1.0], [-1.0, 1.0])}]))
    # Forgotten Plains: dense ruins (structures), divine glass clusters on the ground
    feats = [[] for _ in range(11)]
    feats[wg.VEGETAL_DECORATION] = ["lacuna:divine_glass_cluster_patch", "minecraft:patch_grass_plain"]
    g.data("worldgen/biome/forgotten_plains", wg.biome(0.6, 0.3, feats, particle="minecraft:ash", particle_p=0.004))
    g.shared.setdefault("fl_biomes", []).append("lacuna:forgotten_plains")
    g.name("biome.lacuna.forgotten_plains", "Forgotten Plains", "Pianure dimenticate")
    g.data("worldgen/configured_feature/divine_glass_cluster_patch", {"type": "minecraft:random_patch", "config": {
        "tries": 3, "xz_spread": 4, "y_spread": 2,
        "feature": {"feature": {"type": "minecraft:simple_block", "config": {"to_place": {
            "type": "minecraft:simple_state_provider",
            "state": {"Name": "lacuna:divine_glass_cluster", "Properties": {"facing": "up", "waterlogged": "false"}}}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                {"type": "minecraft:would_survive", "state": {"Name": "lacuna:divine_glass_cluster",
                                                              "Properties": {"facing": "up", "waterlogged": "false"}}}]}}]}}})
    g.data("worldgen/placed_feature/divine_glass_cluster_patch", {"feature": "lacuna:divine_glass_cluster_patch", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 18}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})

    def fl_biome_tag(g2):
        g2.tag("worldgen/biome", "lacuna:is_forgotten_land", *g2.shared["fl_biomes"])
        g2.tag("worldgen/biome", "lacuna:has_structure/forgotten_ruins", "#lacuna:is_forgotten_land")
    g.finalizer(fl_biome_tag)


def structures(g):
    sets = {}
    for name, (variants, set_name) in RUIN_STRUCTURES.items():
        locs = [mod(f"ruins/{name}_{i}") for i in range(1, variants + 1)]
        g.data(f"worldgen/template_pool/ruins/{name}", wg.pool(locs, processors="lacuna:murals"))
        start_y = -3 if name == "sunken_plaza" else 0
        g.data(f"worldgen/structure/{name}", wg.jigsaw_structure(mod(f"ruins/{name}"), "#lacuna:has_structure/forgotten_ruins",
                                                                   start_y=start_y))
        sets.setdefault(set_name, []).append(mod(name))
    ruins_set = wg.structure_set(sets["forgotten_ruins"], 14, 6, 1847261933)
    for entry in ruins_set["structures"]:
        if entry["structure"] == mod("memory_shrine"):
            entry["weight"] = 3  # the shrines carry the Memory Murals: make them the most common ruin
    g.data("worldgen/structure_set/forgotten_ruins", ruins_set)
    # hanging entities in templates need their TileX/Y/Z rewritten to world coordinates (structure/HangingEntityProcessor)
    g.data("worldgen/processor_list/murals", {"processors": [{"processor_type": "lacuna:hanging_entities"}]})
    g.data("worldgen/structure_set/ruin_clusters", wg.structure_set(sets["ruin_clusters"], 7, 3, 902114877))
    g.tag("worldgen/structure", "lacuna:forgotten_ruins", *[mod(n) for n in RUIN_STRUCTURES])

    # Ruin cache loot: other milestones append entries through g.shared["ruin_cache"]
    g.shared["ruin_cache"] = [
        wg.loot_entry("lacuna:divine_glass_shard", 8, 1, 2),
        wg.loot_entry("lacuna:ruby", 10, 1, 2),
        wg.loot_entry("lacuna:ruby", 8, 1, 2),
        wg.loot_entry("minecraft:gold_nugget", 15, 2, 6),
        wg.loot_entry("minecraft:amethyst_shard", 8, 1, 3),
        wg.loot_entry("minecraft:paper", 10, 1, 4),
        wg.loot_entry("minecraft:book", 5),
        wg.loot_entry("minecraft:bone", 10, 1, 3),
        wg.loot_entry("minecraft:echo_shard", 2),
        wg.loot_entry("minecraft:name_tag", 1),
    ]
    g.shared["ruin_cache_rare"] = [{"type": "minecraft:empty", "weight": 1000}]

    def write_cache(g2):
        g2.data("loot_table/chests/ruin_cache", wg.chest_table([
            wg.chest_pool(g2.shared["ruin_cache"], 2, 5),
            {"bonus_rolls": 0.0, "entries": g2.shared["ruin_cache_rare"], "rolls": 1.0}]))
    g.finalizer(write_cache)


def messages(g):
    g.name("lacuna.tooltip.durability", "Durability: %s / %s", "Durabilità: %s / %s")
    g.name("lacuna.tooltip.hold_details", "Hold %s for details", "Tieni premuto %s per i dettagli")
    g.name("key.lacuna.item_details", "Item details (hold over an item)", "Dettagli oggetto (tieni premuto sopra un oggetto)")
    g.name("key.categories.lacuna", "Lacuna", "Lacuna")
    g.name("lacuna.totem.silent", "The totem is silent in this dimension.", "Il totem tace in questa dimensione.")
    g.name("lacuna.totem.resists", "It resists the banishing.", "Resiste all'esilio.")
    g.name("lacuna.banish.arrived", "The world fades. You stand in the Forgotten Land.",
           "Il mondo svanisce. Sei nella Terra Dimenticata.")
    g.name("lacuna.banish.returned", "The Forgotten Land lets you go.", "La Terra Dimenticata ti lascia andare.")
    g.name("lacuna.banish.countdown", "Forgotten Land — returning in %s — Overworld ≈ %s, %s",
           "Terra Dimenticata — ritorno tra %s — Mondo normale ≈ %s, %s")
    g.name("lacuna.fl.cannot_leave", "The Forgotten Land does not let you leave.", "La Terra Dimenticata non ti lascia andare.")
    g.name("lacuna.fl.no_rest", "There is no rest in the Forgotten Land.", "Non c'è riposo nella Terra Dimenticata.")
    g.name("lacuna.relic.name", "Unreadable Relic", "Reliquia illeggibile")
    g.name("lacuna.relic.scattered", "Your belongings were scattered across the overworld near %s %s",
           "I tuoi averi sono stati dispersi nel mondo vicino a %s %s")


# ------------------------------------------------------------------------------------------- forgotten portal
# 8 frames, 2 per side with the corners empty, around a 2x2 opening; the Totem of Banishing lights it (see
# banish/ForgottenPortals). The frame is shaped like the End portal frame: 13/16 tall, sides show rows 3..15.

FRAME_TEX = {
    "particle": "lacuna:block/forgotten_portal_frame_side",
    "bottom": "lacuna:block/forgotten_portal_frame_bottom",
    "top": "lacuna:block/forgotten_portal_frame_top",
    "side": "lacuna:block/forgotten_portal_frame_side",
}
FRAME_FACING_Y = {"east": 270, "north": 180, "south": 0, "west": 90}


def frame_element(top, side, bottom=None):
    faces = {"up": {"uv": [0, 0, 16, 16], "texture": top}}
    if bottom:
        faces["down"] = {"uv": [0, 0, 16, 16], "texture": bottom, "cullface": "down"}
    for d in ("north", "south", "west", "east"):
        faces[d] = {"uv": [0, 3, 16, 16], "texture": side, "cullface": d}
    return {"from": [0, 0, 0], "to": [16, 13, 16], "faces": faces}


def portal(g):
    stone = frame_element("#top", "#side", "#bottom")
    variants = {}
    for active in ("false", "true"):
        model = "lacuna:block/forgotten_portal_frame" + ("_active" if active == "true" else "")
        for facing, y in FRAME_FACING_Y.items():
            variants[f"active={active},facing={facing}"] = {"model": model, "y": y} if y else {"model": model}
    g.single("forgotten_portal_frame", "Forgotten Portal Frame", "Cornice del portale dimenticato",
             {"parent": "minecraft:block/block", "textures": FRAME_TEX, "elements": [stone]},
             blockstate={"variants": variants})
    # active: the same stone with the runes and the hand sigil drawn again on top, full bright (lit runes)
    glow = frame_element("#top_glow", "#side_glow")
    glow["shade"] = False
    glow["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    g.asset("models/block/forgotten_portal_frame_active", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": dict(FRAME_TEX, top_glow="lacuna:block/forgotten_portal_frame_top_glow",
                         side_glow="lacuna:block/forgotten_portal_frame_side_glow"),
        "elements": [stone, glow]})
    g.props("forgotten_portal_frame", active=["true", "false"], facing=["north", "south", "west", "east"])
    g.tag("block", "minecraft:mineable/pickaxe", mod("forgotten_portal_frame"))
    g.shaped("forgotten_portal_frame", [" R ", "ROR", " R "], {"R": "lacuna:ruby", "O": "minecraft:crying_obsidian"},
             "lacuna:forgotten_portal_frame", category="building", unlock="lacuna:totem_of_banishing")

    # the portal: a flat, see-through, full-bright plate at 12/16 like the End portal surface (no item, no drops)
    g.single("forgotten_portal", "Forgotten Portal", "Portale dimenticato", {
        "parent": "minecraft:block/block", "ambientocclusion": False, "render_type": "minecraft:translucent",
        "textures": {"particle": "lacuna:block/forgotten_portal", "portal": "lacuna:block/forgotten_portal"},
        "elements": [{"from": [0, 12, 0], "to": [16, 12, 16], "shade": False,
                      "neoforge_data": {"block_light": 15, "sky_light": 15},
                      "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#portal"},
                                "down": {"uv": [0, 0, 16, 16], "texture": "#portal"}}}]},
             item=False, loot=None)
    for t in ("minecraft:dragon_immune", "minecraft:wither_immune"):
        g.tag("block", t, mod("forgotten_portal"))

    g.name("item.lacuna.forgotten_portal_frame.tip",
           "Eight frames, two per side with the corners empty, around a 2×2 opening: touch one with the Totem of Banishing.",
           "Otto cornici, due per lato con gli angoli vuoti, attorno a un'apertura 2×2: toccane una con il Totem dell'esilio.")
    g.name("lacuna.portal.incomplete", "The ring is incomplete: 8 frames, 2 per side (corners empty), around a 2×2 opening.",
           "L'anello è incompleto: 8 cornici, 2 per lato (angoli vuoti), attorno a un'apertura 2×2.")
    g.name("lacuna.portal.blocked", "The opening inside the frames must be clear.",
           "L'apertura dentro le cornici deve essere libera.")
    g.name("lacuna.portal.already_open", "This portal is already open.", "Questo portale è già aperto.")
