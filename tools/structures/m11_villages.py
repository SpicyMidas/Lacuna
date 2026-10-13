"""M11 (revision 2): civic buildings for VANILLA villages, one set per vanilla village style (plains, desert,
savanna, taiga, snowy), built in that style's palette. VillagePools.java appends them to the vanilla pools
minecraft:village/<type>/houses at server start.

Every building follows the vanilla house conventions (checked against the vanilla NBTs):
  * the entrance jigsaw sits at x=0, y=0 in the middle of the west side: orientation west_up, name and target
    minecraft:building_entrance, pool minecraft:empty, joint aligned, final_state a stair step up to the door;
  * the floor is y=0, the front wall x=1 with the door at y=1-2, the building extends toward +x;
  * minecraft:bottom jigsaws in the floor (up_north) pull an unemployed villager from minecraft:village/<type>/villagers,
    who then claims the building's job site (mint press, judge bench, guild board, lectern);
  * roofs overhang by one block (z=0 / z=W+1, x=0 / x=D+1); interior air is explicit, the outside is left unset.

Buildings: village_hall (ledger, council table, judge bench), mint (mint press, strongbox), guild_hall (one guild
board under each guild banner) and schoolhouse (school desks, lectern, blackboard, bookshelves).

civic_square: a town centre (appended to minecraft:village/<type>/town_centers) - a paved crossroads with the bell under
a canopy, lamps, benches and flower beds; its four corner lots hold the four civic buildings, attached by jigsaws that
point INTO the square's own bounding box (so they are placed inside it, never colliding with the streets), and four
street exits continue the village outward like the vanilla town centres.
"""

L = "lacuna:"

STYLES = {
    "plains": dict(lower="cobblestone", floor="oak_planks", wall="oak_planks", post="oak_log", plate="stripped_oak_log",
                   roof="oak_stairs", ridge="oak_slab", gable="oak_planks", step="cobblestone_stairs", door="oak_door",
                   window="glass_pane", chair="oak_stairs", accent="white_terracotta", carpet="yellow_carpet",
                   chimney="cobblestone", flat=False, banner="yellow"),
    "desert": dict(lower="cut_sandstone", floor="smooth_sandstone", wall="smooth_sandstone", post="cut_sandstone",
                   plate="cut_sandstone", roof="smooth_sandstone_stairs", ridge="smooth_sandstone_slab", gable="cut_sandstone",
                   step="smooth_sandstone_stairs", door="jungle_door", window="iron_bars", chair="jungle_stairs",
                   accent="orange_terracotta", carpet="lime_carpet", chimney="cut_sandstone", flat=True, banner="lime"),
    "savanna": dict(lower="orange_terracotta", floor="acacia_planks", wall="acacia_planks", post="acacia_log",
                    plate="acacia_log", roof="acacia_stairs", ridge="acacia_slab", gable="acacia_planks", step="acacia_stairs",
                    door="acacia_door", window="glass_pane", chair="acacia_stairs", accent="yellow_terracotta",
                    carpet="orange_carpet", chimney="red_terracotta", flat=False, banner="orange"),
    "taiga": dict(lower="cobblestone", floor="spruce_planks", wall="spruce_planks", post="spruce_log", plate="spruce_log",
                  roof="spruce_stairs", ridge="spruce_slab", gable="spruce_planks", step="cobblestone_stairs", door="spruce_door",
                  window="glass_pane", chair="spruce_stairs", accent="mossy_cobblestone", carpet="purple_carpet",
                  chimney="cobblestone", flat=False, banner="purple"),
    "snowy": dict(lower="diorite", floor="spruce_planks", wall="spruce_planks", post="stripped_spruce_log",
                  plate="stripped_spruce_log", roof="spruce_stairs", ridge="spruce_slab", gable="spruce_planks", step="diorite_stairs",
                  door="spruce_door", window="glass_pane", chair="spruce_stairs", accent="packed_ice", carpet="light_blue_carpet",
                  chimney="diorite", flat=False, banner="light_blue"),
}

