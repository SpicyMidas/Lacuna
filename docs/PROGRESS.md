# Lacuna — progress log

Format per milestone: what was built, deviations from LACUNA_SPEC.md, known limitations, gate status.

## Build environment note (applies to every milestone below)

This code was written in a sandbox whose network policy **denies `maven.neoforged.net` and every Mojang host**
(`piston-meta`/`piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`) and
throttles `repo.maven.apache.org`. ModDevGradle therefore cannot download NeoForge or Minecraft, so the
gates that need Minecraft (**1 build, 2 runData, 5 GameTests, 6 smoke**) could **not** be executed here.
APIs were checked against the NeoForge 1.21.1 sources (github.com/neoforged/NeoForge, branch `1.21.1`,
including its patches and tests) and vanilla 1.21.1 data from misode/mcmeta, not against a compiled jar.

Gates that **were** run for every milestone:
- gate 3 `python3 tools/validate_assets.py`
- gate 4 for `com.lacuna.mod.logic` (pure Java): compiled standalone with `javac` and run with the JUnit 5
  console launcher (the Gradle `test` task needs the NeoForge toolchain to configure).
- determinism of `tools/gen_resources.py`, `tools/gen_textures.py`, `tools/gen_structures.py` (run twice, no diff).

**First thing to do on a machine with normal internet:** `./gradlew build`, fix any compile errors, then run
`./gradlew runGameTestServer` and `bash tools/smoke.sh`.

## Fallbacks and known limitations (global list)

- Java datagen is replaced by deterministic Python generators (`tools/gen_resources.py`), because they can run
  and be validated without Minecraft. `runData` is still configured; it has no providers and is a no-op.
- Client entry point uses `@Mod(value = "lacuna", dist = Dist.CLIENT)` (equivalent of a client-only
  `@EventBusSubscriber`): listeners are registered explicitly in its constructor.

## M0 — project, tooling, gates, config, SafeRun, commands, creative tab, texture pipeline

Built:
- ModDevGradle project (Gradle wrapper 9.2.1, MDG 2.0.148, NeoForge 21.1.259 = newest in the official 1.21.1
  MDK at setup time), run configs `client`, `server`, `gameTestServer`, `data`; JUnit 5 for pure logic.
- `neoforge.mods.toml` template: depends only on `neoforge` and `minecraft [1.21.1]`, `side = "BOTH"`.
- `LacunaConfig` (COMMON, every feature has `enabled` + numbers, safe getters that fall back to defaults) and
  `Balance` (every fixed number, incl. registration-time stats).
- `SafeRun` (catches Throwable except VirtualMachineError, logs once with stack trace, runs a restore callback,
  disables the feature for the session; reset on ServerStoppedEvent).
- `/lacuna` command tree (permission 2) with `selftest` (coordinate round trips); other subcommands are
  placeholders until their milestone.
- `CoordMath` (+ unit tests, incl. negatives and round-trip error ≤ 11).
- Creative tab "Lacuna".
- Tools: `gen_textures.py` (+ `texlib.py`), `gen_resources.py` (+ `content/`, vanilla blockstate/model
  templates in `tools/templates/`), `gen_structures.py` (nbtlib, deterministic gzip), `validate_assets.py`
  (with a vanilla 1.21.1 id index in `tools/vanilla_1.21.1.json`), `smoke.sh` (+ `SmokeRunner`, activated only
  by `-Dlacuna.smoke=phase1|phase2`), `log_allowlist.txt`.

Deviations: smoke test drives the server through `SmokeRunner` reading `run/lacuna_smoke_<phase>.txt` instead
of piping stdin (Gradle run tasks do not forward stdin reliably).

Gates: 3 ✔, 4 (logic) ✔ 8/8, 1/2/5/6 not runnable here (see note).

## M1 — Ruby, Divine Glass, Divine Sight

Built:
- Blocks `ruby_ore`, `deepslate_ruby_ore` (DropExperienceBlock XP 3–7, hardness 3.0/4.5, blast 3, iron tool),
  `ruby_block`; item `ruby` (raw ruby removed in 0.5.0); Ruby tool set (SimpleTier: diamond level, 800 uses,
  speed 7, +3 damage like diamond, enchantability 15, repair with ruby).
- Ore worldgen (size 4, count 4, trapezoid −64..16, air discard 0.5) through a NeoForge biome modifier on
  `#minecraft:is_overworld`. Recipes: storage ⇄ 9, raw variants, smelt/blast.
- `divine_glass` (TransparentBlock: light 7, hardness 0.5, blast 6, no suffocation/view blocking, translucent,
  culls against itself, always drops itself), `divine_glass_pane` (IronBarsBlock), `divine_glass_cluster`
  (AmethystClusterBlock, 2–4 shards, Fortune +1/level, Silk Touch keeps cluster), `divine_glass_shard`.
- Divine Sight (client, SafeRun): every 10 ticks the view ray looks for Divine Glass within 6 blocks; behind it,
  a 50° cone up to `divineSight.range` is sampled (≤ 4096 positions, loaded chunks only); targets in
  `lacuna:divine_sight_targets` (ores gold, containers white, spawners violet) and living entities (hostile red,
  passive green) are outlined without depth test. Generic `OutlineRenderer`/`OutlineSet` for reuse by the
  Sunken Bell.
- GameTests: ruby ore drop, divine glass light/transparency/self-drop, cluster shard count.
- `/lacuna selftest` now checks registry binding, that every recipe file loaded, coordinates and tag contents.

Deviations:
- Outlines render at `RenderLevelStageEvent.Stage.AFTER_PARTICLES` (after translucent blocks) because the
  render-type stages (incl. AFTER_TRANSLUCENT_BLOCKS) pass a null PoseStack in NeoForge 21.1.
- No animated shimmer `.mcmeta` (optional in spec).

Gates: 3 ✔ (validator also negative-tested with injected faults), 4 (logic) ✔, 1/2/5/6 not runnable here.

## M2 — Forgotten Land, Totem of Banishing, banishment, relics

Built:
- Datapack dimension `lacuna:forgotten_land` (dimension type exactly as spec 9.1, `coordinate_scale` 12) with custom
  noise settings: one low-frequency 2D noise on a Y gradient gives gentle hills with the surface clamped to Y 62–80,
  sea level 63 (small ponds), no aquifers, carvers or ore veins. Multi-noise biome source with
  `lacuna:forgotten_plains` (gray-blue fog/sky/water, ash particles, eerie additions, no spawns, divine glass
  cluster patches). M10 adds the regional biomes.
- Unbreakable ruin family (`ruin_stone`, `ruin_stone_bricks`, `cracked_…`, `ruin_pillar`, `chiseled_ruin_glyph`,
  slab, stairs, wall): destroyTime −1, blast 3,600,000, `PushReaction.BLOCK`, tag `lacuna:ruin_blocks` (also
  dragon/wither immune and `features_cannot_replace`).
- Ruin structures (jigsaw, single piece, `beard_thin`): broken_arch ×3, fallen_colonnade ×2, ruined_hall ×2,
  sunken_plaza, memory_shrine ×2, obelisk_ruin, generated by `tools/gen_structures.py`; sets
  `forgotten_ruins` (spacing 14 / separation 6) and denser `ruin_clusters` (7 / 3). Chests use
  `lacuna:chests/ruin_cache` (later milestones append Solar Flare Fragment / Chronal Shard entries).
