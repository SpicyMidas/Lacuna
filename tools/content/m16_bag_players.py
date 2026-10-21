"""Comically Large Bag, players: carried off in the bag for a while (bag/BaggedPlayers, client/bag/BagPlayerClient).

The inside-the-sack overlay is painted by tools/art/m16_bag_players.py (textures/gui/bag_inside.png).
"""

LANG = {
    "item.lacuna.comically_large_bag.use4": ("Empty: use on a player to carry them off for a while (PvP only)",
                                             "Vuoto: usalo su un giocatore per portartelo via per un po' (solo con il PvP)"),
    "lacuna.bag.player_for": ("%s (for %s s)", "%s (per %s s)"),
    "lacuna.bag.player_out": ("%s (wriggling out)", "%s (sta sgusciando fuori)"),
    "lacuna.bag.in_bag": ("In %s's bag! Jump or attack to struggle · %s s",
                          "Nel sacco di %s! Salta o attacca per divincolarti · %s s"),
    "lacuna.bag.player_free": ("%s wriggles free!", "%s sguscia fuori!"),
    "lacuna.bag.player_let_out": ("%s tumbles out of the bag.", "%s ruzzola fuori dal sacco."),
    "lacuna.bag.you_free": ("You wriggle free!", "Sei sgusciato fuori!"),
    "lacuna.bag.player_cooldown": ("%s is still dizzy from the last bag.", "%s è ancora stordito dall'ultimo sacco."),
    "lacuna.bag.player_immune": ("They can't be caught right now.", "Ora non si lascia acchiappare."),
    "lacuna.bag.no_pvp": ("PvP is off: no bagging players.", "Il PvP è disattivato: niente giocatori nel sacco."),
    "lacuna.bag.hands_full": ("You're already carrying someone.", "Stai già portando qualcuno."),
    "lacuna.bag.player_busy": ("They're tangled up with another bag.", "È già alle prese con un altro sacco."),
    "lacuna.bag.player_gone": ("Empty: they already wriggled out.", "Vuoto: è già sgusciato fuori."),
}


def build(g):
    for key, (en, it) in LANG.items():
        g.name(key, en, it)
