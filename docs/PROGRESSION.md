# Lacuna — progression: how to get every item

From 1.0.0-beta crafting is deliberately narrow. **Materials, building blocks, wood, tools, armour and simple
utilities are crafted. Relics — the special weapons and gadgets — are never crafted: they are found.** Totems are the
one exception in between: they are crafted, but only from a **Lacunar Quintessence**, a legendary essence that is
itself only found.

In game, the **Lacuna Almanac** (every player gets one on first join; book + ruby) has a page for everything below,
and the Lacuna advancement tab follows the same path.

Chances below are per chest, per kill or per block; "+Looting" means +1 % per Looting level. Data lives in
`tools/content/m19_progression.py` (essence, ruin caches, vanilla sources), `m10_biomes_structures.py` (Sunken Bell
Tower, Mirror Hoard, Reflection), `m11_villages.py` (mint), `m15_starcaller.py` (star rocks) and
`logic/MerchantPricing.java` (the Last Merchant).

## The path in short

1. **Overworld**: rubies, Umbral Steel and the four wood families. Umbral Steel tools (each sets a ruby in its grip),
   civic blocks, repair kits, the Comically Large Bag. Mobs give clockwork gears, umbral nuggets, chronal shards.
2. **Relics and the first Quintessence**: watch the night sky (falling stars), dive for Sunken Bell Towers, meet the
   Last Merchant (rubies are his only currency, and what relics cost), raid vanilla structures and bosses.
3. **A Totem of Banishing** (Quintessence + Totem of Undying + rubies + gold) opens the **Forgotten Land**: light a
   Forgotten Portal with it once and the way stays open.
4. **Forgotten Land**: divine glass, zenithite, chronal shards, exilite; ruin caches; the **Mirror Labyrinth**, whose
   Reflection always leaves a Quintessence and whose hoard always holds a relic.

## Lacunar Quintessence

Legendary essence (Epic, always glinting, animated texture, fire- and explosion-proof; a dropped one lasts 20 minutes).

| Source | Chance | Notes |
|---|---|---|
| The **Reflection** of a Mirror Labyrinth | 100 % | when a player defeats it; once per labyrinth (the ring stays solved) |
| A **natural falling star** (clear nights, ~30 % per night) | 20 % per star | rests glowing at the impact; called stars never carry one |
| **Prime Star Rock**, mined without Silk Touch | 1 % | any star rock, natural or called by the Starcaller Trumpet |
| **Sunken Bell Tower** chest | 12 % | one chest per tower (deep oceans) |
| **Forgotten Land ruin cache** | 1 % | ruined halls, sunken plazas, memory shrines |
| **Last Merchant** | 1 visit in 5 | one, for 64 rubies + 4 ruby blocks (100 rubies) |

Recipes (the only way to craft a totem):

| Result | Recipe |
|---|---|
| Totem of Banishing (6 uses, never repaired) | `RQR / GTG / .R.` — 3 rubies, 1 Quintessence, 2 gold ingots, 1 Totem of Undying |
| Totem of Confusion | `.P. / AMA / GQG` — ender pearl, 2 amethyst shards, armour stand, 2 gold ingots, 1 Quintessence |
| Totem of Undying (vanilla, the legendary path) | `EGE / GQG / .G.` — 2 emeralds, 4 gold ingots, 1 Quintessence |

Repair kits refuse totems (`#lacuna:repair_kit_refuses`): their uses are final.

## Relics (no recipe)

| Relic | Sources (chance) |
|---|---|
| Sun's Fury | Mirror Hoard 4 %; Sunken Bell Tower 2 %; Last Merchant forges one from a **Solar Flare + 32 rubies** (always offered) |
| Eclipser | Mirror Hoard 10 %; ruin cache 0.3 %; ancient city chest 2 %; Last Merchant |
| Gravemaw Greatsword | the Wither 20 % (+Looting); Mirror Hoard 8 %; ruin cache 0.4 %; Last Merchant |
| Tidecaller Sickle | Elder Guardian 33 % (+Looting); Sunken Bell Tower 6 %; Last Merchant |
| Ember Triangle | bastion treasure chest 12 %; Mirror Hoard 6 %; ruin cache 0.3 %; Last Merchant |
| Wand of Quickening | woodland mansion chest 8 %; Mirror Hoard 10 %; ruin cache 0.5 %; Last Merchant |
| Starcaller Trumpet | Sunken Bell Tower 5 %; pillager outpost chest 5 %; Last Merchant |
| Mirror of Recursion | Mirror Hoard 14 %; End city treasure 8 %; ruin cache 0.5 %; Last Merchant |
| Chronoshard Pocket Watch | Sunken Bell Tower 4 %; village mint 3 %; Mirror Hoard 9 %; ruin cache 0.5 %; Last Merchant |
| Null Lantern | ancient city chest 6 %; Mirror Hoard 8 %; ruin cache 0.5 %; Last Merchant |
| Divine Magnifying Glass | desert pyramid chest 8 % (4 per pyramid); village mint 3 %; Mirror Hoard 9 %; ruin cache 0.6 %; Last Merchant |
| Echo Dagger | Warden 33 % (+Looting); ancient city chest 4 %; Mirror Hoard 9 %; Last Merchant |
| Pyrrhic Scepter | Piglin Brute 8 % (+Looting); bastion treasure 8 %; Mirror Hoard 7 %; ruin cache 0.5 %; Last Merchant |
| Heartwood Bow | pillager outpost 7 %; woodland mansion 7 %; ruin cache 0.5 %; Last Merchant |
| Whisperbone Flute | ancient city chest 6 %; Sunken Bell Tower 4 %; Mirror Hoard 6 %; ruin cache 0.5 %; Last Merchant |

