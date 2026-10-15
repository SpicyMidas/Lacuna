# Changelog

## 0.5.0 — revision 5 (player feedback)

- **Wand of Quickening** (palette swap of the hand-drawn scepter: emerald shaft, pink heart gem): for one heart, a
  dropped item (or your off-hand item) grows little legs and fights beside you for 40 s, then turns back into the
  very same item. It keeps its ability: blades slash and sweep (x1.25 damage), bows and crossbows shoot (the
  Heartwood Bow still roots), tridents, snowballs and potions are thrown, TNT charges in and blows up, shields taunt
  and block, food heals you, ender pearls blink, blocks body-slam. Smooth walk cycle, hops, squash and stretch.
- **Ember Triangle** (hand-drawn): a circular thermal wave that instantly pulverizes hostile creatures into dust
  shaped exactly like their bodies, which then peels away on the hot air; huge layered sound and screen shake;
  advancement **Triangle of Instant Pulverization**. 16 uses, 60 s cooldown, repaired with Ember.
- **Forgotten Portal:** 8 craftable frames around a 2x2 opening, laid flat like the End portal; light it with the
  Totem of Banishing (one use). Stand in it to cross to the Forgotten Land (1:12 coordinates) and back; mobs pushed
  in are banished.
- **Eclipser:** its own black and crimson melee slash (and sweep) instead of the white vanilla one.
- **Umbral Steel slashes** are now blue and violet in the steel's own palette.
- **Raw Ruby removed:** ruby ores drop rubies directly.
- **Last Merchant:** the same villager face, now in a very dark grey robe.
- **Chronoshard Pocket Watch:** the animation only plays the smooth, always-forward frames (the strip contained
  repeated and stray frames that made the hands jump back).
- **Village Coin** shown smaller everywhere; **Divine Glass** about three times rarer (fewer clusters, fewer shards).
- **Effects and sounds:** ores and crystals ring and sparkle when mined, coins jingle on pickup, rare items chime,
  ruby blades leave red glints, every special item has a faint aura while held (rift motes on the Totem, sun glints,
  violet wisps, crimson sparks growing with the Eclipser's charge, drips from the Tidecaller...), Starlace armor
  sparkles with tiny stars at night, the Magnifying Glass sizzles where it burns, and a distant bell sometimes tolls
  in the Forgotten Land.

## 0.4.0 — revision 4 (player feedback)

- **Null Lantern fixed:** reading brain memories that goal-based mobs (zombies, skeletons...) do not have threw an
  error that switched the lantern off for the whole session; it now really keeps every hostile mob off you. It can
  be placed like a lantern, standing or hanging (vanilla lantern shape, its own violet texture) and shelters everyone
  within 12 blocks while lit (light 13); its durability is a charge that burns held or placed, runs dark (very dark
  purple, no light) instead of breaking, and is refilled with Ender Pearls (+50 %) or Echo Shards (+100 %), clicked
  onto it in the inventory or used on the placed lantern.
- **Tidecaller Sickle** reworked around the tide: right click sends a rolling **Tidal Wave** that sweeps mobs away,
  douses fire and quenches lava; with 3 Tide Charges, sneak + right click opens a drowning **Maelstrom** that ends
  in a geyser. Melee hits drench (Slowness) and hit harder in water or rain.
- **Shadow Walk** is the effect itself: however it is given (Eclipser or /effect) it switches the player to the
  shadow spectator mode for its duration and back afterwards.
- **Fractured Pearl:** shards go to the pearl's own slot, the nearest free hotbar slot, the off hand, the inventory,
  and only then the ground, with a message saying where.
- **Textures:** V3 hand-drawn textures (clockwork gear, coin, Null Lantern, Pyrrhic Scepter, Mirror of Recursion,
  silk dust, Starlace armor items and layers, Starlace cloth, Tidecaller Sickle, Whisperbone Flute); the Gravemaw
  Greatsword shows its 24x24 icon in inventories, item frames and on the ground and its 32x32 sprite in the hands.

## 0.3.0 — revision 3 (player feedback)

- **Textures:** V2 hand-drawn textures (animated Chronoshard Pocket Watch, Divine Magnifying Glass, Eclipser charge
  states, Echo Dagger, Fractured Pearl and both shards, Heartwood Bow) used verbatim; the placed Arrival Anchor is
  repainted in the pearl's violet and gold.
- **Totem of Banishing:** tolls its own bell on every use; 6 uses in total and no repair, 15 s cooldown after
  banishing a creature or player, 4 s after crossing yourself. The Forgotten Land countdown shows the overworld
  coordinates you would return to (1 block = 12).
- **Item details on demand:** Lacuna item descriptions only appear while the "Item details" key (Space) is held.
- **Divine Magnifying Glass:** the beam always runs straight where you look; it never bends towards mobs.
- **Null Lantern:** works even on mobs already attacking you or that you hit.
- **Umbral Steel** is now stronger than Ruby (netherite-like tier); the sword's *Umbral Rend*, the axe's *Eclipse
  Cleave*, and *Nightforged* faster mining in the dark on every tool. The Chronoshard Pocket Watch is crafted and
  repaired with Umbral Steel; the Echo Dagger with Zenithite.
- **Heartwood Bow:** arrows really grow Grasping Roots around targets and where they land.
- **Mobs:** the spider-like and hound-like custom mobs are gone (the Lantern Moth stays); their drops come from
  spiders, cave spiders, husks and pillagers. The Last Merchant is a plains villager in a blue vest.
- **Fractured Pearl:** the shards always reach you; any that do not fit drop at your feet.
- **Sounds:** new layered cues for the magnifying glass, echo dagger, pyrrhic bolt, null lantern, divine glass,
  tidecaller sickle, whisperbone flute, pearl split, clockwork decoy and the new umbral abilities and roots.

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
