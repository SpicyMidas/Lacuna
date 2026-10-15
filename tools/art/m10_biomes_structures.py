"""M10 textures: Forgotten Land biome blocks (10a); structure blocks and mobs are appended by later parts."""
from texlib import c, from_ascii, lerp, new, noise_fill, ramp, rng, save, with_alpha

ASH = ramp("#7E8287", "#9A9EA2", "#B4B8BB", "#CDD0D2", "#E2E4E5")

FOOT = [
    "................",
    "..##............",
    ".####...........",
    ".####......##...",
    ".####.....####..",
    "..##......####..",
    "..........####..",
    "...##......##...",
    "..####..........",
    "..####......##..",
    "..####.....####.",
    "...##......####.",
    "...........####.",
    "............##..",
    "................",
    "................",
]


def ashen_snow():
    base = noise_fill(ASH, "ashen_snow_block", cell=4, speckle=0.22)
    px = base.load()
    r = rng("ashen_flecks")
    for _ in range(14):
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = c("#5E6266")
    save(base, "block/ashen_snow_block")
    for stage, strength in ((1, 0.18), (2, 0.36), (3, 0.6)):
        img = base.copy()
        p = img.load()
        for y, row in enumerate(FOOT):
            for x, ch in enumerate(row):
                if ch == "#":
                    p[x, y] = lerp(p[x, y], c("#2F3236"), strength)
        save(img, f"block/ashen_snow_block_footprint_{stage}")


def clear_ice():
    img = new(16, 16)
    px = img.load()
    r = rng("clear_ice")
    for y in range(16):
        for x in range(16):
            px[x, y] = c("#CFEFFF", 46 + r.randrange(12))
    # a few bright streaks and a faint frame so the surface reads as ice
    for k in range(3):
        x0, y0 = r.randrange(2, 12), r.randrange(2, 12)
        for i in range(4):
            if x0 + i < 16 and y0 + i < 16:
                px[x0 + i, y0 + i] = c("#FFFFFF", 150)
    for i in range(16):
        for (x, y) in ((i, 0), (0, i)):
            px[x, y] = c("#E8F8FF", 110)
        for (x, y) in ((i, 15), (15, i)):
            px[x, y] = c("#A8D8F0", 90)
    save(img, "block/clear_ice")


def updraft_spring():
    img = new(16, 16)
    px = img.load()
    r = rng("updraft_spring")
    water = ramp("#2E6E9E", "#3F8FC0", "#64B4E0", "#A8E0FF", alpha=200)
    for y in range(16):
        for x in range(16):
            # rising diagonal streaks
            t = ((x * 3 + (15 - y) * 2 + r.randrange(3)) % 12) / 11.0
            px[x, y] = water[min(3, int(t * 4))]
    for _ in range(10):
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = c("#F0FBFF", 230)
    save(img, "block/updraft_spring")


MUSHROOM = [
    "................",
    "................",
    "......hhhh......",
    "....hHHCCHHh....",
    "...hHCCccCCHh...",
    "...HCcCCCCcCH...",
    "..hHHHHHHHHHHh..",
    "...ddddsdddd....",
    ".......ss.......",
    "..hh...ss..hhh..",
    ".hCCh..ss.hCcCh.",
    ".HHHH..ss.HHHHH.",
    "..ds...ss..dsd..",
    "...s...ss...s...",
    "...s..ssss..s...",
    "................",
]


def spore_mushroom():
    save(from_ascii(MUSHROOM, {"h": c("#1E6E78"), "H": c("#2A9AA6"), "C": c("#62E0E6"), "c": c("#C8FFF8"),
                               "d": c("#16494F"), "s": c("#B8C8B4")}), "block/spore_mushroom")


def buried_watch():
    face = new(16, 16)
    px = face.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = c("#8E6F42")
    for y in range(3, 13):
        for x in range(3, 13):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d <= 20:
                px[x, y] = c("#F2EAD2") if d <= 13 else c("#C9A869")
    for (x, y) in ((7, 4), (7, 5), (7, 6), (7, 7), (8, 7), (9, 7), (10, 8)):
        px[x, y] = c("#2A1E12")
    for (x, y) in ((7, 3), (11, 7), (7, 11), (4, 7)):
        px[x, y] = c("#5A4226")
    # a crack across the glass
    for (x, y) in ((5, 9), (6, 9), (6, 10), (7, 10), (8, 11)):
        px[x, y] = c("#9AA4A8")
    save(face, "block/buried_watch_top")
    side = noise_fill(ramp("#5E4524", "#86663A", "#B08D57", "#D2B57A"), "buried_watch_side", cell=2, speckle=0.15)
    sp = side.load()
    for x in range(16):
        sp[x, 10] = c("#3A2A18")
    save(side, "block/buried_watch_side")


