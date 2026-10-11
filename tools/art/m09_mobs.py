"""M9 textures: Lantern Moth (bee UV), Gloomcrawler (spider UV), Clockwork Hound (wolf UV), Clockwork Decoy
(humanoid UV), Silk Dust, Clockwork Gear, Clockwork Decoy item and the silk glow decal."""
from texlib import c, from_ascii, lerp, new, ramp, rng, save, with_alpha
from art.humanoid import face_rects, humanoid_skin


def paint_box(px, r, u, v, w, h, d, pal, faces=None, jitter=0.8, shade=None):
    """Fills every face of a vanilla box UV with a vertical ramp (top of a face = last colour)."""
    for face, (x0, y0, fw, fh) in face_rects(u, v, w, h, d).items():
        if faces and face not in faces:
            continue
        for y in range(y0, y0 + fh):
            for x in range(x0, x0 + fw):
                t = (y - y0) / max(1, fh - 1)
                if face in ("top",):
                    t = 0.0
                elif face == "bottom":
                    t = 1.0
                idx = int((1 - t) * (len(pal) - 1) + r.random() * jitter)
                px[x, y] = pal[max(0, min(len(pal) - 1, idx))]
        if shade and face in shade:
            for y in range(y0, y0 + fh):
                for x in range(x0, x0 + fw):
                    px[x, y] = lerp(px[x, y], c("#000000"), shade[face])


def rect(px, x0, y0, w, h, col):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = col


# ---------------------------------------------------------------------------------------- lantern moth (bee layout)
def lantern_moth():
    img = new(64, 64)
    px = img.load()
    r = rng("lantern_moth")
    fur = ramp("#B79A63", "#D8C08A", "#EDE0B4", "#FBF4DA")
    paint_box(px, r, 0, 0, 7, 7, 10, fur)
    # soft amber bands across the back and sides
    for x in range(10, 17):
        for y in (3, 4, 7, 8):
            px[x, y] = lerp(px[x, y], c("#C08A3A"), 0.55)
    for y in range(10, 17):
        for x in (3, 4, 7, 20, 23, 24):
            px[x, y] = lerp(px[x, y], c("#C08A3A"), 0.45)
    # face: dark fuzzy mask with big glossy eyes
    rect(px, 10, 10, 7, 7, c("#6E5434"))
    for (x, y) in ((11, 12), (11, 13), (12, 12), (12, 13), (14, 12), (14, 13), (15, 12), (15, 13)):
        px[x, y] = c("#14100C")
    px[11, 12] = c("#F6E7A8")
    px[15, 12] = c("#F6E7A8")
    for x in range(12, 15):
        px[x, 15] = c("#8C6B40")
    # glowing tail tip (back face)
    rect(px, 27, 10, 7, 7, c("#FFE9A0"))
    rect(px, 29, 12, 3, 3, c("#FFF8D8"))
    # feathery antennae
    for y in range(0, 6):
        for x in range(2, 11):
            px[x, y] = c("#7A5A34") if (x + y) % 2 else c("#9C7848")
    # wings: pale glowing membrane, golden veins, dusky eye spots
    for (x0, y0) in ((6, 18), (15, 18)):
        for y in range(y0, y0 + 6):
            for x in range(x0, x0 + 9):
                t = (x - x0) / 8.0
                px[x, y] = lerp(c("#FFF6D6"), c("#F3D98A"), t * 0.8)
        for x in range(x0, x0 + 9):
            px[x, y0] = c("#D9A84A")
            px[x, y0 + 5] = c("#C99640")
        for y in range(y0, y0 + 6):
            px[x0 + 8, y] = c("#C99640")
        for x in (x0 + 3, x0 + 6):
            px[x, y0 + 2] = c("#E2B65C")
            px[x, y0 + 3] = c("#E2B65C")
        px[x0 + 5, y0 + 2] = c("#8C6A3A")
        px[x0 + 5, y0 + 3] = c("#5A4228")
        px[x0 + 4, y0 + 3] = c("#8C6A3A")
    # legs and stinger (a soft tail tuft)
    for y in range(1, 7):
        for x in range(26, 40):
            px[x, y] = c("#5C4428")
    for (x, y) in ((26, 9), (27, 9), (28, 9), (29, 9), (28, 7), (28, 8)):
        px[x, y] = c("#EADBA8")
    save(img, "entity/lantern_moth")


