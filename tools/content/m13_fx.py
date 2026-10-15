"""Revision 2 effects: particle sprite lists (assets/lacuna/particles/*.json)."""

PARTICLES = {
    "light_mote": ["light_mote"],
    "solar_glint": ["solar_glint_0", "solar_glint_1"],
    "crimson_spark": ["crimson_spark_0", "crimson_spark_1"],
    "shadow_mote": ["shadow_mote_0", "shadow_mote_1", "shadow_mote_2", "shadow_mote_3"],
    "umbral_slash": ["umbral_slash_0", "umbral_slash_1", "umbral_slash_2"],
    "eclipse_slash": ["eclipse_slash_0", "eclipse_slash_1", "eclipse_slash_2"],
    "umbral_shard": ["umbral_shard_0", "umbral_shard_1", "umbral_shard_2"],
    "mist": ["mist_0", "mist_1", "mist_2"],
    "umbral_wisp": ["light_mote"],
    # Ember Triangle pulverization dust (tinted and cooled by client/gear/EmberDust)
    "ember_dust": [f"ember_dust_{i}" for i in range(6)],
    "ember_speck": ["ember_speck_0", "ember_speck_1", "ember_speck_2"],
}


# custom sound events: event id -> (files under assets/lacuna/sounds, subtitle en, subtitle it)
SOUNDS = {
    "item.totem_of_banishing.bell": (["item/totem_bell"], "Banishing bell tolls", "Rintocca la campana dell'esilio"),
}


def build(g):
    for name, sprites in PARTICLES.items():
        g.asset(f"particles/{name}", {"textures": [f"lacuna:{s}" for s in sprites]})
    sounds = {}
    for event, (files, en, it) in SOUNDS.items():
        subtitle = "subtitles.lacuna." + event
        sounds[event] = {"subtitle": subtitle, "sounds": [{"name": f"lacuna:{f}", "attenuation_distance": 32} for f in files]}
        g.name(subtitle, en, it)
    g.asset("sounds", sounds)
