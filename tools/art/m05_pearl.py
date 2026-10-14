"""M5 textures: Arrival Anchor (the placed shard). The pearl and both shards are hand drawn (tools/art/hand)."""
from texlib import c, from_ascii, noise_fill, ramp, save

PEARL = [
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "....oLLlmmmo....",
    "...oLWLlmmdmo...",
    "...oLLlXmmmdo...",
    "...olllXXmmdo...",
    "...ommXXmmddo...",
    "...ommmXmmddo...",
    "...ommXmmdddo...",
    "....omXmddddo...",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]

RIGHT_HALF = [
    "................",
    "................",
    "................",
    "........ooo.....",
    "........XLmo....",
    ".........XLmo...",
    "........XLLmmo..",
    "........XlmmmdO.",
    ".........Xmmmdo.",
    "........Xmmmddo.",
    "........Xmmdddo.",
    ".........Xdddo..",
    "........Xooo....",
    "................",
    "................",
    "................",
]

LEFT_HALF = [row[::-1].replace("O", "o") for row in RIGHT_HALF]


def paint():
    teal = {"o": c("#12262C"), "O": c("#12262C"), "L": c("#7FE8E0"), "W": c("#E8FFFC"), "l": c("#4FB0B0"),
            "m": c("#2E6A78"), "d": c("#1E3A44"), "X": c("#E8FFFC")}
    violet = {"o": c("#1E1230"), "O": c("#1E1230"), "L": c("#B99BFF"), "W": c("#F0E8FF"), "l": c("#8A6AD8"),
              "m": c("#4A2E78"), "d": c("#2C1A4A"), "X": c("#F0E8FF")}
    save(from_ascii(PEARL, teal), "item/fractured_pearl")
    save(from_ascii(RIGHT_HALF, teal), "item/arrival_shard")
    left = from_ascii(LEFT_HALF, violet)
    save(left, "item/departure_shard")
    save(anchor_texture(), "block/arrival_anchor")


# palette of the hand-drawn pearl: dusky violet shell, gold seam, white-gold glint
SHELL = ["#14121F", "#2B2239", "#43314F", "#5E4258"]
GOLD = ["#9A6A4A", "#C58E3E", "#F2C04E", "#FFF4C2"]
SEAM = [(2, 13), (3, 12), (4, 12), (5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (10, 5), (11, 4), (12, 3), (13, 2)]


def anchor_texture():
    """Translucent violet shell with a dark rim and a gold seam; the centre 4x4 (the core cube) burns gold."""
    img = noise_fill(ramp(*SHELL), "arrival_anchor", cell=2, speckle=0.25, contrast=1.3)
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, _ = px[x, y]
            px[x, y] = (r, g, b, 205)
            if x in (0, 15) or y in (0, 15):
                px[x, y] = c("#07070D", 255)
            elif x in (1, 14) or y in (1, 14):
                px[x, y] = c(SHELL[1], 235)
    for i, (x, y) in enumerate(SEAM):
        px[x, y] = c(GOLD[2] if i % 3 else GOLD[3], 255)
        if 0 < x + 1 < 15:
            px[x + 1, y] = c(GOLD[1], 245)
    for y in range(6, 10):
        for x in range(6, 10):
            edge = x in (6, 9) or y in (6, 9)
            px[x, y] = c(GOLD[1] if edge else GOLD[2], 255)
    px[7, 7] = c(GOLD[3], 255)
    px[8, 7] = c(GOLD[3], 255)
    px[7, 8] = c(GOLD[3], 255)
    return img
