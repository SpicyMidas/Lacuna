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
