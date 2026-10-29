"""M11 (revision 3): civic buildings for VANILLA villages, one set per vanilla village style (plains, desert,
savanna, taiga, snowy), each built in that style's own vanilla building vocabulary. VillagePools.java appends them to
the vanilla pools minecraft:village/<type>/houses at server start, and the civic square places one of each.

Every building follows the vanilla house conventions (checked against the vanilla NBTs):
  * the entrance jigsaw sits at x=0, y=0 in front of the door on the west side: orientation west_up, name and target
    minecraft:building_entrance, pool minecraft:empty, joint aligned, final_state a stair step up to the door;
  * the floor is y=0 (one block above the ground, like the vanilla houses), the street front is x=1, the door at
    y=1-2, the building extends toward +x; it never reaches past z=0 / z=W+1, so it fits the civic square's pinwheel;
  * minecraft:bottom jigsaws in the floor (up_north) pull an unemployed villager from minecraft:village/<type>/villagers,
    who then claims the building's job site (mint press, judge bench, guild board, lectern); the beds make it a home;
  * no other job-site blocks (barrels, composters, cauldrons, smokers...) so the villagers take the civic professions;
  * interior air is explicit, the outside is left unset (the terrain stays).

The shell is built from volumes (boxes with a wall height and a roof kind): the walls follow the outline of their
union, and the roof is a height field over it (the highest volume wins), so T and L plans and cross gables get proper
valleys, outer and inner corner stairs; stair shapes and pane/fence/wall connections are then computed like the game
does (jigsaw pieces are placed with known shapes, so the templates must already hold them).

Vanilla vocabulary per style (palettes counted from the vanilla village NBTs):
  plains:  cobblestone plinth, oak log corner posts, oak plank / white terracotta walls, stripped oak wall plates,
           steep oak stair roofs with slab ridges and finished gable overhangs, glass panes, torches beside a stair
           hood over the door;
  desert:  smooth sandstone, cut sandstone corners and roof band, flat stepped roofs with slab parapets, jungle button
           beam ends, small window openings, terracotta and glazed terracotta inlays, slab awnings, jungle doors;
  savanna: acacia log frames, acacia planks and orange / yellow terracotta walls, low hipped acacia roofs with a flat
           slab top, fence-post porches;
  taiga:   cobblestone walls (stone bricks for the hall), spruce log frames, spruce plank gables, spruce stair roofs
           with spruce log verges, spruce trapdoor shutters, lanterns;
  snowy:   diorite plinth, stripped spruce log frames, spruce plank walls, stepped spruce plank roofs under snow,
           spruce trapdoor shutters, lanterns, packed ice accents.

Buildings: village_hall (ledger, council table, judge bench; a cupola on the ridge), mint (mint press, strongbox, a
chimney), guild_hall (one guild board under each guild banner; T plan) and schoolhouse (school desks, lectern,
blackboard, bookshelves; a front cross gable).

Each building has a second design with its own silhouette (village/<type>/alt/<building>, same footprint, entrance and
functional blocks, so it fits every slot the first one fits; the single-building pools pick either at random):
  village_hall: a side-gabled hall behind a square tower over the entrance (pyramid roof with a spire, a balcony on a
                beam over the door, a lantern room reached by a ladder), a back chimney;
  mint:         a counting house: a low stone shop wing with the copper teller counter in front of a tall two-storey
                treasury (stone ground floor with barred vault windows, front gable), two chimneys;
  guild_hall:   a great hall behind an open loggia (columns, a catslide roof, the guild banners on the columns),
                stained-glass windows in the guild colours, a forge chimney;
  schoolhouse:  a front-gabled school behind a columned porch, with a belfry and spire on the ridge.
Every building names its role on a hanging sign (translated: lacuna.civic.sign.<building>.1/.2), and keeps an apron of
explicit air in front of its facade so a hill or a grass tuft never blocks the door; planters flank the plainer facades.
Where a taller volume rises above a lower roof (towers, the treasury) its walls continue above that roof.

civic_square: a town centre (appended to minecraft:village/<type>/town_centers): a terrain-following paved plaza with
lamps, benches, flower beds and two market stalls under striped awnings, the bell under a rigid pavilion (civic_bell:
posts on stone feet, a beam ring, a hipped roof in the style's vocabulary, lanterns under the beams) in the middle, the
four civic buildings in a pinwheel around it (each a rigid piece at its own ground height, like the vanilla houses
along a street) and four street exits that continue the village outward like the vanilla town centres.
"""
import json

L = "lacuna:"

# roof kinds: "gable" (stairs + slab ridge), "stepped" (full blocks under snow, vanilla snowy), "hip" (low hip with a
# flat slab top, vanilla savanna), "flat" (vanilla desert)
STYLES = {
    "plains": dict(base="cobblestone", floor="oak_planks", wall="oak_planks", hall_wall="white_terracotta", post="oak_log",
                   plate="stripped_oak_log", gable="oak_planks", roof="oak_stairs", ridge="oak_slab", fill="oak_planks",
                   kind="gable", verge=None, step="cobblestone_stairs", door="oak_door", window="glass_pane",
                   shutter=None, fence="oak_fence", chair="oak_stairs", accent="white_terracotta",
                   carpet="yellow_carpet", chimney="cobblestone", banner="yellow", base_h=1, porch_posts=False,
                   light="wall_torch", pot="potted_poppy", pot2="potted_dandelion", flat=False),
    "desert": dict(base="cut_sandstone", floor="smooth_sandstone", wall="smooth_sandstone", hall_wall="smooth_sandstone",
                   post="cut_sandstone", plate="cut_sandstone", gable="smooth_sandstone", roof="smooth_sandstone",
                   ridge="smooth_sandstone_slab", fill="smooth_sandstone", kind="flat", verge=None,
                   step="smooth_sandstone_stairs", door="jungle_door", window="opening", shutter=None,
                   fence="sandstone_wall", chair="jungle_stairs", accent="terracotta", carpet="lime_carpet",
                   chimney="cut_sandstone", banner="lime", base_h=1, porch_posts=False, light="wall_torch",
                   pot="potted_cactus", pot2="potted_dead_bush", flat=True),
    "savanna": dict(base="terracotta", floor="acacia_planks", wall="acacia_planks", hall_wall="orange_terracotta",
                    post="acacia_log", plate="acacia_log", gable="acacia_planks", roof="acacia_stairs", ridge="acacia_slab",
                    fill="acacia_planks", kind="hip", verge=None, step="acacia_stairs", door="acacia_door",
                    window="glass_pane", shutter=None, fence="acacia_fence", chair="acacia_stairs",
                    accent="yellow_terracotta", carpet="orange_carpet", chimney="red_terracotta", banner="orange", base_h=1,
                    porch_posts=True, light="wall_torch", pot="potted_red_tulip", pot2="potted_acacia_sapling", flat=False),
    "taiga": dict(base="cobblestone", floor="spruce_planks", wall="spruce_planks", hall_wall="spruce_planks",
                  post="spruce_log", plate="spruce_log", gable="spruce_planks", roof="spruce_stairs", ridge="spruce_slab",
                  fill="spruce_planks", kind="gable", verge="spruce_log", step="cobblestone_stairs", door="spruce_door",
                  window="glass_pane", shutter="spruce_trapdoor", fence="spruce_fence", chair="spruce_stairs",
                  accent="mossy_cobblestone", carpet="purple_carpet", chimney="cobblestone", banner="purple", base_h=2,
                  porch_posts=False, light="lantern", pot="potted_fern", pot2="potted_spruce_sapling", flat=False),
    "snowy": dict(base="diorite", floor="spruce_planks", wall="spruce_planks", hall_wall="spruce_planks",
                  post="stripped_spruce_log", plate="stripped_spruce_log", gable="spruce_planks", roof="spruce_stairs",
                  ridge="spruce_slab", fill="spruce_planks", kind="stepped", verge=None, step="diorite_stairs",
                  door="spruce_door", window="glass_pane", shutter="spruce_trapdoor", fence="spruce_fence",
                  chair="spruce_stairs", accent="packed_ice", carpet="light_blue_carpet", chimney="diorite",
                  banner="light_blue", base_h=1, porch_posts=False, light="lantern", pot="potted_blue_orchid",
                  pot2="potted_spruce_sapling", flat=False),
}

# the wood of the role signs (the style's door wood), the planters' soil and plants
for _v, (_wood, _soil, _plants) in {
        "plains": ("oak", "grass_block", ("poppy", "dandelion", "azure_bluet", "oxeye_daisy")),
        "desert": ("jungle", "sand", ("dead_bush",)),
        "savanna": ("acacia", "coarse_dirt", ("orange_tulip", "dandelion", "red_tulip")),
        "taiga": ("spruce", "podzol", ("fern", "lily_of_the_valley", "poppy")),
        "snowy": ("spruce", "grass_block", ("lily_of_the_valley", "azure_bluet", "blue_orchid"))}.items():
    STYLES[_v].update(wood=_wood, soil=_soil, plants=_plants)

