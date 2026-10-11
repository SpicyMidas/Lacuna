"""M10: Forgotten Land biomes (10a), Sunken Bell Tower + Obelisk (10b), Mirror Labyrinth (10c), Last Merchant (10d)."""
from gen_resources import loot_ore, loot_silk_only, mod
from content import worldgen as wg

FL = "lacuna:"
HORIZONTAL = {"north": 0, "east": 90, "south": 180, "west": 270}


def build(g):
    biome_blocks(g)
    biomes(g)
    bell_tower(g)
    obelisk(g)
    g.tag("worldgen/structure", "lacuna:orrery_targets", "lacuna:sunken_bell_tower", "lacuna:obelisk")
    labyrinth(g)


# ============================================================================================ 10a blocks
def biome_blocks(g):
    # Ashen snow: four footprint stages (3 = fresh, fades every 100 ticks)
    g.block_lang("ashen_snow_block", "Ashen Snow", "Neve cinerea")
    g.asset("models/block/ashen_snow_block", {"parent": "minecraft:block/cube_all",
                                              "textures": {"all": "lacuna:block/ashen_snow_block"}})
    variants = {"footprint=0": {"model": "lacuna:block/ashen_snow_block"}}
    for i in (1, 2, 3):
        g.asset(f"models/block/ashen_snow_block_footprint_{i}", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": f"lacuna:block/ashen_snow_block_footprint_{i}", "bottom": "lacuna:block/ashen_snow_block",
            "side": "lacuna:block/ashen_snow_block"}})
        variants[f"footprint={i}"] = {"model": f"lacuna:block/ashen_snow_block_footprint_{i}"}
    g.asset("blockstates/ashen_snow_block", {"variants": variants})
    g.block_item_parent("ashen_snow_block")
    g.loot("ashen_snow_block", "self")
    g.props("ashen_snow_block", footprint=["0", "1", "2", "3"])
    g.tag("block", "minecraft:mineable/shovel", mod("ashen_snow_block"))

    g.cube("clear_ice", "Clear Ice", "Ghiaccio limpido", render="minecraft:translucent", loot=loot_silk_only("clear_ice"))
    g.tag("block", "minecraft:mineable/pickaxe", mod("clear_ice"))
    g.tag("block", "minecraft:ice", mod("clear_ice"))

    g.block_lang("updraft_spring", "Updraft Spring", "Sorgente ascendente")
    g.asset("models/block/updraft_spring", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                            "textures": {"all": "lacuna:block/updraft_spring"}})
    g.asset("blockstates/updraft_spring", {"variants": {"": {"model": "lacuna:block/updraft_spring"}}})
    g.block_item_parent("updraft_spring")
    g.loot("updraft_spring", loot_silk_only("updraft_spring"))
    g.props("updraft_spring", range=[str(i) for i in range(1, 41)])
    g.tag("block", "minecraft:mineable/pickaxe", mod("updraft_spring"))

    g.cross("spore_mushroom", "Spore Mushroom", "Fungo sporale")

    watch_faces = {d: {"uv": [3, 10, 13, 13], "texture": "#side"} for d in ("north", "south", "east", "west")}
    watch_faces["up"] = {"uv": [3, 3, 13, 13], "texture": "#face"}
    watch_faces["down"] = {"uv": [3, 3, 13, 13], "texture": "#side"}
    g.single("buried_watch", "Buried Watch", "Orologio sepolto", {
        "parent": "minecraft:block/block", "textures": {"particle": "lacuna:block/buried_watch_side",
                                                        "face": "lacuna:block/buried_watch_top",
                                                        "side": "lacuna:block/buried_watch_side"},
        "elements": [{"from": [3, -1, 3], "to": [13, 2, 13], "rotation": {"angle": 22.5, "axis": "x", "origin": [8, 0, 8]},
                      "faces": watch_faces},
                     {"from": [7, 2, 2], "to": [9, 4, 3], "rotation": {"angle": 22.5, "axis": "x", "origin": [8, 0, 8]},
                      "faces": {d: {"uv": [0, 0, 2, 2], "texture": "#side"} for d in ("north", "south", "east", "west", "up", "down")}}]},
        loot=loot_ore("buried_watch", "lacuna:chronal_shard", 1.0, 2.0, fortune="none"),
        blockstate={"variants": {f"facing={f}": ({"model": "lacuna:block/buried_watch", "y": y} if y else
                                                 {"model": "lacuna:block/buried_watch"}) for f, y in HORIZONTAL.items()}})
    g.props("buried_watch", facing=list(HORIZONTAL))
    g.tag("block", "minecraft:mineable/pickaxe", mod("buried_watch"))

    gear_faces = {"up": {"uv": [0, 0, 16, 16], "texture": "#gear"}, "down": {"uv": [0, 0, 16, 16], "texture": "#gear"}}
    for d in ("north", "south", "east", "west"):
        gear_faces[d] = {"uv": [0, 7, 16, 8], "texture": "#gear"}
    g.single("rusted_gear", "Rusted Gear", "Ingranaggio arrugginito", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"particle": "lacuna:block/rusted_gear", "gear": "lacuna:block/rusted_gear"},
        "elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": gear_faces}]},
        blockstate={"variants": {f"facing={f}": ({"model": "lacuna:block/rusted_gear", "y": y} if y else
                                                 {"model": "lacuna:block/rusted_gear"}) for f, y in HORIZONTAL.items()}})
    g.props("rusted_gear", facing=list(HORIZONTAL))
    g.tag("block", "minecraft:mineable/pickaxe", mod("rusted_gear"))


