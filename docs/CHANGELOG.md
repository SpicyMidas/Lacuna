# Changelog

## 0.7.0 — revision 7 (player feedback)

- **Wand of Quickening:** the spark of life is now a shader projectile: a glowing pink orb with a white-hot heart that
  flies from the wand to the item along a corkscrewing arc, trailing a ribbon of light, and bursts into a ring of light
  and hearts.
- **Divine glass** is much rarer: clusters in 1 of 40 Forgotten Plains chunks (was 18), smaller patches, one shard per
  cluster; fewer shards in chests; the Last Merchant asks 4 rubies a shard.
- **Ruby ore** no longer shines/animates, like every other ore (hand-drawn base frame).
- **Sun's Fury:** during the focus the converging sunbeams start on the sun's disc wherever it is drawn in the sky,
  sunrise and sunset included, take its colour (warm white high up, vanilla sunrise orange near the horizon) and curve
  in through a shrinking ring onto the target; while the sun is below the horizon there are no rays, only the white
  target marker (like the Magnifying Glass at night), and the rays come back as the sun rises.
- **Chrono Hourglass** (new, two blocks tall): a 3D hourglass with see-through glass bulbs and animated sand. Feed it
  Chronal Shards (60 s each, up to 10 min; sneak to pour the whole stack) and everything within 4 blocks runs ×4 faster:
  furnaces, smokers, brewing stands, hoppers, campfires and other ticking blocks, crops and saplings (extra random
  ticks), baby animals and breeding cooldowns. Players are never affected; overlapping hourglasses don't stack.
  A redstone signal pauses it, a comparator reads the time left, and breaking it refunds unspent shards.
- **Comically Large Bag** (new): right-click a creature, minecart, boat or armour stand to put it in the bag, or
  sneak + right-click a block (chests, barrels, shulker boxes, furnaces... keep every item, enchantment and component).
  Use it on a block face to let it out: an entity comes back as the very same entity (same UUID, name, owner,
  inventory, trades), not a copy. Too much nested loot (data over 64 KB or more than 4 filled shulker boxes, bundles or
  full bags inside) is refused: "Too much stuff, the bag isn't so large after all…". Players, bosses, multipart mobs,
  unbreakable and two-block blocks are refused; spawners need an operator in creative. A full bag can't be destroyed
  or lost in the void. Drawn comically large in hand.
- **Fixed: effect graphics vanishing in some worlds** (Tidecaller, Gravemaw Greatsword and every other shader effect).
  One effect throwing an error once switched all effect visuals off for the rest of the session (the attacks still
  worked, invisible); now a failing effect is dropped and logged on its own and nothing else is affected. Under
  Fabulous graphics effects were also hidden behind water, glass and clouds; they now sort correctly.
- **Tidecaller:** the whirlpool and waterspout are rebuilt in Minecraft style: blocky spinning rings textured with the
  animated vanilla water (tinted with the biome's water colour), pixel-art foam and spray, stepped whirlpool terraces,
  and vanilla splash, bubble and drip particles with bubble-column and breeze sounds. Same attack as before.
- **Starcaller Trumpet** (new): blow it like a goat horn and a small falling star streaks down from the sky at an
  angle with a glowing trail and a whistle. It locks on to the creature under your crosshair and follows it, or falls on
  the block you looked at (up to 96 blocks). The impact is a small blast (8 damage at the centre, knockback, burning),
  with a little crater and a few fires only when mobGriefing is on, a screen shake and a flash. It leaves a glowing
  Fallen Star block that breaks into Star Fragments: they repair the trumpet and craft Starlace Cloth (1 fragment + 3
  string = 4 cloth). 32 uses, 30 s cooldown. New advancement "Wish Upon a Star".
- **Villages:** the civic buildings (village hall, mint, guild hall, schoolhouse) are rebuilt in each village style's
  vanilla look: plains cobblestone, oak frames and steep stair roofs; desert smooth sandstone with flat stepped roofs;
  savanna acacia frames with low hipped roofs; taiga cobblestone and spruce with log-edged gables and shutters; snowy
  stripped spruce under snow-covered stepped roofs. Proper corner stairs and valleys on T and cross-gabled plans,
  overhangs, panes, door hoods with torches or lanterns, a lantern cupola on the hall and a smoking chimney on the
  mint. Functional blocks, beds and street connections are unchanged.

## 0.6.0 — revision 6 (player feedback)

- **Slashes like vanilla:** the Eclipser and the Gravemaw Greatsword sweep exactly like a vanilla sword (same rules,
  targets, damage, knockback, sound, and the slash placed one block in front at mid-height for 4 ticks, 8 frames), with
  their own sprites (black and crimson, blue and violet); the big shader crescent is gone.
- **Villages:** the civic buildings have beds, so villagers count them as homes, sleep in them and hide there when
  the bell rings.
- **Last Merchant** only deals in rubies: he pays rubies for ores and ingots and sells his goods for rubies.
- **Sun's Fury:** during the focus the sun and the converging sunbeams glide every frame along the time sweep
  instead of stepping once per tick.
- **Chronoshard Pocket Watch:** plays exactly one turn of the slow hand and loops seamlessly.
- **Forgotten Portal:** the opening is one seamless End-style shader surface (swirling grey-violet fog, drifting
  motes and lavender stars in parallax layers); stepping in crosses instantly, with a full-screen void transition
  that fades in, holds while the land loads and fades out on the other side.
- **Starlace armor:** Constellation Step (double jump with the full set), Starfall (halved fall damage with 2+
  pieces; with the full set a high landing becomes a starlight shockwave instead of damage), Starlit (+5 % speed per
  piece at night under the open sky).
- **Textures and models:** new 16x16 Gravemaw Greatsword icon, Village Coin and Mirror of Recursion textures; the
  Arrival Anchor is the new hand-made 6x5x5 crystal shell; it sits on the floor like a turtle egg, and thrown at a
  wall or ceiling it sticks where it lands, base against that surface; the Sunken Bell stands on the ground.
- **Mirror glass** shows a faint, properly mirrored reflection of you and nearby creatures, with a soft sheen
  (client toggle `mirror.reflections`).

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
