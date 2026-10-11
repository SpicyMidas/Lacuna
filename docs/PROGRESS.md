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
  `ruby_block`, `raw_ruby_block`; items `ruby`, `raw_ruby`; Ruby tool set (SimpleTier: diamond level, 800 uses,
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
  enchantable, item+item merge disabled). Recipe `.G. / GDG / .S.`.
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
- Eclipser (`SwordItem` with its own tier so durability is 250, total damage 7, speed 1.6, repair with umbral
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
- Chronoshard Pocket Watch (12 durability, recipe `.R. / GCG / .D.`): snapshot {dimension, position, view,
  health, food, saturation, game time} every 2400 ticks while anywhere in the inventory (first one immediately),
  ring of 3 (component `lacuna:watch_snapshots`); right click rewinds to the newest snapshot ≥ 400 ticks old in the
  current dimension only (safe spot, health = max(now, then), food/saturation restored, fire cleared, harmful
  effects applied in the last 10 s removed — tracked in the `harmful_effects` player attachment), consumes it and
  newer ones, 1 durability. Chronal shards inserted from the other hand (component, max 5) → cooldown
  6000 − 600·shards. Repair: gold ingot (25 %, not specified in spec).
- Mirror of Recursion (20 durability, `QDQ / DGD / QDQ`): BLOCK use animation; `ProjectileImpactEvent` on a player
  raising it: projectiles in `lacuna:reflectable` (minus `lacuna:unreflectable`) coming from the front 180° are
  cancelled and sent back toward their shooter with the player as owner (arrows become non-pickable), 1 durability;
  up to 3 echoes stored (component `lacuna:mirror_stored`); sneak + use re-fires the oldest at
  0.85^(reuse+1) speed (arrows also scale base damage); an echo already used 3 times shatters instead.
  Repair: divine glass shard (25 %, not specified in spec).
- Null Lantern (300 durability, `.U. / ULU / .D.`): held in either hand, hostile mobs (not bosses) between 3 and
  16 blocks cannot target the holder unless hurt by them in the last 5 s (`LivingChangeTargetEvent` + a 1 Hz sweep
  that drops existing targets); natural monster spawns within 24 blocks fail (`MobSpawnEvent.PositionCheck`);
  1 durability per 200 ticks held. Repair: umbral iron ingot.
- Whisperbone Flute (40 durability, `.B. / BEB / .B.`, goat-horn animation/sound, 1200 cooldown) + Echo mob:
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
- Umbral Iron tool set (tier from M4). `lacuna:umbral_gear` (tools + Eclipser + Gravemaw) deals +25 %
  (`gear.umbralDarkBonus`) direct melee damage while the wielder's max local raw brightness ≤ 4.
- Special weapons (own tiers for exact durability/repair; only Unbreaking via `supportsEnchantment` /
  `isPrimaryItemFor`; added to `enchantable/durability`):
  Gravemaw Greatsword (700, 11 dmg, 0.8 speed; still-standing charge ≤ 40 ticks, release ≥ 20 → 8-block ±35°
  crescent, `10 + 0.3·charge`, knockback 1, sweep particles at 3 distances, 3 durability, cooldown 120; moving
  > 0.05/tick cancels), Tidecaller Sickle (450, 4 dmg, 2.2 speed, prismarine repair; hook within 12 blocks pulls
  the target in, 3 dmg, 2 durability, cooldown 100; Tide Charges in water/rain, max 3, heal 2 on the next hit),
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
- Heartwood Bow recipe (`.PS / PRS / .PS`) and repair tag `lacuna:heartwood_bow_repair` (Echo Birch planks).
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
- **Gloomcrawler** (monster): own `Monster` subclass with the vanilla `SpiderModel` (scale 0.8), wall climbing
  (`WallClimberNavigation` + synced climbing flag). Next to leaves/grass/ferns/bushes/saplings and without a target
  it crouches (half-height hitbox, flattened, near-invisible translucent texture, eyes hidden); while crouched it
  only acquires a non-sneaking player within 4 blocks, then leaps (`LeapAtTargetGoal`) and bites. A flee goal runs
  to the darkest of 10 sampled spots (block light ≤ 4) whenever block light ≥ 7 is at its feet or a player
  holding `#lacuna:light_sources` is inside 6 blocks; it never targets or keeps targeting such a player. 22 HP,
  4 damage. Spawns in dark forests, swamps, taigas (monster light rules, weight 6). Drops 0–2 string, 5 % umbral
  iron nugget (player kill, looting bonus).
- **Clockwork Hound** (creature): extends `Wolf` to reuse `WolfModel`/sounds/anger timer; taming, feeding,
  dyeing, armor, breeding and `applyTamingSideEffects` are disabled; all wolf goals replaced. 30 HP, 5 damage,
  speed 0.33. `ScentTracker` (volatile, server-thread only) records each player every 200 ticks for 3600 ticks
  (≤ 18 points), cleared on logout, dimension change and server stop, never saved; pure trail logic is
  `logic/ScentTrail` (unit-tested). With no target a hound picks the oldest trail point within 48 blocks, walks the
  trail oldest → newest (tracking by point time, so pruning never skips it) and targets the player inside 16
  blocks. If that player is in water or sneaking within 8 blocks it loses the scent: target and anger cleared,
  the trail broken at the newest point near the player, 400 tick calm period. A player who hits it is fought
  normally (neutral mob). Spawns on sand/terracotta in deserts and badlands (weight 3). Drops 1–3 clockwork gears,
  4 % chronal shard.
- **Clockwork Decoy**: shapeless recipe (3 gears + redstone + white wool), thrown (snowball arc, stack 16, 400
  tick cooldown); on landing it becomes a `clockwork_decoy` humanoid (zombie-layer model, brass texture, 20 HP,
  immovable, never saved, no loot/XP, immune to its owner) for 300 ticks. On its first tick every `Enemy` mob within
  16 blocks that targets the owner or nothing switches to it, and it re-asserts that every 5 ticks (vanilla
  target goals would otherwise switch back within a second); expiry/death releases them.
- Spawn eggs (`DeferredSpawnEggItem`) for moth, gloomcrawler and hound; attributes via
  `EntityAttributeCreationEvent`; placements via `RegisterSpawnPlacementsEvent` (REPLACE, ON_GROUND,
  MOTION_BLOCKING_NO_LEAVES); spawns via `neoforge:add_spawns` biome modifiers; vanilla sounds (bee, spider, wolf).
- `/lacuna scent` lists every trail and marks the caller's points with particles. Selftest now checks that every
  living Lacuna entity has attributes and that the light tags are non-empty.
- Validator: new check that every `Lacuna.id("textures/…png")` referenced from Java exists (negative-tested).
- GameTests: spec attributes for the three mobs, hound cannot be tamed/fed, silk glow light/no collision/
  scheduled removal, decoy lures an idle zombie, gloomcrawler crouches next to leaves.

Deviations:
- The Gloomcrawler extends `Monster` instead of `Spider` (avoids spider jockeys/effects on spawn); the model is the
  vanilla `SpiderModel`, so the visual result is the same.
- The hound's "calm down" only applies to scent hunts; once a player has hurt it, it fights like any neutral mob.
- Gloomcrawler flee threshold uses block light ≥ 7 at its own position as the "light source ≥ 9 within 6 blocks"
  proxy, plus any player holding a light item inside 6 blocks (cheap and avoids a 13³ block scan per tick).

Known limitations: brain-driven mobs (piglins, hoglins, wardens) ignore the decoy because they do not use
`Mob#setTarget`-based goals.

Gates: 3 ✔, 4 (logic, 26 tests) ✔, 1/2/5/6 not runnable here.

## M10a — Forgotten Land biomes

Built:
- The dimension's `multi_noise` source now has five biomes (temperature/vegetation noise only; terrain unchanged):
  Forgotten Plains (base), Cinderfrost Flats (T < −0.45), Whispering Marsh (humid), Clockwork Dunes (hot, dry),
  Inverted Falls (hot, humid). Per-biome surface rules, features, spawns, tints, particles and ambient sounds.
- **Forgotten Plains**: + Echo Birch groves (rarity 1/7 × 3) and Gloomcrawlers (weight 4).
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
  every 20 ticks living entities within 6 blocks glow for 60 ticks — reveals crouched Gloomcrawlers; skipped when no
  player is within 48 blocks; worldgen-placed mushrooms start their cycle on the first random tick), Lantern
  Moths (ambient 8) and Gloomcrawlers (monster 6). Client (config `marshFog`, SafeRun): fog far plane blends to 28
  blocks and non-relative sounds from beyond 16 blocks play at 40 % (a delegating `SoundInstance`, because
  `PlaySoundEvent` fires before the sound is resolved).
