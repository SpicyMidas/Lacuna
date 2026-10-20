"""Starlace Bandage (mummy/Mummification): starlace cloth cut into bandages that wrap a creature into a frozen, silent
mummy; shears unwrap it as it was. Item, recipe, refusal tag, messages and an advancement."""
from content.m12_polish import adv, code


def build(g):
    g.item("starlace_bandage", "Starlace Bandage", "Benda stellare")
    g.shapeless("starlace_bandage", ["lacuna:starlace_cloth", "lacuna:starlace_cloth", "lacuna:starlace_cloth",
                                     "minecraft:string"], "lacuna:starlace_bandage", 3, unlock="lacuna:starlace_cloth")
    # what the Comically Large Bag refuses (bosses aside: those are refused in code), the bandage refuses too
    g.tag("entity_type", "lacuna:bandage_refuses", "#lacuna:bag_refuses")
    g.name("item.lacuna.starlace_bandage.tip", "Strips of starlace: what they bind lies still and silent.",
           "Strisce di tessuto stellare: ciò che avvolgono resta immobile e muto.")
    g.name("item.lacuna.starlace_bandage.use", "Use on a creature: wrap it into a mummy",
           "Usa su una creatura: avvolgila fino a farne una mummia")
    g.name("item.lacuna.starlace_bandage.wraps", " Peaceful: 1 bandage; hostile or large: 2; large and hostile: 3",
           " Pacifica: 1 benda; ostile o grande: 2; grande e ostile: 3")
    g.name("item.lacuna.starlace_bandage.hurry", " Finish within %s s, or the wraps come loose",
           " Finisci entro %s s, o le bende si allentano")
    g.name("item.lacuna.starlace_bandage.mummy",
           "Mummy: frozen, silent and harmless, never despawns, takes %s%% less damage",
           "Mummia: immobile, muta e innocua, non scompare mai, subisce il %s%% di danni in meno")
    g.name("item.lacuna.starlace_bandage.shears", "Shears unwrap it as it was; some bandages come back",
           "Le cesoie la liberano com'era; alcune bende si recuperano")
    g.name("item.lacuna.starlace_bandage.refuse", "Players and mighty creatures can't be wrapped",
           "Giocatori e creature possenti non si possono avvolgere")
    g.name("lacuna.mummy.progress", "Starlace wraps: %s/%s", "Bende stellari: %s/%s")
    g.name("lacuna.mummy.sealed", "Mummified: %s", "Mummificata: %s")
    g.name("lacuna.mummy.already", "Already wrapped tight. Shears unwrap it.", "Già avvolta stretta. Le cesoie la liberano.")
    g.name("lacuna.mummy.no_players", "Players can't be mummified.", "I giocatori non si possono mummificare.")
    g.name("lacuna.mummy.too_mighty", "Far too mighty to be wrapped.", "Troppo possente per essere avvolta.")
    g.name("lacuna.mummy.not_yours", "That creature belongs to someone else.", "Quella creatura appartiene a qualcun altro.")
    g.name("lacuna.mummy.ridden", "Someone is riding it.", "Qualcuno la sta cavalcando.")
    g.name("lacuna.mummy.protected", "You can't do that here.", "Non puoi farlo qui.")
    g.name("lacuna.mummy.wont_hold", "The bandage won't hold on that.", "La benda non tiene su quello.")
    adv(g, "its_a_wrap", "lacuna:starlace_bandage", code(), "root",
        en=("It's a Wrap", "Mummify a creature with Starlace Bandages."),
        it=("Tutto avvolto", "Mummifica una creatura con le bende stellari."))
