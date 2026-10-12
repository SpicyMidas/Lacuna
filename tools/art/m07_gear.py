"""M7 textures: Zenithite, Starlace cloth/armor (+ armor layers), Umbral tools, five special weapons, Rooted icon."""
import math

from texlib import c, from_ascii, lerp, new, noise_fill, ramp, rng, save, with_alpha
from art.common import STICK, deepslate_base, gem, ore, shards, stone_base, storage_block, tool

ZEN = ramp("#1A4A78", "#2A6FA8", "#4FA8E8", "#9FE0FF", "#E8FAFF")
STAR = ramp("#5A6A8C", "#8FA3C4", "#B8C7E0", "#E8F0FF", "#FFFFFF")
UMBRAL_TOOL = [c("#161927"), c("#2E3350"), c("#444B73"), c("#6A72A0"), c("#9B6BFF")]

HELMET = [
    "................", "................", ".....oooooo.....", "....oHhhhhhmo...", "...oHhhhhhhhmo..",
    "..oHhhhhhhhhmdo.", "..ohmmmmmmmmmdo.", "..ohmmsmmsmmmdo.", "..ohmoooooomdo..", "..ohmo....omdo..",
    "..oooo....oooo..", "................", "................", "................", "................",
    "................"]
CHEST = [
    "................", "..ooo......ooo..", ".oHho......ohmo.", ".ohmooooooooomdo", ".ohmmhhhhhhmmmdo",
    ".ooomhmmmmmmmooo", "...ohmmmsmmmdo..", "...ohmmmmmmmdo..", "...ohmmmsmmmdo..", "...ohmmmmmmmdo..",
    "...ohmmmsmmmdo..", "...ohmmmmmmmdo..", "...oooooooooooo.", "................", "................",
    "................"]
LEGS = [
    "................", "................", "...oooooooooo...", "...ohhhhhhhhmo..", "...ohmmmmmmmdo..",
    "...ohmmooommdo..", "...ohmo...omdo..", "...ohmo...omdo..", "...ohmo...omdo..", "...ohmo...omdo..",
    "...ohmo...omdo..", "...ohmo...omdo..", "...oooo...oooo..", "................", "................",
    "................"]
BOOTS = [
    "................", "................", "................", "................", "................",
    "................", "...oooo..oooo...", "...ohmo..ohmo...", "...ohmo..ohmo...", "...ohmo..ohmo...",
    "..oohmo..ohmoo..", ".ohhmmo..ohmmdo.", ".oooooo..oooooo.", "................", "................",
    "................"]

GREATSWORD = [
    "............oooo",
    "...........ohhlo",
    "..........ohllmo",
    ".........ohllmo.",
    "........ohllmo..",
    ".......ohllmo...",
    "......ohllmo....",
    ".....ohllmo.....",
    "..oooohllmo.....",
    "..oRrhllmo......",
    "..ooRhlmooo.....",
    "...oohmoRro.....",
    "...cbcooooo.....",
    "..cac...........",
    ".cac............",
    "cbc.............",
]

SICKLE = [
    "................",
    ".......oooo.....",
    ".....oohhlloo...",
    "....ohlo..ollo..",
    "...ohlo.....olo.",
    "...olo.......oo.",
    "...olo..........",
    "....olo.........",
    ".....oo.........",
    "......cac.......",
    ".....cac........",
    "....cac.........",
    "...cac..........",
    "..cac...........",
    ".cbc............",
    ".cc.............",
]

DAGGER = [
    "................",
    "................",
    "................",
    "................",
    "..........ooo...",
    ".........ohlo...",
    "........ohlmo...",
    ".......ohlmo....",
    "......ohlmo.....",
    "...oo.olmo......",
    "...oeooeo.......",
    "....oeeo........",
    "...cbo.oo.......",
    "..cac...........",
    "..cc............",
    "................",
]

SCEPTER = [
    "................",
    "...........ooo..",
    "..........oRrRo.",
    "..........oRWro.",
    "..........ooRoo.",
    ".........oGgo...",
    "........oyYo....",
    ".......oyYo.....",
    "......oyYo......",
    ".....oyYo.......",
    "....oyYo........",
    "...oyYo.........",
    "..oGgo..........",
    "..oGo...........",
    "...o............",
    "................",
]


def bow(pull: int):
    """Curved heartwood bow; pull 0..3 draws the string back."""
    img = new()
    px = img.load()
    wood = [c("#3A5A2A"), c("#5A8A3A"), c("#8AC060"), c("#C8E8A0")]
    string = c("#E8E8E8")
    pts = []
    for t in range(0, 15):
        a = t / 14.0
        x = 2 + a * 11
        y = 13 - a * 11
        bulge = math.sin(a * math.pi) * 3.0
        pts.append((int(round(x + bulge * 0.7)), int(round(y + bulge * 0.7))))
    for i, (x, y) in enumerate(pts):
        px[x, y] = wood[1]
        if 0 <= x + 1 < 16:
            px[x + 1, y] = wood[2] if i % 3 else wood[3]
        if 0 <= y + 1 < 16:
            px[x, y + 1] = wood[0]
    # leaves at the tips
    for (x, y) in (pts[0], pts[-1]):
        for dx, dy in ((-1, 0), (0, 1), (1, -1)):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                px[x + dx, y + dy] = c("#4FA84A")
    # string from tip to tip, pulled toward the bottom-left
    (x0, y0), (x1, y1) = pts[0], pts[-1]
    mx, my = (x0 + x1) / 2 - pull * 1.2, (y0 + y1) / 2 + pull * 1.2
    for k in range(17):
        a = k / 16.0
        if a < 0.5:
            x = x0 + (mx - x0) * a * 2
            y = y0 + (my - y0) * a * 2
        else:
            x = mx + (x1 - mx) * (a - 0.5) * 2
            y = my + (y1 - my) * (a - 0.5) * 2
        xi, yi = int(round(x)), int(round(y))
        if 0 <= xi < 16 and 0 <= yi < 16 and px[xi, yi][3] == 0:
            px[xi, yi] = string
    if pull > 0:
        ax, ay = int(round(mx)), int(round(my))
        for k in range(6):
            x, y = ax + k, ay - k
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = c("#8A6234") if k < 5 else c("#C8C8C8")
    return img


