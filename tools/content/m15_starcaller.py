"""Starcaller Trumpet: blown like a goat horn, it calls a small falling star (lacuna:falling_star) down on the block or
creature in the crosshair; the star leaves a glowing Fallen Star block that breaks into Star Fragments."""
from gen_resources import loot_ore, mod
from content.m12_polish import adv, code

# vanilla goat horn hand poses (models/item/goat_horn.json and tooting_goat_horn.json): the trumpet is held the same way
# held like a bow (vanilla item/bow display), and blown with the bow draw pose (StarcallerTrumpetItem: UseAnim.BOW)
HELD = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def build(g):
    trumpet(g)
    fallen_star(g)
    falling_star(g)
    starlace(g)
    adv(g, "wish_upon_a_star", "lacuna:starcaller_trumpet", code(), "root", frame="goal",
        en=("Wish Upon a Star", "Call a falling star down with the Starcaller Trumpet."),
        it=("Esprimi un desiderio", "Fai cadere una stella con la Tromba Richiamastelle."))


def trumpet(g):
    g.item("starcaller_trumpet", "Starcaller Trumpet", "Tromba Richiamastelle", extra={"display": HELD})
    g.shaped("starcaller_trumpet", ["ZCZ", "GHG", " G "],
             {"Z": "lacuna:zenithite_shard", "C": "lacuna:starlace_cloth", "G": "minecraft:gold_ingot", "H": "minecraft:goat_horn"},
             "lacuna:starcaller_trumpet", category="equipment", unlock="minecraft:goat_horn")
    g.tag("item", "minecraft:enchantable/durability", mod("starcaller_trumpet"))
    g.name("item.lacuna.starcaller_trumpet.lore", "Sound it, and a star answers.", "Suonala, e una stella risponde.")
    g.name("item.lacuna.starcaller_trumpet.use", "Use: call a falling star down where you look (up to %s blocks)",
           "Usa: fai cadere una stella dove guardi (fino a %s blocchi)")
    g.name("item.lacuna.starcaller_trumpet.lock", " Aim at a creature: the star locks on and follows it as it falls",
           " Mira a una creatura: la stella la aggancia e la segue mentre cade")
    g.name("item.lacuna.starcaller_trumpet.blast", " Small blast: up to %s damage, knockback, fire; leaves a Fallen Star",
           " Piccola esplosione: fino a %s danni, contraccolpo, fuoco; lascia una Stella caduta")
    g.name("item.lacuna.starcaller_trumpet.crater", " Cracks the ground only when mobGriefing is on",
           " Spacca il terreno solo se mobGriefing è attivo")
    g.name("item.lacuna.starcaller_trumpet.cooldown", "Cooldown: %s s", "Ricarica: %s s")
    g.name("item.lacuna.starcaller_trumpet.repair", "Repair at an anvil with Star Fragments",
           "Si ripara all'incudine con i frammenti di stella")
    g.name("lacuna.starcaller.no_target", "The call finds nothing for a star to fall on.",
           "Il richiamo non trova nulla su cui far cadere una stella.")
    g.name("lacuna.starcaller.too_far", "No star can reach that spot.", "Nessuna stella può raggiungere quel punto.")


def fallen_star(g):
    rock = "#rock"
    core = "#core"

    def faces(tex):
        return {d: {"texture": tex} for d in ("north", "south", "east", "west", "up", "down")}

    def el(frm, to, tex, rot=None, glow=False):
        e = {"from": frm, "to": to, "faces": faces(tex)}
        if rot:
            e["rotation"] = rot
        if glow:
            e["neoforge_data"] = dict(GLOW)
        return e

    elements = [
        el([3, 0, 4], [13, 3, 12], rock),
        el([4, 3, 5], [11, 5, 11], rock),
        el([1, 0, 2], [3, 1, 4], rock),
        el([12, 0, 12], [14, 1, 14], rock),
        el([6, 3, 6], [10, 8, 10], core, {"origin": [8, 5, 8], "axis": "y", "angle": 45}, glow=True),
        el([7, 6, 7], [9, 11, 9], core, {"origin": [8, 8, 8], "axis": "z", "angle": 22.5}, glow=True),
        el([10, 3, 7], [11.5, 6, 8.5], core, {"origin": [10.5, 3, 7.5], "axis": "x", "angle": -22.5}, glow=True),
    ]
    model = {"parent": "minecraft:block/block",
             "textures": {"particle": "lacuna:block/fallen_star", "rock": "lacuna:block/fallen_star",
                          "core": "lacuna:block/fallen_star_core"},
             "elements": elements,
             "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 3, 0], "scale": [1.0, 1.0, 1.0]},
                         "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
                         "fixed": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [1.0, 1.0, 1.0]},
                         "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
                         "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
                         "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]}}}
    g.single("fallen_star", "Fallen Star", "Stella caduta", model,
             loot=loot_ore("fallen_star", "lacuna:star_fragment", 1, 3, "uniform"))
    g.tag("block", "minecraft:mineable/pickaxe", mod("fallen_star"))
    g.item("star_fragment", "Star Fragment", "Frammento di stella")
    g.name("item.lacuna.star_fragment.tip", "Still warm from the fall. Mends the Starcaller Trumpet, weaves Starlace.",
           "Ancora caldo della caduta. Ripara la Tromba Richiamastelle, intesse il tessuto stellare.")
    g.name("item.lacuna.fallen_star.tip", "Mine it with a pickaxe for Star Fragments.",
           "Scavala con un piccone per ottenere frammenti di stella.")


def falling_star(g):
    g.name("entity.lacuna.falling_star", "Falling Star", "Stella cadente")
    g.data("damage_type/falling_star", {"exhaustion": 0.1, "message_id": "lacuna.falling_star",
                                        "scaling": "when_caused_by_living_non_player", "effects": "burning"})
    # blast protection applies; the star pushes its victims itself
    for tag in ("minecraft:is_explosion", "minecraft:no_knockback"):
        g.tag("damage_type", tag, "lacuna:falling_star")
    g.name("death.attack.lacuna.falling_star", "%1$s was struck by a falling star called by %2$s",
           "%1$s è stato colpito da una stella cadente richiamata da %2$s")
    g.name("death.attack.lacuna.falling_star.player", "%1$s was struck by a falling star while fighting %2$s",
           "%1$s è stato colpito da una stella cadente mentre combatteva contro %2$s")
    g.name("death.attack.lacuna.falling_star.item", "%1$s was struck by a falling star called by %2$s with %3$s",
           "%1$s è stato colpito da una stella cadente richiamata da %2$s con %3$s")


def starlace(g):
    # star fragments weave Starlace Cloth more richly than zenithite does (zenithite + 2 string -> 2 cloth)
    g.shapeless("starlace_cloth_from_star_fragment",
                ["lacuna:star_fragment", "minecraft:string", "minecraft:string", "minecraft:string"],
                "lacuna:starlace_cloth", 4, unlock="lacuna:star_fragment")
