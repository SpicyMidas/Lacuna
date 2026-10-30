"""Structure maps, the Wayfarer's Chart rolls and the Forgotten Land obelisk inscription (tools/content/m21_structure_maps.py).

  * map/decorations/sunken_bell_tower, obelisk, ancient_city (8x8, vanilla map decoration style: black outline, three
    tones) - the targets of the exploration maps found in chests or bought from the Last Merchant (exploration/
    StructureMaps registers the decoration types; Lacuna's textures/map/decorations are stitched into the vanilla
    map_decorations atlas).
  * map/wayfarers_chart_roll (64x6) - the held Wayfarer's Chart's two rolls (client/chart/ChartRolls), flat as on the
    item icon and in its colours: a dark outline above and below, the light parchment band, its shaded ends and the
    wooden knobs past the sheet's sides.
  * block/forgotten_obelisk_inscription - the Obelisk Inscription's glyphs cut into the Forgotten Land's grey ruin stone
    (the broken obelisks there are ruin stone, not polished blackstone).
"""
from texlib import c, from_ascii, lerp, new, noise_fill, rng, save
from art.m02_forgotten_land import GROOVE, LIGHT, RUIN
from art.m10_biomes_structures import GLYPHS

OUTLINE = c("#000000")

BELL_TOWER = [
    "...aa...",
    "..abca..",
    ".abccda.",
    ".abccda.",
    ".abbcda.",
    "abbbbdda",
    "aaaeeaaa",
    "...ee...",
]
BELL_PALETTE = {"a": OUTLINE, "b": c("#5FB3A1"), "c": c("#A6E0CF"), "d": c("#2F6E62"), "e": c("#D9AE4A")}

OBELISK = [
    "...aa...",
    "..abca..",
    "..abda..",
    "..aeda..",
    "..abda..",
    "..aeda..",
    ".abbdda.",
    "aaaaaaaa",
]
OBELISK_PALETTE = {"a": OUTLINE, "b": c("#4A4458"), "c": c("#7A7290"), "d": c("#211D2A"), "e": c("#E7C46A")}

ANCIENT_CITY = [
    ".aaaaaa.",
    "abbbbbba",
    "abdeedba",
    "abeffeba",
    "abeffeba",
    "abdeedba",
    "abbaabba",
    "aaa..aaa",
]
ANCIENT_CITY_PALETTE = {"a": OUTLINE, "b": c("#6A6A74"), "d": c("#34343C"), "e": c("#0E6670"), "f": c("#52EEF5")}

# the item icon's roll colours (art/m19_wayfarers_chart): outline, knob, shaded end, parchment band
ROLL_OUTLINE = c("#5A4630")
ROLL_KNOB = c("#8A7352")
ROLL_END = c("#C9B48A")
ROLL_BAND = c("#E4D2A4")
ROLL_LIGHT = c("#EEDFB6")
ROLL_SHADE = c("#D6C294")


def chart_roll():
    """The icon's roll at the held chart's scale: 64 texels along the sheet (knobs included), 6 across."""
    w, h = 64, 6
    img = new(w, h)
    px = img.load()
    for x in range(3, w - 3):
        px[x, 0] = ROLL_OUTLINE
        px[x, h - 1] = ROLL_OUTLINE
    for y in range(1, h - 1):
        band = ROLL_LIGHT if y == 1 else ROLL_SHADE if y == h - 2 else ROLL_BAND
        for x in range(w):
            end = min(x, w - 1 - x)
            px[x, y] = ROLL_OUTLINE if end == 0 else ROLL_KNOB if end < 3 else ROLL_END if end < 5 else band
    return img


def forgotten_obelisk_inscription():
    """The black inscription's glyphs, gold-inlaid and cut into ruin stone: a groove above and left of every stroke,
    a light rim below and right (light from the top left, like the chiseled ruin glyph)."""
    img = noise_fill(RUIN[1:], "forgotten_obelisk_inscription", cell=4, speckle=0.12)
    px = img.load()
    glyph = {(x, y) for y, row in enumerate(GLYPHS) for x, ch in enumerate(row) if ch == "g"}
    for (x, y) in glyph:
        for dx, dy, col, t in ((-1, -1, GROOVE, 0.8), (0, -1, GROOVE, 0.55), (1, 1, LIGHT, 0.5)):
            p = (x + dx, y + dy)
            if 0 <= p[0] < 16 and 0 <= p[1] < 16 and p not in glyph:
                px[p] = lerp(px[p], col, t)
    for (x, y) in glyph:
        px[x, y] = c("#9C7A3A") if (x + y) % 3 else c("#D4B261")
    return img


def paint():
    save(from_ascii(BELL_TOWER, BELL_PALETTE), "map/decorations/sunken_bell_tower")
    save(from_ascii(OBELISK, OBELISK_PALETTE), "map/decorations/obelisk")
    save(from_ascii(ANCIENT_CITY, ANCIENT_CITY_PALETTE), "map/decorations/ancient_city")
    save(chart_roll(), "map/wayfarers_chart_roll")
    save(forgotten_obelisk_inscription(), "block/forgotten_obelisk_inscription")
