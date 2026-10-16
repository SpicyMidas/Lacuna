# Lacuna configuration (`config/lacuna-common.toml`)

Every feature has an `enabled` switch; numbers default to the values in `com.lacuna.mod.Balance`.
Registration-time numbers (durability, attack damage/speed, stack sizes) are not configurable because configs
load after registration; they live in `Balance`.

| Section | Key | Default | Meaning |
|---|---|---|---|
| general | blockChangesPerTick | 400 | Max block changes per tick for long jobs (Sun's Fury) |
| general | debugLogging | false | Extra logging |
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
| sunsFury | enabled / destructionRadius / maxBlockChangesPerUse | true / 6 / 12000 | Sun's Fury |
| sunsFury | changeTime | true | Set time to next noon + clear weather |
| items | fracturedPearl, pocketWatch, mirrorOfRecursion, nullLantern, whisperboneFlute, orrery | true | Utility items |
| items | nullLanternRadius / orreryRadiusChunks | 16 / 48 | |
| gear | specialWeapons / starlaceGlide | true / true | |
| gear | starlaceAbilities | true | Starlace set: Constellation Step (mid-air jump, full set), Starfall (fall damage halved at 2+ pieces; full set: falls over 5 blocks land as a starlight shockwave), Starlit (+5 % speed per piece at night under open sky / in the End) |
| gear | umbralDarkBonus | 0.25 | Extra damage fraction for Umbral gear in darkness |
| wood | extras | false | Reserved for signs/boats (not implemented) |
| wood | tidebloomFish | true | Tidebloom roots spawn fish |
| forgottenBiomes | marshFog, dunesSlowCrops, updraftSprings, mirrorLabyrinth, lastMerchant, sunkenBell | true | |
| forgottenBiomes | lastMerchantIntervalTicks | 72000 | ~3 in-game days |
| township | enabled, laws, council, currency, guilds | true | Townships and their subsystems |

## Client (`config/lacuna-client.toml`)

| Section | Key | Default | Meaning |
|---|---|---|---|
| divineSight | mode | XRAY | XRAY: Divine Glass is a window onto a void showing only ores, containers, spawners and mobs; OUTLINE: coloured boxes through walls (automatic fallback when the stencil buffer is unavailable or the window fails) |
| divineSight | xrayRange / xrayMaxBlocks | 28 / 1024 | X-ray window: target search radius around the camera, max target blocks drawn |
