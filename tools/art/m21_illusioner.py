"""The Illusioner: vanilla's unused illager, brought into the game by Lacuna (illusioner/**), re-dressed in starcloth.

Overrides the vanilla texture (assets/minecraft/textures/entity/illager/illusioner.png, the 64x64 illager layout of
client/model/IllagerModel) so the vanilla renderer and Lacuna's duplicates (client/illusioner) wear the same robe:

* the evoker's robe cut (V-neck, a gold line down the front, a split hem, cuffed sleeves) woven from starcloth: deep
  night-blue cloth in the starlace weave (a soft check of two tones) with tiny star glints, thicker towards the hem,
  silver-gold trim (old gold threads, starlace silver hem bands) and a small constellation stitched on the back;
* the hat layer (shown by the vanilla IllusionerRenderer) painted as a pointed hood: a seam along the crown that runs
  down the back into a liripipe tail with a gold tassel, a gold-rimmed face opening and side drapes down to the chest;
* the face in the hood's shadow: dark brow, a dim nose and chin, eyes of pale starlight (also drawn full-bright by the
  eyes layer, lacuna:textures/entity/illusioner_eyes.png).
"""
from texlib import c, new, ramp, rng, save

# deep night blue starcloth (dark .. light) and the starlace palette it is woven from (art/m16_starlace_bandage.py)
CLOTH = ramp("#181C36", "#1F2445", "#272D55", "#303866", "#3A4478", "#46528A")
SILVER = ramp("#5A6A8C", "#8FA3C4", "#B8C7E0", "#E8F0FF")
GOLD = ramp("#9C5A1C", "#E8A93A", "#FFD866", "#FFF0A8")
GLINT = [c("#E8F0FF"), c("#FFFFFF"), c("#C9B8FF"), c("#B8C7E0")]
SKIN = ramp("#5E6464", "#737979", "#848989", "#8E9393", "#959B9B")       # illager grey
SHADE = ramp("#0F1120", "#161A2C", "#1F2436", "#2B3040", "#3C4250")       # the face in the hood's shadow
EYE = [c("#C9B8FF"), c("#F4F0FF")]
BOOT = ramp("#151824", "#1E2232", "#282D40")

TEX = "../../minecraft/textures/entity/illager/illusioner"               # assets/minecraft/... (vanilla override)


