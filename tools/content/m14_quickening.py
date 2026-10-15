"""Wand of Quickening: lends a heart to a dropped item, which grows legs and fights for 40 s (lacuna:animated_item)."""
from gen_resources import mod


def build(g):
    g.item("quickening_wand", "Wand of Quickening", "Bacchetta del Risveglio", model="handheld")
    g.name("item.lacuna.quickening_wand.lore", "Lend a heart, and still things wake.",
           "Presta un cuore, e le cose immobili si destano.")
    g.name("item.lacuna.quickening_wand.use",
           "Use on a dropped item, or with an item in your off hand: pay 2 health to bring it to life for %s s",
           "Usa su un oggetto a terra, o con un oggetto nella mano secondaria: paga 2 salute per dargli vita per %s s")
    g.name("item.lacuna.quickening_wand.use2",
           "It fights for you with its own gifts: blades slash, bows shoot, food feeds you, TNT goes off",
           "Combatte per te con le sue doti: le lame fendono, gli archi scoccano, il cibo ti nutre, il TNT esplode")
    g.name("item.lacuna.quickening_wand.use3", "Then it falls asleep again, just as it was. Up to %s awake at once",
           "Poi si riaddormenta, proprio com'era. Fino a %s svegli alla volta")
    g.name("item.lacuna.quickening_wand.use4", "Sneak + use on your animated item: it falls asleep early",
           "Accovacciato + usa sul tuo oggetto animato: si riaddormenta subito")
    g.name("entity.lacuna.animated_item", "Animated Item", "Oggetto animato")
    g.name("entity.lacuna.animated_item.of", "Animated %s", "%s in vita")
    g.name("lacuna.quickening.no_target", "Aim at a dropped item, or hold one in your off hand.",
           "Mira a un oggetto a terra, o tienine uno nella mano secondaria.")
    g.name("lacuna.quickening.too_weak", "Your heart is too faint to share.", "Il tuo cuore è troppo debole per essere condiviso.")
    g.name("lacuna.quickening.awake", "%s comes to life!", "%s prende vita!")

    g.shaped("quickening_wand", [" E ", "DBD", " B "],
             {"E": "lacuna:echo_fragment", "D": "lacuna:silk_dust", "B": "minecraft:blaze_rod"},
             "lacuna:quickening_wand", category="equipment", unlock="lacuna:echo_fragment")
    g.tag("item", "minecraft:enchantable/durability", mod("quickening_wand"))
    # an animated object never drowns (LivingEntity#canBreatheUnderwater reads this tag)
    g.tag("entity_type", "minecraft:can_breathe_under_water", mod("animated_item"))
