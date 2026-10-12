# Lacuna

*A NeoForge 1.21.1 mod about the things the world has forgotten.*

Lacuna adds a pocket dimension made of everything nobody remembers, relics of light, shadow, time and space, new
materials and gear, four wood families, strange creatures, ruins and structures, and **Townships** — walled towns
with laws, a council, a currency and guilds. No dependencies besides NeoForge; English and Italian included.

## Where to start

1. **Ruby** ore (deep, like diamonds) and **Divine Glass** clusters. Craft the **Totem of Banishing** (gold, rubies,
   a Totem of Undying).
2. Use the totem to enter the **Forgotten Land**, a gray, quiet world where every block is 12 Overworld blocks. Its
   ruins cannot be mined; their chests can be looted. Strike a mob with the totem to banish it for a while. You leave
   when your time runs out, by using the totem again, or by dying.
3. Explore its biomes: **Cinderfrost Flats** (ash snow that keeps footprints, ice over drowned ruins), **Inverted
   Falls** (floating islands with zenithite, upward springs), **Whispering Marsh** (fog, spore mushrooms, moths and
   gloomcrawlers), **Clockwork Dunes** (buried watches, slow time, clockwork hounds). Find the **Mirror Labyrinth**
   and beat your own Reflection for its hoard.
4. Back home: the **Divine Magnifying Glass** (a beam of focused sunlight), **Umbral Steel** and the **Eclipser**,
   **Sun's Fury**, the **Fractured Pearl** (two linked teleport shards), the **Chronoshard Pocket Watch**, **Mirror of
   Recursion**, **Null Lantern**, **Whisperbone Flute** and the **Orrery of Thresholds**, which points to Obelisks
   and Sunken Bell Towers.
5. Visit a **Township** (plains, desert, savanna, taiga, snowy biomes). Read its ledger: keep the laws, trade, kill
   monsters near it and your reputation grows; mint ore into coins, pay fines at the courthouse, join a guild and
   take contracts. The Last Merchant sometimes comes by with a cart full of what you mined.

The advancement tab "Lacuna" follows this path. Every number is in `docs/CONFIG.md`; most features can be switched
off in `config/lacuna-common.toml`.

## For developers

- Spec: [`LACUNA_SPEC.md`](LACUNA_SPEC.md) · Progress, deviations and limitations: [`docs/PROGRESS.md`](docs/PROGRESS.md)
  · Config: [`docs/CONFIG.md`](docs/CONFIG.md) · Manual tests: [`docs/TESTING.md`](docs/TESTING.md)
  · Changes: [`docs/CHANGELOG.md`](docs/CHANGELOG.md)

```
./gradlew build            # compile + jar (needs access to maven.neoforged.net and Mojang servers)
./gradlew runClient        # play
./gradlew runGameTestServer
bash tools/smoke.sh        # dedicated-server smoke test (tools/smoke_phase1.txt / phase2.txt)
```

Assets and data are produced by Python 3 tools (`pip install -r tools/requirements.txt`), deterministically:

```
python3 tools/gen_textures.py      # PNG textures
python3 tools/gen_resources.py     # models, blockstates, lang (en_us, it_it), loot, recipes, tags, worldgen, advancements
python3 tools/gen_structures.py    # structure NBT templates (ruins, towers, labyrinths, 170 township pieces)
python3 tools/validate_assets.py   # consistency checks (models, textures, lang, tags, loot, worldgen, jigsaws)
```

Pure logic (coordinates, beams, charges, scent trails, reputation, council, pricing) is unit-tested with JUnit 5.

License: All Rights Reserved.
