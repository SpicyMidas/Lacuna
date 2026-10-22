"""M1: Ruby, Divine Glass, Divine Sight targets."""
from gen_resources import loot_ore, mod, SILK, pool


def build(g):
    # ---------------------------------------------------------------- ruby blocks
    g.cube("ruby_ore", "Ruby Ore", "Minerale di rubino", loot=loot_ore("ruby_ore", "lacuna:ruby"))
    g.cube("deepslate_ruby_ore", "Deepslate Ruby Ore", "Minerale di rubino di ardesia profonda",
           loot=loot_ore("deepslate_ruby_ore", "lacuna:ruby"))
    g.cube("ruby_block", "Block of Ruby", "Blocco di rubino")

    g.item("ruby", "Ruby", "Rubino")

    # ---------------------------------------------------------------- divine glass
    g.item("divine_glass_shard", "Divine Glass Shard", "Frammento di vetro divino")
    g.cube("divine_glass", "Divine Glass", "Vetro divino", render="minecraft:translucent")
    g.pane("divine_glass_pane", "Divine Glass Pane", "Pannello di vetro divino", "lacuna:block/divine_glass",
           "lacuna:block/divine_glass_pane_top", "minecraft:translucent")
    cluster_loot = {"type": "minecraft:block", "pools": [pool([{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "conditions": [SILK], "name": mod("divine_glass_cluster")},
        {"type": "minecraft:item", "name": mod("divine_glass_shard"), "functions": [
                        {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
             "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}},
            {"function": "minecraft:explosion_decay"}]}]}])],
        "random_sequence": mod("blocks/divine_glass_cluster")}
    g.cross("divine_glass_cluster", "Divine Glass Cluster", "Grappolo di vetro divino", loot=cluster_loot, facing=True)

    # ---------------------------------------------------------------- recipes
    g.storage("lacuna:ruby_block", "lacuna:ruby")
    g.cooking("ruby_from_smelting_ruby_ore", "smelting", "lacuna:ruby_ore", "lacuna:ruby", 1.0, 200)
    g.cooking("ruby_from_blasting_ruby_ore", "blasting", "lacuna:ruby_ore", "lacuna:ruby", 1.0, 100)
    g.cooking("ruby_from_smelting_deepslate_ruby_ore", "smelting", "lacuna:deepslate_ruby_ore", "lacuna:ruby", 1.0, 200)
    g.cooking("ruby_from_blasting_deepslate_ruby_ore", "blasting", "lacuna:deepslate_ruby_ore", "lacuna:ruby", 1.0, 100)
    g.shaped("divine_glass", ["DD", "DD"], {"D": "lacuna:divine_glass_shard"}, "lacuna:divine_glass", category="building")
    g.shaped("divine_glass_pane", ["GGG", "GGG"], {"G": "lacuna:divine_glass"}, "lacuna:divine_glass_pane", 16,
             category="decorations")

    # ---------------------------------------------------------------- tags
    for b in ("ruby_ore", "deepslate_ruby_ore", "ruby_block", "divine_glass_cluster"):
        g.tag("block", "minecraft:mineable/pickaxe", mod(b))
    for b in ("ruby_ore", "deepslate_ruby_ore", "ruby_block"):
        g.tag("block", "minecraft:needs_iron_tool", mod(b))
    g.tag("block", "c:ores", "#c:ores/ruby")
    g.tag("block", "c:ores/ruby", mod("ruby_ore"), mod("deepslate_ruby_ore"))
    g.tag("block", "c:ores_in_ground/stone", mod("ruby_ore"))
    g.tag("block", "c:ores_in_ground/deepslate", mod("deepslate_ruby_ore"))
    g.tag("block", "c:ore_rates/singular", mod("ruby_ore"), mod("deepslate_ruby_ore"))
    g.tag("block", "c:storage_blocks", "#c:storage_blocks/ruby")
    g.tag("block", "c:storage_blocks/ruby", mod("ruby_block"))
    g.tag("block", "minecraft:beacon_base_blocks", mod("ruby_block"))
    g.tag("block", "minecraft:impermeable", mod("divine_glass"))
    g.tag("block", "c:glass_blocks", mod("divine_glass"))
    g.tag("block", "c:glass_panes", mod("divine_glass_pane"))
    g.tag("block", "lacuna:divine_glass", mod("divine_glass"), mod("divine_glass_pane"))
    g.tag("item", "c:ores", "#c:ores/ruby")
    g.tag("item", "c:ores/ruby", mod("ruby_ore"), mod("deepslate_ruby_ore"))
    g.tag("item", "c:gems", "#c:gems/ruby")
    g.tag("item", "c:gems/ruby", mod("ruby"))
    g.tag("item", "c:storage_blocks", "#c:storage_blocks/ruby")
    g.tag("item", "c:storage_blocks/ruby", mod("ruby_block"))
    g.tag("item", "c:glass_blocks", mod("divine_glass"))
    g.tag("item", "c:glass_panes", mod("divine_glass_pane"))
    g.tag("item", "minecraft:beacon_payment_items", mod("ruby"))

    # Divine Sight targets: ores, spawners, containers, buried treasure blocks (suspicious sand/gravel)
    g.tag("block", "lacuna:divine_sight_targets", "#c:ores", "minecraft:spawner", "minecraft:trial_spawner",
          "#c:chests", "#c:barrels", "minecraft:barrel", "minecraft:chest", "minecraft:trapped_chest",
          "minecraft:suspicious_sand", "minecraft:suspicious_gravel", "minecraft:vault")

    # ---------------------------------------------------------------- worldgen: ruby ore
    g.data("worldgen/configured_feature/ore_ruby", {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 0.5, "size": 4, "targets": [
            {"state": {"Name": "lacuna:ruby_ore"}, "target": {"predicate_type": "minecraft:tag_match",
                                                                "tag": "minecraft:stone_ore_replaceables"}},
            {"state": {"Name": "lacuna:deepslate_ruby_ore"}, "target": {"predicate_type": "minecraft:tag_match",
                                                                          "tag": "minecraft:deepslate_ore_replaceables"}}]}})
    g.data("worldgen/placed_feature/ore_ruby", {"feature": "lacuna:ore_ruby", "placement": [
        {"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -64},
                                                      "max_inclusive": {"absolute": 16}}},
        {"type": "minecraft:biome"}]})
    g.data("neoforge/biome_modifier/add_ore_ruby", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
                                                     "features": "lacuna:ore_ruby", "step": "underground_ores"})
