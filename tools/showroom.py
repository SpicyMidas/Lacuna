# Writes data/lacuna/function/showroom.mcfunction: a walk-through gallery of every Lacuna item, in order.
S = []
def c(x): S.append(x)
STEP = 7
st = [
 ("Totem of", "Banishing", "use / hit a mob", ["totem_of_banishing"], ["zombie"]),
 ("Divine", "Magnifying Glass", "use, daytime", ["divine_magnifying_glass"], ["zombie"]),
 ("Divine Glass", "X-ray window", "look through it", ["divine_glass:8", "divine_glass_pane:8", "divine_glass_shard:8"], []),
 ("Eclipser", "charge + dash", "hit, then use", ["eclipser"], ["zombie_line"]),
 ("Sun's Fury", "throw it", "hold use, release", ["suns_fury", "solar_flare"], ["husk_far"]),
 ("Fractured", "Pearl", "use, throw shards", ["fractured_pearl:2"], []),
 ("Chronoshard", "Pocket Watch", "use", ["chronoshard_pocket_watch", "chronal_shard:16"], []),
 ("Mirror of", "Recursion", "+ mirror glass", ["mirror_of_recursion", "mirror_glass:16"], ["mirror"]),
 ("Null Lantern", "", "hold or place", ["null_lantern"], ["zombie"]),
 ("Whisperbone", "Flute", "use", ["whisperbone_flute"], []),
 ("Starlace", "armor + bandage", "wear; wrap the cow", ["starlace_helmet", "starlace_chestplate", "starlace_leggings", "starlace_boots", "starlace_bandage:8", "minecraft:shears"], ["cow"]),
 ("Gravemaw", "Greatsword", "swing / hold use", ["gravemaw_greatsword"], ["zombie_line"]),
 ("Tidecaller", "Sickle", "use near water", ["tidecaller_sickle"], ["pool"]),
 ("Bow, Dagger,", "Scepter", "shoot / stab", ["heartwood_bow", "minecraft:arrow:64", "echo_dagger", "pyrrhic_scepter"], ["skeleton"]),
 ("Ember", "Triangle", "use near the group", ["ember_triangle"], ["zombie_group"]),
 ("Wand of", "Quickening", "aim at a dropped item", ["quickening_wand", "minecraft:iron_sword", "minecraft:bow", "gravemaw_greatsword"], ["zombie"]),
 ("Comically", "Large Bag", "use on cow / chest", ["comically_large_bag:2"], ["cow", "chest"]),
 ("Starcaller", "Trumpet", "blow at the pig", ["starcaller_trumpet", "star_fragment:8"], ["pig_far"]),
 ("Totem of", "Confusion", "right-click", ["totem_of_confusion"], ["zombie"]),
 ("Chrono", "Hourglass", "feed shards", ["chrono_hourglass", "chronal_shard:32"], ["hourglass"]),
 ("Star Rock", "3 stages", "water cools molten", ["molten_star_rock:4", "prime_star_rock:4", "extinguished_star_rock:4", "minecraft:water_bucket"], ["stars"]),
 ("Exilite", "debris + blocks", "smelt, craft", ["exilite_debris:4", "exilite_shard:8", "exilite_ingot:4", "exilite_block", "exilite_bricks:16", "chiseled_exilite:4"], ["exilite"]),
 ("Forgotten", "Portal", "totem on the frame", ["totem_of_banishing", "forgotten_portal_frame:8"], ["portal"]),
 ("Village", "blocks + GUI", "right-click each", ["village_coin:64", "guild_contract"], ["village"]),
 ("Merchant &", "the rest", "spawn eggs", ["last_merchant_spawn_egg", "ruby:64", "clockwork_decoy:2", "lantern_moth_spawn_egg", "ember:16", "zenithite_shard:16", "silk_dust:16", "repair_kit"], []),
]
N = len(st); L = N * STEP + 4
c("# Lacuna showroom: run in a flat creative world. Walk +X (east) along the hall; each station has a sign and a chest.")
c("gamemode creative @s")
c("gamerule doMobSpawning false"); c("gamerule doDaylightCycle false"); c("gamerule sendCommandFeedback false"); c("time set 6000"); c("weather clear")
c(f"fill ~-2 ~ ~-4 ~{L} ~7 ~9 minecraft:air")
c(f"fill ~-2 ~-1 ~-4 ~{L} ~-1 ~9 minecraft:smooth_stone")
c(f"fill ~-2 ~-1 ~-2 ~{L} ~-1 ~-1 minecraft:polished_andesite")
def item(spec, slot):
    parts = spec.split(":")
    if parts[0] == "minecraft": iid, n = "minecraft:" + parts[1], (parts[2] if len(parts) > 2 else "1")
    else: iid, n = "lacuna:" + parts[0], (parts[1] if len(parts) > 1 else "1")
    return '{Slot:%db,id:"%s",count:%s}' % (slot, iid, n)
