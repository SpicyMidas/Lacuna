"""M2 ruin templates for the Forgotten Land (deterministic, seeded by template name)."""

L = "lacuna:"
BRICK = L + "ruin_stone_bricks"
CRACKED = L + "cracked_ruin_stone_bricks"
STONE = L + "ruin_stone"
PILLAR = L + "ruin_pillar"
GLYPH = L + "chiseled_ruin_glyph"
SLAB = L + "ruin_slab"
STAIRS = L + "ruin_stairs"
WALL = L + "ruin_wall"
CLUSTER = L + "divine_glass_cluster"
CACHE = "lacuna:chests/ruin_cache"


def brick(t, x, y, z, decay=0.25):
    r = t.r.random()
    if r < decay * 0.4:
        return  # missing block
    t.set(x, y, z, CRACKED if r < decay else BRICK)


def slab(t, x, y, z, top=False):
    t.set(x, y, z, SLAB, {"type": "top" if top else "bottom", "waterlogged": "false"})


def stairs(t, x, y, z, facing, half="bottom"):
    t.set(x, y, z, STAIRS, {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})


# 2D data values of Direction (Painting "facing" byte)
FACING_2D = {"south": 0, "west": 1, "north": 2, "east": 3}
# counter-clockwise direction of each facing: even-width paintings extend one block that way
CCW = {"north": (-1, 0), "south": (1, 0), "east": (0, -1), "west": (0, 1)}
BACK = {"north": (0, 1), "south": (0, -1), "east": (-1, 0), "west": (1, 0)}
MURAL_SIZES = {"memory_dawn": (2, 2), "memory_spire": (3, 2), "memory_harvest": (2, 2), "memory_festival": (3, 2),
               "memory_fall": (2, 2)}