SQUARES = {
    "plains": dict(ground="grass_block", path="dirt_path", curb="cobblestone", square="stone_bricks", square2="polished_andesite",
                   post="oak_fence", canopy="oak_slab", flowers=("poppy", "dandelion", "oxeye_daisy", "cornflower")),
    "desert": dict(ground="sand", path="smooth_sandstone", curb="cut_sandstone", square="cut_sandstone", square2="smooth_sandstone",
                   post="sandstone_wall", canopy="smooth_sandstone_slab", flowers=("dead_bush", "cactus")),
    "savanna": dict(ground="grass_block", path="dirt_path", curb="coarse_dirt", square="terracotta", square2="orange_terracotta",
                    post="acacia_fence", canopy="acacia_slab", flowers=("poppy", "dandelion", "orange_tulip", "allium")),
    "taiga": dict(ground="grass_block", path="dirt_path", curb="cobblestone", square="cobblestone", square2="mossy_cobblestone",
                  post="spruce_fence", canopy="spruce_slab", flowers=("fern", "sweet_berry_bush", "poppy", "lily_of_the_valley")),
    "snowy": dict(ground="snow_block", path="dirt_path", curb="diorite", square="polished_diorite", square2="packed_ice",
                  post="spruce_fence", canopy="spruce_slab", flowers=("blue_orchid", "lily_of_the_valley", "azure_bluet")),
}

GUILDS = (("smiths", "gray", "minecraft:half_vertical"), ("gemcutters", "red", "minecraft:rhombus"),
          ("scholars", "purple", "minecraft:circle"))


def is_log(name):
    return name.endswith("_log") or name.endswith("_wood")


