"""Wayfarer's Chart: a live map of the Overworld centred on its holder (chart/, client/chart; art:
tools/art/m19_wayfarers_chart.py).

Held, it shows the Overworld around you and scrolls as you walk; from the Nether or the Forgotten Land it shows the
Overworld around your translated position (the Forgotten Land's 1:12, the Nether's 1:8); in the End it rests on your
way home. Sneak + use changes the scale (1 to 16 blocks per pixel).

Recipe (kept here so it is easy to rebalance): an empty map and a compass between two rubies, under a Divine Glass
Shard, the lens that sees across. Mid-game: rubies are mined below Y 16 and shards turn up in the Overworld's
Obelisks and Sunken Bell Towers or on the Forgotten Land's surface.
"""

LANG = [
    ("item.lacuna.wayfarers_chart.lore", "Wherever you wander, it remembers the Overworld.",
     "Ovunque tu vada, ricorda il Mondo normale."),
    ("item.lacuna.wayfarers_chart.use",
     "Hold it: a live map of the Overworld around you. From the Nether or the Forgotten Land it shows the Overworld "
     "under your translated position.",
     "Tienila in mano: una mappa viva del Mondo normale intorno a te. Dal Nether o dalla Terra Dimenticata mostra il "
     "Mondo normale sotto la tua posizione tradotta."),
    ("item.lacuna.wayfarers_chart.zoom", "Sneak + use: change the scale (now 1 pixel = %s blocks).",
     "Accovacciato + usa: cambia la scala (ora 1 pixel = %s blocchi)."),
    ("item.lacuna.wayfarers_chart.tip",
     "Land seen lately is inked in full colour; farther land is sketched faintly, from what the world would grow there.",
     "La terra vista di recente è tracciata a pieni colori; quella più lontana è abbozzata a tinte tenui, da ciò che "
     "il mondo vi farebbe crescere."),
    ("lacuna.chart.scale", "Chart scale: 1 pixel = %s blocks", "Scala della carta: 1 pixel = %s blocchi"),
    ("lacuna.chart.waiting", "The ink is still settling…", "L'inchiostro si sta ancora posando…"),
    ("lacuna.chart.position", "X %s · Z %s", "X %s · Z %s"),
    ("lacuna.chart.overworld", "Overworld X %s · Z %s", "Mondo normale X %s · Z %s"),
    ("lacuna.chart.here", "Here X %s · Z %s · ×%s", "Qui X %s · Z %s · ×%s"),
    ("lacuna.chart.home", "Way home X %s · Z %s", "Via di casa X %s · Z %s"),
    ("lacuna.chart.no_bearing", "No bearing on the Overworld from here", "Da qui il Mondo normale non ha direzione"),
]


def build(g):
    g.item("wayfarers_chart", "Wayfarer's Chart", "Carta del viandante")
    for key, en, it in LANG:
        g.name(key, en, it)
    g.shaped("wayfarers_chart", [" D ", "RMR", " C "],
             {"D": "lacuna:divine_glass_shard", "R": "lacuna:ruby", "M": "minecraft:map", "C": "minecraft:compass"},
             "lacuna:wayfarers_chart", category="equipment", unlock="minecraft:compass")
