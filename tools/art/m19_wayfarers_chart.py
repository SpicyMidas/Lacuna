"""Wayfarer's Chart (tools/content/m19_wayfarers_chart.py): the item and its map markers.

  * item/wayfarers_chart - a chart unrolled between two rolls: green land, a blue sea, a gold compass rose and a dotted
    violet path, the way between the worlds (the rift's colour).
  * map/decorations/chart_spawn, chart_home, chart_banished_from (8x8, vanilla map decoration style) - the world
    spawn (a gold star), the holder's bed or respawn anchor (a red bed) and, in the Forgotten Land, the spot their
    banishment took them from (a violet rift). Lacuna's textures/map/decorations are stitched into the vanilla
    map_decorations atlas, where the chart's painter looks them up.
"""
from texlib import c, from_ascii, save

CHART = [
    "................",
    "..oooooooooooo..",
    ".oeqhhhhhhhhqeo.",
    "..oooooooooooo..",
    "..opgggpppwwwo..",
    "..ogGgggpwwWwo..",
    "..opgggppwwwwo..",
    "..oppggvppwwwo..",
    "..opppvppppwwo..",
    "..oppvpppkkkpo..",
    "..opvpppkkrkko..",
    "..oplppppkkkpo..",
    "..oooooooooooo..",
    ".oeqhhhhhhhhqeo.",
    "..oooooooooooo..",
    "................",
]
CHART_PALETTE = {
    "o": c("#5A4630"), "e": c("#8A7352"), "q": c("#C9B48A"), "h": c("#E4D2A4"), "p": c("#EADBB4"),
    "g": c("#7FA542"), "G": c("#5A8A30"), "w": c("#5A7FE0"), "W": c("#8FB0F4"),
    "k": c("#B08A3C"), "r": c("#C8302A"), "v": c("#8A5CE0"), "l": c("#B898F0"),
}

SPAWN = [
    "...oo...",
    "..oyyo..",
    ".ooyyoo.",
    "oyywwyyo",
    "oyywwyyo",
    ".ooyyoo.",
    "..oyyo..",
    "...oo...",
]
HOME = [
    "........",
    "o.......",
    "owoooooo",
    "owwrrrro",
    "orrrrrro",
    "oooooooo",
    "o......o",
    "........",
]
BANISHED_FROM = [
    "..oooo..",
    ".ovvvvo.",
    "ovVllVvo",
    "ovlwwlvo",
    "ovlwwlvo",
    "ovVllVvo",
    ".ovvvvo.",
    "..oooo..",
]
MARKER_PALETTE = {"o": c("#2A2016"), "y": c("#F2C94C"), "w": c("#FFFFFF"), "r": c("#C8302A"),
                  "v": c("#6A34C8"), "V": c("#B898F0"), "l": c("#E0D0FF")}


def paint():
    save(from_ascii(CHART, CHART_PALETTE), "item/wayfarers_chart")
    save(from_ascii(SPAWN, MARKER_PALETTE), "map/decorations/chart_spawn")
    save(from_ascii(HOME, MARKER_PALETTE), "map/decorations/chart_home")
    save(from_ascii(BANISHED_FROM, MARKER_PALETTE), "map/decorations/chart_banished_from")
