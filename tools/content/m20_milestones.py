"""Advancements as a progression guide: the Lacuna tab gains the milestones it lacked, each description hinting at where
to go (the Almanac has the details). Vanilla triggers wherever one fits; `lacuna:used_relic` (guide/UsedRelicTrigger,
fired where a power takes effect) for relic and gadget powers; the code-granted `minecraft:impossible` criterion
(util/LacunaAdvancements) for the rest. Small XP rewards on the two challenges only.

The tree (new milestones marked +):
  root (a ruby)
    + A Gem the Mountains Cried (ruby) -> + Heavier in the Dark (umbral steel) -> + Nightforged (an umbral tool)
                                        -> + Rubies Only (trade with the Last Merchant), Two Halves (pearl),
                                           + Wayfarer (the chart)
    + Never Forged (any relic) -> Blade of the Eclipse -> A Piece of the Sun, + Through Another's Eyes (possession)
                               -> Triangle of Instant Pulverization, Wish Upon a Star, + It's Alive! (wand)
                               -> + Curator of the Forgotten (every relic, challenge)
    The Shape of the Gap (Quintessence) -> + The Totem's Own Stone (exilite, challenge)
                                        -> + A Hand That Remembers (totem) -> + The Way Stays Open (portal)
                                                                          -> A Land Nobody Remembers -> Remembered Again,
                                           + Where Waterfalls Run Upward,
                                           + Count the Mirrors (labyrinth) -> + Only You Live There Now (Reflection)
                                        -> Which One Is Real?
    + The Drowned Are Not Alone (bell tower) -> + Ore Song (ring the bell)
    + Seven Brothers, No Names (obelisk), Shooting Star, A Friend of the Village, It's a Wrap,
    + It's in the Bag, + Borrowed Seconds (hourglass), + Moth to a Flame (silk dust)
"""
from content import m19_progression as m19
from content.m12_polish import adv, code, has

RELICS = [f"lacuna:{r}" for r in m19.RELIC_SOURCES]


def has_any(*items):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": list(items)}]}}


def entity_is(entity_type):
    return [{"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": entity_type}}]


def inside(structure):
    return {"trigger": "minecraft:location", "conditions": {"player": [
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"location": {"structures": structure}}}]}}


def used_relic(item):
    return {"trigger": "lacuna:used_relic", "conditions": {"item": {"items": item}}}


def clicked(block):
    """Right-clicked {@code block} with an empty hand (or an item it ignores) and the block answered."""
    return {"trigger": "minecraft:default_block_use", "conditions": {"location": [
        {"condition": "minecraft:block_state_property", "block": block}]}}


