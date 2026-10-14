# LACUNA — Master Build Spec for Claude Code

Target: **Minecraft 1.21.1 · NeoForge 21.1.x · Java 21**. Mod id: `lacuna`. Display name: **Lacuna**.
Zero external dependencies (no Mixins, no ASM, no libraries). Highest priority: **the game must never crash**, on client or server, during load, worldgen, play, save, or shutdown.

---

## 0. Kickoff message (paste into Claude Code after saving this file as `LACUNA_SPEC.md` in an empty project folder)

```
Read LACUNA_SPEC.md completely before writing any code. Then execute Milestone 0, and after
it every milestone in order (M1, M2, ...). After each milestone run ALL gates from section 4,
fix every failure, commit to git, and append to docs/PROGRESS.md (what was built, deviations,
known limitations). Do not ask me questions unless something blocks you completely; when the
spec is ambiguous, pick the documented default, note it in docs/PROGRESS.md, and continue.
Never guess a Minecraft/NeoForge API from memory: open the real 1.21.1 sources/jars
(run genSources / inspect the decompiled jar) and verify signatures first. Stability beats
features: if something cannot be made safe, use the fallback listed in the spec.
```

---

## 1. Mission and working rules

1. Build a content mod about magical items "beyond human comprehension": a pocket dimension (the Forgotten Land), light/shadow/time/space items, new materials and gear, four wood families, mobs, structures, and large civilized "Townships" (bigger villages).
2. Work in milestones (section 7). Each milestone ends **green** (all gates pass) and **committed**.
3. Every number in this spec is a default and must live in config or one `Balance` class, never scattered as magic numbers.
4. Prefer vanilla systems (data-driven JSON, vanilla models/renderers/particles/sounds, vanilla entity models with retextures) over custom code. Less custom code = fewer crashes.
5. Keep a `docs/PROGRESS.md` log and a `docs/TESTING.md` (manual client checklist for the human, with `/give` commands for every item and `/lacuna` debug commands).
6. If a feature is too risky after an honest attempt, ship the **fallback** written in that feature's section, behind a config flag, and log it in PROGRESS.md. Never leave a half-working feature enabled.

## 2. Tech stack and project setup

- Start from the official **NeoForge MDK** for 1.21.1 using **ModDevGradle** (`net.neoforged.moddev`). Use the **latest stable 21.1.x** NeoForge (look it up at `https://maven.neoforged.net/releases/net/neoforged/neoforge/`; 21.1.219 existed in Jan 2026, take the newest). Java 21 toolchain. Optional: Parchment mappings for readability.
- Group `com.lacuna`, base package `com.lacuna.mod`. License: All Rights Reserved. Version `0.1.0`.
- Gradle run configs required: `client`, `server`, `data`, `gameTestServer`. Enable `genSources`.
- `neoforge.mods.toml`: modId `lacuna`, dependency on `neoforge` and `minecraft` `[1.21.1]` only, `side = "BOTH"`.
- Tests: JUnit 5 for **pure logic classes** (no Minecraft types), NeoForge **GameTests** for in-world logic.
- Source layout:
  - `src/main/java/com/lacuna/mod/` (common code) with `client/` subpackage for everything that touches `net.minecraft.client`.
  - `src/main/resources/` for handwritten assets/data, `src/generated/resources/` for datagen output (committed).
  - `tools/` (Python 3 + Pillow): `gen_textures.py`, `gen_structures.py` (writes structure NBT with `nbtlib`), `validate_assets.py`, `smoke.sh`.

## 3. The stability contract ("no crashes")

**Registration**
- Everything registered with `DeferredRegister` in the mod constructor, using the mod event bus. No static registry access before registration.
- Block/item/entity/etc. ids are lowercase snake_case, unique, and equal across lang/models/loot/recipes (validated by `validate_assets.py`).

**Sides**
- No `net.minecraft.client` import in common classes. Client code only in `client/` and only registered from `@EventBusSubscriber(value = Dist.CLIENT)` handlers.
- Every handler that acts on the world checks `level.isClientSide()` and uses `ServerLevel`/`ServerPlayer` only on the server.
- Custom networking only where the spec says so, via `RegisterPayloadHandlersEvent` / `PayloadRegistrar` with a protocol version. Validate every payload on the server (distance, range, rate limits). Never trust client data.

**Data**
- 1.21 uses **data components**, not NBT, for item state. Every custom component has a `Codec` and `StreamCodec`, is registered, and has safe defaults.
- Persistent state: `SavedData` (with codec/factory) for world-level data, **data attachments** (serializable) for per-entity/per-player data. Always call `setDirty()`. Never rely on static mutable maps for persistent data; static maps are allowed only for explicitly volatile data and must be cleared on `ServerStoppedEvent`.
- Entities implement `addAdditionalSaveData`/`readAdditionalSaveData`. Temporary entities (Echo, Decoy, Reflection, Solar Ray) `discard()` themselves if loaded from disk without a valid owner/timer.