def mural(t, x, y, z, facing, variant):
    """Hangs a Memory Mural painting whose anchor block is (x, y, z) (the air block in front of the wall), facing
    away from the wall. Clears the painted area and makes every supporting wall block solid so the painting survives
    (hanging entities pop off when their wall has holes). The lacuna:murals processor fixes TileX/Y/Z at placement."""
    from nbtlib.tag import Byte
    w, h = MURAL_SIZES[variant]
    cx, cz = CCW[facing]
    bx, bz = BACK[facing]
    lo = -(w // 2) if w % 2 else 0
    hi = w // 2
    for k in range(lo, hi + 1):
        for dy in range(h):
            px, pz = x + cx * k, z + cz * k
            t.set(px, y + dy, pz, "minecraft:air")
            t.set(px + bx, y + dy, pz + bz, BRICK)
    t.entity((x + 0.5, y + 0.5, z + 0.5), {"id": "minecraft:painting", "variant": L + variant, "facing": Byte(FACING_2D[facing])})


def pillar(t, x, y0, y1, z):
    for y in range(y0, y1 + 1):
        t.set(x, y, z, PILLAR, {"axis": "y"})


def cluster(t, x, y, z):
    t.set(x, y, z, CLUSTER, {"facing": "up", "waterlogged": "false"})


def wall_piece(t, x, y, z):
    t.set(x, y, z, WALL, {"up": "true", "north": "none", "east": "none", "south": "none", "west": "none",
                          "waterlogged": "false"})


def broken_arch(Template, i):
    t = Template(f"ruins/broken_arch_{i}", 9, 9, 3)
    height = 6 + i % 2
    for z in range(3):
        for x in (1, 7):
            for y in range(0, height):
                if y > height - 2 and x == 7 and i == 2:
                    continue  # collapsed side
                brick(t, x, y, z, 0.2)
        for x in range(1, 8):
            if t.r.random() < 0.8:
                brick(t, x, height, z, 0.3)
        t.set(4, height + 1, z, GLYPH)
    stairs(t, 2, height - 1, 1, "east", "top")
    stairs(t, 6, height - 1, 1, "west", "top")
    for x in range(0, 9):
        for z in range(3):
            if t.r.random() < 0.3:
                slab(t, x, 0, z)
    if i == 3:
        cluster(t, 4, height + 2, 1)
    else:
        cluster(t, 0, 0, 0)
    t.save()


def fallen_colonnade(Template, i):
    length = 13 + 2 * i
    t = Template(f"ruins/fallen_colonnade_{i}", length, 7, 5)
    for x in range(length):
        for z in range(5):
            if t.r.random() < 0.7:
                slab(t, x, 0, z)
    for k, x in enumerate(range(1, length, 3)):
        h = 6 if k % 2 == 0 else t.r.randint(1, 4)
        pillar(t, x, 0, h - 1, 1)
        pillar(t, x, 0, max(1, h - 2), 3)
        if h < 4:
            # fallen drum lying across the floor
            for dz in range(1, 4):
                t.set(min(length - 1, x + 1), 1, dz, PILLAR, {"axis": "z"})
        else:
            t.set(x, h, 1, SLAB, {"type": "bottom", "waterlogged": "false"})
    cluster(t, length // 2, 1, 2)
    t.save()


def ruined_hall(Template, i):
    sx, sz = 13 + 2 * (i - 1), 11
    t = Template(f"ruins/ruined_hall_{i}", sx, 8, sz)
    for x in range(sx):
        for z in range(sz):
            t.set(x, 0, z, BRICK if t.r.random() > 0.25 else CRACKED)
    for x in range(sx):
        for z in (0, sz - 1):
            top = t.r.randint(1, 6)
            for y in range(1, top):
                brick(t, x, y, z)
    for z in range(1, sz - 1):
        for x in (0, sx - 1):
            top = t.r.randint(1, 6)
            for y in range(1, top):
                brick(t, x, y, z)
    # doorway
    for y in range(1, 4):
        t.clear(sx // 2, y, 0)
    for y in range(1, 4):
        t.set(sx // 2, y, 0, "minecraft:air")
    # inner columns
    for x in (3, sx - 4):
        for z in (3, sz - 4):
            pillar(t, x, 1, t.r.randint(3, 6), z)
    # altar + cache
    t.set(sx // 2, 1, sz - 3, GLYPH)
    t.chest(sx // 2, 1, sz - 2, CACHE, facing="north")
    stairs(t, sx // 2 - 1, 1, sz - 3, "east")
    stairs(t, sx // 2 + 1, 1, sz - 3, "west")
    cluster(t, 1, 1, 1)
    cluster(t, sx - 2, 1, sz - 2)
    # a Memory Mural above the altar, on the back wall
    for x in range(sx // 2 - 1, sx // 2 + 2):
        t.set(x, 1, sz - 1, BRICK)
    mural(t, sx // 2, 2, sz - 2, "north", "memory_spire" if i == 1 else "memory_festival")
    t.save()


def sunken_plaza(Template, i):
    s = 15
    t = Template(f"ruins/sunken_plaza_{i}", s, 5, s)
    c = s // 2
    for x in range(s):
        for z in range(s):
            d = max(abs(x - c), abs(z - c))
            if d == 7:
                t.set(x, 3, z, BRICK if t.r.random() > 0.3 else CRACKED)
            elif d == 6:
                facing = "south" if z < c - 5 else "north" if z > c + 5 else "east" if x < c else "west"
                stairs(t, x, 2, z, facing)
            elif d == 5:
                slab(t, x, 1, z, top=True)
            else:
                t.set(x, 0, z, BRICK if (x + z) % 2 else STONE)
                for y in range(1, 4):
                    t.set(x, y, z, "minecraft:air")
    # basin with water in the middle
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            t.set(x, 0, z, "minecraft:water", {"level": "0"})
    for (x, z) in ((c - 2, c), (c + 2, c), (c, c - 2), (c, c + 2)):
        wall_piece(t, x, 1, z)
    t.chest(c + 3, 1, c + 3, CACHE, facing="north")
    cluster(t, c - 3, 1, c - 3)
    t.save()


def memory_shrine(Template, i):
    s = 9
    t = Template(f"ruins/memory_shrine_{i}", s, 7, s)
    for x in range(s):
        for z in range(s):
            t.set(x, 0, z, BRICK)
            edge = x in (0, s - 1) or z in (0, s - 1)
            for y in range(1, 6):
                if edge:
                    if y == 5 and t.r.random() < 0.4:
                        continue
                    brick(t, x, y, z, 0.15)
                else:
                    t.set(x, y, z, "minecraft:air")
    # entrance
    for y in range(1, 4):
        t.set(s // 2, y, 0, "minecraft:air")
    # corner pillars, glyph altar, cache
    for (x, z) in ((1, 1), (s - 2, 1), (1, s - 2), (s - 2, s - 2)):
        pillar(t, x, 1, 4, z)
    t.set(s // 2, 1, s - 3, GLYPH)
    t.chest(s // 2, 1, s - 2, CACHE, facing="north")
    cluster(t, s // 2, 2, s - 3)
    # partial roof
    for x in range(1, s - 1):
        for z in range(1, s - 1):
            if t.r.random() < 0.45:
                slab(t, x, 6, z)
    # Memory Murals (painting entities) on the three inner walls, placed last so nothing overwrites their wall
    back, west, east = (("memory_spire", "memory_dawn", "memory_harvest") if i == 1
                        else ("memory_festival", "memory_fall", "memory_dawn"))
    mural(t, s // 2, 2, s - 2, "north", back)
    mural(t, 1, 2, 4, "east", west)
    mural(t, s - 2, 2, 3, "west", east)
    t.save()


def obelisk_ruin(Template, i):
    t = Template(f"ruins/obelisk_ruin_{i}", 5, 13, 5)
    for x in range(5):
        for z in range(5):
            if t.r.random() < 0.8:
                slab(t, x, 0, z)
    for x in range(1, 4):
        for z in range(1, 4):
            t.set(x, 0, z, BRICK)
            t.set(x, 1, z, STONE)
    for y in range(2, 10):
        t.set(2, y, 2, GLYPH if y % 3 == 0 else STONE)
        if y < 5:
            for (x, z) in ((1, 2), (3, 2), (2, 1), (2, 3)):
                brick(t, x, y, z, 0.3)
    # M10: the Forgotten Land obelisk still bears one inscription, just above the ring of bricks
    t.set(2, 5, 2, L + "obelisk_inscription")
    # broken top lying beside the base
    for x in range(0, 3):
        t.set(x, 1, 4, PILLAR, {"axis": "x"})
    cluster(t, 4, 1, 0)
    t.save()


def build(Template):
    for i in (1, 2, 3):
        broken_arch(Template, i)
    for i in (1, 2):
        fallen_colonnade(Template, i)
        ruined_hall(Template, i)
        memory_shrine(Template, i)
    sunken_plaza(Template, 1)
    obelisk_ruin(Template, 1)