GEAR = [
    "......oooo......",
    "......oRRo......",
    "..oo..oRRo..oo..",
    ".oRRooRrrRooRRo.",
    ".oRrRRrrrrRRrRo.",
    "..oRrrrrrrrrrRo.",
    "oooRrr.oo.rrRooo",
    "oRRrr.o..o.rrRRo",
    "oRRrr.o..o.rrRRo",
    "oooRrr.oo.rrRooo",
    "..oRrrrrrrrrrRo.",
    ".oRrRRrrrrRRrRo.",
    ".oRRooRrrRooRRo.",
    "..oo..oRRo..oo..",
    "......oRRo......",
    "......oooo......",
]


def rusted_gear():
    img = from_ascii(GEAR, {"o": c("#3A2014"), "R": c("#8A4A2A"), "r": c("#A35E34")})
    px = img.load()
    r = rng("rusted_gear")
    for _ in range(18):
        x, y = r.randrange(16), r.randrange(16)
        if px[x, y][3]:
            px[x, y] = r.choice([c("#6B3A22"), c("#B87A4A"), c("#5A4A3A")])
    save(img, "block/rusted_gear")


def sunken_bell():
    img = noise_fill(ramp("#2F6B5E", "#3F8A74", "#5BA88C", "#7FC4A6", "#A8DCC0"), "sunken_bell", cell=3, speckle=0.25)
    px = img.load()
    r = rng("sunken_bell_spots")
    for _ in range(20):
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = r.choice([c("#B87A4A"), c("#C98E5A"), c("#2A4F45")])  # bare bronze and deep patina
    for x in range(16):
        px[x, 12] = c("#24463D")
        px[x, 13] = c("#C98E5A")
    for y in range(0, 3):
        for x in range(7, 9):
            px[x, y] = c("#3A2A18")
    save(img, "block/sunken_bell")


GLYPHS = [
    "................",
    ".gg..g.....g.gg.",
    ".g..ggg...gg..g.",
    ".gg..g.....g.gg.",
    "................",
    "..g.g..gg..g....",
    "..ggg...g..ggg..",
    "....g..gg....g..",
    "................",
    ".g...ggg..g..g..",
    ".gg....g..ggg...",
    ".g...ggg....g...",
    "................",
    "...gg..g.g..gg..",
    "....g..ggg...g..",
    "...gg....g..gg..",
]


def obelisk_inscription():
    img = noise_fill(ramp("#0E0C12", "#16141C", "#1F1C27", "#2A2633"), "obelisk_inscription", cell=4, speckle=0.12)
    px = img.load()
    for y, row in enumerate(GLYPHS):
        for x, ch in enumerate(row):
            if ch == "g":
                px[x, y] = c("#B08A3E") if (x + y) % 3 else c("#E7C46A")
    save(img, "block/obelisk_inscription")


def mirror(name, crack=False):
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            t = ((x + y) % 16) / 15.0
            px[x, y] = lerp(c("#5E6670", 150), c("#B9C2CC", 150), 1.0 - abs(t - 0.5) * 2)
    for i in range(16):
        for (x, y) in ((i, 0), (0, i), (i, 15), (15, i)):
            px[x, y] = c("#E8EEF4", 230)
    for i in range(3, 8):
        px[i, 11 - i] = c("#FFFFFF", 200)  # glint
    if crack:
        for (x, y) in ((8, 2), (8, 3), (7, 4), (8, 5), (9, 6), (9, 7), (8, 8), (7, 9), (8, 10), (10, 8), (11, 9), (6, 5), (5, 6)):
            px[x, y] = c("#E7C46A", 255)
    save(img, f"block/{name}")


