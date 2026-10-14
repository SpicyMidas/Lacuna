# Changelog

## 0.2.0 — revision 2 (player feedback)

- **Textures:** the hand-drawn textures (ruby set, ores with animation, Umbral Steel set, Divine Magnifying Glass,
  Eclipser charge states, 32x32 Gravemaw Greatsword) are used verbatim (`tools/art/hand/`).
- **Umbral Iron is now Umbral Steel** (all ids, names, recipes, worldgen).
- **Villages instead of Townships:** the Township structures are gone; reputation, laws (curfew instead of gates),
  bell, council, coins, Coiner/Magistrate/Guildmaster, guilds, contracts, schools and the caravan effect now run in
  vanilla villages, which grow a civic square (village hall, mint, guild hall, schoolhouse) or civic houses.
- **Sun's Fury:** held and thrown like a trident; on landing a focus phase brightens the sun, sweeps night into noon
  and concentrates light on the target ring before a shader column of light strikes. **Divine Magnifying Glass:**
  shader beam drawn on every client from the lens.
- **Eclipser:** a real dash with black sword slashes, trail and afterimages; Shadow Walk starts after the dash.
- **Divine Glass:** true X-ray window - through the glass only ores, chests, spawners and mobs float in a void.
- **Gravemaw Greatsword:** larger in the hands, umbral charge-up, visible crescent wave, heavy hits.
- **Fractured Pearl:** the placed anchor is a cube; shards work in every dimension and across dimensions (never into
  or out of the Forgotten Land).
- **Null Lantern:** hostile mobs never target or hurt the holder unless provoked (no close-range exception).
- **Mobs:** the M9 ambush spider and brass hound are gone; spiders and cave spiders now rarely drop Umbral Steel
  nuggets, husks drop Clockwork Gears and rarely Chronal Shards, pillagers drop Clockwork Gears. Spiders and rabbits
  took their place in the Forgotten Land. The Last Merchant looks like a plains villager in a blue vest.
- **Forgotten Land:** much denser fog (client config) with mist; Memory Mural paintings hang in shrines and halls.
- **Effects framework:** shader beams/flares, additive particles, screen shake/flash/tints (Distortion Effects
  slider and a client toggle respected); vortex rifts for Totem crossings, golden rewind ring, blazing Pyrrhic bolt.


## 1.0.0 — first complete build (NeoForge 21.1.x, Minecraft 1.21.1)

- **M0** Project, config, `SafeRun` crash shield, `/lacuna` debug commands, creative tab, Python asset pipeline.
- **M1** Ruby (ore, tools, storage), Divine Glass and clusters, Divine Sight X-ray outlines.
- **M2** The Forgotten Land dimension (12× scale), unmineable ruins with murals, Totem of Banishing, banishment with
  persistent records, relic scattering, "you cannot leave" rules.
- **M3** Divine Magnifying Glass beam, custom damage types, fixed-amount anvil repairs.
- **M4** Umbral Iron, the Eclipser (charge, dash, Shadow Walk), Solar Flare, Sun's Fury.
- **M5** Fractured Pearl with linked Arrival/Departure shards and the Arrival Anchor.
- **M6** Chronoshard Pocket Watch, Mirror of Recursion, Null Lantern, Whisperbone Flute + Echo, Orrery of Thresholds.
- **M7** Starlace armor, Zenithite, Umbral tools, five special weapons.
- **M8** Four wood families (Cindervine, Crystal Willow, Tidebloom, Echo Birch) with trees and worldgen.
- **M9** Lantern Moth, two hostile mobs (removed in 0.2.0), Silk Dust, Clockwork Decoy.
- **M10** Five Forgotten Land biomes, Sunken Bell Tower, Obelisk of Unreadable Names, Mirror Labyrinth + Reflection,
  the Last Merchant.
- **M11** Townships: five walled town styles, reputation, laws, bell and gates, council policies, township coin,
  Coiner/Magistrate/Guildmaster villagers, courthouse fines, guilds and contracts, schools, caravan effect.
- **M12** Advancement tree, curated creative tab, tooltip polish, player README, 30-minute soak script.

Known limitations and fallbacks are listed in `docs/PROGRESS.md`.
