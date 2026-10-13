"""M0: creative tab, command and system messages, config screen titles."""


def build(g):
    g.name("itemGroup.lacuna", "Lacuna", "Lacuna")
    g.name("lacuna.configuration.title", "Lacuna Config", "Configurazione di Lacuna")
    for section, en, it in [
        ("general", "General", "Generale"),
        ("divineSight", "Divine Sight", "Vista Divina"),
        ("forgottenLand", "Forgotten Land", "Terra Dimenticata"),
        ("beam", "Divine Magnifying Glass", "Lente Divina"),
        ("eclipser", "Eclipser", "Eclissatore"),
        ("sunsFury", "Sun's Fury", "Furia del Sole"),
        ("items", "Utility Items", "Oggetti utili"),
        ("gear", "Gear", "Equipaggiamento"),
        ("wood", "Wood Families", "Famiglie di legno"),
        ("mobs", "Mobs", "Creature"),
        ("forgottenBiomes", "Forgotten Land Biomes", "Biomi della Terra Dimenticata"),
        ("villages", "Villages", "Villaggi"),
    ]:
        g.name(f"lacuna.configuration.{section}", en, it)
    g.name("lacuna.message.disabled", "This power is dormant on this world.", "Questo potere è sopito in questo mondo.")
    g.name("lacuna.message.feature_failed", "Something beyond comprehension went wrong; the power fades.",
           "Qualcosa di incomprensibile è andato storto; il potere svanisce.")
