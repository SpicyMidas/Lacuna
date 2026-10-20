"""Comically Large Bag: a slumped, open burlap sack (empty) and the same sack bulging and tied shut (full).

Light comes from the top left like every vanilla item; a faded red patch with twine stitches marks it as the same
well-travelled bag in both states.
"""
from texlib import c, from_ascii, save

PALETTE = {
    "o": c("#3A2412"),  # outline
    "d": c("#6B4524"),  # sack shadow
    "m": c("#8F6236"),  # sack
    "l": c("#B17D47"),  # sack, lit
    "h": c("#D2A26A"),  # sack highlight
    "i": c("#1F1209"),  # inside the open mouth
    "t": c("#EADBB0"),  # twine
    "T": c("#B39A68"),  # twine shadow
    "p": c("#A04A34"),  # patch
    "P": c("#72301F"),  # patch shadow
    "s": c("#EADBB0"),  # patch stitches
}

EMPTY = [
    "................",
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "....odmmmmdo....",
    "...omiiiiiido...",
    "...ohhlllmmdot..",
    "..omlhllmmmmdoT.",
    "..omllllmdmmdo.t",
    ".omlhllmdmmmmdoT",
    ".omllldmmmPpPdo.",
    "omllldmmmmpspddo",
    "omlmmmmmmdPpPddo",
    ".oddmmdddddddoo.",
    "..oooooooooooo..",
]

FULL = [
    "................",
    ".....o.oo.o.....",
    "....omomlomo....",
    ".....omlhmo.....",
    "....tTttttTt....",
    "...t.omhlmo.T...",
    "...TomlhhlmdoT..",
    "..omlhhllllmdo..",
    ".omlhhlllllmmdo.",
    ".omlhllllllmmdo.",
    "omllllllllmmmddo",
    "omlllllllmmPpPdo",
    "omllllllmmmpspdo",
    ".odmmmmmmmmPpPo.",
    "..oddddddddddo..",
    "...oooooooooo...",
]


def paint():
    for name, rows in (("comically_large_bag", EMPTY), ("comically_large_bag_full", FULL)):
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
        save(from_ascii(rows, PALETTE), f"item/{name}")