class Civic:
    """One civic building: walls x=1..D, z=1..W, wall height H (floor y=0), roof above, entrance at (0, 0, zc)."""

    def __init__(self, Template, vtype, name, D, W, H):
        self.v = vtype
        self.p = STYLES[vtype]
        self.D, self.W, self.H = D, W, H
        self.zc = (W + 1) // 2
        top = H + 2 if self.p["flat"] else H + 1 + (W + 1) // 2
        self.t = Template(f"village/{vtype}/{name}", D + 2, top + 3, W + 2)  # +3: chimney and its campfire
        self.interior = {}  # y -> (z_lo, z_hi) of the air inside the roof

    # ------------------------------------------------------------------ primitives
    def set(self, x, y, z, name, props=None, nbt=None):
        if ":" not in name:
            name = "minecraft:" + name
        self.t.set(x, y, z, name, props, nbt)

    def pillar(self, x, y, z, name, axis="y"):
        self.set(x, y, z, name, {"axis": axis} if is_log(name) else None)

    def stairs(self, x, y, z, facing, block=None, half="bottom"):
        self.set(x, y, z, block or self.p["roof"], {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})

    def slab(self, x, y, z, block, kind="bottom"):
        self.set(x, y, z, block, {"type": kind, "waterlogged": "false"})

    def pane(self, x, y, z, along):
        """Glass pane / iron bars connected along the wall direction ('x' or 'z')."""
        on = {"x": ("east", "west"), "z": ("north", "south")}[along]
        props = {d: ("true" if d in on else "false") for d in ("north", "east", "south", "west")}
        props["waterlogged"] = "false"
        self.set(x, y, z, self.p["window"], props)

    def lantern_on_chain(self, x, z):
        """A lantern hanging on chains from the ridge (gable) or the roof (flat) down to just above the walls' top."""
        ceiling_y = self.ceiling()
        drop = 1 if self.p["flat"] else ceiling_y - self.H - 2
        for y in range(ceiling_y - drop, ceiling_y):
            self.set(x, y, z, "chain", {"axis": "y", "waterlogged": "false"})
        self.set(x, ceiling_y - drop - 1, z, "lantern", {"hanging": "true", "waterlogged": "false"})

    def wall_banner(self, x, y, z, facing, color, pattern, pattern_color="white"):
        self.set(x, y, z, f"{color}_wall_banner", {"facing": facing},
                 {"id": "minecraft:banner", "patterns": [{"color": pattern_color, "pattern": pattern}]})

    def chest(self, x, y, z, facing, loot=None):
        nbt = {"id": "minecraft:chest"}
        if loot:
            nbt["LootTable"] = loot
        self.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"}, nbt)

    def villager_spot(self, x, z):
        """minecraft:bottom jigsaw in the floor: an (usually unemployed) villager of this style spawns above it."""
        self.t.jigsaw(x, 0, z, "up_north", "minecraft:bottom", "minecraft:bottom", f"minecraft:village/{self.v}/villagers",
                      final_state="minecraft:" + self.p["floor"], joint="rollable")

    # ------------------------------------------------------------------ shell
    def shell(self, posts_x, windows_x, windows_z=()):
        p, D, W, H, zc = self.p, self.D, self.W, self.H, self.zc
        # floor
        for x in range(1, D + 1):
            for z in range(1, W + 1):
                edge = x in (1, D) or z in (1, W)
                self.set(x, 0, z, p["lower"] if edge else p["floor"])
        # walls
        for y in range(1, H + 1):
            for x in range(1, D + 1):
                for z in range(1, W + 1):
                    if not (x in (1, D) or z in (1, W)):
                        continue
                    corner = x in (1, D) and z in (1, W)
                    if corner or (z in (1, W) and x in posts_x):
                        self.pillar(x, y, z, p["post"])
                    else:
                        self.set(x, y, z, p["lower"] if y == 1 else p["wall"])
        # windows: side walls at the given x, front/back walls at the given z
        for y in range(2, min(H, 3) + 1):
            for x in windows_x:
                self.pane(x, y, 1, "x")
                self.pane(x, y, W, "x")
            for z in windows_z:
                self.pane(D, y, z, "z")
        # door, step lights
        for half, y in (("lower", 1), ("upper", 2)):
            self.set(1, y, zc, p["door"], {"facing": "east", "half": half, "hinge": "right", "open": "false", "powered": "false"})
        for z in (zc - 1, zc + 1):
            self.set(0, 2, z, "wall_torch", {"facing": "west"})
        # entrance jigsaw: becomes a step up to the door
        step = f"minecraft:{p['step']}[facing=east,half=bottom,shape=straight,waterlogged=false]"
        self.t.jigsaw(0, 0, zc, "west_up", "minecraft:building_entrance", "minecraft:building_entrance", "minecraft:empty",
                      final_state=step, joint="aligned")
        if p["flat"]:
            self.flat_roof()
        else:
            self.gable_roof()
        self.fill_air()

    def gable_roof(self):
        """Ridge along x; stairs from the overhangs z=0 / z=W+1 up to the ridge; gable ends at x=1 and x=D."""
        p, D, W, H = self.p, self.D, self.W, self.H
        for x in range(1, D + 1):  # top plates on the long walls
            for z in (1, W):
                self.pillar(x, H + 1, z, p["plate"], axis="x")
        lo, hi, y = 0, W + 1, H + 1
        while lo < hi:
            for x in range(0, D + 2):
                self.stairs(x, y, lo, "south")
                self.stairs(x, y, hi, "north")
            for z in range(max(lo + 1, 1), min(hi - 1, W) + 1):
                if y > H + 1 or z not in (1, W):
                    self.set(1, y, z, p["gable"])
                    self.set(D, y, z, p["gable"])
            self.interior[y] = (max(lo + 1, 2), min(hi - 1, W - 1))
            lo, hi, y = lo + 1, hi - 1, y + 1
        if lo == hi:
            for x in range(0, D + 2):
                self.slab(x, y, lo, p["ridge"])
        self.ridge_y = y
        # a round window in each gable
        self.set(1, H + 2, self.zc, "glass")
        self.set(D, H + 2, self.zc, "glass")

    def flat_roof(self):
        """Desert style: flat roof with a cut-sandstone band and a slab parapet."""
        p, D, W, H = self.p, self.D, self.W, self.H
        for x in range(1, D + 1):
            for z in range(1, W + 1):
                self.set(x, H + 1, z, p["gable"])
                if x in (1, D) or z in (1, W):
                    if (x + z) % 2 == 0:
                        self.slab(x, H + 2, z, p["ridge"])
        self.ridge_y = H + 1

    def fill_air(self):
        D, W, H = self.D, self.W, self.H
        for x in range(2, D):
            for y in range(1, H + 1):
                for z in range(2, W):
                    self.set(x, y, z, "air")
            for y, (z0, z1) in self.interior.items():
                for z in range(z0, z1 + 1):
                    if self.t.get(x, y, z) is None:
                        self.set(x, y, z, "air")

    def ceiling(self):
        """Block row the hanging lanterns attach to (the ridge for gables, the roof for flat roofs)."""
        return self.ridge_y if not self.p["flat"] else self.H + 1

    def save(self):
        self.t.save()


