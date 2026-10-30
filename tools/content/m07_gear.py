"""M7: Starlace armor + Zenithite, Umbral tool set, five special weapons, Heartwood Bow grasping roots."""
from gen_resources import loot_ore, mod

# Umbral Steel tools outclass diamond and the ore is commoner than diamond, so each one also sets a ruby where the head
# meets the haft (the vanilla shape, its first stick turned into a ruby; the sword's ruby is its hilt)
TOOLS = {"sword": ["U", "U", "R"], "pickaxe": ["UUU", " R ", " S "], "axe": ["UU", "UR", " S"],
         "shovel": ["U", "R", "S"], "hoe": ["UU", " R", " S"]}
TOOL_KEY = {"U": "lacuna:umbral_steel_ingot", "R": "lacuna:ruby", "S": "minecraft:stick"}


def build(g):
    zenithite(g)
    starlace(g)
    umbral_tools(g)
    weapons(g)
    grasping_roots(g)
    ember_triangle(g)


def zenithite(g):
    g.cube("zenithite_ore", "Zenithite Ore", "Minerale di zenithite",
           loot=loot_ore("zenithite_ore", "lacuna:zenithite_shard", 1, 2))
    g.cube("deepslate_zenithite_ore", "Deepslate Zenithite Ore", "Minerale di zenithite di ardesia profonda",
           loot=loot_ore("deepslate_zenithite_ore", "lacuna:zenithite_shard", 1, 2))
    g.cube("zenithite_block", "Block of Zenithite", "Blocco di zenithite")
    g.item("zenithite_shard", "Zenithite Shard", "Scheggia di zenithite")
    g.storage("lacuna:zenithite_block", "lacuna:zenithite_shard")
    for b in ("zenithite_ore", "deepslate_zenithite_ore", "zenithite_block"):
        g.tag("block", "minecraft:mineable/pickaxe", mod(b))
        g.tag("block", "minecraft:needs_iron_tool", mod(b))
    g.tag("block", "c:ores", "#c:ores/zenithite")
    g.tag("block", "c:ores/zenithite", mod("zenithite_ore"), mod("deepslate_zenithite_ore"))
    g.tag("item", "c:ores", "#c:ores/zenithite")
    g.tag("item", "c:ores/zenithite", mod("zenithite_ore"), mod("deepslate_zenithite_ore"))
    g.tag("item", "c:gems", "#c:gems/zenithite")
    g.tag("item", "c:gems/zenithite", mod("zenithite_shard"))


def starlace(g):
    g.item("starlace_cloth", "Starlace Cloth", "Tessuto stellare")
    g.shapeless("starlace_cloth", ["lacuna:zenithite_shard", "minecraft:string", "minecraft:string"], "lacuna:starlace_cloth", 2)
    pieces = {"helmet": (["CCC", "C C"], "Starlace Helmet", "Elmo stellare", "minecraft:head_armor"),
              "chestplate": (["C C", "CCC", "CCC"], "Starlace Tunic", "Tunica stellare", "minecraft:chest_armor"),
              "leggings": (["CCC", "C C", "C C"], "Starlace Leggings", "Gambali stellari", "minecraft:leg_armor"),
              "boots": (["C C", "C C"], "Starlace Boots", "Stivali stellari", "minecraft:foot_armor")}
    for piece, (pattern, en, it, tag) in pieces.items():
        iid = f"starlace_{piece}"
        g.item(iid, en, it)
        g.shaped(iid, pattern, {"C": "lacuna:starlace_cloth"}, f"lacuna:{iid}", category="equipment", unlock="lacuna:starlace_cloth")
        g.tag("item", tag, mod(iid))
        g.tag("item", "minecraft:trimmable_armor", mod(iid))
    g.name("item.lacuna.starlace_boots.glide", "Sneak while falling: glide on starlight", "Accovacciati in caduta: plana sulla luce delle stelle")
    # set abilities (gear/StarlaceSet): lore and set-bonus lines shown on every piece
    g.name("item.lacuna.starlace.lore", "Zenithite thread that still remembers the night sky.",
           "Filo di zenithite che ricorda ancora il cielo notturno.")
    g.name("item.lacuna.starlace.set", "Set bonus (%s/%s):", "Bonus del set (%s/%s):")
    g.name("item.lacuna.starlace.starlit", "Starlit (each piece): +%s%% speed at night under the open sky, or in the End",
           "Luce stellare (ogni pezzo): +%s%% di velocità di notte a cielo aperto, o nell'End")
    g.name("item.lacuna.starlace.cushion", "Starfall (%s+): fall damage reduced by %s%%",
           "Caduta stellare (%s+): danni da caduta ridotti del %s%%")
    g.name("item.lacuna.starlace.starfall", "Starfall (%s): land from over %s blocks unharmed, in a shockwave of starlight that strikes nearby foes",
           "Caduta stellare (%s): atterri illeso da oltre %s blocchi, in un'onda di luce stellare che colpisce i nemici vicini")
    g.name("item.lacuna.starlace.step", "Constellation Step (%s): press jump in mid-air to leap again",
           "Passo della costellazione (%s): premi salto a mezz'aria per balzare di nuovo")