SQUARES = {
    "plains": dict(ground="grass_block", path="dirt_path", curb="cobblestone", square="stone_bricks", square2="polished_andesite",
                   post="oak_fence", canopy="oak_slab", flowers=("poppy", "dandelion", "oxeye_daisy", "cornflower")),
    "desert": dict(ground="sand", path="smooth_sandstone", curb="cut_sandstone", square="cut_sandstone", square2="smooth_sandstone",
                   post="sandstone_wall", canopy="smooth_sandstone_slab", flowers=("dead_bush", "cactus")),
    "savanna": dict(ground="grass_block", path="dirt_path", curb="coarse_dirt", square="terracotta", square2="orange_terracotta",
                    post="acacia_fence", canopy="acacia_slab", flowers=("poppy", "dandelion", "orange_tulip", "allium")),
    "taiga": dict(ground="grass_block", path="dirt_path", curb="cobblestone", square="cobblestone", square2="mossy_cobblestone",
                  post="spruce_fence", canopy="spruce_slab", flowers=("fern", "sweet_berry_bush", "poppy", "lily_of_the_valley")),
    "snowy": dict(ground="snow_block", path="dirt_path", curb="diorite", square="polished_diorite", square2="polished_andesite",
                  post="spruce_fence", canopy="spruce_slab", flowers=("blue_orchid", "lily_of_the_valley", "azure_bluet")),
}

GUILDS = (("smiths", "gray", "minecraft:half_vertical"), ("gemcutters", "red", "minecraft:rhombus"),
          ("scholars", "purple", "minecraft:circle"))

DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
NOT_FULL = ("_stairs", "_slab", "_fence", "_wall", "_pane", "_door", "_trapdoor", "_carpet", "_bed", "torch", "lantern",
            "chain", "_banner", "_button", "campfire", "chest", "lectern", "anvil", "ladder", "flower_pot", "iron_bars", "air",
            "structure_void", "jigsaw", "_fence_gate", "pressure_plate", "snow", "bell", "_sign", "decorated_pot",
            "lightning_rod", "amethyst_cluster")


def is_log(name):
    return name.endswith("_log") or name.endswith("_wood")


def mc(name):
    return name if ":" in name else "minecraft:" + name


def full_block(name):
    if name is None or name.startswith("lacuna:"):
        return False
    n = name.split(":", 1)[1]
    return not (n.startswith("potted_") or any(n.endswith(s) for s in NOT_FULL))


def family(name):
    if name is None:
        return None
    n = name.split(":", 1)[1]
    if n.endswith("glass_pane") or n == "iron_bars":
        return "pane"
    if n.endswith("_fence"):
        return "fence"
    if n.endswith("_wall") and not n.endswith("_wall_banner"):
        return "wall"
    return None


class Vol:
    """A box of the plan: walls x0..x1, z0..z1 up to y=H, then its roof ('x' / 'z': gable with the ridge along that axis,
    'hip', 'flat')."""

    def __init__(self, x0, x1, z0, z1, H, ridge, tiers=None):
        self.x0, self.x1, self.z0, self.z1, self.H, self.ridge = x0, x1, z0, z1, H, ridge
        self.tiers = tiers  # hip roofs: rings of stairs before the flat top (None: the building's default)

    def inside(self, x, z):
        return self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1

    def rank(self, x, z):
        """Roof row of a column: 0 on the overhang, 1 over the walls, rising toward the ridge (None: not under it)."""
        if self.ridge == "flat":
            return 0 if self.inside(x, z) else None
        if not (self.x0 - 1 <= x <= self.x1 + 1 and self.z0 - 1 <= z <= self.z1 + 1):
            return None
        dz = min(z - (self.z0 - 1), (self.z1 + 1) - z)
        dx = min(x - (self.x0 - 1), (self.x1 + 1) - x)
        return dz if self.ridge == "x" else dx if self.ridge == "z" else min(dx, dz)