- Memory Murals: 5 painting variants (2x2 / 3x2, placeable) with generated warm full-colour city scenes.
- Totem of Banishing (16 durability, 2400-tick cooldown, ruby anvil repair, not enchantable, no item+item merge),
  texture based on the provided hand reference recoloured as weathered stone with a faceted ruby.
- Banishment: `BanishmentSavedData` (overworld data storage) + `banishment_id` attachment; ÷12 / ×12 translation,
  safe-spot search (preferred Y → 16/±24 spiral → surface → spawn, loaded chunks only, target chunk loaded once),
  drift factor in [0.75, 1.5], action-bar countdown, 20-tick checks, offline timers keep running on game time,
  mobs in unloaded chunks reloaded via an expiring chunk ticket (record dropped after 15 misses), arrival slow
  falling + 40 ticks damage immunity, melee hit = 1.5 knockback then banishment 5 ticks later.
  Exclusions: `lacuna:cannot_be_banished`, spectators, players unless PvP + `banishAllowPlayers`.
- Forgotten Land rules: leaving is cancelled (`EntityTravelToDimensionEvent`, ender pearls, chorus fruit) except for
  Lacuna's own transfers and operator commands; beds/anchors are blocked with a message (no explosion).
- Relics: a player death inside scatters the drops in the overworld at the ×12 position (invulnerable, glowing,
  lifespan 30 min, owner-only pickup for 10 min via `ItemEntityPickupEvent.Pre`, floating name
  "Unreadable Relic", chat message with coarse coordinates); falls back to normal drops if the spot is not loaded.
- Commands `/lacuna banish [targets]`, `/lacuna return [targets]`, `/lacuna givekit`; selftest checks the dimension,
  banishment data and tags. GameTests: ruin unbreakability, pig banish/return round trip.

Deviations / interpretations:
- Memory shrine templates use the documented **fallback mural blocks** (`mural_block_1..5`) instead of painting
  entities, because entity placement inside templates could not be verified here; the painting variants still
  exist and are placeable.
- "Operator commands are never blocked" is detected by remembering the server tick of the last permission-2 command
  (`CommandEvent`); a cross-dimension move in that same tick is allowed.
- Ender pearls and chorus fruit are cancelled inside the Forgotten Land (literal reading of 9.1).
- Banished mobs are made persistent so they do not despawn while away.

Gates: 3 ✔ (now also checks dimension/biome/feature/structure references and chest loot tables in NBT),
4 (logic) ✔, 1/2/5/6 not runnable here.

## M3 — Divine Magnifying Glass

Built:
- `divine_magnifying_glass` (500 durability, 1 per 20 beam ticks, use duration 72000, BOW animation, 10-tick
  cooldown on release, repaired at an anvil with Divine Glass Shards +60 each through `AnvilRepairs`, not
  enchantable, item+item merge disabled). Recipe `.G. / GDG / .S.` (removed in 1.0.0-beta: a relic, see
  [PROGRESSION.md](PROGRESSION.md)).
- Server beam each use tick: block clip (COLLIDER) up to 24 blocks (×0.5 underwater), entity hit along it,
  3.0 damage every 5 ticks (12/s) with damage type `lacuna:solar_beam` (in `minecraft:is_fire`, armor applies),
  4 s fire. `BeamMath` (unit-tested): day ×1 yellow, underwater ×0.4 / range ×0.5 blue no ignition (+bubbles),
  night ×0.5 white, both ×0.2 blue; Forgotten Land counts as day; dimensions without sky count as night.
- Block effects every 10 ticks (not underwater, `mayInteract` + `mayBuild`, config `beam.affectsBlocks`): fire
  next to flammable faces (`BaseFireBlock.canBePlacedAt`), melts snow/ice (ice → water unless ultrawarm or
  `lacuna:not_meltable`), primes TNT, large smoke. Water hit: cloud + poof + extinguish sound every 5 ticks,
  optional `beam.evaporateWater` removes flowing water.
- Dust particle ray (one per block, every 2 ticks, max 24).
- No movement slowdown while channelling: client `MovementInputUpdateEvent` pre-scales input by 5 to cancel the
  vanilla ×0.2 that follows the event.
- All 7 Lacuna damage types (`solar_beam`, `eclipse`, `echo_strike`, `solar_ray`, `pyrrhic_bolt`,
  `gravemaw_wave`, `reflection_touch`) with death messages (en + it).
- GameTest: beam pulse damages a zombie in front of a mock player.

Gates: 3 ✔, 4 (logic, now 15 tests) ✔, 1/2/5/6 not runnable here.

## M4 — Umbral Iron, Eclipser + Shadow Walk, Solar Flare, Sun's Fury

Built:
- Umbral Iron: ores (stone hardness 4.0, deepslate 5.5 — deepslate value not in spec, vanilla +1.5 convention),
  raw/ingot/nugget, storage + raw blocks, recipes (smelt/blast, 9⇄1), ore worldgen Y −64..0, size 6, 3/chunk.
  `UMBRAL` tier (iron level, 900 uses, 6.5 speed, ench. 12) for the M7 tool set.
- Eclipser (`SwordItem` with its own tier so durability is 250, total damage 8, speed 1.6, repair with umbral
  ingots, only Unbreaking via `supportsEnchantment`/`isPrimaryItemFor`). Charge in data component
  `lacuna:eclipse_charge` {charge, last_hit}: +10 per hit at most every 5 ticks, reset on any damage taken
  (`LivingDamageEvent.Post`), −2/s after 8 s idle, action bar `Eclipse N%`, 4 textures via predicate
  `lacuna:charge` (0 / 1–33 / 34–66 / 67–100), no re-equip bob on charge changes.
- Dash (charge ≥ 20): consumes all charge, distance `4 + 0.1·charge` (≤ `dashMaxDistance`), damage
  `6 + 0.14·charge` (≤ 20, type `lacuna:eclipse`) once to every living entity within 1.5 of the path, durability
  `5 + 15·charge/100`, cooldown 400. Stops before solid blocks unless Shadow Walk is enabled.
- Shadow Walk: spectator mode for `40 + 80·charge/100` ticks with `lacuna:shadow_walk` effect icon; previous mode
  in a serializable attachment; restored on expiry/timer, milk (effect removed), death, logout, login, dimension
  change, server stopping; never overrides a mode an operator changed; stuck players moved to the nearest safe
  spot within 16 blocks, else the dash origin; mob targets cleared and new targeting cancelled; cross-dimension
  spectator teleports are cancelled while active.
- Solar Flare Fragment (1.5 % per ruin-cache roll; merchant/labyrinth sources come with M10), Solar Flare, Sun's
  Fury (single use, consumed unless creative, fizzles inside the Forgotten Land and is kept). Thrown entity (speed
  2.5, gravity 0.03, 10 s fuse, `ThrownItemRenderer`, discarded if reloaded from disk). Detonation: overworld time
  → next noon + clear weather, then a `SolarRayEntity` (noSave, `NoopRenderer`): flash + 60 ticks of particles
  (8 packets/tick), 5 damage every 5 ticks + 10 s fire within radius 7, block job sorted in outward rings,
  ≤ `blockChangesPerTick` (400) per tick and ≤ 12000 per use, hardness ≤ 50, skips bedrock, fluids and
  `lacuna:solar_immune`, only with `mobGriefing` and `mayInteract`, loaded chunks/world border/build height only.

Deviations:
- Shadow Walk duration follows the formula `40 + 80 × charge/100` (56 ticks at charge 20; the prose says 2 s).
- Same-dimension spectator "teleport to player" cannot be blocked without Mixins; documented limitation.

