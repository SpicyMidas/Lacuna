"""M11a Township templates (5 biome palettes): walled core with plaza and civic buildings, district lot buildings,
roads and farms. Everything is deterministic (seeded by template name).

Layout of the 63x63 core (y 0 = ground):
  apron ring 0/62 (gate road jigsaws), wall ring 1/61 with corner towers and four 2-wide gates,
  main streets x 29..33 and z 29..33, plaza 22..40 with fountain, statue, bell tower and ledger,
  civic buildings at the diagonals (council NW, courthouse NE, guild hall SW, library SE),
  eight 11x11 district lots along the streets (market N, workshop E, residential S, civic W).
"""

L = "lacuna:"
SIZE = 63
C = 31
CORE_H = 24

PALETTES = {
    "plains": dict(wall="stone_bricks", wall2="mossy_stone_bricks", wall_top="stone_brick_wall", accent="chiseled_stone_bricks",
                   pave="cobblestone", path="dirt_path", plaza="polished_andesite", plaza2="stone_bricks", ground="grass_block",
                   plank="oak_planks", log="oak_log", roof="dark_oak_stairs", roof_full="dark_oak_planks", slab="oak_slab",
                   fence="oak_fence", door="oak_door", trapdoor="oak_trapdoor", leaves="oak_leaves", wood="oak",
                   villager="minecraft:plains", wool="white_wool", bed="red_bed", stone_slab="stone_brick_slab",
                   stone_stairs="stone_brick_stairs"),
    "desert": dict(wall="sandstone", wall2="cut_sandstone", wall_top="sandstone_wall", accent="chiseled_sandstone",
                   pave="smooth_sandstone", path="smooth_sandstone", plaza="cut_sandstone", plaza2="smooth_sandstone", ground="sand",
                   plank="birch_planks", log="stripped_birch_log", roof="smooth_sandstone_stairs", roof_full="smooth_sandstone",
                   slab="birch_slab", fence="birch_fence", door="birch_door", trapdoor="birch_trapdoor", leaves="birch_leaves",
                   wood="birch", villager="minecraft:desert", wool="yellow_wool", bed="orange_bed", stone_slab="smooth_sandstone_slab",
                   stone_stairs="sandstone_stairs"),
    "savanna": dict(wall="mud_bricks", wall2="packed_mud", wall_top="mud_brick_wall", accent="terracotta",
                    pave="coarse_dirt", path="dirt_path", plaza="terracotta", plaza2="orange_terracotta", ground="grass_block",
                    plank="acacia_planks", log="acacia_log", roof="acacia_stairs", roof_full="acacia_planks", slab="acacia_slab",
                    fence="acacia_fence", door="acacia_door", trapdoor="acacia_trapdoor", leaves="acacia_leaves", wood="acacia",
                    villager="minecraft:savanna", wool="orange_wool", bed="yellow_bed", stone_slab="mud_brick_slab",
                    stone_stairs="mud_brick_stairs"),
    "taiga": dict(wall="cobblestone", wall2="mossy_cobblestone", wall_top="cobblestone_wall", accent="mossy_stone_bricks",
                  pave="cobblestone", path="dirt_path", plaza="stone_bricks", plaza2="mossy_stone_bricks", ground="grass_block",
                  plank="spruce_planks", log="spruce_log", roof="spruce_stairs", roof_full="spruce_planks", slab="spruce_slab",
                  fence="spruce_fence", door="spruce_door", trapdoor="spruce_trapdoor", leaves="spruce_leaves", wood="spruce",
                  villager="minecraft:taiga", wool="brown_wool", bed="green_bed", stone_slab="cobblestone_slab",
                  stone_stairs="cobblestone_stairs"),
    "snowy": dict(wall="polished_diorite", wall2="diorite", wall_top="diorite_wall", accent="quartz_block",
                  pave="stone_bricks", path="dirt_path", plaza="polished_diorite", plaza2="smooth_quartz", ground="snow_block",
                  plank="spruce_planks", log="stripped_spruce_log", roof="spruce_stairs", roof_full="spruce_planks", slab="spruce_slab",
                  fence="spruce_fence", door="spruce_door", trapdoor="spruce_trapdoor", leaves="spruce_leaves", wood="spruce",
                  villager="minecraft:snow", wool="light_blue_wool", bed="blue_bed", stone_slab="diorite_slab",
                  stone_stairs="diorite_stairs"),
}

