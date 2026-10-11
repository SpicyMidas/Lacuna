"""M5 textures: Fractured Pearl (teal with a bright crack), Arrival (teal right half) and Departure (violet left half)."""
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
    anchor = noise_fill(ramp("#1E3A44", "#2E6A78", "#4FB0B0", "#7FE8E0", "#E8FFFC"), "arrival_anchor", cell=2, speckle=0.2,
                        contrast=1.4)
    px = anchor.load()
    for i in range(16):
        px[i, i] = c("#E8FFFC", 255)
    save(anchor, "block/arrival_anchor")
