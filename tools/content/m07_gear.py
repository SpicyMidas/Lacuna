"""M7: Starlace armor + Zenithite, Umbral tool set, five special weapons, Heartwood Bow grasping roots."""
from gen_resources import loot_ore, mod

TOOLS = {"sword": ["R", "R", "S"], "pickaxe": ["RRR", " S ", " S "], "axe": ["RR", "RS", " S"],
         "shovel": ["R", "S", "S"], "hoe": ["RR", " S", " S"]}


def build(g):
    zenithite(g)
    starlace(g)
    umbral_tools(g)
    weapons(g)
    grasping_roots(g)


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


def umbral_tools(g):
    names = {"sword": ("Umbral Steel Sword", "Spada di acciaio umbrale"), "pickaxe": ("Umbral Steel Pickaxe", "Piccone di acciaio umbrale"),
             "axe": ("Umbral Steel Axe", "Ascia di acciaio umbrale"), "shovel": ("Umbral Steel Shovel", "Pala di acciaio umbrale"),
             "hoe": ("Umbral Steel Hoe", "Zappa di acciaio umbrale")}
    for tool, (en, it) in names.items():
        iid = f"umbral_steel_{tool}"
        g.item(iid, en, it, model="handheld")
        g.shaped(iid, TOOLS[tool], {"R": "lacuna:umbral_steel_ingot", "S": "minecraft:stick"}, f"lacuna:{iid}",
                 category="equipment", unlock="lacuna:umbral_steel_ingot")
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
    g.tag("item", "lacuna:drawn_weapons", mod("umbral_steel_sword"), mod("umbral_steel_axe"), mod("ruby_sword"), mod("ruby_axe"),
          mod("gravemaw_greatsword"), mod("tidecaller_sickle"), mod("echo_dagger"), mod("heartwood_bow"),
          "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:iron_sword", "minecraft:golden_sword",
          "minecraft:diamond_sword", "minecraft:netherite_sword", "minecraft:bow", "minecraft:crossbow", "minecraft:trident",
          "minecraft:iron_axe", "minecraft:diamond_axe", "minecraft:netherite_axe", "minecraft:mace")


def weapons(g):
    # 32x32 hand-drawn texture; the display transforms make it a real two-hander in the hands (GUI stays normal)
    g.item("gravemaw_greatsword", "Gravemaw Greatsword", "Spadone Fauce-di-tomba", model="handheld", extra={"display": {
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 8.5, 1.25], "scale": [1.55, 1.55, 1.55]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 8.5, 1.25], "scale": [1.55, 1.55, 1.55]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.6, 1.13], "scale": [0.85, 0.85, 0.85]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.6, 1.13], "scale": [0.85, 0.85, 0.85]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]}}})
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
    g.name("lacuna.sickle.tide", "Tide charges: %s / %s", "Cariche di marea: %s / %s")
    g.name("lacuna.scepter.too_weak", "You are too weak to pay the price.", "Sei troppo debole per pagarne il prezzo.")

    g.shaped("gravemaw_greatsword", ["UUU", "URU", " S "],
             {"U": "lacuna:umbral_steel_ingot", "R": "lacuna:ruby", "S": "minecraft:stick"},
             "lacuna:gravemaw_greatsword", category="equipment", unlock="lacuna:umbral_steel_ingot")
    g.shaped("tidecaller_sickle", [" PP", " SP", "S  "], {"P": "minecraft:prismarine_shard", "S": "minecraft:stick"},
             "lacuna:tidecaller_sickle", category="equipment", unlock="minecraft:prismarine_shard")
    g.shaped("echo_dagger", [" E ", " D ", " S "],
             {"E": "minecraft:echo_shard", "D": "lacuna:divine_glass_shard", "S": "minecraft:stick"},
             "lacuna:echo_dagger", category="equipment", unlock="minecraft:echo_shard")
    g.shaped("pyrrhic_scepter", [" R ", " B ", " G "],
             {"R": "lacuna:ruby", "B": "minecraft:blaze_rod", "G": "minecraft:gold_ingot"},
             "lacuna:pyrrhic_scepter", category="equipment", unlock="minecraft:blaze_rod")
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
