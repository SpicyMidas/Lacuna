# Lacuna

*A NeoForge 1.21.1 mod about the things the world has forgotten.*

Lacuna adds the **Forgotten Land**, a pocket dimension made of everything nobody remembers. It also adds relics of light,
shadow, time and space that are found and never crafted, a legendary essence that makes totems, new materials, gear
and four wood families, falling stars, strange creatures, ruins, and a civic life for vanilla villages. Its only
dependency is NeoForge. English and Italian are included.

**Version 1.0.0-beta.1** — the first beta. Feedback and bug reports: <https://github.com/SpicyMidas/Lacuna/issues>.

## Where to start

Every new player receives the **Lacuna Almanac**, an in-game guide with a page for every item, block, creature and
place, and where to find it. You can also craft one from a book and a ruby. The **Lacuna** advancement tab follows the
same path. Hold **Shift** over an item for its details; you can rebind this under Controls → Lacuna.

1. **Overworld.**
   - Mine **ruby** (deep, like diamond) and **Umbral Steel** (whose tools beat diamond).
   - Grow the four wood families.
   - Visit villages: they now have a hall, a mint, guild halls and a school.
   - At night, watch the sky for **falling stars**. Each one leaves a star rock that cools from molten to prime to
     extinguished, and gives star fragments as it does.
2. **Lacunar Quintessence.** This legendary essence is the only way to craft a totem. Sources:
   - natural falling stars, which sometimes carry one;
   - Sunken Bell Towers in deep oceans;
   - the Last Merchant, who wanders by with a cart and trades only in rubies;
   - always, the Reflection that guards a Mirror Labyrinth.
3. **Totem of Banishing:** a Quintessence, a Totem of Undying, rubies and gold.
   - **Use it** to blast into the Forgotten Land, a gray, quiet world where every block stands for 12 Overworld blocks.
     You come back when your time runs out, when you use the totem again, or when you die.
   - **Strike a mob** with it to banish the mob instead.
   - **Light a ring of Forgotten Portal Frames** with it, and the way stays open.
4. **Forgotten Land.** It has four biomes:
   - **Cinderfrost Flats:** ash snow that keeps footprints, and ice over drowned ruins.
   - **Inverted Falls:** floating islands with zenithite, and upward springs.
   - **Whispering Marsh:** fog, spore mushrooms, moths and spiders.
   - **Clockwork Dunes:** buried watches and slowed time.

   It also holds divine glass, chronal shards and exilite debris, ruins with caches, murals and obelisks, and the
   **Mirror Labyrinth**: beat your own Reflection there for a Quintessence and a relic.

Every item and its sources, with chances, is listed in [`docs/PROGRESSION.md`](docs/PROGRESSION.md).

## What's inside

- **Relics (found, never crafted)**, from structure chests, vanilla bosses and treasure, village mints and the Last
  Merchant:
  - **Eclipser**, a dashing blade of shadow that lets you possess creatures.
  - **Sun's Fury**, a trident of sunlight; the Last Merchant forges it from a Solar Flare.
  - **Divine Magnifying Glass**, a beam of focused sunlight.
  - **Gravemaw Greatsword**, **Tidecaller Sickle**, **Heartwood Bow**, **Echo Dagger**, **Pyrrhic Scepter** and
    **Ember Triangle**.
  - **Chronoshard Pocket Watch** (rewinds you), **Mirror of Recursion** (returns projectiles) and **Null Lantern**
    (hostile mobs ignore you).
  - **Whisperbone Flute** (calls the echoes of the dead) and **Wand of Quickening** (brings dropped items to life).
  - **Starcaller Trumpet**, which calls down a star.
- **Totems:**
  - **Totem of Banishing:** the Forgotten Land and banishment.
  - **Totem of Confusion:** five of you; only one is real.
  - A craftable **Totem of Undying**.
  - All three need a Lacunar Quintessence.
- **Crafted gear and gadgets:**
  - **Umbral Steel** tools.
  - **Starlace** armour and the **Starlace Bandage**, which wraps mobs into mummies.
  - **Fractured Pearl:** two linked teleport shards.
  - **Chrono Hourglass:** time runs faster around it for crops, furnaces and healing.
  - **Comically Large Bag:** carries off a mob, a cart, a block with its contents, or a player in PvP.
  - **Clockwork Decoy**, **Repair Kit**.
  - **Wayfarer's Chart:** a live map of the Overworld that follows you, and from other dimensions shows where you'd
    stand in the Overworld.
- **Village civic life:** extra buildings in vanilla villages, each run through a block with its own screen:
  - the **Village Ledger:** your standing, the laws and the policy;
  - a **Mint Press**, where the village Coiner turns ore into village coins;
  - a **Judge's Bench** for fines;
  - a **Council Table**;
  - **Guild Boards** (Smiths, Gemcutters and Scholars) with contracts;
  - a **School Desk**.
- **Blocks:**
  - Cindervine, Crystal Willow, Tidebloom and Echo Birch wood sets;
  - Divine Glass, a window that shows the ores, chests, spawners and mobs hidden behind it;
  - Mirror Glass, with real reflections;
  - the exilite stone family;
  - star rocks.

## Compatibility

- **Dedicated servers:** works on dedicated servers. No client code is reached on the server side.
- **Shader packs:** Iris/Oculus packs are detected. While one is active, the mirror reflections, the Divine Glass
  X-ray and the custom beam and ray effects use plain fallbacks.
- **Recipe viewers:** JEI, EMI and REI see every recipe, and technical blocks are hidden.
- **Common `c:` tags:** used for ores, raw materials, ingots, gems, storage blocks, tools, armour, wood, job sites and
  biomes. Modded biomes that carry the convention tags get the mod's trees and features.
- **Block-moving mods:** piston-proof blocks are marked so mods like Create or Carry On leave them alone. Summoned
  creatures can't be captured.
- **Configuration:** almost every feature can be switched off in `config/lacuna-common.toml`; reflections and other
  visuals in `lacuna-client.toml`. Every option is described in [`docs/CONFIG.md`](docs/CONFIG.md).

## For developers

- Spec: [`LACUNA_SPEC.md`](LACUNA_SPEC.md)
- Progress, deviations and limitations: [`docs/PROGRESS.md`](docs/PROGRESS.md)
- Progression: [`docs/PROGRESSION.md`](docs/PROGRESSION.md)
- Config: [`docs/CONFIG.md`](docs/CONFIG.md)
- Manual tests: [`docs/TESTING.md`](docs/TESTING.md)
- Changes: [`docs/CHANGELOG.md`](docs/CHANGELOG.md)

```
./gradlew build            # compile + jar (needs access to maven.neoforged.net and Mojang servers)
./gradlew runClient        # play
./gradlew runGameTestServer
bash tools/smoke.sh        # dedicated-server smoke test (tools/smoke_phase1.txt / phase2.txt)
```

Assets and data are produced by Python 3 tools (`pip install -r tools/requirements.txt`), deterministically.
Hand-drawn textures in `tools/art/hand/` are used as they are.

```
python3 tools/gen_textures.py      # PNG textures
python3 tools/gen_resources.py     # models, blockstates, lang (en_us, it_it), loot, recipes, tags, worldgen, advancements
python3 tools/gen_structures.py    # structure NBT templates (ruins, towers, labyrinths, village civic buildings)
python3 tools/validate_assets.py   # consistency checks (models, textures, lang, tags, loot, worldgen, jigsaws)
```

Pure logic (coordinates, beams, charges, path trails, reputation, council, pricing) is unit-tested with JUnit 5.

License: All Rights Reserved.