# ---------------------------------------------------------------------------------------- gloomcrawler (spider layout)
def gloom_base(alpha=255):
    img = new(64, 32)
    px = img.load()
    r = rng("gloomcrawler")
    hide = ramp("#0E140D", "#18231A", "#22331F", "#30452A", alpha=alpha)
    paint_box(px, r, 32, 4, 8, 8, 8, hide)          # head
    paint_box(px, r, 0, 0, 6, 6, 6, hide)           # neck
    paint_box(px, r, 0, 12, 10, 8, 12, hide)        # abdomen
    legs = ramp("#0A0F09", "#141E12", "#22301C", alpha=alpha)
    paint_box(px, r, 18, 0, 16, 2, 2, legs)          # legs
    # leaf-litter mottling on the abdomen and head
    for _ in range(70):
        x = r.randrange(0, 44)
        y = r.randrange(12, 32)
        if px[x, y][3]:
            px[x, y] = with_alpha(r.choice([c("#3E5A2C"), c("#4C6B30"), c("#2A3B20"), c("#5B4A2A")]), alpha)
    for _ in range(18):
        x = r.randrange(32, 64)
        y = r.randrange(4, 20)
        if px[x, y][3]:
            px[x, y] = with_alpha(r.choice([c("#3E5A2C"), c("#2A3B20")]), alpha)
    # a pale chevron of dead leaves on the back (abdomen top)
    for i in range(5):
        for (x, y) in ((16 - i, 14 + i * 2), (17 + i, 14 + i * 2)):
            px[x, y] = with_alpha(c("#6B5A33"), alpha)
    # leg joints
    for x in range(20, 52, 4):
        px[x, 2] = with_alpha(c("#3A2A1A"), alpha)
        px[x, 3] = with_alpha(c("#3A2A1A"), alpha)
    return img


EYES = [(42, 15), (45, 15), (42, 16), (45, 16), (41, 14), (46, 14), (43, 13), (44, 13)]


def gloomcrawler():
    img = gloom_base()
    px = img.load()
    for (x, y) in EYES:
        px[x, y] = c("#5A0E14")
    px[42, 15] = c("#B0202C")
    px[45, 15] = c("#B0202C")
    save(img, "entity/gloomcrawler")
    hidden = gloom_base(alpha=40)
    save(hidden, "entity/gloomcrawler_hidden")
    eyes = new(64, 32)
    ep = eyes.load()
    for (x, y) in EYES:
        ep[x, y] = c("#C0202A")
    ep[42, 15] = c("#FF5A4A")
    ep[45, 15] = c("#FF5A4A")
    save(eyes, "entity/gloomcrawler_eyes")


# ---------------------------------------------------------------------------------------- clockwork hound (wolf layout)
def hound(angry):
    img = new(64, 32)
    px = img.load()
    r = rng("clockwork_hound")
    brass = ramp("#5E4524", "#86663A", "#B08D57", "#D2B57A")
    dark = ramp("#3A2A18", "#5A4226", "#76582F")
    paint_box(px, r, 0, 0, 6, 6, 4, brass)            # head
    paint_box(px, r, 16, 14, 2, 2, 1, dark)           # ears
    paint_box(px, r, 0, 10, 3, 3, 4, dark)            # muzzle
    paint_box(px, r, 18, 14, 6, 9, 6, brass)          # body
    paint_box(px, r, 21, 0, 8, 6, 7, ramp("#4A3520", "#6E5230", "#8E6F42", "#A9884F"))  # mane (gear housing)
    paint_box(px, r, 0, 18, 2, 8, 2, dark)            # legs
    paint_box(px, r, 9, 18, 2, 8, 2, ramp("#6B3A22", "#8A4A2A", "#A35E34"))  # tail: rusted spring
    # rust blooms
    for _ in range(40):
        x = r.randrange(0, 52)
        y = r.randrange(0, 32)
        if px[x, y][3]:
            px[x, y] = r.choice([c("#8A4A2A"), c("#A35E34"), c("#6B3A22")])
    # rivet lines along the body sides and plates on the mane
    for y in range(20, 29, 2):
        for x in (19, 22, 31, 34):
            px[x, y] = c("#E8D19A")
    for x in range(28, 36, 2):
        px[x, 9] = c("#2A1E12")
        px[x, 11] = c("#E8D19A")
    # spring coils on the tail
    for y in range(20, 28, 2):
        for x in range(9, 17):
            px[x, y] = c("#4A2814")
    # eyes on the head front (4..9, 4..9)
    eye = c("#FF3A2A") if angry else c("#FFC94A")
    glow = c("#FFB0A0") if angry else c("#FFF0B0")
    for (x, y) in ((5, 6), (8, 6)):
        px[x, y] = eye
    px[5, 5] = glow
    px[8, 5] = glow
    if angry:
        for x in range(4, 10):
            px[x, 4] = c("#3A2A18")
    # nose
    px[5, 14] = c("#1E140A")
    px[6, 14] = c("#1E140A")
    save(img, "entity/clockwork_hound_angry" if angry else "entity/clockwork_hound")


