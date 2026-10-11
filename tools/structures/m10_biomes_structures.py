"""M10 structure templates: Sunken Bell Tower, Obelisk of Unreadable Names (10b); Mirror Labyrinth variants (10c)."""

L = "lacuna:"
BELL_LOOT = "lacuna:chests/sunken_bell_tower"


def stone(t, x, y, z, decay=0.0):
    r = t.r.random()
    if r < decay:
        return
    t.set(x, y, z, "mossy_stone_bricks" if r < 0.35 + decay else ("cracked_stone_bricks" if r < 0.45 + decay else "stone_bricks"))


def stairs(t, x, y, z, block, facing, half="bottom"):
    t.set(x, y, z, block, {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})


def sunken_bell_tower(Template):
    t = Template("sunken_bell_tower", 9, 26, 9)
    # foundation and walls (more decay higher up: the sea has been eating the top)
    for x in range(9):
        for z in range(9):
            t.set(x, 0, z, "mossy_stone_bricks" if t.r.random() < 0.5 else "stone_bricks")
    for y in range(1, 21):
        decay = 0.0 if y < 10 else (y - 9) * 0.02
        for x in range(9):
            for z in range(9):
                if x in (0, 8) or z in (0, 8):
                    corner = x in (0, 8) and z in (0, 8)
                    stone(t, x, y, z, 0.0 if corner else decay)
    # arched windows on every side, every 6 levels; doorway to the south
    for y0 in (4, 10, 16):
        for (x, z) in ((4, 0), (4, 8), (0, 4), (8, 4)):
            for dy in range(3):
                t.clear(x, y0 + dy, z)
    for y in range(1, 4):
        t.clear(4, y, 8)
    stairs(t, 3, 3, 8, "stone_brick_stairs", "east", "top")
    stairs(t, 5, 3, 8, "stone_brick_stairs", "west", "top")
    # inner floors with a hole for the ladder shaft, sea lantern light
    for y in (7, 14):
        for x in range(1, 8):
            for z in range(1, 8):
                if (x, z) != (6, 6):
                    t.set(x, y, z, "dark_prismarine" if (x + z) % 3 else "prismarine_bricks")
        t.set(2, y, 2, "sea_lantern")
    for y in range(1, 21):
        t.set(6, y, 7, "stone_bricks")
    # loot in the base
    t.set(2, 1, 2, "chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": BELL_LOOT})
    t.set(1, 1, 1, "sea_lantern")
    # bell chamber: corner pillars, open arches, beam, the bell
    for y in range(21, 24):
        for (x, z) in ((0, 0), (0, 8), (8, 0), (8, 8), (1, 0), (0, 1), (7, 0), (8, 1), (0, 7), (1, 8), (7, 8), (8, 7)):
            t.set(x, y, z, "stone_bricks" if y < 23 else "chiseled_stone_bricks")
    for x in range(1, 8):
        for z in range(1, 8):
            if (x, z) != (6, 6):
                t.set(x, 20, z, "polished_andesite" if (x + z) % 2 else "andesite")
    # ladder shaft from the base to the bell chamber (through the holes left in every floor)
    for y in range(1, 21):
        t.set(6, y, 6, "ladder", {"facing": "north", "waterlogged": "false"})
    for x in range(1, 8):
        t.set(x, 23, 4, "stripped_dark_oak_log", {"axis": "x"})
    t.set(4, 22, 4, L + "sunken_bell")
    # weathered copper roof
    for x in range(9):
        for z in range(9):
            if x in (0, 8) or z in (0, 8):
                facing = "south" if z == 0 else "north" if z == 8 else "east" if x == 0 else "west"
                stairs(t, x, 24, z, "oxidized_cut_copper_stairs", facing)
            else:
                t.set(x, 24, z, "oxidized_cut_copper")
    for x in range(2, 7):
        for z in range(2, 7):
            t.set(x, 25, z, "weathered_cut_copper" if (x, z) != (4, 4) else "oxidized_copper")
    t.save()


def obelisk(Template):
    t = Template("obelisk", 5, 11, 5)
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            t.set(x, 0, z, "chiseled_polished_blackstone" if corner else "polished_blackstone_bricks")
    for x in range(1, 4):
        for z in range(1, 4):
            for y in (1, 2, 3):
                t.set(x, y, z, "polished_blackstone")
    # inscriptions on the four faces of the base, at hand height
    for (x, z) in ((2, 1), (2, 3), (1, 2), (3, 2)):
        t.set(x, 2, z, L + "obelisk_inscription")
    for y in range(4, 10):
        t.set(2, y, 2, "polished_blackstone" if y % 3 else L + "obelisk_inscription")
    t.set(2, 10, 2, "chiseled_polished_blackstone")
    # cracked steps around, half sunk
    for (x, z) in ((2, 0), (2, 4), (0, 2), (4, 2)):
        t.set(x, 1, z, "polished_blackstone_brick_slab", {"type": "bottom", "waterlogged": "false"})
    t.save()