def trigger(name, lit):
    img = noise_fill(ramp("#4A5560", "#5E6A76", "#76828E", "#8E9AA6"), name, cell=4, speckle=0.15)
    px = img.load()
    ring = c("#9FE8FF") if lit else c("#3A444E")
    for y in range(16):
        for x in range(16):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if 20 <= d <= 34:
                px[x, y] = ring
            if d <= 3:
                px[x, y] = c("#E8FAFF") if lit else c("#2A323A")
    save(img, f"block/{name}")


def reflection():
    from art.humanoid import humanoid_skin
    img = humanoid_skin("reflection", body=ramp("#2A2F36", "#4A525C", "#7A848F", "#B9C2CC"), alpha=215,
                        eyes=c("#E8FAFF"), seed="reflection", legs=ramp("#22262C", "#3A4048", "#5E6670"))
    save(img, "entity/reflection")


# Last Merchant robe: UV regions of the vanilla villager model (64x64) that the robe covers. The head (face, hat)
# is never painted, so the villager's own face shows unchanged.
ROBE_JACKET_SIDES = {"right": range(0, 6), "front": range(6, 14), "left": range(14, 20), "back": range(20, 28)}
ROBE_NECK = {44: range(8, 12), 45: range(9, 11)}     # V-neck on the front of the robe
ROBE_BELT = 51


def last_merchant_robe():
    """A very dark grey travelling robe for the Last Merchant: robe, body, sleeves and trousers, face untouched."""
    img = new(64, 64)
    px = img.load()
    r = rng("last_merchant_robe")
    deep, dark, base, mid, light = ramp("#141417", "#1B1B1F", "#222227", "#29292F", "#323238")
    trim, thread, boot = c("#0E0E10"), c("#4A4A53"), c("#0B0B0D")
    pal = [deep, dark, base, base, mid, light]
    folds = [r.uniform(-0.16, 0.16) for _ in range(64)]  # soft vertical folds, one value per column

    def cloth(x, y, lift=0.0):
        v = 0.5 + folds[x] + lift + r.uniform(-0.22, 0.22)
        return pal[max(0, min(len(pal) - 1, int(v * len(pal))))]

    def fill(x0, y0, x1, y1, lift=0.0):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = cloth(x, y, lift)

    # robe (jacket cube, uv 0,38): four sides 20 rows tall, the shoulders of the top face, open hem underneath
    for face, cols in ROBE_JACKET_SIDES.items():
        for y in range(44, 64):
            for x in cols:
                if face == "front" and x in ROBE_NECK.get(y, ()):
                    continue
                # light catches the shoulders, the hem sinks into shadow
                px[x, y] = cloth(x, y, 0.12 if y < 47 else (-0.12 if y > 59 else 0.0))
    for y in range(38, 44):
        for x in (6, 7, 12, 13):
            px[x, y] = cloth(x, y, 0.1)
    for x in range(0, 28):
        px[x, ROBE_BELT] = trim                       # a narrow dark sash
        if px[x, 63][3]:
            px[x, 63] = thread if x % 3 == 0 else trim  # stitched hem
    for y, cols in ROBE_NECK.items():
        px[cols.start - 1, y] = thread
        px[cols.stop, y] = thread
    px[9, ROBE_BELT] = thread                         # the sash knot
    px[10, ROBE_BELT] = thread
    # body (uv 16,20): sides and underside, under the robe
    fill(16, 26, 40, 38)
    fill(30, 20, 38, 26, -0.1)
    # sleeves (both side boxes of the crossed arms, uv 44,22); the folded hands (uv 40,38) stay bare
    fill(48, 22, 56, 26, 0.05)
    fill(44, 26, 60, 34)
    for x in range(44, 60):
        px[x, 33] = trim                              # cuffs
    # trousers and boots (uv 0,22)
    fill(4, 22, 12, 26, -0.1)
    fill(0, 26, 16, 38, -0.05)
    for y in range(35, 38):
        for x in range(0, 16):
            px[x, y] = boot
    for x in range(8, 12):
        for y in range(22, 26):
            px[x, y] = boot                           # soles
    save(img, "entity/last_merchant_robe")


def paint():
    last_merchant_robe()
    mirror("mirror_glass")
    mirror("labyrinth_exit", crack=True)
    trigger("labyrinth_trigger", True)
    trigger("labyrinth_trigger_solved", False)
    reflection()
    sunken_bell()
    obelisk_inscription()
    ashen_snow()
    clear_ice()
    updraft_spring()
    spore_mushroom()
    buried_watch()
    rusted_gear()
