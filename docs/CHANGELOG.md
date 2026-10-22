# Changelog

## 0.12.0 — revision 12 (player feedback)

- **Gravemaw Greatsword icon** (16x16) is brighter: its shades moved one step up the 32x32 sword's own colour
  ramp, with the same dark outline.
- **Sun's Fury in real 3D, no grey:** the grey/brown haze around the rays is gone (the wide outer glow layers now only
  add light instead of covering the sky). The strike pillar and the converging focus sunbeams are true 3D tubes: each
  pixel traces the view ray through the round shaft, so the beam has exact thickness from any angle and you can stand
  inside it and look straight up. Still depth-correct against clouds and terrain, dimming gently with distance.
- **Item details** now show while holding **Shift** (was Space); rebindable under Controls → Lacuna.
- **Totem of Confusion** works with a plain right click, like the Totem of Banishing (no more raising it like a horn).
<!-- rev12 -->

## 0.11.0 — revision 11 (player feedback)

- **New hand-drawn textures:** the whole Umbral Steel set (raw, ingot, sword, pickaxe, axe, shovel, hoe), the
  Starlace Bandage, Prime Star Rock (animated) and the Extinguished Block of the Star.
- **Ruby tools removed** (sword, pickaxe, axe, shovel, hoe): rubies are for trading with the Last Merchant, ruby
  blocks and the rest stay.
- **Starcaller Trumpet** is held like a bow but lower in the hand and tilted a little more.
- **Exilite is a debris now** (like ancient debris, hand-drawn side/top): blast-proof, diamond pickaxe, drops itself,
  still super rare deep in the Forgotten Land and always hidden in stone. Smelt it into Exilite Shards; 4 shards + 4
  gold = an Exilite Ingot (fireproof, beacon payment). One ingot between two crying obsidian = 2 Forgotten Portal
  Frames. Block of Exilite = 9 ingots (beacon base); the decorative Exilite family is made from 1 shard + 8 calcite.
  Hand-drawn shard and ingot textures.
- **Totem of Banishing:** new hand-drawn texture. Using it blasts everything within 6 blocks away hard (no damage) where
  you leave and again where you land, with a shockwave ring, stone chips, sparks and a heavy boom; a struck target is
  thrown back before it vanishes. The arrival totem pop-up now plays only for totem banishment, not portals or commands.
  Banishment particles use the new Exilite palette.
