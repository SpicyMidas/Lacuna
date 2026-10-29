"""Totem of Confusion: used with a right click, it conjures five copies of the player (lacuna:confusion_copy) that wear
their skin, mirror their every move on their own headings, break in two hits and fade after about 20 seconds."""
from content.m12_polish import adv, code

def build(g):
    g.item("totem_of_confusion", "Totem of Confusion", "Totem della Confusione")
    # the armour stand is the body, amethyst the illusion, the pearl the swap; gold and a Lacunar Quintessence (m19)
    # make it a totem
    g.shaped("totem_of_confusion", [" P ", "AMA", "GQG"],
             {"P": "minecraft:ender_pearl", "A": "minecraft:amethyst_shard", "M": "minecraft:armor_stand",
              "G": "minecraft:gold_ingot", "Q": "lacuna:lacunar_quintessence"},
             "lacuna:totem_of_confusion", category="equipment", unlock="lacuna:lacunar_quintessence")
    g.name("item.lacuna.totem_of_confusion.lore", "Five of you. Only one is real.", "Cinque di te. Solo uno è vero.")
    g.name("item.lacuna.totem_of_confusion.use", "Use: %s copies of you appear around you",
           "Usa: %s copie di te compaiono intorno a te")
    g.name("item.lacuna.totem_of_confusion.swap", " %s%% chance you swap places with one of them, else you stay in their midst",
           " %s%% di probabilità di scambiarti con una di loro, altrimenti resti in mezzo a loro")
    g.name("item.lacuna.totem_of_confusion.mimic", " They copy your every move: walk, sprint, jump, sneak, swing, each on its own heading",
           " Copiano ogni tuo movimento: camminare, correre, saltare, accovacciarsi, colpire, ognuna nella sua direzione")
    g.name("item.lacuna.totem_of_confusion.copies", " %s hits break a copy; they fade after %s s",
           " %s colpi rompono una copia; svaniscono dopo %s s")
    g.name("item.lacuna.totem_of_confusion.lure", " Hostile mobs hunting you are drawn to them",
           " I mostri ostili che ti danno la caccia ne vengono attirati")
    g.name("item.lacuna.totem_of_confusion.cooldown", "Cooldown: %s s", "Ricarica: %s s")
    g.name("item.lacuna.totem_of_confusion.uses", "Uses left: %s/%s", "Usi rimasti: %s/%s")
    g.name("item.lacuna.totem_of_confusion.tip", "Only you are told whether you swapped.",
           "Solo tu sai se ti sei scambiato di posto.")
    g.name("lacuna.confusion.swap", "You slipped into one of your copies' places.",
           "Hai preso il posto di una delle tue copie.")
    g.name("lacuna.confusion.stay", "Your copies gather around you.", "Le tue copie si radunano intorno a te.")
    g.name("entity.lacuna.confusion_copy", "Confusion Copy", "Copia della Confusione")
    adv(g, "which_one_is_real", "lacuna:totem_of_confusion", code(), "lacunar_quintessence",
        en=("Which One Is Real?", "Hide among copies of yourself with a Totem of Confusion."),
        it=("Qual è quello vero?", "Nasconditi tra le copie di te stesso con un Totem della Confusione."))