# ---------------------------------------------------------------------------------------- decoy (humanoid layout)
def decoy():
    img = humanoid_skin("clockwork_decoy", body=ramp("#5E4524", "#86663A", "#B08D57", "#D2B57A"),
                        eyes=c("#FFE27A"), seed="clockwork_decoy", accent=c("#6B3A22"),
                        legs=ramp("#3A2A18", "#5A4226", "#76582F"))
    px = img.load()
    # wind-up key on the back of the body and a gear rosette on the chest
    for (x, y) in ((35, 22), (36, 22), (35, 23), (36, 23), (34, 21), (37, 21), (34, 24), (37, 24)):
        px[x, y] = c("#E8D19A")
    for (x, y) in ((23, 22), (24, 22), (22, 23), (25, 23), (22, 24), (25, 24), (23, 25), (24, 25)):
        px[x, y] = c("#6B3A22")
    px[23, 23] = c("#E8D19A")
    px[24, 24] = c("#E8D19A")
    save(img, "entity/clockwork_decoy")


# ---------------------------------------------------------------------------------------- items and decal
SILK = [
    "................",
    "................",
    ".........w......",
    "....w...........",
    "...........s....",
    "......s.........",
    ".......w...s....",
    "..s.............",
    ".....ysy.....w..",
    "....yhhhy.......",
    "...yhwhhsy......",
    "..yhhhshhhy.....",
    ".ydhhhhhhhdy....",
    ".dddyyyyyddd....",
    "................",
    "................",
]

GEAR = [
    "................",
    ".......oo.......",
    "...oo.oGGo.oo...",
    "...oGooGGooGo...",
    "....oGGggGGo....",
    "..ooGgg..ggGoo..",
    ".oGGgg.oo.ggGGo.",
    ".oGGg.o..o.gGGo.",
    "..oGg.o..o.gGo..",
    "..oGgg.oo.ggGo..",
    "...oGgg..gggo...",
    "...oGGggggGGo...",
    "..oGooGGGGooGo..",
    "..oo..oGGo..oo..",
    ".......oo.......",
    "................",
]

DECOY = [
    "................",
    "......oooo......",
    ".....oGGGGo.....",
    ".....oGeGeo.....",
    ".....oGGGGo..kk.",
    "......oGGo..k..k",
    "....ooGGGGoo.kk.",
    "...oGGgrrgGGok..",
    "...oGgrGGrgGoo..",
    "...oGgrrrrgGo...",
    "....ooGggGoo....",
    ".....oGooGo.....",
    ".....oGooGo.....",
    ".....odooddo....",
    ".....oooooooo...",
    "................",
]


def items():
    save(from_ascii(SILK, {"w": c("#FFFDF0"), "s": c("#FFE27A"), "y": c("#E9C866"), "h": c("#F7E6A6"),
                           "d": c("#B8954A")}), "item/silk_dust")
    gear_pal = {"o": c("#3A2A18"), "G": c("#C9A869"), "g": c("#8E6F42")}
    save(from_ascii(GEAR, gear_pal), "item/clockwork_gear")
    save(from_ascii(DECOY, {"o": c("#3A2A18"), "G": c("#C9A869"), "g": c("#8E6F42"), "e": c("#FFE27A"),
                            "r": c("#8A4A2A"), "d": c("#5A4226"), "k": c("#E8D19A")}), "item/clockwork_decoy")
    # decal: sparse sparkles, mostly transparent (translucent render type)
    img = new(16, 16)
    px = img.load()
    r = rng("silk_dust_glow")
    for _ in range(26):
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = r.choice([c("#FFF6C8", 230), c("#FFE27A", 200), c("#FFFDF0", 250)])
    for _ in range(40):
        x, y = r.randrange(16), r.randrange(16)
        if not px[x, y][3]:
            px[x, y] = c("#FFE9A0", 70)
    save(img, "block/silk_dust_glow")


def paint():
    lantern_moth()
    gloomcrawler()
    hound(False)
    hound(True)
    decoy()
    items()
