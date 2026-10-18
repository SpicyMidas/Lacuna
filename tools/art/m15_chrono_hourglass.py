"""Chrono Hourglass textures: dark wood caps with a gold inlay and a chronal gem, wooden posts with gold collars, faint
see-through bulb glass, golden chronal sand (+ its falling stream), the 2D inventory icon and the chrono_tick particle.

UV layout used by tools/content/m15_chrono_hourglass.py:
  cap    natural UVs on the 14x14 plate faces (texels 1-14): gold border, wood rings, gold ring, gem in the middle
  frame  u 0-1 / v 0-13 lower posts (row 0 = middle of the hourglass), u 2-3 / v 0-13 upper posts (row 0 = top),
         rows 14-15 plate sides (row 14 = gold rim on the inner edge, row 15 = wood)
  glass  natural UVs on the bulb walls (u 4-12 at the widest layer)
"""
import math

from texlib import c, new, noise_fill, ramp, rng, save

WOOD = ramp("#24161A", "#3A2424", "#523328", "#6B4632")
GOLD = ramp("#6E4A0E", "#A8741A", "#DCA531", "#F6D46A", "#FFF1B0")
CHRONO = ramp("#2E2858", "#4A6AD8", "#7FB8F0", "#C8F0FF")
SAND = ramp("#B47A26", "#D69A36", "#EBB84F", "#F7D47A", "#FFEDB5")
GLINT = c("#B8E6FF")


def cap():
    r = rng("chrono_hourglass_cap")
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            ring = int(d * 1.4 + r.random() * 0.6) % 4
            px[x, y] = WOOD[[1, 2, 2, 3][ring]] if r.random() > 0.12 else WOOD[1]
    # gold border inlay on the visible 14x14 face (texels 1-14), lit from the top left
    for i in range(1, 15):
        px[i, 1] = GOLD[3]
        px[1, i] = GOLD[3]
        px[i, 14] = GOLD[1]
        px[14, i] = GOLD[1]
    px[1, 1] = GOLD[4]
    px[14, 14] = GOLD[0]
    for i in range(16):
        px[i, 0] = px[0, i] = px[i, 15] = px[15, i] = WOOD[0]
    # gold ring around the middle and the chronal gem
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.3 <= d < 4.3:
                px[x, y] = GOLD[3] if (x + y) < 15 else GOLD[2]
            elif 2.3 <= d < 3.3:
                px[x, y] = WOOD[0]
    for (x, y), col in {(7, 7): CHRONO[3], (8, 7): CHRONO[2], (7, 8): CHRONO[2], (8, 8): CHRONO[1],
                        (6, 7): CHRONO[1], (9, 8): CHRONO[0], (7, 6): CHRONO[2], (8, 9): CHRONO[0],
                        (6, 8): CHRONO[0], (9, 7): CHRONO[1], (8, 6): CHRONO[1], (7, 9): CHRONO[1]}.items():
        px[x, y] = col
    save(img, "block/chrono_hourglass_cap")


def frame():
    img = noise_fill(WOOD[:3], "chrono_hourglass_frame", cell=4, speckle=0.15)
    px = img.load()
    r = rng("chrono_hourglass_frame_posts")

    def post(u, collars):
        for y in range(14):
            px[u, y] = WOOD[3] if r.random() > 0.2 else WOOD[2]
            px[u + 1, y] = WOOD[1] if r.random() > 0.2 else WOOD[2]
        for y, (lit, shade) in collars.items():
            px[u, y] = GOLD[lit]
            px[u + 1, y] = GOLD[shade]

    # lower posts: wide collar at the top (the hourglass's waist), foot ring above the bottom plate
    post(0, {0: (4, 2), 1: (2, 1), 3: (3, 1), 11: (3, 1), 12: (3, 1), 13: (1, 0)})
    # upper posts: head ring under the top plate, collar at the bottom (the waist)
    post(2, {0: (3, 1), 1: (3, 1), 2: (1, 0), 10: (3, 1), 12: (3, 2), 13: (4, 2)})
    # plate sides: gold rim (inner edge) over dark wood
    for x in range(16):
        px[x, 14] = GOLD[3] if x % 5 else GOLD[4]
        px[x, 15] = WOOD[1] if r.random() > 0.25 else WOOD[2]
    px[0, 14] = px[15, 14] = GOLD[1]
    save(img, "block/chrono_hourglass_frame")