Gates: 3 ✔, 4 (logic, 21 tests) ✔, 1/2/5/6 not runnable here.

## M5 — Fractured Pearl system

Built:
- `fractured_pearl` (recipe `DRD / RPR / DRD`, stack 1): right click splits into an `arrival_shard` and a
  `departure_shard` sharing a random link id (data component `lacuna:pearl_link` {id, cracked}).
- Arrival Shard (durability 10, 1 per throw, 30 s cooldown): thrown entity (speed 1.5, gravity 0.03, no damage,
  never teleports the thrower). On a block it locks: `arrival_anchor` placed in the adjacent replaceable non-fluid
  block (mayInteract respected), the shard stack is stored in the anchor's block entity, `PearlLinkSavedData`
  records link → {dimension, pos}; re-locking removes the old anchor first (its chunk loaded once). Entity hits,
  unplaceable spots → drops back as an item; void/unloaded chunk → returned to the thrower.
- Arrival Anchor: floating teal crystal (rotated cube model, light 6), blast resistance 1200, push-immune, not
  replaceable by fluids, no loot table; broken by a (non-creative) player it pops the stored shard and clears the
  link; removed any other way, the next Departure use finds no anchor and cleans the record.
- Departure Shard (durability 5, 15 s cooldown): teleports to the anchor (safe spot, chunk loaded once, dismount,
  leashes break, portal particles both ends). Missing/invalid link, another dimension or the Forgotten Land:
  "The shard hums, but nothing answers.", no durability, no cooldown, tooltip switches to "Cracked".
- Tooltips: `Linked: ABCD-1234`. Anvil: both shards +3 durability per ender pearl; item+item merging disabled.
- GameTests (spec list): link round trip (resolution never checks the holder, so a friend can use it), two pairs
  do not interfere, broken anchor invalidates, cross-dimension and Forgotten Land refusal. Restart persistence uses
  the same SavedData pattern as banishment records (covered by the smoke test's banished pig).

Gates: 3 ✔, 4 (logic) ✔, 1/2/5/6 not runnable here.

## M6 — Utility items

Built:
- Chronoshard Pocket Watch (12 durability; a relic since 1.0.0-beta, no recipe): snapshot {dimension, position, view,
  health, food, saturation, game time} every 2400 ticks while anywhere in the inventory (first one immediately),
  ring of 3 (component `lacuna:watch_snapshots`); right click rewinds to the newest snapshot ≥ 400 ticks old in the
  current dimension only (safe spot, health = max(now, then), food/saturation restored, fire cleared, harmful
  effects applied in the last 10 s removed — tracked in the `harmful_effects` player attachment), consumes it and
  newer ones, 1 durability. Chronal shards inserted from the other hand (component, max 5) → cooldown
  6000 − 600·shards. Repair: gold ingot (25 %, not specified in spec).
- Mirror of Recursion (20 durability; relic, no recipe): BLOCK use animation; `ProjectileImpactEvent` on a player
  raising it: projectiles in `lacuna:reflectable` (minus `lacuna:unreflectable`) coming from the front 180° are
  cancelled and sent back toward their shooter with the player as owner (arrows become non-pickable), 1 durability;
  up to 3 echoes stored (component `lacuna:mirror_stored`); sneak + use re-fires the oldest at
  0.85^(reuse+1) speed (arrows also scale base damage); an echo already used 3 times shatters instead.
  Repair: divine glass shard (25 %, not specified in spec).
- Null Lantern (300 durability; relic, no recipe): held in either hand, hostile mobs (not bosses) between 3 and
  16 blocks cannot target the holder unless hurt by them in the last 5 s (`LivingChangeTargetEvent` + a 1 Hz sweep
  that drops existing targets); natural monster spawns within 24 blocks fail (`MobSpawnEvent.PositionCheck`);
  1 durability per 200 ticks held. Repair: umbral iron ingot.
  - Revision 4: fixed the lantern silently switching itself off (brain memories were read with `Brain#getMemory`,
    which throws for goal-based mobs, and SafeRun then disabled `null_lantern`; every read is now guarded by
    `hasMemoryValue`). The item is a `BlockItem` for `lacuna:null_lantern` (`lantern/NullLanternBlock` extends
    `LanternBlock`, + `lit`; vanilla `template_lantern` / `template_hanging_lantern` models with generated
    `block/null_lantern` (3-frame flicker) and `block/null_lantern_off` textures). Durability = charge: placing moves
    it into `NullLanternBlockEntity`, `getDrops` puts it back (exactly one item, also on explosions and pistons).
    `lantern/LanternWard` holds the shared ward: held lit lantern or within 12 blocks of a lit placed one, plus a
    volatile per-dimension registry of lit placed lanterns (cleared on server stop) for the target, damage and spawn
    events. Both burn 1 charge / 200 game ticks; at 0 the item stays (damage capped, `lacuna:lit` item property ->
    `item/null_lantern_off`) and the block turns `lit=false`, with a fizzle cue. Recharge: Ender Pearl 50 %, Echo
    Shard 100 % (`overrideOtherStackedOnMe` in any slot, or `useItemOn` on the placed lantern, which relights it).
- Whisperbone Flute (40 durability; relic, no recipe; goat-horn animation/sound, 1200 cooldown) + Echo mob:
  volatile per-dimension ring buffer (64) of non-player non-boss deaths; up to 3 deaths within 20 blocks and
  1200 ticks become Echoes (translucent zombie-model humanoid, health ≤ 20, attack ≤ 6, speed 0.3, 200 ticks,
  fights what the owner hits / is hit by, immune to the owner, no loot/XP, `noSave`), each leaving an
  `echo_fragment` on expiry (+20 flute repair at an anvil). No deaths: "The bones stay silent." with no cost.
- Orrery of Thresholds (`DKD / RCR / SSS`): static pedestal-and-rings model; right click (5 s per player) shows the
  ÷12/×12 translation and the direction + distance to the nearest `lacuna:orrery_targets` structure within
  `orreryRadiusChunks` (one search per second server-wide); inside the Forgotten Land only the translation.
- GameTest: deaths are remembered once and turn into capped Echoes.

Gates: 3 ✔, 4 (logic) ✔, 1/2/5/6 not runnable here.

## M7 — Gear

Built:
- Zenithite: `zenithite_ore` + `deepslate_zenithite_ore` (XP 2–5, 1–2 shards, Fortune), `zenithite_block`,
  `zenithite_shard`. World generation on Inverted Falls islands comes with M10.
- Starlace (registered `ArmorMaterial`: 2/6/5/2, toughness 0.5, enchantability 18, factor 24, repair cloth):
  `starlace_cloth` (shard + 2 string → 2), four armor pieces, 64x32 armor layers. Starlace Boots: sneaking while
  falling > 4 blocks gives slow falling 80 ticks once (item cooldown 200 ticks, 1 durability), server-side only.
  Set abilities (`gear/StarlaceSet`, toggle `gear.starlaceAbilities`): Constellation Step (full set; jump again in
  mid-air once per airtime: the client sends `StarStepPayload` on the jump key's rising edge, the server re-checks set,
  airtime, liquids/flight/elytra/riding and an 8-tick cooldown, then sets the motion), Starfall (2+ pieces halve fall
  damage; full set: a landing from > 5 blocks is harmless and hits hostile mobs within 3.5 blocks for 4 magic damage
  and knockback, ring FX `starlace_starfall`), Starlit (+5 % speed per piece at night under open sky or in the End,
  one transient modifier `lacuna:starlit` refreshed every second). Tooltips list the set bonus with the worn count.
- Umbral Iron tool set (tier from M4). `lacuna:umbral_gear` (tools + Eclipser + Gravemaw) deals +25 %
  (`gear.umbralDarkBonus`) direct melee damage while the wielder's max local raw brightness ≤ 4.
- Special weapons (own tiers for exact durability/repair; only Unbreaking via `supportsEnchantment` /
  `isPrimaryItemFor`; added to `enchantable/durability`):
  Gravemaw Greatsword (700, 11 dmg, 0.8 speed; still-standing charge ≤ 40 ticks, release ≥ 20 → 8-block ±35°
  crescent, `10 + 0.3·charge`, knockback 1, sweep particles at 3 distances, 3 durability, cooldown 120; moving
  > 0.05/tick cancels), Tidecaller Sickle (450, 4 dmg, 2.2 speed, prismarine repair; revision 3: use = Tidal Wave
  rolling 14 blocks over the terrain, 4 dmg + carry, douses fire and quenches lava, cooldown 60; sneak + use at 3 Tide
  Charges = Maelstrom whirlpool, 1.5 drown dmg/s for 5 s, closing geyser, cooldown 300; melee drenches, +3 in water/rain),
  Heartwood Bow (480; arrows tagged through `customArrow` + attachment apply `lacuna:rooted` 2 s, 4 s + pinned
  particles with a wall ≤ 1.5 blocks behind the target; green particles on blocks; recipe and repair material
  `#lacuna:heartwood_bow_repair` arrive with Echo Birch in M8), Echo Dagger (300, 3 dmg, 2.6 speed, echo shard
  repair; each direct melee hit repeats once 10 ticks later at 60 % as `lacuna:echo_strike`, which never echoes),
  Pyrrhic Scepter (250, gold repair; pays 2 HP — refused at ≤ 4 HP — for a homing bolt: speed 1.2, 30° cone,
  24 blocks, turn rate 0.15, `5 + (1 − hp/max)·10` damage, 2 s fire; particle-only `NoopRenderer`, noSave).
- `lacuna:rooted` effect (movement speed and jump strength −100 %).
- GameTest: special weapons accept Unbreaking only; durability values.

Deviations: Zenithite ore is split into stone and deepslate variants (spec: "(stone/deepslate)").

Gates: 3 ✔, 4 (logic) ✔, 1/2/5/6 not runnable here.

## M8 — Four wood families

Built:
- Cindervine, Crystal Willow, Tidebloom (mangrove) and Echo Birch, each with log, stripped log, wood, stripped wood,
  planks, stairs, slab, fence, fence gate, door, trapdoor, pressure plate, button, leaves, sapling, potted
  sapling (60 blocks, registered explicitly in `ModWoodBlocks`). `BlockSetType`/`WoodType` per family are
  created in the `WoodFamily` enum initialiser (before any block supplier runs).
- Stripping with an axe through NeoForge's `neoforge:strippables` data map (log → stripped log, wood → stripped
  wood). Flammability via `FireBlock#setFlammable` in common setup (vanilla values; Cindervine excluded:
  fire-proof, items fire resistant, not in `logs_that_burn`). Fuel: planks/logs through the vanilla tags in
  NeoForge's furnace fuel data map; Cindervine planks 400 ticks (item override + data map), Ember 1600 ticks.
  Compost 30 % for saplings and leaves (`neoforge:compostables`). Flower pots wired with `addPlant`.
