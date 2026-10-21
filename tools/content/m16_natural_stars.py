"""Natural falling stars (star/NaturalStarfall): on clear nights a star now and then falls near a player on its own,
leaving the same molten Star Rock the Starcaller Trumpet calls. Ground tag, damage type, the hint and an advancement."""
from content.m12_polish import adv, code

DIRECTIONS = {
    "north": ("north", "nord"), "north_east": ("north-east", "nord-est"), "east": ("east", "est"),
    "south_east": ("south-east", "sud-est"), "south": ("south", "sud"), "south_west": ("south-west", "sud-ovest"),
    "west": ("west", "ovest"), "north_west": ("north-west", "nord-ovest"),
}


def build(g):
    # natural, open ground a fallen star may rest on (never planks, roofs or paths: those are somebody's)
    g.tag("block", "lacuna:fallen_star_ground", "#minecraft:dirt", "#minecraft:sand", "#minecraft:base_stone_overworld",
          "#minecraft:terracotta", "minecraft:gravel", "minecraft:snow_block", "minecraft:powder_snow", "minecraft:clay",
          "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:calcite", "minecraft:packed_ice")
    g.data("damage_type/shooting_star", {"exhaustion": 0.1, "message_id": "lacuna.shooting_star", "scaling": "never",
                                         "effects": "burning"})
    for tag in ("minecraft:is_explosion", "minecraft:no_knockback"):
        g.tag("damage_type", tag, "lacuna:shooting_star")
    g.name("death.attack.lacuna.shooting_star", "%1$s was struck by a shooting star",
           "%1$s è stato colpito da una stella cadente")
    g.name("death.attack.lacuna.shooting_star.player", "%1$s was struck by a shooting star while fighting %2$s",
           "%1$s è stato colpito da una stella cadente mentre combatteva contro %2$s")
    g.name("lacuna.starfall.natural", "A star falls beyond the hills, to the %s…",
           "Una stella cade oltre le colline, verso %s…")
    for key, (en, it) in DIRECTIONS.items():
        g.name(f"lacuna.starfall.dir.{key}", en, it)
    adv(g, "shooting_star", "lacuna:star_fragment", code(), "root",
        en=("Shooting Star", "See a star fall from the clear night sky on its own."),
        it=("Stella cadente", "Vedi una stella cadere da sola dal cielo notturno sereno."))
