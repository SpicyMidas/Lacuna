"""M6: Chronoshard Pocket Watch, Mirror of Recursion, Null Lantern, Whisperbone Flute + Echo, Orrery."""
from gen_resources import mod
from content import worldgen as wg


def box(frm, to, tex, uv_side=None):
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        faces[d] = {"texture": tex}
    return {"from": frm, "to": to, "faces": faces}


def build(g):
    items(g)
    orrery(g)
    tags(g)


def items(g):
    g.item("chronoshard_pocket_watch", "Chronoshard Pocket Watch", "Orologio da taschino di cronoscheggia")
    g.item("chronal_shard", "Chronal Shard", "Scheggia cronale")
    g.item("mirror_of_recursion", "Mirror of Recursion", "Specchio della ricorsione")
    g.item("null_lantern", "Null Lantern", "Lanterna nulla")
    g.item("whisperbone_flute", "Whisperbone Flute", "Flauto d'ossosussurro")
    g.item("echo_fragment", "Echo Fragment", "Frammento d'eco")
    g.name("entity.lacuna.echo", "Echo", "Eco")
    lore = {
        "chronoshard_pocket_watch": ("It ticks a little behind the world.", "Ticchetta un po' in ritardo sul mondo."),
        "mirror_of_recursion": ("Every reflection remembers the last one.", "Ogni riflesso ricorda il precedente."),
        "null_lantern": ("It shines a light nobody can see by.", "Emana una luce con cui nessuno può vedere."),
        "whisperbone_flute": ("The dead still know the tune.", "I morti conoscono ancora la melodia."),
    }
    for iid, (en, it) in lore.items():
        g.name(f"item.lacuna.{iid}.lore", en, it)
    g.name("item.lacuna.mirror_of_recursion.use", "Hold: reflect projectiles. Sneak + use: release a stored echo.",
           "Tieni premuto: rifletti i proiettili. Accovacciato + usa: rilascia un'eco.")
    g.name("item.lacuna.null_lantern.use", "Held: hostile mobs lose interest in you.",
           "In mano: le creature ostili perdono interesse per te.")
    g.name("item.lacuna.whisperbone_flute.use", "Use: call echoes of the recently fallen.", "Usa: richiama le eco dei caduti recenti.")
    for key, en, it in [
        ("lacuna.tooltip.cooldown", "Cooldown: %s s", "Ricarica: %s s"),
        ("lacuna.watch.snapshots", "Moments held: %s / %s", "Momenti trattenuti: %s / %s"),
        ("lacuna.watch.shards", "Chronal shards: %s / %s", "Schegge cronali: %s / %s"),
        ("lacuna.watch.full", "The watch cannot hold more shards.", "L'orologio non può contenere altre schegge."),
        ("lacuna.watch.inserted", "Chronal shard set (%s / %s).", "Scheggia cronale inserita (%s / %s)."),
        ("lacuna.watch.too_fresh", "No moment is old enough to return to.", "Nessun momento è abbastanza vecchio per tornarci."),
        ("lacuna.watch.other_dimension", "That moment belongs to another world.", "Quel momento appartiene a un altro mondo."),
        ("lacuna.mirror.stored", "Stored echoes: %s / %s", "Eco conservate: %s / %s"),
        ("lacuna.mirror.empty", "The mirror holds no echoes.", "Lo specchio non contiene eco."),
        ("lacuna.mirror.destabilized", "The echo destabilises and shatters.", "L'eco si destabilizza e va in frantumi."),
        ("lacuna.flute.silent", "The bones stay silent.", "Le ossa restano in silenzio."),
    ]:
        g.name(key, en, it)

    g.shaped("chronoshard_pocket_watch", [" R ", "GCG", " D "],
             {"R": "lacuna:ruby", "G": "minecraft:gold_ingot", "C": "minecraft:clock", "D": "lacuna:divine_glass_shard"},
             "lacuna:chronoshard_pocket_watch", category="tools", unlock="minecraft:clock")
    g.shaped("mirror_of_recursion", ["QDQ", "DGD", "QDQ"],
             {"Q": "minecraft:amethyst_shard", "D": "lacuna:divine_glass_shard", "G": "minecraft:gold_ingot"},
             "lacuna:mirror_of_recursion", category="equipment", unlock="lacuna:divine_glass_shard")
    g.shaped("null_lantern", [" U ", "ULU", " D "],
             {"U": "lacuna:umbral_steel_ingot", "L": "minecraft:soul_lantern", "D": "lacuna:divine_glass_shard"},
             "lacuna:null_lantern", category="tools", unlock="lacuna:umbral_steel_ingot")
    g.shaped("whisperbone_flute", [" B ", "BEB", " B "], {"B": "minecraft:bone", "E": "minecraft:echo_shard"},
             "lacuna:whisperbone_flute", category="tools", unlock="minecraft:echo_shard")
    g.shared["ruin_cache_rare"].append(wg.loot_entry("lacuna:chronal_shard", 10))
    g.shared["ruin_cache_rare"][0]["weight"] -= 10