MIRROR = L + "mirror_glass"
EXIT = L + "labyrinth_exit"
HOARD_LOOT = "lacuna:chests/mirror_hoard"
CELLS = 12            # 12 x 12 cells -> 25 x 25 blocks (cell i at block 2i+1, walls on even coordinates)
ROOM = {(5, 5), (5, 6), (6, 5), (6, 6)}
HOARD = (6, 4)        # sealed alcove north of the central room


def cell_block(i):
    return 2 * i + 1


def mirror_labyrinth(Template, variant):
    t = Template(f"mirror_labyrinth/maze_{variant}", 25, 5, 25)
    size = 25
    # floor of unbreakable ruin bricks, solid mirror walls everywhere, mirror ceiling
    for x in range(size):
        for z in range(size):
            t.set(x, 0, z, L + ("ruin_stone_bricks" if (x + z) % 5 else "cracked_ruin_stone_bricks"))
            for y in (1, 2, 3):
                t.set(x, y, z, MIRROR)
            t.set(x, 4, z, MIRROR)

    def carve(x, z, block="air"):
        for y in (1, 2, 3):
            t.set(x, y, z, block)

    # depth-first maze over the cells outside the room and the hoard alcove
    visited = set(ROOM) | {HOARD}
    start = (5, CELLS - 1)
    stack = [start]
    visited.add(start)
    carve(cell_block(start[0]), cell_block(start[1]))
    while stack:
        cx, cz = stack[-1]
        options = [(cx + dx, cz + dz, dx, dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                   if 0 <= cx + dx < CELLS and 0 <= cz + dz < CELLS and (cx + dx, cz + dz) not in visited]
        if not options:
            stack.pop()
            continue
        nx, nz, dx, dz = t.r.choice(options)
        carve(cell_block(cx) + dx, cell_block(cz) + dz)
        carve(cell_block(nx), cell_block(nz))
        visited.add((nx, nz))
        stack.append((nx, nz))
    # the central room (3 x 3) with the trigger, joined to the maze on its south side
    for x in range(11, 14):
        for z in range(11, 14):
            carve(x, z)
    carve(11, 14)
    t.set(12, 0, 12, L + "chiseled_ruin_glyph")
    t.set(12, 1, 12, L + "labyrinth_trigger", {"solved": "false"})
    # sealed Mirror Hoard alcove: opened (with the north door) when the Reflection dies
    hx, hz = cell_block(HOARD[0]), cell_block(HOARD[1])
    carve(hx, hz)
    t.set(hx, 1, hz, "chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": HOARD_LOOT})
    carve(hx, hz + 1, EXIT)
    # entrance (south) and the exit door (north, made of labyrinth_exit)
    carve(cell_block(5), size - 1)
    carve(cell_block(6), 0, EXIT)
    # corner pillars of ruin stone
    for (x, z) in ((0, 0), (0, size - 1), (size - 1, 0), (size - 1, size - 1)):
        for y in range(1, 5):
            t.set(x, y, z, L + "ruin_pillar", {"axis": "y"})
    check_maze(t, size, (cell_block(5), size - 1), (12, 12), (hx, hz))
    t.save()


def check_maze(t, size, entrance, trigger, hoard):
    """The ring must be reachable from the entrance while the hoard stays sealed (exit blocks count as walls)."""
    walkable = {"minecraft:air", L + "labyrinth_trigger"}
    seen = {entrance}
    todo = [entrance]
    while todo:
        x, z = todo.pop()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if 0 <= n[0] < size and 0 <= n[1] < size and n not in seen and t.get(n[0], 1, n[1]) in walkable:
                seen.add(n)
                todo.append(n)
    if trigger not in seen or hoard in seen:
        raise RuntimeError(f"{t.name}: maze check failed (trigger reachable={trigger in seen}, hoard sealed={hoard not in seen})")


def build(Template):
    sunken_bell_tower(Template)
    obelisk(Template)
    for v in range(1, 6):
        mirror_labyrinth(Template, v)
