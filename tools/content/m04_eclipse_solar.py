"""M4: Umbral Iron, Eclipser + Shadow Walk, Solar Flare, Sun's Fury."""
from gen_resources import loot_ore, mod
from content import worldgen as wg


def build(g):
    umbral(g)
    eclipser(g)
    solar(g)


def umbral(g):
    g.cube("umbral_iron_ore", "Umbral Iron Ore", "Minerale di ferro umbrale",
           loot=loot_ore("umbral_iron_ore", "lacuna:raw_umbral_iron"))
    g.cube("deepslate_umbral_iron_ore", "Deepslate Umbral Iron Ore", "Minerale di ferro umbrale di ardesia profonda",
           loot=loot_ore("deepslate_umbral_iron_ore", "lacuna:raw_umbral_iron"))
    g.cube("umbral_iron_block", "Block of Umbral Iron", "Blocco di ferro umbrale")
    g.cube("raw_umbral_iron_block", "Block of Raw Umbral Iron", "Blocco di ferro umbrale grezzo")
    g.item("raw_umbral_iron", "Raw Umbral Iron", "Ferro umbrale grezzo")
    g.item("umbral_iron_ingot", "Umbral Iron Ingot", "Lingotto di ferro umbrale")
    g.item("umbral_iron_nugget", "Umbral Iron Nugget", "Pepita di ferro umbrale")

    g.storage("lacuna:umbral_iron_block", "lacuna:umbral_iron_ingot")
    g.storage("lacuna:raw_umbral_iron_block", "lacuna:raw_umbral_iron")
    g.shaped("umbral_iron_ingot_from_nuggets", ["###", "###", "###"], {"#": "lacuna:umbral_iron_nugget"},
             "lacuna:umbral_iron_ingot", category="misc")
    g.shapeless("umbral_iron_nugget", ["lacuna:umbral_iron_ingot"], "lacuna:umbral_iron_nugget", 9)
    g.smelt_and_blast("umbral_iron_ingot", "lacuna:raw_umbral_iron", "lacuna:umbral_iron_ingot", 0.7)
    for ore_id in ("umbral_iron_ore", "deepslate_umbral_iron_ore"):
        g.cooking(f"umbral_iron_ingot_from_smelting_{ore_id}", "smelting", f"lacuna:{ore_id}", "lacuna:umbral_iron_ingot", 0.7, 200)
        g.cooking(f"umbral_iron_ingot_from_blasting_{ore_id}", "blasting", f"lacuna:{ore_id}", "lacuna:umbral_iron_ingot", 0.7, 100)

    for b in ("umbral_iron_ore", "deepslate_umbral_iron_ore", "umbral_iron_block", "raw_umbral_iron_block"):
        g.tag("block", "minecraft:mineable/pickaxe", mod(b))
        g.tag("block", "minecraft:needs_iron_tool", mod(b))
    g.tag("block", "c:ores", "#c:ores/umbral_iron")
    g.tag("block", "c:ores/umbral_iron", mod("umbral_iron_ore"), mod("deepslate_umbral_iron_ore"))
    g.tag("block", "c:ores_in_ground/stone", mod("umbral_iron_ore"))
    g.tag("block", "c:ores_in_ground/deepslate", mod("deepslate_umbral_iron_ore"))
    g.tag("block", "c:ore_rates/singular", mod("umbral_iron_ore"), mod("deepslate_umbral_iron_ore"))
    g.tag("block", "c:storage_blocks", "#c:storage_blocks/umbral_iron", "#c:storage_blocks/raw_umbral_iron")
    g.tag("block", "c:storage_blocks/umbral_iron", mod("umbral_iron_block"))
    g.tag("block", "c:storage_blocks/raw_umbral_iron", mod("raw_umbral_iron_block"))
    g.tag("item", "c:ores", "#c:ores/umbral_iron")
    g.tag("item", "c:ores/umbral_iron", mod("umbral_iron_ore"), mod("deepslate_umbral_iron_ore"))
    g.tag("item", "c:ingots", "#c:ingots/umbral_iron")
    g.tag("item", "c:ingots/umbral_iron", mod("umbral_iron_ingot"))
    g.tag("item", "c:nuggets", "#c:nuggets/umbral_iron")
    g.tag("item", "c:nuggets/umbral_iron", mod("umbral_iron_nugget"))
    g.tag("item", "c:raw_materials", "#c:raw_materials/umbral_iron")
    g.tag("item", "c:raw_materials/umbral_iron", mod("raw_umbral_iron"))
    g.tag("item", "c:storage_blocks", "#c:storage_blocks/umbral_iron", "#c:storage_blocks/raw_umbral_iron")
    g.tag("item", "c:storage_blocks/umbral_iron", mod("umbral_iron_block"))
    g.tag("item", "c:storage_blocks/raw_umbral_iron", mod("raw_umbral_iron_block"))

    g.data("worldgen/configured_feature/ore_umbral_iron", {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 0.0, "size": 6, "targets": [
            {"state": {"Name": "lacuna:umbral_iron_ore"}, "target": {"predicate_type": "minecraft:tag_match",
                                                                       "tag": "minecraft:stone_ore_replaceables"}},
            {"state": {"Name": "lacuna:deepslate_umbral_iron_ore"}, "target": {
                "predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]}})
    g.data("worldgen/placed_feature/ore_umbral_iron", {"feature": "lacuna:ore_umbral_iron", "placement": [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -64},
                                                      "max_inclusive": {"absolute": 0}}},
        {"type": "minecraft:biome"}]})
    g.data("neoforge/biome_modifier/add_ore_umbral_iron", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
                                                            "features": "lacuna:ore_umbral_iron", "step": "underground_ores"})


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
    g.shaped("eclipser", [" U ", "RUR", " S "],
             {"U": "lacuna:umbral_iron_ingot", "R": "lacuna:ruby", "S": "minecraft:stick"},
             "lacuna:eclipser", category="equipment", unlock="lacuna:umbral_iron_ingot")
    g.tag("item", "minecraft:enchantable/durability", mod("eclipser"))
    g.tag("item", "lacuna:drawn_weapons", mod("eclipser"))
    g.name("effect.lacuna.shadow_walk", "Shadow Walk", "Passo d'ombra")


def solar(g):
    g.item("solar_flare_fragment", "Solar Flare Fragment", "Frammento di brillamento solare")
    g.item("solar_flare", "Solar Flare", "Brillamento solare")
    g.item("suns_fury", "Sun's Fury", "Furia del Sole", model="handheld")
    g.name("item.lacuna.suns_fury.lore", "A noon that was never allowed to end.", "Un mezzogiorno a cui non fu mai permesso di finire.")
    g.name("item.lacuna.suns_fury.use", "Use: throw. Single use. Burns everything near the impact.",
           "Usa: lancia. Monouso. Brucia tutto vicino all'impatto.")
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
