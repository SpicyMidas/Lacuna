"""Chrono Hourglass: a two-block-tall hourglass that speeds up local time while its chronal sand runs.

Block models (one per half): dark wood caps, four brass posts and two glass bulbs built from stacked square layers.
The sand is drawn by client/chrono/ChronoHourglassRenderer from the same layer table (keep BULB_* in sync with its
BOTTOM/TOP arrays). Glass faces only cover the outside of the bulbs: the ledges between layers are thin rings, so no
glass plane ever cuts through the sand.
"""
from gen_resources import mod

BID = "chrono_hourglass"
FACES = ("north", "south", "east", "west")

# glass layers (radius, y0, y1) in pixels of each half's own model
BULB_LOWER = [(3, 2, 3), (4, 3, 11), (3, 11, 13), (2, 13, 14), (1, 14, 16)]
BULB_UPPER = [(1, 0, 2), (2, 2, 3), (3, 3, 5), (4, 5, 13), (3, 13, 14)]
POSTS = [(2, 2), (12, 2), (2, 12), (12, 12)]


def _el(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def _sides(r, y0, y1):
    """The four outer glass walls of one bulb layer (natural UVs)."""
    return _el([8 - r, y0, 8 - r], [8 + r, y1, 8 + r], {d: {"texture": "#glass"} for d in FACES})


def _ring(y, r_in, r_out, facing):
    """A horizontal glass ledge between two layers: four zero-thickness strips around the narrower layer."""
    a, b, c, d = 8 - r_out, 8 - r_in, 8 + r_in, 8 + r_out
    strips = [([a, a], [d, b]), ([a, c], [d, d]), ([a, b], [b, c]), ([c, b], [d, c])]
    return [_el([x0, y, z0], [x1, y, z1], {facing: {"texture": "#glass"}}) for (x0, z0), (x1, z1) in strips]


def _bulb(layers, open_bottom, open_top):
    out = []
    for r, y0, y1 in layers:
        out.append(_sides(r, y0, y1))
    for (r0, _a, top), (r1, _b, _c) in zip(layers, layers[1:]):
        if r1 < r0:
            out += _ring(top, r1, r0, "up")
        elif r1 > r0:
            out += _ring(top, r0, r1, "down")
    if not open_bottom:
        r, y0, _ = layers[0]
        out.append(_el([8 - r, y0, 8 - r], [8 + r, y0, 8 + r], {"down": {"texture": "#glass"}}))
    if not open_top:
        r, _, y1 = layers[-1]
        out.append(_el([8 - r, y1, 8 - r], [8 + r, y1, 8 + r], {"up": {"texture": "#glass"}}))
    return out


def _plate(y0, y1, cull, flip):
    side_uv = [1, 16, 15, 14] if flip else [1, 14, 15, 16]
    faces = {d: {"texture": "#frame", "uv": side_uv} for d in FACES}
    faces["up"] = {"texture": "#cap"}
    faces["down"] = {"texture": "#cap"}
    faces[cull]["cullface"] = cull
    return _el([1, y0, 1], [15, y1, 15], faces)


def _posts(y0, y1, u):
    return [_el([x, y0, z], [x + 2, y1, z + 2], {d: {"texture": "#frame", "uv": [u, 0, u + 2, 14]} for d in FACES})
            for x, z in POSTS]


def _model(elements):
    return {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
            "textures": {"particle": mod("block/chrono_hourglass_cap"), "cap": mod("block/chrono_hourglass_cap"),
                         "frame": mod("block/chrono_hourglass_frame"), "glass": mod("block/chrono_hourglass_glass")},
            "elements": elements}


def build(g):
    # glass walls first in each list is not needed for sorting (translucent quads are sorted per face), frame first
    lower = [_plate(0, 2, "down", False)] + _posts(2, 16, 0) + _bulb(BULB_LOWER, open_bottom=True, open_top=True)
    upper = [_plate(14, 16, "up", True)] + _posts(0, 14, 2) + _bulb(BULB_UPPER, open_bottom=True, open_top=True)
    g.block_lang(BID, "Chrono Hourglass", "Clessidra cronale")
    g.asset(f"models/block/{BID}_lower", _model(lower))
    g.asset(f"models/block/{BID}_upper", _model(upper))
    g.asset(f"blockstates/{BID}", {"variants": {
        "half=lower": {"model": mod(f"block/{BID}_lower")},
        "half=upper": {"model": mod(f"block/{BID}_upper")}}})
    g.props(BID, half=["lower", "upper"])
    g.asset(f"models/item/{BID}", {"parent": "minecraft:item/generated", "textures": {"layer0": mod(f"item/{BID}")}})
    g.loot(BID, "door")  # one hourglass, from the lower half only (the upper half breaks along with it)
    g.tag("block", "minecraft:mineable/pickaxe", mod(BID))

    # block entities an hourglass never speeds up (data-driven: packs may add or remove entries)
    g.tag("block_entity_type", "lacuna:chrono_hourglass_immune", *[{"id": i, "required": False} for i in (
        "lacuna:chrono_hourglass", "minecraft:mob_spawner", "minecraft:trial_spawner", "minecraft:vault",
        "minecraft:end_gateway", "lacuna:forgotten_portal", "lacuna:arrival_anchor", "lacuna:null_lantern",
        "lacuna:updraft_spring", "minecraft:piston", "minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor",
        "minecraft:sculk_shrieker")])
    # blocks whose random and scheduled ticks an hourglass never hurries: portals (piglins spawn on random ticks),
    # spawners, hourglasses and redstone parts that are not signal sources (signal sources are skipped in code), so
    # circuits keep their timing
    g.tag("block", "lacuna:chrono_hourglass_immune", *[{"id": i, "required": False} for i in (
        "lacuna:chrono_hourglass", "lacuna:forgotten_portal", "lacuna:arrival_anchor", "lacuna:null_lantern",
        "lacuna:updraft_spring", "minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway",
        "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault", "minecraft:redstone_lamp",
        "minecraft:dispenser", "minecraft:dropper", "minecraft:crafter", "minecraft:command_block",
        "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:tripwire",
        "minecraft:big_dripleaf", "minecraft:big_dripleaf_stem", "minecraft:sculk_shrieker", "minecraft:piston",
        "minecraft:sticky_piston", "minecraft:moving_piston", "minecraft:piston_head")])

    # still craftable (it runs only on chronal shards), but its frame now holds two of them: one per bulb
    g.shaped(BID, ["GCG", "#S#", "GCG"],
             {"G": "minecraft:gold_ingot", "#": "minecraft:glass", "C": "lacuna:chronal_shard", "S": "minecraft:sand"},
             mod(BID), category="misc", unlock="lacuna:chronal_shard")

    g.asset("particles/chrono_tick", {"textures": [f"lacuna:chrono_tick_{i}" for i in range(4)]})

    for key, en, it in [
        (f"block.lacuna.{BID}.use", "Use a Chronal Shard: +%s s of sand, up to %s min (sneak: pour in the whole stack)",
         "Usa una scheggia cronale: +%s s di sabbia, fino a %s min (accovacciato: versa tutta la pila)"),
        (f"block.lacuna.{BID}.use2", "While the sand runs, time within %2$s blocks flows %1$sx faster: furnaces, crops, grass, "
         "saplings, ice melting, copper aging, young animals, eggs",
         "Mentre la sabbia scorre, il tempo entro %2$s blocchi scorre %1$sx più veloce: fornaci, colture, erba, "
         "arbusti, ghiaccio che si scioglie, rame che invecchia, cuccioli, uova"),
        (f"block.lacuna.{BID}.use4", "Water and lava flow %1$sx faster; players heal %2$sx faster and shake off harmful "
         "effects sooner",
         "Acqua e lava scorrono %1$sx più veloci; i giocatori guariscono %2$sx più in fretta e smaltiscono prima gli "
         "effetti negativi"),
        (f"block.lacuna.{BID}.use5", "Redstone timing, spawners, portals and other hourglasses are never affected",
         "Tempi della pietrarossa, generatori, portali e altre clessidre non vengono mai alterati"),
        (f"block.lacuna.{BID}.use3", "A redstone signal holds the sand; comparators read the time left",
         "Un segnale di pietrarossa trattiene la sabbia; i comparatori leggono il tempo rimasto"),
        (f"item.lacuna.{BID}.tip", "Every grain is a borrowed second.", "Ogni granello è un secondo preso in prestito."),
        ("lacuna.hourglass.full", "The hourglass cannot hold any more sand.", "La clessidra non può contenere altra sabbia."),
        ("lacuna.hourglass.empty", "The sand is spent. Feed it a Chronal Shard.", "La sabbia è finita. Nutrila con una scheggia cronale."),
        ("lacuna.hourglass.running", "Sand left: %s", "Sabbia rimasta: %s"),
        ("lacuna.hourglass.paused", "Held by redstone: %s left", "Trattenuta dalla pietrarossa: %s rimasti"),
    ]:
        g.name(key, en, it)