class Civic:
    """One civic building: front wall x=1 (door at z=zc), floor y=0, entrance jigsaw at (0, 0, zc)."""

    def __init__(self, Template, vtype, name, D, W, H, alt=False):
        self.Template = Template
        self.name = name
        self.alt = alt  # the second design: village/<type>/alt/<name>
        self.v = vtype
        self.p = dict(STYLES[vtype])
        self.D, self.W, self.H = D, W, H
        self.zc = (W + 1) // 2
        self.blocks = {}
        self.jigsaws = []
        self.vols = []
        self.wall = self.p["wall"]
        self.gable = self.p["gable"]
        self.base = self.p["base"]
        self.base_h = self.p["base_h"]
        self.posts = set()
        self.tiers = 2  # hip roofs: rings of stairs before the flat slab top
        self.band = None  # flat roofs: a colour band in the top row of the walls (the desert's terracotta inlays)

    # ------------------------------------------------------------------ primitives
    def set(self, x, y, z, name, props=None, nbt=None):
        self.blocks[(x, y, z)] = (mc(name), dict(props or {}), nbt)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def pillar(self, x, y, z, name, axis="y"):
        self.set(x, y, z, name, {"axis": axis} if is_log(name) else None)

    def stairs(self, x, y, z, facing, block=None, half="bottom"):
        self.set(x, y, z, block or self.p["roof"], {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})

    def slab(self, x, y, z, block, kind="bottom"):
        self.set(x, y, z, block, {"type": kind, "waterlogged": "false"})

    def connected(self, x, y, z, name):
        """Panes, bars, fences and walls: the connections are filled in by fix_shapes()."""
        self.set(x, y, z, name, {"waterlogged": "false"})

    def trapdoor(self, x, y, z, facing, block=None, open_=True, half="bottom"):
        self.set(x, y, z, block or self.p["shutter"], {"facing": facing, "half": half, "open": "true" if open_ else "false",
                                                       "powered": "false", "waterlogged": "false"})

    def lantern(self, x, y, z, hanging=False):
        self.set(x, y, z, "lantern", {"hanging": "true" if hanging else "false", "waterlogged": "false"})

    def wall_torch(self, x, y, z, facing):
        self.set(x, y, z, "wall_torch", {"facing": facing})

    def lantern_on_chain(self, x, z, low=None):
        """A lantern hanging on chains from the ceiling (roof fill, tie beam or flat roof) down to the top of the walls."""
        low = self.H if low is None else low
        y = low + 1
        while self.get(x, y, z) in (None, "minecraft:air") and y < 64:
            y += 1
        for yy in range(low + 1, y):
            self.set(x, yy, z, "chain", {"axis": "y", "waterlogged": "false"})
        self.lantern(x, low, z, hanging=True)

    def wall_banner(self, x, y, z, facing, color, pattern, pattern_color="white"):
        """pattern: one pattern id (in pattern_color) or a list of (pattern, colour) layers."""
        layers = pattern if isinstance(pattern, list) else [(pattern, pattern_color)]
        self.set(x, y, z, f"{color}_wall_banner", {"facing": facing},
                 {"id": "minecraft:banner", "patterns": [{"color": c, "pattern": pat} for pat, c in layers]})

    def chest(self, x, y, z, facing, loot=None):
        nbt = {"id": "minecraft:chest"}
        if loot:
            nbt["LootTable"] = loot
        self.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"}, nbt)

    def bed(self, x, z, facing):
        """A bed on the floor: foot at (x, z), head one block towards `facing`. Beds are what villagers count as homes:
        they claim them, sleep in them and run to them when the bell rings."""
        dx, dz = DIRS[facing]
        name = f"{self.p['banner']}_bed"
        self.set(x, 1, z, name, {"facing": facing, "occupied": "false", "part": "foot"})
        self.set(x + dx, 1, z + dz, name, {"facing": facing, "occupied": "false", "part": "head"})

    def villager_spot(self, x, z):
        """minecraft:bottom jigsaw in the floor: an (usually unemployed) villager of this style spawns above it."""
        self.jigsaws.append(((x, 0, z), ("up_north", "minecraft:bottom", "minecraft:bottom", f"minecraft:village/{self.v}/villagers"),
                             dict(final_state="minecraft:" + self.p["floor"], joint="rollable")))

    # ------------------------------------------------------------------ plan
    def volume(self, x0, x1, z0, z1, H=None, ridge="x", tiers=None):
        """ridge 'x' / 'z': gable along that axis; 'hip': hipped (with tiers=9: a full pyramid, e.g. a tower)."""
        kind = self.p["kind"]
        if kind == "flat":
            ridge = "flat"
        elif kind == "hip":
            ridge = "hip"
        self.vols.append(Vol(x0, x1, z0, z1, self.H if H is None else H, ridge, tiers))

    def tiers_of(self, v):
        return self.tiers if v.tiers is None else v.tiers

    def in_plan(self, x, z):
        return any(v.inside(x, z) for v in self.vols)

    def level(self, x, z):
        """(roof y, roof row, owning volume, uncapped roof y) of a column: the highest volume over it wins. Hip roofs
        stop rising after `tiers` rings (a flat slab top), the uncapped height still gives the stairs their facing."""
        best = None
        for v in self.vols:
            r = v.rank(x, z)
            if r is None:
                continue
            raw = v.H + 1 + r
            y = v.H + 1 + min(r, self.tiers_of(v)) if v.ridge == "hip" else raw
            if best is None or (raw, y) > (best[3], best[0]):
                best = (y, r, v, raw)
        return best

    def outward(self, x, z):
        for f, (dx, dz) in DIRS.items():
            if not self.in_plan(x + dx, z + dz):
                return f
        return None

    def along(self, x, z):
        """Axis of the wall a perimeter column belongs to ('x' or 'z'), None at corners."""
        px = all(self.is_perimeter(x + d, z) for d in (-1, 1))
        pz = all(self.is_perimeter(x, z + d) for d in (-1, 1))
        if px and not pz:
            return "x"
        if pz and not px:
            return "z"
        o = self.outward(x, z)
        if o in ("north", "south") and not pz:
            return "x"
        if o in ("east", "west") and not px:
            return "z"
        return None

    def is_perimeter(self, x, z):
        if not self.in_plan(x, z):
            return False
        return any(not self.in_plan(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1))

    def is_corner(self, x, z):
        if not self.is_perimeter(x, z):
            return False
        hx = any(self.is_perimeter(x + d, z) and self.edge_pair(x, z, x + d, z) for d in (-1, 1))
        hz = any(self.is_perimeter(x, z + d) and self.edge_pair(x, z, x, z + d) for d in (-1, 1))
        return hx and hz

    def edge_pair(self, x, z, x2, z2):
        """Two adjacent perimeter columns lie on the same wall (they share an outside side)."""
        if x == x2:
            return not self.in_plan(x - 1, z) and not self.in_plan(x2 - 1, z2) or \
                not self.in_plan(x + 1, z) and not self.in_plan(x2 + 1, z2)
        return not self.in_plan(x, z - 1) and not self.in_plan(x2, z2 - 1) or \
            not self.in_plan(x, z + 1) and not self.in_plan(x2, z2 + 1)

    # ------------------------------------------------------------------ shell
    def shell(self, windows=(), door=None, window_h=None, extra_posts=(), gable_windows=()):
        p = self.p
        flat = p["kind"] == "flat"
        cols = sorted({(x, z) for v in self.vols for x in range(v.x0, v.x1 + 1) for z in range(v.z0, v.z1 + 1)})
        self.cols = cols
        self.corners = {c for c in cols if self.is_corner(*c)}
        if flat:  # every box keeps its own corner piers, also where it rises above a lower one
            for v in self.vols:
                self.corners |= {(x, z) for x in (v.x0, v.x1) for z in (v.z0, v.z1)}
        self.posts = self.corners | set(extra_posts)
        lv = {c: self.level(*c) for c in cols}
        # floor
        for (x, z) in cols:
            self.set(x, 0, z, self.base if self.is_perimeter(x, z) else p["floor"])
        # walls: a column is wall at height y where the outside (or a lower roof) touches it: in a pitched building,
        # the roof of another (lower) volume, e.g. a tower's or a treasury's walls continue above the hall's roof
        for (x, z) in cols:
            top, r, v, _ = lv[(x, z)]
            for y in range(1, top):
                exposed = False
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        n = (x + dx, z + dz)
                        if n not in lv:
                            exposed = True
                        elif flat and y >= lv[n][0]:
                            exposed = True
                        elif not flat and lv[n][2] is not v and y >= lv[n][0]:
                            exposed = True
                if not exposed:
                    continue
                self.set(x, y, z, *self.wall_block(x, y, z, v, flat))
        # windows
        wh = window_h or tuple(range(2, min(3, self.H) + 1))
        if flat:
            wh = (2,)
        for (x, z, *ys) in windows:
            o = self.outward(x, z)
            axis = "x" if o in ("north", "south") else "z"
            for y in (ys or wh):
                if p["window"] == "opening":  # vanilla desert: plain holes, some with a potted cactus
                    self.set(x, y, z, p["pot"] if (x + z) % 3 == 0 else "air")
                else:
                    self.connected(x, y, z, p["window"])
                if p["shutter"]:
                    ox, oz = DIRS[o]
                    for s in (-1, 1):
                        ax, az = (s, 0) if axis == "x" else (0, s)
                        wx, wz = x + ax, z + az
                        if (wx, wz) in self.posts or not self.is_perimeter(wx, wz) or any(
                                (wx, wz) == tuple(w[:2]) for w in windows) or (door and (wx, wz) == door):
                            continue
                        self.trapdoor(wx + ox, y, wz + oz, o)
        for (x, y, z) in gable_windows:
            o = self.outward(x, z)
            self.connected(x, y, z, p["window"] if p["window"] != "opening" else "air")
        # roof
        if flat:
            self.flat_roof(lv)
        else:
            self.pitched_roof(lv)
        # interior air under the roof
        for (x, z) in cols:
            top = lv[(x, z)][0] - (1 if flat else 2)
            for y in range(1, top + 1):
                if self.get(x, y, z) is None:
                    self.set(x, y, z, "air")
        # door
        if door:
            dx, dz = door
            o = self.outward(dx, dz)
            for half, y in (("lower", 1), ("upper", 2)):
                self.set(dx, y, dz, p["door"], {"facing": OPP[o], "half": half, "hinge": "right", "open": "false",
                                               "powered": "false"})
        self.lv = lv

    def wall_block(self, x, y, z, v, flat):
        p = self.p
        if (x, z) in self.posts and (y <= v.H + (0 if flat else 1)):
            return p["post"], {"axis": "y"} if is_log(p["post"]) else None
        if y <= self.base_h:
            return self.base, None
        if flat and self.band and y == v.H:
            return self.band, None
        if y <= v.H:
            return self.wall, None
        if not flat and y == v.H + 1:
            a = self.along(x, z) or "x"
            return p["plate"], {"axis": a} if is_log(p["plate"]) else None
        return self.gable, None

    def pitched_roof(self, lv):
        p = self.p
        kind = p["kind"]
        cols = {}
        for v in self.vols:
            for x in range(v.x0 - 1, v.x1 + 2):
                for z in range(v.z0 - 1, v.z1 + 2):
                    if (x, z) not in cols:
                        cols[(x, z)] = self.level(x, z)

        def L(c):
            return cols[c][3] if c in cols else None

        for c, (yy, r, v, y) in sorted(cols.items()):
            x, z = c
            inside = self.in_plan(x, z)
            capped = kind == "hip" and r > self.tiers_of(v)
            higher = [f for f, (dx, dz) in DIRS.items() if L((x + dx, z + dz)) is not None and L((x + dx, z + dz)) > y]
            facing = None
            if higher:
                slope = ("north", "south") if v.ridge == "x" else ("east", "west") if v.ridge == "z" else tuple(DIRS)
                pref = [f for f in higher if f in slope]
                facing = (pref or higher)[0]
            else:
                diag = [(dx, dz) for dx in (-1, 1) for dz in (-1, 1)
                        if L((x + dx, z + dz)) is not None and L((x + dx, z + dz)) > y]
                if diag:
                    dx, dz = diag[0]
                    if v.ridge == "x":
                        facing = "south" if dz > 0 else "north"
                    else:
                        facing = "east" if dx > 0 else "west"
                else:
                    for f, (dx, dz) in DIRS.items():
                        ox, oz = DIRS[OPP[f]]
                        a, b = L((x + dx, z + dz)), L((x + ox, z + oz))
                        if a == y and (b is None or b < y) and (v.ridge == "hip" or (f in ("north", "south")) == (v.ridge == "x")):
                            facing = f
                            break
            gable_end = not inside and v.ridge in ("x", "z") and (
                (v.ridge == "x" and x in (v.x0 - 1, v.x1 + 1)) or (v.ridge == "z" and z in (v.z0 - 1, v.z1 + 1)))
            if kind == "stepped":
                self.set(x, y, z, p["fill"])
                self.set(x, y + 1, z, "snow", {"layers": "1"})
                if not inside:
                    self.slab(x, y - 1, z, p["ridge"], "top")
            elif capped:
                self.slab(x, yy, z, p["ridge"], "top")
            elif facing is None:
                self.slab(x, yy, z, p["ridge"])
            elif gable_end and p["verge"]:
                self.pillar(x, yy, z, p["verge"], axis=v.ridge)
            else:
                self.stairs(x, yy, z, facing)
                if gable_end and r >= 1:
                    self.stairs(x, yy - 1, z, OPP[facing], half="top")
            if inside and not self.is_perimeter(x, z) and yy - 1 > v.H + 1:
                self.set(x, yy - 1, z, p["fill"])
        self.roof_cols = cols

    def flat_roof(self, lv):
        p = self.p
        for (x, z), (y, r, v, _) in lv.items():
            edge = any((x + dx, z + dz) not in lv or lv[(x + dx, z + dz)][0] < y for dx in (-1, 0, 1) for dz in (-1, 0, 1))
            self.set(x, y, z, p["plate"] if edge else p["roof"])
            if edge:
                if (x, z) in self.corners:
                    self.set(x, y + 1, z, p["post"])
                else:
                    self.slab(x, y + 1, z, p["ridge"])
        # beam ends (vigas) under the roof band, every other block of the outer walls
        for (x, z), (y, r, v, _) in lv.items():
            o = self.outward(x, z)
            if o is None or (x, z) in self.posts or (x + z) % 2:
                continue
            ox, oz = DIRS[o]
            if self.get(x + ox, y - 1, z + oz) is None:
                self.set(x + ox, y - 1, z + oz, "jungle_button", {"face": "wall", "facing": o, "powered": "false"})
        self.roof_cols = dict(lv)

    def roof_y(self, x, z):
        return self.lv[(x, z)][0]

    # ------------------------------------------------------------------ details
    def entrance(self, lights=True):
        """The step (entrance jigsaw), a hood or awning over the door unless the eave already covers it, lights."""
        p, zc = self.p, self.zc
        step = f"minecraft:{p['step']}[facing=east,half=bottom,shape=straight,waterlogged=false]"
        self.jigsaws.append(((0, 0, zc), ("west_up", "minecraft:building_entrance", "minecraft:building_entrance", "minecraft:empty"),
                             dict(final_state=step, joint="aligned")))
        if not lights:
            return
        hood = self.get(0, 3, zc) is None and self.get(0, 4, zc) is None
        if hood:
            for z in (zc - 1, zc, zc + 1):
                if p["kind"] == "flat":
                    self.slab(0, 3, z, p["ridge"], "top")
                else:
                    self.stairs(0, 3, z, "east")
        if hood and p["porch_posts"]:
            for z in (zc - 1, zc + 1):
                for y in (0, 1, 2):
                    self.connected(0, y, z, p["fence"])
            for z in (zc - 2, zc + 2):
                if self.get(0, 2, z) is None and self.get(0, 3, z) is None:
                    self.wall_torch(0, 2, z, "west")
        elif p["light"] == "lantern":
            for z in (zc - 1, zc + 1):
                y = 3 if hood or self.get(0, 4, z) is not None else None
                if y is not None:
                    self.lantern(0, y - 1 if hood else 3, z, hanging=True)
        else:
            for z in (zc - 1, zc + 1):
                self.wall_torch(0, 2, z, "west")

    def front_details(self, fx=1):
        """Flower pots beside the step, a glazed terracotta medallion over desert doors (facade at x=fx), ice lamps in
        the snow."""
        p, zc = self.p, self.zc
        dz = 2 if p["porch_posts"] else 1
        for z, pot in ((zc - dz, p["pot"]), (zc + dz, p["pot2"])):
            if self.get(0, 0, z) is None:
                self.set(0, 0, z, pot)
        if p["kind"] == "flat":
            for y in range(self.H, 2, -1):
                if self.get(fx, y, zc) == mc(self.wall) and self.get(fx - 1, y, zc) is None:
                    self.set(fx, y, zc, "lime_glazed_terracotta", {"facing": "west"})
                    break
        if self.v == "snowy":
            for z in (1, self.W):
                if self.get(0, 0, z) is None and self.get(0, 1, z) is None:
                    self.set(0, 0, z, "packed_ice")
                    self.lantern(0, 1, z)

    def cupola(self, cx, cz):
        """A small open lantern turret on the ridge (the civic mark of the hall)."""
        p = self.p
        cells = [(cx + dx, cz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
        tops = {c: self.roof_cols[c][0] for c in cells}  # stairs, slabs or (stepped) the planks under the snow
        base = max(tops.values()) + (0 if p["kind"] == "gable" else 1)
        for (x, z) in cells:
            for y in range(tops[(x, z)] + 1, base + 1):
                self.set(x, y, z, p["fill"])
            self.set(x, base, z, p["fill"])
        for dx in (-1, 1):
            for dz in (-1, 1):
                for y in (base + 1, base + 2):
                    self.connected(cx + dx, y, cz + dz, p["fence"])
        self.lantern(cx, base + 1, cz)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx, dz) == (0, 0):
                    self.set(cx, base + 3, cz, p["fill"])
                    self.slab(cx, base + 4, cz, p["ridge"])
                else:
                    f = ("east" if dx < 0 else "west") if dx else ("south" if dz < 0 else "north")
                    self.stairs(cx + dx, base + 3, cz + dz, f, p["roof"] if p["roof"].endswith("_stairs") else p["step"])

    def clear(self, x, y, z):
        self.blocks.pop((x, y, z), None)

    def tie_beam(self, x, z0, z1, y=None):
        y = self.H + 1 if y is None else y
        for z in range(z0, z1 + 1):
            if self.get(x, y, z) in (None, "minecraft:air"):
                self.pillar(x, y, z, self.p["plate"], axis="z")

    def chimney(self, x, z, top):
        for y in range(0, top + 1):
            self.set(x, y, z, self.p["chimney"])
        self.set(x, top + 1, z, "campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})

    # ------------------------------------------------------------------ character
    def sign_text(self):
        key = f"lacuna.civic.sign.{self.name}"
        empty = json.dumps({"text": ""})
        text = {"messages": [empty, json.dumps({"translate": key + ".1"}), json.dumps({"translate": key + ".2"}), empty],
                "color": "black", "has_glowing_text": False}
        return {"id": "minecraft:hanging_sign", "is_waxed": True, "front_text": text, "back_text": dict(text)}

    def wall_sign(self, x, y, z, facing):
        """The building's role on a hanging sign from a wall bracket: it hangs off the block beside it (east or west
        of a north/south facing sign), its two faces along the street."""
        self.set(x, y, z, f"{self.p['wood']}_wall_hanging_sign", {"facing": facing, "waterlogged": "false"}, self.sign_text())

    def ceiling_sign(self, x, y, z, rotation=4):
        """The role sign on two chains under a beam, its face toward the street (rotation 4: west)."""
        self.set(x, y, z, f"{self.p['wood']}_hanging_sign", {"attached": "false", "rotation": str(rotation),
                                                             "waterlogged": "false"}, self.sign_text())

    def role_sign(self, spots):
        """The wall hanging sign at the first free spot (x, y, z) in front of a full facade block at x + 1."""
        for (x, y, z) in spots:
            if self.get(x, y, z) is None and full_block(self.get(x + 1, y, z)):
                self.wall_sign(x, y, z, "north")
                return
        raise AssertionError(f"{self.v}/{self.name}: no spot for the role sign among {spots}")

    def planter(self, z, x=0):
        """A raised bed by the facade: the style's soil with one of its plants."""
        if self.get(x, 0, z) is None and self.get(x, 1, z) is None:
            soil = self.p["soil"]
            self.set(x, 0, z, soil, {"snowy": "false"} if soil in ("grass_block", "podzol") else None)
            plants = self.p["plants"]
            self.set(x, 1, z, plants[(z + len(self.name)) % len(plants)])

    def apron(self, x=0):
        """Explicit air in front of the facade (walking level and two above): a hill, a bush or a grass tuft never
        blocks the door or the windows (the terrain adaptation alone leaves slopes there)."""
        steps = {pos for pos, _, _ in self.jigsaws}
        for z in range(1, self.W + 1):
            for y in range(0, 3):
                if self.get(x, y, z) is None and (x, y, z) not in steps:
                    self.set(x, y, z, "air")

    def ladder(self, x, z, y0, y1, facing):
        """A ladder column; facing points away from the wall it hangs on."""
        for y in range(y0, y1 + 1):
            self.set(x, y, z, "ladder", {"facing": facing, "waterlogged": "false"})

    def close_wall(self, cells, y0, y1, block=None):
        """Wall blocks in the given columns from y0 to y1 where there is nothing solid yet."""
        for (x, z) in cells:
            for y in range(y0, y1 + 1):
                if self.get(x, y, z) in (None, "minecraft:air"):
                    self.set(x, y, z, block or self.wall)

    def lean_to(self, x0, x1, z0, z1, y0):
        """A roof sloping down toward the street: y0 at x0, one higher per block toward x1 (snowy: stepped planks)."""
        p = self.p
        for x in range(x0, x1 + 1):
            y = y0 + x - x0
            for z in range(z0, z1 + 1):
                if p["kind"] == "stepped":
                    self.set(x, y, z, p["fill"])
                    self.set(x, y + 1, z, "snow", {"layers": "1"})
                else:
                    self.stairs(x, y, z, "east")

    def belfry(self, cx, cz):
        """A slender open belfry on the ridge: corner posts, a lantern hanging inside, a stair cap and a copper spire."""
        p = self.p
        cells = [(cx + dx, cz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
        tops = {c: self.roof_cols[c][0] for c in cells}
        base = max(tops.values()) + (0 if p["kind"] == "gable" else 1)
        for (x, z) in cells:
            for y in range(tops[(x, z)] + 1, base + 1):
                self.set(x, y, z, p["fill"])
            self.set(x, base, z, p["fill"])
        for dx in (-1, 1):
            for dz in (-1, 1):
                for y in (base + 1, base + 2):
                    self.connected(cx + dx, y, cz + dz, p["fence"])
        self.lantern(cx, base + 2, cz, hanging=True)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx, dz) == (0, 0):
                    self.set(cx, base + 3, cz, p["fill"])
                    self.set(cx, base + 4, cz, p["fill"])
                else:
                    f = ("east" if dx < 0 else "west") if dx else ("south" if dz < 0 else "north")
                    self.stairs(cx + dx, base + 3, cz + dz, f, p["roof"] if p["roof"].endswith("_stairs") else p["step"])
        self.set(cx, base + 5, cz, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})

    def stained(self, cells, colors):
        """Glass panes at the given (x, y, z) become stained panes, cycling through the colours."""
        i = 0
        for (x, y, z) in cells:
            if self.get(x, y, z) == "minecraft:glass_pane":
                self.connected(x, y, z, f"{colors[i % len(colors)]}_stained_glass_pane")
                i += 1

    # ------------------------------------------------------------------ output
    def fix_shapes(self):
        B = self.blocks

        def name(pos):
            b = B.get(pos)
            return b[0] if b else None

        for pos, (n, props, nbt) in list(B.items()):
            x, y, z = pos
            if n.endswith("_stairs"):
                props["shape"] = self.stair_shape(pos, props)
            fam = family(n)
            if fam:
                conn = {}
                for f, (dx, dz) in DIRS.items():
                    m = name((x + dx, y, z + dz))
                    mf = family(m)
                    ok = full_block(m) or (mf == fam) or (fam == "pane" and mf == "wall") or (fam == "wall" and mf == "pane")
                    if m and m.endswith("_fence_gate") and fam in ("fence", "wall"):
                        ok = True
                    conn[f] = ok
                if fam == "wall":
                    straight = (conn["north"] and conn["south"] and not conn["east"] and not conn["west"]) or \
                               (conn["east"] and conn["west"] and not conn["north"] and not conn["south"])
                    above = name((x, y + 1, z))
                    for f in DIRS:
                        props[f] = "low" if conn[f] else "none"
                    props["up"] = "false" if straight and above in (None, "minecraft:air") else "true"
                else:
                    for f in DIRS:
                        props[f] = "true" if conn[f] else "false"
                props["waterlogged"] = "false"

    def stair_shape(self, pos, props):
        B = self.blocks
        x, y, z = pos
        f = props["facing"]
        half = props.get("half", "bottom")

        def st(dirn):
            dx, dz = DIRS[dirn]
            b = B.get((x + dx, y, z + dz))
            if b and b[0].endswith("_stairs") and b[1].get("half", "bottom") == half:
                return b[1]["facing"]
            return None

        def can_take(dirn):
            dx, dz = DIRS[dirn]
            b = B.get((x + dx, y, z + dz))
            return not (b and b[0].endswith("_stairs") and b[1]["facing"] == f and b[1].get("half", "bottom") == half)

        axis = lambda d: "x" if d in ("east", "west") else "z"
        d1 = st(f)
        if d1 and axis(d1) != axis(f) and can_take(OPP[d1]):
            return "outer_left" if d1 == CCW[f] else "outer_right"
        d2 = st(OPP[f])
        if d2 and axis(d2) != axis(f) and can_take(d2):
            return "inner_left" if d2 == CCW[f] else "inner_right"
        return "straight"

    def save(self):
        self.fix_shapes()
        sx = max(p[0] for p in self.blocks) + 1
        sy = max(p[1] for p in self.blocks) + 1
        sz = max(p[2] for p in self.blocks) + 1
        assert min(p[0] for p in self.blocks) >= 0 and min(p[2] for p in self.blocks) >= 0, self.name
        assert sz <= self.W + 2, f"{self.v}/{self.name} is wider than its slot ({sz} > {self.W + 2})"
        assert sx <= self.D + 2, f"{self.v}/{self.name} is deeper than its slot ({sx} > {self.D + 2})"
        path = f"village/{self.v}/alt/{self.name}" if self.alt else f"village/{self.v}/{self.name}"
        t = self.Template(path, max(sx, self.D + 2), sy, self.W + 2)
        for (x, y, z), (n, props, nbt) in self.blocks.items():
            t.set(x, y, z, n, props or None, nbt)
        for pos, (orient, name, target, pool), kw in self.jigsaws:
            t.jigsaw(*pos, orient, name, target, pool, **kw)
        t.save()


# ============================================================================================ buildings
def village_hall(Template, v):
    """A front-gabled hall (white terracotta in plains, like the vanilla plains temples) with a lantern cupola on the
    ridge; in the desert a flat-roofed hall with a tower over the court corner."""
    b = Civic(Template, v, "village_hall", D=11, W=9, H=4)
    p, zc = b.p, b.zc
    flat = p["kind"] == "flat"
    b.wall = p["hall_wall"]
    if v == "taiga":
        b.base = "stone_bricks"
    b.volume(1, 11, 1, 9, ridge="x")
    if flat:
        b.volume(8, 11, 1, 4, H=7)
    windows = [(3, 1), (4, 1), (8, 1), (9, 1), (3, 9), (4, 9), (8, 9), (9, 9), (1, 2), (1, 8), (11, 3), (11, 7)]
    gable = [] if p["kind"] in ("hip", "flat") else [(1, b.H + 3, zc), (11, b.H + 3, zc)]
    if flat:
        windows += [(9, 1, 6), (10, 1, 6), (11, 2, 6), (11, 3, 6)]
    b.shell(windows=windows, door=(1, zc), extra_posts=((6, 1), (6, 9), (1, 3), (1, 7)), gable_windows=gable)
    if not flat:
        b.tie_beam(6, 2, 8)
    b.entrance()
    b.front_details()
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
    b.set(10, 1, 7, p["pot"])
    b.chest(10, 1, 8, "west")
    # facade banners on the front posts
    for z in (zc - 2, zc + 2):
        b.wall_banner(0, 3, z, "west", p["banner"], "minecraft:stripe_center", "white")
    for x in (4, 8):
        b.lantern_on_chain(x, zc)
    if not flat:
        b.cupola(6, zc)
    b.bed(3, 8, "west")
    b.bed(3, 2, "west")
    b.villager_spot(3, 7)
    b.role_sign([(0, 4, 7), (0, 4, 3), (0, 3, 1), (0, 3, b.W), (0, 2, 7)])
    b.planter(1)
    b.planter(b.W)
    b.apron()
    b.save()


def mint(Template, v):
    """A small side-gabled workshop with a stone chimney at the back (cobblestone walls in the taiga)."""
    b = Civic(Template, v, "mint", D=9, W=7, H=3)
    p, zc = b.p, b.zc
    flat = p["kind"] == "flat"
    b.base_h = 2  # a stone ground course: the workshop with the furnace
    if v == "taiga":
        b.wall = "cobblestone"
    b.volume(1, 9, 1, 7, ridge="z")
    gable = [] if p["kind"] in ("hip", "flat") else [(5, b.H + 3, 1), (5, b.H + 3, 7)]
    b.shell(windows=[(1, 2), (1, 6), (3, 1), (7, 1), (3, 7), (7, 7), (9, 4)], door=(1, zc),
            extra_posts=((5, 1), (5, 7)), gable_windows=gable)
    b.entrance()
    b.front_details()
    # teller's counter with bars, opening in the middle
    for z in range(2, 7):
        if z == zc:
            continue
        b.slab(5, 1, z, "waxed_cut_copper_slab", "top")
        b.connected(5, 2, z, "iron_bars")
    # the press (Coiner job site), furnace, anvil and the strongbox
    b.set(7, 1, zc, L + "mint_press", {"facing": "west"})
    b.set(8, 1, 2, "furnace", {"facing": "west", "lit": "false"})
    b.set(8, 1, 6, "anvil", {"facing": "north"})
    b.chest(8, 1, 5, "west", loot="lacuna:chests/village_mint")
    b.set(8, 1, 3, p["accent"] if p["accent"] != "packed_ice" else "polished_diorite")
    b.set(8, 2, 3, p["pot2"])
    # waiting bench by the door
    b.stairs(2, 1, 2, "north", p["chair"])
    b.stairs(3, 1, 2, "north", p["chair"])
    # chimney against the back wall behind the furnace, smoking
    top = b.roof_y(9, 2) + (2 if flat else 3)
    b.chimney(10, 2, top)
    b.lantern_on_chain(3, zc)
    b.lantern_on_chain(7, zc)
    b.bed(6, 6, "east")
    b.bed(3, 6, "west")
    b.villager_spot(3, 5)
    b.role_sign([(0, 3, 7), (0, 3, 1), (0, 2, 7), (0, 2, 1)])
    b.planter(1)
    b.planter(b.W)
    b.apron()
    b.save()


def guild_hall(Template, v):
    """A T-plan hall: a front-gabled entrance wing under the guild banners leading into a broad side-gabled hall with
    the three guild boards (stepped flat roofs in the desert, hipped in the savanna)."""
    b = Civic(Template, v, "guild_hall", D=12, W=9, H=4)
    p, zc = b.p, b.zc
    flat = p["kind"] == "flat"
    b.volume(1, 4, 3, 7, H=3 if flat else 4, ridge="x")
    b.volume(4, 12, 1, 9, ridge="z")
    windows = [(6, 1), (10, 1), (6, 9), (10, 9), (4, 2), (4, 8), (2, 3), (3, 3), (2, 7), (3, 7), (12, 2), (12, 8)]
    gable = [] if p["kind"] in ("hip", "flat") else [(1, b.H + 2, zc), (8, b.H + 3, 1), (8, b.H + 3, 9)]
    b.shell(windows=windows, door=(1, zc), extra_posts=((8, 1), (8, 9)), gable_windows=gable)
    if not flat:
        b.tie_beam(8, 2, 8)
    b.entrance()
    b.front_details()
    # one board per guild along the back wall, each under its banner
    for i, (guild, color, pattern) in enumerate(GUILDS):
        z = 3 + i * 2
        b.set(11, 1, z, L + "guild_board", {"facing": "west", "guild": guild})
        b.wall_banner(11, 3, z, "west", color, pattern, "yellow")
    b.chest(11, 1, 2, "west")
    b.set(11, 1, 8, "crafting_table")
    # long feast table with chairs
    for x in range(6, 10):
        b.slab(x, 1, zc, p["ridge"] if not flat else "smooth_stone_slab", "top")
        b.stairs(x, 1, zc - 1 if x % 2 == 0 else zc + 1, "north" if x % 2 == 0 else "south", p["chair"])
    for x in range(2, 6):
        b.set(x, 1, zc, p["carpet"])
    b.set(5, 1, 2, "anvil", {"facing": "east"})
    b.set(5, 1, 8, p["accent"] if p["accent"] != "packed_ice" else "polished_diorite")
    b.set(5, 2, 8, p["pot"])
    for z, color in ((zc - 2, "gray"), (zc + 2, "purple")):
        b.wall_banner(0, 3, z, "west", color, "minecraft:border", "yellow")
    for x in (6, 10):
        b.lantern_on_chain(x, zc)
    b.bed(9, 8, "east")
    b.bed(9, 2, "east")
    b.villager_spot(6, 3)
    b.villager_spot(6, 7)
    b.role_sign([(0, 4, 3), (0, 4, 7), (0, 2, 3)])
    b.apron()
    b.save()


def schoolhouse(Template, v):
    """A side-gabled schoolhouse with a cross gable over the door (in the desert a taller front block)."""
    b = Civic(Template, v, "schoolhouse", D=9, W=9, H=3)
    p, zc = b.p, b.zc
    flat = p["kind"] == "flat"
    b.volume(1, 9, 1, 9, ridge="z")
    b.volume(1, 4, 3, 7, H=4 if flat else 3, ridge="x")
    gable = [] if p["kind"] in ("hip", "flat") else [(1, b.H + 2, zc), (5, b.H + 3, 1), (5, b.H + 3, 9)]
    b.shell(windows=[(1, 2), (1, 8), (3, 1), (7, 1), (3, 9), (7, 9), (9, 2), (9, 8)], door=(1, zc),
            extra_posts=((5, 1), (5, 9), (1, 3), (1, 7)), gable_windows=gable)
    if not flat:
        b.tie_beam(5, 2, 8)
    b.entrance()
    b.front_details()
    # blackboard in front of the back wall, the teacher's lectern before it
    for z in range(3, 8):
        for y in (2, 3):
            b.set(8, y, z, "black_terracotta")
    b.set(7, 1, zc, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    # two rows of desks facing the teacher, an aisle in the middle
    for x in (3, 5):
        for z in (3, 4, 6, 7):
            b.set(x, 1, z, L + "school_desk", {"facing": "east"})
            b.stairs(x - 1, 1, z, "west", p["chair"])
    for x in range(2, 7):
        b.set(x, 1, zc, p["carpet"])
    for z in (2, 8):
        for x in (7, 8):
            b.set(x, 1, z, "bookshelf")
            b.set(x, 2, z, "bookshelf")
    b.set(8, 3, 2, p["pot"])
    b.set(8, 3, 8, p["pot2"])
    for x in (3, 6):
        b.lantern_on_chain(x, zc)
    b.bed(3, 8, "west")
    b.bed(5, 2, "east")
    b.villager_spot(2, 2)
    b.role_sign([(0, 3, 7), (0, 3, 3), (0, 2, 7)])
    b.planter(1)
    b.planter(b.W)
    b.apron()
    b.save()


# ============================================================================================ second designs
def village_hall_alt(Template, v):
    """The hall behind a square tower over the entrance: a side-gabled hall (flat in the desert) and a tower with a
    pyramid roof and a copper spire (stepped under snow, a parapeted block in the desert), a balcony on a beam over the
    door with the role sign hanging under it, a lantern room up a ladder, the village crest, planted beds beside the
    tower, a back chimney."""
    b = Civic(Template, v, "village_hall", D=11, W=9, H=4, alt=True)
    p, zc = b.p, b.zc
    flat = p["kind"] == "flat"
    if flat:
        b.band = p["accent"]
    b.wall = p["hall_wall"]
    if v == "taiga":
        b.base = "stone_bricks"
    th = 9 if flat else 11  # the tower's walls
    b.volume(5, 11, 1, 9, ridge="z")
    b.volume(1, 5, 3, 7, H=th, ridge="hip", tiers=9)
    windows = [(7, 1), (9, 1), (7, 9), (9, 9), (11, 3), (11, 7), (5, 2), (5, 8), (3, 3), (3, 7),
               (3, 3, 6, 7), (3, 7, 6, 7), (1, 4, 6, 7), (1, 6, 6, 7), (3, 3, th - 1), (3, 7, th - 1)]
    gable = [] if p["kind"] in ("hip", "flat") else [(8, b.H + 3, 1), (8, b.H + 3, 9)]
    b.shell(windows=windows, door=(1, zc), extra_posts=((8, 1), (8, 9)), gable_windows=gable)
    # the tower: a floor over the vestibule (a ladder on its north wall), the balcony door, the balcony on its beam
    for x in range(2, 6):
        for z in range(zc - 1, zc + 2):
            b.set(x, 5, z, p["floor"])
    b.ladder(2, zc - 1, 1, 5, "south")
    for half, y in (("lower", 6), ("upper", 7)):
        b.set(1, y, zc, p["door"], {"facing": "east", "half": half, "hinge": "right", "open": "false", "powered": "false"})
    for z in range(zc - 1, zc + 2):
        b.pillar(0, 4, z, p["plate"], axis="z")
        b.slab(0, 5, z, p["ridge"], "top")
        b.connected(0, 6, z, p["fence"])
    b.lantern_on_chain(3, zc, low=th - 1)
    if not flat:
        ay = b.roof_cols[(3, zc)][0]
        b.set(3, ay, zc, p["fill"])
        b.set(3, ay + 1, zc, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    b.wall_banner(0, th if flat else th - 1, zc, "west", p["banner"],
                  [("minecraft:rhombus", "white"), ("minecraft:circle", p["banner"]), ("minecraft:border", "white")])
    b.entrance()
    b.front_details()
    b.ceiling_sign(0, 3, zc)
    # beds of flowers in front of the hall on both sides of the tower
    for z in (1, 2, b.W - 1, b.W):
        for x in range(1, 5):
            for y in range(0, 3):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, "air")
        b.planter(z, x=4)
    # the hall: council table, the court corner, the ledger under the banner
    for x in range(2, 6):
        if b.get(x, 1, zc) is None:
            b.set(x, 1, zc, p["carpet"])
    for x in (7, 8):
        b.set(x, 1, zc, L + "council_table", {"facing": "west"})
        b.stairs(x, 1, zc - 1, "north", p["chair"])
        b.stairs(x, 1, zc + 1, "south", p["chair"])
    b.stairs(6, 1, zc, "west", p["chair"])
    for z in (2, 3):
        b.set(9, 1, z, L + "judge_bench", {"facing": "west"})
        b.stairs(10, 1, z, "east", p["chair"])
    b.set(10, 1, zc, L + "village_ledger", {"facing": "west"})
    for z in (zc - 1, zc + 1):
        b.set(10, 1, z, "bookshelf")
        b.set(10, 2, z, "bookshelf")
    b.wall_banner(10, 3, zc, "west", p["banner"], "minecraft:triangles_top", "white")
    b.set(10, 1, 7, p["pot"])
    b.chest(10, 1, 8, "west")
    for z in (3, 7):
        b.lantern_on_chain(8, z)
    b.chimney(12, zc, b.roof_y(11, zc) + (2 if flat else 3))
    b.bed(6, 8, "east")
    b.bed(6, 2, "east")
    b.villager_spot(9, 7)
    b.apron()
    b.save()


def mint_alt(Template, v):
    """A counting house: a low stone shop wing on the street with the teller's copper counter, and behind it a tall
    treasury with a front gable (in the desert a stepped block): stone up to the strongroom's ceiling with barred vault
    windows, the press and the strongbox below, a counting room above (a ladder), two chimneys."""
    b = Civic(Template, v, "mint", D=9, W=7, H=3, alt=True)
    p, zc = b.p, b.zc
    flat = p["kind"] == "flat"
    if flat:
        b.band = p["accent"]
    b.base_h = 3  # stone up to the strongroom's ceiling
    b.volume(1, 4, 1, 7, ridge="z")
    b.volume(5, 9, 1, 7, H=6, ridge="x")
    windows = [(1, 2), (1, 6), (3, 1), (3, 7), (7, 1, 2), (7, 7, 2), (6, 1, 4, 5), (8, 1, 4, 5), (6, 7, 4, 5),
               (8, 7, 4, 5), (9, zc, 4, 5)]
    gable = [] if p["kind"] in ("hip", "flat") else [(4, 9, zc)]
    b.shell(windows=windows, door=(1, zc), gable_windows=gable)
    for z in (1, 7):  # the strongroom's windows are barred
        if b.get(7, 2, z) == "minecraft:glass_pane":
            b.connected(7, 2, z, "iron_bars")
    b.entrance()
    b.front_details()
    # the wing: the teller's copper counter with bars (open in the middle), benches for the customers
    for z in range(2, 7):
        if z != zc:
            b.slab(3, 1, z, "waxed_cut_copper_slab", "top")
            b.connected(3, 2, z, "iron_bars")
    b.stairs(2, 1, 2, "north", p["chair"])
    b.stairs(2, 1, 6, "south", p["chair"])
    b.lantern_on_chain(2, zc)
    # the strongroom: the press (Coiner job site), furnace, anvil, the strongbox; the counting room above
    b.set(6, 1, zc, L + "mint_press", {"facing": "west"})
    b.set(8, 1, 2, "furnace", {"facing": "west", "lit": "false"})
    b.set(8, 1, 6, "anvil", {"facing": "north"})
    b.chest(8, 1, 5, "west", loot="lacuna:chests/village_mint")
    b.set(8, 1, 3, p["accent"] if p["accent"] != "packed_ice" else "polished_diorite")
    b.set(8, 2, 3, p["pot2"])
    for x in range(5, 9):
        for z in range(2, 7):
            b.set(x, 3, z, p["floor"])
    b.ladder(8, zc, 1, 3, "west")
    b.lantern(7, 2, zc, hanging=True)
    for z in (2, 6):
        b.set(5, 4, z, "bookshelf")
    b.set(7, 4, 2, "raw_copper_block")
    b.set(7, 4, 6, "decorated_pot", {"cracked": "false", "facing": "west", "waterlogged": "false"})
    b.lantern_on_chain(6, zc, low=5)
    for z in (2, 6):
        b.chimney(10, z, b.roof_y(9, z) + (2 if flat else 3))
    b.bed(5, 2, "east")
    b.bed(5, 6, "east")
    b.villager_spot(7, 3)
    b.role_sign([(0, 3, 7), (0, 3, 1), (0, 2, 7), (0, 2, 1)])
    b.planter(2)
    b.planter(b.W - 1)
    b.apron()
    b.save()


def guild_hall_alt(Template, v):
    """A great hall behind an open loggia: columns carry a beam and a catslide roof sweeping down from the hall's eave
    (a flat arcade in the desert), the guild banners hang on the columns and over the door, the role sign under the
    beam; tall windows stained in the guild colours, a forge chimney at the back corner."""
    b = Civic(Template, v, "guild_hall", D=12, W=9, H=5, alt=True)
    p, zc = b.p, b.zc
    flat = p["kind"] == "flat"
    if flat:
        b.band = p["accent"]
    b.volume(3, 12, 1, 9, ridge="z")
    front = (2,) if flat else (2, 3, 4)
    windows = [(3, z, *front) for z in (2, 3, 7, 8)] + [(x, z) for x in (4, 5, 7, 8, 10, 11) for z in (1, 9)] + [(12, 2), (12, 8)]
    gable = [] if p["kind"] in ("hip", "flat") else [(x, b.H + 3, z) for x in (7, 8) for z in (1, 9)]
    b.shell(windows=windows, door=(3, zc), extra_posts=((6, 1), (9, 1), (6, 9), (9, 9)), gable_windows=gable)
    colors = {2: "gray", 3: "red", 7: "red", 8: "purple"}
    for (x, y, z) in [(3, y, z) for z in colors for y in front] + gable:
        if b.get(x, y, z) == "minecraft:glass_pane":
            b.connected(x, y, z, f"{colors.get(z, 'gray' if x == 7 else 'purple')}_stained_glass_pane")
    # the loggia: a stone floor, columns, the beam, the roof down from the hall's eave
    for x in (1, 2):
        for z in range(1, b.W + 1):
            b.set(x, 0, z, b.base)
            for y in (1, 2, 3):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, "air")
    for z in (1, 3, 7, 9):
        for y in (1, 2, 3):
            b.pillar(1, y, z, p["post"])
    if flat:
        for z in range(1, b.W + 1):
            b.set(0, 4, z, p["plate"])
            b.set(1, 4, z, p["roof"])
            b.set(2, 4, z, p["roof"])
            b.slab(0, 5, z, p["ridge"])
    else:
        for z in range(1, b.W + 1):
            b.pillar(1, 4, z, p["plate"], axis="z")
        b.lean_to(0, 1, 0, b.W + 1, 4)
    for z in (zc - 1, zc + 1):
        b.lantern(1, 3, z, hanging=True)
    (guild_s, color_s, pat_s), (guild_g, color_g, pat_g), (guild_c, color_c, pat_c) = GUILDS
    b.wall_banner(0, 3, 3, "west", color_s, pat_s, "yellow")
    b.wall_banner(0, 3, 7, "west", color_c, pat_c, "yellow")
    b.wall_banner(2, 3 if flat else 4, zc, "west", color_g, pat_g, "yellow")
    b.entrance(lights=False)
    b.front_details(fx=3)
    b.ceiling_sign(1, 3, zc)
    # the hall: a board under each guild banner on the back wall, the feast table, the guild's tools
    for i, (guild, color, pattern) in enumerate(GUILDS):
        z = 3 + i * 2
        b.set(11, 1, z, L + "guild_board", {"facing": "west", "guild": guild})
        b.wall_banner(11, 3, z, "west", color, pattern, "yellow")
    b.chest(11, 1, 2, "west")
    b.set(11, 1, 8, "crafting_table")
    for x in range(6, 10):
        b.slab(x, 1, zc, p["ridge"] if not flat else "smooth_stone_slab", "top")
        b.stairs(x, 1, zc - 1 if x % 2 == 0 else zc + 1, "north" if x % 2 == 0 else "south", p["chair"])
    for x in range(4, 6):
        b.set(x, 1, zc, p["carpet"])
    b.set(5, 1, 2, "anvil", {"facing": "east"})
    b.set(4, 1, 2, "chipped_anvil", {"facing": "east"})
    shelf = {"facing": "east", **{f"slot_{i}_occupied": "true" if i % 3 else "false" for i in range(6)}}
    b.set(4, 1, 8, "chiseled_bookshelf", shelf)
    b.set(4, 2, 8, "chiseled_bookshelf", shelf)
    b.set(5, 1, 8, p["accent"] if p["accent"] != "packed_ice" else "polished_diorite")
    b.set(5, 2, 8, "amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    for x in (6, 10):
        b.lantern_on_chain(x, zc)
    b.chimney(13, 2, b.roof_y(12, 2) + (2 if flat else 3))
    b.bed(9, 8, "east")
    b.bed(9, 2, "east")
    b.villager_spot(6, 3)
    b.villager_spot(6, 7)
    b.apron()
    b.save()


def schoolhouse_alt(Template, v):
    """A front-gabled school behind a porch of columns (a parapeted porch in the desert), a belfry with a copper spire
    on the ridge, the role sign hanging between the columns over a planter."""
    b = Civic(Template, v, "schoolhouse", D=9, W=9, H=3, alt=True)
    p, zc = b.p, b.zc
    flat = p["kind"] == "flat"
    if flat:
        b.band = p["accent"]
    b.volume(2, 9, 1, 9, ridge="x")
    windows = [(2, 3), (2, 7), (4, 1), (5, 1), (7, 1), (8, 1), (4, 9), (5, 9), (7, 9), (8, 9), (9, 2), (9, 8)]
    gable = [] if p["kind"] in ("hip", "flat") else [(2, b.H + 3, zc), (9, b.H + 3, zc)]
    b.shell(windows=windows, door=(2, zc), extra_posts=((6, 1), (6, 9)), gable_windows=gable)
    # the porch: a stone floor, columns on the street edge, a beam and a roof sloping to the street
    for z in range(1, b.W + 1):
        b.set(1, 0, z, b.base)
        for y in (1, 2):
            if b.get(1, y, z) is None:
                b.set(1, y, z, "air")
        b.pillar(1, 3, z, p["plate"], axis="z")
        if flat:
            b.set(0, 3, z, p["plate"])
            b.slab(0, 4, z, p["ridge"])
        else:
            b.stairs(0, 3, z, "east")
    for z in (1, 3, 7, 9):
        for y in (0, 1, 2):
            b.pillar(0, y, z, p["post"])
    for z in (zc - 1, zc + 1):
        b.lantern(0, 2, z, hanging=True)
    b.entrance(lights=False)
    b.front_details(fx=2)
    b.set(0, 2, 2, f"{p['wood']}_wall_hanging_sign", {"facing": "west", "waterlogged": "false"}, b.sign_text())
    b.planter(2)
    b.planter(b.W - 1)
    # the classroom: blackboard, the teacher's lectern, two rows of desks, the library corners
    for z in range(3, 8):
        for y in (2, 3):
            b.set(8, y, z, "black_terracotta")
    b.set(7, 1, zc, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    for x in (4, 6):
        for z in (3, 4, 6, 7):
            b.set(x, 1, z, L + "school_desk", {"facing": "east"})
            b.stairs(x - 1, 1, z, "west", p["chair"])
    for x in range(3, 7):
        b.set(x, 1, zc, p["carpet"])
    for z in (2, 8):
        for x in (7, 8):
            b.set(x, 1, z, "bookshelf")
            b.set(x, 2, z, "bookshelf")
    b.set(8, 3, 2, p["pot"])
    b.set(8, 3, 8, p["pot2"])
    for x in (4, 6):
        b.lantern_on_chain(x, zc)
    b.belfry(4, zc)
    b.bed(4, 8, "east")
    b.bed(4, 2, "east")
    b.villager_spot(3, 2)
    b.apron()
    b.save()


# ============================================================================================ civic square (town centre)
# A terrain-following plaza (projection terrain_matching, like the vanilla streets: its paving follows the ground and it
# never carves terraces into hills). The rigid pieces around it are placed at the ground height of their own jigsaw:
#   * the bell canopy in the middle (the village meeting point), attached by a minecraft:bottom jigsaw in the floor;
#   * the four civic buildings in a pinwheel around the plaza, doors facing it, attached by building_entrance jigsaws on
#     its edges with a higher selection priority than the streets, so they are placed first;
#   * four street exits in the middle of the sides; each building keeps 6 blocks from the exits, so a 16-wide vanilla
#     street piece always fits on one side of every exit (the pinwheel leaves that side free).
SQ = 27
SQ_C = SQ // 2
SQ_OFF = 7         # building entrance offset from the side's middle
SQ_H = 7          # the bell canopy (5 tall, standing on the plaza) must fit inside the plaza's box
PINWHEEL = (("village_hall", SQ_C - SQ_OFF, 0, "north"), ("guild_hall", SQ - 1, SQ_C - SQ_OFF, "east"),
            ("schoolhouse", SQ_C + SQ_OFF, SQ - 1, "south"), ("mint", 0, SQ_C + SQ_OFF, "west"))
CANOPY = 7


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

    c = SQ_C
    # ground: a path ring, a curb, the checkered paving and a ring of the second paving around the canopy
    for x in range(SQ):
        for z in range(SQ):
            ring = min(x, z, SQ - 1 - x, SQ - 1 - z)
            d = max(abs(x - c), abs(z - c))
            if ring <= 1:
                block = q["path"]
            elif ring == 2:
                block = q["curb"]
            elif d <= CANOPY // 2 + 2:
                block = q["square2"]
            else:
                block = q["square"] if (x + z) % 2 else q["square2"]
            put(x, 0, z, block)
    # paths from the four doors and the four exits to the middle stay plain path, so they read as streets
    for (_, ex, ez, facing) in PINWHEEL:
        dx, dz = {"north": (0, 1), "south": (0, -1), "west": (1, 0), "east": (-1, 0)}[facing]
        x, z = ex, ez
        for _ in range(4):
            for w in (-1, 0, 1):
                put(x + (w if dz else 0), 0, z + (w if dx else 0), q["path"])
            x, z = x + dx, z + dz
    for (x, z) in ((c, 0), (c, SQ - 1), (0, c), (SQ - 1, c)):
        for i in range(4):
            for w in (-1, 0, 1):
                if z == 0:
                    put(x + w, 0, i, q["path"])
                elif z == SQ - 1:
                    put(x + w, 0, SQ - 1 - i, q["path"])
                elif x == 0:
                    put(i, 0, z + w, q["path"])
                else:
                    put(SQ - 1 - i, 0, z + w, q["path"])
    # flower beds in the four quarters, pinwheeled like the buildings so they never block a door or an exit path,
    # each with a lamp post in the middle
    for (x0, z0) in ((8, 4), (SQ - 7, 8), (SQ - 11, SQ - 7), (4, SQ - 11)):
        for x in range(x0, x0 + 3):
            for z in range(z0, z0 + 3):
                if (x, z) == (x0 + 1, z0 + 1):
                    put(x, 0, z, q["curb"])
                    post(t, x, 1, z, q["post"])
                    post(t, x, 2, z, q["post"])
                    put(x, 3, z, "lantern", {"hanging": "false", "waterlogged": "false"})
                    continue
                if v == "desert":
                    put(x, 0, z, "sand")
                    if (x + z) % 2 == 0:
                        put(x, 1, z, r.choice(q["flowers"]))
                else:
                    put(x, 0, z, "grass_block")
                    flower = r.choice(q["flowers"])
                    put(x, 1, z, flower, {"age": "1"} if flower == "sweet_berry_bush" else None)
    # benches around the canopy, facing it
    bench = STYLES[v]["chair"]
    k = CANOPY // 2 + 2
    for (x, z, facing) in ((c - k, c - 2, "west"), (c - k, c + 2, "west"), (c + k, c - 2, "east"), (c + k, c + 2, "east"),
                           (c - 2, c - k, "north"), (c + 2, c - k, "north"), (c - 2, c + k, "south"), (c + 2, c + k, "south")):
        put(x, 1, z, bench, {"facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false"})
    floor = "minecraft:" + q["square2"]
    # the bell canopy (meeting point) in the middle
    t.jigsaw(c, 0, c, "up_north", "minecraft:bottom", "minecraft:bottom", f"lacuna:village/{v}/civic_bell", final_state=floor,
             joint="rollable", selection_priority=2)
    # villagers, the village golem and cats like the vanilla town centres
    for (x, z, pool) in ((c - k, c, f"minecraft:village/{v}/villagers"), (c + k, c, f"minecraft:village/{v}/villagers"),
                         (c, c - k, "minecraft:village/common/iron_golem"), (c, c + k, "minecraft:village/common/cats")):
        t.jigsaw(x, 0, z, "up_north", "minecraft:bottom", "minecraft:bottom", pool, final_state=floor, joint="rollable")
    # the civic buildings first, then the streets out of the four sides
    for (building, x, z, facing) in PINWHEEL:
        t.jigsaw(x, 1, z, f"{facing}_up", "minecraft:building_entrance", "minecraft:building_entrance",
                 f"lacuna:village/{v}/{building}", final_state="minecraft:structure_void", joint="aligned", selection_priority=1)
    for (x, z, facing) in ((c, 0, "north"), (c, SQ - 1, "south"), (0, c, "west"), (SQ - 1, c, "east")):
        t.jigsaw(x, 1, z, f"{facing}_up", "minecraft:street", "minecraft:street", f"minecraft:village/{v}/streets",
                 final_state="minecraft:structure_void", joint="aligned")
    t.save()


def civic_bell(Template, v):
    """The canopy over the village bell: four posts, a two-tier slab roof, the bell hanging from it. Rigid."""
    t = Template(f"village/{v}/civic_bell", CANOPY, 5, CANOPY)
    q = SQUARES[v]
    m = CANOPY // 2
    for (x, z) in ((1, 1), (1, CANOPY - 2), (CANOPY - 2, 1), (CANOPY - 2, CANOPY - 2)):
        for y in (0, 1, 2):
            post(t, x, y, z, q["post"])
    for x in range(CANOPY):
        for z in range(CANOPY):
            t.set(x, 3, z, "minecraft:" + q["canopy"], {"type": "bottom", "waterlogged": "false"})
            if x in (1, CANOPY - 2) and 1 <= z <= CANOPY - 2 or z in (1, CANOPY - 2) and 1 <= x <= CANOPY - 2:
                t.set(x, 4, z, "minecraft:" + q["canopy"], {"type": "bottom", "waterlogged": "false"})
    t.set(m, 4, m, "minecraft:" + q["canopy"], {"type": "bottom", "waterlogged": "false"})
    t.set(m, 2, m, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"}, {"id": "minecraft:bell"})
    t.set(m, 1, m, "minecraft:air")
    # clear the space under the roof (terrain and grass) except the posts
    for x in range(CANOPY):
        for z in range(CANOPY):
            for y in (0, 1, 2):
                if t.get(x, y, z) is None and (x, z) != (m, m):
                    t.set(x, y, z, "minecraft:air")
    t.jigsaw(m, 0, m, "down_south", "minecraft:bottom", "minecraft:bottom", "minecraft:empty", final_state="minecraft:air",
             joint="rollable")
    t.save()


BUILDINGS = (village_hall, mint, guild_hall, schoolhouse, village_hall_alt, mint_alt, guild_hall_alt, schoolhouse_alt)


def build(Template):
    for v in STYLES:
        for fn in BUILDINGS:
            fn(Template, v)
        civic_square(Template, v)
        civic_bell(Template, v)