- **Mirror Hoard**: one relic is guaranteed (the weights above add up to 100), on top of its materials and its 10 %
  Solar Flare Fragment. Boss chances need a player kill.
- Relics keep their repair materials (Ember for the Ember Triangle, divine glass shards for the magnifying glass,
  echo fragments for the flute, star fragments for the trumpet...). Every relic's details tooltip names its sources.

## Last Merchant

One attempt every 3 days, 25 % by day (about one visit in 12 days); he stays 3 days. **Rubies only.**

- Buys: the three ore families you mined most since his last visit (twice the coin rate per ruby), a bulk lot of the
  top one, two kinds of ingots — this is how ore turns into rubies.
- Sells: divine glass shard 4, chronal shard 6, echo shard 8, umbral steel ingot 4, 2 zenithite shards 3, 4 ember 1,
  3 bottles o' enchanting 2, Fractured Pearl 10 (rubies, scaled by the nearest village's price index); 8 % of visits a
  Solar Flare Fragment (16 rubies + 8 divine glass shards).
- **Relics**: two drawn at random each visit, one of each — 32 rubies (Divine Magnifying Glass, Chronoshard Pocket
  Watch, Whisperbone Flute), 40 (Heartwood Bow, Pyrrhic Scepter, Echo Dagger, Mirror of Recursion, Starcaller
  Trumpet), 48 (Null Lantern, Tidecaller Sickle, Wand of Quickening), 64 + 2 ruby blocks (Eclipser, Gravemaw
  Greatsword, Ember Triangle). Never a totem.
- **Sun's Fury forge**: Solar Flare + 32 rubies → Sun's Fury, every visit.
- **Lacunar Quintessence**: 1 visit in 5, 64 rubies + 4 ruby blocks.

## Crafted

