"""M4: Umbral Steel, Eclipser + Shadow Walk, Solar Flare, Sun's Fury."""
from gen_resources import loot_ore, mod
from content import worldgen as wg


def build(g):
    umbral(g)
    eclipser(g)
    solar(g)


def umbral(g):
    g.cube("umbral_steel_ore", "Umbral Steel Ore", "Minerale di acciaio umbrale",
           loot=loot_ore("umbral_steel_ore", "lacuna:raw_umbral_steel"))
    g.cube("deepslate_umbral_steel_ore", "Deepslate Umbral Steel Ore", "Minerale di acciaio umbrale di ardesia profonda",
           loot=loot_ore("deepslate_umbral_steel_ore", "lacuna:raw_umbral_steel"))
    g.cube("umbral_steel_block", "Block of Umbral Steel", "Blocco di acciaio umbrale")
    g.cube("raw_umbral_steel_block", "Block of Raw Umbral Steel", "Blocco di acciaio umbrale grezzo")
    g.item("raw_umbral_steel", "Raw Umbral Steel", "Acciaio umbrale grezzo")
    g.item("umbral_steel_ingot", "Umbral Steel Ingot", "Lingotto di acciaio umbrale")
    g.item("umbral_steel_nugget", "Umbral Steel Nugget", "Pepita di acciaio umbrale")

    g.storage("lacuna:umbral_steel_block", "lacuna:umbral_steel_ingot")
    g.storage("lacuna:raw_umbral_steel_block", "lacuna:raw_umbral_steel")
    g.shaped("umbral_steel_ingot_from_nuggets", ["###", "###", "###"], {"#": "lacuna:umbral_steel_nugget"},
             "lacuna:umbral_steel_ingot", category="misc")
    g.shapeless("umbral_steel_nugget", ["lacuna:umbral_steel_ingot"], "lacuna:umbral_steel_nugget", 9)
    g.smelt_and_blast("umbral_steel_ingot", "lacuna:raw_umbral_steel", "lacuna:umbral_steel_ingot", 0.7)
    for ore_id in ("umbral_steel_ore", "deepslate_umbral_steel_ore"):
        g.cooking(f"umbral_steel_ingot_from_smelting_{ore_id}", "smelting", f"lacuna:{ore_id}", "lacuna:umbral_steel_ingot", 0.7, 200)
        g.cooking(f"umbral_steel_ingot_from_blasting_{ore_id}", "blasting", f"lacuna:{ore_id}", "lacuna:umbral_steel_ingot", 0.7, 100)

    for b in ("umbral_steel_ore", "deepslate_umbral_steel_ore", "umbral_steel_block", "raw_umbral_steel_block"):
        g.tag("block", "minecraft:mineable/pickaxe", mod(b))
        g.tag("block", "minecraft:needs_iron_tool", mod(b))
    g.tag("block", "c:ores", "#c:ores/umbral_steel")
    g.tag("block", "c:ores/umbral_steel", mod("umbral_steel_ore"), mod("deepslate_umbral_steel_ore"))
    g.tag("block", "c:ores_in_ground/stone", mod("umbral_steel_ore"))
    g.tag("block", "c:ores_in_ground/deepslate", mod("deepslate_umbral_steel_ore"))
    g.tag("block", "c:ore_rates/singular", mod("umbral_steel_ore"), mod("deepslate_umbral_steel_ore"))
    g.tag("block", "c:storage_blocks", "#c:storage_blocks/umbral_steel", "#c:storage_blocks/raw_umbral_steel")
    g.tag("block", "c:storage_blocks/umbral_steel", mod("umbral_steel_block"))
    g.tag("block", "c:storage_blocks/raw_umbral_steel", mod("raw_umbral_steel_block"))
    g.tag("item", "c:ores", "#c:ores/umbral_steel")
    g.tag("item", "c:ores/umbral_steel", mod("umbral_steel_ore"), mod("deepslate_umbral_steel_ore"))
    g.tag("item", "c:ingots", "#c:ingots/umbral_steel")
    g.tag("item", "c:ingots/umbral_steel", mod("umbral_steel_ingot"))
    g.tag("item", "c:nuggets", "#c:nuggets/umbral_steel")
    g.tag("item", "c:nuggets/umbral_steel", mod("umbral_steel_nugget"))
    g.tag("item", "c:raw_materials", "#c:raw_materials/umbral_steel")
    g.tag("item", "c:raw_materials/umbral_steel", mod("raw_umbral_steel"))
    g.tag("item", "c:storage_blocks", "#c:storage_blocks/umbral_steel", "#c:storage_blocks/raw_umbral_steel")
    g.tag("item", "c:storage_blocks/umbral_steel", mod("umbral_steel_block"))
    g.tag("item", "c:storage_blocks/raw_umbral_steel", mod("raw_umbral_steel_block"))

    g.data("worldgen/configured_feature/ore_umbral_steel", {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 0.0, "size": 6, "targets": [
            {"state": {"Name": "lacuna:umbral_steel_ore"}, "target": {"predicate_type": "minecraft:tag_match",
                                                                       "tag": "minecraft:stone_ore_replaceables"}},
            {"state": {"Name": "lacuna:deepslate_umbral_steel_ore"}, "target": {
                "predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]}})
    g.data("worldgen/placed_feature/ore_umbral_steel", {"feature": "lacuna:ore_umbral_steel", "placement": [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -64},
                                                      "max_inclusive": {"absolute": 0}}},
        {"type": "minecraft:biome"}]})
    g.data("neoforge/biome_modifier/add_ore_umbral_steel", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
                                                            "features": "lacuna:ore_umbral_steel", "step": "underground_ores"})


def eclipser(g):
    overrides = [{"predicate": {"lacuna:charge": t}, "model": f"lacuna:item/eclipser_charge_{i}"}
                 for i, t in ((1, 0.01), (2, 0.34), (3, 0.67))]
    g.item("eclipser", "Eclipser", "Eclissatore", model="handheld", overrides=overrides)
    for i in (1, 2, 3):
        g.item_model(f"eclipser_charge_{i}", {"parent": "minecraft:item/handheld",
                                              "textures": {"layer0": f"lacuna:item/eclipser_charge_{i}"}})
    g.name("item.lacuna.eclipser.lore", "Where it cuts, the light forgets to return.", "Dove taglia, la luce dimentica di tornare.")
    g.name("item.lacuna.eclipser.use", "Use (charge ≥ %s): dash through the dark", "Usa (carica ≥ %s): scatta attraverso il buio")
    g.name("lacuna.eclipser.charge", "Eclipse %s%%", "Eclissi %s%%")
    g.name("lacuna.eclipser.not_enough", "The eclipse is too faint (%s%%)", "L'eclissi è troppo debole (%s%%)")
    g.name("item.lacuna.eclipser.dash", "Dash: %s to %s damage to every foe in its path; the air wake hurls them aside",
           "Scatto: da %s a %s danni a ogni nemico sul percorso; la scia d'aria li scaglia via")
    # the dash applies its own sideways "air wake" shove and must land on every creature exactly once, even one
    # that was just hit by a melee swing (armour still applies)
    g.tag("damage_type", "minecraft:no_knockback", "lacuna:eclipse")
    g.tag("damage_type", "minecraft:bypasses_cooldown", "lacuna:eclipse")
    g.shaped("eclipser", [" U ", "RUR", " S "],
             {"U": "lacuna:umbral_steel_ingot", "R": "lacuna:ruby", "S": "minecraft:stick"},
             "lacuna:eclipser", category="equipment", unlock="lacuna:umbral_steel_ingot")
    g.tag("item", "minecraft:enchantable/durability", mod("eclipser"))
    g.tag("item", "lacuna:drawn_weapons", mod("eclipser"))
    g.name("effect.lacuna.shadow_walk", "Shadow Walk", "Passo d'ombra")


# Hand transforms of the drawn-back Sun's Fury (vanilla applies the trident's SPEAR use animation and raised-arm pose
# on top). Solved for the diagonal 16 px sprite: third person = raised over the throwing shoulder, tip forward and up
# (35 degrees); first person = shaft from the lower corner of the throwing side, tip forward toward the crosshair (the
# client replays the same numbers in client/solar/HandPose to start the light at the spear head). The left-hand
# entries equal the right-hand ones on purpose: ItemTransform mirrors them, so the left hand is a true mirror image.
_SF_THIRD = {"rotation": [-170, -90, 20], "translation": [0.49, -2.34, -4.17], "scale": [1.2, 1.2, 1.2]}
_SF_FIRST = {"rotation": [-175, 58.6, 216.5], "translation": [5.36, 4.45, -9.84], "scale": [0.85, 0.85, 0.85]}
SUNS_FURY_THROWING_DISPLAY = {
    "thirdperson_righthand": _SF_THIRD,
    "thirdperson_lefthand": _SF_THIRD,
    "firstperson_righthand": _SF_FIRST,
    "firstperson_lefthand": _SF_FIRST,
}


def solar(g):
    g.item("solar_flare_fragment", "Solar Flare Fragment", "Frammento di brillamento solare")
    g.item("solar_flare", "Solar Flare", "Brillamento solare")
    # thrown like a trident: while it is drawn back (predicate lacuna:throwing, registered in client/solar) the
    # spear swaps to a model whose hand transforms hold it raised over the shoulder, tip forward
    g.item("suns_fury", "Sun's Fury", "Furia del Sole", model="handheld",
           overrides=[{"predicate": {"lacuna:throwing": 1}, "model": "lacuna:item/suns_fury_throwing"}])
    g.item_model("suns_fury_throwing", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": "lacuna:item/suns_fury"},
        "display": SUNS_FURY_THROWING_DISPLAY,
    })
    g.name("item.lacuna.suns_fury.lore", "A noon that was never allowed to end.", "Un mezzogiorno a cui non fu mai permesso di finire.")
    g.name("item.lacuna.suns_fury.use", "Use: hold to draw back, release to throw. Single use. The sun answers where it lands.",
           "Usa: tieni premuto per caricare, rilascia per lanciare. Monouso. Il sole risponde dove si pianta.")
    g.name("lacuna.suns_fury.fizzles", "The sun cannot reach the Forgotten Land.", "Il sole non raggiunge la Terra Dimenticata.")
    g.name("entity.lacuna.suns_fury", "Sun's Fury", "Furia del Sole")
    g.name("entity.lacuna.solar_ray", "Solar Ray", "Raggio solare")
    g.shaped("solar_flare", [" F ", "FDF", " F "], {"F": "lacuna:solar_flare_fragment", "D": "lacuna:divine_glass"},
             "lacuna:solar_flare", category="misc", unlock="lacuna:solar_flare_fragment")
    g.shaped("suns_fury", ["GFG", "RNR", " S "],
             {"F": "lacuna:solar_flare", "G": "minecraft:gold_block", "R": "lacuna:ruby_block",
              "N": "minecraft:netherite_ingot", "S": "minecraft:stick"},
             "lacuna:suns_fury", category="equipment", unlock="lacuna:solar_flare")
    # 1.5 % per roll in ruin caches (15 / 1000 weight in the rare pool)
    g.shared["ruin_cache_rare"].append(wg.loot_entry("lacuna:solar_flare_fragment", 15))
    g.shared["ruin_cache_rare"][0]["weight"] -= 15
