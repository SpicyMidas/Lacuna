# Lacuna — manual client checklist

Run `./gradlew runClient`, create a creative world with cheats, then follow the sections for each milestone.
All `/lacuna` commands need permission level 2.

## M0
- [ ] The mod loads; Mods screen lists **Lacuna 0.1.0**; its config button opens the config screen.
- [ ] Creative inventory has a **Lacuna** tab.
- [ ] `/lacuna selftest` prints `Lacuna selftest PASS (...)`.

## M1 — Ruby, Divine Glass, Divine Sight
```
/give @s lacuna:ruby_ore
/give @s lacuna:deepslate_ruby_ore
/give @s lacuna:ruby 9
/give @s lacuna:divine_glass 16
/give @s lacuna:divine_glass_pane 16
/give @s lacuna:divine_glass_cluster 4
```
- [ ] Ores/blocks/tools render with their textures (no magenta/black checkerboard), tools held diagonally.
- [ ] Mining ruby ore with an iron pickaxe drops a ruby + XP; stone pickaxe drops nothing.
- [ ] A new world has ruby ore below Y 16 (`/fill` a cave or spectate).
- [ ] Divine Glass: transparent, glows (light 7), adjacent glass faces are culled, you can stand inside it
      without suffocating.
- [ ] Divine Sight: place a 1x1 Divine Glass window in a wall, put ores / a chest / a spawner and a mob behind
      the wall within 12 blocks, look through the glass from ≤ 6 blocks: outlines appear through the wall
      (gold ores, white chest, violet spawner, red hostile, green passive) and vanish when you look away.
- [ ] Config `divineSight.enabled=false` disables outlines.

## M2 — Forgotten Land, Totem of Banishing, relics
```
/give @s lacuna:totem_of_banishing
/give @s lacuna:ruby 4
/give @s lacuna:ruin_stone_bricks 16
/give @s lacuna:mural_block_1
/give @s minecraft:painting
/execute in lacuna:forgotten_land run tp @s 0 100 0
/execute in lacuna:forgotten_land run locate structure #lacuna:forgotten_ruins
/lacuna banish @e[type=cow,limit=1,sort=nearest]
/lacuna return @s
```
- [ ] Totem texture: pale stone hand with a ruby in the palm; tooltip shows lore, usage and durability.
- [ ] Right click in the overworld: particles + sound, you arrive in a gray-blue foggy land of gentle hills;
      action bar shows `Forgotten Land — returning in m:ss`. Coordinates ≈ overworld / 12.
- [ ] After the timer (≈ 4.5–9 min) you return at ≈ ×12 of where you stand, with slow falling.
- [ ] Right click again inside: early return. Totem loses 1 durability per transfer and has a 2 min cooldown.
- [ ] Hit a cow with the totem: it is knocked back, then vanishes; it returns when its own timer ends.
- [ ] Log out inside, wait past the timer, log in: you are returned within a second.
- [ ] Nether portal / `/execute` as non-op cannot leave; `/execute in minecraft:overworld run tp` as op works.
- [ ] Beds and respawn anchors inside: message, no explosion.
- [ ] Die inside (keepInventory off): no drops inside; chat says where they were scattered; in the overworld they
      float with the name "Unreadable Relic", glow, cannot burn, only you can pick them up for 10 minutes.
- [ ] Ruins: cannot be mined in survival/adventure, can be broken in creative; memory shrines show mural blocks;
      paintings menu offers the five Memory Murals.
- [ ] Anvil: totem + ruby repairs 25 %; totem + totem gives no result.

## M3 — Divine Magnifying Glass
```
/give @s lacuna:divine_magnifying_glass
/give @s lacuna:divine_glass_shard 8
/time set noon
/summon zombie ~5 ~ ~
```
- [ ] Hold right click: a yellow dust ray up to 24 blocks; zombie takes damage and burns; you walk at normal speed.
- [ ] At night the ray is white and weaker; with your head underwater it is blue, short, with bubbles and no fire.
- [ ] Aim at grass/wood: fire appears next to the face; at snow/ice: it melts; at water: steam puffs + hiss.
- [ ] Durability drops by 1 per second of beam; anvil + shards repairs 60 each; it cannot be enchanted.
- [ ] Inside the Forgotten Land the ray is yellow (counts as day).

