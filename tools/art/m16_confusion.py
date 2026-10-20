"""Totem of Confusion placeholder: an amethyst idol with teal eyes and gold bands, two faint mirror copies of its head
peeking out on either side. The hand-drawn texture (tools/art/hand/item/totem_of_confusion.png) replaces it verbatim
when present.

Amethyst follows the vanilla amethyst ramp; the outline is a deep violet like vanilla purple items.
"""
from texlib import c, from_ascii, new, save

IDOL = [
    "................",
    ".....OOOOOO.....",
    "....OhhllllO....",
    "....OlellemO....",
    "....OmEmmEdO....",
    "....OmmOOmdO....",
    "....OgggggGO....",
    "....OlhllmdO....",
    ".OOOOlmllmdOOOO.",
    ".OhlmOmllmOmmdO.",
    "..OOOOlmmdOOOO..",
    "....OgggggGO....",
    "....OmllmmdO....",
    ".....OmmmdO.....",
    ".....OdmmdO.....",
    "......OOOO......",
]

# a faint copy of the head, looking outward (mirrored for the right side)
GHOST = [
    ".oo.",
    "oaao",
    "obeo",
    "obbo",
    ".oo.",
]

IDOL_PAL = {
    "O": c("#2A1240"), "d": c("#5B2C83"), "m": c("#8A4FBF"), "l": c("#B98AE6"), "h": c("#E6D2FF"),
    "e": c("#3FF0D2"), "E": c("#129C8A"), "g": c("#E8B84A"), "G": c("#9A6420"),
}
GHOST_PAL = {"o": c("#4B3A70", 200), "a": c("#D9CCF5", 170), "b": c("#9C88CC", 160), "e": c("#8FF5E4", 200)}


def totem_of_confusion():
    img = new(16, 16)
    left = from_ascii(GHOST, GHOST_PAL)
    img.alpha_composite(left, (0, 2))
    img.alpha_composite(left.transpose(0), (12, 2))  # FLIP_LEFT_RIGHT
    img.alpha_composite(from_ascii(IDOL, IDOL_PAL))
    save(img, "item/totem_of_confusion")


def paint():
    totem_of_confusion()
