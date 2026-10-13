"""M11 (revision 2): the civic system runs in vanilla villages. Civic blocks, the village coin and ledger, the mint's
loot, the protected-block tag and every civic message. The civic buildings themselves are structure templates
(tools/structures/m11_villages.py) appended to the vanilla village house pools at server start (VillagePools.java)."""
from gen_resources import loot_self, mod
from content import worldgen as wg

HORIZONTAL = {"north": 0, "east": 90, "south": 180, "west": 270}
VILLAGE_TYPES = {"plains": ("Plains Village", "Villaggio delle pianure"), "desert": ("Desert Village", "Villaggio del deserto"),
                 "savanna": ("Savanna Village", "Villaggio della savana"), "taiga": ("Taiga Village", "Villaggio della taiga"),
                 "snowy": ("Snowy Village", "Villaggio innevato"), "player": ("Founded Village", "Villaggio fondato")}


STYLES = ("plains", "desert", "savanna", "taiga", "snowy")
CIVIC_BUILDINGS = ("village_hall", "mint", "guild_hall", "schoolhouse")
MOSSY = ("plains", "taiga")


def build(g):
    civic_blocks(g)
    civic_pools(g)
    reputation(g)
    council_currency(g)
    guilds(g)


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
    civic(g, "village_ledger", "Village Ledger", "Registro del villaggio",
          {"particle": "lacuna:block/village_ledger_side", "side": "lacuna:block/village_ledger_side",
           "top": "lacuna:block/village_ledger_top"},
          [box([2, 0, 2], [14, 10, 14], "#side"),
           {"from": [0, 10, 0], "to": [16, 13, 16], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 10, 8]},
            "faces": {"up": {"texture": "#top"}, "down": {"texture": "#side"}, "north": {"texture": "#side", "uv": [0, 0, 16, 3]},
                      "south": {"texture": "#side", "uv": [0, 0, 16, 3]}, "east": {"texture": "#side", "uv": [0, 0, 16, 3]},
                      "west": {"texture": "#side", "uv": [0, 0, 16, 3]}}}])
    civic(g, "council_table", "Council Table", "Tavolo del consiglio",
          {"particle": "lacuna:block/council_table", "top": "lacuna:block/council_table", "leg": "lacuna:block/village_ledger_side"},
          [box([0, 12, 0], [16, 16, 16], "#top"), box([6, 0, 6], [10, 12, 10], "#leg")])
    civic(g, "judge_bench", "Judge's Bench", "Banco del giudice",
          {"particle": "lacuna:block/judge_bench", "all": "lacuna:block/judge_bench"},
          [box([0, 0, 0], [16, 14, 16], "#all"), box([0, 14, 0], [16, 16, 3], "#all")])
    guild_board(g)
    civic(g, "school_desk", "School Desk", "Banco di scuola",
          {"particle": "lacuna:block/school_desk", "top": "lacuna:block/school_desk", "leg": "lacuna:block/village_ledger_side"},
          [box([0, 10, 0], [16, 13, 16], "#top"), box([1, 0, 1], [15, 10, 3], "#leg")])
    civic(g, "mint_press", "Mint Press", "Pressa della zecca",
          {"particle": "lacuna:block/mint_press", "all": "lacuna:block/mint_press"},
          [box([1, 0, 1], [15, 6, 15], "#all"), box([6, 6, 6], [10, 16, 10], "#all"), box([2, 13, 7], [14, 15, 9], "#all")])
    g.tag("block", "minecraft:mineable/pickaxe", mod("mint_press"))
    for b in ("village_ledger", "council_table", "judge_bench", "guild_board", "school_desk"):
        g.tag("block", "minecraft:mineable/axe", mod(b))
    # a ledger placed outside every village founds a player village around itself
    g.shapeless("village_ledger", ["minecraft:lectern", "minecraft:writable_book", "minecraft:emerald"], mod("village_ledger"),
                category="misc")
    # the job sites are craftable like vanilla ones, so every village (not only those with civic buildings) can get a
    # Coiner, a Magistrate or a Guildmaster: an unemployed villager claims the block
    g.shaped("mint_press", ["III", " P ", "SSS"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston",
                                                  "S": "minecraft:smooth_stone"}, mod("mint_press"))
    g.shaped("judge_bench", ["SBS", "PPP", "P P"], {"S": "#minecraft:wooden_slabs", "B": "minecraft:book",
                                                    "P": "#minecraft:planks"}, mod("judge_bench"), unlock="minecraft:book")
    g.shaped("school_desk", ["SSS", "/B/"], {"S": "#minecraft:wooden_slabs", "/": "minecraft:stick", "B": "minecraft:book"},
             mod("school_desk"), unlock="minecraft:book")
    g.shaped("council_table", ["CCC", "SSS", " L "], {"C": "#minecraft:wool_carpets", "S": "#minecraft:wooden_slabs",
                                                      "L": "#minecraft:logs"}, mod("council_table"), unlock="minecraft:stick")
    for guild, banner in (("smiths", "gray"), ("gemcutters", "red"), ("scholars", "purple")):
        # the board takes its guild from the banner (the colours of the guild hall's banners)
        rid = f"guild_board_{guild}"
        g.data(f"recipe/{rid}", {"type": "minecraft:crafting_shaped", "category": "misc",
                                 "key": {"/": {"item": "minecraft:stick"}, "B": {"item": f"minecraft:{banner}_banner"},
                                         "P": {"item": "minecraft:paper"}},
                                 "pattern": ["/P/", "PBP", "/P/"],
                                 "result": {"count": 1, "id": mod("guild_board"), "components": {
                                     "minecraft:block_state": {"guild": guild},
                                     "minecraft:item_name": '{"translate":"item.lacuna.guild_board.%s"}' % guild}}})
        g.unlock(rid, f"minecraft:{banner}_banner", "misc")
    # the mint's strongbox (opening it while a villager watches costs reputation, like any village container)
    g.data("loot_table/chests/village_mint", {"type": "minecraft:chest", "random_sequence": mod("chests/village_mint"), "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 2.0, "max": 4.0}, "bonus_rolls": 0.0, "entries": [
            {"type": "minecraft:item", "name": mod("village_coin"), "weight": 4,
             "functions": [{"function": "minecraft:set_count", "add": False, "count": {"type": "minecraft:uniform", "min": 1.0, "max": 4.0}}]},
            {"type": "minecraft:item", "name": "minecraft:raw_copper", "weight": 3,
             "functions": [{"function": "minecraft:set_count", "add": False, "count": {"type": "minecraft:uniform", "min": 2.0, "max": 6.0}}]},
            {"type": "minecraft:item", "name": "minecraft:raw_iron", "weight": 2,
             "functions": [{"function": "minecraft:set_count", "add": False, "count": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0}}]},
            {"type": "minecraft:item", "name": "minecraft:gold_nugget", "weight": 2,
             "functions": [{"function": "minecraft:set_count", "add": False, "count": {"type": "minecraft:uniform", "min": 2.0, "max": 7.0}}]},
            {"type": "minecraft:item", "name": mod("raw_ruby"), "weight": 1}]}]})