def build(g):
    g.tag("item", "lacuna:relics", *RELICS)
    g.finalizer(check_tree)

    # ---------------------------------------------------------------------------------------- the Overworld
    adv(g, "mine_ruby", "lacuna:ruby", {"ruby": has("lacuna:ruby")}, "root",
        en=("A Gem the Mountains Cried", "Mine a ruby: the ore hides deep, from Y 16 down, like diamonds."),
        it=("Una gemma pianta dalle montagne", "Scava un rubino: il minerale si nasconde in profondità, da Y 16 in giù, "
                                               "come i diamanti."))
    adv(g, "umbral_steel", "lacuna:umbral_steel_ingot", {"ingot": has("lacuna:umbral_steel_ingot")}, "mine_ruby",
        en=("Heavier in the Dark", "Smelt an Umbral Steel ingot from the dark ore that lies below Y 0."),
        it=("Più pesante al buio", "Fondi un lingotto di acciaio umbrale dal minerale scuro che giace sotto Y 0."))
    adv(g, "nightforged", "lacuna:umbral_steel_pickaxe",
        {"tool": has_any(*[f"lacuna:umbral_steel_{t}" for t in ("sword", "pickaxe", "axe", "shovel", "hoe")])},
        "umbral_steel",
        en=("Nightforged", "Forge an Umbral Steel tool, a ruby set in its grip: it digs faster where it is dark."),
        it=("Forgiato nella notte", "Forgia un attrezzo di acciaio umbrale, con un rubino nell'impugnatura: scava più in "
                                    "fretta dove è buio."))
    adv(g, "last_merchant", "lacuna:last_merchant_spawn_egg",
        {"trade": {"trigger": "minecraft:villager_trade", "conditions": {"villager": entity_is("lacuna:last_merchant")}}},
        "mine_ruby",
        en=("Rubies Only", "Trade with the Last Merchant. He turns up by day now and then, and takes nothing but rubies."),
        it=("Solo rubini", "Commercia con l'Ultimo mercante. Arriva di giorno ogni tanto, e non accetta altro che rubini."))
    adv(g, "wayfarer", "lacuna:wayfarers_chart", {"chart": has("lacuna:wayfarers_chart")}, "mine_ruby",
        en=("Wayfarer", "Craft a Wayfarer's Chart, a map that remembers the Overworld wherever you are."),
        it=("Viandante", "Fabbrica una Carta del viandante, una mappa che ricorda il Mondo normale ovunque tu sia."))

    # ---------------------------------------------------------------------------------------- relics
    adv(g, "find_relic", "lacuna:mirror_of_recursion", {"relic": has("#lacuna:relics")}, "root",
        en=("Never Forged", "Find a relic. No smith can make one: search Sunken Bell Towers, treasure and Mirror Hoards, "
                            "or pay the Last Merchant."),
        it=("Mai forgiata", "Trova una reliquia. Nessun fabbro sa farne: cerca nelle torri della campana sommersa, nei "
                            "tesori e nei Labirinti degli specchi, o paga l'Ultimo mercante."))
    adv(g, "possession", "minecraft:ender_eye", code(), "forge_eclipser",
        en=("Through Another's Eyes", "Possess a creature while shadow walking after an Eclipser dash."),
        it=("Con gli occhi di un altro", "Possiedi una creatura durante il Passo d'ombra dopo uno scatto "
                                         "dell'Eclissatore."))
    adv(g, "its_alive", "lacuna:quickening_wand", {"woke": used_relic("lacuna:quickening_wand")}, "find_relic",
        en=("It's Alive!", "Wake a dropped item with the Wand of Quickening and let it fight beside you."),
        it=("È vivo!", "Risveglia un oggetto a terra con la Bacchetta del Risveglio e lascialo combattere al tuo fianco."))
    every_relic = {r.split(":", 1)[1]: has(r) for r in RELICS}
    adv(g, "every_relic", "lacuna:suns_fury", every_relic, "find_relic", frame="challenge",
        en=("Curator of the Forgotten", "Hold each of the fifteen relics at least once. The Almanac says where they hide."),
        it=("Custode del dimenticato", "Possiedi almeno una volta ciascuna delle quindici reliquie. L'Almanacco dice "
                                       "dove si nascondono."))
    every_all(g, "every_relic", 100)

    # ---------------------------------------------------------------------------------------- totems and the land
    adv(g, "totem_of_banishing", "lacuna:totem_of_banishing", {"totem": has("lacuna:totem_of_banishing")},
        "lacunar_quintessence",
        en=("A Hand That Remembers", "Craft a Totem of Banishing: a Lacunar Quintessence set in Exilite Ingots, a ruby "
                                     "at its base."),
        it=("Una mano che ricorda", "Fabbrica un Totem dell'esilio: una Quintessenza lacunare incastonata in lingotti di "
                                    "esilite, con un rubino alla base."))
    adv(g, "open_portal", "lacuna:forgotten_portal_frame", code(), "totem_of_banishing",
        en=("The Way Stays Open", "Light a Forgotten Portal: eight frames around a 2×2 opening, touched with the totem."),
        it=("La via resta aperta", "Accendi un Portale dimenticato: otto cornici attorno a un'apertura 2×2, toccate con "
                                   "il totem."))
    adv(g, "zenithite", "lacuna:zenithite_shard", {"shard": has("lacuna:zenithite_shard")}, "enter_forgotten_land",
        en=("Where Waterfalls Run Upward", "Mine zenithite on the floating islands of the Inverted Falls."),
        it=("Dove le cascate salgono", "Scava la zenithite sulle isole fluttuanti delle Cascate rovesciate."))
    adv(g, "exilite", "lacuna:exilite_debris", {"debris": has("lacuna:exilite_debris")}, "lacunar_quintessence",
        frame="challenge",
        en=("The Totem's Own Stone", "Dig up Exilite Debris, hidden in the deepest stone of the Overworld (below Y -16) "
                                     "and of the Forgotten Land (diamond pickaxe)."),
        it=("La pietra del totem", "Dissotterra dei Detriti di esilite, nascosti nella pietra più profonda del Mondo "
                                   "normale (sotto Y -16) e della Terra Dimenticata (piccone di diamante)."))
    reward(g, "exilite", 50)
    adv(g, "mirror_labyrinth", "lacuna:mirror_glass", {"inside": inside("lacuna:mirror_labyrinth")}, "enter_forgotten_land",
        en=("Count the Mirrors", "Walk into a Mirror Labyrinth, somewhere in the Forgotten Land."),
        it=("Conta gli specchi", "Entra in un Labirinto degli specchi, da qualche parte nella Terra Dimenticata."))
    adv(g, "defeat_reflection", "lacuna:lacunar_quintessence",
        {"killed": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": entity_is("lacuna:reflection")}}},
        "mirror_labyrinth", frame="goal",
        en=("Only You Live There Now", "Step on the ring at the heart of a Mirror Labyrinth and defeat your own Reflection."),
        it=("Ora lì vivi solo tu", "Calpesta l'anello al centro di un Labirinto degli specchi e sconfiggi il tuo "
                                   "Riflesso."))

    # ---------------------------------------------------------------------------------------- the world
    adv(g, "sunken_bell_tower", "lacuna:sunken_bell", {"inside": inside("lacuna:sunken_bell_tower")}, "root",
        en=("The Drowned Are Not Alone", "Find a Sunken Bell Tower on the ocean floor."),
        it=("Gli annegati non sono soli", "Trova una Torre della campana sommersa sul fondo dell'oceano."))
    adv(g, "ore_song", "lacuna:ruby_ore", {"rang": clicked("lacuna:sunken_bell")}, "sunken_bell_tower",
        en=("Ore Song", "Ring a Sunken Bell and see the ores around it answer."),
        it=("Il canto dei minerali", "Suona una Campana sommersa e guarda rispondere i minerali intorno."))
    adv(g, "obelisk", "lacuna:obelisk_inscription", {"read": clicked("lacuna:obelisk_inscription")}, "root",
        en=("Seven Brothers, No Names", "Read an Obelisk of Unreadable Names, a black pillar standing alone in the wilds."),
        it=("Sette fratelli, nessun nome", "Leggi un Obelisco dei nomi illeggibili, un pilastro nero solitario nelle "
                                           "terre selvagge."))
    adv(g, "in_the_bag", "lacuna:comically_large_bag", {"bagged": used_relic("lacuna:comically_large_bag")}, "root",
        en=("It's in the Bag", "Stuff a creature into a Comically Large Bag."),
        it=("È nel sacco", "Infila una creatura in un Sacco Comicamente Grande."))
    adv(g, "borrowed_seconds", "lacuna:chrono_hourglass", {"fed": {"trigger": "minecraft:item_used_on_block", "conditions": {
        "location": [{"condition": "minecraft:location_check", "predicate": {"block": {"blocks": "lacuna:chrono_hourglass"}}},
                     {"condition": "minecraft:match_tool", "predicate": {"items": "lacuna:chronal_shard"}}]}}}, "root",
        en=("Borrowed Seconds", "Feed a Chronal Shard to a Chrono Hourglass and watch time hurry around it."),
        it=("Secondi in prestito", "Nutri una Clessidra cronale con una Scheggia cronale e guarda il tempo correre "
                                   "intorno."))
    adv(g, "moth_to_a_flame", "lacuna:silk_dust", {"dust": has("lacuna:silk_dust")}, "root",
        en=("Moth to a Flame", "Collect Silk Dust from a Lantern Moth: they flutter around bright light on forest and "
                               "swamp nights."),
        it=("Come falene alla fiamma", "Raccogli Polvere di seta da una Falena lanterna: svolazzano attorno alle luci "
                                       "nelle notti di foreste e paludi."))


def check_tree(g):
    """Every Lacuna advancement's parent must exist (a missing one silently drops the whole branch in game)."""
    prefix = "data/lacuna/advancement/lacuna/"
    keys = {p[len(prefix):-5] for p in g.files if p.startswith(prefix)}
    for k in keys:
        parent = g.files[f"{prefix}{k}.json"].get("parent")
        if parent:
            assert parent.startswith("lacuna:lacuna/") and parent.split("/", 1)[1] in keys, f"advancement {k}: no parent {parent}"


def every_all(g, key, xp):
    """Turns the advancement's single OR group into one requirement per criterion (all of them), with an XP reward."""
    path = f"data/lacuna/advancement/lacuna/{key}.json"
    data = dict(g.files[path])
    data["requirements"] = [[c] for c in data["criteria"]]
    data["rewards"] = {"experience": xp}
    g.override(path, data)


def reward(g, key, xp):
    path = f"data/lacuna/advancement/lacuna/{key}.json"
    data = dict(g.files[path])
    data["rewards"] = {"experience": xp}
    g.override(path, data)
