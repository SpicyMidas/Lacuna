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
/give @s lacuna:raw_ruby 9
/give @s lacuna:ruby_sword
/give @s lacuna:ruby_pickaxe
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

## M4 — Umbral Iron, Eclipser, Sun's Fury
```
/give @s lacuna:eclipser
/give @s lacuna:umbral_iron_ingot 8
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
/give @s lacuna:orrery_of_thresholds
/summon skeleton ~8 ~ ~
```
- [ ] Watch: carry it 20 s+ (first snapshot is immediate), walk away, wait ≥ 20 s, use it: back where you were,
      reverse-portal particles; tooltip counts moments and shards. Shard in off hand + use: inserts it.
- [ ] Mirror: hold use facing a skeleton: arrows bounce back at it; sneak + use fires a stored echo; the 4th use of
      the same echo shatters.
- [ ] Lantern in off hand at night: zombies 4–16 blocks away ignore you; hit one and it attacks; no new monsters
      spawn close by.
- [ ] Flute: kill 2 cows nearby, play the flute: 2 translucent Echoes appear and fight what you hit; after 10 s
      they fade and drop Echo Fragments. With no recent deaths: "The bones stay silent."
- [ ] Orrery: right click shows coordinates ÷12 and (after M10) the direction to an obelisk / bell tower.

## M7 — Gear
```
/give @s lacuna:starlace_boots
/give @s lacuna:starlace_chestplate
/give @s lacuna:umbral_iron_sword
/give @s lacuna:gravemaw_greatsword
/give @s lacuna:tidecaller_sickle
/give @s lacuna:heartwood_bow
/give @s minecraft:arrow 32
/give @s lacuna:echo_dagger
/give @s lacuna:pyrrhic_scepter
```
- [ ] Starlace armor renders on the player (pale blue weave); jump from 10 blocks and sneak: slow falling, then a
      10 s cooldown on the boots.
- [ ] Umbral sword in a dark cave hits harder (+25 %) than in daylight.
- [ ] Gravemaw: stand still, hold use ≥ 1 s, release: sweep particles in an arc, mobs in front take damage; moving
      cancels.
- [ ] Sickle: right click a mob 10 blocks away: it is yanked toward you; in rain you gain Tide charges (tooltip)
      and your next hit heals 1 heart.
- [ ] Heartwood Bow: the hit mob cannot move for 2 s (4 s if pinned against a wall, with particles).
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
/give @s lacuna:gloomcrawler_spawn_egg 2
/give @s lacuna:clockwork_hound_spawn_egg 2
/give @s lacuna:clockwork_decoy 4
/give @s lacuna:silk_dust 16
/give @s minecraft:torch 16
/lacuna scent
```
- [ ] Moths glow, flutter like small pale bees, never sting, and gather around a torch held in hand or a placed
      lantern; a Null Lantern in hand does not attract them. Killing one drops 0–2 Silk Dust.
- [ ] Silk Dust on the floor, walls and ceiling leaves a faint sparkling light-9 decal; walk through it; it
      disappears after 3 minutes (try `/tick sprint 3600`), also after leaving and re-entering the world.
- [ ] A Gloomcrawler next to leaves/tall grass flattens and turns almost invisible; walking past it (not sneaking)
      within 4 blocks makes it leap; sneaking past does not. Holding a torch within 6 blocks makes it flee to darkness.
- [ ] Clockwork Hound: walk 60+ blocks away over ~2 minutes, then `/lacuna scent`; the hound follows the particle
      trail and attacks once within 16 blocks. Jumping into water or sneaking near it makes it give up.
      Bones/meat do nothing to it.
- [ ] Throw a decoy near zombies chasing you: they turn to the mannequin for 15 s; the decoy cannot be damaged by
      its thrower; it vanishes in a puff of smoke afterwards.
