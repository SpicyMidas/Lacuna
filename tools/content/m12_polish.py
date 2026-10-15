"""M12: advancement tree, tooltip polish (item.lacuna.<id>.tip lines shown by the client GenericTooltips hook)."""

ADV = "lacuna:lacuna/"


def build(g):
    advancements(g)
    tips(g)


def display(icon, key, frame="task", background=None, hidden=False):
    d = {"icon": {"id": icon}, "title": {"translate": f"advancements.lacuna.{key}.title"},
         "description": {"translate": f"advancements.lacuna.{key}.description"}, "frame": frame,
         "show_toast": True, "announce_to_chat": True, "hidden": hidden}
    if background:
        d["background"] = background
    return d


def adv(g, key, icon, criteria, parent=None, frame="task", background=None, en=("", ""), it=("", "")):
    data = {"display": display(icon, key, frame, background), "criteria": criteria,
            "requirements": [list(criteria.keys())]}
    if parent:
        data["parent"] = ADV + parent
    g.data(f"advancement/lacuna/{key}", data)
    g.name(f"advancements.lacuna.{key}.title", en[0], it[0])
    g.name(f"advancements.lacuna.{key}.description", en[1], it[1])


def has(item):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}


def code():
    return {"code": {"trigger": "minecraft:impossible"}}


def advancements(g):
    adv(g, "root", "lacuna:totem_of_banishing", {"ruby": has("lacuna:ruby"), "raw_ruby": has("lacuna:raw_ruby")},
        background="lacuna:textures/block/ruin_stone_bricks.png",
        en=("Lacuna", "Find your first ruby: the world remembers more than it shows."),
        it=("Lacuna", "Trova il tuo primo rubino: il mondo ricorda più di quanto mostri."))
    g.files[f"data/lacuna/advancement/lacuna/root.json"]["requirements"] = [["ruby", "raw_ruby"]]
    adv(g, "enter_forgotten_land", "lacuna:chiseled_ruin_glyph",
        {"enter": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "lacuna:forgotten_land"}}}, "root",
        en=("A Land Nobody Remembers", "Enter the Forgotten Land."), it=("Una terra che nessuno ricorda", "Entra nella Terra Dimenticata."))
    adv(g, "survive_banishment", "lacuna:totem_of_banishing",
        {"return": {"trigger": "minecraft:changed_dimension", "conditions": {"from": "lacuna:forgotten_land", "to": "minecraft:overworld"}}},
        "enter_forgotten_land", frame="goal",
        en=("Remembered Again", "Come back from the Forgotten Land."), it=("Di nuovo ricordato", "Torna dalla Terra Dimenticata."))
    adv(g, "forge_eclipser", "lacuna:eclipser", {"eclipser": has("lacuna:eclipser")}, "root", frame="goal",
        en=("Blade of the Eclipse", "Forge the Eclipser."), it=("Lama dell'eclisse", "Forgia l'Eclissatore."))
    adv(g, "throw_suns_fury", "lacuna:suns_fury", code(), "forge_eclipser", frame="challenge",
        en=("A Piece of the Sun", "Throw Sun's Fury."), it=("Un pezzo di sole", "Scaglia la Furia del Sole."))
    adv(g, "link_pearl", "lacuna:fractured_pearl", code(), "root",
        en=("Two Halves", "Split a Fractured Pearl into its linked shards."), it=("Due metà", "Dividi una perla fratturata nelle sue schegge collegate."))
    adv(g, "village_friend", "lacuna:village_ledger", code(), "root",
        en=("A Friend of the Village", "Become Friendly with a village: trade, defend it, ring its bell at dawn."),
        it=("Amico del villaggio", "Diventa Amichevole con un villaggio: commercia, difendilo, suona la sua campana all'alba."))
    adv(g, "triangle_of_instant_pulverization", "lacuna:ember_triangle", code(), "root", frame="challenge",
        en=("Triangle of Instant Pulverization", "Turn a creature to dust with the Ember Triangle."),
        it=("Triangolo di polverizzazione istantanea", "Riduci in polvere una creatura con il Triangolo di brace."))


TIPS = {
    "ruby": ("A gem the mountains cried.", "Una gemma pianta dalle montagne."),
    "divine_glass_shard": ("Still warm with old light.", "Ancora calda di luce antica."),
    "umbral_steel_ingot": ("Heavier in the dark.", "Più pesante al buio."),
    "zenithite_shard": ("Found only where waterfalls run upward.", "Si trova solo dove le cascate salgono."),
    "chronal_shard": ("It ticks when nobody listens.", "Ticchetta quando nessuno ascolta."),
    "echo_fragment": ("A memory that refused to fade.", "Un ricordo che ha rifiutato di svanire."),
    "solar_flare_fragment": ("Rare beyond measure.", "Rarissimo."),
    "ember": ("Burns for a very long time.", "Brucia molto a lungo."),
    "silk_dust": ("Right click a surface to leave a fading glow.", "Clic destro su una superficie per lasciare un bagliore che svanisce."),
    "clockwork_gear": ("Still turning, a little.", "Gira ancora, un poco."),
    "clockwork_decoy": ("Throw it: monsters chase the mannequin for 15 s.", "Lancialo: i mostri inseguono il manichino per 15 s."),
    "village_coin": ("Minted from what the land gave up.", "Coniata da ciò che la terra ha ceduto."),
    "updraft_spring": ("Lifts whatever stands above it.", "Solleva qualunque cosa vi stia sopra."),
    "spore_mushroom": ("Makes nearby creatures glow.", "Fa brillare le creature vicine."),
    "obelisk_inscription": ("The names move when you look away.", "I nomi si muovono quando distogli lo sguardo."),
    "sunken_bell": ("Ring it to hear the ores answer.", "Suonala per sentire rispondere i minerali."),
    "village_ledger": ("Right click: your standing, the laws and the policy of the village. Placed outside villages, it founds one.",
                       "Clic destro: posizione, leggi e politica del villaggio. Piazzato fuori dai villaggi, ne fonda uno."),
    "guild_board": ("Join a guild or take a contract.", "Iscriviti a una gilda o prendi un contratto."),
    "judge_bench": ("Pay fines with village coins.", "Paga le multe con le monete del villaggio."),
    "mint_press": ("A Coiner's workplace.", "Il posto di lavoro del coniatore."),
    "school_desk": ("Villagers learn faster near it.", "Vicino ad esso i villici imparano più in fretta."),
}


def tips(g):
    for item, (en, it) in TIPS.items():
        g.name(f"item.lacuna.{item}.tip", en, it)