- Leaves: vanilla `LeavesBlock` decay/persistence, loot = vanilla oak-style (5 % sapling with Fortune table, 2 %
  sticks) plus 5 % `ember` (Cindervine, which also smoulders with small flames/smoke) and 5 %
  `crystal_willow_shard` (4 shards → glass).
- Trees: datapack tree features cloned from vanilla templates: Cindervine (acacia forking trunk), Crystal Willow
  (cherry trunk, glassy translucent leaves, `lacuna:hanging_strands` tree decorator placing
  `crystal_willow_strands` that only survive attached from above), Tidebloom (vanilla mangrove trunk + root placer
  with `tidebloom_roots`; roots splash on break and have a 1 % random-tick chance to spawn a cod/salmon in adjacent
  water if < 3 fish nearby), Echo Birch (birch shape, custom amethyst-chime `SoundType`; paired saplings within 4
  blocks / Y ±1 grow together and get a persistent-leaf bridge between canopies).
- Worldgen via biome modifiers (rarity 1/24–1/40): Echo Birch in birch forests, Cindervine in savanna/badlands/
  windswept, Crystal Willow in cherry groves/meadows, Tidebloom in swamps/mangrove swamps/rivers. FL biome groves
  arrive with M10.
- Heartwood Bow repair tag `lacuna:heartwood_bow_repair` (Echo Birch planks); its recipe (`.PS / PRS / .PS`) was
  removed in 1.0.0-beta (a relic).
- GameTests: stripping for every family, placed leaves persistence, Cindervine plank burn time.

Deviations: no signs, hanging signs or boats (spec: optional in M12).

Gates: 3 ✔, 4 (logic) ✔, 1/2/5/6 not runnable here.

## M9 — Mobs

Built:
- **Lantern Moth** (`lantern_moth`, ambient): extends `Bee` purely to reuse the vanilla `BeeModel` and flying
  move control/navigation; every bee goal is removed after `Bee#registerGoals` (so Bee's internal goal fields
  exist), no hive/pollination/sting/anger/breeding. 6 HP, scale 0.8, drawn full-bright. Goal: hover around a block
  light ≥ 12 within 16 blocks or a player holding `#lacuna:moth_attractor` (torches, lanterns, glowstone, candles;
  the Null Lantern is excluded). Spawns at night under open sky in `#c:is_forest` (includes flower forests) and
  `#c:is_swamp` (weight 8, groups 1–3); the Forgotten Land's fixed dusk counts as night for the Whispering Marsh
  (M10). Despawns like a bat. Drops 0–2 `silk_dust` (+ looting).
- **Silk Dust** + **`silk_dust_glow`**: right click a face places a thin, collision-less, light-9 translucent decal
  (6 facings, button-style rotations) that removes itself after 3600 ticks through a scheduled tick (saved with
  the chunk); a random tick re-schedules it if the tick ever went missing. Breaks with its supporting block.
- Two more mobs built here (an ambush spider and a brass tracking hound) were removed in revision 2 (0.2.0):
  their drops now come from vanilla mobs through `neoforge:add_table` global loot modifiers
  (`data/neoforge/loot_modifiers/global_loot_modifiers.json` → `lacuna:extra/*` tables): spiders and cave spiders
  5 % umbral steel nugget (player kill, looting bonus); husks 0–2 clockwork gears (+ looting) and 4 % chronal shard;
  pillagers 0–1 clockwork gear (+ looting).
- **Clockwork Decoy**: shapeless recipe (3 gears + redstone + white wool), thrown (snowball arc, stack 16, 400
  tick cooldown); on landing it becomes a `clockwork_decoy` humanoid (zombie-layer model, brass texture, 20 HP,
  immovable, never saved, no loot/XP, immune to its owner) for 300 ticks. On its first tick every `Enemy` mob within
  16 blocks that targets the owner or nothing switches to it, and it re-asserts that every 5 ticks (vanilla
  target goals would otherwise switch back within a second); expiry/death releases them.