## M4 — Umbral Steel, Eclipser, Sun's Fury
```
/give @s lacuna:eclipser
/give @s lacuna:umbral_steel_ingot 8
/give @s lacuna:suns_fury
/give @s lacuna:solar_flare_fragment 4
/summon zombie ~4 ~ ~
/gamerule mobGriefing true
```
- [ ] Eclipser looks like the black thorn blade with the red ribbon guard; hits raise `Eclipse 10%…100%` and the
      texture turns redder (white-hot core at ≥ 67 %); getting hurt resets to 0; idle 8 s decays 2 %/s.
- [ ] Right click at ≥ 20 %: dash through mobs (damage), you become a spectator-like shadow for 2.8–6 s, then
      your mode returns; ending inside a wall moves you out. Log out mid-walk and back in: survival restored.
- [ ] Sun's Fury: throw it in the overworld: time jumps to noon, a golden column burns mobs and carves a crater
      (radius 6) over ~1 s; with `mobGriefing false` only damage. In the Forgotten Land it fizzles and is kept.

## M5 — Fractured Pearl
```
/give @s lacuna:fractured_pearl 2
/give @s minecraft:ender_pearl 4
```
- [ ] Use the pearl: you get a teal Arrival Shard and a violet Departure Shard; tooltips show the same `Linked:` id.
- [ ] Throw the Arrival Shard at a wall: a floating teal crystal appears against the face.
- [ ] Walk away, use the Departure Shard: you appear at the crystal. Give the Departure Shard to a friend: it
      works for them too.
- [ ] Mine the crystal: the Arrival Shard drops (same link). Use the Departure Shard now: "The shard hums, but
      nothing answers.", no cooldown/durability spent, tooltip shows "Cracked".
- [ ] Anchor in the Nether, Departure from the overworld: refused. Inside the Forgotten Land: refused.
- [ ] Restart the server/world: an existing link still works.

## M6 — Utility items
```
/give @s lacuna:chronoshard_pocket_watch
/give @s lacuna:chronal_shard 5
/give @s lacuna:mirror_of_recursion
/give @s lacuna:null_lantern
/give @s lacuna:whisperbone_flute
/give @s lacuna:wayfarers_chart
/summon skeleton ~8 ~ ~
```
- [ ] Watch: carry it 20 s+ (first snapshot is immediate), walk away, wait ≥ 20 s, use it: back where you were,
      reverse-portal particles; tooltip counts moments and shards. Shard in off hand + use: inserts it.
- [ ] Mirror: hold use facing a skeleton: arrows bounce back at it; sneak + use fires a stored echo; the 4th use of
      the same echo shatters.
- [ ] Lantern in off hand at night: zombies, skeletons and spiders ignore you even after you hit them (puff of
      shadow + whisper when one gives up); no new monsters spawn close by.
- [ ] Place it on the ground and under a ceiling (hanging): purple light 13, drifting motes; standing within 12 blocks
      of it, mobs leave you alone. Break it: one lantern drops with the same charge (also from a creeper blast).
- [ ] `/give @s lacuna:null_lantern[damage=299]`: in survival, after ≤ 10 s in hand it fizzles out (dark sprite, action-bar
      message, no ward). Click an Ender Pearl onto it in the inventory: +50 %; use an Echo Shard on a dark placed
      lantern: it relights at 100 %.
- [ ] Flute: kill 2 cows nearby, play the flute: 2 translucent Echoes appear and fight what you hit; after 10 s
      they fade and drop Echo Fragments. With no recent deaths: "The bones stay silent."