def faces(u, v, w, h, d):
    """Box-UV face rectangles (x0, y0, x1, y1) of a cube at texture offset (u, v), as vanilla lays them out."""
    return {
        "top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h), "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def paint_illusioner():
    img = new(64, 64)
    px = img.load()
    r = rng("illusioner_starcloth")
    folds = [r.uniform(-0.12, 0.12) for _ in range(64)]

    def cloth(x, y, lift=0.0, glints=0.0):
        """Starcloth: the starlace check (two neighbouring tones) over soft folds, now and then a star glint."""
        if glints and r.random() < glints:
            g = r.random()
            return GLINT[0] if g < 0.45 else GLINT[2] if g < 0.75 else GLINT[3] if g < 0.92 else GLINT[1]
        v = 0.5 + folds[x] + lift + r.uniform(-0.1, 0.1) + (0.07 if (x + y) % 2 == 0 else -0.07)
        return CLOTH[max(0, min(len(CLOTH) - 1, int(v * len(CLOTH))))]

    def fill(rect, lift=0.0, glints=0.0, grad=0.0):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            t = (y - y0) / max(1, y1 - y0 - 1)
            for x in range(x0, x1):
                px[x, y] = cloth(x, y, lift - grad * t, glints)

    def skin(rect, lift=0.0):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                v = 0.55 + lift + r.uniform(-0.18, 0.18)
                px[x, y] = SKIN[max(0, min(len(SKIN) - 1, int(v * len(SKIN))))]

    # ------------------------------------------------------------------ head (uv 0,0; 8x10x8), mostly under the hood
    head = faces(0, 0, 8, 10, 8)
    for name in ("top", "bottom", "right", "left", "back"):
        x0, y0, x1, y1 = head[name]
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = SHADE[r.randint(1, 3)]
    # the face (8..15, 8..17): black under the brim, a dark brow, starlight eyes, a dim nose and chin
    for y in range(8, 18):
        for x in range(8, 16):
            if y <= 11:
                px[x, y] = SHADE[0] if y < 11 else SHADE[1]
            elif y <= 13:
                px[x, y] = SHADE[1] if x in (8, 15) or y == 13 and x in (9, 10, 13, 14) else SHADE[2]
            elif y == 14:
                px[x, y] = SHADE[1]
            else:
                # the lower face catches a little light from below, darker at the cheeks
                k = 2 + (y - 15) + (0 if 9 <= x <= 14 else -1)
                px[x, y] = SHADE[max(1, min(4, k))]
    px[9, 14], px[10, 14] = EYE[0], EYE[1]
    px[13, 14], px[14, 14] = EYE[1], EYE[0]
    px[11, 17], px[12, 17] = SKIN[0], SKIN[0]                           # the chin, just lit
    # nose (uv 24,0; 2x4x2): dim grey, its top lost in shadow
    nose = faces(24, 0, 2, 4, 2)
    for name, rect in nose.items():
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                if name == "top":
                    px[x, y] = SHADE[2]
                elif name == "bottom":
                    px[x, y] = SKIN[0]
                else:
                    px[x, y] = SHADE[3] if y < y0 + 2 else SKIN[0] if name == "front" else SHADE[4]

    # ------------------------------------------------------------------ hood (hat layer, uv 32,0; 8x12x8, inflated)
    hood = faces(32, 0, 8, 12, 8)
    for name in ("top", "right", "front", "left", "back"):
        fill(hood[name], 0.08, 0.035)
    # crown: a seam along the middle from the brow to the nape, the cloth rising to it (a pointed hood)
    for y in range(0, 8):
        for x in range(40, 48):
            if x in (43, 44):
                px[x, y] = CLOTH[4] if x == 43 else CLOTH[5]
            elif x in (42, 45):
                px[x, y] = cloth(x, y, 0.15)
        px[43 + (y % 2), y] = SILVER[0] if y % 3 == 0 else px[43 + (y % 2), y]
    # face opening (front 40..47 x 8..19): the brim overhangs, a gold rim, side drapes down over the chest
    for y in range(8, 20):
        for x in range(40, 48):
            if 12 <= y <= 17 and 41 <= x <= 46 and not (y == 12 and x in (41, 46)):
                px[x, y] = (0, 0, 0, 0)
            elif y >= 18 and 42 <= x <= 45:
                px[x, y] = (0, 0, 0, 0)                                 # open at the throat: the robe's V shows
            elif y == 19 and x in (41, 46):
                px[x, y] = (0, 0, 0, 0)
    for x in range(41, 47):
        px[x, 11] = GOLD[1] if x in (41, 46) else GOLD[2]               # the brim's gold edge
    px[41, 12], px[46, 12] = GOLD[1], GOLD[1]
    for y in range(13, 19):
        px[40, y] = GOLD[1] if y % 2 else GOLD[0]
        px[47, y] = GOLD[1] if y % 2 else GOLD[0]
    px[41, 18], px[46, 18] = SILVER[2], SILVER[2]
    px[40, 19], px[47, 19] = SILVER[1], SILVER[1]
    for x in range(41, 47):
        px[x, 10] = CLOTH[1]                                            # the brim's underside, in shadow
    # sides: a silver band along the lower edge, cut back towards the face like a draped hood
    for x0 in (32, 48):
        for x in range(x0, x0 + 8):
            px[x, 19] = SILVER[1] if x % 2 else SILVER[2]
            px[x, 18] = cloth(x, 18, -0.15)
        # the hood's cheek folds: a darker fold line running back from the face opening
        for y in range(12, 18):
            fx = x0 + 6 if x0 == 32 else x0 + 1
            px[fx, y] = CLOTH[1]
    # back: the seam runs down into a liripipe tail with a gold tassel at its point
    for y in range(8, 20):
        for x in range(56, 64):
            if x in (59, 60):
                px[x, y] = CLOTH[4] if x == 59 else CLOTH[3]
    for y in range(8, 19, 3):
        px[59, y] = SILVER[0]
    for x in range(56, 64):
        px[x, 19] = SILVER[1] if x % 2 else SILVER[2]
    px[58, 18], px[61, 18] = CLOTH[2], CLOTH[2]
    px[59, 19], px[60, 19] = GOLD[2], GOLD[1]                           # tassel
    px[59, 18], px[60, 18] = GOLD[1], GOLD[0]

    # ------------------------------------------------------------------ body (uv 16,20; 8x12x6), under the robe
    for name, rect in faces(16, 20, 8, 12, 6).items():
        fill(rect, -0.05)
    # the inner tunic shows in the V of the robe: pale starlace with a gold seam
    for y in range(26, 30):
        for x in range(22, 30):
            px[x, y] = SILVER[1] if (x + y) % 2 else SILVER[2]
        px[25, y], px[26, y] = GOLD[1], GOLD[2]

    # ------------------------------------------------------------------ robe (uv 0,38; 8x20x6): the evoker's cut
    robe = faces(0, 38, 8, 20, 6)
    for name in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = robe[name]
        for y in range(y0, y1):
            t = (y - y0) / (y1 - y0 - 1)
            for x in range(x0, x1):
                # light on the shoulders, deeper towards the hem; more stars in the lower skirt, like a night sky
                px[x, y] = cloth(x, y, 0.1 - 0.2 * t, 0.01 + 0.035 * t * t)
    # shoulders (top face, the strips beside the neck)
    for y in range(38, 44):
        for x in (6, 7, 12, 13):
            px[x, y] = cloth(x, y, 0.15)
        px[6, y] = GOLD[1]
        px[13, y] = GOLD[1]
    # V-neck and hem split (transparent, as on the evoker), the gold collar lines and the line down the front
    for y, cols in {44: range(7, 13), 45: range(8, 12), 46: range(9, 11), 62: range(9, 11), 63: range(7, 13)}.items():
        for x in cols:
            px[x, y] = (0, 0, 0, 0)
    for y, (a, b) in {44: (6, 13), 45: (7, 12), 46: (8, 11)}.items():
        px[a, y] = GOLD[2]
        px[b, y] = GOLD[1]
    for x in range(21, 27):
        px[x, 44] = GOLD[1] if x in (21, 26) else GOLD[2]               # collar round the back
    px[0, 44], px[27, 44] = GOLD[0], GOLD[0]
    for y in range(47, 62):
        px[9, y] = GOLD[1] if y % 4 else GOLD[2]
        px[10, y] = GOLD[2] if y % 4 else GOLD[3]
    px[8, 62], px[11, 62] = GOLD[1], GOLD[1]
    # silver-gold hem band all round (the front split excepted): silver, a gold stitch, deep shadow under it
    for x in range(0, 28):
        if px[x, 60][3]:
            px[x, 60] = GOLD[1] if x % 3 == 0 else SILVER[1]
        if px[x, 61][3] and x not in (9, 10):
            px[x, 61] = SILVER[2] if x % 2 else SILVER[1]
        if px[x, 62][3]:
            px[x, 62] = CLOTH[1]
        if px[x, 63][3]:
            px[x, 63] = CLOTH[0] if x % 3 else SILVER[0]
    # a constellation stitched in gold on the back of the robe (the Mirror, if anyone asks)
    for (x, y), col in {(22, 49): GOLD[2], (24, 48): GOLD[3], (25, 51): GOLD[2], (23, 53): GOLD[2], (21, 55): GOLD[1],
                        (26, 55): GOLD[2]}.items():
        px[x, y] = col
    for (x, y) in ((23, 49), (24, 50), (24, 52), (22, 54)):
        px[x, y] = CLOTH[5]                                             # faint thread between the stars
    # sides: a slit at the hip edged in silver
    for y in range(56, 60):
        px[2, y] = SILVER[0]
        px[17, y] = SILVER[0]

    # ------------------------------------------------------------------ crossed arms (uv 44,22 and 40,38)
    for name, rect in faces(44, 22, 4, 8, 4).items():
        fill(rect, 0.05, 0.02)
    for x in range(44, 60):
        px[x, 32] = GOLD[1] if x % 2 else GOLD[2]                      # cuff: gold over silver
        px[x, 33] = SILVER[1]
    for name, rect in faces(40, 38, 8, 4, 4).items():
        fill(rect, 0.02, 0.02)
    for y in range(38, 46):
        px[47, y] = GOLD[1]                                             # where the two sleeves cross
        px[56, y] = GOLD[1] if y < 42 else SILVER[1]
    # ------------------------------------------------------------------ arms (uv 40,46; 4x12x4): sleeve, cuff, hand
    arm = faces(40, 46, 4, 12, 4)
    for name in ("top", "right", "front", "left", "back"):
        fill(arm[name], 0.06, 0.02)
    skin(arm["bottom"])
    for x in range(40, 56):
        px[x, 56] = SILVER[1] if x % 2 else SILVER[2]                   # wide sleeve ends: silver band...
        px[x, 57] = GOLD[1]                                             # ...and a gold cuff
        px[x, 58] = CLOTH[1]                                            # the sleeve's shadow on the wrist
    skin((40, 59, 56, 62))

    # ------------------------------------------------------------------ legs (uv 0,22; 4x12x4): trousers and boots
    for name, rect in faces(0, 22, 4, 12, 4).items():
        fill(rect, -0.1)
    for y in range(34, 38):
        for x in range(0, 16):
            px[x, y] = BOOT[2] if y == 34 else BOOT[1] if y < 37 else BOOT[0]
    for x in range(8, 12):
        for y in range(22, 26):
            px[x, y] = BOOT[0]                                          # soles
    save(img, TEX)


def paint_eyes():
    """The eyes alone, drawn full-bright on top (client/illusioner eyes layer): pale starlight under the hood."""
    img = new(64, 64)
    px = img.load()
    px[9, 14], px[10, 14] = EYE[0], EYE[1]
    px[13, 14], px[14, 14] = EYE[1], EYE[0]
    save(img, "entity/illusioner_eyes")


def paint():
    paint_illusioner()
    paint_eyes()