def civic_pools(g):
    """One single-piece pool per civic piece: the civic square's jigsaws attach exactly that building, and operators
    can place any piece with its streets and villagers, e.g. /place jigsaw lacuna:village/plains/civic_square
    minecraft:street 7 ~ ~ ~ or /place jigsaw lacuna:village/desert/mint minecraft:building_entrance 2 ~ ~ ~."""
    for v in STYLES:
        processors = "minecraft:mossify_10_percent" if v in MOSSY else "minecraft:empty"
        for b in CIVIC_BUILDINGS + ("civic_bell",):
            g.data(f"worldgen/template_pool/village/{v}/{b}", wg.pool([mod(f"village/{v}/{b}")], processors=processors))
        # the plaza follows the ground like the vanilla streets (VillagePools adds the same element to town_centers)
        g.data(f"worldgen/template_pool/village/{v}/civic_square", wg.pool([mod(f"village/{v}/civic_square")],
                                                                           processors=processors, projection="terrain_matching"))


# ============================================================================================ 11b reputation, laws
LAWS = [("No drawn weapons inside the village", "Niente armi sguainate nel villaggio"),
        ("Curfew: strangers indoors after dusk", "Coprifuoco: gli stranieri al chiuso dopo il tramonto"),
        ("No breaking blocks inside the village", "Vietato rompere blocchi nel villaggio"),
        ("The bell rings only at dawn", "La campana suona solo all'alba")]
TIERS = {"hostile": ("Hostile", "Ostile"), "wary": ("Wary", "Diffidente"), "neutral": ("Neutral", "Neutrale"),
         "friendly": ("Friendly", "Amichevole"), "honored": ("Honored", "Onorato")}
