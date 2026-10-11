# Lacuna configuration (`config/lacuna-common.toml`)

Every feature has an `enabled` switch; numbers default to the values in `com.lacuna.mod.Balance`.
Registration-time numbers (durability, attack damage/speed, stack sizes) are not configurable because configs
load after registration; they live in `Balance`.

| Section | Key | Default | Meaning |
|---|---|---|---|
| general | blockChangesPerTick | 400 | Max block changes per tick for long jobs (Sun's Fury) |
| general | debugLogging | false | Extra logging |
| divineSight | enabled / range / maxBoxes | true / 12 / 128 | Client X-ray through Divine Glass |
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
| gear | umbralDarkBonus | 0.25 | Extra damage fraction for Umbral gear in darkness |
| wood | extras | false | Reserved for signs/boats (not implemented) |
| wood | tidebloomFish | true | Tidebloom roots spawn fish |
| mobs | scentTracking / gloomcrawlerAmbush | true / true | |
| forgottenBiomes | marshFog, dunesSlowCrops, updraftSprings, mirrorLabyrinth, lastMerchant, sunkenBell | true | |
| forgottenBiomes | lastMerchantIntervalTicks | 72000 | ~3 in-game days |
| township | enabled, laws, council, currency, guilds | true | Townships and their subsystems |