def orrery(g):
    t_base, t_ring, t_core = "#base", "#ring", "#core"
    elements = [
        box([3, 0, 3], [13, 2, 13], t_base),
        box([6, 2, 6], [10, 8, 10], t_base),
        box([2, 8, 7.5], [14, 9, 8.5], t_ring),           # ring along X
        box([7.5, 8, 2], [8.5, 9, 14], t_ring),           # ring along Z
        {"from": [2, 10, 7.5], "to": [14, 11, 8.5], "rotation": {"angle": 45, "axis": "y", "origin": [8, 10, 8]},
         "faces": {d: {"texture": t_ring} for d in ("north", "south", "east", "west", "up", "down")}},
        box([6.5, 9, 6.5], [9.5, 12, 9.5], t_core),
    ]
    g.single("orrery_of_thresholds", "Orrery of Thresholds", "Planetario delle soglie",
             {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
              "textures": {"particle": "lacuna:block/orrery_base", "base": "lacuna:block/orrery_base",
                           "ring": "lacuna:block/orrery_ring", "core": "lacuna:block/orrery_core"},
              "elements": elements})
    g.tag("block", "minecraft:mineable/pickaxe", mod("orrery_of_thresholds"))
    g.shaped("orrery_of_thresholds", ["DKD", "RCR", "SSS"],
             {"D": "lacuna:divine_glass_shard", "K": "minecraft:compass", "R": "lacuna:ruby", "C": "minecraft:clock",
              "S": "minecraft:smooth_stone_slab"}, "lacuna:orrery_of_thresholds", category="decorations", unlock="minecraft:compass")
    for key, en, it in [
        ("lacuna.orrery.coords_ow", "Here %s %s ≈ Forgotten Land %s %s", "Qui %s %s ≈ Terra Dimenticata %s %s"),
        ("lacuna.orrery.coords_fl", "Here %s %s ≈ Overworld %s %s", "Qui %s %s ≈ Mondo %s %s"),
        ("lacuna.orrery.found", "%s — a threshold lies %s, %s blocks away", "%s — una soglia si trova a %s, a %s blocchi"),
        ("lacuna.orrery.nothing", "%s — no threshold within reach", "%s — nessuna soglia a portata"),
        ("lacuna.orrery.busy", "%s — the rings are still turning", "%s — gli anelli stanno ancora girando"),
    ]:
        g.name(key, en, it)
    for d, en, it in [("n", "north", "nord"), ("ne", "north-east", "nord-est"), ("e", "east", "est"), ("se", "south-east", "sud-est"),
                      ("s", "south", "sud"), ("sw", "south-west", "sud-ovest"), ("w", "west", "ovest"), ("nw", "north-west", "nord-ovest")]:
        g.name(f"lacuna.direction.{d}", en, it)


def tags(g):
    g.tag("entity_type", "lacuna:reflectable", "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident",
          "minecraft:fireball", "minecraft:small_fireball", "minecraft:shulker_bullet", "minecraft:llama_spit",
          "minecraft:breeze_wind_charge", "minecraft:wind_charge")
    g.tag("entity_type", "lacuna:unreflectable", "minecraft:ender_pearl", "minecraft:wither_skull", "minecraft:dragon_fireball",
          "minecraft:potion", "minecraft:fishing_bobber", "minecraft:egg", "minecraft:snowball", "minecraft:experience_bottle")