**World safety**
- Never call `getBlockEntity`, `getBlockState`, or `setBlock` on unloaded chunks: guard with `level.hasChunkAt(pos)` / `isLoaded`, or load the chunk explicitly **once** and bounded.
- Hard per-tick budgets: block changes (default 400/tick), entity queries (AABB ≤ 64 blocks per side), particles (≤ 40 packets/tick/effect). Long jobs (Sun's Fury) are spread over ticks.
- All teleports use a **safe-spot search** (section 6) and never place a player inside blocks, lava, or the void. Fall back to the origin position, then world spawn.
- Respect `mobGriefing`, `level.mayInteract`, world border, and build height in anything that destroys or places blocks.

**Defensive code**
- Risky systems (Divine Sight renderer, Banishment, Shadow Walk, Township logic, Echo/Decoy, scent tracking) run inside a `SafeRun` helper that catches `Throwable` (not `Error` of type OOM/StackOverflow), logs once with full stack trace and context, restores any partial state (e.g., return a banished player, restore gamemode), and **disables that feature for the rest of the session** instead of crashing. Registration code is NOT wrapped (it must fail loudly in dev).
- Null/empty/absent handling everywhere: components, attachments, `Optional`s, `Holder`s, missing registry entries (use `getOptional`, never `.get()` unchecked on user data).
- No reflection, no Mixins, no Access Transformers unless proven unavoidable (then document why and test it).

**1.21.1 API pitfalls (verify each against sources)**
- `ResourceLocation` constructor is private: use `ResourceLocation.fromNamespaceAndPath(...)` / `withDefaultNamespace(...)`.
- Datapack folders are **singular** in 1.21: `loot_table`, `recipe`, `structure`, `advancement`, `tags/block`, `tags/item`, `tags/entity_type`, etc. (worldgen folders unchanged).
- Damage types, painting variants, enchantments, biomes, dimensions, structures are datapack registries: custom damage sources need JSON (`data/lacuna/damage_type/*.json`) and a `ResourceKey`; get the `Holder` via `registryAccess()`.
- Armor uses registered `ArmorMaterial` holders; tools use `Tier` with `incorrectBlocksForDrops` tag; weapon stats via `ItemAttributeModifiers`.
- Dimension change: `Entity#changeDimension(DimensionTransition)`. For non-players it may return a **new entity instance**; always use the returned entity, never the old reference. Verify whether the UUID survives; if not, track by a serializable attachment id.
- `EntityTravelToDimensionEvent` is cancelable; our own transfers must bypass our own blocker with a scoped flag.
- Tree growers, wood types and block set types must be created before the blocks that use them (static init order). Verify how `WoodType`/`BlockSetType` are registered in 1.21.1.
- Signs/hanging signs/boats need extra block-entity wiring in 1.21.1: **excluded until M12** (see there).

## 4. Verification gates (run after EVERY milestone; all must pass)

1. `./gradlew build` succeeds with no warnings about missing registry entries.
2. `./gradlew runData` succeeds and running it **twice** produces no git diff (idempotent).
3. `python tools/validate_assets.py` passes: valid JSON; every registered item/block/entity has a lang key; every blockstate → model → texture path exists (mod or vanilla); every loot table/recipe/tag references existing ids; no stray `missingno` references; every texture is 16x16 (or documented size) with valid alpha.
4. `./gradlew test` (JUnit) passes.
5. `./gradlew runGameTestServer` passes all `lacuna` GameTests.
6. `bash tools/smoke.sh` passes: boots a dedicated server (`eula=true`, `--nogui`), creates a fresh world, runs a scripted console session (including `/lacuna selftest`, `/execute in lacuna:forgotten_land run forceload add 0 0 255 255`, `/place structure ...` for every structure added so far, `/locate` checks), saves (`save-all`), restarts the server on the same world, verifies state persisted, then `stop`. The log must contain **no** `ERROR`, `Exception`, `Failed to`, `Missing`, `Unknown registry`, or `Couldn't load` lines (an allow-list file `tools/log_allowlist.txt` may whitelist known-benign vanilla lines).
7. Keep `docs/TESTING.md` updated with the manual **client** checks for this milestone (the human runs `runClient`; Claude Code cannot see the GUI). Client-only code must still be exercised as far as possible via `validate_assets.py` and by making all renderers/models use vanilla classes.

Add a debug command tree `/lacuna` (permission level 2): `selftest`, `banish [targets]`, `return [targets]`, `rep get|set <player> <value>`, `township info`, `givekit` (gives one of every item), `scent`. `selftest` runs internal invariants (registries complete, recipes parse, coordinate math round-trips, tags non-empty) and logs a PASS/FAIL summary.

## 5. Conventions

- Registries in `registry/` classes: `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModEntities`, `ModComponents`, `ModAttachments`, `ModEffects`, `ModSounds` (vanilla events wrapped only), `ModCreativeTabs`, `ModVillagers` (professions/POI).
- Logic in feature packages: `banish/`, `beam/`, `eclipse/`, `solar/`, `pearl/`, `chrono/`, `mirror/`, `lantern/`, `flute/`, `orrery/`, `gear/`, `wood/`, `mob/`, `worldgen/`, `township/`, `client/`.
- Pure math/logic classes (no MC types) live in `logic/`: `CoordMath`, `BeamMath`, `ChargeMath`, `ReputationMath`, `PriceMath`, `ScentTrail`, each unit-tested.
- One creative tab "Lacuna" (icon: Totem of Banishing), grouped logically.
- Tooltips: every special item gets 1–2 lore lines (gray italic) plus functional lines (durability/charge/cooldown). Lang file `en_us` generated via datagen; add `it_it` in M12.
- Only vanilla `SoundEvents` and vanilla particles. No custom `.ogg`.
- Commit messages: `M<number>: <summary>`.

## 6. Global constants and config

`ModConfigSpec` (COMMON) + `docs/CONFIG.md`. Each feature has `enabled` (default true) and its numbers. Highlights:

- `forgottenLand.scale = 12.0` (**1 Forgotten Land block = 12 overworld blocks**; the overworld is 12x larger; going in divides by 12, coming out multiplies by 12).
- `forgottenLand.timerTicks = 7200` (6 min), `driftMin = 0.75`, `driftMax = 1.5`.
- All durabilities, cooldowns, damages, ranges in the milestone tables below.

**Coordinate translation (exactly like the Nether, with ratio 12 instead of 8)**
- Overworld → FL: `x' = x / 12`, `z' = z / 12` (floor toward negative infinity consistently; `CoordMath` with unit tests including negatives and the round-trip error ≤ 11 blocks).
- FL → Overworld: `x' = x * 12`, `z' = z * 12`.
- Set `coordinate_scale: 12.0` in the FL dimension type, but compute positions in `CoordMath` yourself and clamp to the destination world border.
- After converting, run the **safe-spot search**: (1) try the preferred Y (the remembered origin Y when returning; the surface when entering), (2) search a spiral up to 16 blocks horizontally and ±24 vertically for a 2-high free space with a solid, non-harmful floor (not lava/fire/cactus/magma/powder snow/water-over-void), (3) fall back to the surface via heightmap `MOTION_BLOCKING_NO_LEAVES`, (4) fall back to world spawn. Never place the player in an unloaded or ungenerated chunk without loading it first (bounded, once).
- Only Overworld ↔ Forgotten Land transfers exist. The Totem does nothing in the Nether/End (action bar message).

---

## 7. Milestones

| M | Content |
|---|---------|
| 0 | Project, tooling, gates, config, SafeRun, /lacuna commands, creative tab, texture pipeline |
| 1 | Ruby, Divine Glass, Divine Sight |
| 2 | Forgotten Land dimension, ruins, murals, Totem of Banishing, banishment system, relics |
| 3 | Divine Magnifying Glass |
| 4 | Umbral Iron (needed by Eclipser), Eclipser + Shadow Walk, Solar Flare, Sun's Fury |
| 5 | Fractured Pearl, Arrival/Departure shards, anchor block |
| 6 | Chronoshard Pocket Watch, Mirror of Recursion, Null Lantern, Whisperbone Flute + Echo, Orrery |
| 7 | Starlace, Zenithite, Umbral/Ruby tool sets, 5 special weapons |
| 8 | Four wood families (full sets), saplings, worldgen |
| 9 | Mobs: Lantern Moth, Gloomcrawler, Clockwork Hound (+ Silk Dust, Decoy) |
| 10 | FL biomes (5), Overworld/FL structures, Last Merchant caravan, Mirror Labyrinth |
| 11 | Townships (11a structures, 11b reputation/laws, 11c council/currency/courthouse, 11d guilds/contracts/schools) |
| 12 | Polish: Italian lang, advancements, optional signs/boats, optional TerraBlender, final QA |

---

## 8. M1 — Ruby and Divine Glass

**Ruby**
- Items: `ruby`, `raw_ruby`. Blocks: `ruby_ore`, `deepslate_ruby_ore`, `ruby_block`, `raw_ruby_block`.
- Ore drops 1 `ruby` (Fortune applies, like diamond), XP 3–7, needs iron tool or better. Hardness 3.0 (deepslate 4.5), blast resistance 3.0/3.0.
- Worldgen: vein size 4, 4 per chunk, trapezoid Y -64..16, air-exposure discard 0.5. Biome modifier adds to `#minecraft:is_overworld`.
- Ruby tier tools: sword, pickaxe, axe, shovel, hoe. Durability 800, speed 7.0, enchantability 15, mining level like diamond; damage bonuses like diamond tier. Repair with `ruby`.
- Recipes: ruby block ⇄ 9 ruby; raw variants; smelt/blast `raw_ruby` → `ruby`.

**Divine Glass**
- Items: `divine_glass_shard`. Blocks: `divine_glass` (4 shards 2x2 → 1 block), `divine_glass_pane` (6 blocks → 16 panes), `divine_glass_cluster` (natural crystal cluster, like amethyst cluster, drops 2–4 shards, Silk Touch drops the cluster, Fortune +1 per level; spawns only in the Forgotten Land, see M2/M10).
- Divine Glass block: fully transparent, `noOcclusion`, does not suffocate or block view, light level 7, hardness 0.5, blast resistance 6, drops itself (needs Silk Touch like glass? NO: always drops itself so it is obtainable without tricks). Faces between adjacent Divine Glass blocks are culled (like vanilla glass). Render layer: translucent. Optional animated shimmer texture (`.mcmeta`, 8 frames, subtle).
- **Divine Sight (the "X-ray" mechanic). Client-side only, bounded.** When the camera ray (from the eye along the view direction) passes through a Divine Glass or Divine Glass Pane block within 6 blocks, everything within `divineSight.range` (default 12) blocks *behind that glass inside a 50° cone* is X-rayed: blocks in tag `lacuna:divine_sight_targets` (default: `#c:ores`, spawners, chests, barrels, buried treasure blocks) and living entities get a colored **outline box rendered without depth test** (ores gold, chests white, mobs red, passive green). Implementation rules:
  - Scan on a throttle (every 10 ticks), max 4096 block positions per scan, only already-loaded client chunks, cache results, max 128 box outlines + 32 entity boxes. Render in `RenderLevelStageEvent` (after translucent blocks) with `LevelRenderer.renderLineBox`/line render types.
  - Entire feature wrapped in `SafeRun` (client): on any exception disable Divine Sight and log once.
  - Config: `divineSight.enabled`, `.range`, `.maxBoxes`. Keep the outline renderer generic (`OutlineRenderer` + `OutlineSet`) because the Sunken Bell Tower (M10) reuses it through a server payload.
  - *Fallback if rendering proves unstable*: Divine Glass remains a plain transparent block and the Sunken Bell reveal uses the same renderer; document it.

## 9. M2 — Forgotten Land, Totem of Banishing, banishment, relics

### 9.1 The dimension `lacuna:forgotten_land`
- **Scale:** `coordinate_scale: 12.0`. Overworld is 12x larger. See section 6 for the math.
- Dimension type JSON: `has_skylight: true`, `has_ceiling: false`, `natural: false` (beds/anchors do not work), `ultrawarm: false`, `ambient_light: 0.1`, `fixed_time: 9000`, `min_y -64`, `height 384`, `logical_height 384`, `infiniburn #minecraft:infiniburn_overworld`, `effects: minecraft:overworld`, `monster_spawn_light_level 0`, `piglin_safe: false`, `has_raids: false`. Verify every field against the 1.21.1 codec.
- **Look (no client code needed):** each biome's `effects`: foggy **gray-blue tint**: `fog_color 0x8C9DB0`, `sky_color 0x7F90A6`, `water_color 0x5E7F9C`, `water_fog_color 0x4A667F`, no clouds feel via low contrast, ambient `minecraft:ash` particle at low probability in some biomes, ambient mood/additions sounds from vanilla events (soft, eerie).
- **Terrain:** flat but not totally flat: custom `noise_settings` (or a tuned copy) with gentle low-frequency hills, **surface height between Y 62 and Y 80**, sea level 63, small ponds, no deep ravines. Verify by generating at least 600 chunks (`forceload`) with no errors and checking `/locate` + a height sampling GameTest. Biomes come from a `multi_noise` biome source (datapack JSON, no code): base biome `lacuna:forgotten_plains` plus four regional biomes defined in M10.
- **Ruins you cannot mine:** blocks `ruin_stone`, `ruin_stone_bricks`, `cracked_ruin_stone_bricks`, `ruin_pillar` (axis block), `chiseled_ruin_glyph`, `ruin_slab`, `ruin_stairs`, `ruin_wall`. All: `destroyTime -1`, blast resistance 3,600,000, `PushReaction.BLOCK`, not replaceable by fluids/pistons/endermen, tag `lacuna:ruin_blocks`, **unbreakable in survival and adventure, breakable in creative**. Visual: weathered pale blue-gray stone with faint carved glyphs.
- **Ruin structures** (jigsaw or single templates, `terrain_adaptation: beard_thin`, rare sets with `spacing 14 / separation 6` in chunks, plus a denser "ruin cluster" set): `broken_arch`, `fallen_colonnade`, `ruined_hall`, `sunken_plaza`, `memory_shrine` (contains the murals), `obelisk_ruin`. Generated by `tools/gen_structures.py` with deterministic seeds. Contain chests with `lacuna:chests/ruin_cache` and `divine_glass_cluster` features nearby.
- **Rare "pictures of what it once was" = Memory Murals.** Datapack painting variants `lacuna:memory_dawn` (2x2), `memory_spire` (3x2), `memory_harvest` (2x2), `memory_festival` (3x2), `memory_fall` (2x2) with pixel-art textures generated by `gen_textures.py` (scenes of a bright civilized city/sky, in warm full color, contrasting the gray land). Add them to `minecraft:placeable` so they can be hung. Place them in `memory_shrine` templates (entity entries in structure NBT). *Fallback if template entities misbehave:* `mural_block_1..5` (full blocks with the same pictures) placed in the templates instead.
- **Rules inside the Forgotten Land**
  - No natural hostile spawns (biome spawners empty except the mobs listed in M9/M10).
  - **You cannot leave**: cancel `EntityTravelToDimensionEvent`, ender pearls, chorus fruit, Departure Shards (see M5), Chronoshard cross-dimension rewinds. Commands run by operators are never blocked. The only exits are the timer expiring, the Totem used from inside, or death (respawn follows vanilla rules to the overworld).
  - Sun's Fury and the Orrery's locate do not work inside; Divine Magnifying Glass does.
  - Bed/respawn anchor use is blocked (message).

### 9.2 Totem of Banishing
- Item `totem_of_banishing`: hand-shaped totem with a red ruby (texture: weathered pale stone hand with a faceted ruby in the palm). Stack 1. **Durability 16**; 1 durability per transfer (in or out). **Cooldown 2400 ticks (2 min)** shared across uses. Repair: combine with `ruby` at an anvil (+25% per ruby; item+item merging and enchanting disabled).
- Recipe (shaped): `RGR / GTG / .R.` where R = `ruby`, G = `gold_ingot`, T = `totem_of_undying` (no Divine Glass needed, because shards come from the Forgotten Land).
- **Right click (self):** if in the Overworld → enter the Forgotten Land; if in the Forgotten Land → return to the Overworld (early exit). Sound: `ENDERMAN_TELEPORT`-like + `PORTAL_TRAVEL` fade, particles `REVERSE_PORTAL`.
- **Hit (melee) another living entity:** strong knockback (strength 1.5) and the target is **banished** to the Forgotten Land (same translation rules). Excluded by tag `lacuna:cannot_be_banished` (Ender Dragon, Wither, Warden, Elder Guardian, all `lacuna:` bosses, ender-related bosses, players if `banish.allowPlayers=false`; default players allowed only when PvP is enabled). Costs 1 durability, triggers the cooldown.
- **Timer:** default 6 min = `7200` ticks × hidden **drift factor** rolled per banishment in [0.75, 1.5] ("time drift" of the land: you experience a variable stay). Action bar countdown updates each second: `Forgotten Land — returning in 5:42`. Banished mobs return when their own timer ends.
- **Return location:** the translated overworld position of where the entity currently stands in the FL (`x*12`, `z*12`), Y preferring the remembered origin Y, then the safe-spot search. A banished player also gets short slow falling + brief invulnerability (40 ticks) on arrival.
- **Persistence (must survive logout, crash, restart):** `BanishmentSavedData` maps `banishmentId` → `{originDimension, originPos, returnGameTime, driftFactor, entityUuid, isPlayer}`. Store `banishmentId` in a **serializable data attachment** on the entity (so it survives dimension change even if the UUID/instance changes). On player login, server start, and every 20 ticks, check expiry and return anyone due. If a banished mob no longer exists, remove its record. If a banished mob dies, remove its record (relic rules do not apply to mobs).
- Edge cases to handle explicitly: player logs out while banished (timer keeps counting on game time; on login return if expired), player dies in FL, server stops mid-banishment, target teleports/dies/despawns, leashed or riding entities (dismount/unleash first), two banishments at once, creative/spectator players, dimension missing from datapack (feature disables itself with a message instead of crashing).

### 9.3 Relics (death in the Forgotten Land)
- On a player death in the FL with `keepInventory` false: instead of vanilla drops, the dropped stacks are spawned in the **Overworld at the translated death position** (`×12`, safe-spot search; load that chunk once, bounded). Each `ItemEntity`: invulnerable to fire/explosion, **extended lifetime (≈30 min)**, glowing outline, owner-only pickup for 10 minutes, and a **visible hovering name "Unreadable Relic"** (custom name visible) so the real item name stays hidden until picked up. Chat message to the player: "Your belongings were scattered across the overworld near X Z" with coarse coordinates (rounded to 100).
- *Fallback if the overworld chunk cannot be loaded safely:* drop normally in the FL.

## 10. M3 — Divine Magnifying Glass

- Item `divine_magnifying_glass`: gold ring, Divine Glass lens, brown handle. Stack 1, **durability 500**, **1 durability per 20 ticks of beam**. Recipe: `.G. / GDG / .S.` with D = `divine_glass` block, G = `gold_ingot`, S = `stick`.
- **Hold right click** to channel (use duration 72000, `UseAnim.BOW`-style or NONE, no slowdown). Server each tick: ray from eye up to **24 blocks** (`ClipContext.Block.COLLIDER` for solids, a second clip with fluids to detect water). Stops at the first solid block.
- **Damage:** base 12 damage/second to the first living entity hit, applied as 3.0 every 5 ticks, custom damage type `lacuna:solar_beam` (tag `minecraft:is_fire`, does not bypass armor), sets the target on fire for 4 s.
- **Modifiers (multiply, defined in `BeamMath`, unit-tested):**
  - **Daytime & surface light** (default): ×1.0, **yellow ray** (`0xFFD23C`).
  - **Underwater** (eyes in water): damage ×0.4, range ×0.5, **blue ray** (`0x3C8CFF`), no ignition, bubble particles.
  - **Night** (`!level.isDay()` in dimensions with a sky, or sky light 0 and no daylight elsewhere): damage ×0.5, **white ray** (`0xFFFFFF`).
  - Underwater and night multiply (×0.2, blue).
  - In the Forgotten Land (fixed afternoon time) the beam counts as day.
- **Block interaction (not underwater, at most every 10 ticks, respects `mayInteract`/permissions):** ignites flammable blocks (placing fire adjacent to the hit face where `BaseFireBlock.canBePlacedAt`), melts snow/snow layers/ice/packed ice (ice becomes water only if allowed by `lacuna:not_meltable`), ignites TNT through fire, `LARGE_SMOKE` where it burns.
- **Water vapor:** when the (fluid-aware) ray hits water: `CLOUD` + `POOF` particles and `FIRE_EXTINGUISH` sound every 5 ticks. Config `beam.evaporateWater` (default false): if true, may remove flowing water.
- Visual beam: server `sendParticles` with `DustParticleOptions` (verify the 1.21.1 constructor) along the ray, one every 1.0 block every 2 ticks, capped; no custom renderer.
- After releasing: 10-tick cooldown. Breaking at 0 durability stops the beam cleanly. Not enchantable; repair at an anvil with `divine_glass_shard` (+60 durability each).

## 11. M4 — Umbral Iron, Eclipser, Sun's Fury

### 11.1 Umbral Iron (needed by the Eclipser; full tool set in M7)
- `raw_umbral_iron`, `umbral_iron_ingot`, `umbral_iron_nugget`, `umbral_iron_block`, `raw_umbral_iron_block`, `umbral_iron_ore`, `deepslate_umbral_iron_ore`. Ore generation: Y -64..0, vein size 6, 3 per chunk, hardness 4.0, needs iron tool. Smelt/blast raw → ingot; 9 nuggets ⇄ ingot; 9 ingots ⇄ block.
- Palette: dark slate-blue with thin violet veins (section 20).

### 11.2 Eclipser
- Item `eclipser`: black sword, texture very close to a vanilla sword shape, black blade (`#0B0B10`) with red edge pixels and a red crossguard gem. Stack 1. **Durability 250**. Attack damage 7 (including base), attack speed 1.6.
- Recipe (shaped): `.U. / RUR / .S.` with U = `umbral_iron_ingot` (×2 total in the blade column), R = `ruby` (×2 crossguard), S = `stick`.
- **Charge (0–100):** +10 per melee hit that damages a living entity (max once per 5 ticks, not on invulnerable/dead targets). Charge **resets to 0 when the wielder takes damage**, decays 2 per second after 8 s without hitting. Stored as a data component. Visual: model override predicate `lacuna:charge` with 4 textures (0, 1–33, 34–66, 67–100: red pixels spread from the edge, at max the blade outline glows red with a white core). Action bar shows `Eclipse 70%`.
- **Dash (right click, charge ≥ 20):** consumes all charge. Distance `d = 4 + 0.10 × charge` blocks (max 14, config), along the look vector. Every living entity within 1.5 blocks of the path takes **damage `6 + 0.14 × charge`** (max 20) once (custom damage type `lacuna:eclipse`). Durability cost `5 + 15 × charge/100` (rounded), **cooldown 400 ticks**. Dash stops before solid blocks unless Shadow Walk allows passing.
- **Shadow Walk** (`lacuna:shadow_walk` mob effect, 2 s at 20 charge up to 6 s at 100: `40 + 80 × charge/100` ticks): "almost spectator". **Implementation (robust, desync-free): temporarily switch the player to `GameType.SPECTATOR`**, remembering the previous mode in an attachment, and restore it when the effect ends. Required safety: restore on effect expiry, on death, on logout (`PlayerLoggedOutEvent`), on login if the flag is still set, on `ServerStoppingEvent`, and on dimension change; never override if an operator changed the player's gamemode meanwhile. On restore, if the player is inside solid blocks, move to the nearest safe spot within 16 blocks, else back to the dash origin. Mobs lose targeting on the player during the effect. It works in every dimension, including the Forgotten Land (the restore step always uses the safe-spot search).
- Not enchantable (except Unbreaking); repair with `umbral_iron_ingot`.

### 11.3 Solar Flare and Sun's Fury
- Items: `solar_flare_fragment` (very rare), `solar_flare`, `suns_fury`.
- **Fragment sources (all deliberately rare):** `ruin_cache` chests 1.5% per roll, Mirror Labyrinth hoard 10%, Last Merchant 8% chance to stock one (price 16 `ruby` + 24 `divine_glass_shard`, one per merchant).
- **Solar Flare recipe (shaped):** `.F. / FDF / .F.` with F = fragment (4), D = `divine_glass` block.
- **Sun's Fury recipe (shaped):** `GFG / RNR / .S.` with F = `solar_flare`, G = `gold_block`, R = `ruby_block`, N = `netherite_ingot`, S = `stick`. **Single use** (stack 1, consumed on throw, not in Creative).
- **Throw:** right click → `SunsFuryEntity` projectile (speed 2.5, slight gravity 0.03), detonates on block/entity impact or after 10 s. Cannot be used in the Forgotten Land (fizzles, item kept).
- **Detonation:** (1) if in the Overworld, set time to the **next noon** (never backwards; keep day count) and clear weather; (2) spawn `SolarRayEntity` at the impact point lasting 60 ticks: a bright golden particle column (END_ROD/FLAME/dust, radius 2, height 80, capped particles) plus `FLASH` at start; (3) entities within radius 7: 25 damage per second (5 every 5 ticks) and fire 10 s; (4) **block destruction** within a cylinder of radius 6 (config), from 8 blocks below to 40 above the impact: destroy blocks with hardness ≤ 50 and not bedrock, not `lacuna:solar_immune` (ruin blocks, bedrock, end portal frame, command blocks), no drops, sweep outward in rings, **max 400 block changes per tick and 12,000 per use**; only if `mobGriefing` is true and `mayInteract` passes, otherwise only damage. Sound: low-volume `GENERIC_EXPLODE` + `LIGHTNING_BOLT_THUNDER`.
- Safe against crash: all block loops guarded by `hasChunkAt`, budgeted per tick via an in-world job queue stored in the entity (discarded if loaded from disk without state).

## 12. M5 — Fractured Pearl system

- Items: `fractured_pearl`, `arrival_shard`, `departure_shard`. Block: `arrival_anchor` (+ block entity optional).
- **Fractured Pearl recipe (shaped):** `DRD / RPR / DRD` with D = `divine_glass_shard`, R = `ruby`, P = `ender_pearl` → 1 pearl (rare). Stack 1.
- **Right click the pearl:** it splits into a matched pair: one `arrival_shard` ("right half") and one `departure_shard` ("left half"), both carrying the same random **`link_id`** (data component). Both shards can be dropped, traded, or given to friends.
- **Arrival Shard** (durability 10, throw cooldown 30 s): right click throws like an ender pearl/snowball (`ArrivalShardEntity`, speed 1.5, gravity 0.03; **no damage and no teleport to the thrower**). On hitting a block, it **locks**: places an `arrival_anchor` block in the air block against the hit face (if not placeable, drop the shard back as an item), registers `link_id → {dimension, pos}` in `PearlLinkSavedData`, stays forever (the anchor never despawns, `blast resistance 1200`, immune to fluids). On hitting an entity or flying into the void/unloaded chunk it returns as an item drop. Throwing a shard whose link already has an anchor first removes the old anchor (re-lock = move). Breaking the anchor by a player drops the arrival shard (link entry removed, shard reusable); if the anchor is destroyed any other way (creative `/setblock`, etc.) the record is cleaned on next validation.
- **Departure Shard** (durability 5, cooldown 15 s): **right click does not throw anything**; it teleports the user to the linked anchor's location at that moment (anchor pos + 0.5/0/0.5, safe-spot check; chunk loaded once, bounded). Works only **within the same dimension** as the anchor and **never from/to the Forgotten Land crossings**; if the link is missing/invalid/other dimension: message "The shard hums, but nothing answers." and **no durability cost, no cooldown**. Teleport effects: `PORTAL_TRAVEL`-like sound, `PORTAL` particles at both ends. Passengers are dismounted first. Leashes break.
- Repair: arrival and departure shards with `ender_pearl` (+3 durability) at an anvil.
- Item tooltip shows `Linked: ABCD-1234` (first 8 hex of the id) and "Cracked" when the link is invalid.
- Required tests: link round-trip, give-to-friend (different player uses departure), anchor broken, server restart persistence, two pairs not interfering, cross-dimension refusal.

## 13. M6 — Utility items

All have durability bars as stated and the shared repair rules (anvil with the listed material; no item+item merging).

**Chronoshard Pocket Watch** (`chronoshard_pocket_watch`, durability 12)
- Recipe: `.R. / GCG / .D.` R = `ruby`, G = `gold_ingot`, C = `clock`, D = `divine_glass_shard`.
- While in the player's inventory (main/offhand) it records a snapshot every 2400 ticks into a ring of **3** snapshots (component): `{dimension, position, yRot, xRot, health, food, gameTime}` (**no inventory snapshot**, to avoid duplication exploits).
- **Right click:** rewinds to the newest snapshot that is at least 400 ticks old **only if it was taken in the player's current dimension and that dimension is not the FL↔Overworld boundary** (rewinding never crosses dimensions). Restores position (safe-spot), `health = max(current, snapshot)`, food/saturation, clears fire and harmful effects of the last 10 s; consumes that snapshot and any newer ones; spends 1 durability.
- **Chronal Shards** (`chronal_shard`, found in Clockwork Dunes `buried_watch` blocks and rare ruin chests): right click the watch with a shard in the other hand to insert (max 5, component). **Cooldown = 6000 − 600 × shards** ticks (5 min → 2.5 min).
- Effect: particles `REVERSE_PORTAL` at the old and new place; no echo entity.

**Mirror of Recursion** (`mirror_of_recursion`, durability 20)
- Recipe (shaped): `QDQ / DGD / QDQ` with Q = `amethyst_shard`, D = `divine_glass_shard`, G = `gold_ingot`.
- **Hold right click** (use anim like a shield): while raised, any projectile in the tag `lacuna:reflectable` (arrows, spectral arrows, tridents, fireballs, small fireballs, blaze/shulker bullets, etc.; excluded by `lacuna:unreflectable`: ender pearls, wither skulls, dragon fireballs, potions, fishing hooks, eggs/snowballs) that would hit the front 180° arc is **cancelled and returned to its source** (velocity toward the shooter's position, owner set to the player), costing 1 durability.
- Each reflected projectile is also **stored** (max 3, component: `{entityTypeId, snapshot of damage-relevant fields, reuseCount}`). **Sneak + right click** re-fires the oldest stored projectile along the look vector with power multiplier `0.85 ^ (reuseCount+1)`; when `reuseCount` reaches 3 the stored echo "destabilizes" (shatters with particles, nothing fires). Arrows get their base damage scaled; other projectiles fire unscaled but still count reuse.

**Null Lantern** (`null_lantern`, durability 300)
- Recipe: `.U. / ULU / .D.` U = `umbral_iron_ingot`, L = `soul_lantern`, D = `divine_glass_shard`.
- While held in either hand: hostile mobs within 16 blocks cannot **target** the player (cancel `LivingChangeTargetEvent`), unless the player attacked them in the last 5 s or they are within 3 blocks; natural hostile spawns within 24 blocks of the holder are blocked (`MobSpawnEvent.PositionCheck`). Burns 1 durability per 200 ticks held. No light emission requirement.
- Does not affect bosses (tag `lacuna:cannot_be_banished`).

**Whisperbone Flute** (`whisperbone_flute`, durability 40)
- Recipe: `.B. / BEB / .B.` B = `bone`, E = `echo_shard`.
- Use (goat-horn style animation, cooldown 1200 ticks): summons up to **3 Echoes** of mobs that **died within 20 blocks in the last 1200 ticks**, using a per-level in-memory ring buffer (max 64 entries) fed by `LivingDeathEvent` (non-player, non-boss). Echo = entity `lacuna:echo`: translucent retextured zombie/humanoid model, health `min(20, deadMobMaxHealth)`, attack `min(6, deadMobAttack)`, speed 0.3, lifetime **200 ticks**, targets what its owner attacks or is hurt by, immune to its owner, no XP/loot, not saved (discarded on load). On expiry it drops one `echo_fragment`.
- `echo_fragment` repairs the flute (+20 durability).
- If no valid deaths: message "The bones stay silent." (no cost).

**Orrery of Thresholds** (block `orrery_of_thresholds`, no durability)
- Recipe: `DKD / RCR / SSS` D = `divine_glass_shard`, K = `compass`, R = `ruby`, C = `clock`, S = `smooth_stone_slab`. Decorative pedestal with rings (static model; optional slow-rotating rings via block entity renderer **only** in M12 polish).
- Right click (5 s cooldown per player, no GUI): action bar shows the overworld ⇄ Forgotten Land coordinate translation for the player's position (`÷12` / `×12`) and the **direction and distance** to the nearest structure in structure tag `lacuna:orrery_targets` (Obelisk, Sunken Bell Tower) within `orrery.radiusChunks` (default 48; use `ServerLevel#findNearestMapStructure`, run server-side, rate-limited globally to avoid lag spikes). Inside the Forgotten Land the locate part is skipped.

## 14. M7 — Gear

**Starlace** (armor, new `ArmorMaterial`): `zenithite_shard` (from Zenithite ore, Inverted Falls islands) + 2 `string` (shapeless) → `starlace_cloth` ×2. Helmet/chestplate/leggings/boots from cloth with vanilla armor shapes. Durability factor 24, defense 2/6/5/2, toughness 0.5, enchantability 18, repair `starlace_cloth`. Armor layer textures `textures/models/armor/starlace_layer_1/2.png` (64x32).
- **Starlace Boots glide:** while falling with `fallDistance > 4` and sneaking, apply `slow_falling` for 80 ticks once per fall, 200-tick cooldown, costs 1 durability. Server-side only; no motion hacking.
- `zenithite_ore` block (stone/deepslate), `zenithite_shard` item, `zenithite_block`.

**Ruby tool set** (M1) and **Umbral Iron tool set** now: sword, pickaxe, axe, shovel, hoe. Umbral (stronger than Ruby): durability 1500, speed 8.5, damage bonus 4 (sword 8 / axe 10 vs Ruby 7 / 9), enchantability 15, netherite-level mining; **bonus +25% damage dealt (any Umbral tool/weapon) when the wielder stands where `getMaxLocalRawBrightness ≤ 4`**. **Nightforged** (all five tools): +30% mining speed while the light at the eyes is ≤ 7. **Umbral Rend** (sword): the 3rd consecutive hit on a target within 4 s detonates for +6 armor-piercing damage (`lacuna:umbral_rend`), heals the wielder 2, Slowness II + Darkness 2 s. **Eclipse Cleave** (axe): fully charged hits (strength ≥ 0.9) send a shadow shockwave: other mobs within 3 blocks of the target take 60% of the hit (`lacuna:eclipse_cleave`) and are knocked back (owner pets, villagers, player-made golems and, without PvP, players are spared).

**Five special weapons** (all shown with the use bar; none enchantable except Unbreaking; repair material listed):

1. **Gravemaw Greatsword** — durability 700, attack damage 11, attack speed 0.8, repair `umbral_iron_ingot`. Recipe `UUU / URU / .S.` (U umbral ingot, R ruby, S stick). **Hold right click while standing still (movement < 0.05)** to charge up to 40 ticks; releasing at ≥ 20 ticks fires a **crescent wave**: instant arc of 8 blocks, ±35°, damage `10 + 0.3 × chargeTicks`, knockback 1.0, particles `SWEEP_ATTACK` arcs at three distances, 3 durability, cooldown 120 ticks. Moving cancels the charge.
2. **Tidecaller Sickle** — durability 450, attack damage 4, speed 2.2, repair `prismarine_shard`. Recipe (shaped) `.PP / .SP / S..` (P = `prismarine_shard`, S = `stick`). **Right click (cooldown 100):** hooks the first living non-boss entity within 12 blocks and pulls it toward you (velocity 1.2, up to 8 blocks), 3 damage, 2 durability. If you are in water/rain when the hook lands you gain a **Tide Charge** (max 3, component); each charge is consumed by your next melee hit to **heal 2 HP**.
3. **Heartwood Bow** — durability 480, repair `echo_birch_planks`. Recipe (needs M8) `.PS / PRS / .PS` (P echo birch planks, S string, R ruby). Arrows fired apply the `lacuna:rooted` effect (movement speed −100%, jump strength −100%) for 2 s, **4 s and "pinned" particles if a solid wall is within 1.5 blocks behind the target along the arrow direction**. Every hit grows **Grasping Roots** (`lacuna:grasping_roots`, no item/drops, instant break, no collision, cobweb-like hold + Rooted while inside): in the target's feet block and its 4 neighbours (6 s), or a patch of 3-5 where an arrow hits a block (10 s); only into air/short grass/fern/thin snow above a sturdy face, never into fluids; they wither (1 s dry stage) and crumble.
4. **Echo Dagger** — durability 300, attack damage 3, speed 2.6, repair `echo_shard`. Recipe `.E. / .D. / .S.` (E echo shard, D divine glass shard, S stick). Each hit schedules **one echo hit after 10 ticks** at 60% damage using damage type `lacuna:echo_strike`; echo hits never trigger further echoes.
5. **Pyrrhic Scepter** — durability 250, repair `gold_ingot`. Recipe `.R. / .B. / .G.` (R ruby, B blaze rod, G gold ingot). **Right click (cooldown 20):** spends **2 HP** (cannot cast at ≤ 4 HP) and fires a **homing bolt** (`PyrrhicBoltEntity`, speed 1.2, homing toward the nearest valid hostile in a 30° cone within 24 blocks, turn rate 0.15). Damage `5 + (1 − health/maxHealth) × 10` (5–15), sets fire 2 s, 1 durability. Pyrrhic bolt has no custom renderer (particle trail only).

Projectile entities (Sun's Fury, Arrival Shard, Pyrrhic bolt, Decoy throw) use vanilla `ThrownItemRenderer` or a no-op renderer with particles. No custom entity models for projectiles.

## 15. M8 — Four wood families (full vanilla-like behavior)

Families: **Cindervine**, **Crystal Weeping Willow**, **Tidebloom Mangrove**, **Echo Birch**. Each family includes: `log`, `stripped_log`, `wood`, `stripped_wood`, `planks`, `stairs`, `slab`, `fence`, `fence_gate`, `door`, `trapdoor`, `pressure_plate`, `button`, `leaves`, `sapling`, `potted_sapling`. Datagen for models/blockstates/lang/loot/recipes/tags.

**Required behavior (same as vanilla trees)**
- **Bark can be removed with an axe** (`ItemAbilities.AXE_STRIP`): `log → stripped_log`, `wood → stripped_wood`, for every family. Implement via NeoForge's data-driven mechanism if available in 21.1.x (check data maps), otherwise via a `getToolModifiedState` override in a `LacunaLogBlock extends RotatedPillarBlock`. Test with a GameTest per family.
- Logs/planks/leaves flammability and fuel values like vanilla (use data maps or block overrides; verify), **Cindervine wood is fire-proof but a fuel (planks 400 ticks)**.
- Saplings grow with bone meal and random ticks, drop from leaves (5%, standard sapling table, plus sticks 2%), can be placed in pots, composted (30%/leaves 30%).
- Leaves decay normally, `persistent` when placed.
- Doors/trapdoors/buttons/plates/fence gates use proper `BlockSetType`/`WoodType`; verify sounds and activation. **No signs, hanging signs or boats yet** (M12 optional).
- Creative tab and tags: `lacuna:` and vanilla `logs`, `planks`, `wooden_*`, `leaves`, `saplings`, `mineable/axe`, `logs_that_burn` (not Cindervine), etc.

**Family character**
- **Cindervine:** charcoal-black bark with ember-orange cracks, logs light level 4; leaves smolder (rare `SMALL_FLAME`/`SMOKE` particles) and break into 5% `ember` (item, fuel 1600 ticks via data map); tall gnarled hollow-looking trunk.
- **Crystal Weeping Willow:** translucent pale green-white wood (log/planks render cutout/translucent, light level 5), leaves are glassy shards (translucent), drop `crystal_willow_shard` (5%; 4 shards → `glass`); hanging `crystal_willow_strands` block (cross-model plant hanging under leaves, survives only when attached above) generated by the tree feature.
- **Tidebloom Mangrove:** red-teal mangrove-like tree using the vanilla `mangrove` configured-feature structure (root placer + upwards branching trunk) with our blocks; `tidebloom_roots` block (waterloggable) whose break releases `SPLASH`/`BUBBLE` particles and which has a 1% random tick chance to spawn a cod or salmon in adjacent water if fewer than 3 fish nearby.
- **Echo Birch:** pale ivory bark with faint blue rune lines; **custom `SoundType` built from vanilla events (amethyst/chime)** so chopping echoes; **paired saplings:** if another Echo Birch sapling is within 4 blocks (same Y ±1) when one grows, both grow immediately and a **bridge of leaves** connects the two canopies at canopy height (replace only air).

**Worldgen (no TerraBlender):** configured/placed features per family, added by **biome modifiers** to overworld biomes: Echo Birch rare in `#minecraft:is_birch_forest`-style biomes (and in FL biomes, M10), Cindervine in savanna/badlands/windswept, Crystal Weeping Willow rare in cherry groves/meadows, Tidebloom Mangrove in swamps/mangrove swamps/rivers. Rarity `1/24`–`1/40` per chunk so vanilla trees are unaffected; everything also available as saplings from leaves.

## 16. M9 — Mobs (reuse vanilla models with retextures wherever possible)

For every mob: attributes via `EntityAttributeCreationEvent`, `SpawnPlacementRegisterEvent`, spawn eggs, loot table, lang, tags, sounds from vanilla events, renderer registered client-side only.

- **Lantern Moth** (`lantern_moth`): passive flyer (use `FlyingMoveControl` + `FlyingPathNavigation` like the vanilla Bee), 6 HP, small, **vanilla `BeeModel` retextured** (pale yellow-white glowing wings) at scale 0.8. Goal: hover near the nearest light source (block light ≥ 12, within 16 blocks) or a player holding an item in `lacuna:moth_attractor` (torches, lanterns, glowstone, candles, `null_lantern` excluded). Spawns at night in forests/flower forests/swamps (weight 8, groups 1–3) and in the Whispering Marsh. Drops 0–2 `silk_dust`.
- **Silk Dust** (`silk_dust`): right click a block face places a **glow decal** block `silk_dust_glow` (non-solid, light 9, a thin faint yellow sparkle, waterlogging not required) that **removes itself after 3600 ticks** via a scheduled tick (re-schedule safely; if the chunk reloads the scheduled tick persists). Decoration only. Stack 64.
- **Gloomcrawler** (`gloomcrawler`): ambush monster, 22 HP, 4 damage, **vanilla `SpiderModel` retextured and scaled 0.8**. Behavior: while idle in/next to tall grass, ferns, bushes or leaves, it is **crouched (smaller hitbox, near-invisible texture alpha pass)**; when a non-sneaking player is within 4 blocks it **leaps** (`LeapAtTargetGoal` + melee). **It retreats from light:** a custom goal flees toward darker positions (block light ≤ 4) whenever a light source ≥ 9 is within 6 blocks, and it will not attack players holding a lit light item inside 6 blocks of it. Spawns in dark forests/swamps/taigas at low light (monster spawn rules, weight 6) and in the Forgotten Plains/Marsh. Drops 0–2 `string`, 5% `umbral_iron_nugget`.
- **Clockwork Hound** (`clockwork_hound`): neutral desert tracker, 30 HP, 5 damage, speed 0.33, **vanilla `WolfModel` retextured** (brass/rust). Uses a **scent trail**: a volatile `ScentTracker` stores each player's position every 200 ticks for the last 3600 ticks (max 18 points/player, cleared on logout and server stop, never saved). With no target, a hound within 48 blocks of a trail point follows the trail forward (oldest to newest) and targets the player when within 16 blocks. Calms down if the player is in water or sneaking inside 8 blocks (breaks the trail for that point). Spawns in deserts/badlands (weight 3) and Clockwork Dunes. Drops 1–3 `clockwork_gear`, 4% `chronal_shard`.
- **Clockwork Decoy** (item `clockwork_decoy`, shapeless: 3 `clockwork_gear` + 1 `redstone` + 1 `white_wool`): throw → `DecoyEntity` (humanoid, retextured Armor-Stand-like or zombie model, 20 HP, no AI movement besides standing) lasting **15 s**; on spawn, hostile mobs within 16 blocks currently targeting the owner (or without targets) retarget to the decoy. Cooldown 400 ticks.
- **Echo** (`echo`, from M6 flute) and **Reflection** (M10) and **Last Merchant** (M10) share the "no persistent state" rule.

## 17. M10 — Forgotten Land biomes, structures, merchant

**Forgotten Land biomes** (all in `multi_noise` JSON; terrain stays the same gentle noise settings; each biome has its own surface rules, features, spawns, and tinted `effects` as in 9.1):

- `forgotten_plains` (base): dense ruins, ruin cache chests, `divine_glass_cluster` patches, murals, Echo Birch groves.
- **Cinderfrost Flats** (cold, high temperature negative): gray **ashen snow** block `ashen_snow_block` as the surface (with blockstate `footprint` 0–3: stepping on it sets 3 and a scheduled tick reduces it every 100 ticks so dark **footprints fade**), `minecraft:ash` ambient particles, **translucent ice** `clear_ice` (nearly transparent, ice-like) ponds over buried ruins so ruins are visible underneath, Cindervine trees (rare, bare dead spire shapes optionally), no hostile spawns.
- **Inverted Falls**: floating **stone islands** (custom simple `Feature` generating squashed ellipsoids at Y 110–150 with grass/moss top, `zenithite_ore` veins, `divine_glass_cluster` and Crystal Weeping Willows); **upward waterfalls via `updraft_spring` blocks**: a block (with ticking block entity, server every 4 ticks) that gives an upward velocity (+0.08/tick up to 0.4) to entities inside a vertical column up to 40 blocks (stops at the first solid block), plus client `animateTick` rising `CLOUD`/`BUBBLE_POP`/`FALLING_WATER` particles. **No custom fluids.** Placed at pool basins under islands. Cap work per spring (AABB query only for the column).
- **Whispering Marsh**: shallow water, Tidebloom Mangroves, `spore_mushroom` blocks (light 7, spore particles; a scheduled tick every 20 ticks applies `glowing` for 60 ticks to living entities within 6 blocks, thereby **revealing hidden mobs like Gloomcrawlers**), Lantern Moths, Gloomcrawlers. **Fog & muffled sound (client-only, optional, SafeRun):** `ViewportEvent.RenderFog` brings the far plane to ~28 blocks while the camera is in this biome, and client `PlaySoundEvent` lowers volume of sounds originating beyond 16 blocks to 40%.
- **Clockwork Dunes** (hot, dry): sand dunes, `buried_watch` blocks (half-buried pocket watch decor; drop 1–2 `chronal_shard`), `rusted_gear` decorative blocks, Clockwork Hounds, sparse Echo Birch. "Slow time": **crop growth ×0.5** in this biome (cancel 50% of `CropGrowEvent.Pre`) and ambient quiet `NOTE_BLOCK_HAT`-like ticking via biome additions sound (vanilla event, low probability).

**Structures (all `data/lacuna/structure/*.nbt` generated by script, datapack structure + structure_set + tags + loot, tested with `/place structure`)**
- **Sunken Bell Tower** (oceans/deep oceans): half-submerged tower, 9x9 base, ~26 tall. Block `sunken_bell` (right click): cooldown 60 s per player, sends `RevealOresPayload(positions≤64, durationTicks=160)` containing ore blocks within 24 blocks (scanned server-side with a bounded scan, only loaded chunks) which the client renders with the shared `OutlineRenderer`; plus a bell sound and expanding ring particles. Loot chest in the base.
- **Obelisk of Unreadable Names** (rare, any land biome in the Overworld, and `obelisk_ruin` in FL): 3x3 base, 9 tall black pillar, block `obelisk_inscription`: right click shows one of 24 lore lines (lang keys, chosen by position hash) in chat; **12% chance of a small reward** (1–3 `ruby`, or 1–2 `divine_glass_shard`, or 1 `chronal_shard`), limited to once per player per obelisk (`SavedData` set of `(playerUuid, pos)`), 20 min block cooldown.
- **Mirror Labyrinth** (Forgotten Land, rare, `maze` of mirror-glass walls `mirror_glass` (tinted-glass look with silver edge), 25x25 footprint, 5 deterministic variants generated by `gen_structures.py`): when a player enters the central ring trigger (a pressure-style `labyrinth_trigger` block), a **Reflection** spawns (`reflection` entity: retextured zombie/humanoid dark silver, 20 HP, contact damage 6, speed 0.28) whose movement **replays the player's own path from 60 ticks earlier** (uses the same volatile position history as the Hound's `ScentTracker`, but 3-second resolution of 5 ticks). Killing it removes the exit wall (`labyrinth_exit` blocks → air with particles) and unlocks the **Mirror Hoard** chest (`lacuna:chests/mirror_hoard`: divine glass shards, rubies, chronal shard, 10% solar flare fragment). The Reflection discards itself if the player leaves 48 blocks or logs out.
- **Caravan of the Last Merchant**: a wandering trader style entity `last_merchant` (retextured `VillagerModel`, retextured trader llamas optional), spawned by a custom world-tick spawner like the vanilla wandering trader (default every ~3 days, 25% chance at day, near a random player in the Overworld, despawns after 3 in-game days). **Trades in mined materials, not emeralds:** buys raw ores/ingots/ruby/umbral iron and sells (before M11 the currency is `emerald` as a placeholder, behind a single `CurrencyService` interface; M11c swaps it for `township_coin`) `divine_glass_shard`, `ruby`, `chronal_shard`, `echo_shard` at fair prices. **Stock reflects what the nearest player mined recently** (read vanilla `Stats.BLOCK_MINED` for ores; the top mined ore type gets an extra "bulk buy" offer). 8% chance of one `solar_flare_fragment` offer. If a Last Merchant dies within 128 blocks of a Township (M11), that township's price index rises (M11d). Never persists after despawn.

## 18. M11 — Townships (bigger, cooler, more civilized villages)

Vanilla villages stay untouched; **Townships are a new structure family** that generates in village-like biomes (plains, savanna, taiga, snowy plains, desert), rare, large, walled, with districts and civic life. Split into four sub-milestones with gates after each. **Do M11 only after M0–M10 are green.**

### 18.1 M11a — Structures (no gameplay logic yet)
- Structure type `minecraft:jigsaw`, `size 12` (vanilla villages use 6), `max_distance_from_center 128`, `terrain_adaptation: beard_thin`, `project_start_to_heightmap: WORLD_SURFACE_WG`, five biome variants (`township_plains`, `_desert`, `_savanna`, `_taiga`, `_snowy`) using vanilla block palettes of the matching village style.
- **Core piece** `township_core` (about 61x61 footprint, built once by `gen_structures.py`): a **stone wall ring** with towers and **four gates** (gate blocks defined in 18.2), central **Monument Plaza** with a statue of the founder (3 statue designs, each with a **sign plaque** carrying a short lore text, vanilla sign NBT), **fountain fed by an aqueduct** (stone arches carrying a water channel from outside, a decorative piece placed by the jigsaw), **Bell Tower** (vanilla `bell`), **Council Hall** (`council_table` block), **Courthouse** (`judge_bench` block), **Guild Hall** (`guild_board` block + three guild banners), **Library/School** (bookshelves, lecterns, `school_desk` block), and the `township_ledger` block in the center plaza.
- **District lots** are jigsaw connectors with names `lot_market`, `lot_residential`, `lot_workshop`, `lot_civic`, and pools of 6–10 buildings each: market stalls (with villagers), 2–3 story houses with beds (POI), workshops (job-site blocks of all vanilla professions), granary/orchards/public gardens with crops and apple trees, stables, a tavern. Streets are paved with `dirt_path`/`cobblestone`/`stone_bricks` (no new road block). Outside each gate: paved roads (jigsaw street pool) leading outward with farms/orchards.
- Building templates are produced programmatically by `gen_structures.py` (writing gzipped NBT `structure/*.nbt`, `DataVersion` correct for 1.21.1, valid palette/blocks/block entities/jigsaw blocks with `name/target/pool/joint/final_state`). Required checks: every pool references existing pieces and `minecraft:empty` fallbacks; `/place structure lacuna:township_plains` works in at least 20 different seeds/positions with no errors, generation finishes within a sane time, and `/locate structure #lacuna:townships` works.
- Spread: `structure_set` with `spacing 34`, `separation 12` chunks (rarer than vanilla villages), salt unique, not generating inside vanilla villages.

### 18.2 M11b — Ledger, reputation, laws, bell, gates
- `township_ledger` block (+ block entity with a persistent `townshipId` UUID; registers its township in `TownshipSavedData` the first time it loads or ticks: lazy, idempotent). Record: `{id, center, radius, seed, laws[2], coinValue 1.0, priceIndex 1.0, policy, policyEndDay, guilds[], reputation: Map<UUID,int>}`. Right click the ledger: chat summary (reputation tier, laws, current policy, next council day, coin value).
- **Reputation** −100..+100 per player per township; tiers: **Hostile** (< −50), **Wary** (−50..−10), **Neutral** (−9..9), **Friendly** (10..49), **Honored** (≥ 50). `ReputationMath` is pure and unit-tested. Changes: +1 per completed trade with a township villager (cap +2/day), +3 per hostile mob killed within 32 blocks of the ledger (cap +10/day), −20 killing a villager/guard, −10 hurting one, −5 breaking blocks tagged `lacuna:township_protected` (walls, gates, road blocks, doors) (cap −15/day), −1 per mature crop harvested in township farmland unless **Honored**, −3 for opening containers (chests/barrels) in township buildings with a villager within 12 blocks (unless Honored, and only a small cap/day) — a stand-in for theft.
- **Effects:** villager trade price multiplier `1 − rep/400` (±25%) times township `priceIndex` (apply via `MerchantOffer` special price on interaction; verify safe API); **Hostile**: guards (iron golems of the township) attack; **Honored**: free crop harvesting, gates always open.
- **Laws** (each township gets 2 at generation, chosen by seed): (1) **No drawn weapons inside the walls** (holding a sword/axe/bow/crossbow/trident gives a warning at −0, then −1 rep per 10 s and guards approach), (2) **Gates close at dusk** (strangers with rep < 10 are escorted out/blocked), (3) **No breaking blocks inside the walls** (extra −2), (4) **The bell rings only at dawn** (see bell). Enforcement is rep penalties + guard targeting, never instant death, with a 3 s warning in the action bar.
- **Bell:** hook right clicks on vanilla `bell` blocks inside a township's radius (`PlayerInteractEvent.RightClickBlock`, no cancel): ringing during dawn (ticks 0–2000) gives +2 rep once per in-game day; more than 3 rings per day gives −5 rep ("annoyed"); if law 4 is active, ringing outside dawn gives −3.
- **Gates:** `township_gate` block (2 wide, 3 tall, custom `Block` with `OPEN` state): opens at dawn (day time 23000–12000), closes at dusk via scheduled ticks that re-schedule safely (if chunk unloaded they resume on load via `onPlace`/`tick` hooks), always open for Honored players. Not breakable in survival by non-creative players unless sneaking with an axe (then −5 rep).

### 18.3 M11c — Council, currency, courthouse
- **Council** (every 7 in-game days at dawn, inside the ledger tick): picks one **policy** for 7 days from: `Free Market` (villager prices ×0.90), `Austerity` (×1.10), `Festival` (reputation gains ×2, fireworks/particles at the plaza), `Martial Law` (guards stronger: +50% damage, laws enforced twice as fast). Weighted by the **average reputation** of players near the township in the last week (higher average → more Free Market/Festival).
- **Currency:** item `township_coin`. A **Coiner** villager (new profession, job block `mint_press`) exchanges raw ore/ingots (iron, gold, copper, ruby, umbral iron) for coins at a rate scaled by the township's `coinValue`. **Coin value** (0.6–1.4) drops 2% per 16 ore delivered and recovers +1% per day back toward 1.0. `CurrencyService` replaces the emerald placeholder used by the Last Merchant. Coin prices of all township-specific trades are multiplied by `1/coinValue`.
- **Courthouse:** `judge_bench` block: right click with `township_coin` in hand pays fines: each coin restores +5 reputation (up to 0); with an empty hand shows the player's standing and fine. Cooldown 3 in-game days between pardons. A **Magistrate** villager profession (job block `judge_bench`) exists with a couple of coin trades.
- Both new professions: register `PoiType` + `VillagerProfession` + trades (`VillagerTradesEvent`) carefully; verify POI/profession registration order.

### 18.4 M11d — Guilds, contracts, schools, caravan effects
- **Guilds** (three: **Smiths'**, **Gemcutters'**, **Scholars'**): join at the `guild_board` (right click with 20 `township_coin`, requires rep ≥ 10, one guild per player, stored in a player attachment). Members can trade with their guild's **Guildmaster** villager (profession job block `guild_board`, the villager's guild type in an attachment; non-members get "Guild members only" and the GUI does not open): Smiths' sell `umbral_iron_ingot` and repair kits; Gemcutters' sell `ruby` and rare `divine_glass_shard`; Scholars' sell `chronal_shard`, `echo_shard`, enchanted books. Prices in coins.
- **Guild contracts:** right click the board: you receive one `guild_contract` item (component `{itemId, count, rewardCoins, townshipId}`); handing it in at the board with the required items pays coins and +3 rep. One active contract at a time.
- **Schools/libraries:** at each in-game dawn, for up to 40 villagers within the township radius that are near a `school_desk`/lectern in the Library/School (within 24 blocks), add 10 villager XP once per day (cap level 3 via natural leveling rules). Cheap, bounded loop.
- **Caravan effect:** a killed Last Merchant within 128 blocks raises that township's `priceIndex` by +0.2 (max 1.5), decaying −0.03/day.
- **Gardens:** within township farmland, harvest rules as in 18.2.
- Everything in 18.x is behind `township.enabled` and each subfeature flag; every event handler uses `SafeRun`; township logic only runs for loaded chunks and the ledger loop is O(villagers in radius ≤ 40).

## 19. M12 — Polish and optional items

- `it_it` language file (datagen), tooltips polish, advancements tree (root "Lacuna": first ruby, enter the Forgotten Land, survive a banishment, forge the Eclipser, throw Sun's Fury, link a Fractured Pearl, find a Township), `README.md` for players.
- Optional (only if stable): **signs/hanging signs/boats** for the four wood families (carefully wiring block entity types and entity types; keep behind `wood.extras` config), Orrery ring animation via a block entity renderer, emissive eyes layers for mobs, TerraBlender overworld biome injection for the five Forgotten Land biomes as a **separate optional milestone** (add the dependency only if the mod still passes every gate with it).
- Final QA: 30 min dedicated server soak with a scripted bot-free console (random `/lacuna` actions, `/time`, `/place` across dimensions), memory/tick profiling note in PROGRESS.md, then a release build `./gradlew build` and `docs/CHANGELOG.md`.

## 20. Asset style guide and texture list (generated by `tools/gen_textures.py`, deterministic, seeded)

**Style rules:** 16x16, Minecraft vanilla-like pixel art, 3–5 tone ramps per material, selective dark outline, light from top-left, no anti-aliasing, transparent background for items, no pure black (`#000000`) except Eclipser edges (use `#0B0B10`), no more than ~20 distinct colors per texture. Block textures tile where needed (verify tiling with a test image). Armor layers 64x32, paintings (2x2 = 32x32, 3x2 = 48x32), entity textures matching the vanilla model UV sizes they reuse (wolf 64x32, spider 64x32, bee 64x64, zombie 64x64, villager 64x64).

**Palettes**
- Ruby: `#4A0716 #8E0F2A #D6143F #FF5C7A #FFC2CC`.
- Divine Glass: white-gold `#FFF7D6 #FFE9A0 #FFD35C` with prism fringe pixels `#B8E8FF #FFC8F0`, translucent blocks alpha 150–210.
- Totem of Banishing: stone `#6E6A64 #8C8780 #B2ACA2` hand shape, ruby palm.
- Magnifying Glass: gold `#8A5A10 #D49A22 #F2C94C`, lens `#FFF6C8` with highlight, handle `#5A3A1C`.
- Eclipser: blade `#0B0B10 #1A1A22 #2B2B38`, edge `#B3122E #E03A52`, charge states add red then white-hot core `#FFF0E0`.
- Sun's Fury: spear shaft `#5A3A1C`, head `#FFB000 #FF7A00 #FFF2A0` with a flame wisp.
- Solar Flare: orange orb `#FF6A00 #FFB000 #FFF2A0` with flame tips; Fragment: shard of the same.
- Fractured Pearl: `#1E3A44 #2E6A78 #7FE8E0` with a bright crack line; **Arrival Shard (right half)** teal `#2E6A78/#7FE8E0`; **Departure Shard (left half)** violet `#4A2E78/#B99BFF`.
- Umbral Iron: base `#1E2233 #2E3350 #444B73`, veins `#7B4DFF #9B6BFF`, highlight `#D8C8FF`.
- Starlace: `#8FA3C4 #B8C7E0 #E8F0FF` weave with white star pixels.
- Zenithite: `#2A6FA8 #4FA8E8 #9FE0FF #E8FAFF`.
- Ruin stone: `#5E6670 #7D8691 #A3ADB8` with glyph grooves `#454C55`; fog tint reference `#8C9DB0`.
- Cindervine wood: `#17120F #2A1D16 #4A2A18` with ember cracks `#FF6A1A #FFB04A`.
- Crystal Willow wood: `#BFE8D8 #E6FFF4 #FFFFFF` translucent; leaves `#9FD9C4 #D8FFF0` alpha ~200.
- Tidebloom Mangrove: bark `#5A2A32 #8A3A48`, leaves `#2E8A78 #4FC7A8`.
- Echo Birch: bark `#E8E2D0 #CFC8B0` with blue rune lines `#6FA8E8`, leaves `#BFD8E8 #E8F4FF`.

**Item/model rules:** tools/weapons use `item/handheld` (and `item/handheld_rod`-style where suited); items with states use `overrides`/predicates registered with `ItemProperties` client-side (safe, SafeRun). Block models via datagen (cubes, cross, slabs, stairs, fences, doors, trapdoors, etc.). Every texture named after its registry id.

**Texture list (must all exist and pass `validate_assets.py`):** all items and blocks in this spec (including four wood families, murals, ruin stone family, ashen snow with 4 footprint variants, clear ice, mirror glass, buried watch, rusted gear, spore mushroom, updraft spring, Sunken Bell, Obelisk parts, arrival anchor, orrery, township blocks, silk dust glow, decal), mob/entity textures (lantern moth, gloomcrawler, clockwork hound, echo, decoy, reflection, last merchant), armor layers, spawn eggs (color pairs), and mob effect icons (`shadow_walk`, `rooted`, 18x18).

## 21. Tags, damage types, loot (checklist)

- **Damage types:** `solar_beam` (is_fire), `eclipse`, `echo_strike`, `solar_ray` (is_fire, bypasses nothing special), `pyrrhic_bolt`, `gravemaw_wave`, `reflection_touch`, all with messages in lang.
- **Tags:** `lacuna:cannot_be_banished`, `lacuna:unreflectable`, `lacuna:reflectable`, `lacuna:solar_immune`, `lacuna:ruin_blocks`, `lacuna:township_protected`, `lacuna:moth_attractor`, `lacuna:divine_sight_targets`, `lacuna:not_meltable`, structure tags `lacuna:orrery_targets`, `lacuna:townships`; add Lacuna items/blocks to the right vanilla/common tags (`c:ores`, `c:gems`, `c:ingots`, `minecraft:swords`, `minecraft:pickaxes`, `mineable/*`, `needs_*_tool`, `logs`, `planks`, `leaves`, `saplings`, etc.).
- **Loot tables:** all blocks (ores, clusters, leaves with sapling/ember/shard drops), mobs, chests `lacuna:chests/ruin_cache`, `mirror_hoard`, `bell_tower`, structure chests, fishing not needed.
- **Recipes:** all in this spec, generated by datagen with correct 1.21.1 JSON (`result.id`/`count`, ingredient objects) and recipe unlock advancements (hidden is fine).

## 22. Definition of done

- Every gate in section 4 passes on the final commit; `docs/PROGRESS.md`, `docs/TESTING.md`, `docs/CONFIG.md`, and `README.md` exist.
- A fresh world: loads, enters/leaves the Forgotten Land repeatedly (including logout/login/restart while banished), generates 600+ FL chunks and 20+ Townships without errors, survives `save-all` + restart + `stop`.
- No raw stack traces in logs, no unhandled exceptions, no missing textures/models/lang keys, no `missingno`.
- Any feature that fell back to a safer variant is listed in PROGRESS.md "Fallbacks and known limitations".

## 23. Out of scope (do NOT build)

Custom fluids, custom GUIs/screens (use chat/action bar), shaders, custom sound files, Mixins/ASM/reflection, external libraries, multi-loader support, signs/boats before M12, inventory snapshot rewind, cross-dimension Fractured Pearl links, TerraBlender before M12, hidden-name tooltips for relics beyond the hovering "Unreadable Relic" name.
