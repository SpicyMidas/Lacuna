# Lacuna

A NeoForge 1.21.1 content mod about magic "beyond human comprehension": the Forgotten Land pocket dimension,
light/shadow/time/space relics, new materials and gear, four wood families, mobs, ruins and Townships.

- Spec: [`LACUNA_SPEC.md`](LACUNA_SPEC.md) · Progress: [`docs/PROGRESS.md`](docs/PROGRESS.md) ·
  Config: [`docs/CONFIG.md`](docs/CONFIG.md) · Manual tests: [`docs/TESTING.md`](docs/TESTING.md)

## Building

```
./gradlew build            # compile + jar (needs access to maven.neoforged.net and Mojang servers)
./gradlew runClient        # play
./gradlew runGameTestServer
bash tools/smoke.sh        # dedicated-server smoke test
```

Assets and data are produced by Python 3 tools (`pip install -r tools/requirements.txt`):

```
python3 tools/gen_textures.py      # PNG textures (deterministic)
python3 tools/gen_resources.py     # models, blockstates, lang, loot, recipes, tags, worldgen
python3 tools/gen_structures.py    # structure NBT templates
python3 tools/validate_assets.py   # consistency checks
```

License: All Rights Reserved.