def umbral_tools(g):
    names = {"sword": ("Umbral Steel Sword", "Spada di acciaio umbrale"), "pickaxe": ("Umbral Steel Pickaxe", "Piccone di acciaio umbrale"),
             "axe": ("Umbral Steel Axe", "Ascia di acciaio umbrale"), "shovel": ("Umbral Steel Shovel", "Pala di acciaio umbrale"),
             "hoe": ("Umbral Steel Hoe", "Zappa di acciaio umbrale")}
    for tool, (en, it) in names.items():
        iid = f"umbral_steel_{tool}"
        g.item(iid, en, it, model="handheld")
        key = {k: v for k, v in TOOL_KEY.items() if any(k in row for row in TOOLS[tool])}
        g.shaped(iid, TOOLS[tool], key, f"lacuna:{iid}", category="equipment", unlock="lacuna:umbral_steel_ingot")
        g.tag("item", f"minecraft:{tool}s", mod(iid))
        g.tag("item", "lacuna:umbral_gear", mod(iid))
    for tool, en, it in [
        ("sword", "It drinks the light from every wound.", "Beve la luce da ogni ferita."),
        ("pickaxe", "It digs best where no light has ever been.", "Scava meglio dove la luce non è mai arrivata."),
        ("axe", "Each swing is a small eclipse.", "Ogni colpo è una piccola eclissi."),
        ("shovel", "The deeper the dark, the softer the earth.", "Più fondo è il buio, più morbida è la terra."),
        ("hoe", "Even shadows need tending.", "Anche le ombre vanno coltivate."),
    ]:
        g.name(f"item.lacuna.umbral_steel_{tool}.lore", en, it)
    g.name("item.lacuna.umbral_steel.nightforged", "Nightforged: mines %s faster where light is %s or less",
           "Forgiato nella notte: scava il %s più in fretta dove la luce è %s o meno")
    g.name("item.lacuna.umbral_steel_sword.rend", "Umbral Rend: every third hit tears the shadow open",
           "Squarcio umbrale: ogni terzo colpo squarcia l'ombra")
    g.name("item.lacuna.umbral_steel_sword.rend_detail", " +%s armor-piercing damage, heals you %s, slows and blinds",
           " +%s danni che ignorano l'armatura, ti cura di %s, rallenta e acceca")
    g.name("item.lacuna.umbral_steel_axe.cleave", "Eclipse Cleave: charged hits release a shadow shockwave",
           "Fendente d'eclissi: i colpi caricati liberano un'onda d'urto d'ombra")
    g.name("item.lacuna.umbral_steel_axe.cleave_detail", " Foes within %2$s blocks take %1$s of the hit and are thrown back",
           " I nemici entro %2$s blocchi subiscono il %1$s del colpo e vengono respinti")
    g.tag("item", "lacuna:umbral_gear", mod("eclipser"), mod("gravemaw_greatsword"))
    g.tag("item", "lacuna:drawn_weapons", mod("umbral_steel_sword"), mod("umbral_steel_axe"),
          mod("gravemaw_greatsword"), mod("tidecaller_sickle"), mod("echo_dagger"), mod("heartwood_bow"),
          "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:iron_sword", "minecraft:golden_sword",
          "minecraft:diamond_sword", "minecraft:netherite_sword", "minecraft:bow", "minecraft:crossbow", "minecraft:trident",
          "minecraft:iron_axe", "minecraft:diamond_axe", "minecraft:netherite_axe", "minecraft:mace")