POLICIES = {"none": ("no policy yet", "nessuna politica"), "free_market": ("Free Market", "Libero mercato"),
            "austerity": ("Austerity", "Austerità"), "festival": ("Festival", "Festa"), "martial_law": ("Martial Law", "Legge marziale")}


def reputation(g):
    for i, (en, it) in enumerate(LAWS):
        g.name(f"lacuna.village.law.{i}", en, it)
    for k, (en, it) in TIERS.items():
        g.name(f"lacuna.village.tier.{k}", en, it)
    for k, (en, it) in POLICIES.items():
        g.name(f"lacuna.village.policy.{k}", en, it)
    for k, (en, it) in VILLAGE_TYPES.items():
        g.name(f"lacuna.village.type.{k}", en, it)
    g.name("lacuna.village.header", "— %s —", "— %s —")
    g.name("lacuna.village.reputation", "Your reputation: %s (%s)", "La tua reputazione: %s (%s)")
    g.name("lacuna.village.laws", "Laws: %s; %s", "Leggi: %s; %s")
    g.name("lacuna.village.policy", "Policy: %s (council in %s days)", "Politica: %s (consiglio tra %s giorni)")
    g.name("lacuna.village.coin", "Coin value: %s, price index: %s", "Valore della moneta: %s, indice dei prezzi: %s")
    g.name("lacuna.village.rep_change", "Village reputation %s → %s (%s)", "Reputazione nel villaggio %s → %s (%s)")
    g.name("lacuna.village.warn.weapon", "Sheathe your weapon inside the village!", "Riponi l'arma dentro il villaggio!")
    g.name("lacuna.village.warn.curfew", "Curfew! Strangers must be indoors after dusk.",
           "Coprifuoco! Gli stranieri devono stare al chiuso dopo il tramonto.")
    g.name("lacuna.village.none", "This is not part of any village.", "Questo non appartiene a nessun villaggio.")
    g.name("lacuna.village.founded", "You founded a village: %s. Its villagers now live by the civic rules.",
           "Hai fondato un villaggio: %s. I suoi abitanti ora seguono le regole civiche.")
    g.name("lacuna.village.joined", "This ledger now keeps the records of %s.", "Questo registro ora custodisce gli atti di %s.")
    # doors, beds, the bell, every vanilla job site and the civic blocks
    g.tag("block", "lacuna:village_protected", *[mod(b) for b in ("village_ledger", "council_table", "judge_bench",
                                                                  "guild_board", "school_desk", "mint_press")],
          "#minecraft:doors", "#minecraft:beds", "minecraft:bell", "minecraft:blast_furnace", "minecraft:smoker",
          "minecraft:cartography_table", "minecraft:brewing_stand", "minecraft:composter", "minecraft:barrel",
          "minecraft:fletching_table", "minecraft:cauldron", "minecraft:water_cauldron", "minecraft:lava_cauldron",
          "minecraft:powder_snow_cauldron", "minecraft:lectern", "minecraft:stonecutter", "minecraft:loom",
          "minecraft:smithing_table", "minecraft:grindstone")


# ============================================================================================ 11c council, currency, court
def council_currency(g):
    g.item("village_coin", "Village Coin", "Moneta del villaggio")
    for prof, en, it in (("coiner", "Coiner", "Coniatore"), ("magistrate", "Magistrate", "Magistrato"),
                         ("guildmaster", "Guildmaster", "Maestro di gilda")):
        g.name(f"entity.minecraft.villager.lacuna.{prof}", en, it)
    g.name("lacuna.village.council", "The council of %s has decreed a new policy: %s.",
           "Il consiglio di %s ha decretato una nuova politica: %s.")
    g.name("lacuna.court.standing", "Standing: %s (%s). Fine to clear it: %s coins. Next pardon in %s days.",
           "Posizione: %s (%s). Multa per saldarla: %s monete. Prossimo perdono tra %s giorni.")
    g.name("lacuna.court.too_soon", "The court heard you recently. Come back in a few days.",
           "Il tribunale ti ha ascoltato da poco. Torna tra qualche giorno.")
    g.name("lacuna.court.nothing_owed", "You owe the village nothing.", "Non devi nulla al villaggio.")
    g.name("lacuna.court.paid", "Fine paid: %s coins. Reputation is now %s.", "Multa pagata: %s monete. Reputazione ora %s.")


# ============================================================================================ 11d guilds
GUILDS = {"smiths": ("Smiths' Guild", "Gilda dei fabbri"), "gemcutters": ("Gemcutters' Guild", "Gilda dei tagliagemme"),
          "scholars": ("Scholars' Guild", "Gilda degli studiosi")}