| Item | Recipe / how |
|---|---|
| Ruby, Block of Ruby | smelt/blast the ore (Silk Touch); 9 ⇄ 1 |
| Raw / Umbral Steel Ingot / Nugget, blocks | smelt or blast raw or ore; 9 nuggets ⇄ ingot; 9 ⇄ block (raw and ingot) |
| Zenithite Block | 9 ⇄ 1 |
| Divine Glass, Divine Glass Pane | 4 shards (2×2); 6 glass → 16 panes |
| Glass (vanilla) | 4 Crystal Willow Shards (2×2) |
| Exilite Shard, Ingot, Block | smelt/blast debris; 4 shards + 4 gold (shapeless); 9 ⇄ block |
| Exilite family | 1 shard in a ring of 8 calcite → 8 Exilite; polished, bricks, chiseled, brick stairs/slab/wall (grid or stonecutter) |
| Forgotten Portal Frame | `.R. / ROR / .R.` 4 rubies + crying obsidian → 1; or exilite ingot between 2 crying obsidian → 2 |
| Wood families (Cindervine, Crystal Willow, Tidebloom, Echo Birch) | vanilla wood recipes: planks, wood, stairs, slab, fence, gate, door, trapdoor, plate, button |
| Umbral Steel tools | vanilla shapes with the first stick turned into a ruby: sword `U/U/R`, pickaxe `UUU/.R./.S.`, axe `UU/UR/.S`, shovel `U/R/S`, hoe `UU/.R/.S` |
| Starlace Cloth | 1 zenithite shard + 2 string → 2; 1 star fragment + 3 string → 4 |
| Starlace armour | vanilla armour shapes in cloth (24 for the set) |
| Starlace Bandage | 3 cloth + 1 string → 3 |
| Solar Flare | `.F. / FDF / .F.` 4 Solar Flare Fragments + Divine Glass (for the Sun's Fury forge) |
| Fractured Pearl | `DRD / RPR / DRD` 4 divine glass shards, 4 rubies, ender pearl (also 10 rubies at the merchant) |
| Clockwork Decoy | shapeless: 3 clockwork gears, redstone, white wool |
| Wayfarer's Chart | `.D. / RMR / .C.` divine glass shard, 2 rubies, empty map, compass |
| Lacuna Almanac | shapeless: book + ruby (every player is also handed one on first joining a world) |
| Comically Large Bag | `SLS / LPL / LCL` 2 string, 5 leather, ender pearl, lead (also 2 % in village mints) |
| Chrono Hourglass | `GCG / #S# / GCG` 4 gold, 2 chronal shards, 2 glass, sand (runs on chronal shards) |
| Repair Kit | `UGU / LIL` 2 umbral steel ingots, clockwork gear, 2 leather, iron ingot (also the Smiths' Guild) |
| Village Ledger | shapeless: lectern, book and quill, emerald |
| Mint Press, Judge's Bench, School Desk, Council Table | iron + piston + smooth stone; slabs + book + planks; slabs + sticks + book; carpets + slabs + log |
| Guild Board (×3 guilds) | `/P/ / PBP / /P/` sticks, paper and a gray (Smiths), red (Gemcutters) or purple (Scholars) banner |

Why a few gadgets stay craftable: the **Fractured Pearl** (rubies + Forgotten Land shards), **Chrono Hourglass**
(two chronal shards, and it burns more of them to run) and **Comically Large Bag** (an ender pearl) are utilities
gated by rare materials, not legendary power; the **Clockwork Decoy**, **Starlace Bandage** and **Repair Kit** are
consumables, and the **Wayfarer's Chart** is a navigation tool everyone should be able to make. The **Solar Flare** stays an assembled material because the Last Merchant forges Sun's Fury from it.

## Materials and drops

| Item | Where |
|---|---|
| Ruby | ruby ore (overworld, Y −64..16, like diamond); Last Merchant (pays rubies for ore); Coiner 3 coins; Gemcutters' Guild; obelisk rewards; ruin caches, bell towers, mirror hoards, mints |
| Divine Glass Shard | divine glass clusters (Forgotten Land, 1 + Fortune; Silk Touch keeps the cluster); ruin caches, bell towers, mirror hoards; Last Merchant 4 rubies; Gemcutters' Guild 5 coins; obelisks |
| Raw Umbral Steel | umbral steel ore (overworld, Y −64..0) |
| Umbral Steel Nugget | spiders and cave spiders 5 % (player kill, +Looting); ingot → 9 |
| Zenithite Shard | zenithite ore on Inverted Falls islands (Forgotten Land, 1-2 + Fortune); Last Merchant 2 for 3 rubies; Gemcutters' Guild 6 coins |
| Star Fragment | star rocks: Prime 1-3 (+Fortune), Molten 1-2, Extinguished 0-1 — natural falling stars or the Starcaller Trumpet |
| Solar Flare Fragment | ruin cache 1.5 %; Mirror Hoard 10 %; Molten Star Rock 3 %; Last Merchant 8 % of visits |
| Chronal Shard | buried watches (Clockwork Dunes, 1-2); husks 4 % (player kill, +Looting); ruin cache 1 %; bell towers, mirror hoards; Last Merchant 6 rubies; Scholars' Guild 8 coins; obelisks |
| Echo Fragment | each Echo called by the Whisperbone Flute leaves one; mirror hoards |
| Ember | Cindervine leaves 5 %; Last Merchant 4 for 1 ruby |
| Crystal Willow Shard | Crystal Willow leaves 5 % |
| Silk Dust | Lantern Moths 0-2 (+Looting) |
| Clockwork Gear | husks 0-2, pillagers 0-1 (+Looting) |
| Totem of Confusion | crafted (above); Illusioners 25 % (player kill, +5 % per Looting level) — later raid waves on normal/hard, dark forests at night (rare), about 3 in 10 woodland mansion mages |
| Exilite Debris | hidden veins below Y −8 in the Forgotten Land (diamond pickaxe); exilite shards also 1.2 % in ruin caches |
| Village Coin | Coiner trades (ore → coins), mint strongboxes, guild contracts |
| Guild Contract | a guild board, for members |
| Arrival / Departure Shard | splitting a Fractured Pearl |

## Blocks found in the world

| Block | Where |
|---|---|
| Ruby, Umbral Steel ores (+ deepslate) | overworld; Silk Touch keeps the ore |
| Zenithite ores, Divine Glass Cluster, Exilite Debris | Forgotten Land; Silk Touch keeps ores and clusters (debris drops itself) |
| Ashen Snow, Spore Mushroom, Rusted Gear | Forgotten Land biomes; drop themselves |
| Clear Ice, Updraft Spring, Buried Watch | Forgotten Land biomes; Silk Touch only (the watch otherwise drops chronal shards) |
| Molten / Prime / Extinguished Star Rock | where a star fell; Silk Touch keeps the block |
| Sunken Bell | Sunken Bell Tower (drops itself) |
| Logs, leaves, saplings, Crystal Willow Strands, Tidebloom Roots | the wood families' trees (overworld biomes and the Forgotten Land) |
| Ruin stone family, Mural Fragments, Obelisk Inscription, Mirror Glass, Labyrinth Seal and Ring | structure blocks, unbreakable in survival (creative only) |
| Spawn eggs (Lantern Moth, Last Merchant) | creative only |
