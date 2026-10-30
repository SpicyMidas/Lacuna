# Lacuna configuration (`config/lacuna-common.toml`)

Every feature has an `enabled` switch; numbers default to the values in `com.lacuna.mod.Balance`.
Registration-time numbers (durability, attack damage/speed, stack sizes) are not configurable because configs
load after registration; they live in `Balance`.

| Section | Key | Default | Meaning |
|---|---|---|---|
| general | blockChangesPerTick | 400 | Max block changes per tick for long jobs (Sun's Fury) |
| divineSight | enabled / range / maxBoxes | true / 12 / 128 | Divine Sight through Divine Glass (range/maxBoxes: outline mode) |
| forgottenLand | enabled | true | Totem of Banishing, timers, dimension rules |
| forgottenLand | scale | 12.0 | 1 Forgotten Land block = 12 overworld blocks |
| forgottenLand | timerTicks | 7200 | Base banishment duration (6 min) |
| forgottenLand | driftMin / driftMax | 0.75 / 1.5 | Hidden per-banishment time drift factor range |
| forgottenLand | banishAllowPlayers | true | Players can be banished (also requires server PvP) |
| forgottenLand | relicsEnabled | true | Deaths in the Forgotten Land scatter Unreadable Relics in the overworld |
| beam | enabled / range / damagePerSecond | true / 24 / 12 | Divine Magnifying Glass |
| beam | evaporateWater | false | Beam may remove flowing water |
| beam | affectsBlocks | true | Ignite flammables, melt snow/ice |
| eclipser | enabled / dashMaxDistance / dashCooldownTicks | true / 14 / 400 | Eclipser dash |
| eclipser | shadowWalk | true | Spectator-based Shadow Walk |
| sunsFury | enabled / destructionRadius / maxBlockChangesPerUse | true / 6 / 24000 | Sun's Fury |
| sunsFury | changeTime | true | Set time to next noon + clear weather |
| sunsFury | focusTicks | 100 | Ticks between the landing of Sun's Fury and the strike (the sun climbs to noon meanwhile) |
| items | fracturedPearl, pocketWatch, mirrorOfRecursion, nullLantern, whisperboneFlute, wayfarersChart | true | Utility items |
| items | nullLanternRadius | 16 | |
| items | wayfarersChartBudgetMicros | 2000 | Server time all held Wayfarer's Charts together may spend sampling the Overworld per tick, in microseconds (the chart never loads or generates chunks; a smaller budget only fills new land in more slowly) |
| gear | specialWeapons / starlaceGlide | true / true | |
| gear | starlaceAbilities | true | Starlace set: Constellation Step (mid-air jump, full set), Starfall (fall damage halved at 2+ pieces; full set: falls over 5 blocks land as a starlight shockwave), Starlit (+5 % speed per piece at night under open sky / in the End) |
| gear | umbralDarkBonus | 0.25 | Extra damage fraction for Umbral gear in darkness |
| wood | tidebloomFish | true | Tidebloom roots spawn fish |
| forgottenBiomes | marshFog, dunesSlowCrops, updraftSprings, mirrorLabyrinth, lastMerchant, sunkenBell | true | Marsh fog; half-speed crops in the Clockwork Dunes; Updraft Springs; the Labyrinth's Reflection fight (off: the trigger opens the exit at once); Last Merchant visits; Sunken Bell ore reveal (off: it only rings) |
| forgottenBiomes | lastMerchantIntervalTicks | 72000 | ~3 in-game days |
| villages | enabled, laws, council, currency, guilds, civicBuildings | true | The civic system in vanilla villages (reputation, laws, council, coins, guilds, schools) and its subsystems; civicBuildings adds the village hall, mint, guild hall and schoolhouse to newly generated villages |
| starfall | naturalStars | true | On clear nights in the overworld a star now and then falls 24-64 blocks from a player (never the first night, never in villages, structures or near a base), leaving a molten Star Rock (it cools to Prime Star Rock, then burns out after about three days) |
| starfall | naturalChancePerNight | 0.3 | Chance per player and per clear night (at most one star per player per night) |
| starfall | starlaceBandage | true | Starlace Bandages mummify creatures (shears always unwrap them) |
| shadowPossession | enabled | true | While shadow walking, clicking a creature or player possesses it (see its point of view and steer it until it fights back and casts you out); off: clicking only spectates, as in vanilla |
| shadowPossession | players | true | Players can be possessed too (also requires PvP on the server) |
| almanac | giveOnFirstJoin | true | Every player gets a Lacuna Almanac (the in-game guide) the first time they join a world; the flag lives in the player's persisted data, so turning it off and on again never gives a second one |

## Client (`config/lacuna-client.toml`)

| Section | Key | Default | Meaning |
|---|---|---|---|
| effects | screenEffects | true | Camera shake, flashes and screen tints from Lacuna items (also scaled by the vanilla Distortion Effects slider) |
| solar | sunFlare | true | While Sun's Fury focuses or strikes, the sun flares in the sky and the fog warms |
| forgottenLand | fogDistance / marshFogDistance | 24 / 14 | Fog far plane (blocks) in the Forgotten Land / in the Whispering Marsh (lower = thicker) |
| forgottenLand | mist | true | Drifting mist wisps near the ground in the Forgotten Land |
| eclipser | afterimages | true | Dark afterimages of the dashing player along the Eclipser dash |
| divineSight | mode | XRAY | XRAY: Divine Glass is a window onto a void showing only ores, containers, spawners and mobs; OUTLINE: coloured boxes through walls (automatic fallback when the stencil buffer is unavailable or the window fails) |
| divineSight | xrayRange / xrayMaxBlocks | 28 / 1024 | X-ray window: target search radius around the camera, max target blocks drawn |
| mirror | reflections | true | Mirror Glass and the Labyrinth Seal show a faint, tinted reflection of you (first person included), players, creatures, dropped items, projectiles, minecarts, boats, armour stands, falling blocks and other entities, plus a soft sheen on every mirror face on screen; needs the stencil buffer (off without it) |
| mirror | range | 32 | Reflection range in blocks (8-64): mirrors this close to the camera reflect, and entities up to this far in front of a mirror show in it |
| mirror | maxPlanes | 16 | Mirror planes (a floor, a ceiling, each wall...) reflecting entities at the same time (1-48), largest on screen first; the others keep the sheen. Each entity is drawn once per mirror orientation per frame, however many planes show it |
