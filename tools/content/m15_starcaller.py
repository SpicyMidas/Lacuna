"""Starcaller Trumpet: blown like a goat horn, it calls a small falling star (lacuna:falling_star) down on the block or
creature in the crosshair; the star leaves a Star Rock (molten, then prime, then extinguished) that breaks into Star
Fragments."""
from gen_resources import SILK, SURVIVES, loot_ore, mod, pool
from content.m12_polish import adv, code

# Held: gripped by its middle, the fist round the valves, square to the forearm, bell forward (in-hand centre at the
# fist; solved for the vanilla one-handed hold). Blown (lacuna:blowing, client/star/TrumpetClient): raised level to the
# lips with both hands, mouthpiece at the mouth; the third-person values are solved against the arm angles of
# client/pose/LacunaArmPoses.TRUMPET_BLOW (change both together).
HELD = {
    "thirdperson_righthand": {"rotation": [0, -90, -90], "translation": [0, -2, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, 90], "translation": [0, -2, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -75, 5], "translation": [1.13, 2.4, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 75, -5], "translation": [1.13, 2.4, 1.13], "scale": [0.68, 0.68, 0.68]},
}
BLOWING = {
    "thirdperson_righthand": {"rotation": [170.9, -61.4, 180], "translation": [-0.45, 0.33, -1.96], "scale": [0.75, 0.75, 0.75]},
    "thirdperson_lefthand": {"rotation": [170.9, -61.4, -180], "translation": [-0.45, 0.33, -1.96], "scale": [0.75, 0.75, 0.75]},
    "firstperson_righthand": {"rotation": [-68.2, -17, -53.6], "translation": [-12.14, 5.49, 3.58], "scale": [0.7, 0.7, 0.7]},
    "firstperson_lefthand": {"rotation": [-68.2, -17, -53.6], "translation": [-12.14, 5.49, 3.58], "scale": [0.7, 0.7, 0.7]},
}


def build(g):
    trumpet(g)
    star_rock(g)
    g.item("star_fragment", "Star Fragment", "Frammento di stella")
    g.name("item.lacuna.star_fragment.tip", "Still warm from the fall. Mends the Starcaller Trumpet, weaves Starlace.",
           "Ancora caldo della caduta. Ripara la Tromba Richiamastelle, intesse il tessuto stellare.")
    falling_star(g)
    starlace(g)
    adv(g, "wish_upon_a_star", "lacuna:starcaller_trumpet", code(), "root", frame="goal",
        en=("Wish Upon a Star", "Call a falling star down with the Starcaller Trumpet."),
        it=("Esprimi un desiderio", "Fai cadere una stella con la Tromba Richiamastelle."))


def trumpet(g):
    g.item("starcaller_trumpet", "Starcaller Trumpet", "Tromba Richiamastelle", extra={"display": HELD},
           overrides=[{"predicate": {"lacuna:blowing": 1}, "model": mod("item/starcaller_trumpet_blowing")}])
    g.item_model("starcaller_trumpet_blowing", {"parent": mod("item/starcaller_trumpet"), "display": BLOWING})
    g.shaped("starcaller_trumpet", ["ZCZ", "GHG", " G "],
             {"Z": "lacuna:zenithite_shard", "C": "lacuna:starlace_cloth", "G": "minecraft:gold_ingot", "H": "minecraft:goat_horn"},
             "lacuna:starcaller_trumpet", category="equipment", unlock="minecraft:goat_horn")
    g.tag("item", "minecraft:enchantable/durability", mod("starcaller_trumpet"))
    g.name("item.lacuna.starcaller_trumpet.lore", "Sound it, and a star answers.", "Suonala, e una stella risponde.")
    g.name("item.lacuna.starcaller_trumpet.use", "Use: call a falling star down where you look (up to %s blocks)",
           "Usa: fai cadere una stella dove guardi (fino a %s blocchi)")
    g.name("item.lacuna.starcaller_trumpet.lock", " Aim at a creature: the star locks on and follows it as it falls",
           " Mira a una creatura: la stella la aggancia e la segue mentre cade")
    g.name("item.lacuna.starcaller_trumpet.blast", " Small blast: up to %s damage, knockback, fire; leaves a Molten Star Rock",
           " Piccola esplosione: fino a %s danni, contraccolpo, fuoco; lascia una Roccia stellare fusa")
    g.name("item.lacuna.starcaller_trumpet.crater", " Cracks the ground only when mobGriefing is on",
           " Spacca il terreno solo se mobGriefing è attivo")
    g.name("item.lacuna.starcaller_trumpet.cooldown", "Cooldown: %s s", "Ricarica: %s s")
    g.name("item.lacuna.starcaller_trumpet.repair", "Repair at an anvil with Star Fragments",
           "Si ripara all'incudine con i frammenti di stella")
    g.name("lacuna.starcaller.no_target", "The call finds nothing for a star to fall on.",
           "Il richiamo non trova nulla su cui far cadere una stella.")
    g.name("lacuna.starcaller.too_far", "No star can reach that spot.", "Nessuna stella può raggiungere quel punto.")