def weave(w, h, regions, name):
    img = new(w, h)
    px = img.load()
    r = rng(name)
    for (x0, y0, x1, y1) in regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                v = ((x + y) % 4 == 0) * 2 + ((x - y) % 4 == 0)
                col = STAR[1 + min(3, v)] if v else STAR[1]
                if (x * 7 + y * 13) % 29 == 0:
                    col = STAR[4]
                px[x, y] = col
    return img


def paint():
    save(ore(stone_base(), [ZEN[0], ZEN[1], ZEN[2], ZEN[3], ZEN[4]], "zenithite_ore"), "block/zenithite_ore")
    save(ore(deepslate_base(), [ZEN[0], ZEN[1], ZEN[2], ZEN[3], ZEN[4]], "deepslate_zenithite_ore"), "block/deepslate_zenithite_ore")
    save(storage_block(ZEN, "zenithite_block"), "block/zenithite_block")
    save(shards(ZEN), "item/zenithite_shard")
    cloth = noise_fill(STAR[1:4], "starlace_cloth_bg", cell=2)
    cl = from_ascii(["................", "................", "...oooooooooo...", "..ohmhmhmhmhmo..", "..omhmhWhmhmdo..",
                     "..ohmhmhmhmhdo..", "..omhWhmhmhmdo..", "..ohmhmhmhWhdo..", "..omhmhmhmhmdo..", "..ohmhmWhmhmdo..",
                     "..omhmhmhmhmdo..", "..oddddddddddo..", "...oooooooooo...", "................", "................",
                     "................"],
                    {"o": c("#3A4660"), "h": STAR[3], "m": STAR[2], "d": STAR[1], "W": STAR[4]})
    save(cl, "item/starlace_cloth")
    pal = {"o": c("#3A4660"), "H": STAR[4], "h": STAR[3], "m": STAR[2], "d": STAR[1], "s": c("#FFFFFF")}
    save(from_ascii(HELMET, pal), "item/starlace_helmet")
    save(from_ascii(CHEST, pal), "item/starlace_chestplate")
    save(from_ascii(LEGS, pal), "item/starlace_leggings")
    save(from_ascii(BOOTS, pal), "item/starlace_boots")
    layer1 = [(0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32)]
    layer2 = [(16, 16, 40, 32), (0, 16, 16, 32)]
    save(weave(64, 32, layer1, "starlace_layer_1"), "models/armor/starlace_layer_1")
    save(weave(64, 32, layer2, "starlace_layer_2"), "models/armor/starlace_layer_2")
    for kind in ("sword", "pickaxe", "axe", "shovel", "hoe"):
        save(tool(kind, UMBRAL_TOOL), f"item/umbral_steel_{kind}")
    gpal = {"o": c("#12131C"), "h": c("#6A72A0"), "l": c("#444B73"), "m": c("#2E3350"), "R": c("#D6143F"), "r": c("#FF5C7A"),
            "a": STICK["a"], "b": STICK["b"], "c": STICK["c"]}
    save(from_ascii(GREATSWORD, gpal), "item/gravemaw_greatsword")
    spal = {"o": c("#0E3A44"), "h": c("#9FF0E0"), "l": c("#4FC7A8"), "a": c("#6A8A8A"), "b": c("#3A5A5A"), "c": c("#1A2E2E")}
    save(from_ascii(SICKLE, spal), "item/tidecaller_sickle")
    dpal = {"o": c("#0A2A32"), "h": c("#9EF6FF"), "l": c("#29DFEB"), "m": c("#0E5A66"), "e": c("#FFF7D6"),
            "a": STICK["a"], "b": STICK["b"], "c": STICK["c"]}
    save(from_ascii(DAGGER, dpal), "item/echo_dagger")
    ppal = {"o": c("#3A1A0A"), "R": c("#D6143F"), "r": c("#FF5C7A"), "W": c("#FFC2CC"), "G": c("#F2C94C"), "g": c("#8A5A10"),
            "y": c("#FFB000"), "Y": c("#FF7A00")}
    save(from_ascii(SCEPTER, ppal), "item/pyrrhic_scepter")
    save(bow(0), "item/heartwood_bow")
    for i in range(3):
        save(bow(i + 1), f"item/heartwood_bow_pulling_{i}")
    icon = new(18, 18)
    px = icon.load()
    for y in range(18):
        for x in range(18):
            if (x - 9) ** 2 + (y - 9) ** 2 < 49 and (x + y) % 3 != 0 and y > 8:
                px[x, y] = c("#4FA84A") if (x * y) % 5 else c("#2E6A2A")
    for k in range(6):
        px[9, 3 + k] = c("#8A6234")
        px[6 + k // 2, 9 - k // 2] = c("#4FA84A")
    save(icon, "mob_effect/rooted")
