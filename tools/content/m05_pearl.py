"""M5: Fractured Pearl, Arrival/Departure shards, Arrival Anchor."""

# the placed shard is a cube: an 8x8x8 translucent shell (whole texture on every face, square on square, never
# stretched) around a 4x4x4 bright core
SIDES = ("north", "south", "east", "west", "up", "down")
SHELL_FACES = {d: {"uv": [0, 0, 16, 16], "texture": "#crystal"} for d in SIDES}
CORE_FACES = {d: {"uv": [6, 6, 10, 10], "texture": "#crystal"} for d in SIDES}


def build(g):
    g.item("fractured_pearl", "Fractured Pearl", "Perla fratturata")
    g.item("arrival_shard", "Arrival Shard", "Scheggia d'arrivo")
    g.item("departure_shard", "Departure Shard", "Scheggia di partenza")
    g.name("item.lacuna.fractured_pearl.lore", "Two halves that never stopped looking for each other.",
           "Due metà che non hanno mai smesso di cercarsi.")
    g.name("item.lacuna.fractured_pearl.use", "Use: split into an Arrival and a Departure shard.",
           "Usa: dividi in una scheggia d'arrivo e una di partenza.")
    g.name("item.lacuna.arrival_shard.lore", "The right half. It wants to stay.", "La metà destra. Vuole restare.")
    g.name("item.lacuna.arrival_shard.use", "Use: throw; it anchors where it lands.", "Usa: lancia; si ancora dove atterra.")
    g.name("item.lacuna.departure_shard.lore", "The left half. It wants to go back.", "La metà sinistra. Vuole tornare.")
    g.name("item.lacuna.departure_shard.use", "Use: travel to the linked anchor (same dimension).",
           "Usa: viaggia all'ancora collegata (stessa dimensione).")
    g.name("lacuna.pearl.linked", "Linked: %s", "Collegata: %s")
    g.name("lacuna.pearl.unlinked", "Unlinked", "Non collegata")
    g.name("lacuna.pearl.cracked", "Cracked", "Incrinata")
    g.name("lacuna.pearl.split", "The pearl splits into two halves.", "La perla si divide in due metà.")
    g.name("lacuna.pearl.split_inventory", "The pearl splits; a half went into your inventory (hotbar full).",
           "La perla si divide; una metà è finita nell'inventario (barra piena).")
    g.name("lacuna.pearl.split_ground", "The pearl splits; no room left, a half fell at your feet.",
           "La perla si divide; nessuno spazio, una metà è caduta ai tuoi piedi.")
    g.name("lacuna.pearl.crumbles", "The shard crumbles.", "La scheggia si sbriciola.")
    g.name("lacuna.pearl.nothing_answers", "The shard hums, but nothing answers.", "La scheggia vibra, ma nulla risponde.")
    g.name("lacuna.pearl.forgotten_refuses", "The Forgotten Land swallows the call.", "La Terra Dimenticata inghiotte il richiamo.")
    g.name("entity.lacuna.arrival_shard", "Arrival Shard", "Scheggia d'arrivo")

    g.single("arrival_anchor", "Arrival Anchor", "Ancora d'arrivo",
             {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
              "textures": {"particle": "lacuna:block/arrival_anchor", "crystal": "lacuna:block/arrival_anchor"},
              "elements": [{"from": [6, 6, 6], "to": [10, 10, 10], "faces": CORE_FACES},
                           {"from": [4, 4, 4], "to": [12, 12, 12], "faces": SHELL_FACES}]},
             item=False, loot=None)

    g.shaped("fractured_pearl", ["DRD", "RPR", "DRD"],
             {"D": "lacuna:divine_glass_shard", "R": "lacuna:ruby", "P": "minecraft:ender_pearl"},
             "lacuna:fractured_pearl", category="misc", unlock="minecraft:ender_pearl")
