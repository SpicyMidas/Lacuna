"""Comically Large Bag: one creature, cart, boat or block goes in with all of its data and comes back out unchanged."""
from gen_resources import mod

BAG = "comically_large_bag"

# Held comically large: three times the usual size in third person, nearly double in first person (lowered so it
# does not hide the crosshair), a big lump on the ground. GUI and item frames keep the normal icon size.
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 4, 3.5], "scale": [1.5, 1.5, 1.5]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 1.0, 1.13], "scale": [1.25, 1.25, 1.25]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.9, 0.9, 0.9]},
}

LANG = {
    "item.lacuna.comically_large_bag.lore": ("Bigger on the outside. Mostly.", "Più grande fuori. Più o meno."),
    "item.lacuna.comically_large_bag.use": ("Empty: use on a creature, a cart or a boat to stuff it inside",
                                            "Vuoto: usalo su una creatura, un carrello o una barca per infilarcela dentro"),
    "item.lacuna.comically_large_bag.use2": ("Empty: sneak + use on a block to bag it, contents and all",
                                             "Vuoto: accovacciato + usa su un blocco per insaccarlo, contenuto compreso"),
    "item.lacuna.comically_large_bag.use3": ("Full: use on a block face to let it out, exactly as it went in",
                                             "Pieno: usa sulla faccia di un blocco per farlo uscire, esattamente com'era entrato"),
    "item.lacuna.comically_large_bag.tip": ("One thing at a time. It's large, not infinite.",
                                            "Una cosa alla volta. È grande, non infinito."),
    "lacuna.bag.contains": ("Contains: %s", "Contiene: %s"),
    "lacuna.bag.empty": ("Empty, for now", "Vuoto, per ora"),
    "lacuna.bag.named": ("%s '%s'", "%s '%s'"),
    "lacuna.bag.items": ("%s (%s items)", "%s (%s oggetti)"),
    "lacuna.bag.riders": ("%s (+%s riding)", "%s (+%s in sella)"),
    "lacuna.bag.bagged": ("%s is in the bag!", "%s è nel sacco!"),
    "lacuna.bag.too_much": ("Too much stuff, the bag isn't so large after all…",
                            "Troppa roba, il sacco non è poi così grande…"),
    "lacuna.bag.no_players": ("Players don't fit. We checked.", "I giocatori non ci stanno. Abbiamo controllato."),
    "lacuna.bag.too_big": ("Too big even for this bag.", "Troppo grande perfino per questo sacco."),
    "lacuna.bag.wont_fit": ("That doesn't go in a bag.", "Quello non entra in un sacco."),
    "lacuna.bag.not_yours": ("That isn't yours to bag.", "Non spetta a te insaccarlo."),
    "lacuna.bag.ridden": ("Someone is riding it.", "Qualcuno ci sta sopra."),
    "lacuna.bag.wont_budge": ("It won't budge.", "Non si smuove."),
    "lacuna.bag.too_tangled": ("It's stuck to the rest of itself.", "È attaccato al resto di sé stesso."),
    "lacuna.bag.op_only": ("Only an operator in creative mode can bag that.",
                           "Solo un operatore in modalità creativa può insaccarlo."),
    "lacuna.bag.protected": ("You can't do that here.", "Qui non puoi farlo."),
    "lacuna.bag.no_room": ("No room for it there.", "Lì non c'è spazio."),
    "lacuna.bag.wont_stay": ("It wouldn't stay there.", "Lì non resterebbe."),
    "lacuna.bag.already_out": ("It's... already out there?", "È... già là fuori?"),
    "lacuna.bag.lost": ("Whatever is in here doesn't exist anymore.", "Qualunque cosa ci sia qui dentro, non esiste più."),
    "lacuna.bag.nothing": ("There's nothing to bag.", "Non c'è niente da insaccare."),
    "lacuna.bag.slippery": ("It slipped out of the bag.", "È scivolato fuori dal sacco."),
}


def build(g):
    g.item(BAG, "Comically Large Bag", "Sacco Comicamente Grande", extra={"display": DISPLAY},
           overrides=[{"predicate": {"lacuna:full": 0.5}, "model": mod(f"item/{BAG}_full")},
                      {"predicate": {"lacuna:full": 1}, "model": mod(f"item/{BAG}_stuffed")}])
    g.item_model(f"{BAG}_full", {"parent": mod(f"item/{BAG}"), "textures": {"layer0": mod(f"item/{BAG}_full")}})
    # stuffed: a horse-sized creature, or a creature/container holding 9+ stacks (bag/BagCapture#fullness)
    g.item_model(f"{BAG}_stuffed", {"parent": mod(f"item/{BAG}"), "textures": {"layer0": mod(f"item/{BAG}_stuffed")}})
    for key, (en, it) in LANG.items():
        g.name(key, en, it)

    g.shaped(BAG, ["SLS", "LCL", "LLL"], {"S": "minecraft:string", "L": "minecraft:leather", "C": "minecraft:lead"},
             mod(BAG), category="misc", unlock="minecraft:leather")

    # never bagged: bosses (also #c:bosses in code), temporary or bound mod creatures, blocks that must stay unobtainable
    g.tag("entity_type", "lacuna:bag_refuses", "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden",
          "minecraft:elder_guardian", mod("echo"), mod("reflection"), mod("clockwork_decoy"), mod("animated_item"))
    g.tag("block", "lacuna:bag_refuses", "minecraft:budding_amethyst", "minecraft:reinforced_deepslate",
          "minecraft:end_portal_frame", mod("arrival_anchor"))
