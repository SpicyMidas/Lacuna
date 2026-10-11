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
