"""M11: Townships. 11a structures + civic blocks; 11b-d gameplay data are appended by later parts."""
from gen_resources import loot_self, mod
from content import worldgen as wg

VARIANTS = {
    "plains": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"],
    "desert": ["minecraft:desert"],
    "savanna": ["minecraft:savanna", "minecraft:savanna_plateau"],
    "taiga": ["minecraft:taiga"],
    "snowy": ["minecraft:snowy_plains", "minecraft:snowy_taiga"],
}
HORIZONTAL = {"north": 0, "east": 90, "south": 180, "west": 270}


def build(g):
    civic_blocks(g)
    structures(g)
    reputation(g)


def horizontal_states(model):
    return {"variants": {f"facing={f}": ({"model": model, "y": y} if y else {"model": model}) for f, y in HORIZONTAL.items()}}


def box(fr, to, tex, uv=None):
    faces = {d: {"texture": tex} for d in ("north", "south", "east", "west", "up", "down")}
    if uv:
        for d in faces:
            faces[d]["uv"] = uv
    return {"from": fr, "to": to, "faces": faces}


def civic(g, bid, en, it, textures, elements):
    g.single(bid, en, it, {"parent": "minecraft:block/block", "textures": textures, "elements": elements},
             blockstate=horizontal_states(f"lacuna:block/{bid}"))
    g.props(bid, facing=list(HORIZONTAL))


def civic_blocks(g):
    civic(g, "township_ledger", "Township Ledger", "Registro del borgo",
          {"particle": "lacuna:block/township_ledger_side", "side": "lacuna:block/township_ledger_side",
           "top": "lacuna:block/township_ledger_top"},
          [box([2, 0, 2], [14, 10, 14], "#side"),
           {"from": [0, 10, 0], "to": [16, 13, 16], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 10, 8]},
            "faces": {"up": {"texture": "#top"}, "down": {"texture": "#side"}, "north": {"texture": "#side", "uv": [0, 0, 16, 3]},
                      "south": {"texture": "#side", "uv": [0, 0, 16, 3]}, "east": {"texture": "#side", "uv": [0, 0, 16, 3]},
                      "west": {"texture": "#side", "uv": [0, 0, 16, 3]}}}])
    civic(g, "council_table", "Council Table", "Tavolo del consiglio",
          {"particle": "lacuna:block/council_table", "top": "lacuna:block/council_table", "leg": "lacuna:block/township_ledger_side"},
          [box([0, 12, 0], [16, 16, 16], "#top"), box([6, 0, 6], [10, 12, 10], "#leg")])
    civic(g, "judge_bench", "Judge's Bench", "Banco del giudice",
          {"particle": "lacuna:block/judge_bench", "all": "lacuna:block/judge_bench"},
          [box([0, 0, 0], [16, 14, 16], "#all"), box([0, 14, 0], [16, 16, 3], "#all")])
    civic(g, "guild_board", "Guild Board", "Bacheca della gilda",
          {"particle": "lacuna:block/guild_board", "front": "lacuna:block/guild_board", "frame": "lacuna:block/township_ledger_side"},
          [{"from": [0, 0, 6], "to": [16, 16, 10], "faces": {"north": {"texture": "#front"}, "south": {"texture": "#front"},
                                                               "east": {"texture": "#frame"}, "west": {"texture": "#frame"},
                                                               "up": {"texture": "#frame"}, "down": {"texture": "#frame"}}}])
    civic(g, "school_desk", "School Desk", "Banco di scuola",
          {"particle": "lacuna:block/school_desk", "top": "lacuna:block/school_desk", "leg": "lacuna:block/township_ledger_side"},
          [box([0, 10, 0], [16, 13, 16], "#top"), box([1, 0, 1], [15, 10, 3], "#leg")])
    civic(g, "mint_press", "Mint Press", "Pressa della zecca",
          {"particle": "lacuna:block/mint_press", "all": "lacuna:block/mint_press"},
          [box([1, 0, 1], [15, 6, 15], "#all"), box([6, 6, 6], [10, 16, 10], "#all"), box([2, 13, 7], [14, 15, 9], "#all")])
    # gate cell: bars across the opening; open = raised into the top 2 pixels
    gate_tex = {"particle": "lacuna:block/township_gate", "bars": "lacuna:block/township_gate"}
    g.asset("models/block/township_gate", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                           "textures": gate_tex, "elements": [box([0, 0, 6], [16, 16, 10], "#bars")]})
    g.asset("models/block/township_gate_open", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                "textures": gate_tex, "elements": [box([0, 14, 6], [16, 16, 10], "#bars", [0, 0, 16, 2])]})
    variants = {}
    for axis, y in (("x", 0), ("z", 90)):
        for open_, suffix in (("false", ""), ("true", "_open")):
            v = {"model": f"lacuna:block/township_gate{suffix}"}
            if y:
                v["y"] = y
            variants[f"axis={axis},open={open_}"] = v
    g.single("township_gate", "Township Gate", "Cancello del borgo", None, item={"parent": "lacuna:block/township_gate"},
             blockstate={"variants": variants})
    g.props("township_gate", axis=["x", "z"], open=["true", "false"])
    for b in ("township_gate", "mint_press"):
        g.tag("block", "minecraft:mineable/pickaxe", mod(b))
    for b in ("township_ledger", "council_table", "judge_bench", "guild_board", "school_desk"):
        g.tag("block", "minecraft:mineable/axe", mod(b))


