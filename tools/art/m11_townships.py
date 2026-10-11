"""M11 textures: township civic blocks (11a); coins, contracts and job blocks are appended by later parts."""
from texlib import c, from_ascii, lerp, new, noise_fill, ramp, rng, save

WOOD = ramp("#4A3220", "#5E4029", "#734F33", "#8A6040")
DARK = ramp("#2A1A10", "#3A2416", "#4A301E", "#5A3C26")
IRON = ramp("#4A4E54", "#6A6F76", "#8E949B", "#B8BEC4")


def planks(pal, name):
    img = noise_fill(pal, name, cell=4, speckle=0.12)
    px = img.load()
    for y in (3, 7, 11, 15):
        for x in range(16):
            px[x, y] = pal[0]
    r = rng(name + "_nails")
    for y in (1, 5, 9, 13):
        x = r.randrange(16)
        px[x, y] = pal[0]
    return img


def ledger():
    save(planks(WOOD, "township_ledger_side"), "block/township_ledger_side")
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = c("#6B2A22") if x in (0, 15) or y in (0, 15) else c("#EFE6CF")
    for x in range(1, 15):
        px[x, 8] = c("#B9AE96")  # spine fold
    for y in (3, 5, 11, 13):
        for x in range(2, 14):
            if x != 8 and (x + y) % 5:
                px[x, y] = c("#5A5040")
    px[12, 1] = c("#C9A869")
    px[12, 2] = c("#C9A869")
    save(img, "block/township_ledger_top")


def furniture():
    img = planks(WOOD, "council_table")
    px = img.load()
    for i in range(16):
        px[i, 0] = c("#C9A869")
        px[i, 15] = c("#C9A869")
    save(img, "block/council_table")
    save(planks(DARK, "judge_bench"), "block/judge_bench")
    board = planks(DARK, "guild_board")
    bp = board.load()
    r = rng("guild_notes")
    for _ in range(4):
        x0, y0 = r.randrange(1, 11), r.randrange(1, 11)
        for y in range(y0, y0 + 4):
            for x in range(x0, x0 + 4):
                bp[x, y] = c("#E8DFC4")
        bp[x0 + 1, y0] = c("#B03A2E")  # pin
        for x in range(x0, x0 + 3):
            bp[x, y0 + 2] = c("#6A6050")
    save(board, "block/guild_board")
    desk = planks(ramp("#6E5236", "#86663F", "#9C7A4C", "#B08C5A"), "school_desk")
    dp = desk.load()
    for (x, y) in ((3, 4), (4, 4), (5, 4), (3, 5), (5, 5)):
        dp[x, y] = c("#2A2A2A")  # ink well
    save(desk, "block/school_desk")
    press = noise_fill(IRON, "mint_press", cell=3, speckle=0.2)
    pp = press.load()
    for x in range(16):
        pp[x, 0] = c("#2E3236")
        pp[x, 15] = c("#2E3236")
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        pp[x, y] = c("#E7C46A")
    save(press, "block/mint_press")


GATE = [
    "WWWWWWWWWWWWWWWW",
    "WwwwwwwwwwwwwwwW",
    ".i..i..i..i..i..",
    ".i..i..i..i..i..",
    ".i..i..i..i..i..",
    "iiiiiiiiiiiiiiii",
    ".i..i..i..i..i..",
    ".i..i..i..i..i..",
    ".i..i..i..i..i..",
    ".i..i..i..i..i..",
    "iiiiiiiiiiiiiiii",
    ".i..i..i..i..i..",
    ".i..i..i..i..i..",
    ".i..i..i..i..i..",
    ".I..I..I..I..I..",
    ".I..I..I..I..I..",
]


def gate():
    save(from_ascii(GATE, {"W": c("#5E4029"), "w": c("#734F33"), "i": c("#5A5F66"), "I": c("#8E949B")}), "block/township_gate")


def paint():
    ledger()
    furniture()
    gate()