def glass():
    """Barely tinted, with two bright streaks and a soft third: the sand behind it reads clearly."""
    img = new()
    px = img.load()
    r = rng("chrono_hourglass_glass")
    for y in range(16):
        for x in range(16):
            px[x, y] = (205, 232, 255, 22 + (8 if r.random() < 0.2 else 0))
    for y in range(1, 15):
        px[5, y] = (255, 255, 255, 150 if 2 < y < 13 else 110)
        if 3 < y < 11:
            px[6, y] = (235, 248, 255, 85)
        if 5 < y < 13:
            px[10, y] = (230, 245, 255, 70)
    for x, y in ((4, 2), (11, 4), (9, 13)):
        px[x, y] = (255, 255, 255, 120)
    save(img, "block/chrono_hourglass_glass")


def sand():
    img = noise_fill(SAND[:4], "chrono_hourglass_sand", cell=2, speckle=0.3)
    px = img.load()
    r = rng("chrono_hourglass_sand_glints")
    for _ in range(6):
        px[r.randrange(16), r.randrange(16)] = GLINT
    for _ in range(5):
        px[r.randrange(16), r.randrange(16)] = SAND[4]
    save(img, "block/chrono_hourglass_sand")


def stream():
    """Falling sand: a 4-row repeating grain pattern (the renderer scrolls it down by up to 4 texels)."""
    img = new()
    px = img.load()
    rows = [SAND[3], SAND[2], SAND[4], SAND[1]]
    for y in range(16):
        for x in range(16):
            px[x, y] = rows[(y + (x % 2) * 2) % 4]
    for y in range(0, 16, 4):
        px[7, y] = GLINT
    save(img, "block/chrono_hourglass_stream")


# half-width of the bulb per icon row (centre between columns 7 and 8)
ICON_BULB = {3: 4, 4: 4, 5: 3, 6: 2, 7: 1, 8: 1, 9: 2, 10: 3, 11: 4, 12: 4}


def icon():
    img = new()
    px = img.load()
    edge = (214, 238, 255, 235)
    tint = (190, 226, 255, 80)
    shine = (255, 255, 255, 220)
    # plates: wood outside, gold rim inside
    for x in range(1, 15):
        end = x in (1, 14)
        px[x, 1] = WOOD[1] if end else WOOD[3]
        px[x, 2] = GOLD[1] if end else GOLD[3]
        px[x, 13] = GOLD[0] if end else GOLD[2]
        px[x, 14] = WOOD[0] if end else WOOD[2]
    px[5, 1] = px[9, 14] = WOOD[2]
    # posts with gold collars at the waist
    for y in range(3, 13):
        px[2, y] = WOOD[3]
        px[13, y] = WOOD[1]
    for y in (7, 8):
        px[2, y] = GOLD[4] if y == 7 else GOLD[2]
        px[13, y] = GOLD[2] if y == 7 else GOLD[0]
    # glass bulbs
    for y, hw in ICON_BULB.items():
        x0, x1 = 8 - hw, 7 + hw
        for x in range(x0, x1 + 1):
            px[x, y] = edge if x in (x0, x1) else tint
    for y in (3, 4, 10, 11):
        px[8 - ICON_BULB[y] + 1, y] = shine
    # sand: what is left in the top bulb, the stream through the neck, a heap in the bottom bulb
    for x in range(6, 10):
        px[x, 6] = SAND[2]
    for x in range(5, 11):
        px[x, 5] = SAND[3] if x in (5, 10) else SAND[4]
    px[7, 7] = SAND[3]
    px[8, 8] = SAND[2]
    px[7, 9] = SAND[3]
    for x in range(5, 11):
        px[x, 11] = SAND[3]
    px[6, 11] = SAND[4]
    for x in range(5, 11):
        px[x, 12] = SAND[1] if x in (5, 10) else SAND[2]
    px[7, 10] = SAND[4]
    px[8, 10] = SAND[3]
    px[9, 12] = GLINT
    save(img, "item/chrono_hourglass")


def chrono_tick():
    """Four 8x8 frames of a tiny clock face, the hand sweeping a quarter turn per frame (drawn for additive blending)."""
    hands = [((3, 2), (3, 1)), ((4, 3), (5, 3)), ((3, 4), (3, 5)), ((2, 3), (1, 3))]
    for frame, hand in enumerate(hands):
        img = new(8, 8)
        px = img.load()
        for y in range(8):
            for x in range(8):
                d = math.hypot(x - 3, y - 3)
                if 2.4 <= d < 3.4:
                    px[x, y] = (120, 190, 255, 170 if (x + y + frame) % 3 else 230)
        px[3, 0] = (200, 235, 255, 255)
        for x, y in hand:
            px[x, y] = (225, 245, 255, 255)
        px[3, 3] = (255, 255, 255, 255)
        save(img, f"particle/chrono_tick_{frame}")


def paint():
    cap()
    frame()
    glass()
    sand()
    stream()
    icon()
    chrono_tick()