- Spawn egg (`DeferredSpawnEggItem`) for the moth; attributes via
  `EntityAttributeCreationEvent`; placements via `RegisterSpawnPlacementsEvent` (REPLACE, ON_GROUND,
  MOTION_BLOCKING_NO_LEAVES); spawns via `neoforge:add_spawns` biome modifiers; vanilla bee sounds.
- Selftest now checks that every living Lacuna entity has attributes and that the moth attractor tag is non-empty.
- Validator: new check that every `Lacuna.id("textures/…png")` referenced from Java exists (negative-tested).
- GameTests: moth attributes, silk glow light/no collision/scheduled removal, decoy lures an idle zombie.

Deviations: none.

Known limitations: brain-driven mobs (piglins, hoglins, wardens) ignore the decoy because they do not use
`Mob#setTarget`-based goals.

Gates: 3 ✔, 4 (logic, 26 tests) ✔, 1/2/5/6 not runnable here.

## M10a — Forgotten Land biomes

Built:
- The dimension's `multi_noise` source now has five biomes (temperature/vegetation noise only; terrain unchanged):
  Forgotten Plains (base), Cinderfrost Flats (T < −0.45), Whispering Marsh (humid), Clockwork Dunes (hot, dry),
  Inverted Falls (hot, humid). Per-biome surface rules, features, spawns, tints, particles and ambient sounds.
- **Forgotten Plains**: + Echo Birch groves (rarity 1/7 × 3) and spiders (weight 4).
- **Cinderfrost Flats**: `ashen_snow_block` surface over tuff (blockstate `footprint` 0–3: any living entity
  stepping on it sets 3, a scheduled tick fades one stage per 100 ticks), ash particles, rare Cindervine spires,
  `clear_ice` (near-transparent ice, silk touch only) capping every still pond through a `lacuna:clear_ice_cap`
  top-layer feature, so ruins below stay visible. No precipitation, no hostile spawns.
- **Inverted Falls**: `lacuna:floating_island` feature: stone ellipsoids at Y 110–150 with grass/moss top,
  2–3 zenithite veins, divine glass clusters, 50 % a Crystal Willow. Beside each island a chain of
  `updraft_spring` blocks (pool basin on the ground, then small islets every ≤ 38 blocks) ends 2 blocks above the
  island top so it can be reached. Spring: translucent, no collision, blockstate `range` 1–40 (worldgen sets the
  exact length; placed by hand = 40), ticking block entity: server every 4 ticks for non-player entities, client
  every tick for the local player only (players move client-side); net upward acceleration 0.08/tick (gravity
  compensated) capped at 0.4, fall distance reset, column re-scanned every 40 ticks and stopped by the first
  motion-blocking non-spring block; rising cloud/bubble/splash particles. Inverted Falls also has Crystal Willows
  and divine glass clusters on the ground.
- **Whispering Marsh**: mud/grass surface, `marsh_pool` shallow pools (vanilla `waterlogged_vegetation_patch`
  with mud floor and seagrass), Tidebloom mangroves, lily pads, `spore_mushroom` patches (light 7, spore particles;
  every 20 ticks living entities within 6 blocks glow for 60 ticks — reveals mobs in the fog; skipped when no
  player is within 48 blocks; worldgen-placed mushrooms start their cycle on the first random tick), Lantern
  Moths (ambient 8) and spiders (monster 6). Client (config `marshFog`, SafeRun): fog far plane blends to 28
  blocks and non-relative sounds from beyond 16 blocks play at 40 % (a delegating `SoundInstance`, because
  `PlaySoundEvent` fires before the sound is resolved).
- **Clockwork Dunes**: sand over sandstone, `buried_watch` (tilted half-buried watch, drops 1–2 chronal shards,
  silk touch drops itself), `rusted_gear` (flat decorative gear), dead bushes, sparse Echo Birch, rabbits
  (creature 4), quiet note-block "hat" ticking as the additions sound. Slow time: 50 % of `CropGrowEvent.Pre`
  results are `DO_NOT_GROW` in this biome (config `dunesSlowCrops`).
- Moths treat the Forgotten Land's fixed dusk as night.
- Validator: configured feature types (vanilla or registered in `ModFeatures`) and placement modifier types are
  checked now (negative-tested).
- GameTests: footprint set/fade, updraft lifts a pig, spore mushroom makes a spider glow and emits light 7,
  buried watch drops. Smoke phase 1 locates all four new biomes and runs the three new features once.

Deviations: the updraft's "+0.08/tick" is applied as net acceleration (gravity compensated), otherwise a 0.08
push only cancels gravity and nothing rises; islet springs make islands at Y 110–150 reachable with 40-block columns.

Gates: 3 ✔, 4 (logic) ✔, 1/2/5/6 not runnable here.

## M10b — Sunken Bell Tower, Obelisk of Unreadable Names

Built:
- **Sunken Bell Tower** (`lacuna:sunken_bell_tower`, oceans and deep oceans, spacing 36/12): 9×9×26 mossy
  stone-brick tower projected to the ocean floor (interior positions are left out of the template, so the sea
  fills it; 1.21 waterlogging applies), arched windows, prismarine floors with a ladder shaft, sea lanterns, base
  chest (`lacuna:chests/sunken_bell_tower`: prismarine, metals, rubies, divine shards, nautilus shell, chronal
  shard, 2 % heart of the sea), open bell chamber with oxidized copper roof and the **`sunken_bell`**.
- `sunken_bell`: right click (60 s volatile cooldown per player) → bell toll, rings of nautilus particles and a
  `RevealOresPayload(positions ≤ 64, 160 ticks)` (first custom payload; registered with
  `RegisterPayloadHandlersEvent`, client handler only touched on the physical client) with the nearest ores in
  `#c:ores` within 24 blocks. The scan never loads chunks and skips sections whose palette has no ore. The client
  draws them with the shared `OutlineRenderer` (gold, through walls).
- **Obelisk of Unreadable Names** (`lacuna:obelisk`, rare in land biomes, spacing 44/16): polished blackstone
  platform, 3×3 base with an `obelisk_inscription` on every face, 1×1 pillar to 10 blocks. The Forgotten Land
  `obelisk_ruin` templates now carry one inscription too.
- `obelisk_inscription` (unbreakable in survival, no drops): right click prints one of 24 lore lines (EN/IT),
  chosen by a position hash (`logic/ObeliskRules`, unit-tested: stable, all lines reachable); 12 % chance of a
  reward (1–3 rubies / 1–2 divine glass shards / 1 chronal shard), at most once per player per obelisk and with a
  20 min cooldown per obelisk (`ObeliskSavedData` in the overworld storage).
- Structure tag `lacuna:orrery_targets` = obelisk + sunken bell tower (the Orrery from M6 now finds them).
- GameTests: ore scan (vanilla + `c:ores` ruby, nearest first, limit), obelisk claim/cooldown and unbreakability.
  Smoke phase 1 places both structures and locates `#lacuna:orrery_targets`.

Gates: 3 ✔, 4 (logic, 29 tests) ✔, 1/2/5/6 not runnable here.

## M10c — Mirror Labyrinth, Reflection

Built:
- **Mirror Labyrinth** (`lacuna:mirror_labyrinth`, all Forgotten Land biomes, spacing 30/10): 25×25×5, five
  deterministic variants (`mirror_labyrinth/maze_1..5`) from a seeded depth-first maze over 12×12 cells
  (generator check: the trigger is reachable from the entrance, the hoard is sealed). Unbreakable ruin-brick floor,
  `mirror_glass` walls and ceiling (unbreakable in survival, see-through), 3×3 central room with the
  `labyrinth_trigger` ring, entrance to the south, an exit door to the north and the Mirror Hoard alcove next to the
  room, both sealed with `labyrinth_exit` blocks. Loot `lacuna:chests/mirror_hoard`: divine glass shards, rubies,
  chronal shards, echo fragments, amethyst, 10 % Solar Flare Fragment.