# ============================================================================================ buildings
def village_hall(Template, v):
    b = Civic(Template, v, "village_hall", D=11, W=9, H=5)
    p, zc = b.p, b.zc
    b.shell(posts_x=(6,), windows_x=(3, 4, 8, 9))
    # carpet runner from the door to the council table
    for x in range(2, 5):
        b.set(x, 1, zc, p["carpet"])
    # council table with chairs
    for x in (6, 7):
        b.set(x, 1, zc, L + "council_table", {"facing": "west"})
        b.stairs(x, 1, zc - 1, "north", p["chair"])
        b.stairs(x, 1, zc + 1, "south", p["chair"])
    b.stairs(5, 1, zc, "west", p["chair"])
    b.stairs(8, 1, zc, "east", p["chair"])
    # court corner: the magistrate's bench and seat
    for z in (2, 3):
        b.set(9, 1, z, L + "judge_bench", {"facing": "west"})
        b.stairs(10, 1, z, "east", p["chair"])
    # back wall: the ledger between bookshelves under the village banner
    b.set(10, 1, zc, L + "village_ledger", {"facing": "west"})
    for z in (zc - 1, zc + 1):
        b.set(10, 1, z, "bookshelf")
        b.set(10, 2, z, "bookshelf")
    b.wall_banner(10, 3, zc, "west", p["banner"], "minecraft:triangles_top", "white")
    b.set(10, 1, 7, "potted_red_tulip" if v != "desert" else "potted_cactus")
    b.chest(10, 1, 8, "west")
    # facade banners
    for z in (zc - 2, zc + 2):
        b.wall_banner(0, 3, z, "west", p["banner"], "minecraft:stripe_center", "white")
    for x in (4, 8):
        b.lantern_on_chain(x, zc)
    b.villager_spot(3, 7)
    b.save()


