"""M3: Divine Magnifying Glass + every Lacuna damage type (spec section 21)."""

DAMAGE_TYPES = [
    # id, fire, en death message, en player message, it, it player
    ("solar_beam", True, "%1$s was burned by a focused sunbeam", "%1$s was burned by %2$s's focused sunbeam",
     "%1$s è stato bruciato da un raggio di sole concentrato", "%1$s è stato bruciato dal raggio di sole di %2$s"),
    ("eclipse", False, "%1$s was cut by the eclipse", "%1$s was cut through by %2$s's eclipse",
     "%1$s è stato tagliato dall'eclissi", "%1$s è stato attraversato dall'eclissi di %2$s"),
    ("echo_strike", False, "%1$s was struck by an echo", "%1$s was struck by the echo of %2$s's blade",
     "%1$s è stato colpito da un'eco", "%1$s è stato colpito dall'eco della lama di %2$s"),
    ("solar_ray", True, "%1$s was erased by the sun", "%1$s was erased by %2$s's fury of the sun",
     "%1$s è stato cancellato dal sole", "%1$s è stato cancellato dalla furia del sole di %2$s"),
    ("pyrrhic_bolt", False, "%1$s was hit by a pyrrhic bolt", "%1$s paid for %2$s's pyrrhic bolt",
     "%1$s è stato colpito da un dardo pirrico", "%1$s ha pagato il dardo pirrico di %2$s"),
    ("gravemaw_wave", False, "%1$s was swallowed by a crescent wave", "%1$s was swallowed by %2$s's crescent wave",
     "%1$s è stato inghiottito da un'onda a mezzaluna", "%1$s è stato inghiottito dall'onda a mezzaluna di %2$s"),
    ("umbral_rend", False, "%1$s was torn open by umbral shadows", "%1$s was rent apart by %2$s's umbral blade",
     "%1$s è stato squarciato dalle ombre umbrali", "%1$s è stato squarciato dalla lama umbrale di %2$s"),
    ("eclipse_cleave", False, "%1$s was swept away by an eclipse shockwave", "%1$s was cleaved by %2$s's eclipse shockwave",
     "%1$s è stato travolto da un'onda d'urto d'eclissi", "%1$s è stato travolto dall'onda d'urto d'eclissi di %2$s"),
    ("tidal_wave", False, "%1$s was swept away by a tidal wave", "%1$s was swept away by %2$s's tidal wave",
     "%1$s è stato travolto da un'onda di marea", "%1$s è stato travolto dall'onda di marea di %2$s"),
    ("reflection_touch", False, "%1$s met their own reflection", "%1$s met their own reflection while fighting %2$s",
     "%1$s ha incontrato il proprio riflesso", "%1$s ha incontrato il proprio riflesso combattendo %2$s"),
]


def build(g):
    for did, fire, en, en_p, it, it_p in DAMAGE_TYPES:
        g.data(f"damage_type/{did}", {"exhaustion": 0.1, "message_id": f"lacuna.{did}",
                                       "scaling": "when_caused_by_living_non_player"})
        g.name(f"death.attack.lacuna.{did}", en, it)
        g.name(f"death.attack.lacuna.{did}.player", en_p, it_p)
        if fire:
            g.tag("damage_type", "minecraft:is_fire", f"lacuna:{did}")
    g.tag("damage_type", "minecraft:no_knockback", "lacuna:solar_beam", "lacuna:umbral_rend", "lacuna:eclipse_cleave",
          "lacuna:tidal_wave")
    g.tag("damage_type", "minecraft:bypasses_armor", "lacuna:umbral_rend")

    g.item("divine_magnifying_glass", "Divine Magnifying Glass", "Lente d'ingrandimento divina", model="handheld")
    g.name("item.lacuna.divine_magnifying_glass.lore", "Sunlight remembers where it was told to go.",
           "La luce del sole ricorda dove le è stato detto di andare.")
    g.name("item.lacuna.divine_magnifying_glass.use", "Hold use: focus a burning beam of light.",
           "Tieni premuto: concentra un raggio di luce ardente.")
    g.shaped("divine_magnifying_glass", [" G ", "GDG", " S "],
             {"G": "minecraft:gold_ingot", "D": "lacuna:divine_glass", "S": "minecraft:stick"},
             "lacuna:divine_magnifying_glass", category="equipment", unlock="lacuna:divine_glass")
    g.tag("block", "lacuna:not_meltable", "minecraft:blue_ice")
