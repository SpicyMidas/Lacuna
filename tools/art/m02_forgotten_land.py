"""M2 textures: ruin stone family, memory murals (paintings + mural blocks), Totem of Banishing, Forgotten Portal."""
import math

from PIL import Image

from texlib import c, from_ascii, lerp, new, noise_fill, ramp, rng, save, value_noise
from art.common import shift
from art import forgotten_portal

RUIN = ramp("#5E6670", "#6E7782", "#7D8691", "#8F98A3", "#A3ADB8")
GROOVE = c("#454C55")
LIGHT = c("#B4BDC7")


def ruin_stone():
    img = noise_fill(RUIN, "ruin_stone", cell=4, speckle=0.18, contrast=1.2)
    px = img.load()
    r = rng("ruin_stone_glyphs")
    # faint carved glyph grooves
    for _ in range(3):
        x, y = r.randint(1, 12), r.randint(1, 12)
        shape = r.choice([[(0, 0), (1, 0), (2, 0), (1, 1), (1, 2)], [(0, 0), (0, 1), (0, 2), (1, 2)],
                          [(0, 0), (1, 1), (2, 2), (2, 1)], [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)]])
        for dx, dy in shape:
            px[x + dx, y + dy] = GROOVE
    return img


def bricks(name, cracked=False):
    img = noise_fill(RUIN[1:], name, cell=4, speckle=0.15, contrast=1.0)
    px = img.load()
    for y in range(16):
        for x in range(16):
            row = y // 4
            offset = 4 if row % 2 else 0
            if y % 4 == 3 or (x + offset) % 8 == 7:
                px[x, y] = GROOVE
            elif y % 4 == 0 and (x + offset) % 8 != 7:
                px[x, y] = lerp(px[x, y], LIGHT, 0.35)
    if cracked:
        r = rng(name + "_cracks")
        for _ in range(3):
            x, y = r.randint(1, 14), r.randint(0, 12)
            for k in range(r.randint(2, 4)):
                if 0 <= x < 16 and 0 <= y < 16:
                    px[x, y] = GROOVE
                x += r.choice((-1, 0, 1))
                y += 1
    return img


def pillar_side():
    img = noise_fill(RUIN[1:], "ruin_pillar", cell=4, speckle=0.12)
    px = img.load()
    for y in range(16):
        for x in (0, 15):
            px[x, y] = GROOVE
        for x in (4, 11):
            px[x, y] = lerp(px[x, y], GROOVE, 0.6)
        px[1, y] = lerp(px[1, y], LIGHT, 0.4)
    for x in range(16):
        px[x, 0] = RUIN[0]
        px[x, 15] = RUIN[0]
    return img


def pillar_top():
    img = noise_fill(RUIN[1:], "ruin_pillar_top", cell=4, speckle=0.12)
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if 6.5 < d:
                px[x, y] = GROOVE
            elif 3.5 < d < 4.5:
                px[x, y] = lerp(px[x, y], GROOVE, 0.7)
    return img


GLYPH = [
    "................",
    ".gggggggggggggg.",
    ".g............g.",
    ".g.....gg.....g.",
    ".g...g....g...g.",
    ".g..g.gggg.g..g.",
    ".g....g..g....g.",
    ".g.g.g.gg.g.g.g.",
    ".g....g..g....g.",
    ".g..g.gggg.g..g.",
    ".g...g....g...g.",
    ".g.....gg.....g.",
    ".g............g.",
    ".g.g.g.g.g.g..g.",
    ".gggggggggggggg.",
    "................",
]


def chiseled():
    img = noise_fill(RUIN[1:], "chiseled_ruin_glyph", cell=4, speckle=0.1)
    glyph = from_ascii(GLYPH, {"g": GROOVE})
    img.alpha_composite(glyph)
    px = img.load()
    # light rim below each groove (carved look, light from top-left)
    for y in range(15):
        for x in range(15):
            if glyph.getpixel((x, y))[3] and not glyph.getpixel((x + 1, y + 1))[3]:
                px[x + 1, y + 1] = lerp(px[x + 1, y + 1], LIGHT, 0.5)
    return img


# --------------------------------------------------------------------------------------------- murals
# Pictures of what the land once was: warm, full colour scenes of a bright civilised city and sky.

SKY_DAWN = [c("#2B3A6B"), c("#6A4C8C"), c("#D9708A"), c("#FFB070"), c("#FFE2A0")]
CITY = [c("#3A2A40"), c("#5A3E58"), c("#8C5A60"), c("#C98A6A"), c("#F2C38A")]
GOLD = [c("#8A5A10"), c("#D49A22"), c("#F2C94C"), c("#FFF2A0")]
FIELD = [c("#3E5A20"), c("#6A8A2A"), c("#A8B83A"), c("#E0C850")]
FRAME = c("#4A3020")