def weapons(g):
    # In the hands: the 32x32 hand-drawn texture, scaled by the display transforms into a real two-hander.
    # In inventories, item frames and on the ground: the 24x24 hand-drawn icon (NeoForge separate transforms).
    g.item_model("gravemaw_greatsword_icon", {"parent": "minecraft:item/generated",
                                              "textures": {"layer0": "lacuna:item/gravemaw_greatsword_icon"}})
    icon = {"parent": "lacuna:item/gravemaw_greatsword_icon"}
    g.item("gravemaw_greatsword", "Gravemaw Greatsword", "Spadone Fauce-di-tomba", model="none")
    # gui_light front like every flat item: the wrapper has no parent of its own, so it would otherwise default to the
    # side (block) lighting that shades the icon's flat quads darker in the inventory
    g.item_model("gravemaw_greatsword", {"loader": "neoforge:separate_transforms", "gui_light": "front",
                                         "base": {"parent": "lacuna:item/gravemaw_greatsword_held"},
                                         "perspectives": {"gui": icon, "fixed": icon, "ground": icon}})
    g.item_model("gravemaw_greatsword_held", {"parent": "minecraft:item/handheld",
                                              "textures": {"layer0": "lacuna:item/gravemaw_greatsword"}, "display": {
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 8.5, 1.25], "scale": [1.55, 1.55, 1.55]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 8.5, 1.25], "scale": [1.55, 1.55, 1.55]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.6, 1.13], "scale": [0.85, 0.85, 0.85]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.6, 1.13], "scale": [0.85, 0.85, 0.85]}}})
    g.item("tidecaller_sickle", "Tidecaller Sickle", "Falcetto Richiamamaree", model="handheld")
    pulling = [{"predicate": {"pulling": 1}, "model": "lacuna:item/heartwood_bow_pulling_0"},
               {"predicate": {"pulling": 1, "pull": 0.65}, "model": "lacuna:item/heartwood_bow_pulling_1"},
               {"predicate": {"pulling": 1, "pull": 0.9}, "model": "lacuna:item/heartwood_bow_pulling_2"}]
    g.item("heartwood_bow", "Heartwood Bow", "Arco di cuorlegno", model="parent:minecraft:item/bow",
           extra={"textures": {"layer0": "lacuna:item/heartwood_bow"}, "overrides": pulling})
    for i in range(3):
        g.item_model(f"heartwood_bow_pulling_{i}", {"parent": "lacuna:item/heartwood_bow",
                                                    "textures": {"layer0": f"lacuna:item/heartwood_bow_pulling_{i}"}})
    g.item("echo_dagger", "Echo Dagger", "Pugnale dell'eco", model="handheld")
    g.item("pyrrhic_scepter", "Pyrrhic Scepter", "Scettro pirrico", model="handheld")
    g.name("entity.lacuna.pyrrhic_bolt", "Pyrrhic Bolt", "Dardo pirrico")
    g.name("effect.lacuna.rooted", "Rooted", "Radicato")
    for iid, en, it in [
        ("gravemaw_greatsword", "It has eaten the light of many graves.", "Ha divorato la luce di molte tombe."),
        ("tidecaller_sickle", "It remembers every tide that ever turned.", "Ricorda ogni marea che sia mai cambiata."),
        ("heartwood_bow", "Its arrows grow roots.", "Le sue frecce mettono radici."),
        ("echo_dagger", "Every cut happens twice.", "Ogni taglio accade due volte."),
        ("pyrrhic_scepter", "Victory, at a price.", "La vittoria, a caro prezzo."),
    ]:
        g.name(f"item.lacuna.{iid}.lore", en, it)
    g.name("item.lacuna.gravemaw_greatsword.use", "Stand still and hold use: release a crescent wave",
           "Resta fermo e tieni premuto: libera un'onda a mezzaluna")
    g.name("item.lacuna.heartwood_bow.use", "Arrows sprout grasping roots that hold whatever they catch",
           "Le frecce fanno germogliare radici avvinghianti che trattengono ciò che afferrano")
    g.name("item.lacuna.heartwood_bow.use2", "Hit targets are rooted (longer against a wall)",
           "I bersagli colpiti restano radicati (più a lungo contro un muro)")
    g.name("item.lacuna.pyrrhic_scepter.use", "Use: pay 2 health for a homing bolt", "Usa: paga 2 salute per un dardo a ricerca")
    g.name("item.lacuna.tidecaller_sickle.wave", "Use: Tidal Wave", "Usa: Onda di marea")
    g.name("item.lacuna.tidecaller_sickle.wave_detail",
           " A wall of water rolls %s blocks ahead, sweeping foes away (%s damage), dousing fire and quenching lava",
           " Un muro d'acqua avanza per %s blocchi, travolge i nemici (%s danni), spegne il fuoco e solidifica la lava")
    g.name("item.lacuna.tidecaller_sickle.maelstrom", "Sneak + use with %s tide charges: Maelstrom",
           "Accovacciato + usa con %s cariche di marea: Gorgo")
    g.name("item.lacuna.tidecaller_sickle.maelstrom_detail",
           " A whirlpool drags foes in and drowns them for %s s, then bursts into a geyser",
           " Un vortice trascina dentro i nemici e li annega per %s s, poi esplode in un geyser")
    g.name("item.lacuna.tidecaller_sickle.melee", "Hits drench the target; +%s damage while you are in water or rain",
           "I colpi inzuppano il bersaglio; +%s danni mentre sei in acqua o sotto la pioggia")
    g.name("item.lacuna.tidecaller_sickle.charges",
           "The tide rises with hits in water or rain, waves that strike and time spent in water",
           "La marea sale con i colpi in acqua o sotto la pioggia, le onde che colpiscono e il tempo passato in acqua")
    g.name("lacuna.sickle.tide", "Tide charges: %s / %s", "Cariche di marea: %s / %s")
    g.name("lacuna.sickle.tide_rise", "The tide rises %s", "La marea sale %s")
    g.name("lacuna.sickle.tide_full", "Full tide %s  Sneak + use: Maelstrom", "Alta marea %s  Accovacciato + usa: Gorgo")
    g.name("lacuna.sickle.tide_ebb", "The tide ebbs %s", "La marea si ritira %s")
    g.name("lacuna.sickle.tide_low", "The tide is too low for a maelstrom %s", "La marea è troppo bassa per un gorgo %s")
    g.name("lacuna.scepter.too_weak", "You are too weak to pay the price.", "Sei troppo debole per pagarne il prezzo.")

    # the special weapons are relics: no recipes (sources in m19_progression and docs/PROGRESSION.md)
    for w in ("gravemaw_greatsword", "tidecaller_sickle", "heartwood_bow", "echo_dagger", "pyrrhic_scepter"):
        g.tag("item", "minecraft:enchantable/durability", mod(w))


