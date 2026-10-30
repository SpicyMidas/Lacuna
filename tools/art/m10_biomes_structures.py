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
ROBE_BELT = 51                                        # the belt: rows 51-52 round the robe
ROBE_CAPELET = 47                                     # last row of the short shoulder capelet over the robe


def last_merchant_robe():
    """A very dark grey travelling robe for the Last Merchant: robe, body, sleeves and trousers, face untouched.

    Same silhouette and palette as ever (near-black greys), with the detail a long road leaves on a garment: a short
    shoulder capelet layered over the robe, an inner panel behind the open front edges, deep pleats below a worn leather
    belt (brass buckle, two pouches and a coin purse tied with ruby cord), an embroidered hem band in old gold and ruby
    thread, a stitched ruby lozenge (the merchant's mark) between the shoulders, darned patches, wide embroidered cuffs
    with an elbow patch, and turned-down boot tops with a strap and buckle.
    """
    img = new(64, 64)
    px = img.load()
    r = rng("last_merchant_robe")
    deep, dark, base, mid, light = ramp("#141417", "#1B1B1F", "#222227", "#29292F", "#323238")
    trim, thread, boot = c("#0E0E10"), c("#4A4A53"), c("#0B0B0D")
    pal = [deep, dark, base, base, mid, light]
    panel = ramp("#19181C", "#201E23", "#27252B", "#2E2C33")           # inner tunic: a touch warmer
    gold = ramp("#5E4C2A", "#8C7442", "#B39A5B")                       # old gold thread, worn dull
    ruby = ramp("#4A141C", "#6E1E2A", "#9B2B3B")                       # ruby thread: the only coin he takes
    leather = ramp("#1E1611", "#2E2119", "#3D2B1F", "#4E3828")
    brass = ramp("#5A4A28", "#8A7340", "#C2A762")
    patch = ramp("#23252B", "#2B2D34", "#34363E")                      # darned patches: a cooler, newer grey
    folds = [r.uniform(-0.16, 0.16) for _ in range(64)]                # soft vertical folds, one value per column
    pleat = [0.0] * 64
    for x in range(64):                                                # deep pleats below the belt: ridge, face, crease
        pleat[x] = (0.22, 0.06, -0.08, -0.26)[(x + 1) % 4]

    def cloth(x, y, lift=0.0, ramp_=None):
        p = ramp_ or pal
        v = 0.5 + folds[x] + lift + r.uniform(-0.18, 0.18)
        return p[max(0, min(len(p) - 1, int(v * len(p))))]

    def fill(x0, y0, x1, y1, lift=0.0):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = cloth(x, y, lift)

    def stitched_patch(x0, y0, x1, y1):
        """A darned patch: cooler cloth with a running stitch round its edge."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
                if edge and (x + y) % 2 == 0:
                    px[x, y] = thread
                else:
                    px[x, y] = patch[r.randint(0, 2) if not edge else 0]

    # ------------------------------------------------------------- robe (jacket cube, uv 0,38): four sides, rows 44-63
    for face, cols in ROBE_JACKET_SIDES.items():
        for y in range(44, 64):
            for x in cols:
                if face == "front" and x in ROBE_NECK.get(y, ()):
                    continue
                if y <= ROBE_CAPELET:
                    lift = 0.16                                        # the capelet catches the light
                elif y < ROBE_BELT:
                    lift = 0.02 + 0.3 * folds[x]                       # chest: soft folds
                elif y <= ROBE_BELT + 2:
                    lift = -0.14                                       # shadow under the belt
                else:
                    lift = pleat[x] * (0.6 + (y - 53) / 18.0)         # pleats deepen towards the hem
                    if y >= 59:
                        lift -= 0.1                                    # the hem sinks into shadow
                px[x, y] = cloth(x, y, lift)
    # capelet: its lower edge is a darker hem line with a shadow underneath, the layer over the robe
    for x in range(0, 28):
        if px[x, ROBE_CAPELET][3]:
            px[x, ROBE_CAPELET] = trim if x % 4 else thread
        if px[x, ROBE_CAPELET + 1][3]:
            px[x, ROBE_CAPELET + 1] = deep
    # inner panel behind the open front edges (front face, columns 8-11 below the capelet)
    for y in range(46, 60):
        for x in range(8, 12):
            if y > ROBE_CAPELET + 1 and not (ROBE_BELT <= y <= ROBE_BELT + 1):
                px[x, y] = cloth(x, y, 0.08 + (0.1 if x in (9, 10) else -0.05), panel)
    # the front edges of the outer robe: dark trim with an old-gold running stitch, open down to the hem band
    for y in range(ROBE_CAPELET + 1, 60):
        for x in (7, 12):
            if ROBE_BELT <= y <= ROBE_BELT + 1:
                continue
            px[x, y] = gold[1] if (y % 3 == 0) else trim
    # a column of brass toggles on the inner panel above the belt (mostly under the folded arms)
    for y in (49, 50):
        px[9, y] = brass[1]
        px[10, y] = brass[0]
    # ruby lozenges stitched down the inner panel below the belt
    for y0 in (54, 57):
        px[9, y0] = ruby[1]
        px[10, y0] = ruby[1]
        px[9, y0 + 1] = ruby[0]
        px[10, y0 + 1] = ruby[2]
    for y, cols in ROBE_NECK.items():
        px[cols.start - 1, y] = thread
        px[cols.stop, y] = thread
    # the merchant's mark between the shoulders: a ruby lozenge outlined in gold thread (back face, columns 20-27)
    mark = {(23, 49): ruby[1], (24, 49): ruby[2], (23, 50): ruby[0], (24, 50): ruby[1],
            (22, 49): gold[1], (25, 49): gold[1], (22, 50): gold[0], (25, 50): gold[0], (23, 48): gold[2], (24, 48): gold[1]}
    for (x, y), col in mark.items():
        px[x, y] = col
    # belt: two rows of worn leather all round, edge-lit, with a brass buckle at the front
    for x in range(0, 28):
        px[x, ROBE_BELT] = leather[2] if x % 5 else leather[3]
        px[x, ROBE_BELT + 1] = leather[1] if x % 7 else leather[0]
    px[8, ROBE_BELT], px[11, ROBE_BELT] = brass[1], brass[1]
    px[8, ROBE_BELT + 1], px[11, ROBE_BELT + 1] = brass[0], brass[0]
    px[9, ROBE_BELT], px[10, ROBE_BELT] = brass[2], leather[3]
    px[9, ROBE_BELT + 1], px[10, ROBE_BELT + 1] = leather[0], leather[1]
    px[12, ROBE_BELT + 2] = leather[2]                                  # the belt's tongue hanging past the buckle
    px[12, ROBE_BELT + 3] = leather[1]
    # pouches hung from the belt: a satchel on the right hip, a smaller one on the left, a coin purse at the back
    for (x0, x1, y0, y1) in ((1, 5, 53, 58), (15, 18, 53, 57)):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if y == y0:
                    px[x, y] = leather[3]                               # flap edge
                elif y == y0 + 1:
                    px[x, y] = leather[2]
                elif y == y1 - 1:
                    px[x, y] = leather[0]
                else:
                    px[x, y] = leather[1] if (x + y) % 3 else leather[2]
        px[(x0 + x1) // 2, y0 + 1] = brass[2]                           # the flap's brass stud
    for x, y, col in ((23, 53, ruby[2]), (23, 54, leather[2]), (24, 54, leather[3]), (22, 55, leather[1]),
                      (23, 55, leather[2]), (24, 55, leather[2]), (25, 55, leather[1]), (23, 56, leather[0]),
                      (24, 56, leather[1]), (24, 53, ruby[1])):
        px[x, y] = col                                                  # coin purse, tied with ruby cord
    # darned patches: one on the right side over the ribs, one low on the back
    stitched_patch(2, 48, 5, 51)
    stitched_patch(20, 57, 23, 60)
    # hem band: a gold line, a ruby band with a gold zig-zag, then the stitched dark hem
    for x in range(0, 28):
        if not px[x, 60][3]:
            continue
        px[x, 60] = gold[0] if x % 2 else gold[1]
        px[x, 61] = gold[2] if x % 4 == 0 else ruby[1]
        px[x, 62] = gold[1] if x % 4 == 2 else ruby[0]
        if px[x, 63][3]:
            px[x, 63] = thread if x % 3 == 0 else trim                  # stitched hem
    # shoulders (top face of the robe, uv 6,38): capelet cloth, a trim along the neck opening
    for y in range(38, 44):
        for x in (6, 7, 12, 13):
            px[x, y] = cloth(x, y, 0.18)
        px[7, y] = thread if y % 2 else trim
        px[12, y] = thread if y % 2 == 0 else trim

    # ------------------------------------------------------------- body (uv 16,20): sides and underside, under the robe
    fill(16, 26, 40, 38)
    fill(30, 20, 38, 26, -0.1)

    # ------------------------------------------------------------- sleeves (both side boxes of the crossed arms, uv 44,22)
    # the folded hands (uv 40,38) stay bare
    fill(48, 22, 56, 26, 0.05)
    for y in range(26, 34):
        for x in range(44, 60):
            # a fold across the elbow, light on the upper arm
            px[x, y] = cloth(x, y, 0.1 if y < 28 else (-0.12 if y == 29 else 0.0))
    stitched_patch(44, 27, 47, 30)                                      # elbow patch on the outer side
    for x in range(44, 60):
        px[x, 31] = deep                                                # the cuff's shadow on the sleeve
        px[x, 32] = gold[1] if x % 3 == 1 else (ruby[0] if x % 3 == 2 else trim)  # embroidered cuff band
        px[x, 33] = trim                                                # cuffs
    for x in range(52, 56):
        for y in range(22, 26):
            if y == 22 or x in (52, 55):
                px[x, y] = trim                                         # the cuff's rim round the wrist opening

    # ------------------------------------------------------------- trousers and boots (uv 0,22)
    fill(4, 22, 12, 26, -0.1)
    fill(0, 26, 16, 38, -0.05)
    for x in range(0, 16):
        px[x, 34] = leather[3] if x % 4 else leather[2]                 # turned-down boot tops, edge-lit
        for y in range(35, 38):
            px[x, y] = boot
        px[x, 35] = leather[0] if x % 4 else leather[1]                 # a strap round the ankle...
    for x in (5, 9):
        px[x, 35] = brass[1]                                            # ...buckled at the front and the outer side
    for x in range(8, 12):
        for y in range(22, 26):
            px[x, y] = boot                                             # soles
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