- Stepping on the ring (not in creative/spectator) spawns one **Reflection** per labyrinth: dark-silver
  translucent humanoid (zombie-layer model), 20 HP, contact damage 6 (`lacuna:reflection_touch`, 10 tick
  interval), no physics, never saved. It replays its player's own path 60 ticks late: the player's position is
  sampled every 5 ticks into a volatile `PathTrail` and interpolated
  (`positionAt`, unit-tested). It vanishes when the player leaves 48 blocks, logs out, dies or changes dimension
  (the ring re-arms). Killed by a player → every `labyrinth_exit` within the labyrinth turns to air with
  particles, the ring shows as solved.
- Reflection is in `lacuna:cannot_be_banished` and `lacuna:unreflectable`; selftest checks its attributes.
- GameTests: a defeat opens the seals (not the mirrors) and marks the ring solved; a lost reflection does not.

Deviation: the Mirror Hoard chest is "locked" by sealing it behind `labyrinth_exit` blocks instead of a vanilla
container lock (no item key to lose, works with every chest UI).

Gates: 3 ✔, 4 (logic, 30 tests) ✔, 1/2/5/6 not runnable here.

## M10d — Caravan of the Last Merchant

Built:
- `last_merchant`: extends `WanderingTrader` (vanilla goals, trading UI, despawn timer) rendered as a plains villager
  (vanilla `VillagerModel`, villager + plains type textures) with a blue vest overlay (revision 2); entity type is `noSave` ("no persistent
  state"), so it is gone after despawn, chunk unload or restart. Spawn egg for testing.
- `MerchantSpawner` (server tick, Overworld): every `lastMerchantIntervalTicks` (default 72000 = 3 days, next attempt
  kept in `lacuna_last_merchant` SavedData) a 25 % chance by day (respects `doTraderSpawning`) to spawn 16–48 blocks
  from a random player on dry solid ground; despawns after 3 days; config `lastMerchant`.
- Stock (`logic/MerchantPricing`, unit-tested): buys the three ore families the nearest player mined most (raw iron,
  copper, gold, coal, redstone, lapis, diamond, quartz, ruby, raw umbral steel, zenithite) plus a bulk offer for the
  top one, and two ingot/gem buys; sells divine glass shards (2), rubies (3), chronal shards (8) and echo shards (10);
  8 % chance of one Solar Flare Fragment for 16 rubies + 24 divine glass shards (spec 14). Since 1.0.0-beta he deals
  in rubies only and also sells relics (see the progression section at the end). "Recently mined" = vanilla
  `Stats.BLOCK_MINED` over `#c:ores` since the player's previous merchant visit (a `mined_snapshot` attachment, kept on
  death); the first visit uses lifetime totals.
- `CurrencyService`: every Lacuna trade asks it for the currency item (emerald until M11c installs the township coin).
  Sell prices take a price index (township caravan effect hooks in with M11d).
- GameTest: a merchant stocks ≥ 9 offers, sells the four goods for the currency and buys materials.

Deviation: no trader llamas (optional in the spec).

Gates: 3 ✔, 4 (logic, 34 tests) ✔, 1/2/5/6 not runnable here.

## M11a — Township structures

Built:
- Five structures `lacuna:township_plains|desert|savanna|taiga|snowy` (jigsaw, size 12, max distance 128,
  beard_thin, projected to WORLD_SURFACE_WG) in their village biomes, one structure set (spacing 34, separation 12,
  unique salt, `exclusion_zone` 4 chunks from `minecraft:villages`), structure tag `lacuna:townships`.
- `tools/structures/m11_townships.py` builds 34 templates per biome palette (170 total), all deterministic:
  - **core** (63×63×24): stone wall ring with crenellations, four corner towers, four 2×3 `township_gate`
    openings flanked by lantern pillars; cobbled main streets; plaza with checkered paving, fountain fed by an
    **aqueduct** (arched water channel from beyond the north wall), founder **statue** (3 designs, sign plaque with
    lore), **bell tower** with a vanilla bell, `township_ledger` podium, benches and lamps; civic halls on the
    diagonals: **Council Hall** (`council_table`), **Courthouse** (`judge_bench`), **Guild Hall** (`guild_board` +
    three patterned banners), **Library/School** (bookshelves, lectern, `school_desk`s); flower beds, orchard trees,
    two iron golem guards.
  - eight **district lots** (11×11) on jigsaw connectors `lot_market` (north street), `lot_workshop` (east),
    `lot_residential` (south), `lot_civic` (west); pools of 6 buildings each: market stalls with villagers, 2–3 story
    houses with beds/ladders, workshops (all 13 vanilla job-site blocks across the pool), granary, orchard, public
    garden, stables (with a horse), tavern, school annex.
  - outside each gate: terrain-matching road pieces (chain + end piece with lamps) with side connectors to farms
    (wheat/carrot/potato/beetroot fields with water, orchards).
- Civic blocks (decorative in 11a): `township_ledger`, `township_gate` (OPEN state: no collision when open),
  `council_table`, `judge_bench`, `guild_board`, `school_desk`, `mint_press`.
- Validator: every jigsaw names an existing pool whose pieces offer a connector with its target name and a valid
  `final_state` (negative-tested); township templates are generated in strict mode (an out-of-bounds block is an
  error, not silently dropped).
- Smoke: phase 1 places one plains township and locates `#lacuna:townships`; `tools/smoke_townships.txt` places
  20 townships (4 per variant) for the spec's 20-position check.

Deviations: the aqueduct is part of the core template instead of a separate jigsaw piece (it must line up with the
fountain); eight lot slots per township (pools of 6 buildings each).

Gates: 3 ✔, 4 (logic) ✔, 1/2/5/6 not runnable here (20-placement check is in `tools/smoke_townships.txt`).

## M11b — Ledger, reputation, laws, bell, gates

Built:
- `township_ledger` is now an entity block: its block entity holds a persistent `townshipId`; the first server tick
  registers the record in `TownshipSavedData` (`lacuna_townships`, overworld storage) — lazy and idempotent. Record:
  id, dimension, centre (the ledger), radius 64, seed, 2 laws, coin value, price index, policy + end day, next
  council day, per-player reputation, per-player daily counters, pardon days, weekly reputation samples. Right click
  the ledger (or `/lacuna township info`) prints reputation + tier, laws, policy and next council, coin value.
- `logic/ReputationMath` + `logic/TownshipLaws` (unit-tested): −100..100, tiers Hostile/Wary/Neutral/Friendly/Honored,
  price multiplier `1 − rep/400`, daily caps per kind of change, Festival doubles gains only, fines, law selection by
  seed, dawn/gate time windows.
- Changes (`TownshipEvents`, SafeRun): +1 per trade with a township villager (cap 2/day), +3 per hostile mob killed
  within 32 of the ledger (cap 10/day), −20 for killing a villager/guard, −10 for hitting one (once per 5 s), −5 for
  breaking `#lacuna:township_protected` blocks (cap 15/day), −1 per mature crop harvested on township farmland unless
  Honored (cap 10/day), −3 for opening a container inside the walls with a villager within 12 blocks unless Honored
  (cap 9/day). Creative/spectator players are ignored. An action-bar line shows every change.