def guild_board(g):
    g.block_lang("guild_board", "Guild Board", "Bacheca della gilda")
    variants = {}
    for guild in GUILDS:
        g.asset(f"models/block/guild_board_{guild}", {"parent": "minecraft:block/block", "textures": {
            "particle": f"lacuna:block/guild_board_{guild}", "front": f"lacuna:block/guild_board_{guild}",
            "frame": "lacuna:block/village_ledger_side"},
            "elements": [{"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
                "north": {"texture": "#front"}, "south": {"texture": "#front"}, "east": {"texture": "#frame"},
                "west": {"texture": "#frame"}, "up": {"texture": "#frame"}, "down": {"texture": "#frame"}}}]})
        for f, y in HORIZONTAL.items():
            v = {"model": f"lacuna:block/guild_board_{guild}"}
            if y:
                v["y"] = y
            variants[f"facing={f},guild={guild}"] = v
    g.asset("blockstates/guild_board", {"variants": variants})
    g.block_item_parent("guild_board", "lacuna:block/guild_board_smiths")
    loot = loot_self("guild_board")  # the dropped board keeps its guild
    loot["pools"][0]["entries"][0]["functions"] = [{"function": "minecraft:copy_state", "block": mod("guild_board"),
                                                     "properties": ["guild"]}]
    g.loot("guild_board", loot)
    g.props("guild_board", facing=list(HORIZONTAL), guild=list(GUILDS))


BOARD_NAMES = {"smiths": ("Smiths' Guild Board", "Bacheca della Gilda dei fabbri"),
               "gemcutters": ("Gemcutters' Guild Board", "Bacheca della Gilda dei tagliagemme"),
               "scholars": ("Scholars' Guild Board", "Bacheca della Gilda degli studiosi")}


def guilds(g):
    for k, (en, it) in GUILDS.items():
        g.name(f"lacuna.guild.{k}", en, it)
        g.name(f"item.lacuna.guild_board.{k}", *BOARD_NAMES[k])
    g.item("guild_contract", "Guild Contract", "Contratto di gilda")
    g.item("repair_kit", "Repair Kit", "Kit di riparazione")
    msgs = {
        "item.lacuna.guild_contract.blank": ("A blank contract.", "Un contratto in bianco."),
        "item.lacuna.guild_contract.deliver": ("Deliver %s × %s", "Consegna %s × %s"),
        "item.lacuna.guild_contract.reward": ("Reward: %s coins and +3 reputation", "Ricompensa: %s monete e +3 reputazione"),
        "item.lacuna.repair_kit.use": ("Use with a damaged item in the other hand: restores 25%%.",
                                       "Usa con un oggetto danneggiato nell'altra mano: ripristina il 25%%."),
        "lacuna.guild.already": ("You already belong to the %s.", "Appartieni già alla %s."),
        "lacuna.guild.other": ("You are sworn to the %s.", "Hai giurato fedeltà alla %s."),
        "lacuna.guild.need_rep": ("The guild only accepts friends of the village (reputation 10+).",
                                  "La gilda accetta solo amici del villaggio (reputazione 10+)."),
        "lacuna.guild.need_coins": ("Joining costs %s village coins.", "L'iscrizione costa %s monete del villaggio."),
        "lacuna.guild.joined": ("Welcome to the %s!", "Benvenuto nella %s!"),
        "lacuna.guild.info": ("%s: right click with %s coins to join (reputation 10+, one guild per person).",
                              "%s: clic destro con %s monete per iscriverti (reputazione 10+, una gilda a persona)."),
        "lacuna.guild.contract_active": ("Finish your current contract first.", "Prima completa il contratto in corso."),
        "lacuna.guild.contract_given": ("The guild hands you a contract.", "La gilda ti consegna un contratto."),
        "lacuna.guild.wrong_board": ("This contract belongs to another guild.", "Questo contratto appartiene a un'altra gilda."),
        "lacuna.guild.missing": ("Still missing: %s × %s", "Mancano ancora: %s × %s"),
        "lacuna.guild.contract_done": ("Contract fulfilled: %s coins.", "Contratto completato: %s monete."),
        "lacuna.guild.members_only": ("Guild members only (%s).", "Solo per i membri della gilda (%s)."),
    }
    for k, (en, it) in msgs.items():
        g.name(k, en, it)