PROFESSION_BLOCKS = [
    ("blast_furnace", {"facing": "west", "lit": "false"}), ("smoker", {"facing": "west", "lit": "false"}),
    ("cartography_table", None), ("brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"}),
    ("composter", {"level": "0"}), ("barrel", {"facing": "up", "open": "false"}), ("fletching_table", None),
    ("cauldron", None), ("lectern", {"facing": "west", "has_book": "false", "powered": "false"}),
    ("stonecutter", {"facing": "west"}), ("loom", {"facing": "west"}), ("smithing_table", None),
    ("grindstone", {"face": "floor", "facing": "west"}),
]

LORE = [
    ["Founder Aldric", "raised these walls", "so none would be", "forgotten again."],
    ["Mother Ysolde", "who taught the", "first school to", "remember."],
    ["Ser Corvane", "kept the gates", "through the long", "eclipse."],
]

DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}


def pillar_props(name):
    return {"axis": "y"} if name.endswith("_log") or name.endswith("_wood") else None


class B:
    """Small helper around a Template with a palette."""

    def __init__(self, t, pal):
        self.t = t
        self.p = pal

    def set(self, x, y, z, name, props=None, nbt=None):
        if ":" not in name:
            name = "minecraft:" + name
        self.t.set(x, y, z, name, props, nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def air(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, "air")

    def log(self, x, y, z):
        self.set(x, y, z, self.p["log"], pillar_props(self.p["log"]))

    def stairs(self, x, y, z, facing, half="bottom", block=None):
        self.set(x, y, z, block or self.p["roof"], {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})

    def slab(self, x, y, z, block=None, top=False):
        self.set(x, y, z, block or self.p["slab"], {"type": "top" if top else "bottom", "waterlogged": "false"})

    def door(self, x, y, z, facing):
        for half, dy in (("lower", 0), ("upper", 1)):
            self.set(x, y + dy, z, self.p["door"], {"facing": facing, "half": half, "hinge": "left", "open": "false", "powered": "false"})

    def bed(self, x, y, z, facing):
        dx, dz = DIRS[facing]
        self.set(x, y, z, self.p["bed"], {"facing": facing, "part": "foot", "occupied": "false"})
        self.set(x + dx, y, z + dz, self.p["bed"], {"facing": facing, "part": "head", "occupied": "false"})

    def lantern(self, x, y, z, hanging=False):
        self.set(x, y, z, "lantern", {"hanging": "true" if hanging else "false", "waterlogged": "false"})

    def chest(self, x, y, z, facing, loot=None):
        nbt = {"id": "minecraft:chest"}
        if loot:
            nbt["LootTable"] = loot
        self.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"}, nbt)

    def villager(self, x, y, z, profession="minecraft:none"):
        self.t.entity((x + 0.5, y, z + 0.5), {
            "id": "minecraft:villager", "PersistenceRequired": True,
            "VillagerData": {"profession": profession, "type": self.p["villager"], "level": 1}})

    def golem(self, x, y, z):
        self.t.entity((x + 0.5, y, z + 0.5), {"id": "minecraft:iron_golem", "PlayerCreated": False, "PersistenceRequired": True})

    def jigsaw(self, x, y, z, facing, name, target, pool, final):
        if ":" not in final:
            final = "minecraft:" + final
        self.t.jigsaw(x, y, z, f"{facing}_up", name, target, pool, final_state=final)

    # ----------------------------------------------------------------- building shell
    def shell(self, x0, z0, x1, z1, floors=1, height=4, door_side="east", door_at=None, wall=None, roof=True):
        """Walls with log corners, floors every `height`, glass windows, a door; gable roof along x."""
        wall = wall or self.p["plank"]
        top = floors * height
        for fl in range(floors):
            y0 = fl * height
            self.fill(x0, y0, z0, x1, y0, z1, self.p["plank"] if fl else self.p["pave"])
            for y in range(y0 + 1, y0 + height):
                for x in range(x0, x1 + 1):
                    for z in (z0, z1):
                        self.set(x, y, z, wall)
                for z in range(z0, z1 + 1):
                    for x in (x0, x1):
                        self.set(x, y, z, wall)
                self.air(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1)
            # windows
            wy = y0 + 2
            for x in range(x0 + 2, x1 - 1, 3):
                self.set(x, wy, z0, "glass_pane")
                self.set(x, wy, z1, "glass_pane")
            for z in range(z0 + 2, z1 - 1, 3):
                self.set(x0, wy, z, "glass_pane")
                self.set(x1, wy, z, "glass_pane")
        for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
            for y in range(1, top):
                self.log(x, y, z)
        self.fill(x0, top, z0, x1, top, z1, self.p["plank"])
        if door_side in ("east", "west"):
            zc = door_at if door_at is not None else (z0 + z1) // 2
            self.door(x1 if door_side == "east" else x0, 1, zc, "west" if door_side == "east" else "east")
        else:
            xc = door_at if door_at is not None else (x0 + x1) // 2
            self.door(xc, 1, z0 if door_side == "north" else z1, "south" if door_side == "north" else "north")
        if roof:
            self.gable(x0 - 1, z0 - 1, x1 + 1, z1 + 1, top + 1)
        return top

    def gable(self, x0, z0, x1, z1, y):
        """Roof rising from the north and south edges toward a ridge along x."""
        lo, hi = z0, z1
        while lo < hi:
            for x in range(x0, x1 + 1):
                self.stairs(x, y, lo, "south")
                self.stairs(x, y, hi, "north")
            lo += 1
            hi -= 1
            y += 1
        if lo == hi:
            for x in range(x0, x1 + 1):
                self.slab(x, y, lo, self.p["slab"])


# =========================================================================================== core
def core(Template, v, pal, idx):
    t = Template(f"township/{v}/core", SIZE, CORE_H, SIZE)
    b = B(t, pal)
    lots = [(18, 6, 28, 16), (34, 6, 44, 16), (18, 46, 28, 56), (34, 46, 44, 56),
            (6, 18, 16, 28), (6, 34, 16, 44), (46, 18, 56, 28), (46, 34, 56, 44)]

    def in_lot(x, z):
        return any(x0 <= x <= x1 and z0 <= z <= z1 for (x0, z0, x1, z1) in lots)

    # ground inside the walls (lots are left to their own pieces)
    for x in range(2, SIZE - 2):
        for z in range(2, SIZE - 2):
            if not in_lot(x, z):
                b.set(x, 0, z, pal["ground"])
    walls(b)
    streets(b)
    plaza(b, idx)
    civic_buildings(b)
    gardens(b)
    # lot connectors: (x, z, facing into the lot, kind)
    for (x, z, facing, kind) in ((29, 11, "west", "market"), (33, 11, "east", "market"),
                                 (51, 29, "north", "workshop"), (51, 33, "south", "workshop"),
                                 (29, 51, "west", "residential"), (33, 51, "east", "residential"),
                                 (11, 29, "north", "civic"), (11, 33, "south", "civic")):
        b.jigsaw(x, 0, z, facing, f"lacuna:lot_{kind}", "lacuna:lot_entry", f"lacuna:township/{v}/lot_{kind}", pal["pave"])
    # gate roads
    for (x, z, facing) in ((C, 0, "north"), (C, SIZE - 1, "south"), (0, C, "west"), (SIZE - 1, C, "east")):
        b.jigsaw(x, 0, z, facing, "lacuna:gate_road", "lacuna:road_entry", f"lacuna:township/{v}/road", pal["path"])
    # guards
    b.golem(C - 4, 1, C + 6)
    b.golem(C + 4, 1, C - 6)
    t.save()


def walls(b):
    p = b.p
    lo, hi = 1, SIZE - 2
    for i in range(lo, hi + 1):
        for (x, z) in ((i, lo), (i, hi), (lo, i), (hi, i)):
            b.set(x, 0, z, p["wall"])
            for y in range(1, 7):
                b.set(x, y, z, p["wall2"] if b.t.r.random() < 0.15 else p["wall"])
            if i % 2 == 0:
                b.set(x, 7, z, p["wall_top"], {"up": "true"})
    # corner towers
    for (cx, cz) in ((lo, lo), (lo, hi - 4), (hi - 4, lo), (hi - 4, hi - 4)):
        for x in range(cx, cx + 5):
            for z in range(cz, cz + 5):
                edge = x in (cx, cx + 4) or z in (cz, cz + 4)
                for y in range(0, 11):
                    if edge or y in (0, 10):
                        b.set(x, y, z, p["wall"] if y < 10 else p["accent"])
                    else:
                        b.set(x, y, z, "air")
                if edge and (x + z) % 2 == 0:
                    b.set(x, 11, z, p["wall_top"], {"up": "true"})
        b.lantern(cx + 2, 9, cz + 2, hanging=True)
    # gates (2 wide x 3 tall, open) with accent pillars
    for (x0, z0, axis) in ((C - 1, lo, "x"), (C - 1, hi, "x"), (lo, C - 1, "z"), (hi, C - 1, "z")):
        for k in range(2):
            x = x0 + (k if axis == "x" else 0)
            z = z0 + (k if axis == "z" else 0)
            for y in (1, 2, 3):
                b.set(x, y, z, L + "township_gate", {"open": "true", "axis": axis})
        for k in (-1, 2):
            x = x0 + (k if axis == "x" else 0)
            z = z0 + (k if axis == "z" else 0)
            for y in range(1, 9):
                b.set(x, y, z, p["accent"])
            b.lantern(x, 9, z)


def streets(b):
    p = b.p
    for i in range(2, SIZE - 2):
        for w in range(C - 2, C + 3):
            for (x, z) in ((w, i), (i, w)):
                edge = w in (C - 2, C + 2)
                b.set(x, 0, z, p["pave"] if edge else p["path"])
    # lamp posts along the streets
    for i in range(6, SIZE - 6, 8):
        if 20 <= i <= 42:
            continue
        for (x, z) in ((C - 2, i), (C + 2, i), (i, C - 2), (i, C + 2)):
            b.set(x, 1, z, p["fence"])
            b.set(x, 2, z, p["fence"])
            b.lantern(x, 3, z)


def plaza(b, idx):
    p = b.p
    for x in range(22, 41):
        for z in range(22, 41):
            b.set(x, 0, z, p["plaza"] if (x + z) % 2 else p["plaza2"])
            for y in range(1, 4):
                b.set(x, y, z, "air")
    # fountain: 5x5 rim, water, centre column
    for x in range(C - 2, C + 3):
        for z in range(C - 2, C + 3):
            rim = x in (C - 2, C + 2) or z in (C - 2, C + 2)
            b.set(x, 1, z, p["stone_slab"] if rim else "water", {"type": "bottom", "waterlogged": "false"} if rim else {"level": "0"})
            if not rim:
                b.set(x, 0, z, p["wall"])
    for y in (1, 2, 3):
        b.set(C, y, C, p["accent"])
    b.set(C, 4, C, "water", {"level": "0"})
    aqueduct(b)
    statue(b, idx)
    bell_tower(b)
    # ledger on a podium south of the fountain, benches, lamps
    b.set(C, 0, C + 6, p["accent"])
    b.set(C, 1, C + 6, L + "township_ledger", {"facing": "south"})
    for x in (C - 6, C - 5, C + 5, C + 6):
        b.stairs(x, 1, C + 8, "south", block=p["stone_stairs"])
        b.stairs(x, 1, C - 8, "north", block=p["stone_stairs"])
    for (x, z) in ((23, 23), (23, 39), (39, 39)):
        b.set(x, 1, z, p["fence"])
        b.set(x, 2, z, p["fence"])
        b.lantern(x, 3, z)


def aqueduct(b):
    """Stone arches carrying a water channel from beyond the north wall to the fountain."""
    p = b.p
    for z in range(0, 27):
        for x in (C - 1, C + 1):
            b.set(x, 7, z, p["wall"])
        b.set(C, 6, z, p["wall"])
        b.set(C, 7, z, "water", {"level": "0"})
        if z % 6 == 3:
            for y in range(1, 6):
                b.set(C - 2, y, z, p["wall2"])
                b.set(C + 2, y, z, p["wall2"])
            for x in range(C - 2, C + 3):
                b.set(x, 6, z, p["wall"])
    b.set(C, 7, 27, p["wall"])
    b.set(C, 8, 26, p["wall"])
    for y in range(5, 8):
        b.set(C, y, 28, p["accent"])
    b.set(C, 7, 29, "water", {"level": "0"})  # spout above the fountain


def statue(b, idx):
    p = b.p
    x0, z0 = 23, 23
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            b.set(x, 1, z, p["accent"])
    cx, cz = x0 + 1, z0 + 1
    design = idx % 3
    body = ["polished_andesite", "quartz_block", "iron_block"][design]
    head = ["gold_block", "smooth_quartz", "polished_andesite"][design]
    b.set(cx, 2, cz, body)
    b.set(cx, 3, cz, body)
    b.set(cx, 4, cz, head)
    if design == 0:      # founder with a raised arm holding a lantern
        b.set(cx + 1, 4, cz, body)
        b.set(cx + 1, 5, cz, body)
        b.lantern(cx + 1, 6, cz)
    elif design == 1:    # scholar with an open book (lectern) before her
        b.set(cx, 2, cz + 1, "lectern", {"facing": "north", "has_book": "false", "powered": "false"})
        b.set(cx - 1, 3, cz, body)
    else:                # knight with a raised sword
        b.set(cx + 1, 3, cz, body)
        b.set(cx + 1, 4, cz, "iron_bars")
        b.set(cx + 1, 5, cz, "iron_bars")
        b.set(cx + 1, 6, cz, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    b.t.sign(cx, 1, z0 + 3, LORE[design], wood=p["wood"], wall_facing="south")


def bell_tower(b):
    p = b.p
    x0, z0 = 37, 23
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            corner = x in (x0, x0 + 2) and z in (z0, z0 + 2)
            for y in range(1, 14):
                if corner:
                    b.log(x, y, z)
            b.set(x, 13, z, p["roof_full"])
    b.set(x0 + 1, 12, z0 + 1, p["plank"])
    b.set(x0 + 1, 11, z0 + 1, "bell", {"attachment": "ceiling", "facing": "north", "powered": "false"}, {"id": "minecraft:bell"})
    for x in range(x0 - 1, x0 + 4):
        for z in (z0 - 1, z0 + 3):
            b.stairs(x, 14, z, "south" if z == z0 - 1 else "north")
    for z in range(z0, z0 + 3):
        b.stairs(x0 - 1, 14, z, "east")
        b.stairs(x0 + 3, 14, z, "west")
    b.set(x0 + 1, 15, z0 + 1, p["roof_full"])


def civic_buildings(b):
    p = b.p
    # NW council hall, NE courthouse, SW guild hall, SE library: 10x10, doors toward the plaza
    # 9x9 halls; a free 1-block lane (x 17 / x 45) runs between each hall and the lots toward the plaza
    specs = [((8, 8, 16, 16), "east", 12, "council"), ((46, 8, 54, 16), "west", 12, "court"),
             ((8, 46, 16, 54), "east", 50, "guild"), ((46, 46, 54, 54), "west", 50, "library")]
    for (x0, z0, x1, z1), side, door_z, kind in specs:
        b.shell(x0, z0, x1, z1, floors=1, height=5, door_side=side, door_at=door_z, wall=p["wall"])
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        if kind == "council":
            b.set(cx, 1, cz, L + "council_table", {"facing": "east"})
            b.set(cx + 1, 1, cz, L + "council_table", {"facing": "east"})
            for (x, z, f) in ((cx, cz - 1, "south"), (cx + 1, cz - 1, "south"), (cx, cz + 1, "north"), (cx + 1, cz + 1, "north"),
                              (cx - 1, cz, "east"), (cx + 2, cz, "west")):
                b.stairs(x, 1, z, f, block=p["roof"])
            b.villager(cx - 2, 1, cz + 2)
        elif kind == "court":
            b.set(x0 + 2, 1, cz, L + "judge_bench", {"facing": "east"})
            b.set(x0 + 2, 1, cz - 1, L + "judge_bench", {"facing": "east"})
            for z in range(z0 + 2, z1 - 1):
                b.stairs(cx + 2, 1, z, "west", block=p["roof"])
            b.villager(cx, 1, cz + 2)
        elif kind == "guild":
            # one board per guild, each under its banner (smiths gray, gemcutters red, scholars purple)
            for i, (guild, color, pattern) in enumerate((("smiths", "gray", "minecraft:half_vertical"),
                                                         ("gemcutters", "red", "minecraft:rhombus"),
                                                         ("scholars", "purple", "minecraft:circle"))):
                z = z0 + 2 + i * 2
                b.set(x0 + 1, 2, z, L + "guild_board", {"facing": "east", "guild": guild})
                b.set(x0 + 1, 3, z, f"{color}_wall_banner", {"facing": "east"},
                      {"id": "minecraft:banner", "patterns": [{"color": "yellow", "pattern": pattern}]})
            b.villager(cx + 1, 1, cz + 2)
        else:
            for z in range(z0 + 1, z1):
                for y in (1, 2, 3):
                    b.set(x1 - 1, y, z, "bookshelf")
            for i in range(3):
                b.set(x0 + 2, 1, z0 + 2 + i * 2, L + "school_desk", {"facing": "east"})
            b.set(cx, 1, cz, "lectern", {"facing": "east", "has_book": "false", "powered": "false"})
            b.villager(cx - 1, 1, cz + 2)
        b.lantern(cx, 4, cz, hanging=True)


def gardens(b):
    """Public gardens and orchard rows between the corner towers, the civic halls and the lots."""
    p = b.p
    r = b.t.r
    # flower beds along the plaza edges (outside the street) and small orchards in the free corners
    for (x0, z0, x1, z1) in ((19, 19, 21, 21), (41, 19, 43, 21), (19, 41, 21, 43), (41, 41, 43, 43)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                b.set(x, 0, z, "grass_block" if p["ground"] != "sand" else "coarse_dirt")
                b.set(x, 1, z, r.choice(["poppy", "dandelion", "oxeye_daisy", "cornflower", "allium"]))
    for (tx, tz) in ((8, 3), (52, 3), (3, 52), (57, 52), (20, 3), (42, 58)):
        tree(b, tx, 1, tz)


def tree(b, x, y, z):
    p = b.p
    for dy in range(3):
        b.log(x, y + dy, z)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            for dy in (3, 4):
                if (dx, dz) != (0, 0) or dy == 4:
                    b.set(x + dx, y + dy, z + dz, p["leaves"], {"distance": "1", "persistent": "true", "waterlogged": "false"})
    b.log(x, y + 3, z)


# =========================================================================================== lot pieces (11 x 11, entrance east)
def lot(Template, v, pal, name, h=16):
    t = Template(f"township/{v}/{name}", 11, h, 11)
    b = B(t, pal)
    for x in range(11):
        for z in range(11):
            b.set(x, 0, z, pal["ground"])
    for x in range(8, 11):
        b.set(x, 0, 5, pal["path"])
    return t, b


def entry(b, v):
    b.jigsaw(10, 0, 5, "east", "lacuna:lot_entry", "lacuna:lot_market", "minecraft:empty", b.p["path"])


def market(Template, v, pal, i):
    t, b = lot(Template, v, pal, f"market_{i}", 8)
    r = t.r
    colors = ["red", "yellow", "blue", "green", "orange", "purple", "cyan", "lime"]
    canopy = f"{colors[(i * 3) % len(colors)]}_wool"
    stripe = f"{colors[(i * 3 + 1) % len(colors)]}_wool"
    for x in range(2, 9):
        for z in range(1, 10):
            b.set(x, 0, z, pal["pave"])
    for (x, z) in ((2, 1), (8, 1), (2, 9), (8, 9)):
        for y in range(1, 4):
            b.set(x, y, z, pal["fence"])
    for x in range(1, 10):
        for z in range(0, 11):
            b.set(x, 4, z, canopy if (x + i) % 2 else stripe)
    goods = [("hay_block", {"axis": "y"}), ("melon", None), ("pumpkin", None), ("barrel", {"facing": "up", "open": "false"}),
             ("bookshelf", None), ("crafting_table", None)]
    for z in range(2, 9):
        b.slab(4, 1, z, pal["slab"], top=True)
        if r.random() < 0.6:
            name, props = goods[(i + z) % len(goods)]
            b.set(3, 1, z, name, props)
    b.chest(3, 1, 9, "east")
    b.villager(6, 1, 5)
    if i % 2 == 0:
        b.villager(6, 1, 3)
    b.lantern(5, 3, 5, hanging=True)
    entry(b, v)
    t.save()


def house(Template, v, pal, i):
    floors = 2 + (i % 2)
    t, b = lot(Template, v, pal, f"house_{i}", 4 * floors + 8)
    x0, z0, x1, z1 = 2, 1, 8, 9
    top = b.shell(x0, z0, x1, z1, floors=floors, height=4, door_side="east", wall=pal["plank"] if i % 3 else pal["wall"])
    # ladder shaft and beds on every floor
    for y in range(1, top):
        b.set(x0 + 1, y, z1 - 1, "ladder", {"facing": "north", "waterlogged": "false"})
    for fl in range(floors):
        y = fl * 4 + 1
        if fl:
            b.set(x0 + 1, fl * 4, z1 - 1, "ladder", {"facing": "north", "waterlogged": "false"})
        b.bed(x0 + 2, y, z0 + 1, "south")
        if fl == 0:
            b.set(x1 - 1, y, z0 + 1, "crafting_table")
            b.set(x1 - 2, y, z0 + 1, "furnace", {"facing": "south", "lit": "false"})
        else:
            b.bed(x0 + 4, y, z0 + 1, "south")
            b.chest(x1 - 1, y, z0 + 1, "west")
        b.lantern(5, y + 2, 5, hanging=True)
    b.villager(5, 1, 5)
    b.villager(4, 1, 6)
    # little front garden
    b.set(9, 1, 3, "flower_pot")
    b.set(9, 1, 7, "potted_poppy")
    entry(b, v)
    t.save()


def workshop(Template, v, pal, i):
    t, b = lot(Template, v, pal, f"workshop_{i}", 13)
    x0, z0, x1, z1 = 1, 1, 8, 9
    b.shell(x0, z0, x1, z1, floors=1, height=5, door_side="east", wall=pal["wall"] if i % 2 else pal["plank"])
    jobs = [PROFESSION_BLOCKS[(i - 1) * 2 + k] for k in range(2)]  # 6 workshops x 2 + 1 = all 13 job sites
    if i == 6:
        jobs.append(PROFESSION_BLOCKS[12])
    spots = [(x0 + 1, z0 + 1), (x0 + 1, z1 - 1), (x0 + 3, z0 + 1)]
    for (name, props), (x, z) in zip(jobs, spots):
        b.set(x, 1, z, name, props)
    b.set(x0 + 1, 1, (z0 + z1) // 2, "crafting_table")
    if i in (2, 5):
        b.set(x0 + 5, 1, z0 + 1, L + "mint_press", {"facing": "south"})  # M11c: Coiner job site
    b.chest(x0 + 3, 1, z1 - 1, "north")
    b.lantern(4, 4, 5, hanging=True)
    b.villager(5, 1, 4)
    entry(b, v)
    t.save()


def civic_lot(Template, v, pal, i):
    kind = ["granary", "orchard", "garden", "stables", "tavern", "school"][i - 1]
    t, b = lot(Template, v, pal, f"civic_{i}", 16)
    r = t.r
    if kind == "granary":
        b.shell(1, 1, 8, 9, floors=1, height=5, door_side="east", wall=pal["plank"])
        for x in range(2, 5):
            for z in range(2, 9):
                for y in range(1, 1 + r.randint(1, 3)):
                    b.set(x, y, z, "hay_block", {"axis": "y"})
        b.set(7, 1, 2, "composter", {"level": "0"})
        b.set(7, 1, 8, "barrel", {"facing": "up", "open": "false"})
    elif kind == "orchard":
        for x in range(0, 10):
            for z in range(0, 11):
                b.set(x, 0, z, "grass_block" if pal["ground"] != "sand" else "coarse_dirt")
        for (x, z) in ((2, 2), (2, 8), (6, 5)):
            tree(b, x, 1, z)
        for x in range(0, 10):
            b.set(x, 1, 0, pal["fence"])
            b.set(x, 1, 10, pal["fence"])
    elif kind == "garden":
        for x in range(1, 10):
            for z in range(1, 10):
                b.set(x, 0, z, "grass_block" if pal["ground"] != "sand" else "coarse_dirt")
                if (x + z) % 3 == 0:
                    b.set(x, 1, z, r.choice(["poppy", "dandelion", "allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley"]))
        for z in range(1, 10):
            b.set(1, 1, z, pal["leaves"], {"distance": "1", "persistent": "true", "waterlogged": "false"})
        for x in (4, 6):
            b.stairs(x, 1, 5, "east", block=pal["roof"])
        b.set(5, 0, 5, "water", {"level": "0"})
    elif kind == "stables":
        for x in range(1, 10):
            for z in range(1, 10):
                b.set(x, 0, z, "coarse_dirt")
        for x in range(1, 10):
            for z in (1, 9):
                b.set(x, 1, z, pal["fence"])
        for z in range(1, 10):
            b.set(1, 1, z, pal["fence"])
            b.set(5, 1, z, pal["fence"])
        b.set(5, 1, 5, pal["fence"] + "_gate", {"facing": "east", "in_wall": "false", "open": "false", "powered": "false"})
        for (x, z) in ((2, 2), (2, 8), (6, 2)):
            b.set(x, 1, z, "hay_block", {"axis": "y"})
        for x in range(1, 10):
            for z in range(1, 10):
                if x in (1, 5, 9) or z in (1, 9):
                    b.set(x, 4, z, pal["slab"], {"type": "bottom", "waterlogged": "false"})
        for (x, z) in ((1, 1), (9, 1), (1, 9), (9, 9)):
            for y in (2, 3):
                b.set(x, y, z, pal["fence"])
        b.t.entity((7.5, 1, 4.5), {"id": "minecraft:horse", "PersistenceRequired": True, "Variant": 1})
    elif kind == "tavern":
        b.shell(1, 1, 8, 9, floors=2, height=4, door_side="east", wall=pal["plank"])
        for (x, z) in ((3, 3), (3, 7), (6, 3)):
            b.set(x, 1, z, pal["fence"])
            b.set(x, 2, z, pal["slab"], {"type": "bottom", "waterlogged": "false"})
        for z in range(2, 9):
            b.set(7, 1, z, "barrel", {"facing": "west", "open": "false"})
        b.set(2, 1, 8, "brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
        b.set(2, 1, 2, "campfire", {"facing": "east", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
        for y in range(1, 8):
            b.set(2, y, 5, "ladder", {"facing": "east", "waterlogged": "false"})
        b.bed(4, 5, 3, "south")
        b.bed(6, 5, 3, "south")
        b.villager(4, 1, 5)
    else:  # school annex
        b.shell(1, 1, 8, 9, floors=1, height=5, door_side="east", wall=pal["wall"])
        for z in (3, 5, 7):
            b.set(4, 1, z, L + "school_desk", {"facing": "west"})
            b.set(6, 1, z, L + "school_desk", {"facing": "west"})
        b.set(2, 1, 5, "lectern", {"facing": "east", "has_book": "false", "powered": "false"})
        for z in range(2, 9):
            b.set(2, 2, z, "bookshelf")
        b.villager(5, 1, 4)
    b.lantern(9, 1, 2)
    entry(b, v)
    t.save()


# =========================================================================================== roads and farms
def road(Template, v, pal, i):
    t = Template(f"township/{v}/road_{i}", 5, 4, 16)
    b = B(t, pal)
    for z in range(16):
        for x in range(5):
            b.set(x, 0, z, pal["pave"] if x in (0, 4) else pal["path"])
    b.jigsaw(2, 0, 15, "south", "lacuna:road_entry", "lacuna:road", "minecraft:empty", pal["path"])
    b.jigsaw(2, 0, 0, "north", "lacuna:road", "lacuna:road_entry", f"lacuna:township/{v}/road", pal["path"])
    b.jigsaw(0, 0, 8, "west", "lacuna:road_side", "lacuna:farm_entry", f"lacuna:township/{v}/farm", pal["pave"])
    b.jigsaw(4, 0, 8, "east", "lacuna:road_side", "lacuna:farm_entry", f"lacuna:township/{v}/farm", pal["pave"])
    if i == 2:
        b.set(0, 1, 2, pal["fence"])
        b.set(0, 2, 2, pal["fence"])
        b.lantern(0, 3, 2)
    t.save()


def road_end(Template, v, pal):
    t = Template(f"township/{v}/road_end", 5, 5, 6)
    b = B(t, pal)
    for z in range(6):
        for x in range(5):
            b.set(x, 0, z, pal["path"])
    for x in (0, 4):
        b.set(x, 1, 0, pal["fence"])
        b.set(x, 2, 0, pal["fence"])
        b.lantern(x, 3, 0)
    b.jigsaw(2, 0, 5, "south", "lacuna:road_entry", "lacuna:road", "minecraft:empty", pal["path"])
    t.save()


def farm(Template, v, pal, i):
    t = Template(f"township/{v}/farm_{i}", 9, 6, 9)
    b = B(t, pal)
    r = t.r
    if i in (1, 2):
        crop = [("wheat", 7), ("carrots", 7), ("potatoes", 7), ("beetroots", 3)][(i * 2 + len(v)) % 4]
        for x in range(9):
            for z in range(9):
                edge = x in (0, 8) or z in (0, 8)
                if edge:
                    b.log(x, 0, z)
                elif x == 4:
                    b.set(x, 0, z, "water", {"level": "0"})
                else:
                    b.set(x, 0, z, "farmland", {"moisture": "7"})
                    b.set(x, 1, z, crop[0], {"age": str(r.randint(max(0, crop[1] - 3), crop[1]))})
        b.set(8, 1, 8, "composter", {"level": "0"})
    else:
        for x in range(9):
            for z in range(9):
                b.set(x, 0, z, "grass_block" if pal["ground"] != "sand" else "coarse_dirt")
        for (x, z) in ((2, 2), (6, 6), (6, 2)):
            tree(b, x, 1, z)
        for x in range(9):
            b.set(x, 1, 0, pal["fence"])
    b.jigsaw(0, 0, 4, "west", "lacuna:farm_entry", "lacuna:road_side", "minecraft:empty", pal["path"])
    t.save()


def build(Template):
    for idx, (v, pal) in enumerate(PALETTES.items()):
        core(Template, v, pal, idx)
        for i in range(1, 7):
            market(Template, v, pal, i)
            house(Template, v, pal, i)
            workshop(Template, v, pal, i)
            civic_lot(Template, v, pal, i)
        road(Template, v, pal, 1)
        road(Template, v, pal, 2)
        road_end(Template, v, pal)
        for i in (1, 2, 3):
            farm(Template, v, pal, i)