- Effects: villager prices × `1 − rep/400` × price index × policy, applied to the offers in
  `PlayerContainerEvent.Open` (after the villager reset its special prices, before the offers are sent); Hostile
  players are attacked by the township's (non player-made) iron golems; Honored players harvest freely and gates open
  for them at night.
- Laws (2 per township): no drawn weapons inside the walls (`#lacuna:drawn_weapons` in the main hand), gates close at
  dusk (strangers with rep < 10 inside the walls at night), no breaking blocks inside the walls (extra −2, cap 10/day),
  the bell rings only at dawn. Enforcement from the ledger every 20 ticks: 3 s action-bar warning, then −1 every 10 s
  (5 s under Martial Law) and the nearest guard walks over. Never instant death.
- Bell: right clicks on vanilla bells inside a township (event not cancelled): +2 once per day at dawn, −5 after 3
  rings a day, −3 outside dawn when law 4 is active.
- Gates: each `township_gate` cell keeps a 40-tick scheduled tick (random-tick fallback for worldgen gates): open
  23000–12000, closed at night unless an Honored player is within 4 blocks; survival players can only break a gate
  while sneaking with an axe (and lose 5 through the protected-block rule).
- Commands: `/lacuna rep get|set <player> [value]`, `/lacuna township info`.
- GameTests: ledger registration is idempotent and covers its radius, Honored tier lowers prices; gates follow the
  time of day and keep their schedule.

Deviation: "inside the walls" is approximated as within 30 blocks (Chebyshev) of the ledger, which sits 6 blocks off
the core's centre.

Gates: 3 ✔, 4 (logic, 40 tests) ✔, 1/2/5/6 not runnable here.

## M11c — Council, currency, courthouse

Built:
- **Council** (`TownshipDaily`, at dawn): the first council is 7 days after a township is first seen, then every 7
  days; it picks one policy for 7 days with `logic/CouncilMath` (unit-tested), weighted by the week's average
  reputation of players around the township (sampled once per dawn): Free Market (prices ×0.90), Austerity (×1.10),
  Festival (reputation gains ×2, firework/happy particles over the plaza every 5 s), Martial Law (guards get a
  transient +50 % attack modifier, laws penalise every 5 s). Players in the township are told the decree.
- **Currency**: `township_coin`. `CurrencyService` now returns the coin while `township.enabled` and
  `township.currency` are on (the Last Merchant trades in coins too), the emerald otherwise.
- **Coiner** profession (job site `mint_press`, placed in two workshop buildings) buys raw iron/copper/gold, rubies,
  raw umbral iron and ingots for coins; **Magistrate** (job site `judge_bench`) sells paper, books, name tags and a bell
  for coins. POI types + professions are registered through DeferredRegister (POIs after blocks), trades through
  `VillagerTradesEvent`; villager/zombie-villager profession overlays and names exist for both (and the M11d
  Guildmaster).
- **Coin value** 0.6–1.4: −2 % per 16 ore minted at a Coiner (`TradeWithVillagerEvent`), +0.01 per day back toward
  1.0. When a trading screen opens in a township, coin prices are multiplied by `1/coinValue` and ore-for-coin trades
  by `coinValue`, on top of reputation × price index × policy.
- **Courthouse**: `judge_bench` with coins in hand pays fines (+5 reputation per coin, never above 0, one pardon per 3
  in-game days); empty hand shows standing, fine and pardon cooldown.
- Validator: every profession needs its lang name and villager + zombie-villager textures (negative-tested by the
  first run). GameTests: mint press/judge bench map to their POIs, Coiner has level-1 trades, coin is the currency.

Gates: 3 ✔, 4 (logic, 43 tests) ✔, 1/2/5/6 not runnable here.

## M11d — Guilds, contracts, schools, caravan effect

Built:
- **Guilds** (Smiths', Gemcutters', Scholars'): the Guild Hall now has one `guild_board` per guild (blockstate
  `guild`, each under its banner; creative players cycle it with sneak + empty hand). Join with 20 coins in hand
  (reputation ≥ 10, one guild per player; stored in the `guild` player attachment, kept on death).
- **Guildmaster** profession (job site `guild_board`): its guild is read from its job-site board (brain `JOB_SITE`
  memory) and cached in a villager attachment; non-members get "Guild members only" and the trading screen does not
  open. Trades in coins, generated per guild by dispatching item listings: Smiths — umbral iron ingots, iron ingots,
  repair kits (new `repair_kit`: restores 25 % durability of the item in the other hand), an umbral pickaxe;
  Gemcutters — rubies, lapis, rare divine glass shards, zenithite, amethyst; Scholars — chronal shards, paper, echo
  shards, enchanted books (random tradeable enchantment).
- **Contracts**: members right click their board for one `guild_contract` (data component `{item, count,
  reward_coins, township, guild}` with Codec + StreamCodec; tooltip shows the task); handing it in at that guild's board
  with the items pays the coins and +3 reputation. One active contract at a time.
- **Schools**: once a day at dawn, up to 40 working villagers of the township within 24 blocks of a `school_desk` or
  lectern gain 10 XP, capped at the level-3 threshold so natural levelling (on their next trade) stops at level 3.
  Desks come from a palette-filtered scan of loaded chunks only.
- **Caravan effect**: a Last Merchant killed within 128 blocks raises that township's price index by 0.2 (max 1.5),
  −0.03 per day back to 1.0; a newly spawned Last Merchant uses the nearest township's price index for his prices.
- Gardens: harvest rules from 18.2 apply to all township farmland (Honored players harvest freely).
- Everything runs behind `township.enabled` and its sub-flags (`currency`, `guilds`), inside SafeRun, only for loaded
  chunks, with bounded loops (≤ 40 villagers/golems).
- GameTests: schools teach a farmer next to a desk; contract component round trip; price index cap/decay; guild board
  is the Guildmaster job site.

Deviation: Guildmasters share one profession (one POI type); the guild comes from the board they work at.

Gates: 3 ✔, 4 (logic, 43 tests) ✔, 1/2/5/6 not runnable here.

## M12 — Polish

Built:
- `it_it` covers every key (415/415, generated next to `en_us`).
- Advancement tab "Lacuna": root (first ruby) → enter the Forgotten Land → come back (goal); forge the Eclipser (goal)
  → throw Sun's Fury (challenge, granted from code via `minecraft:impossible` + `LacunaAdvancements`); split a
  Fractured Pearl (code); walk into a Township (`minecraft:location` with `#lacuna:townships`).
- Curated creative tab: relics, weapons/tools/armour, materials, township and creature items, then all blocks;
  anything unlisted is appended (GameTest checks completeness).
- Tooltip polish: any item with an `item.lacuna.<id>.tip` key gets a grey italic line (client `ItemTooltipEvent`);
  21 materials/blocks have one.
- Player README, `docs/CHANGELOG.md`, `tools/smoke_soak.txt` (deterministic 30-minute console soak: selftest,
  time/weather changes, banish/return round trips, FL features, mob and merchant spawns, structure placements, saves).
- GameTest: tab order/completeness and all seven advancements loaded.

Not done (optional in the spec): wood signs/hanging signs/boats, Orrery block-entity animation, TerraBlender
injection. Emissive layers exist where they mattered (full-bright Lantern Moths).

Final QA status: the 30-minute soak, memory/tick profiling and `./gradlew build` need a machine with access to
maven.neoforged.net and the Mojang servers (blocked here); the scripts are ready (`tools/smoke.sh`,
`tools/smoke_soak.txt`, `tools/smoke_townships.txt`). Profiling note to fill in after that run: average tick time
with 1 player in a Township, peak during township generation, heap after 30 minutes.