def sky(img, pal, horizon):
    px = img.load()
    w, h = img.size
    for y in range(h):
        t = min(1.0, y / max(1, horizon))
        idx = t * (len(pal) - 1)
        lo = int(idx)
        hi = min(len(pal) - 1, lo + 1)
        col = lerp(pal[lo], pal[hi], idx - lo)
        for x in range(w):
            # ordered dither between bands (pixel-art friendly)
            if (x + y) % 2 and idx - lo > 0.5:
                px[x, y] = pal[hi]
            else:
                px[x, y] = pal[lo] if idx - lo < 0.5 else pal[hi]


def frame(img):
    px = img.load()
    w, h = img.size
    for x in range(w):
        px[x, 0] = FRAME
        px[x, h - 1] = FRAME
    for y in range(h):
        px[0, y] = FRAME
        px[w - 1, y] = FRAME


def towers(img, base_y, pal, seed, count, max_h, spire=True):
    r = rng(seed)
    px = img.load()
    w, _ = img.size
    x = 1
    while x < w - 2:
        tw = r.randint(2, 4)
        th = r.randint(max_h // 3, max_h)
        for xx in range(x, min(w - 1, x + tw)):
            for yy in range(base_y - th, base_y):
                if 0 < yy < img.height - 1:
                    shade = pal[1] if xx == x else pal[2]
                    px[xx, yy] = shade
        # windows
        for yy in range(base_y - th + 2, base_y - 1, 3):
            wx = x + tw // 2
            if 0 < yy < img.height - 1 and wx < w - 1:
                px[wx, yy] = GOLD[3]
        if spire and tw >= 3:
            top = base_y - th
            cx = x + tw // 2
            for k in range(1, 4):
                if top - k > 0:
                    px[cx, top - k] = GOLD[1] if k < 3 else GOLD[3]
        x += tw + r.randint(0, 2)


def ground(img, y0, pal):
    px = img.load()
    w, h = img.size
    for y in range(y0, h - 1):
        for x in range(1, w - 1):
            px[x, y] = pal[min(len(pal) - 1, (y - y0) * len(pal) // max(1, h - y0))]


def sun(img, cx, cy, rad, col, glow):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            d = math.hypot(x - cx, y - cy)
            if d <= rad:
                px[x, y] = col
            elif d <= rad + 1.5 and (x + y) % 2 == 0:
                px[x, y] = glow


def mural_dawn():
    img = new(32, 32)
    sky(img, SKY_DAWN, 22)
    sun(img, 16, 20, 4, c("#FFF2C0"), c("#FFD08A"))
    towers(img, 26, CITY, "dawn_towers", 6, 14)
    ground(img, 26, [c("#5A3E58"), c("#3A2A40")])
    frame(img)
    return img


def mural_spire():
    img = new(48, 32)
    sky(img, [c("#3A5A9C"), c("#5A8AD0"), c("#9CC8F0"), c("#E8F4FF")], 28)
    px = img.load()
    # great central spire
    for y in range(3, 28):
        half = max(1, (y - 3) // 4)
        for x in range(24 - half, 24 + half + 1):
            px[x, y] = CITY[3] if x < 24 else CITY[2]
    for y in range(1, 4):
        px[24, y] = GOLD[3]
    for y in range(8, 26, 4):
        px[24, y] = GOLD[2]
    towers(img, 28, [c("#2A3050"), c("#4A5078"), c("#7A86B0")], "spire_city", 10, 10)
    ground(img, 28, [c("#4A5078"), c("#2A3050")])
    # banners
    for bx in (8, 38):
        for y in range(10, 15):
            px[bx, y] = c("#D6143F")
        px[bx, 9] = GOLD[2]
    frame(img)
    return img


def mural_harvest():
    img = new(32, 32)
    sky(img, [c("#6AA8E0"), c("#9CCBF0"), c("#E8F4FF")], 14)
    sun(img, 25, 6, 3, c("#FFF2A0"), c("#FFE070"))
    ground(img, 15, FIELD)
    px = img.load()
    r = rng("harvest")
    for y in range(17, 30, 3):
        for x in range(2, 30):
            if (x + y) % 4 == 0:
                px[x, y] = GOLD[2]
    # people (tiny figures with baskets)
    for fx in (7, 15, 23):
        px[fx, 21] = c("#F0C8A0")
        px[fx, 22] = c("#C04040") if fx != 15 else c("#4060C0")
        px[fx, 23] = c("#3A2A20")
        px[fx + 1, 22] = GOLD[1]
    # distant city walls
    for x in range(1, 31):
        px[x, 14] = CITY[2]
        if x % 3 == 0:
            px[x, 13] = CITY[3]
    frame(img)
    return img


def mural_festival():
    img = new(48, 32)
    sky(img, [c("#140C2C"), c("#24184A"), c("#3A2468"), c("#5A3A8A")], 26)
    px = img.load()
    r = rng("festival")
    for _ in range(40):
        x, y = r.randint(1, 46), r.randint(1, 14)
        px[x, y] = c("#E8E8FF")
    towers(img, 27, [c("#1A1028"), c("#2A1C3C"), c("#3E2C54")], "festival_city", 12, 12, spire=True)
    # floating lanterns
    for _ in range(14):
        x, y = r.randint(2, 45), r.randint(3, 20)
        px[x, y] = c("#FFB040")
        if y + 1 < 31:
            px[x, y + 1] = c("#FF7A20")
    ground(img, 27, [c("#2A1C3C"), c("#1A1028")])
    # fireworks
    for cx, cy, col in ((12, 8, c("#FF5C7A")), (34, 6, c("#7FE8E0"))):
        for a in range(8):
            ang = a * math.pi / 4
            for k in (2, 3):
                x, y = int(round(cx + math.cos(ang) * k)), int(round(cy + math.sin(ang) * k))
                if 0 < x < 47 and 0 < y < 31:
                    px[x, y] = col
    frame(img)
    return img


def mural_fall():
    img = new(32, 32)
    sky(img, [c("#4A4A58"), c("#6A6070"), c("#9C7A70"), c("#D08A60")], 24)
    px = img.load()
    # broken towers, the city fading into gray
    towers(img, 26, [c("#3A3440"), c("#5A5260"), c("#7A7280")], "fall_towers", 6, 16, spire=False)
    r = rng("fall_cracks")
    for _ in range(30):
        x, y = r.randint(1, 30), r.randint(1, 25)
        if px[x, y][0] < 0x80:
            px[x, y] = c("#8C9DB0")  # the gray-blue of the Forgotten Land seeping in
    for _ in range(12):
        x, y = r.randint(1, 30), r.randint(1, 20)
        px[x, y] = c("#B0B0B8")  # ash
    ground(img, 26, [c("#5E6670"), c("#454C55")])
    frame(img)
    return img


def mural_block(picture: Image.Image):
    """16x16 front face for the mural fallback blocks: the picture centred inside a ruin-stone frame."""
    inner = picture.resize((12, 12 if picture.width == picture.height else 8), Image.NEAREST)
    img = noise_fill(RUIN[1:], "mural_frame", cell=4, speckle=0.1)
    px = img.load()
    for i in range(16):
        px[i, 0] = GROOVE
        px[0, i] = GROOVE
        px[i, 15] = RUIN[0]
        px[15, i] = RUIN[0]
    oy = (16 - inner.height) // 2
    img.paste(inner, (2, oy))
    return img


# --------------------------------------------------------------------------------------------- totem
# Inspired by the provided 16x16 open-hand texture: same silhouette (four fingers, thumb out to the left,
# creases on the palm), recoloured as weathered pale stone, with a faceted ruby set low in the palm.

TOTEM = [
    "........OO......",
    ".....OqOmsOOO...",
    ".....OhOnsOhO...",
    ".....omomnOmoOO.",
    ".....qmqhsomOhO.",
    ".....OmOmmqmOmO.",
    ".Oq..qmOmmOmOhO.",
    "OmmO.OmmmhmhmkO.",
    "qkmsOOmmmmmhhhO.",
    ".Oskmmmkmmkmmmo.",
    "..Ommmmskmmkmmq.",
    "...Okhhmmmmhhso.",
    "....OmmmgGmhkOO.",
    ".....qssGdssO...",
    ".....OkmmmmkO...",
    "......OOoqOOx...",
]


def totem():
    pal = {
        "O": c("#4E4338"), "o": c("#5E5247"), "q": c("#6E6A64"), "x": c("#6E6A64"),
        "m": c("#CFC6B3"), "h": c("#ECE6D8"), "n": c("#BDB4A2"), "s": c("#A39B8C"), "k": c("#8C8780"),
        "g": c("#FF8FA3"), "G": c("#D6143F"), "d": c("#8E0F2A"),
    }
    img = from_ascii(TOTEM, pal)
    px = img.load()
    # faceted ruby set low in the palm: crown facets on top, bright table, dark pavilion below
    px[8, 11] = c("#FF5C7A")
    px[9, 11] = c("#FF5C7A")
    px[7, 12] = c("#8E0F2A")
    px[8, 12] = c("#FFC2CC")
    px[9, 12] = c("#D6143F")
    px[10, 12] = c("#8E0F2A")
    px[8, 13] = c("#D6143F")
    px[9, 13] = c("#4A0716")
    # a little lichen on the weathered stone
    px[6, 3] = c("#9BA889")
    px[13, 9] = c("#9BA889")
    return img


def paint():
    save(ruin_stone(), "block/ruin_stone")
    save(bricks("ruin_stone_bricks"), "block/ruin_stone_bricks")
    save(bricks("cracked_ruin_stone_bricks", cracked=True), "block/cracked_ruin_stone_bricks")
    save(pillar_side(), "block/ruin_pillar")
    save(pillar_top(), "block/ruin_pillar_top")
    save(chiseled(), "block/chiseled_ruin_glyph")
    pictures = [("memory_dawn", mural_dawn()), ("memory_spire", mural_spire()), ("memory_harvest", mural_harvest()),
                ("memory_festival", mural_festival()), ("memory_fall", mural_fall())]
    for i, (name, pic) in enumerate(pictures, start=1):
        save(pic, f"painting/{name}")
        save(mural_block(pic), f"block/mural_block_{i}")
    save(totem(), "item/totem_of_banishing")
    forgotten_portal.paint()