def structures(g):
    structures_list = []
    for v, biomes in VARIANTS.items():
        base = f"township/{v}"
        g.data(f"worldgen/template_pool/{base}/core", wg.pool([mod(f"{base}/core")]))
        for kind, piece in (("market", "market"), ("residential", "house"), ("workshop", "workshop"), ("civic", "civic")):
            g.data(f"worldgen/template_pool/{base}/lot_{kind}", wg.pool([mod(f"{base}/{piece}_{i}") for i in range(1, 7)]))
        g.data(f"worldgen/template_pool/{base}/road", wg.pool(
            [mod(f"{base}/road_1"), mod(f"{base}/road_2"), mod(f"{base}/road_end")], projection="terrain_matching", weights=[3, 2, 2]))
        g.data(f"worldgen/template_pool/{base}/farm", wg.pool([mod(f"{base}/farm_{i}") for i in (1, 2, 3)]))
        g.tag("worldgen/biome", f"lacuna:has_structure/township_{v}", *biomes)
        data = wg.jigsaw_structure(mod(f"{base}/core"), f"#lacuna:has_structure/township_{v}", size=12, max_distance=128)
        g.data(f"worldgen/structure/township_{v}", data)
        structures_list.append(mod(f"township_{v}"))
    g.data("worldgen/structure_set/townships", {
        "placement": {"type": "minecraft:random_spread", "salt": 1934871203, "spacing": 34, "separation": 12,
                      "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 4}},
        "structures": [{"structure": s, "weight": 1} for s in structures_list]})
    g.tag("worldgen/structure", "lacuna:townships", *structures_list)


# ============================================================================================ 11b reputation, laws
LAWS = [("No drawn weapons inside the walls", "Niente armi sguainate dentro le mura"),
        ("Gates close at dusk", "Le porte si chiudono al tramonto"),
        ("No breaking blocks inside the walls", "Vietato rompere blocchi dentro le mura"),
        ("The bell rings only at dawn", "La campana suona solo all'alba")]
TIERS = {"hostile": ("Hostile", "Ostile"), "wary": ("Wary", "Diffidente"), "neutral": ("Neutral", "Neutrale"),
         "friendly": ("Friendly", "Amichevole"), "honored": ("Honored", "Onorato")}
POLICIES = {"none": ("no policy yet", "nessuna politica"), "free_market": ("Free Market", "Libero mercato"),
            "austerity": ("Austerity", "Austerità"), "festival": ("Festival", "Festa"), "martial_law": ("Martial Law", "Legge marziale")}


def reputation(g):
    for i, (en, it) in enumerate(LAWS):
        g.name(f"lacuna.township.law.{i}", en, it)
    for k, (en, it) in TIERS.items():
        g.name(f"lacuna.township.tier.{k}", en, it)
    for k, (en, it) in POLICIES.items():
        g.name(f"lacuna.township.policy.{k}", en, it)
    g.name("lacuna.township.header", "— Township %s —", "— Borgo %s —")
    g.name("lacuna.township.reputation", "Your reputation: %s (%s)", "La tua reputazione: %s (%s)")
    g.name("lacuna.township.laws", "Laws: %s; %s", "Leggi: %s; %s")
    g.name("lacuna.township.policy", "Policy: %s (council in %s days)", "Politica: %s (consiglio tra %s giorni)")
    g.name("lacuna.township.coin", "Coin value: %s, price index: %s", "Valore della moneta: %s, indice dei prezzi: %s")
    g.name("lacuna.township.rep_change", "Township reputation %s → %s (%s)", "Reputazione nel borgo %s → %s (%s)")
    g.name("lacuna.township.warn.weapon", "Sheathe your weapon inside the walls!", "Riponi l'arma dentro le mura!")
    g.name("lacuna.township.warn.curfew", "Strangers must leave the walls by dusk!", "Gli stranieri devono uscire dalle mura al tramonto!")
    g.tag("block", "lacuna:township_protected", *[mod(b) for b in ("township_gate", "township_ledger", "council_table", "judge_bench",
                                                                   "guild_board", "school_desk", "mint_press")],
          "#minecraft:doors", "minecraft:bell", "minecraft:dirt_path", "minecraft:cobblestone", "minecraft:stone_bricks",
          "minecraft:mossy_stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_wall", "minecraft:sandstone",
          "minecraft:cut_sandstone", "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone", "minecraft:sandstone_wall",
          "minecraft:mud_bricks", "minecraft:packed_mud", "minecraft:mud_brick_wall", "minecraft:cobblestone_wall",
          "minecraft:mossy_cobblestone", "minecraft:polished_diorite", "minecraft:diorite", "minecraft:diorite_wall",
          "minecraft:quartz_block")
