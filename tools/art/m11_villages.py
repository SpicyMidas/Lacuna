"""M11 textures: village civic blocks, the village coin, guild items and the civic professions."""
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
    save(planks(WOOD, "township_ledger_side"), "block/village_ledger_side")  # old seed name keeps the pixels identical
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
    save(img, "block/village_ledger_top")


def furniture():
    img = planks(WOOD, "council_table")
    px = img.load()
    for i in range(16):
        px[i, 0] = c("#C9A869")
        px[i, 15] = c("#C9A869")
    save(img, "block/council_table")
    save(planks(DARK, "judge_bench"), "block/judge_bench")
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


COIN = [
    "................",
    "................",
    ".....oooooo.....",
    "....oGGGGGGo....",
    "...oGgGGGGgGo...",
    "..oGgGhhhhGgGo..",
    "..oGGhGGGGhGGo..",
    "..oGGhGGGGGGGo..",
    "..oGGhGGGGhGGo..",
    "..oGgGhhhhGgGo..",
    "...oGgGGGGgGo...",
    "....oGGGGGGo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]


def coin():
    save(from_ascii(COIN, {"o": c("#6B4A12"), "G": c("#E2B64A"), "g": c("#B98A2A"), "h": c("#FFF2B0")}), "item/village_coin")


def profession_overlay(name, robe, trim, accessory):
    from art.m09_mobs import paint_box
    img = new(64, 64)
    px = img.load()
    r = rng(name)
    paint_box(px, r, 0, 38, 8, 20, 6, robe)        # robe over the jacket area
    paint_box(px, r, 16, 20, 8, 12, 6, robe)       # body
    for y in range(44, 64):
        px[10, y] = trim
    for x in range(6, 14):
        px[x, 44] = trim
    accessory(px)
    for kind in ("villager", "zombie_villager"):
        save(img, f"entity/{kind}/profession/{name}")


def coiner_bits(px):
    for (x, y) in ((8, 50), (9, 50), (8, 51), (12, 54), (13, 54), (12, 55)):
        px[x, y] = c("#E2B64A")  # coins sewn on the apron


def magistrate_bits(px):
    for x in range(22, 30):
        px[x, 26] = c("#F2F2F2")  # white collar
        px[x, 27] = c("#F2F2F2")


def guildmaster_bits(px):
    for i in range(12):
        px[6 + i % 8, 44 + i] = c("#C9A869")  # sash


EMBLEMS = {
    "smiths": ["................", "................", "....aaaaaaaa....", "...aaaaaaaaaaa..", "....aaaaaaaa....",
               "......aaaa......", ".....aaaaaa.....", "....aaaaaaaa....", "................"],
    "gemcutters": ["................", ".......gg.......", "......gGGg......", ".....gGhhGg.....", "....gGGhhGGg....",
                   ".....gGGGGg.....", "......gGGg......", ".......gg.......", "................"],
    "scholars": ["................", "...bbbb..bbbb...", "..bwwwwbbwwwwb..", "..bwlwwbbwlwwb..", "..bwwwwbbwwwwb..",
                 "..bwlwwbbwlwwb..", "..bwwwwbbwwwwb..", "...bbbbbbbbbb...", "................"],
}


def guild_boards():
    pal = {"a": c("#5A5F66"), "g": c("#7A1E2A"), "G": c("#C0202C"), "h": c("#FF8A8A"), "b": c("#3A2A50"), "w": c("#EFE6CF"),
           "l": c("#6A6050")}
    for guild, emblem in EMBLEMS.items():
        img = planks(DARK, f"guild_board_{guild}")
        px = img.load()
        for y, row in enumerate(emblem):
            for x, ch in enumerate(row):
                if ch != ".":
                    px[x, y + 3] = pal[ch]
        for (x, y) in ((2, 13), (3, 13), (12, 13), (13, 13)):
            px[x, y] = c("#E8DFC4")
        save(img, f"block/guild_board_{guild}")


CONTRACT = [
    "................",
    "...pppppppppp...",
    "..pPPPPPPPPPPp..",
    "..pPllllllllPp..",
    "..pPPPPPPPPPPp..",
    "..pPlllllllPPp..",
    "..pPPPPPPPPPPp..",
    "..pPllllllPPPp..",
    "..pPPPPPPPPPPp..",
    "..pPPPPPPPrrPp..",
    "..pPPPPPPrRRrp..",
    "..pPPPPPPrRRrp..",
    "..pPPPPPPPrrPp..",
    "...pppppppppp...",
    "................",
    "................",
]

KIT = [
    "................",
    "................",
    "....oooooooo....",
    "...oLLLLLLLLo...",
    "..oLlllllllLLo..",
    "..oLlhhllsslLo..",
    "..oLlhhllsslLo..",
    "..oLllllllllLo..",
    "..oGGGGGGGGGGo..",
    "..oLllllllllLo..",
    "..oLlllllllLLo..",
    "...oLLLLLLLLo...",
    "....oooooooo....",
    "................",
    "................",
    "................",
]


def items():
    save(from_ascii(CONTRACT, {"p": c("#8A7A5A"), "P": c("#EFE6CF"), "l": c("#6A6050"), "r": c("#7A1E2A"), "R": c("#C0202C")}),
         "item/guild_contract")
    save(from_ascii(KIT, {"o": c("#2A1A10"), "L": c("#734F33"), "l": c("#8A6040"), "h": c("#B8BEC4"), "s": c("#6A6F76"),
                          "G": c("#C9A869")}), "item/repair_kit")


def paint():
    guild_boards()
    items()
    coin()
    profession_overlay("coiner", ramp("#4A3220", "#5E4029", "#734F33"), c("#E2B64A"), coiner_bits)
    profession_overlay("magistrate", ramp("#14141A", "#1E1E26", "#2A2A33"), c("#B03A2E"), magistrate_bits)
    profession_overlay("guildmaster", ramp("#3A1E4A", "#4E2A63", "#663A80"), c("#C9A869"), guildmaster_bits)
    ledger()
    furniture()
