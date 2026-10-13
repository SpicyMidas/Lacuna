"""Revision 2 effects: particle sprite lists (assets/lacuna/particles/*.json)."""

PARTICLES = {
    "light_mote": ["light_mote"],
    "solar_glint": ["solar_glint_0", "solar_glint_1"],
    "crimson_spark": ["crimson_spark_0", "crimson_spark_1"],
    "shadow_mote": ["shadow_mote_0", "shadow_mote_1", "shadow_mote_2", "shadow_mote_3"],
    "umbral_slash": ["umbral_slash_0", "umbral_slash_1", "umbral_slash_2"],
    "umbral_shard": ["umbral_shard_0", "umbral_shard_1", "umbral_shard_2"],
    "mist": ["mist_0", "mist_1", "mist_2"],
    "umbral_wisp": ["light_mote"],
}


def build(g):
    for name, sprites in PARTICLES.items():
        g.asset(f"particles/{name}", {"textures": [f"lacuna:{s}" for s in sprites]})
