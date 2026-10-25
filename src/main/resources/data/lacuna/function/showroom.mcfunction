# Lacuna showroom: run in a flat creative world. Walk +X (east) along the hall; each station has a sign and a chest.
gamemode creative @s
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule sendCommandFeedback false
time set 6000
weather clear
fill ~-2 ~ ~-4 ~179 ~7 ~9 minecraft:air
fill ~-2 ~-1 ~-4 ~179 ~-1 ~9 minecraft:smooth_stone
fill ~-2 ~-1 ~-2 ~179 ~-1 ~-1 minecraft:polished_andesite
# 1. Totem of Banishing
setblock ~2 ~-1 ~ minecraft:glowstone
fill ~-1 ~-1 ~2 ~5 ~-1 ~2 minecraft:polished_deepslate
setblock ~2 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:totem_of_banishing",count:1}]}
setblock ~2 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"1. Totem of","color":"dark_purple"}','{"text":"Banishing","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"use / hit a mob","color":"dark_gray"}']}}
summon minecraft:zombie ~2 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
# 2. Divine Magnifying Glass
setblock ~9 ~-1 ~ minecraft:glowstone
fill ~6 ~-1 ~2 ~12 ~-1 ~2 minecraft:polished_deepslate
setblock ~9 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:divine_magnifying_glass",count:1}]}
setblock ~9 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"2. Divine","color":"dark_purple"}','{"text":"Magnifying Glass","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"use, daytime","color":"dark_gray"}']}}
summon minecraft:zombie ~9 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
# 3. Divine Glass X-ray window
setblock ~16 ~-1 ~ minecraft:glowstone
fill ~13 ~-1 ~2 ~19 ~-1 ~2 minecraft:polished_deepslate
setblock ~16 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:divine_glass",count:8},{Slot:1b,id:"lacuna:divine_glass_pane",count:8},{Slot:2b,id:"lacuna:divine_glass_shard",count:8}]}
setblock ~16 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"3. Divine Glass","color":"dark_purple"}','{"text":"X-ray window","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"look through it","color":"dark_gray"}']}}
# 4. Eclipser charge + dash
setblock ~23 ~-1 ~ minecraft:glowstone
fill ~20 ~-1 ~2 ~26 ~-1 ~2 minecraft:polished_deepslate
setblock ~23 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:eclipser",count:1}]}
setblock ~23 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"4. Eclipser","color":"dark_purple"}','{"text":"charge + dash","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"hit, then use","color":"dark_gray"}']}}
summon minecraft:zombie ~23 ~ ~3.0 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
summon minecraft:zombie ~23 ~ ~4.5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
summon minecraft:zombie ~23 ~ ~6.0 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
summon minecraft:zombie ~23 ~ ~7.5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
# 5. Sun's Fury throw it
setblock ~30 ~-1 ~ minecraft:glowstone
fill ~27 ~-1 ~2 ~33 ~-1 ~2 minecraft:polished_deepslate
setblock ~30 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:suns_fury",count:1},{Slot:1b,id:"lacuna:solar_flare",count:1}]}
setblock ~30 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"5. Suns Fury","color":"dark_purple"}','{"text":"throw it","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"hold use, release","color":"dark_gray"}']}}
summon minecraft:husk ~30 ~ ~8 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
# 6. Fractured Pearl
setblock ~37 ~-1 ~ minecraft:glowstone
fill ~34 ~-1 ~2 ~40 ~-1 ~2 minecraft:polished_deepslate
setblock ~37 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:fractured_pearl",count:2}]}
setblock ~37 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"6. Fractured","color":"dark_purple"}','{"text":"Pearl","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"use, throw shards","color":"dark_gray"}']}}
# 7. Chronoshard Pocket Watch
setblock ~44 ~-1 ~ minecraft:glowstone
fill ~41 ~-1 ~2 ~47 ~-1 ~2 minecraft:polished_deepslate
setblock ~44 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:chronoshard_pocket_watch",count:1},{Slot:1b,id:"lacuna:chronal_shard",count:16}]}
setblock ~44 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"7. Chronoshard","color":"dark_purple"}','{"text":"Pocket Watch","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"use","color":"dark_gray"}']}}
# 8. Mirror of Recursion
setblock ~51 ~-1 ~ minecraft:glowstone
fill ~48 ~-1 ~2 ~54 ~-1 ~2 minecraft:polished_deepslate
setblock ~51 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:mirror_of_recursion",count:1},{Slot:1b,id:"lacuna:mirror_glass",count:16}]}
setblock ~51 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"8. Mirror of","color":"dark_purple"}','{"text":"Recursion","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"+ mirror glass","color":"dark_gray"}']}}
fill ~49 ~ ~7 ~53 ~3 ~7 lacuna:mirror_glass
# 9. Null Lantern 
setblock ~58 ~-1 ~ minecraft:glowstone
fill ~55 ~-1 ~2 ~61 ~-1 ~2 minecraft:polished_deepslate
setblock ~58 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:null_lantern",count:1}]}
setblock ~58 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"9. Null Lantern","color":"dark_purple"}','{"text":"","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"hold or place","color":"dark_gray"}']}}
summon minecraft:zombie ~58 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
# 10. Whisperbone Flute
setblock ~65 ~-1 ~ minecraft:glowstone
fill ~62 ~-1 ~2 ~68 ~-1 ~2 minecraft:polished_deepslate
setblock ~65 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:whisperbone_flute",count:1}]}
setblock ~65 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"10. Whisperbone","color":"dark_purple"}','{"text":"Flute","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"use","color":"dark_gray"}']}}
# 11. Starlace armor + bandage
setblock ~72 ~-1 ~ minecraft:glowstone
fill ~69 ~-1 ~2 ~75 ~-1 ~2 minecraft:polished_deepslate
setblock ~72 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:starlace_helmet",count:1},{Slot:1b,id:"lacuna:starlace_chestplate",count:1},{Slot:2b,id:"lacuna:starlace_leggings",count:1},{Slot:3b,id:"lacuna:starlace_boots",count:1},{Slot:4b,id:"lacuna:starlace_bandage",count:8},{Slot:5b,id:"minecraft:shears",count:1}]}
setblock ~72 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"11. Starlace","color":"dark_purple"}','{"text":"armor + bandage","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"wear; wrap the cow","color":"dark_gray"}']}}
summon minecraft:cow ~73 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
# 12. Gravemaw Greatsword
setblock ~79 ~-1 ~ minecraft:glowstone
fill ~76 ~-1 ~2 ~82 ~-1 ~2 minecraft:polished_deepslate
setblock ~79 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:gravemaw_greatsword",count:1}]}
setblock ~79 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"12. Gravemaw","color":"dark_purple"}','{"text":"Greatsword","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"swing / hold use","color":"dark_gray"}']}}
summon minecraft:zombie ~79 ~ ~3.0 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
summon minecraft:zombie ~79 ~ ~4.5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
summon minecraft:zombie ~79 ~ ~6.0 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
summon minecraft:zombie ~79 ~ ~7.5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
# 13. Tidecaller Sickle
setblock ~86 ~-1 ~ minecraft:glowstone
fill ~83 ~-1 ~2 ~89 ~-1 ~2 minecraft:polished_deepslate
setblock ~86 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:tidecaller_sickle",count:1}]}
setblock ~86 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"13. Tidecaller","color":"dark_purple"}','{"text":"Sickle","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"use near water","color":"dark_gray"}']}}
fill ~83 ~-3 ~2 ~89 ~-1 ~8 minecraft:water
# 14. Bow, Dagger, Scepter
setblock ~93 ~-1 ~ minecraft:glowstone
fill ~90 ~-1 ~2 ~96 ~-1 ~2 minecraft:polished_deepslate
setblock ~93 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:heartwood_bow",count:1},{Slot:1b,id:"minecraft:arrow",count:64},{Slot:2b,id:"lacuna:echo_dagger",count:1},{Slot:3b,id:"lacuna:pyrrhic_scepter",count:1}]}
setblock ~93 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"14. Bow, Dagger,","color":"dark_purple"}','{"text":"Scepter","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"shoot / stab","color":"dark_gray"}']}}
summon minecraft:skeleton ~93 ~ ~7 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
# 15. Ember Triangle
setblock ~100 ~-1 ~ minecraft:glowstone
fill ~97 ~-1 ~2 ~103 ~-1 ~2 minecraft:polished_deepslate
setblock ~100 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:ember_triangle",count:1}]}
setblock ~100 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"15. Ember","color":"dark_purple"}','{"text":"Triangle","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"use near the group","color":"dark_gray"}']}}
summon minecraft:zombie ~100 ~ ~4 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
summon minecraft:zombie ~101.5 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
summon minecraft:zombie ~98.5 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
summon minecraft:zombie ~100 ~ ~6.5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
# 16. Wand of Quickening
setblock ~107 ~-1 ~ minecraft:glowstone
fill ~104 ~-1 ~2 ~110 ~-1 ~2 minecraft:polished_deepslate
setblock ~107 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:quickening_wand",count:1},{Slot:1b,id:"minecraft:iron_sword",count:1},{Slot:2b,id:"minecraft:bow",count:1},{Slot:3b,id:"lacuna:gravemaw_greatsword",count:1}]}
setblock ~107 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"16. Wand of","color":"dark_purple"}','{"text":"Quickening","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"aim at a dropped item","color":"dark_gray"}']}}
summon minecraft:zombie ~107 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
# 17. Comically Large Bag
setblock ~114 ~-1 ~ minecraft:glowstone
fill ~111 ~-1 ~2 ~117 ~-1 ~2 minecraft:polished_deepslate
setblock ~114 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:comically_large_bag",count:2}]}
setblock ~114 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"17. Comically","color":"dark_purple"}','{"text":"Large Bag","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"use on cow / chest","color":"dark_gray"}']}}
summon minecraft:cow ~115 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
setblock ~113 ~ ~5 minecraft:chest{Items:[{Slot:0b,id:"minecraft:diamond",count:8},{Slot:1b,id:"minecraft:shulker_box",count:1}]}
# 18. Starcaller Trumpet
setblock ~121 ~-1 ~ minecraft:glowstone
fill ~118 ~-1 ~2 ~124 ~-1 ~2 minecraft:polished_deepslate
setblock ~121 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:starcaller_trumpet",count:1},{Slot:1b,id:"lacuna:star_fragment",count:8}]}
setblock ~121 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"18. Starcaller","color":"dark_purple"}','{"text":"Trumpet","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"blow at the pig","color":"dark_gray"}']}}
summon minecraft:pig ~121 ~ ~8 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}
# 19. Totem of Confusion
setblock ~128 ~-1 ~ minecraft:glowstone
fill ~125 ~-1 ~2 ~131 ~-1 ~2 minecraft:polished_deepslate
setblock ~128 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:totem_of_confusion",count:1}]}
setblock ~128 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"19. Totem of","color":"dark_purple"}','{"text":"Confusion","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"right-click","color":"dark_gray"}']}}
summon minecraft:zombie ~128 ~ ~5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}],Rotation:[180f,0f]}
# 20. Chrono Hourglass
setblock ~135 ~-1 ~ minecraft:glowstone
fill ~132 ~-1 ~2 ~138 ~-1 ~2 minecraft:polished_deepslate
setblock ~135 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:chrono_hourglass",count:1},{Slot:1b,id:"lacuna:chronal_shard",count:32}]}
setblock ~135 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"20. Chrono","color":"dark_purple"}','{"text":"Hourglass","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"feed shards","color":"dark_gray"}']}}
setblock ~135 ~1 ~4 lacuna:chrono_hourglass[half=upper]
setblock ~135 ~ ~4 lacuna:chrono_hourglass[half=lower]
setblock ~137 ~ ~4 minecraft:furnace{Items:[{Slot:0b,id:"minecraft:raw_iron",count:32},{Slot:1b,id:"minecraft:coal",count:16}]}
fill ~132 ~-1 ~6 ~138 ~-1 ~7 minecraft:farmland[moisture=7]
fill ~132 ~ ~6 ~138 ~ ~7 minecraft:wheat
setblock ~135 ~-1 ~8 minecraft:water
# 21. Star Rock 3 stages
setblock ~142 ~-1 ~ minecraft:glowstone
fill ~139 ~-1 ~2 ~145 ~-1 ~2 minecraft:polished_deepslate
setblock ~142 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:molten_star_rock",count:4},{Slot:1b,id:"lacuna:prime_star_rock",count:4},{Slot:2b,id:"lacuna:extinguished_star_rock",count:4},{Slot:3b,id:"minecraft:water_bucket",count:1}]}
setblock ~142 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"21. Star Rock","color":"dark_purple"}','{"text":"3 stages","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"water cools molten","color":"dark_gray"}']}}
setblock ~140 ~ ~5 lacuna:molten_star_rock
setblock ~142 ~ ~5 lacuna:prime_star_rock
setblock ~144 ~ ~5 lacuna:extinguished_star_rock
# 22. Exilite debris + blocks
setblock ~149 ~-1 ~ minecraft:glowstone
fill ~146 ~-1 ~2 ~152 ~-1 ~2 minecraft:polished_deepslate
setblock ~149 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:exilite_debris",count:4},{Slot:1b,id:"lacuna:exilite_shard",count:8},{Slot:2b,id:"lacuna:exilite_ingot",count:4},{Slot:3b,id:"lacuna:exilite_block",count:1},{Slot:4b,id:"lacuna:exilite_bricks",count:16},{Slot:5b,id:"lacuna:chiseled_exilite",count:4}]}
setblock ~149 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"22. Exilite","color":"dark_purple"}','{"text":"debris + blocks","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"smelt, craft","color":"dark_gray"}']}}
setblock ~147 ~0 ~6 lacuna:exilite_debris
setblock ~149 ~0 ~6 lacuna:exilite_block
setblock ~151 ~0 ~6 lacuna:exilite
setblock ~147 ~1 ~6 lacuna:polished_exilite
setblock ~149 ~1 ~6 lacuna:exilite_bricks
setblock ~151 ~1 ~6 lacuna:chiseled_exilite
# 23. Forgotten Portal
setblock ~156 ~-1 ~ minecraft:glowstone
fill ~153 ~-1 ~2 ~159 ~-1 ~2 minecraft:polished_deepslate
setblock ~156 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:totem_of_banishing",count:1},{Slot:1b,id:"lacuna:forgotten_portal_frame",count:8}]}
setblock ~156 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"23. Forgotten","color":"dark_purple"}','{"text":"Portal","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"totem on the frame","color":"dark_gray"}']}}
setblock ~155 ~-1 ~3 lacuna:forgotten_portal_frame
setblock ~156 ~-1 ~3 lacuna:forgotten_portal_frame
setblock ~157 ~-1 ~4 lacuna:forgotten_portal_frame
setblock ~157 ~-1 ~5 lacuna:forgotten_portal_frame
setblock ~156 ~-1 ~6 lacuna:forgotten_portal_frame
setblock ~155 ~-1 ~6 lacuna:forgotten_portal_frame
setblock ~154 ~-1 ~4 lacuna:forgotten_portal_frame
setblock ~154 ~-1 ~5 lacuna:forgotten_portal_frame
fill ~155 ~-1 ~4 ~156 ~-1 ~5 minecraft:air
# 24. Village blocks + GUI
setblock ~163 ~-1 ~ minecraft:glowstone
fill ~160 ~-1 ~2 ~166 ~-1 ~2 minecraft:polished_deepslate
setblock ~163 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:village_coin",count:64},{Slot:1b,id:"lacuna:guild_contract",count:1}]}
setblock ~163 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"24. Village","color":"dark_purple"}','{"text":"blocks + GUI","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"right-click each","color":"dark_gray"}']}}
setblock ~160 ~ ~4 lacuna:village_ledger
setblock ~163 ~ ~4 lacuna:guild_board
setblock ~166 ~ ~4 lacuna:judge_bench
setblock ~160 ~ ~7 lacuna:council_table
setblock ~163 ~ ~7 lacuna:mint_press
setblock ~166 ~ ~7 lacuna:school_desk
# 25. Merchant & the rest
setblock ~170 ~-1 ~ minecraft:glowstone
fill ~167 ~-1 ~2 ~173 ~-1 ~2 minecraft:polished_deepslate
setblock ~170 ~ ~1 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"lacuna:last_merchant_spawn_egg",count:1},{Slot:1b,id:"lacuna:ruby",count:64},{Slot:2b,id:"lacuna:clockwork_decoy",count:2},{Slot:3b,id:"lacuna:lantern_moth_spawn_egg",count:1},{Slot:4b,id:"lacuna:ember",count:16},{Slot:5b,id:"lacuna:zenithite_shard",count:16},{Slot:6b,id:"lacuna:silk_dust",count:16},{Slot:7b,id:"lacuna:repair_kit",count:1}]}
setblock ~170 ~1 ~1 minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"25. Merchant &","color":"dark_purple"}','{"text":"the rest","color":"dark_purple"}','{"text":"","color":"black"}','{"text":"spawn eggs","color":"dark_gray"}']}}
tp @s ~1 ~ ~-2 -90 15
tellraw @s {"text":"Lacuna showroom ready: walk east; open each chest, read each sign.","color":"light_purple"}