- **Clockwork Dunes**: sand over sandstone, `buried_watch` (tilted half-buried watch, drops 1–2 chronal shards,
  silk touch drops itself), `rusted_gear` (flat decorative gear), dead bushes, sparse Echo Birch, Clockwork Hounds
  (creature 3), quiet note-block "hat" ticking as the additions sound. Slow time: 50 % of `CropGrowEvent.Pre`
  results are `DO_NOT_GROW` in this biome (config `dunesSlowCrops`).
- Gloomcrawler spawn rule: dark-only monster rules in the Overworld, any light in the Forgotten Land (its sky is
  always lit, so the M9 rule could never pass there). Moths treat the Forgotten Land's fixed dusk as night.
- Validator: configured feature types (vanilla or registered in `ModFeatures`) and placement modifier types are
  checked now (negative-tested).
- GameTests: footprint set/fade, updraft lifts a pig, spore mushroom makes a Gloomcrawler glow and emits light 7,
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
  sampled every 5 ticks into a volatile `ScentTrail` (same class as the hound's trails) and interpolated
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
- `last_merchant`: extends `WanderingTrader` (vanilla goals, trading UI, despawn timer) rendered with the vanilla
  wandering-trader `VillagerModel` and a faded travelling-robe texture; entity type is `noSave` ("no persistent
  state"), so it is gone after despawn, chunk unload or restart. Spawn egg for testing.
- `MerchantSpawner` (server tick, Overworld): every `lastMerchantIntervalTicks` (default 72000 = 3 days, next attempt
  kept in `lacuna_last_merchant` SavedData) a 25 % chance by day (respects `doTraderSpawning`) to spawn 16–48 blocks
  from a random player on dry solid ground; despawns after 3 days; config `lastMerchant`.
- Stock (`logic/MerchantPricing`, unit-tested): buys the three ore families the nearest player mined most (raw iron,
  copper, gold, coal, redstone, lapis, diamond, quartz, raw ruby, raw umbral iron, zenithite) plus a bulk offer for the
  top one, and two ingot/gem buys; sells divine glass shards (2), rubies (3), chronal shards (8) and echo shards (10);
  8 % chance of one Solar Flare Fragment for 16 rubies + 24 divine glass shards (spec 14). "Recently mined" = vanilla
  `Stats.BLOCK_MINED` over `#c:ores` since the player's previous merchant visit (a `mined_snapshot` attachment, kept on
  death); the first visit uses lifetime totals.
- `CurrencyService`: every Lacuna trade asks it for the currency item (emerald until M11c installs the township coin).
  Sell prices take a price index (township caravan effect hooks in with M11d).
- GameTest: a merchant stocks ≥ 9 offers, sells the four goods for the currency and buys materials.

Deviation: no trader llamas (optional in the spec).

Gates: 3 ✔, 4 (logic, 34 tests) ✔, 1/2/5/6 not runnable here.