Gates: 3 ✔, 4 (logic, 43 tests) ✔, 1/2/5/6 not runnable here.

## Real build and runtime verification (network enabled)

- `./gradlew build`: **passes** (jar `build/libs/lacuna-0.1.0.jar`). The first real compile found one wrong import
  (`PathNavigation` lives in `entity.ai.navigation`).
- `./gradlew runGameTestServer`: **all 36 GameTests pass**. The first run exposed data the offline validator did not
  check — township `max_distance_from_center` 128 + beard padding > 128 (now 116), a wrong sound id
  (`ambient.underwater.loop.additions`), storage recipe file names containing `:` — all fixed, and the validator now
  checks each of them. Test-only fixes: tests set the blocks/rotation they depend on; the GameTest server has no
  datapack dimensions, so the banishment round trip is covered by the smoke test instead.
- `tools/smoke.sh` (dedicated server, two boots on the same world): **SMOKE PASS** — selftest 537 checks, FL
  generation, all ruin/FL/Overworld structures, a plains Township, `locate`, banishment record persisted across restart.
  `/place feature lacuna:floating_island` on live chunks no longer reads worldgen heightmaps.
- Client: launches under a virtual display with software OpenGL and loads all Lacuna resources with no model/texture/
  renderer errors (entity renderers are constructed at resource load); joining a world was too slow to finish on
  software rendering here.

## 1.0.0-beta — progression pass (crafting balance, Lacunar Quintessence)

Every item and its sources, with chances: [PROGRESSION.md](PROGRESSION.md) (it supersedes the relic recipes in the
milestones above and in the spec).

- **Relics are found, not crafted.** Recipes and recipe-unlock advancements removed for the Eclipser, Sun's Fury,
  Divine Magnifying Glass, Chronoshard Pocket Watch, Mirror of Recursion, Null Lantern, Whisperbone Flute, Gravemaw
  Greatsword, Tidecaller Sickle, Heartwood Bow, Echo Dagger, Pyrrhic Scepter, Ember Triangle, Wand of Quickening and
  Starcaller Trumpet. Sources: a guaranteed relic roll in every Mirror Hoard, a relic roll in Sunken Bell Towers,
  ruin caches and village mints (m10/m11/m19), `neoforge:add_table` global loot modifiers on fitting vanilla bosses
  (Wither, Elder Guardian, Warden, Piglin Brute; player kills) and chests (ancient city, bastion treasure, pillager
  outpost, woodland mansion, desert pyramid, End city), and the Last Merchant. Each relic's details tooltip
  (`item.lacuna.<id>.tip`) names its sources.
- **Lacunar Quintessence** (`lacunar_quintessence`, `essence/QuintessenceItem`): Epic, fire resistant, glint, animated
  texture (`tools/art/m19_quintessence.py`); dropped it lasts 20 minutes and ignores fire, lava, cactus and blasts.
  Sources: the Reflection (loot table, `killed_by_player`: once per labyrinth, so `ReflectionEntity` no longer
  suppresses its loot), natural falling stars 20 % (`essence/Quintessence#onNaturalStarLanded`, hooked in
  `FallingStarEntity#strike`), Prime Star Rock 1 %, Sunken Bell Tower 12 %, ruin caches 1 %, the Last Merchant 1 visit
  in 5. It is the only way to craft a totem: Totem of Banishing (`RQR / GTG / .R.`, still with a Totem of Undying),
  Totem of Confusion (the essence replaces the emerald) and a vanilla Totem of Undying (`EGE / GQG / .G.`).
  Advancement `lacuna/lacunar_quintessence` ("The Shape of the Gap"); `enter_forgotten_land` now hangs from it, and
  `forge_eclipser` reads "Claim the Eclipser" (its criterion was always "has the item").
- **Last Merchant** (`logic/MerchantPricing`): rubies only; two relics drawn at random per visit (32-48 rubies, epic
  ones 64 + 2 ruby blocks), the Sun's Fury forge (Solar Flare + 32 rubies — Sun's Fury has no recipe, so the four
  fragments still matter), and a Quintessence in 1 visit of 5 (64 rubies + 4 ruby blocks). Prices above 64 are paid
  partly in ruby blocks (9 each). Unit-tested; the merchant GameTest now expects rubies.
- **Craftable, re-costed**: Umbral Steel tools set a ruby in the grip (the ore is commoner than diamond and the tools
  outclass diamond), the Comically Large Bag needs an ender pearl, the Chrono Hourglass two chronal shards (no umbral
  ingot); Repair Kits gain a recipe (`UGU / LIL`) and refuse `#lacuna:repair_kit_refuses` (the totems: their uses are
  final). Ruin-cache rare pool normalised to exactly 1000 (the exilite entry had not been subtracted from the empty
  weight).

## Beta — the Wayfarer's Chart replaces the Orrery

- Removed the Orrery of Thresholds completely (player request): block + item, recipe and its unlock advancement, model,
  textures, lang (`lacuna.orrery.*`, the `lacuna.direction.*` keys only it used), loot table, `mineable/pickaxe` entry,
  config keys `items.orrery` / `items.orreryRadiusChunks`, its `Balance` numbers and the structure tag
  `lacuna:orrery_targets` (smoke phase 1 now locates `lacuna:obelisk` directly). Old worlds simply lose the block.
- Wayfarer's Chart (`chart/`, `client/chart/`, content/art module `m19_wayfarers_chart`): held like a vanilla filled
  map, a live 128 x 128 map of the Overworld centred on the holder that scrolls smoothly (world-anchored 32 x 32-pixel
  tiles, the texture slid by fractions of a pixel). Other dimensions show the Overworld around the translated position
  (Forgotten Land × `forgottenLand.scale`, else the `coordinate_scale` ratio: Nether × 8; the End rests on the holder's
  bed or the world spawn). Sneak + use: 1/2/4/8/16 blocks per pixel. Markers: world spawn, bed/anchor, banishment
  origin; caption with the coordinates.
- Server sampling never loads or generates a chunk: loaded chunks are surveyed like `MapItem#update` (and stay surveyed
  while cached), the rest is sketched from the generator (multi-noise biome at surface depth through `c:` biome tags
  to map colours, surface height ≈ 128 × depth noise at Y 0) and drawn faded. One shared per-tick budget
  (`items.wayfarersChartBudgetMicros`, 2000 µs) with passes resuming pixel by pixel; tiles shared by all holders,
  refreshed every 40 ticks while shown, at most 768 cached, at most 4 sent per holder per tick, only when changed.
- Client boot run (`tools/demo/wayfarers_chart.txt`, normal world, software rendering): the chart in hand in the
  Overworld at 1:4 and 1:8 and from the Forgotten Land (Overworld X 480 · Z -300 from X 40 · Z -25, ×12), surveyed
  land in full colour next to the faded sketch (rivers, forests, plains, cherry groves, snow, sand). Measured on that
  CPU-starved machine: 20-40 µs per climate sample, about 80 samples per tick within the budget, so a fully unexplored
  view at 1:8 fills in about 15 s, nearest tiles first. The Nether shot of that run landed inside netherrack; the
  script now carves a pocket.
- Not done: item frames show the chart's icon, not a live map.

Gates: 3 ✔ (validate_assets 0 errors), compile ✔, one client boot run ✔; GameTests and smoke not rerun (rule #1).
