"""Shadow Possession (Shadow Walk): clicking a creature or player while shadow walking possesses it (eclipse/Possession).
Refusal tag, HUD and refusal messages."""


def build(g):
    # never possessed: what the Comically Large Bag refuses (bosses, wardens, elder guardians, bound mod creatures;
    # #c:bosses is also refused in code) and the Last Merchant
    g.tag("entity_type", "lacuna:possession_refuses", "#lacuna:bag_refuses", "lacuna:last_merchant")

    g.name("lacuna.possess.hud.resistance", "Host resistance", "Resistenza dell'ospite")
    g.name("lacuna.possess.hud.fighting", "Your host is fighting back! %s s", "Il tuo ospite si ribella! %s s")
    g.name("lacuna.possess.hud.hint", "Move to steer · Attack to strike · Sneak to leave",
           "Muoviti per guidarlo · Attacca per colpire · Accovacciati per uscire")
    g.name("lacuna.possess.host.seized", "A shadow has seized your body!", "Un'ombra si è impadronita del tuo corpo!")
    g.name("lacuna.possess.host.hint", "Struggle to cast it out: move, look away, strike",
           "Divincolati per scacciarla: muoviti, distogli lo sguardo, colpisci")
    g.name("lacuna.possess.host.breaking", "Breaking free... %s s", "Ti stai liberando... %s s")
    g.name("lacuna.possess.host.fighting", "You fight back against the shadow!", "Ti ribelli all'ombra!")
    g.name("lacuna.possess.freed", "You cast the shadow out!", "Hai scacciato l'ombra!")
    g.name("lacuna.possess.expelled", "Your host cast you out!", "Il tuo ospite ti ha scacciato!")

    g.name("lacuna.possess.nothing", "There is no soul there to possess.", "Lì non c'è nessuna anima da possedere.")
    g.name("lacuna.possess.reeling", "Your shadow is still reeling (%s s).", "La tua ombra è ancora stordita (%s s).")
    g.name("lacuna.possess.taken", "Another shadow already dwells in that body.", "Un'altra ombra abita già quel corpo.")
    g.name("lacuna.possess.wary", "That soul just cast a shadow out and is on its guard.",
           "Quell'anima ha appena scacciato un'ombra ed è in guardia.")
    g.name("lacuna.possess.too_mighty", "That soul is far too mighty to possess.",
           "Quell'anima è troppo possente per essere posseduta.")
    g.name("lacuna.possess.no_players", "Players can't be possessed here.", "Qui i giocatori non si possono possedere.")
    g.name("lacuna.possess.untouchable", "That player can't be possessed right now.", "Quel giocatore non si può possedere ora.")
    g.name("lacuna.possess.no_pvp", "PvP is off: players can't be possessed.",
           "Il PvP è disattivato: i giocatori non si possono possedere.")
    g.name("lacuna.possess.protected", "You can't possess anyone here.", "Qui non puoi possedere nessuno.")
    g.name("lacuna.possess.lifeless", "That body won't answer to anyone.", "Quel corpo non risponde a nessuno.")
    g.name("lacuna.possess.ridden", "It is riding or being ridden.", "Sta cavalcando o è cavalcata.")
    g.name("lacuna.possess.not_yours", "That creature belongs to someone else.", "Quella creatura appartiene a qualcun altro.")

    g.name("effect.lacuna.shadow_walk.description",
           "A shadow that passes through walls, unseen. Click a creature or player to possess it until it casts you out.",
           "Un'ombra che attraversa i muri, invisibile. Clicca una creatura o un giocatore per possederlo finché non ti scaccia.")
