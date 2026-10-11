"""M3 textures: Divine Magnifying Glass (gold ring, divine glass lens, brown handle)."""
from texlib import c, from_ascii, save

GLASS = [
    "................",
    "......ooooo.....",
    ".....oGGGGgo....",
    "....oGlLLLwGo...",
    "...oGlLWWLLlGo..",
    "...oGLWLLLLlGo..",
    "...oGLLLLLLlGo..",
    "...oGlLLLLlpgo..",
    "....oGlllllGo...",
    ".....ogGGGgo....",
    "....odgooooo....",
    "...ohdo.........",
    "..ohdo..........",
    ".ohdo...........",
    ".odo............",
    "..o.............",
]


def paint():
    pal = {
        "o": c("#5A3A10"), "G": c("#D49A22"), "g": c("#8A5A10"),
        "L": c("#FFF6C8", 200), "l": c("#FFE9A0", 210), "W": c("#FFFFFF", 230), "w": c("#B8E8FF", 220),
        "p": c("#FFC8F0", 220), "h": c("#8A5A2C"), "d": c("#5A3A1C"),
    }
    save(from_ascii(GLASS, pal), "item/divine_magnifying_glass")