def star_rock(g):
    """The three Star Rock stages a falling star leaves (star/StarRock): Molten (placed by the impact) cools to Prime in
    a minute or two, Prime burns out to the Extinguished Block of the Star after about three days. Each stage is its own
    block (own name, loot, Silk Touch item); the old lacuna:fallen_star is aliased to Prime in Java."""
    # Molten: animated, drawn full bright by the block itself (emissiveRendering, like magma)
    g.cube("molten_star_rock", "Molten Star Rock", "Roccia stellare fusa", loot=molten_loot())
    # Prime: the hand-drawn animated star block (tools/art/hand/block/prime_star_rock.png + .mcmeta)
    g.cube("prime_star_rock", "Prime Star Rock", "Roccia stellare viva",
           loot=loot_ore("prime_star_rock", "lacuna:star_fragment", 1, 3, "uniform"))
    g.cube("extinguished_star_rock", "Extinguished Block of the Star", "Blocco spento della stella",
           loot=loot_ore("extinguished_star_rock", "lacuna:star_fragment", 0, 1, "none"))
    for bid in ("molten_star_rock", "prime_star_rock", "extinguished_star_rock"):
        g.tag("block", "minecraft:mineable/pickaxe", mod(bid))
        g.tag("block", "lacuna:star_rocks", mod(bid))
    g.name("item.lacuna.molten_star_rock.tip",
           "Fresh from the sky: burns to stand on and cools to Prime Star Rock in a minute or two, at once in water or "
           "rain. Mined now: only 1-2 Star Fragments, rarely a Solar Flare Fragment.",
           "Appena caduta dal cielo: brucia chi ci sta sopra e si raffredda in Roccia stellare viva in un minuto o due, "
           "subito nell'acqua o sotto la pioggia. Scavata ora: solo 1-2 frammenti di stella, di rado un frammento di "
           "brillamento solare.")
    g.name("item.lacuna.prime_star_rock.tip",
           "The star at its best: mine it with a pickaxe for 1-3 Star Fragments (Fortune helps). It chimes softly, and "
           "burns out after about three days.",
           "La stella al suo meglio: scavala con un piccone per 1-3 frammenti di stella (Fortuna aiuta). Tintinna piano e "
           "si spegne dopo circa tre giorni.")
    g.name("item.lacuna.extinguished_star_rock.tip",
           "A spent star, grey and cratered like the Moon. Mined, it gives at most one Star Fragment.",
           "Una stella esaurita, grigia e piena di crateri come la Luna. Scavata, dà al massimo un frammento di stella.")


def molten_loot():
    """1-2 Star Fragments (no Fortune: still molten, most of it runs off), plus a 3% Solar Flare Fragment."""
    table = loot_ore("molten_star_rock", "lacuna:star_fragment", 1, 2, "none")
    table["pools"].append(pool([{"type": "minecraft:item", "name": "lacuna:solar_flare_fragment"}], conditions=[
        {"condition": "minecraft:inverted", "term": SILK}, {"condition": "minecraft:random_chance", "chance": 0.03},
        SURVIVES]))
    return table


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