def mint(Template, v):
    b = Civic(Template, v, "mint", D=9, W=7, H=4)
    p, zc = b.p, b.zc
    b.shell(posts_x=(5,), windows_x=(3, 7))
    # teller's counter with bars, opening in the middle
    for z in range(2, 7):
        if z == zc:
            continue
        b.slab(5, 1, z, "smooth_stone_slab", "top")
        b.set(5, 2, z, "iron_bars", {"north": "true" if z - 1 != zc else "false", "south": "true" if z + 1 != zc else "false",
                                     "east": "false", "west": "false", "waterlogged": "false"})
    # the press (Coiner job site), furnace, anvil and the strongbox
    b.set(7, 1, zc, L + "mint_press", {"facing": "west"})
    b.set(8, 1, 2, "furnace", {"facing": "west", "lit": "false"})
    b.set(8, 1, 6, "anvil", {"facing": "north"})
    b.chest(8, 1, 5, "west", loot="lacuna:chests/village_mint")
    b.set(8, 1, 3, p["accent"])
    b.set(8, 2, 3, "potted_dead_bush" if v == "desert" else "flower_pot")
    # waiting bench by the door
    b.stairs(2, 1, 2, "north", p["chair"])
    b.stairs(3, 1, 2, "north", p["chair"])
    # chimney above the furnace corner
    top = b.ceiling() if not p["flat"] else b.H + 2
    for y in range(b.H + 1, top + 1):
        b.set(9, y, 2, p["chimney"])
    b.set(9, top + 1, 2, "campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    b.lantern_on_chain(3, zc)
    b.lantern_on_chain(7, zc)
    b.villager_spot(3, 5)
    b.save()


def guild_hall(Template, v):
    b = Civic(Template, v, "guild_hall", D=11, W=9, H=5)
    p, zc = b.p, b.zc
    b.shell(posts_x=(6,), windows_x=(3, 4, 8, 9))
    # one board per guild along the back wall, each under its banner
    for i, (guild, color, pattern) in enumerate(GUILDS):
        z = 3 + i * 2
        b.set(10, 1, z, L + "guild_board", {"facing": "west", "guild": guild})
        b.wall_banner(10, 3, z, "west", color, pattern, "yellow")
    b.chest(10, 1, 2, "west")
    b.set(10, 1, 8, "crafting_table")
    # long feast table with chairs
    for x in range(4, 8):
        b.slab(x, 1, zc, p["ridge"] if not p["flat"] else "smooth_stone_slab", "top")
        b.stairs(x, 1, zc - 1 if x % 2 == 0 else zc + 1, "north" if x % 2 == 0 else "south", p["chair"])
    b.set(2, 1, 2, "anvil", {"facing": "east"})
    b.set(2, 1, 8, p["accent"])
    b.set(2, 2, 8, "flower_pot")
    for z in (zc - 2, zc + 2):
        b.wall_banner(0, 3, z, "west", "gray" if z < zc else "purple", "minecraft:border", "yellow")
    for x in (4, 8):
        b.lantern_on_chain(x, zc)
    b.villager_spot(3, 3)
    b.villager_spot(3, 7)
    b.save()


def schoolhouse(Template, v):
    b = Civic(Template, v, "schoolhouse", D=10, W=9, H=4)
    p, zc = b.p, b.zc
    b.shell(posts_x=(5,), windows_x=(3, 4, 7), windows_z=())
    # blackboard on the back wall, the teacher's lectern before it
    for z in range(3, 8):
        for y in (2, 3):
            b.set(9, y, z, "black_terracotta")
    b.set(8, 1, zc, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    # two rows of desks facing the teacher, an aisle in the middle
    for x in (4, 6):
        for z in (3, 4, 6, 7):
            b.set(x, 1, z, L + "school_desk", {"facing": "east"})
            b.stairs(x - 1, 1, z, "west", p["chair"])
    for x in (7, 8):
        for z in (2, 8):
            b.set(x, 1, z, "bookshelf")
            b.set(x, 2, z, "bookshelf")
    for x in (3, 7):
        b.lantern_on_chain(x, zc)
    b.villager_spot(2, 2)
    b.save()


# ============================================================================================ civic square (town centre)
# 37 x 37 piece: crossroads bands x/z 15..21 (curbs on 15 and 21), the paved square 12..24 in the middle, four 15 x 15
# corner lots for the buildings (each building keeps clear of the square's corner, where a flower bed and a lamp stand).
SQ = 37
SQ_C = 18
SQ_H = 18         # tall enough to hold the buildings attached inside it
LOTS = ((15, 6, "west", "village_hall"), (30, 15, "north", "guild_hall"), (15, 30, "west", "mint"), (30, 21, "south", "schoolhouse"))


def post(t, x, y, z, name):
    if name.endswith("_wall"):
        t.set(x, y, z, "minecraft:" + name, {"up": "true", "north": "none", "east": "none", "south": "none", "west": "none",
                                             "waterlogged": "false"})
    else:
        t.set(x, y, z, "minecraft:" + name, {"north": "false", "east": "false", "south": "false", "west": "false",
                                             "waterlogged": "false"})


def civic_square(Template, v):
    t = Template(f"village/{v}/civic_square", SQ, SQ_H, SQ)
    q = SQUARES[v]
    r = t.r

    def put(x, y, z, name, props=None, nbt=None):
        t.set(x, y, z, name if ":" in name else "minecraft:" + name, props, nbt)

    # ground everywhere, the crossroads with curbs, the paved square in the middle
    for x in range(SQ):
        for z in range(SQ):
            in_x, in_z = 15 <= x <= 21, 15 <= z <= 21
            if 12 <= x <= 24 and 12 <= z <= 24:
                put(x, 0, z, q["square"] if (x + z) % 2 else q["square2"])
            elif in_x or in_z:
                edge = (in_x and x in (15, 21)) or (in_z and z in (15, 21))
                put(x, 0, z, q["curb"] if edge else q["path"])
            else:
                put(x, 0, z, q["ground"])
    # flower beds and lamps in the four corners of the square
    for (x0, z0, lx, lz) in ((12, 12, 14, 14), (23, 12, 22, 14), (12, 23, 14, 22), (23, 23, 22, 22)):
        for x in (x0, x0 + 1):
            for z in (z0, z0 + 1):
                if v == "desert":
                    put(x, 0, z, "sand")
                    if (x + z) % 2 == 0:
                        put(x, 1, z, r.choice(q["flowers"]))
                else:
                    put(x, 0, z, "grass_block")
                    flower = r.choice(q["flowers"])
                    put(x, 1, z, flower, {"age": "1"} if flower == "sweet_berry_bush" else None)
        post(t, lx, 1, lz, q["post"])
        post(t, lx, 2, lz, q["post"])
        put(lx, 3, lz, "lantern", {"hanging": "false", "waterlogged": "false"})
    # canopy over the bell (the village meeting point): four posts, a slab roof, the bell hanging in the middle
    for (x, z) in ((16, 16), (16, 20), (20, 16), (20, 20)):
        for y in (1, 2, 3):
            post(t, x, y, z, q["post"])
    for x in range(15, 22):
        for z in range(15, 22):
            put(x, 4, z, q["canopy"], {"type": "bottom", "waterlogged": "false"})
    for x in range(16, 21):
        for z in range(16, 21):
            if x in (16, 20) or z in (16, 20):
                put(x, 5, z, q["canopy"], {"type": "bottom", "waterlogged": "false"})
    put(SQ_C, 5, SQ_C, q["canopy"], {"type": "bottom", "waterlogged": "false"})
    put(SQ_C, 3, SQ_C, "bell", {"attachment": "ceiling", "facing": "north", "powered": "false"}, {"id": "minecraft:bell"})
    # benches facing the bell
    bench = STYLES[v]["chair"]
    for (x, z, facing) in ((13, 16, "west"), (13, 20, "west"), (23, 16, "east"), (23, 20, "east"),
                           (16, 13, "north"), (20, 13, "north"), (16, 23, "south"), (20, 23, "south")):
        put(x, 1, z, bench, {"facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false"})
    # villagers, the village golem and cats like the vanilla town centres
    floor = "minecraft:" + q["square"]
    for (x, z, pool) in ((13, 18, f"minecraft:village/{v}/villagers"), (23, 18, f"minecraft:village/{v}/villagers"),
                         (18, 13, "minecraft:village/common/iron_golem"), (18, 23, "minecraft:village/common/cats")):
        t.jigsaw(x, 0, z, "up_north", "minecraft:bottom", "minecraft:bottom", pool, final_state=floor, joint="rollable")
    # streets out of the four sides
    for (x, z, facing) in ((SQ_C, 0, "north"), (SQ_C, SQ - 1, "south"), (0, SQ_C, "west"), (SQ - 1, SQ_C, "east")):
        t.jigsaw(x, 1, z, f"{facing}_up", "minecraft:street", "minecraft:street", f"minecraft:village/{v}/streets",
                 final_state="minecraft:structure_void", joint="aligned")
    # the civic buildings, attached inside the square's own box (jigsaws on the curbs pointing into each lot)
    for (x, z, facing, building) in LOTS:
        t.jigsaw(x, 1, z, f"{facing}_up", "minecraft:building_entrance", "minecraft:building_entrance",
                 f"lacuna:village/{v}/{building}", final_state="minecraft:structure_void", joint="aligned")
    t.save()


BUILDINGS = (village_hall, mint, guild_hall, schoolhouse)


def build(Template):
    for v in STYLES:
        for fn in BUILDINGS:
            fn(Template, v)
        civic_square(Template, v)