def roots_model(tex):
    """A tangle: the two diagonal planes of a cross plus four inset crop-style planes (a cage around the feet)."""
    def plane(frm, to, faces, rot=False):
        el = {"from": frm, "to": to, "shade": False,
              "faces": {f: {"uv": uv, "texture": "#roots"} for f, uv in faces}}
        if rot:
            el["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
        return el
    fwd, back = [0, 0, 16, 16], [16, 0, 0, 16]
    return {
        "parent": "minecraft:block/block", "ambientocclusion": False, "render_type": "minecraft:cutout",
        "textures": {"particle": tex, "roots": tex},
        "elements": [
            plane([0.8, 0, 8], [15.2, 16, 8], [("north", fwd), ("south", fwd)], rot=True),
            plane([8, 0, 0.8], [8, 16, 15.2], [("west", fwd), ("east", fwd)], rot=True),
            plane([3, 0, 0], [3, 13, 16], [("west", fwd), ("east", back)]),
            plane([13, 0, 0], [13, 13, 16], [("west", back), ("east", fwd)]),
            plane([0, 0, 3], [16, 13, 3], [("north", fwd), ("south", back)]),
            plane([0, 0, 13], [16, 13, 13], [("north", back), ("south", fwd)]),
        ],
    }


def grasping_roots(g):
    """Heartwood Bow roots: no item, no loot; random y rotations; a dry texture for the final withering second."""
    def variants(model):
        return [{"model": model}, {"model": model, "y": 90}, {"model": model, "y": 180}, {"model": model, "y": 270}]
    blockstate = {"variants": {"withering=false": variants("lacuna:block/grasping_roots"),
                               "withering=true": variants("lacuna:block/grasping_roots_withered")}}
    g.single("grasping_roots", "Grasping Roots", "Radici avvinghianti", roots_model("lacuna:block/grasping_roots"),
             item=False, loot=None, blockstate=blockstate)
    g.asset("models/block/grasping_roots_withered", roots_model("lacuna:block/grasping_roots_withered"))
    g.props("grasping_roots", sprouted=["true", "false"], withering=["true", "false"])


def ember_triangle(g):
    """Ember Triangle: a relic that releases a circular thermal wave and pulverizes what it catches (gear/EmberWave)."""
    g.item("ember_triangle", "Ember Triangle", "Triangolo di brace")  # a relic: no recipe (m19_progression)
    # the pulverizing damage: nothing protects from it (armor, shields, resistance, enchantments) and it pushes nobody
    g.data("damage_type/pulverized", {"exhaustion": 0.0, "message_id": "lacuna.pulverized", "scaling": "never",
                                      "effects": "burning"})
    for tag in ("minecraft:bypasses_armor", "minecraft:bypasses_shield", "minecraft:bypasses_effects",
                "minecraft:no_knockback"):
        g.tag("damage_type", tag, "lacuna:pulverized")
    g.name("death.attack.lacuna.pulverized", "%1$s was pulverized", "%1$s è stato polverizzato")
    g.name("death.attack.lacuna.pulverized.player", "%1$s was pulverized by %2$s", "%1$s è stato polverizzato da %2$s")
    g.name("death.attack.lacuna.pulverized.item", "%1$s was pulverized by %2$s using %3$s",
           "%1$s è stato polverizzato da %2$s con %3$s")
    g.name("item.lacuna.ember_triangle.lore", "Three embers that swore never to go out.",
           "Tre braci che hanno giurato di non spegnersi mai.")
    g.name("item.lacuna.ember_triangle.use", "Use: Thermal Wave (%s blocks around you)",
           "Usa: Onda termica (%s blocchi attorno a te)")
    g.name("item.lacuna.ember_triangle.use_detail", " Hostile creatures and anything hunting you are pulverized on the spot",
           " Le creature ostili e chiunque ti dia la caccia vengono polverizzati all'istante")
    g.name("item.lacuna.ember_triangle.spare", " Spares you, your pets, villagers, golems and peaceful animals",
           " Risparmia te, i tuoi animali, i villici, i golem e gli animali pacifici")
    g.name("item.lacuna.ember_triangle.pvp", " Other players only when PvP is on",
           " Gli altri giocatori solo se il PvP è attivo")
    g.name("item.lacuna.ember_triangle.boss", " Bosses resist: %s damage instead", " I boss resistono: subiscono invece %s danni")
    g.name("item.lacuna.ember_triangle.cooldown", "Cooldown: %s s", "Ricarica: %s s")
    g.name("item.lacuna.ember_triangle.repair", "Repair at an anvil with Ember (+%s uses each)",
           "Si ripara all'incudine con la brace (+%s usi ciascuna)")