# ============================================================================================ 10a biomes
def placed(g, name, feature, placement):
    g.data(f"worldgen/placed_feature/{name}", {"feature": feature, "placement": placement})


def tree_placement(sapling, chance=None, count=None, water=0):
    p = []
    if count:
        p.append({"type": "minecraft:count", "count": count})
    if chance:
        p.append({"type": "minecraft:rarity_filter", "chance": chance})
    p += [{"type": "minecraft:in_square"}, {"type": "minecraft:surface_water_depth_filter", "max_water_depth": water},
          {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, {"type": "minecraft:biome"},
          {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": {
              "Name": sapling, "Properties": {"stage": "0"}}}}]
    return p


def surface_patch(placement_chance):
    return [{"type": "minecraft:rarity_filter", "chance": placement_chance}, {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]


def block_patch(block_state, tries, spread):
    return {"type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": spread, "y_spread": 2, "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {"to_place": block_state}},
        "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            {"type": "minecraft:would_survive", "state": first_state(block_state)}]}}]}}}


def first_state(provider):
    if provider["type"] == "minecraft:simple_state_provider":
        return provider["state"]
    return provider["entries"][0]["data"]


def facing_provider(name):
    return {"type": "minecraft:weighted_state_provider", "entries": [
        {"weight": 1, "data": {"Name": name, "Properties": {"facing": f}}} for f in HORIZONTAL]}


def biome_features(g):
    # trees reused from M8 with Forgotten Land densities
    placed(g, "fl_echo_birch_grove", "lacuna:echo_birch_tree", tree_placement("lacuna:echo_birch_sapling", chance=7, count=3))
    placed(g, "fl_echo_birch_sparse", "lacuna:echo_birch_tree", tree_placement("lacuna:echo_birch_sapling", chance=48))
    placed(g, "fl_cindervine_spire", "lacuna:cindervine_tree", tree_placement("lacuna:cindervine_sapling", chance=30))
    placed(g, "fl_crystal_willow", "lacuna:crystal_willow_tree", tree_placement("lacuna:crystal_willow_sapling", chance=14))
    placed(g, "fl_tidebloom_grove", "lacuna:tidebloom_tree", tree_placement("lacuna:tidebloom_sapling", count=2, water=2))
    # code features
    g.data("worldgen/configured_feature/floating_island", {"type": "lacuna:floating_island", "config": {}})
    placed(g, "floating_island", "lacuna:floating_island", [
        {"type": "minecraft:rarity_filter", "chance": 4}, {"type": "minecraft:in_square"}, {"type": "minecraft:biome"}])
    g.data("worldgen/configured_feature/clear_ice_cap", {"type": "lacuna:clear_ice_cap", "config": {}})
    placed(g, "clear_ice_cap", "lacuna:clear_ice_cap", [])
    # marsh pools: shallow water basins with a mud floor and seagrass
    g.tag("block", "lacuna:marsh_pool_replaceable", "#minecraft:dirt", "minecraft:sand", "minecraft:gravel")
    g.data("worldgen/configured_feature/marsh_pool", {"type": "minecraft:waterlogged_vegetation_patch", "config": {
        "depth": 1, "extra_bottom_block_chance": 0.3, "extra_edge_column_chance": 0.4,
        "ground_state": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:mud"}},
        "replaceable": "#lacuna:marsh_pool_replaceable", "surface": "floor", "vegetation_chance": 0.25,
        "vegetation_feature": {"feature": "minecraft:seagrass_simple", "placement": []}, "vertical_range": 5,
        "xz_radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 5}}})
    placed(g, "marsh_pool", "lacuna:marsh_pool", [
        {"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}])
    # block patches
    g.data("worldgen/configured_feature/spore_mushroom_patch", block_patch(
        {"type": "minecraft:simple_state_provider", "state": {"Name": "lacuna:spore_mushroom"}}, 10, 5))
    placed(g, "spore_mushroom_patch", "lacuna:spore_mushroom_patch", surface_patch(3))
    g.data("worldgen/configured_feature/buried_watch_patch", block_patch(facing_provider("lacuna:buried_watch"), 4, 6))
    placed(g, "buried_watch_patch", "lacuna:buried_watch_patch", surface_patch(7))
    g.data("worldgen/configured_feature/rusted_gear_patch", block_patch(facing_provider("lacuna:rusted_gear"), 5, 6))
    placed(g, "rusted_gear_patch", "lacuna:rusted_gear_patch", surface_patch(5))


def spawner(entity, weight, lo, hi):
    return {"type": entity, "weight": weight, "minCount": lo, "maxCount": hi}


def biomes(g):
    biome_features(g)
    V, T = wg.VEGETAL_DECORATION, wg.TOP_LAYER_MODIFICATION
    gloom = spawner("lacuna:gloomcrawler", 4, 1, 2)

    def feats(**steps):
        f = [[] for _ in range(11)]
        for step, lst in steps.items():
            f[int(step[1:])] = lst
        return f

    # Forgotten Plains gains Echo Birch groves and Gloomcrawlers (M9)
    g.override("data/lacuna/worldgen/biome/forgotten_plains.json", wg.biome(0.6, 0.3, feats(
        s9=["lacuna:divine_glass_cluster_patch", "minecraft:patch_grass_plain", "lacuna:fl_echo_birch_grove"]),
        spawners={"monster": [gloom]}, particle="minecraft:ash", particle_p=0.004))

    defs = {
        "cinderfrost_flats": ("Cinderfrost Flats", "Distese di cenere gelata", wg.biome(
            -0.7, 0.2, feats(s9=["lacuna:fl_cindervine_spire"], s10=["lacuna:clear_ice_cap"]),
            grass="#8F9692", foliage="#7E8581", particle="minecraft:ash", particle_p=0.02,
            fog=wg.rgb("#9AA3AB"), sky=wg.rgb("#8A939C"), water=wg.rgb("#6F8796"), water_fog=wg.rgb("#55697A"),
            additions="minecraft:ambient.basalt_deltas.additions")),
        "inverted_falls": ("Inverted Falls", "Cascate rovesciate", wg.biome(
            0.7, 0.8, feats(s2=["lacuna:floating_island"],
                            s9=["lacuna:divine_glass_cluster_patch", "lacuna:fl_crystal_willow", "minecraft:patch_grass_plain"]),
            grass="#7FA394", foliage="#76A08F", water=wg.rgb("#5E8FB0"), water_fog=wg.rgb("#4A7393"),
            additions="minecraft:ambient.underwater.additions")),
        "whispering_marsh": ("Whispering Marsh", "Palude sussurrante", wg.biome(
            0.4, 0.9, feats(s1=["lacuna:marsh_pool"],
                            s9=["lacuna:fl_tidebloom_grove", "lacuna:spore_mushroom_patch", "minecraft:patch_waterlily",
                                "minecraft:patch_grass_normal"]),
            spawners={"ambient": [spawner("lacuna:lantern_moth", 8, 1, 3)], "monster": [spawner("lacuna:gloomcrawler", 6, 1, 2)]},
            grass="#6B7F6A", foliage="#62785F", particle="minecraft:white_ash", particle_p=0.01,
            water=wg.rgb("#4E6B5A"), water_fog=wg.rgb("#3B5446"), additions="minecraft:ambient.warped_forest.additions")),
        "clockwork_dunes": ("Clockwork Dunes", "Dune a orologeria", wg.biome(
            1.4, 0.0, feats(s9=["lacuna:buried_watch_patch", "lacuna:rusted_gear_patch", "minecraft:patch_dead_bush_2",
                                "lacuna:fl_echo_birch_sparse"]),
            spawners={"creature": [spawner("lacuna:clockwork_hound", 3, 1, 2)]},
            grass="#A99F72", foliage="#A0976A", fog=wg.rgb("#A9A38F"), sky=wg.rgb("#9C9A8C"),
            additions="minecraft:block.note_block.hat")),
    }
    for bid, (en, it, data) in defs.items():
        if bid == "clockwork_dunes":
            data["effects"]["additions_sound"]["tick_chance"] = 0.0015
        g.data(f"worldgen/biome/{bid}", data)
        g.name(f"biome.lacuna.{bid}", en, it)
        g.shared["fl_biomes"].append(f"lacuna:{bid}")

    # surface rules per biome (before the plains default in fl_surface_rule)
    rules = [
        wg.cond(wg.in_biomes("lacuna:cinderfrost_flats"), wg.default_surface(
            wg.block("lacuna:ashen_snow_block", {"footprint": "0"}), wg.block("minecraft:tuff"), wg.block("minecraft:gravel"))),
        wg.cond(wg.in_biomes("lacuna:clockwork_dunes"), wg.default_surface(
            wg.block("minecraft:sand"), wg.block("minecraft:sandstone"), wg.block("minecraft:sand"))),
        wg.cond(wg.in_biomes("lacuna:whispering_marsh"), wg.default_surface(
            wg.seq(wg.cond({"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.15,
                            "max_threshold": 10.0}, wg.block("minecraft:mud")),
                   wg.block("minecraft:grass_block", {"snowy": "false"})),
            wg.block("minecraft:dirt"), wg.block("minecraft:mud"))),
    ]
    g.override("data/lacuna/worldgen/noise_settings/forgotten_land.json", wg.fl_noise_settings(wg.fl_surface_rule(rules)))
    full = [-1.0, 1.0]
    g.override("data/lacuna/dimension/forgotten_land.json", wg.fl_dimension([
        {"biome": "lacuna:forgotten_plains", "parameters": wg.climate([-0.45, 0.45], [-1.0, 0.45])},
        {"biome": "lacuna:cinderfrost_flats", "parameters": wg.climate([-1.0, -0.45], full)},
        {"biome": "lacuna:whispering_marsh", "parameters": wg.climate([-0.45, 0.45], [0.45, 1.0])},
        {"biome": "lacuna:clockwork_dunes", "parameters": wg.climate([0.45, 1.0], [-1.0, 0.0])},
        {"biome": "lacuna:inverted_falls", "parameters": wg.climate([0.45, 1.0], [0.0, 1.0])},
    ]))


# ============================================================================================ 10b structures
def single_structure(g, name, biomes_tag, heightmap, salt, spacing, separation, adaptation="beard_thin"):
    g.data(f"worldgen/template_pool/{name}", wg.pool([mod(name)]))
    data = wg.jigsaw_structure(mod(name), biomes_tag, adaptation=adaptation, heightmap=heightmap)
    g.data(f"worldgen/structure/{name}", data)
    g.data(f"worldgen/structure_set/{name}", wg.structure_set([mod(name)], spacing, separation, salt))


def bell_tower(g):
    bell_faces = lambda uv: {d: {"uv": uv, "texture": "#bell"} for d in ("north", "south", "east", "west", "up", "down")}
    g.single("sunken_bell", "Sunken Bell", "Campana sommersa", {
        "parent": "minecraft:block/block", "textures": {"particle": "lacuna:block/sunken_bell", "bell": "lacuna:block/sunken_bell"},
        "elements": [{"from": [7, 13, 7], "to": [9, 16, 9], "faces": bell_faces([7, 0, 9, 3])},
                     {"from": [5, 4, 5], "to": [11, 13, 11], "faces": bell_faces([5, 3, 11, 12])},
                     {"from": [4, 2, 4], "to": [12, 4, 12], "faces": bell_faces([4, 12, 12, 14])}]})
    g.tag("block", "minecraft:mineable/pickaxe", mod("sunken_bell"))
    g.tag("worldgen/biome", "lacuna:has_structure/sunken_bell_tower", "#minecraft:is_ocean", "#minecraft:is_deep_ocean")
    single_structure(g, "sunken_bell_tower", "#lacuna:has_structure/sunken_bell_tower", "OCEAN_FLOOR_WG", 1530771209, 36, 12)
    g.data("loot_table/chests/sunken_bell_tower", wg.chest_table([
        wg.chest_pool([wg.loot_entry("minecraft:prismarine_shard", 20, 2, 6), wg.loot_entry("minecraft:prismarine_crystals", 15, 1, 4),
                       wg.loot_entry("lacuna:ruby", 10, 1, 3), wg.loot_entry("lacuna:divine_glass_shard", 10, 1, 3),
                       wg.loot_entry("minecraft:gold_ingot", 10, 1, 3), wg.loot_entry("minecraft:iron_ingot", 12, 1, 4),
                       wg.loot_entry("minecraft:nautilus_shell", 5), wg.loot_entry("lacuna:chronal_shard", 3),
                       wg.loot_entry("minecraft:copper_ingot", 12, 2, 6)], 3, 6),
        {"bonus_rolls": 0.0, "rolls": 1.0, "entries": [{"type": "minecraft:empty", "weight": 49},
                                                        {"type": "minecraft:item", "name": "minecraft:heart_of_the_sea", "weight": 1}]}]))
    g.name("lacuna.bell.reveal", "The bell's toll echoes through the stone: %s ores answer.",
           "Il rintocco della campana attraversa la pietra: rispondono %s minerali.")
    g.name("lacuna.bell.cooldown", "The bell is still humming (%ss).", "La campana vibra ancora (%ss).")


OBELISK_LINES = [
    ("Here the names were carved so deep that the stone forgot them.", "Qui i nomi furono incisi così a fondo che la pietra li dimenticò."),
    ("We built the city of glass, and the city remembered us instead.", "Costruimmo la città di vetro, e fu la città a ricordare noi."),
    ("Count the towers. Then count the people. One number is missing.", "Conta le torri. Poi conta le persone. Manca un numero."),
    ("The sun was ours for a thousand summers.", "Il sole fu nostro per mille estati."),
    ("Whoever reads this has already forgotten who wrote it.", "Chi legge ha già dimenticato chi l'ha scritto."),
    ("The bells rang under the sea so the drowned would not feel alone.", "Le campane suonavano sotto il mare perché gli annegati non si sentissero soli."),
    ("Twelve steps there are one step here.", "Dodici passi là sono un passo qui."),
    ("A name is a door. We closed all of ours.", "Un nome è una porta. Abbiamo chiuso tutte le nostre."),
    ("The watches still tick in the sand, counting for no one.", "Gli orologi ticchettano ancora nella sabbia, contando per nessuno."),
    ("Do not look for us in the mirrors. Only you live there now.", "Non cercarci negli specchi. Ora ci vivi solo tu."),
    ("We traded our past for a quiet land. The land kept both.", "Barattammo il passato per una terra quieta. La terra tenne entrambi."),
    ("The rubies were tears of the mountain. We made crowns of them.", "I rubini erano lacrime della montagna. Ne facemmo corone."),
    ("Ash falls where snow once did; it remembers footsteps a little longer.", "Cade cenere dove un tempo cadeva neve; ricorda i passi un po' più a lungo."),
    ("The waterfalls turned upward the day the river lost its name.", "Le cascate si rovesciarono il giorno in cui il fiume perse il nome."),
    ("Every festival ended with a song no one could finish.", "Ogni festa finiva con un canto che nessuno sapeva concludere."),
    ("If you hear whispering in the marsh, answer politely.", "Se senti sussurri nella palude, rispondi con garbo."),
    ("The merchant who still walks remembers every price but his own.", "Il mercante che ancora cammina ricorda ogni prezzo tranne il proprio."),
    ("Here stood the library. The books are still here; the words left.", "Qui sorgeva la biblioteca. I libri ci sono ancora; le parole se ne sono andate."),
    ("Eclipse was not the end. It was a door left ajar.", "L'eclisse non fu la fine. Fu una porta socchiusa."),
    ("Mark the place where you stand: one day it will forget you too.", "Segna il luogo in cui sei: un giorno dimenticherà anche te."),
    ("Seven brothers, one obelisk, no names.", "Sette fratelli, un obelisco, nessun nome."),
    ("The glass grows where the light was kindest.", "Il vetro cresce dove la luce fu più gentile."),
    ("The hounds of brass still follow a master who never returns.", "I segugi d'ottone seguono ancora un padrone che non torna mai."),
    ("Read slowly. The letters move when you are not looking.", "Leggi piano. Le lettere si muovono quando non guardi."),
]


def obelisk(g):
    g.cube("obelisk_inscription", "Obelisk Inscription", "Iscrizione dell'obelisco", loot=None)
    g.tag("block", "lacuna:ruin_blocks", mod("obelisk_inscription"))
    g.tag("worldgen/biome", "lacuna:has_structure/obelisk", "#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_savanna",
          "#minecraft:is_jungle", "#minecraft:is_badlands", "#minecraft:is_hill", "#c:is_plains", "#c:is_desert",
          "#c:is_snowy_plains")
    single_structure(g, "obelisk", "#lacuna:has_structure/obelisk", "WORLD_SURFACE_WG", 772491035, 44, 16)
    assert len(OBELISK_LINES) == 24
    for i, (en, it) in enumerate(OBELISK_LINES):
        g.name(f"lacuna.obelisk.line.{i}", en, it)
    g.name("lacuna.obelisk.reward", "Something falls from a crack in the stone.", "Qualcosa cade da una crepa nella pietra.")


# ============================================================================================ 10c mirror labyrinth
def plate(texture):
    faces = {"up": {"uv": [1, 1, 15, 15], "texture": "#plate"}, "down": {"uv": [1, 1, 15, 15], "texture": "#plate", "cullface": "down"}}
    for d in ("north", "south", "east", "west"):
        faces[d] = {"uv": [1, 15, 15, 16], "texture": "#plate"}
    return {"parent": "minecraft:block/block", "textures": {"particle": texture, "plate": texture},
            "elements": [{"from": [1, 0, 1], "to": [15, 1, 15], "faces": faces}]}


def labyrinth(g):
    g.cube("mirror_glass", "Mirror Glass", "Vetro a specchio", render="minecraft:translucent", loot=None)
    g.cube("labyrinth_exit", "Labyrinth Seal", "Sigillo del labirinto", render="minecraft:translucent", loot=None)
    g.single("labyrinth_trigger", "Labyrinth Ring", "Anello del labirinto", plate("lacuna:block/labyrinth_trigger"), loot=None,
             blockstate={"variants": {"solved=false": {"model": "lacuna:block/labyrinth_trigger"},
                                      "solved=true": {"model": "lacuna:block/labyrinth_trigger_solved"}}})
    g.asset("models/block/labyrinth_trigger_solved", plate("lacuna:block/labyrinth_trigger_solved"))
    g.props("labyrinth_trigger", solved=["true", "false"])
    for b in ("mirror_glass", "labyrinth_exit", "labyrinth_trigger"):
        g.tag("block", "lacuna:ruin_blocks", mod(b))
    g.name("entity.lacuna.reflection", "Reflection", "Riflesso")
    g.tag("entity_type", "lacuna:cannot_be_banished", "lacuna:reflection")
    g.tag("entity_type", "lacuna:unreflectable", "lacuna:reflection")
    g.name("lacuna.labyrinth.reflection", "Something in the mirrors starts to walk your path.",
           "Qualcosa negli specchi comincia a percorrere i tuoi passi.")
    locs = [mod(f"mirror_labyrinth/maze_{i}") for i in range(1, 6)]
    g.data("worldgen/template_pool/mirror_labyrinth", wg.pool(locs))
    g.data("worldgen/structure/mirror_labyrinth", wg.jigsaw_structure(mod("mirror_labyrinth"), "#lacuna:is_forgotten_land"))
    g.data("worldgen/structure_set/mirror_labyrinth", wg.structure_set([mod("mirror_labyrinth")], 30, 10, 1179408013))
    g.data("loot_table/chests/mirror_hoard", wg.chest_table([
        wg.chest_pool([wg.loot_entry("lacuna:divine_glass_shard", 20, 2, 6), wg.loot_entry("lacuna:ruby", 15, 2, 5),
                       wg.loot_entry("lacuna:chronal_shard", 8, 1, 2), wg.loot_entry("lacuna:echo_fragment", 6, 1, 2),
                       wg.loot_entry("minecraft:amethyst_shard", 10, 2, 6)], 4, 7),
        {"bonus_rolls": 0.0, "rolls": 1.0, "entries": [
            {"type": "minecraft:empty", "weight": 9},
            {"type": "minecraft:item", "name": "lacuna:solar_flare_fragment", "weight": 1}]}]))
