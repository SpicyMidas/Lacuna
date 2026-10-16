"""M5: Fractured Pearl, Arrival/Departure shards, Arrival Anchor."""


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

    # the hand-made 6x5x5 model (tools/art/hand/block/arrival_anchor.png): its base sits on the surface the shard
    # locked on - on the floor like a turtle egg, upside down on a ceiling, sideways on a wall (rotated like amethyst)
    m = "lacuna:block/arrival_anchor"
    g.single("arrival_anchor", "Arrival Anchor", "Ancora d'arrivo",
             {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
              "textures": {"particle": "lacuna:block/arrival_anchor", "crystal": "lacuna:block/arrival_anchor"},
              "elements": ANCHOR_ELEMENTS},
             item=False, loot=None, blockstate={"variants": {
                 "facing=up": {"model": m}, "facing=down": {"model": m, "x": 180},
                 "facing=north": {"model": m, "x": 90}, "facing=south": {"model": m, "x": 90, "y": 180},
                 "facing=east": {"model": m, "x": 90, "y": 90}, "facing=west": {"model": m, "x": 90, "y": 270}}})
    g.props("arrival_anchor", facing=["north", "south", "east", "west", "up", "down"])

    g.shaped("fractured_pearl", ["DRD", "RPR", "DRD"],
             {"D": "lacuna:divine_glass_shard", "R": "lacuna:ruby", "P": "minecraft:ender_pearl"},
             "lacuna:fractured_pearl", category="misc", unlock="minecraft:ender_pearl")


ANCHOR_ELEMENTS = [{"from": [5, 0, 6.5], "to": [11, 5, 10.5], "faces": {"north": {"uv": [6, 0, 12, 5], "texture": "#crystal"}, "east": {"uv": [12, 0, 16, 5], "texture": "#crystal"}, "south": {"uv": [0, 0, 6, 5], "texture": "#crystal"}, "west": {"uv": [12, 5, 16, 10], "texture": "#crystal"}, "up": {"uv": [0, 5, 6, 9], "texture": "#crystal"}, "down": {"uv": [6, 5, 12, 9], "texture": "#crystal"}}}, {"from": [6, 1, 6], "to": [10, 4, 9.5], "faces": {"north": {"uv": [0, 9, 4, 12], "texture": "#crystal"}, "east": {"uv": [0, 9, 3.5, 12], "texture": "#crystal"}, "south": {"uv": [0, 9, 4, 12], "texture": "#crystal"}, "west": {"uv": [0, 9, 3.5, 12], "texture": "#crystal"}, "up": {"uv": [0, 9, 4, 12], "texture": "#crystal"}, "down": {"uv": [0, 9, 4, 12], "texture": "#crystal"}}}, {"from": [5, 0, 5.5], "to": [6, 4, 6.5], "faces": {"north": {"uv": [15, 12, 16, 16], "texture": "#crystal"}, "east": {"uv": [15, 12, 16, 16], "texture": "#crystal"}, "south": {"uv": [15, 12, 16, 16], "texture": "#crystal"}, "west": {"uv": [13, 10, 14, 14], "texture": "#crystal"}, "up": {"uv": [15, 15, 16, 16], "texture": "#crystal"}, "down": {"uv": [0, 15, 1, 16], "texture": "#crystal"}}}, {"from": [10, 0, 5.5], "to": [11, 5, 6.5], "faces": {"north": {"uv": [15, 12, 16, 16], "texture": "#crystal"}, "east": {"uv": [14, 10, 15, 15], "texture": "#crystal"}, "south": {"uv": [15, 12, 16, 16], "texture": "#crystal"}, "west": {"uv": [15, 12, 16, 16], "texture": "#crystal"}, "up": {"uv": [14, 10, 15, 11], "texture": "#crystal"}, "down": {"uv": [14, 13, 15, 14], "texture": "#crystal"}}}, {"from": [5, 4, 5.5], "to": [10, 5, 6.5], "faces": {"north": {"uv": [12, 15, 16, 16], "texture": "#crystal"}, "east": {"uv": [15, 15, 16, 16], "texture": "#crystal"}, "south": {"uv": [8, 4, 10, 5], "texture": "#crystal"}, "west": {"uv": [3, 12, 4, 13], "texture": "#crystal"}, "up": {"uv": [5, 10, 10, 11], "texture": "#crystal"}, "down": {"uv": [12, 15, 16, 16], "texture": "#crystal"}}}, {"from": [6, 0, 5.5], "to": [10, 1, 6.5], "faces": {"north": {"uv": [12, 16, 16, 15], "texture": "#crystal"}, "east": {"uv": [15, 15, 16, 16], "texture": "#crystal"}, "south": {"uv": [12, 15, 16, 16], "texture": "#crystal"}, "west": {"uv": [15, 15, 16, 16], "texture": "#crystal"}, "up": {"uv": [14, 15, 16, 16], "texture": "#crystal"}, "down": {"uv": [1, 11, 5, 12], "texture": "#crystal"}}}]