- [ ] Wayfarer's Chart in the main hand, off hand empty: held with both hands like a filled map (look down to raise
      it); in the off hand or next to another item: one-handed. It shows the Overworld around you and scrolls smoothly
      as you walk (not in vanilla map squares); the white arrow in the middle turns with you. Loaded land is in full
      map colours; land farther out is sketched faintly by biome (sea, rivers, forest, desert, snow, mountains). The
      gold star (world spawn) and the red bed (your bed) show, pinned smaller to the edge when out of view. The caption
      reads X / Z; the corner shows the scale.
- [ ] Sneak + use: page sound, action bar "Chart scale: 1 pixel = 8 blocks" (cycles 1, 2, 4, 8, 16); the tooltip
      shows the current scale.
- [ ] Forgotten Land: the chart shows the Overworld around your position × 12 (caption "Overworld X · Z" and "Here
      X · Z · ×12", the same Overworld numbers as the banishment countdown) with a violet mark where the banishment
      took you from. Nether: × 8. The End: the chart rests on your bed (or the world spawn) with "No bearing on the
      Overworld from here", no arrow.
- [ ] Two players far apart both holding charts in the Forgotten Land: the server keeps 20 TPS (`/tick query`) and
      no Overworld chunk gets loaded for them (`/forceload query` and the F3 chunk count stay unchanged).

## M7 — Gear
```
/give @s lacuna:starlace_boots
/give @s lacuna:starlace_chestplate
/give @s lacuna:starlace_helmet
/give @s lacuna:starlace_leggings
/give @s lacuna:umbral_steel_sword
/give @s lacuna:gravemaw_greatsword
/give @s lacuna:tidecaller_sickle
/give @s lacuna:heartwood_bow
/give @s minecraft:arrow 32
/give @s lacuna:echo_dagger
/give @s lacuna:pyrrhic_scepter
```
- [ ] Starlace armor renders on the player (pale blue weave); jump from 10 blocks and sneak: slow falling, then a
      10 s cooldown on the boots.
- [ ] Starlace set (survival, all four pieces): jump, release and press jump again in mid-air: a second leap with a
      chime and a small constellation of stars left in the air; only once until you land; nothing in water, on a
      ladder, while riding or flying. Drop 8+ blocks near zombies: no damage, a pale gold/lavender ring races out and
      the zombies are hurt and pushed back. With 2-3 pieces the same fall only hurts half as much. At night under the
      open sky (or in the End) you walk faster (+5 % per piece); under a roof the bonus goes away within a second.
      Tooltips: "Set bonus (n/4)" lines light up for the pieces you are wearing (hold the details key).
- [ ] Umbral sword in a dark cave hits harder (+25 %) than in daylight.
- [ ] Gravemaw: stand still, hold use ≥ 1 s, release: sweep particles in an arc, mobs in front take damage; moving
      cancels.
- [ ] Sickle: right click: a blue-green wave with a foam crest rolls ~14 blocks ahead over the ground, carries mobs
      along (4 damage each), puts out fire and turns the lava it crosses into obsidian/cobblestone; it breaks against
      tall walls. Hits in rain/water or standing in water fill Tide charges (action bar, tooltip).
- [ ] Sickle: with 3 charges, sneak + right click a spot: a whirlpool drags mobs in and drowns them for 5 s, then a
      geyser throws them high into the air. Melee hits slow the target; in rain they hit harder.
- [ ] Heartwood Bow: the hit mob cannot move for 2 s (4 s if pinned against a wall, with particles).
- [ ] Heartwood Bow: grasping roots burst around the hit mob's feet (6 s) and sprout a small patch where arrows hit the ground (10 s); mobs walking in are held; the roots wither and crumble, dropping nothing.
- [ ] Umbral Steel Sword: every 3rd hit within 4 s detonates (black slash burst, +6 damage, heal 2, Slowness + Darkness). Axe: fully charged hits knock back and damage mobs around the target with a dark ring. Umbral tools mine 30% faster in darkness (light <= 7).
- [ ] Echo Dagger: each hit is followed by a second, weaker hit half a second later.
- [ ] Scepter: costs 1 heart per shot, the flame bolt curves toward hostiles; refused at ≤ 2 hearts.
- [ ] Enchanting table/anvil: only Unbreaking applies to the special weapons.

## M8 — Wood families
```
/give @s lacuna:echo_birch_sapling 2
/give @s lacuna:cindervine_log 8
/give @s lacuna:crystal_willow_sapling
/give @s lacuna:tidebloom_sapling
/give @s minecraft:bone_meal 32
/locate biome minecraft:birch_forest
```
- [ ] Every family block (log/wood/planks/stairs/slab/fence/gate/door/trapdoor/plate/button/leaves/sapling/pot)
      renders and drops itself; doors/trapdoors open with the wood sounds; buttons and plates emit redstone.
- [ ] Axe right click strips logs and wood of all four families.
- [ ] Bone meal grows each sapling: Cindervine (gnarled, glowing ember bark, smouldering leaves), Crystal Willow
      (glassy leaves with hanging strands), Tidebloom (mangrove roots, splash on break), Echo Birch (chime sounds).
- [ ] Two Echo Birch saplings 3 blocks apart grow together and get a bridge of leaves.
- [ ] Cindervine logs do not burn in fire or lava; planks smelt 2 items in a furnace (400 ticks); Embers burn long.
- [ ] Leaves decay after logs are removed; placed leaves do not.
## M9 — Mobs
```
/give @s lacuna:lantern_moth_spawn_egg 4
/give @s lacuna:clockwork_decoy 4
/give @s lacuna:silk_dust 16
/give @s minecraft:torch 16
```
- [ ] Moths glow, flutter like small pale bees, never sting, and gather around a torch held in hand or a placed
      lantern; a Null Lantern in hand does not attract them. Killing one drops 0–2 Silk Dust.
- [ ] Silk Dust on the floor, walls and ceiling leaves a faint sparkling light-9 decal; walk through it; it
      disappears after 3 minutes (try `/tick sprint 3600`), also after leaving and re-entering the world.
- [ ] Kill spiders/cave spiders, husks and pillagers as a player: about 1 in 20 spiders drops an Umbral Steel
      nugget, husks drop 0–2 Clockwork Gears (rarely a Chronal Shard), pillagers 0–1 Clockwork Gear.
- [ ] Throw a decoy near zombies chasing you: they turn to the mannequin for 15 s; the decoy cannot be damaged by
      its thrower; it vanishes in a puff of smoke afterwards.
## M10a — Forgotten Land biomes
```
/execute in lacuna:forgotten_land run tp @s 0 100 0
/locate biome lacuna:cinderfrost_flats
/locate biome lacuna:inverted_falls
/locate biome lacuna:whispering_marsh
/locate biome lacuna:clockwork_dunes
/give @s lacuna:updraft_spring 4
/give @s lacuna:spore_mushroom 8
```
- [ ] Cinderfrost Flats: gray ashen snow shows dark footprints that fade over ~5 s; ponds are capped with almost
      invisible ice; ash drifts in the air; no monsters spawn.
- [ ] Inverted Falls: stone islands float at Y 110–150 with zenithite ore, glass clusters and willows; standing in
      the pool's spring lifts you through the islet springs to the island's edge; place a spring yourself: items and
      mobs rise too; a block above the spring cuts the column.
- [ ] Whispering Marsh: fog closes in, far sounds are muffled; spore mushrooms glow and make nearby mobs (and you)
      glow.
- [ ] Clockwork Dunes: buried watches (drop chronal shards) and rusted gears on sand; rabbits; wheat
      planted here grows visibly slower than in the Overworld; a faint ticking ambience.
## M10b — Sunken Bell Tower, Obelisk
```
/locate structure lacuna:sunken_bell_tower
/locate structure lacuna:obelisk
/place structure lacuna:sunken_bell_tower ~ ~ ~20
/place structure lacuna:obelisk ~20 ~ ~
```
- [ ] The bell tower stands on the ocean floor, water-filled below sea level, ladder up to the bell chamber; the base
      chest has loot.
- [ ] Ringing the bell tolls, draws rings and outlines nearby ores in gold for 8 s; ringing again within 60 s shows
      the cooldown.
- [ ] Obelisk inscriptions print a lore line (always the same line for the same block); occasionally (12 %) a small
      reward drops, never twice for the same player and obelisk; inscriptions cannot be mined in survival.
## M10c — Mirror Labyrinth
```
/execute in lacuna:forgotten_land run tp @s 0 100 0
/locate structure lacuna:mirror_labyrinth
/place structure lacuna:mirror_labyrinth ~ ~ ~
```
- [ ] The maze is walled and roofed with see-through mirrors that cannot be mined in survival; the entrance is on
      the south side, a glowing cracked seal on the north side.
- [ ] Stepping on the ring in the centre makes a silvery Reflection appear; after 3 s it walks exactly where you
      walked, through walls, and hurts on contact (6).
- [ ] Killing it shatters the seals: the north door and the hoard alcove open; the chest has the Mirror Hoard loot
      and always one relic; the Reflection drops a Lacunar Quintessence (only when a player kills it).
- [ ] Walking 48+ blocks away (or logging out) makes the Reflection vanish; the ring can be triggered again.
## M10d — Last Merchant
```
/give @s lacuna:last_merchant_spawn_egg
/give @s minecraft:emerald 64
/give @s minecraft:raw_iron 64
/gamerule doTraderSpawning true
```
- [ ] Mine some ores (e.g. copper), then spawn a merchant nearby: his first buy offer is that ore family plus a bulk
      offer, paid in rubies; he sells divine glass shards, chronal shards, echo shards... for rubies only.
- [ ] Every merchant offers two relics (one of each, rubies; Eclipser, Gravemaw and Ember Triangle 64 rubies + 2 ruby
      blocks) and the Sun's Fury forge (Solar Flare + 32 rubies); re-spawned merchants bring other relics.
- [ ] Re-spawn several merchants: about 1 in 12 offers a Solar Flare Fragment for 16 rubies + 8 divine glass shards,
      about 1 in 5 a Lacunar Quintessence for 64 rubies + 4 ruby blocks.
- [ ] Leaving the area (chunk unload) or restarting removes the merchant; he never comes back from a save.
- [ ] The merchant looks like a normal plains villager wearing a blue vest.
## M11a — Township structures
```
/locate structure #lacuna:townships
/place structure lacuna:township_plains
/place structure lacuna:township_desert
/place structure lacuna:township_savanna
/place structure lacuna:township_taiga
/place structure lacuna:township_snowy
```
- [ ] Walls with four towers and four open gates; streets lead to a plaza with fountain + aqueduct, statue with a
      readable plaque, bell tower, ledger podium.
- [ ] Four civic halls (council table, judge's bench, guild board + banners, library with desks) and eight lot
      buildings (market, workshops with job blocks, houses with beds, granary/orchard/garden/stables/tavern/school).
- [ ] Roads leave each gate, follow the terrain and have farms on their sides; villagers and two iron golems live
      there; no vanilla village overlaps a township.
- [ ] Optional: run `tools/smoke_townships.txt` (20 placements) and check the log is clean.
## M11b — Reputation, laws, bell, gates
```
/place structure lacuna:township_plains
/lacuna township info
/lacuna rep get @s
/lacuna rep set @s 60
/time set 13000
/give @s minecraft:iron_sword
```
- [ ] Right click the ledger: reputation, tier, two laws, policy and coin value are listed.
- [ ] Trading, killing zombies near the plaza, hitting a villager, breaking wall blocks, harvesting ripe wheat and
      opening a chest next to a villager change reputation with action-bar feedback and daily caps.
- [ ] With "no drawn weapons": holding the sword inside the walls warns for 3 s, then −1 every 10 s and a golem walks
      over. With "gates close at dusk": at night strangers get the same treatment.
- [ ] Gates are open by day, closed at night; with reputation 60 they open when you approach; they cannot be mined
      unless sneaking with an axe.
- [ ] Ring the bell at dawn (+2 once), four times (−5), outside dawn with law 4 (−3).
- [ ] At −60 reputation the iron golems attack; at +60 villager prices drop.
## M11c — Council, currency, courthouse
```
/give @s lacuna:township_coin 32
/give @s lacuna:mint_press
/give @s minecraft:villager_spawn_egg
/lacuna rep set @s -30
/time add 168000
```
- [ ] A villager next to a mint press becomes a Coiner (buys raw ores/ingots for coins); next to a judge's bench a
      Magistrate.
- [ ] Selling many raw ores lowers the township's coin value (ledger summary); it recovers by 0.01 per day.
- [ ] Paying coins at the judge's bench raises reputation by 5 per coin up to 0; a second pardon within 3 days is
      refused; empty hand shows the fine.
- [ ] After 7+ days at dawn the council announces a policy; Festival shows fireworks on the plaza, Martial Law guards
      hit harder, Free Market/Austerity change villager prices.
- [ ] The Last Merchant now trades in township coins.
## M11d — Guilds, contracts, schools, caravan
```
/lacuna rep set @s 20
/give @s lacuna:township_coin 64
/give @s minecraft:paper 64
/give @s lacuna:repair_kit
/give @s lacuna:last_merchant_spawn_egg
```
- [ ] Right click a guild board with 20 coins: you join that guild (a second guild is refused); its Guildmaster now
      trades with you, other Guildmasters say "Guild members only".
- [ ] Empty-hand right click on your board gives one contract (tooltip: what to deliver, reward); delivering it pays
      coins and +3 reputation; a second contract is refused while one is active.
- [ ] A repair kit used with a damaged tool in the other hand restores a quarter of its durability.
- [ ] Villagers working near the library desks gain XP each dawn and level up to 3 by trading.
- [ ] Kill a Last Merchant near a township: the ledger's price index rises by 0.2 and decays daily.
## M12 — Polish
- [ ] Creative tab: relics first, then weapons/armour, materials, township/creature items, blocks.
- [ ] Hover silk dust, township coin, updraft spring…: a grey italic hint line.
- [ ] The "Lacuna" advancement tab unlocks in order (ruby, Forgotten Land, coming back, Eclipser, Sun's Fury, pearl,
      Township).
- [ ] Switch the game to Italian: every Lacuna text is translated.
- [ ] Dedicated server: `java -Dlacuna.smoke=tools/smoke_soak.txt ...` runs for 30 minutes without errors.

## 1.0.0-beta — progression (docs/PROGRESSION.md)
```
/give @s lacuna:lacunar_quintessence 3
/give @s minecraft:totem_of_undying
/give @s lacuna:repair_kit 2
/loot give @s loot minecraft:chests/ancient_city
/loot give @s loot lacuna:chests/sunken_bell_tower
/place structure lacuna:sunken_bell_tower ~ ~ ~20
```
- [ ] The Quintessence: magenta (epic) name, glint, animated violet drop with a dark gap; details show lore, its use
      and where it is found. Dropped into lava or next to a creeper blast it survives.
- [ ] Recipe book: the Totems of Banishing and Confusion and the vanilla Totem of Undying unlock with the essence and
      need it; no recipe exists any more for the relics (Eclipser, Sun's Fury, magnifying glass, watch, mirror, lantern,
      flute, the five special weapons, Ember Triangle, Wand of Quickening, Starcaller Trumpet).
- [ ] Umbral Steel tools need a ruby in the grip; the bag an ender pearl; the hourglass two chronal shards; repair
      kits craft from 2 umbral ingots, a clockwork gear, 2 leather and an iron ingot.
- [ ] A repair kit used with a damaged Totem of Banishing in the other hand refuses it (action bar message).
- [ ] Each relic's details tooltip lists its sources. `/loot` on the vanilla chests above now and then gives a relic.
- [ ] On clear nights a natural falling star sometimes leaves a glowing Quintessence at its crater; mining a Prime
      Star Rock (without Silk Touch) very rarely gives one.
- [ ] Advancement tab: "The Shape of the Gap" (obtain a Quintessence) leads to "A Land Nobody Remembers"; "Blade of
      the Eclipse" asks to claim the Eclipser.