- **Forgotten Portal:** crossing it is now a plain dimension change, without the full-screen void overlay.
- **Village blocks have real screens** instead of chat messages: the Village Ledger (overview, laws, council, today's
  record), the Guild Board (membership with a Join button, contracts with Request / Turn in), the Judge's Bench
  (standing, fine, Pay fine), the Council Table, the Mint Press (coin value and the Coiner's rates) and the School
  Desk: parchment panels in an oak frame with tabs, scrollable cards, tooltips and your reputation and coins at the
  bottom. Coins are now taken from anywhere in your inventory.

## 0.10.0 — revision 10 (player feedback)

- **Ground marks follow the terrain:** every glyph, reticle, ring, light pool and shockwave drawn on the ground (Sun's
  Fury focus and strike, Gravemaw Greatsword, Ember Triangle, Eclipser, Starcaller, Starlace, rites, ember dust) is
  now laid onto the real top of the blocks underneath: snow layers, slabs, carpets, paths, farmland and each half of a
  stair, wrapping over steps instead of sinking into them or floating, and fading at cliffs. The Divine Magnifying
  Glass also lights a small spot where its beam burns a surface.
- **Sun's Fury ray with real depth:** the ray, the converging sunbeams, the Magnifying Glass beam and their glows are
  drawn after the clouds and tested against the scene's depth (Fast, Fancy and Fabulous): near clouds cover the ray,
  far clouds stay behind it, and it hides behind hills and blocks. The beams are now read as round 3D shafts
  (brighter when seen end-on, soft where they enter the ground, a cloud or surround you) with vanilla fog. Other
  shader effects in Fast/Fancy are also drawn after the clouds so clouds no longer paint over them.


## 0.9.0 — revision 9 (player feedback)

- **Starcaller Trumpet** is held and blown like a bow.
- **Comically Large Bag** is drawn smaller in hand (still big: about twice a normal item in third person).
- **Village Coin** is back to the normal item size everywhere.
- **Totem of Confusion:** you now swap places with a copy 3 times in 4 (was half the time).
- **Eclipser:** 8 attack damage. The dash hits much harder (15.6 at the lowest charge up to 30 at full) and now hits
  every creature along the whole path exactly once, even several lined up one behind another (a swing just before the
  dash no longer swallows the dash damage). Like a fast train pushing the air away, cut enemies are thrown sideways off
  the line with a lift, and creatures just beside the path get shoved by the gust without damage; a V-shaped wake of
  wind lines, puffs and dust follows you, with wind-burst sounds.
- **Totem of Banishing:** when you arrive in the Forgotten Land (totem, portal or command) the totem's activation
  plays on your screen like a totem of undying, with a burst of tumbling bone-and-umber chips and a ring of sparks,
  once the loading screen and the void have lifted. All the End-style particles of banishment and of the Forgotten
  Portal are replaced by two new ones in the totem's colours: tumbling stone chips and pale four-point sparks.
- **Exilite** (new, super rare): an ore deep in the Forgotten Land only (tiny veins, about one per four chunks, near
  the bottom; diamond pickaxe), in the totem's bone, grey, umber and lichen colours, no ruby. Its shards build a
  Forgotten Portal Frame (4 shards around crying obsidian, instead of rubies) and a decorative family: Block of
  Exilite, Polished, Bricks (stairs, slab, wall) and Chiseled Exilite showing the totem's open hand.
- **Wand of Quickening:** the item now comes alive exactly when the spark hits it (until then it stays a glimmering
  dropped item that can't be picked up), and the spark flies about 30% faster. Thrown off-hand items are chased and
  woken on hit; if the item vanishes mid-flight the spark fizzles and the heart is given back.
- **Living items use their powers:** animated items with a special right-click now and then use it on their target,
  after rearing up and glowing: the Gravemaw's crescent wave, the Eclipser's dash (once its own hits charged it),
  the Tidecaller's tidal wave and maelstrom, a Starcaller star, a fleeting Sun's Fury spear, Pyrrhic Scepter bolts,
  a smaller Ember Triangle wave (rarely, only against groups) and the Magnifying Glass sunbeam. Echo Dagger hits echo.
  They wear their durability like a player would; bows still shoot, Heartwood arrows still root.
- **Bag a player** (Comically Large Bag, PvP on): they ride along in the sack for 15 s, invisible, untouchable and
  unable to act, seeing from your right hip through a burlap "inside of the bag" overlay that sways with your walk,
  with muffled sounds and a countdown. Struggling (jump, attack, use, sneak) shortens it. They pop out beside you when
  time is up, when you use the bag on a block, or if either of you leaves, and can't be bagged again for 30 s.
- **Tidecaller water takes the local water colour:** the tidal wave, whirlpool, tornado, geyser, their mist, drips
  and splashes all use the water colour of the biome they're in (swamp green-grey, warm-ocean turquoise...), with the
  same shading as before, dimmed in the dark; the rolling wave re-blends as it crosses biomes.
- **A real tornado:** the maelstrom is rebuilt with its own shader. A dark, lumpy storm cloud gathers first, then the
  funnel ropes down out of it and touches down with a burst of spray and a screen shake. The funnel bends and leans
  with the drifting cloud, a kink travels up it, it has a narrow waist, a foot flaring into the water and a top
  greying into the cloud, a fast dense core inside a torn spray sheath, and pixel streaks and white threads rushing
  upward with foam bands and a bright rim. At its base: a spray cascade, a dark-eyed whirlpool with spiral arms and
  foam flecks, droplets flung off and bits of the ground torn up. At the end it thins into a writhing rope, lifts off
  and the cloud dissolves as the geyser fires; under a low ceiling it shrinks to fit.
- **Star Rock** replaces the Fallen Star lump: a falling star now leaves a full block that cools through three
  stages. **Molten Star Rock** (glowing animated lava-like crust, hot to stand on, smokes and sizzles; water or rain
  cools it at once) drops 1-2 Star Fragments and rarely (3%) a Solar Flare Fragment; after 1-2 minutes it becomes
  **Prime Star Rock** (dark stone with glittering star crystals that glow and twinkle at night, the faint chime) with the
  normal 1-3 fragments (+ Fortune); after about 3 days it goes out as the **Extinguished Block of the Star**, grey
  cratered moon rock, 0-1 fragment. Silk Touch keeps each stage. Old Fallen Stars load as Prime Star Rock.
- **Shadow Possession:** while shadow walking, attack or use a creature or player (within 8 blocks) to possess it:
  you see through its eyes and drive it with your keys; your attack makes it strike (melee, or its own ranged attack:
  creepers ignite, ghasts and blazes shoot). A possessed player's own keys only add a quarter on top of yours, their
  view is pulled to yours and their clicks are blocked. Hosts fight back (players by moving and looking against you,
  clicking, jumping; creatures by will: hostile, big and wounded ones resist harder): a resistance bar fills, then
  you have 4-5 s before you're thrown out of their back. Sneak to leave. The Shadow Walk clock pauses while you're
  inside someone (30 s at most per body). Bosses, other people's pets, mummies, creative players and players without
  PvP can't be possessed. Violet vignette with a heartbeat, a host HUD, crimson cracks when they fight back; onlookers
  see violet wisps at the host's eyes. Config `shadowPossession.enabled` / `players`.


## 0.8.0 — revision 8 (player feedback)

- **New hand-drawn textures:** Chrono Hourglass, Starcaller Trumpet, Totem of Confusion, the Star Fragment (now
  animated, two frames) and the Comically Large Bag in three states.
- **Comically Large Bag:** empty, full, and stuffed. It shows stuffed when it holds a horse-sized creature or
  bigger, or a creature or container carrying 9 or more stacks of items (nested containers count).
- **Chronoshard Pocket Watch, really fixed:** the hand-drawn frames were all correct, but the strip had been exported
  in text order (00, 01... 09, 10, 100, 101... 109, 11, 110...), so every few frames the animation jumped to the wrong
  place. The texture pipeline now detects and plays the true order (all 576 frames, 1 tick each): the fast hand
  steps every frame, the slow hand every 12 frames, the gem fades through its colours, and the loop is seamless.
  No pixel changed.
- **Chrono Hourglass = local tick speed:** while its sand runs, besides furnaces and other block entities (×4),
  crops and baby animals, it now also speeds up every random tick (grass and mycelium spreading, leaves decaying,
  ice and snow melting, cactus, cane, bamboo, vines, kelp, copper aging, budding amethyst, fluids too), weather ticks
  (freezing, snow piling, cauldrons filling), scheduled ticks ×2 (water and lava flow, fire, falling sand, hatching
  eggs), tadpoles, egg-laying and scute timers, and players: hearts regenerate twice as fast (same food cost) and
  harmful effects wear off twice as fast (good effects keep their full time). Redstone timing, pistons, spawners,
  portals and other hourglasses are never affected; overlapping hourglasses never stack.
- **Tidecaller tornado, in the wave's style:** the whirlpool, waterspout and geyser are drawn exactly like the main
  right-click tidal wave (translucent shaded water surfaces in the same deep-teal-to-foam colours, crisp foam crests,
  foot lines and rising glints): a swirling bowl with spiral streams, a swaying funnel that widens upward with
  twisting streams and a curling lip, and a geyser whose crown bursts open and falls back as a curtain.
- **Totem of Confusion** (new): raise it (hold use) and five copies of you appear around you, with your skin, armour,
  held items, cape and name tag. On a coin flip you either swap places with one of them (a copy takes your spot) or
  stay put among them. Each copy walks off on its own heading but mirrors exactly what you do: walking, sprinting,
  jumping, sneaking, swinging, looking. Hostile mobs often switch to a copy. A copy dies in 2 hits and they all fade
  after 20 s. 3 uses, 45 s cooldown. New advancement "Which One Is Real?".
- **Shooting stars (natural):** on clear overworld nights (never the first), now and then a star falls 24-64 blocks
  from a player on open natural ground, away from villages, structures and bases, leaving a Fallen Star. Roughly one
  every 3 nights (config `starfall.naturalChancePerNight`, toggle `starfall.naturalStars`). Nearby players get a hint
  with the direction ("A star falls beyond the hills, to the north-east…"), the whistle plays, and Fallen Stars chime
  faintly so you can find them. New advancement "Shooting Star".
- **Starlace Bandage** (new; 3 Starlace Cloth + 1 string = 3): wrap a mob to mummify it: 1 bandage for peaceful mobs,
  2 for hostile or large ones, 3 for large hostile ones. A mummy is wrapped in glowing starlace bandages, can't move,
  attack or make sounds, takes half damage, doesn't burn in the sun, never despawns and can still be pushed. Shears
  unwrap it exactly as it was (bandages come back half the time). Players, bosses and other people's pets are
  refused. New advancement "It's a Wrap".


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
