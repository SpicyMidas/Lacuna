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
