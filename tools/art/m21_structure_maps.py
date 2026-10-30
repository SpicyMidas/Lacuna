"""Structure maps, the Wayfarer's Chart rolls and the Forgotten Land obelisk inscription (tools/content/m21_structure_maps.py).

  * map/decorations/sunken_bell_tower, obelisk, ancient_city (8x8, vanilla map decoration style: black outline, three
    tones) - the targets of the exploration maps found in chests or bought from the Last Merchant (exploration/
    StructureMaps registers the decoration types; Lacuna's textures/map/decorations are stitched into the vanilla
    map_decorations atlas).
  * map/wayfarers_chart_roll (16x16) - the held Wayfarer's Chart's two rolls (client/chart/ChartRolls): rows 0-7 the
    rolled parchment (the vanilla map background's colours, with the outer wrap's edge as a darker line), rows 8-15
    left the end of the roll seen from the side (wound layers around the rod), right the wood of the end knobs.
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

# the vanilla map background's parchment and border colours
PAPER = c("#D6BE96")
PAPER_DARK = c("#99876C")
PAPER_LINE = c("#B9A27E")
PAPER_LIGHT = c("#E2CDA8")
WOOD = [c("#4A3826"), c("#5A4630"), c("#6E5638"), c("#8A7352")]


def chart_roll():
    img = new(16, 16)
    px = img.load()
    r = rng("wayfarers_chart_roll")
    # rows 0-7: the rolled parchment; v runs around the roll, u along it (one texel per map pixel)
    for y in range(8):
        for x in range(16):
            col = PAPER
            roll = r.random()
            if roll < 0.12:
                col = PAPER_LIGHT
            elif roll < 0.22:
                col = lerp(PAPER, PAPER_LINE, 0.5)
            px[x, y] = col
    for x in range(16):
        px[x, 2] = PAPER_LINE          # the outer wrap's edge, running along the roll
        px[x, 3] = lerp(PAPER, PAPER_LIGHT, 0.6)
    # rows 8-15, cols 0-7: the roll's end, wound layers around the wooden rod
    for y in range(8):
        for x in range(8):
            d = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
            if d > 4.0:
                col = PAPER_DARK
            elif d < 1.3:
                col = WOOD[1]
            elif d < 1.9:
                col = WOOD[0]
            else:
                col = PAPER if int(d * 2) % 2 == 0 else PAPER_LINE
            px[x, 8 + y] = col
    # rows 8-15, cols 8-15: the knobs' wood, the grain running along the axis (u)
    for y in range(8):
        base = WOOD[1 + (y * 5 + 2) % 3]
        for x in range(8):
            col = base
            if r.random() < 0.18:
                col = WOOD[0]
            px[8 + x, 8 + y] = col
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
