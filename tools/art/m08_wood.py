"""M8 textures: four wood families + strands, roots, ember, crystal willow shard."""
from texlib import c, from_ascii, ramp, save, with_alpha
from art.common import shards
from art.wood import bark, door_half, door_item, leaves, log_top, planks, rune_bark, sapling, trapdoor

FAMILIES = {
    "cindervine": dict(bark=ramp("#17120F", "#2A1D16", "#3A2418", "#4A2A18"), inner=ramp("#6A3A20", "#8A4A28", "#B0602A"),
                       stripped=ramp("#3A2014", "#5A3018", "#7A4020", "#9A5028"), leaves=ramp("#5A2A10", "#8A3A10", "#C05A18", "#FF8A2A"),
                       accent=[c("#FF6A1A"), c("#FFB04A")], window=c("#FF8A2A", 200), alpha=255, leaf_alpha=255),
    "crystal_willow": dict(bark=ramp("#8FC8B4", "#BFE8D8", "#E6FFF4", "#FFFFFF"), inner=ramp("#BFE8D8", "#E6FFF4", "#FFFFFF"),
                           stripped=ramp("#BFE8D8", "#D8F4EA", "#E6FFF4", "#FFFFFF"), leaves=ramp("#7FC0AA", "#9FD9C4", "#C0F0DE", "#D8FFF0"),
                           accent=None, window=c("#E6FFF4", 160), alpha=210, leaf_alpha=200),
    "tidebloom": dict(bark=ramp("#3A1A20", "#5A2A32", "#6E323C", "#8A3A48"), inner=ramp("#8A4A50", "#A85A60", "#C87070"),
                      stripped=ramp("#7A3A44", "#9A4A54", "#B05A62", "#C86A70"), leaves=ramp("#1E6A5A", "#2E8A78", "#3EA890", "#4FC7A8"),
                      accent=[c("#4FC7A8")], window=c("#4FC7A8", 200), alpha=255, leaf_alpha=255),
    "echo_birch": dict(bark=ramp("#BFB8A0", "#CFC8B0", "#DDD6C2", "#E8E2D0"), inner=ramp("#D8D0B8", "#E8E2D0", "#F4F0E2"),
                       stripped=ramp("#D0C8B0", "#E0DAC6", "#ECE6D6", "#F6F2E6"), leaves=ramp("#9FB8C8", "#BFD8E8", "#D4E8F4", "#E8F4FF"),
                       accent=None, window=c("#6FA8E8", 200), alpha=255, leaf_alpha=255),
}


def paint():
    for f, p in FAMILIES.items():
        a = p["alpha"]
        if f == "echo_birch":
            side = rune_bark(p["bark"], c("#6FA8E8"), f"{f}_log")
        else:
            side = bark(p["bark"], f"{f}_log", p["accent"], 0.4 if p["accent"] else 0.0, alpha=a)
        save(side, f"block/{f}_log")
        save(log_top(p["bark"], p["inner"], f"{f}_log_top", alpha=a), f"block/{f}_log_top")
        save(bark(p["stripped"], f"stripped_{f}_log", alpha=a), f"block/stripped_{f}_log")
        save(log_top(p["stripped"], p["inner"], f"stripped_{f}_log_top", alpha=a), f"block/stripped_{f}_log_top")
        pl = [p["stripped"][0], p["stripped"][1], p["stripped"][2], p["stripped"][3]]
        save(planks(pl, f"{f}_planks", alpha=a), f"block/{f}_planks")
        save(leaves(p["leaves"], f"{f}_leaves", alpha=p["leaf_alpha"]), f"block/{f}_leaves")
        save(sapling([p["leaves"][1], p["leaves"][3]], p["bark"][1] if f != "crystal_willow" else c("#8FC8B4")), f"block/{f}_sapling")
        save(door_half(pl, f"{f}_door_top", True, p["window"], alpha=a), f"block/{f}_door_top")
        save(door_half(pl, f"{f}_door_bottom", False, alpha=a), f"block/{f}_door_bottom")
        save(door_item(pl, p["window"]), f"item/{f}_door")
        save(trapdoor(pl, f"{f}_trapdoor", alpha=a), f"block/{f}_trapdoor")
    strands = from_ascii(["........s.......", ".......sS.......", "........S.s.....", "......s.S.S.....", "......S.s.S.....",
                          "......S...s.....", ".....s.S..S.....", ".....S.S..s.....", ".....S.s..S.....", ".......S..S.....",
                          ".......s..s.....", ".......S........", ".......S........", ".......w........", "................",
                          "................"],
                         {"s": with_alpha(c("#9FD9C4"), 210), "S": with_alpha(c("#D8FFF0"), 220), "w": c("#FFFFFF")})
    save(strands, "block/crystal_willow_strands")
    roots = bark(ramp("#3A1A20", "#5A2A32", "#8A3A48"), "tidebloom_roots")
    px = roots.load()
    for y in range(16):
        for x in range(16):
            if (x + y * 3) % 5 in (1, 2) and (x * y) % 3:
                px[x, y] = (0, 0, 0, 0)
    save(roots, "block/tidebloom_roots")
    save(from_ascii(["................", "................", "......oo........", ".....oYyo.......", "....oYWYyo......",
                     "....oyYYyo......", "...oyYyRyo......", "...oyyRRyo......", "...orRRrro......", "....orrro.......",
                     ".....ooo........", "................", "................", "................", "................",
                     "................"],
                    {"o": c("#3A1408"), "Y": c("#FFB04A"), "y": c("#FF6A1A"), "W": c("#FFF2A0"), "R": c("#C03A0A"),
                     "r": c("#7A2008")}), "item/ember")
    save(shards([c("#5A9A86"), c("#9FD9C4"), c("#C0F0DE"), c("#E6FFF4"), c("#FFFFFF")]), "item/crystal_willow_shard")