def mob(kind, x):
    na = "NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]"
    if kind == "zombie": c(f"summon minecraft:zombie ~{x} ~ ~5 {{{na},Rotation:[180f,0f]}}")
    elif kind == "zombie_line":
        for k in range(4): c(f"summon minecraft:zombie ~{x} ~ ~{3+k*1.5} {{{na},Rotation:[180f,0f]}}")
    elif kind == "zombie_group":
        for dx, dz in ((0, 4), (1.5, 5), (-1.5, 5), (0, 6.5)): c(f"summon minecraft:zombie ~{x+dx} ~ ~{dz} {{{na}}}")
    elif kind == "husk_far": c(f"summon minecraft:husk ~{x} ~ ~8 {{{na}}}")
    elif kind == "skeleton": c(f"summon minecraft:skeleton ~{x} ~ ~7 {{{na}}}")
    elif kind == "cow": c(f"summon minecraft:cow ~{x+1} ~ ~5 {{{na}}}")
    elif kind == "pig_far": c(f"summon minecraft:pig ~{x} ~ ~8 {{{na}}}")
    elif kind == "chest": c(f'setblock ~{x-1} ~ ~5 minecraft:chest{{Items:[{{Slot:0b,id:"minecraft:diamond",count:8}},{{Slot:1b,id:"minecraft:shulker_box",count:1}}]}}')
    elif kind == "mirror": c(f"fill ~{x-2} ~ ~7 ~{x+2} ~3 ~7 lacuna:mirror_glass")
    elif kind == "pool":
        c(f"fill ~{x-3} ~-3 ~2 ~{x+3} ~-1 ~8 minecraft:water")
    elif kind == "hourglass":
        c(f"setblock ~{x} ~1 ~4 lacuna:chrono_hourglass[half=upper]"); c(f"setblock ~{x} ~ ~4 lacuna:chrono_hourglass[half=lower]")
        c(f'setblock ~{x+2} ~ ~4 minecraft:furnace{{Items:[{{Slot:0b,id:"minecraft:raw_iron",count:32}},{{Slot:1b,id:"minecraft:coal",count:16}}]}}')
        c(f"fill ~{x-3} ~-1 ~6 ~{x+3} ~-1 ~7 minecraft:farmland[moisture=7]"); c(f"fill ~{x-3} ~ ~6 ~{x+3} ~ ~7 minecraft:wheat")
        c(f"setblock ~{x} ~-1 ~8 minecraft:water")
    elif kind == "stars":
        c(f"setblock ~{x-2} ~ ~5 lacuna:molten_star_rock"); c(f"setblock ~{x} ~ ~5 lacuna:prime_star_rock"); c(f"setblock ~{x+2} ~ ~5 lacuna:extinguished_star_rock")
    elif kind == "exilite":
        for k, b in enumerate(("exilite_debris", "exilite_block", "exilite", "polished_exilite", "exilite_bricks", "chiseled_exilite")):
            c(f"setblock ~{x-2+(k%3)*2} ~{k//3} ~6 lacuna:{b}")
    elif kind == "portal":
        for dx, dz in ((-1, 3), (0, 3), (1, 4), (1, 5), (0, 6), (-1, 6), (-2, 4), (-2, 5)):
            c(f"setblock ~{x+dx} ~-1 ~{dz} lacuna:forgotten_portal_frame")
        c(f"fill ~{x-1} ~-1 ~4 ~{x} ~-1 ~5 minecraft:air")
    elif kind == "village":
        for k, b in enumerate(("village_ledger", "guild_board", "judge_bench", "council_table", "mint_press", "school_desk")):
            c(f"setblock ~{x-3+(k%3)*3} ~ ~{4+(k//3)*3} lacuna:{b}")
for i, (a, b, how, items, props) in enumerate(st):
    x = 2 + i * STEP
    c(f"# {i+1}. {a} {b}")
    c(f"setblock ~{x} ~-1 ~ minecraft:glowstone")
    c(f"fill ~{x-3} ~-1 ~2 ~{x+3} ~-1 ~2 minecraft:polished_deepslate")
    inv = ",".join(item(s, k) for k, s in enumerate(items))
    c(f"setblock ~{x} ~ ~1 minecraft:chest[facing=north]{{Items:[{inv}]}}")
    msg = lambda t, col="black": "'{\"text\":\"%s\",\"color\":\"%s\"}'" % (t.replace('"', '').replace("'", ''), col)
    c("setblock ~%d ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:[%s,%s,%s,%s]}}" % (
        x, msg(f"{i+1}. {a}", "dark_purple"), msg(b, "dark_purple"), msg(""), msg(how, "dark_gray")))
    for p in props: mob(p, x)
c(f"tp @s ~1 ~ ~-2 -90 15")
c('tellraw @s {"text":"Lacuna showroom ready: walk east; open each chest, read each sign.","color":"light_purple"}')
import os
open(os.path.join(os.path.dirname(__file__), "..", "src/main/resources/data/lacuna/function/showroom.mcfunction"), "w").write("\n".join(S) + "\n")
print(len(S), "lines")
