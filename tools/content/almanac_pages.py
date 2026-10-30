"""The Lacuna Almanac's pages: title, text and "where to find it" line of every entry, in English and Italian.

Used by m20_almanac.py (which lists the entries, their chapter and icons, and writes the lang keys). Each value is
((title_en, text_en, where_en), (title_it, text_it, where_it)):
  * title None: the entry uses its icon's own name (item.lacuna.<id> / block.lacuna.<id> / the key named in ENTRIES);
  * where None: generated from the loot data by m20_almanac.py (every relic, and the boss/treasure table);
  * markup: a newline starts a paragraph, "- " starts a bullet, **...** is bold.
Numbers come from docs/PROGRESSION.md and Balance.java; keep them in step when rebalancing.
"""

PAGES = {
    # ============================================================================================ getting started
    "welcome": (
        ("How to Read This Almanac",
         "Lacuna fills the gaps the world forgot: a grey land you only reach by banishment, relics no smith can forge, "
         "villages that keep laws and coins.\n"
         "Every page tells what a thing does and, in the gold-rimmed box, **where to find it**. Turn pages with the arrows, "
         "the arrow keys or the mouse wheel. The tabs on the right open a chapter; the top one, or Home, brings you back "
         "to the contents.\n"
         "Hover an icon to see its tooltip. Over any Lacuna item, hold **Shift** (Controls → Lacuna) for its details: "
         "relics even say where they turn up.",
         "Every player is handed one on first joining a world. Lost it? A book and a ruby make another."),
        ("Come leggere questo almanacco",
         "Lacuna colma i vuoti che il mondo ha dimenticato: una terra grigia che si raggiunge solo con l'esilio, reliquie "
         "che nessun fabbro sa forgiare, villaggi con leggi e monete.\n"
         "Ogni pagina spiega a cosa serve una cosa e, nel riquadro bordato d'oro, **dove trovarla**. Sfoglia con le "
         "frecce, i tasti freccia o la rotella del mouse. Le linguette a destra aprono un capitolo; la prima, o Inizio, "
         "riporta all'indice.\n"
         "Passa sopra un'icona per vederne la descrizione. Su un oggetto di Lacuna tieni premuto **Maiusc** (Comandi → "
         "Lacuna) per i dettagli: le reliquie dicono perfino dove si trovano.",
         "Ogni giocatore ne riceve uno al primo ingresso in un mondo. Perso? Un libro e un rubino ne fanno un altro."),
    ),
    "path": (
        ("The Path in Short",
         "- **The Overworld:** mine rubies (below Y 16) and Umbral Steel (below Y 0), forge Umbral Steel tools, fell the "
         "four new trees, make friends in the villages.\n"
         "- **Relics are found, never crafted:** watch the night sky for falling stars, dive to Sunken Bell Towers, raid "
         "treasure and bosses, save rubies for the Last Merchant.\n"
         "- **A Lacunar Quintessence** and a Totem of Undying make a Totem of Banishing: the key to the Forgotten Land.\n"
         "- **The Forgotten Land:** divine glass, zenithite, exilite, ruin caches and the Mirror Labyrinth, whose "
         "Reflection always leaves another Quintessence.",
         "The Lacuna advancement tab follows the same road: every milestone hints at where to look next."),
        ("Il cammino in breve",
         "- **Il Mondo normale:** scava rubini (sotto Y 16) e acciaio umbrale (sotto Y 0), forgia gli attrezzi di "
         "acciaio umbrale, abbatti i quattro nuovi alberi, fatti amici nei villaggi.\n"
         "- **Le reliquie si trovano, non si fabbricano:** scruta il cielo notturno per le stelle cadenti, immergiti "
         "nelle torri della campana sommersa, saccheggia tesori e boss, metti da parte rubini per l'Ultimo mercante.\n"
         "- **Una Quintessenza lacunare** e un Totem dell'immortalità fanno un Totem dell'esilio: la chiave della Terra "
         "Dimenticata.\n"
         "- **La Terra Dimenticata:** vetro divino, zenithite, esilite, i forzieri delle rovine e il Labirinto degli "
         "specchi, il cui Riflesso lascia sempre un'altra Quintessenza.",
         "La scheda dei progressi di Lacuna segue la stessa strada: ogni traguardo suggerisce dove cercare dopo."),
    ),
    "mending": (
        ("Wear and Mending",
         "Relics and gadgets wear out with use. Mend them at an anvil with their own material, never by combining two: "
         "Umbral Steel for the Eclipser, the Gravemaw, the Null Lantern and the Pocket Watch; divine glass shards for the "
         "Magnifying Glass and the Mirror; Echo Fragments for the flute, Ember for the triangle, Star Fragments for the "
         "trumpet, Silk Dust for the wand, ender pearls for the pearl shards.\n"
         "A **Repair Kit** restores a quarter of any item held in the other hand.\n"
         "**Totems** are the exception: their uses are final, and nothing mends them.",
         "Each relic's page in this Almanac names its material. Repair Kits: crafted, or from the Smiths' Guild."),
        ("Usura e riparazione",
         "Reliquie e congegni si consumano con l'uso. Si riparano all'incudine con il loro materiale, mai unendone due: "
         "acciaio umbrale per l'Eclissatore, lo Spadone, la Lanterna nulla e l'Orologio; frammenti di vetro divino per la "
         "Lente e lo Specchio; Frammenti d'eco per il flauto, Brace per il triangolo, Frammenti di stella per la tromba, "
         "Polvere di seta per la bacchetta, perle di Ender per le schegge della perla.\n"
         "Un **Kit di riparazione** ripristina un quarto di qualunque oggetto tenuto nell'altra mano.\n"
         "Fanno eccezione i **totem**: i loro utilizzi sono definitivi e niente li ripara.",
         "La pagina di ogni reliquia in questo almanacco ne indica il materiale. Kit di riparazione: si fabbricano, o "
         "dalla Gilda dei fabbri."),
    ),

    # ============================================================================================ the forgotten land
    "forgotten_land": (
        ("The Forgotten Land",
         "A grey, foggy land caught in an endless afternoon. Every block here stands for **12** in the Overworld: a short "
         "walk there is a long way home.\n"
         "You arrive banished, and a countdown (about six minutes, drifting from 4½ to 9) shows the Overworld spot you will "
         "return to. Until then the land keeps you: no beds, no ender pearls, no way out but its own. The totem lets you "
         "leave early.\n"
         "Die here and your belongings fall in the Overworld under the same spot, glowing as **Unreadable Relics**: for ten "
         "minutes only you can pick them up.",
         "Use a Totem of Banishing, or step into a lit Forgotten Portal. A creature struck with the totem is sent here too."),
        ("La Terra Dimenticata",
         "Una terra grigia e nebbiosa, ferma in un pomeriggio senza fine. Ogni blocco qui vale **12** blocchi del Mondo "
         "normale: una breve passeggiata là è una lunga strada di casa.\n"
         "Ci arrivi esiliato, e un conto alla rovescia (circa sei minuti, che variano da 4 e mezzo a 9) mostra il punto "
         "del Mondo normale in cui tornerai. Fino ad allora la terra ti trattiene: niente letti, niente perle di Ender, "
         "nessuna uscita se non le sue. Con il totem puoi andartene prima.\n"
         "Se muori qui, i tuoi averi cadono nel Mondo normale sotto lo stesso punto, luminosi come **Reliquie "
         "illeggibili**: per dieci minuti solo tu puoi raccoglierli.",
         "Usa un Totem dell'esilio o entra in un Portale dimenticato acceso. Anche una creatura colpita con il totem finisce qui."),
    ),
    "forgotten_portal": (
        (None,
         "A way that stays open. Lay eight frames flat in a ring, two on each side with the corners left empty, around a "
         "2×2 opening, then touch one with a Totem of Banishing: it costs one use, and the portal stays lit for good.\n"
         "Step in to cross to the Forgotten Land, with the usual countdown and the 1:12 coordinates; a portal built there "
         "takes you home. Creatures pushed in are banished too.",
         "Frames: 4 rubies around a crying obsidian make one; an Exilite Ingot between two crying obsidian makes two."),
        (None,
         "Un passaggio che resta aperto. Disponi in piano otto cornici ad anello, due per lato con gli angoli vuoti, "
         "attorno a un'apertura 2×2, poi toccane una con un Totem dell'esilio: costa un utilizzo e il portale resta "
         "acceso per sempre.\n"
         "Entraci per passare nella Terra Dimenticata, con il solito conto alla rovescia e le coordinate 1:12; un portale "
         "costruito laggiù ti riporta a casa. Anche le creature spinte dentro vengono esiliate.",
         "Cornici: 4 rubini attorno a un'ossidiana piangente ne fanno una; un lingotto di esilite tra due ossidiane "
         "piangenti ne fa due."),
    ),
    "forgotten_plains": (
        (None,
         "The heart of the land: gentle grey hills crowded with ruins, groves of Echo Birch and, in about one chunk in "
         "forty, a patch of divine glass clusters. Spiders roam the fog.",
         "Most of the Forgotten Land, between the four regions below."),
        (None,
         "Il cuore della terra: dolci colline grigie affollate di rovine, boschetti di Betulla dell'eco e, in circa un "
         "chunk su quaranta, una macchia di grappoli di vetro divino. Nella nebbia si aggirano i ragni.",
         "Quasi tutta la Terra Dimenticata, tra le quattro regioni che seguono."),
    ),
    "cinderfrost_flats": (
        (None,
         "The cold edge of the land, where ash falls like snow and keeps your **footprints** for a while. Still ponds are "
         "capped with **Clear Ice**, so the drowned ruins below stay in view. Rare, bare Cindervine spires; nothing "
         "hostile spawns.",
         "The coldest stretches of the Forgotten Land. Ashen Snow drops itself; Clear Ice needs Silk Touch."),
        (None,
         "Il confine freddo della terra, dove la cenere cade come neve e conserva le tue **impronte** per un po'. Gli "
         "stagni sono coperti di **Ghiaccio limpido**, così le rovine sommerse restano in vista. Rare guglie spoglie di "
         "Vitecenere; non nasce nulla di ostile.",
         "Le distese più fredde della Terra Dimenticata. La Neve cinerea lascia sé stessa; il Ghiaccio limpido richiede "
         "Tocco di velluto."),
    ),
    "inverted_falls": (
        (None,
         "Stone islands float 110 to 150 blocks up, veined with **zenithite** and crowned with Crystal Willows and divine "
         "glass. Here the waterfalls run upward: an **Updraft Spring** lifts whatever stands in its column, up to 40 "
         "blocks, and a chain of springs beside every island carries you to its top.",
         "The warm, humid stretches of the Forgotten Land. Updraft Springs need Silk Touch; placed by hand they lift 40 blocks."),
        (None,
         "Isole di pietra fluttuano tra 110 e 150 blocchi d'altezza, venate di **zenithite** e coronate di Salici di "
         "cristallo e vetro divino. Qui le cascate salgono: una **Sorgente ascendente** solleva ciò che sta nella sua "
         "colonna, fino a 40 blocchi, e una catena di sorgenti accanto a ogni isola ti porta in cima.",
         "Le distese calde e umide della Terra Dimenticata. Le Sorgenti ascendenti richiedono Tocco di velluto; posate a "
         "mano sollevano per 40 blocchi."),
    ),
    "whispering_marsh": (
        (None,
         "Shallow pools, mud and Tidebloom mangroves under a fog so thick that distant sounds come muffled. **Spore "
         "Mushrooms** glow and make every creature within 6 blocks shine through the mist. Lantern Moths drift here, and "
         "spiders lurk.",
         "The humid stretches of the Forgotten Land. Spore Mushrooms drop themselves."),
        (None,
         "Pozze basse, fango e mangrovie di Fiordimarea sotto una nebbia così fitta che i suoni lontani arrivano "
         "attutiti. I **Funghi sporali** brillano e fanno risplendere nella foschia ogni creatura entro 6 blocchi. Qui "
         "fluttuano le Falene lanterna e si annidano i ragni.",
         "Le distese umide della Terra Dimenticata. I Funghi sporali lasciano sé stessi."),
    ),
    "clockwork_dunes": (
        (None,
         "Hot, dry sand where time runs slow: crops grow at half speed. Half-buried **pocket watches** still tick in the "
         "dunes; dig them up for 1-2 Chronal Shards each. Rusted gears, dead bushes, rabbits and a few Echo Birches.",
         "The hot, dry stretches of the Forgotten Land. Silk Touch keeps a Buried Watch whole."),
        (None,
         "Sabbia calda e secca dove il tempo scorre lento: le coltivazioni crescono a metà velocità. Nelle dune "
         "ticchettano ancora **orologi** mezzi sepolti; dissotterrali per 1-2 Schegge cronali ciascuno. Ingranaggi "
         "arrugginiti, arbusti secchi, conigli e qualche Betulla dell'eco.",
         "Le distese calde e secche della Terra Dimenticata. Con Tocco di velluto l'Orologio sepolto resta intero."),
    ),
    "ruins": (
        ("Forgotten Ruins",
         "Broken arches, fallen colonnades, ruined halls, sunken plazas, memory shrines and obelisk stumps: what is left "
         "of a city nobody remembers. Ruin stone cannot be mined, blasted or pushed.\n"
         "Ruined halls, sunken plazas and memory shrines hide **ruin caches**: rubies, gold, divine glass, now and then a "
         "chronal or exilite shard or a Solar Flare Fragment, and one rare roll: a Lacunar Quintessence (1%) or a relic.",
         "All over the Forgotten Land, thickest in the Forgotten Plains."),
        ("Rovine dimenticate",
         "Archi spezzati, colonnati crollati, sale in rovina, piazze sommerse, santuari della memoria e monconi di "
         "obelisco: ciò che resta di una città che nessuno ricorda. La pietra delle rovine non si scava, non esplode e "
         "non si sposta.\n"
         "Sale in rovina, piazze sommerse e santuari della memoria nascondono i **forzieri delle rovine**: rubini, oro, "
         "vetro divino, a volte una scheggia cronale o di esilite o un Frammento di brillamento solare, e un'estrazione "
         "rara: una Quintessenza lacunare (1%) o una reliquia.",
         "In tutta la Terra Dimenticata, più fitte nelle Pianure dimenticate."),
    ),
    "memory_murals": (
        ("Memory Murals",
         "Five paintings of the city as it was, in warm colours the land has lost: Dawn over the Spires, The Bright "
         "Spire, Harvest Day, Festival of Lanterns and The Fall.",
         "On the walls of memory shrines and ruined halls. A painting you hang yourself may turn out to be one of them."),
        ("Affreschi della memoria",
         "Cinque dipinti della città com'era, nei colori caldi che la terra ha perduto: alba sulle guglie, la guglia "
         "luminosa, giorno del raccolto, festa delle lanterne e la caduta.",
         "Sulle pareti dei santuari della memoria e delle sale in rovina. Un quadro che appendi tu stesso potrebbe "
         "rivelarsi uno di loro."),
    ),
    "mirror_labyrinth": (
        ("Mirror Labyrinth",
         "A 25×25 maze of unbreakable mirror glass. Find the ring at its heart and step on it: your **Reflection** wakes "
         "and walks your own path three seconds behind you.\n"
         "Defeat it and the seals melt away: the exit opens, and so does the **Mirror Hoard**, which always holds a relic "
         "(and 10% of the time a Solar Flare Fragment). The Reflection leaves a Lacunar Quintessence. A labyrinth is "
         "solved only once.",
         "Rare, in every region of the Forgotten Land; the way in faces south."),
        ("Labirinto degli specchi",
         "Un labirinto 25×25 di vetro a specchio indistruttibile. Trova l'anello al suo centro e calpestalo: il tuo "
         "**Riflesso** si desta e ripercorre i tuoi passi tre secondi dietro di te.\n"
         "Sconfiggilo e i sigilli si dissolvono: si apre l'uscita, e con lei il **tesoro del labirinto**, che contiene "
         "sempre una reliquia (e una volta su dieci un Frammento di brillamento solare). Il Riflesso lascia una "
         "Quintessenza lacunare. Un labirinto si risolve una volta sola.",
         "Raro, in ogni regione della Terra Dimenticata; l'ingresso guarda a sud."),
    ),

    # ============================================================================================ relics
    "about_relics": (
        ("About Relics",
         "Relics are Lacuna's special weapons and gadgets, and **no smith can forge them**: they are found, each in at "
         "least two places.\n"
         "- Every Mirror Hoard holds one.\n"
         "- Sunken Bell Towers, ruin caches and village mints hide a few.\n"
         "- Vanilla bosses and treasure keep the ones that suit them.\n"
         "- The Last Merchant brings two at random to every visit, for rubies.\n"
         "They wear out and are mended at an anvil with their own material.",
         "Each relic's page lists its sources with their chances, per chest or per kill (+Looting: +1% per level)."),
        ("Le reliquie",
         "Le reliquie sono le armi e i congegni speciali di Lacuna, e **nessun fabbro sa forgiarle**: si trovano, "
         "ognuna in almeno due posti.\n"
         "- Ogni tesoro del Labirinto degli specchi ne custodisce una.\n"
         "- Le torri della campana sommersa, i forzieri delle rovine e le zecche dei villaggi ne nascondono alcune.\n"
         "- I boss e i tesori del gioco base custodiscono quelle adatte a loro.\n"
         "- L'Ultimo mercante ne porta due a caso a ogni visita, in cambio di rubini.\n"
         "Si consumano e si riparano all'incudine con il loro materiale.",
         "La pagina di ogni reliquia elenca le fonti con le probabilità, per forziere o per uccisione (+Saccheggio: +1% "
         "per livello)."),
    ),
    "suns_fury": (
        (None,
         "A noon that was never allowed to end. Hold use to draw it back, release to throw: it is **used up**. Where it "
         "lands the sun climbs to midday, then a column of light burns everything within 7 blocks for three seconds and, "
         "with mob griefing on, carves the ground away. In the Overworld it leaves the sky clear at noon.\n"
         "In the Forgotten Land it fizzles, and you keep it.",
         None),
        (None,
         "Un mezzogiorno a cui non fu mai permesso di finire. Tieni premuto usa per caricarla, rilascia per lanciarla: "
         "si **consuma**. Dove atterra il sole sale allo zenit, poi una colonna di luce brucia tutto entro 7 blocchi per "
         "tre secondi e, con il mobGriefing attivo, scava via il terreno. Nel Mondo normale lascia il cielo sereno a "
         "mezzogiorno.\n"
         "Nella Terra Dimenticata si spegne, e resta a te.",
         None),
    ),
    "eclipser": (
        (None,
         "Where it cuts, the light forgets to return. Each hit charges the blade (+10%, shown above the hotbar); taking "
         "damage empties it. At 20% or more, use it to **dash** up to 14 blocks: everything along the way takes 15.6 to 30 "
         "damage and is flung aside, and you end in a **Shadow Walk** of 3 to 6 seconds.\n"
         "8 damage, 20 s cooldown on the dash; mend it with Umbral Steel ingots.",
         None),
        (None,
         "Dove taglia, la luce dimentica di tornare. Ogni colpo carica la lama (+10%, mostrato sopra la barra "
         "rapida); subire danni la svuota. Dal 20% in su, usala per **scattare** fino a 14 blocchi: tutto ciò che trovi "
         "lungo la via subisce da 15,6 a 30 danni e viene scagliato di lato, e finisci in un **Passo d'ombra** di 3-6 "
         "secondi.\n"
         "8 danni, 20 s di ricarica per lo scatto; si ripara con lingotti di acciaio umbrale.",
         None),
    ),
    "shadow_walk": (
        ("Shadow Walk",
         "While shadow walking you are a shade: you pass through walls, unseen, and monsters lose track of you.\n"
         "Click a creature (or, with PvP on, a player) within 8 blocks to **possess** it: you see through its eyes and "
         "steer it with your keys; attacking makes it strike, creepers ignite, ghasts and blazes shoot. Hosts fight back, "
         "the hostile, big and wounded ones harder: once their bar fills you have a few seconds left. Sneak to leave; after "
         "30 seconds every host fights back.\n"
         "Bosses, other players' pets and mummies refuse you.",
         "The Eclipser's dash ends in a Shadow Walk."),
        ("Passo d'ombra",
         "Durante il Passo d'ombra sei un'ombra: attraversi i muri, invisibile, e i mostri ti perdono di vista.\n"
         "Clicca una creatura (o, con il PvP attivo, un giocatore) entro 8 blocchi per **possederla**: vedi con i suoi "
         "occhi e la guidi con i tuoi tasti; attaccando la fai colpire, i creeper si innescano, ghast e blaze sparano. "
         "Gli ospiti si ribellano, quelli ostili, grossi e feriti di più: quando la loro barra si riempie ti restano "
         "pochi secondi. Accovacciati per uscire; dopo 30 secondi ogni ospite si ribella.\n"
         "Boss, animali di altri giocatori e mummie ti respingono.",
         "Lo scatto dell'Eclissatore finisce in un Passo d'ombra."),
    ),
    "gravemaw_greatsword": (
        (None,
         "It has eaten the light of many graves: 11 damage, slow and heavy. Stand still and hold use to gather a charge; "
         "release after a second to send a **crescent wave** 8 blocks ahead (16 to 22 damage, knockback). Moving breaks "
         "the charge. Mend it with Umbral Steel ingots.",
         None),
        (None,
         "Ha divorato la luce di molte tombe: 11 danni, lento e pesante. Resta fermo e tieni premuto usa per raccogliere "
         "la carica; rilascia dopo un secondo per lanciare un'**onda a mezzaluna** fino a 8 blocchi (da 16 a 22 danni, "
         "con contraccolpo). Muoversi spezza la carica. Si ripara con lingotti di acciaio umbrale.",
         None),
    ),
    "tidecaller_sickle": (
        (None,
         "It remembers every tide that ever turned. Use it to roll a **Tidal Wave** 14 blocks ahead: it sweeps foes along "
         "(4 damage), puts out fire and crusts lava over.\n"
         "Hits in water or rain, waves that land and time spent in water build up to three **Tide Charges**; with all "
         "three, sneak + use opens a **Maelstrom** that drowns what it drags in and ends in a geyser. Melee hits drench "
         "and strike harder in water or rain. Mend it with prismarine shards.",
         None),
        (None,
         "Ricorda ogni marea che sia mai cambiata. Usalo per far rotolare un'**Onda di marea** per 14 blocchi: trascina i "
         "nemici (4 danni), spegne il fuoco e incrosta la lava.\n"
         "Colpi in acqua o sotto la pioggia, onde andate a segno e tempo passato in acqua accumulano fino a tre "
         "**Cariche di marea**; con tutte e tre, accovacciati + usa apre un **Maelstrom** che annega ciò che risucchia e "
         "finisce in un geyser. In mischia inzuppa e colpisce più forte in acqua o sotto la pioggia. Si ripara con "
         "frammenti di prismarino.",
         None),
    ),
    "ember_triangle": (
        (None,
         "Three embers that swore never to go out. Use it to release a **Thermal Wave** 10 blocks around you: hostile "
         "creatures, and anything hunting you, are pulverized on the spot. Bosses only take 30 damage. You, your pets, "
         "villagers, golems and peaceful animals are spared; other players only with PvP.\n"
         "16 uses, 60 s cooldown; each Ember mends 4 uses at an anvil.",
         None),
        (None,
         "Tre braci che giurarono di non spegnersi mai. Usalo per liberare un'**Onda termica** di 10 blocchi attorno a "
         "te: le creature ostili, e chiunque ti stia dando la caccia, vengono polverizzate all'istante. I boss subiscono "
         "solo 30 danni. Tu, i tuoi animali, i villici, i golem e gli animali pacifici siete risparmiati; gli altri "
         "giocatori solo con il PvP.\n"
         "16 utilizzi, 60 s di ricarica; ogni Brace ripara 4 utilizzi all'incudine.",
         None),
    ),
    "quickening_wand": (
        (None,
         "Lend a heart, and still things wake. Aim at a dropped item, or hold one in your off hand, and use the wand: for "
         "**one heart** the item grows little legs and fights beside you for 40 seconds with its own gifts: blades slash, "
         "bows shoot, food feeds you, TNT charges in. Then it falls asleep again, exactly as it was.\n"
         "Up to three at once; sneak + use on one to put it to sleep early. Mend it with Silk Dust.",
         None),
        (None,
         "Presta un cuore, e le cose immobili si destano. Mira a un oggetto a terra, o tienine uno nella mano secondaria, "
         "e usa la bacchetta: per **un cuore** l'oggetto mette le zampette e combatte al tuo fianco per 40 secondi con i "
         "suoi doni: le lame tagliano, gli archi tirano, il cibo ti sfama, la TNT si lancia alla carica. Poi si "
         "riaddormenta, esattamente com'era.\n"
         "Fino a tre alla volta; accovacciati + usa su uno per addormentarlo prima. Si ripara con Polvere di seta.",
         None),
    ),
    "starcaller_trumpet": (
        (None,
         "Sound it, and a star answers. A small falling star streaks down where you look, up to 96 blocks away, or "
         "**locks on** to the creature under your crosshair and follows it down. The impact deals up to 8 damage with "
         "knockback and fire, and leaves a **Molten Star Rock** to mine; it cracks the ground only with mob griefing on.\n"
         "32 uses, 30 s cooldown; mend it with Star Fragments.",
         None),
        (None,
         "Suonala, e una stella risponde. Una piccola stella cadente scende dove guardi, fino a 96 blocchi, oppure "
         "**aggancia** la creatura nel mirino e la insegue. L'impatto infligge fino a 8 danni con contraccolpo e fuoco, e "
         "lascia una **Roccia stellare fusa** da scavare; incrina il terreno solo con il mobGriefing attivo.\n"
         "32 utilizzi, 30 s di ricarica; si ripara con Frammenti di stella.",
         None),
    ),
    "mirror_of_recursion": (
        (None,
         "Every reflection remembers the last one. Raise it like a shield: arrows, tridents, fireballs and bullets coming "
         "from the front fly back to whoever shot them, and the mirror keeps an **echo** of each, up to three. Sneak + "
         "use fires the oldest echo again, a little weaker every time; after three reuses it shatters. Mend it with divine "
         "glass shards.",
         None),
        (None,
         "Ogni riflesso ricorda il precedente. Alzalo come uno scudo: frecce, tridenti, palle di fuoco e proiettili che "
         "arrivano da davanti tornano a chi li ha lanciati, e lo specchio ne conserva un'**eco**, fino a tre. "
         "Accovacciati + usa rilancia l'eco più vecchia, un po' più debole ogni volta; dopo tre riusi va in frantumi. Si "
         "ripara con frammenti di vetro divino.",
         None),
    ),
    "chronoshard_pocket_watch": (
        (None,
         "It ticks a little behind the world. Anywhere in your inventory it remembers a moment every two minutes (the "
         "last three). Use it to **rewind** to the newest moment at least 20 seconds old: your place, your health (if it "
         "was higher), your hunger; fire and freshly caught harmful effects are gone. It never crosses dimensions.\n"
         "Chronal Shards set in from the other hand (up to 5) shorten its cooldown from 5 to 2½ minutes. Mend it with "
         "Umbral Steel ingots.",
         None),
        (None,
         "Ticchetta un po' in ritardo sul mondo. Ovunque nel tuo inventario ricorda un momento ogni due minuti (gli "
         "ultimi tre). Usalo per **riavvolgere** fino al momento più recente vecchio di almeno 20 secondi: il tuo posto, "
         "la tua salute (se era più alta), la tua fame; fuoco ed effetti negativi appena presi spariscono. Non attraversa "
         "mai le dimensioni.\n"
         "Le Schegge cronali inserite dall'altra mano (fino a 5) ne accorciano la ricarica da 5 a 2 minuti e mezzo. Si "
         "ripara con lingotti di acciaio umbrale.",
         None),
    ),
    "null_lantern": (
        (None,
         "It shines a light nobody can see by. Held, hostile mobs ignore you and cannot hurt you; placed and lit, it "
         "shelters every player within 12 blocks, and no monster spawns within 24. Bosses pay it no heed.\n"
         "Its durability is a **charge** that burns slowly (about 50 minutes), held or placed; at zero it only goes dark. "
         "Click an Ender Pearl (+50%) or an Echo Shard (+100%) onto it, or use one on the placed lantern.",
         None),
        (None,
         "Diffonde una luce con cui nessuno vede. In mano, le creature ostili ti ignorano e non possono ferirti; posata e "
         "accesa, ripara ogni giocatore entro 12 blocchi, e nessun mostro nasce entro 24. I boss non se ne curano.\n"
         "La sua durabilità è una **carica** che si consuma piano (circa 50 minuti), in mano o posata; a zero si limita a "
         "spegnersi. Clicca sopra di lei una perla di Ender (+50%) o un frammento d'eco (+100%), o usalo sulla lanterna "
         "posata.",
         None),
    ),
    "divine_magnifying_glass": (
        (None,
         "Sunlight remembers where it was told to go. Hold use to focus a **burning beam** up to 24 blocks: 12 damage per "
         "second, setting creatures alight, kindling wood, melting snow and ice, priming TNT. At night it turns white and "
         "half as strong; underwater, blue, weaker and shorter. In the Forgotten Land it is always day.\n"
         "Each Divine Glass Shard mends a minute of beam at an anvil.",
         None),
        (None,
         "La luce del sole ricorda dove le fu detto di andare. Tieni premuto usa per concentrare un **raggio ardente** "
         "fino a 24 blocchi: 12 danni al secondo, dà fuoco alle creature, accende il legno, scioglie neve e ghiaccio, "
         "innesca la TNT. Di notte diventa bianco e forte la metà; sott'acqua, blu, più debole e più corto. Nella Terra "
         "Dimenticata è sempre giorno.\n"
         "Ogni frammento di vetro divino ripara un minuto di raggio all'incudine.",
         None),
    ),
    "echo_dagger": (
        (None,
         "Every cut happens twice: half a second after each hit, its **echo** strikes again for 60% of the damage. Light "
         "and quick (3 damage, 2.6 attacks per second). Mend it with zenithite shards.",
         None),
        (None,
         "Ogni taglio avviene due volte: mezzo secondo dopo ogni colpo, la sua **eco** colpisce di nuovo con il 60% dei "
         "danni. Leggero e rapido (3 danni, 2,6 attacchi al secondo). Si ripara con schegge di zenithite.",
         None),
    ),
    "pyrrhic_scepter": (
        (None,
         "Victory, at a price. Use it to pay **2 health** for a blazing bolt that homes in on the nearest hostile ahead: 5 "
         "damage, up to 15 the more wounded you are, and the target catches fire. It refuses when you have 4 health or "
         "less. Mend it with gold ingots.",
         None),
        (None,
         "La vittoria, a caro prezzo. Usalo per pagare **2 punti salute** in cambio di un dardo ardente che insegue la "
         "creatura ostile più vicina davanti a te: 5 danni, fino a 15 quanto più sei ferito, e il bersaglio prende fuoco. "
         "Si rifiuta se hai 4 punti salute o meno. Si ripara con lingotti d'oro.",
         None),
    ),
    "heartwood_bow": (
        (None,
         "Its arrows grow roots. Whatever they hit is **Rooted** in place for 2 seconds (4 when pinned against a wall), "
         "and Grasping Roots sprout around the target or where the arrow lands, holding anything that walks in. Mend it "
         "with Echo Birch planks.",
         None),
        (None,
         "Le sue frecce mettono radici. Ciò che colpiscono resta **Radicato** sul posto per 2 secondi (4 se inchiodato a "
         "un muro), e Radici avvinghianti spuntano attorno al bersaglio o dove la freccia atterra, trattenendo chiunque "
         "ci cammini dentro. Si ripara con assi di Betulla dell'eco.",
         None),
    ),
    "whisperbone_flute": (
        (None,
         "The dead still know the tune. Play it to call back up to three **Echoes** of creatures that died within 20 "
         "blocks in the last minute. They fight beside you for 10 seconds, then fade, each leaving an Echo Fragment, "
         "which mends the flute at an anvil. 60 s cooldown.",
         None),
        (None,
         "I morti conoscono ancora la melodia. Suonalo per richiamare fino a tre **Eco** di creature morte entro 20 "
         "blocchi nell'ultimo minuto. Combattono al tuo fianco per 10 secondi, poi svaniscono lasciando ciascuna un "
         "Frammento d'eco, che ripara il flauto all'incudine. 60 s di ricarica.",
         None),
    ),

    # ============================================================================================ totems & quintessence
    "lacunar_quintessence": (
        (None,
         "What is left when the world forgets: the exact shape of the gap. Legendary and always glinting, it is the "
         "**only way to make a totem**: the Totem of Banishing, the Totem of Confusion, even a Totem of Undying.\n"
         "Dropped, it shrugs off fire, lava and explosions and waits twenty minutes.",
         None),
        (None,
         "Ciò che resta quando il mondo dimentica: la forma esatta del vuoto. Leggendaria e sempre scintillante, è "
         "l'**unico modo di creare un totem**: il Totem dell'esilio, il Totem della Confusione, perfino un Totem "
         "dell'immortalità.\n"
         "Lasciata a terra, resiste a fuoco, lava ed esplosioni e aspetta venti minuti.",
         None),
    ),
    "totem_of_banishing": (
        (None,
         "A stone hand that remembers a land no one else does.\n"
         "- **Use:** cross to the Forgotten Land in a blast that throws everything nearby back, at both ends; used there, "
         "it brings you home early.\n"
         "- **Strike** a creature (or a player, with PvP): it is hurled away and banished. Bosses resist.\n"
         "- **Use on a ring of Forgotten Portal Frames** to light the portal.\n"
         "Six uses in all, never mended: every crossing, banishment and portal counts.",
         "Crafted: a Lacunar Quintessence above a Totem of Undying, with 3 rubies and 2 gold ingots (RQR / GTG / .R.)."),
        (None,
         "Una mano di pietra che ricorda una terra che nessun altro ricorda.\n"
         "- **Usa:** passa nella Terra Dimenticata con un'onda d'urto che respinge tutto ciò che è vicino, a entrambi i "
         "capi; usato laggiù, ti riporta a casa prima.\n"
         "- **Colpisci** una creatura (o un giocatore, con il PvP): viene scagliata via ed esiliata. I boss resistono.\n"
         "- **Usalo su un anello di Cornici del portale dimenticato** per accendere il portale.\n"
         "Sei utilizzi in tutto, mai riparabili: ogni passaggio, esilio e portale conta.",
         "Si fabbrica: una Quintessenza lacunare sopra un Totem dell'immortalità, con 3 rubini e 2 lingotti d'oro "
         "(RQR / GTG / .R.)."),
    ),
    "totem_of_confusion": (
        (None,
         "Five of you. Only one is real. Use it and five copies of you appear around you, with your skin, armour and held "
         "items. Three times in four you **swap places** with one of them, and only you are told. The copies mirror your "
         "every move, each on its own heading, and the hostile mobs hunting you are drawn to them.\n"
         "Two hits break a copy; they all fade after 20 seconds. Three uses, 45 s cooldown.",
         "Crafted: an ender pearl, 2 amethyst shards, an armour stand, 2 gold ingots and a Lacunar Quintessence "
         "(.P. / AMA / GQG)."),
        (None,
         "Cinque di te. Solo uno è vero. Usalo e cinque copie di te appaiono tutt'intorno, con la tua pelle, armatura e "
         "oggetti in mano. Tre volte su quattro **prendi il posto** di una di loro, e lo sai solo tu. Le copie imitano "
         "ogni tua mossa, ognuna nella sua direzione, e le creature ostili che ti cacciano vengono attirate da loro.\n"
         "Due colpi distruggono una copia; svaniscono tutte dopo 20 secondi. Tre utilizzi, 45 s di ricarica.",
         "Si fabbrica: una perla di Ender, 2 frammenti di ametista, un supporto per armature, 2 lingotti d'oro e una "
         "Quintessenza lacunare (.P. / AMA / GQG)."),
    ),
    "totem_of_undying": (
        ("Totem of Undying",
         "The vanilla totem that cheats death, now also crafted the legendary way: a Lacunar Quintessence set in gold, "
         "with two emeralds for eyes. It is the heart of every Totem of Banishing.",
         "Evokers (woodland mansions and raids), or crafted: 2 emeralds, 4 gold ingots and a Lacunar Quintessence "
         "(EGE / GQG / .G.)."),
        ("Totem dell'immortalità",
         "Il totem del gioco base che inganna la morte, ora anche da fabbricare alla maniera leggendaria: una Quintessenza "
         "lacunare incastonata nell'oro, con due smeraldi per occhi. È il cuore di ogni Totem dell'esilio.",
         "Gli evocatori (magioni della foresta e incursioni), oppure si fabbrica: 2 smeraldi, 4 lingotti d'oro e una "
         "Quintessenza lacunare (EGE / GQG / .G.)."),
    ),

    # ============================================================================================ gear & tools
    "umbral_steel_tools": (
        ("Umbral Steel Tools",
         "Steel that grows heavier in the dark: netherite-level mining, 1500 uses, and every tool sets a ruby in its grip.\n"
         "- **Nightforged:** they all dig 30% faster where the light is 7 or less.\n"
         "- In the dark (light 4 or less) they hit 25% harder.\n"
         "- **Sword, Umbral Rend:** every third hit on the same foe tears it open: +6 armour-piercing damage, heals you, "
         "slows and blinds it.\n"
         "- **Axe, Eclipse Cleave:** fully charged hits send a shockwave dealing 60% of the hit to foes within 3 blocks.",
         "Crafted in the vanilla shapes from Umbral Steel ingots, with a ruby in place of the first stick."),
        ("Attrezzi di acciaio umbrale",
         "Acciaio che si fa più pesante al buio: scava come la netherite, 1500 utilizzi, e ogni attrezzo ha un rubino "
         "incastonato nell'impugnatura.\n"
         "- **Forgiati nella notte:** scavano tutti il 30% più in fretta dove la luce è 7 o meno.\n"
         "- Al buio (luce 4 o meno) colpiscono il 25% più forte.\n"
         "- **Spada, Squarcio umbrale:** ogni terzo colpo sullo stesso nemico lo squarcia: +6 danni perforanti, ti cura, "
         "lo rallenta e lo acceca.\n"
         "- **Ascia, Fendente d'eclissi:** i colpi caricati al massimo liberano un'onda d'urto che infligge il 60% del "
         "colpo ai nemici entro 3 blocchi.",
         "Si fabbricano con le forme del gioco base da lingotti di acciaio umbrale, con un rubino al posto del primo "
         "bastone."),
    ),
    "starlace_armor": (
        ("Starlace Armour",
         "Zenithite thread that still remembers the night sky: as sturdy as iron, with powers that grow with the pieces "
         "you wear.\n"
         "- **Starlit:** +5% speed per piece at night under the open sky, or in the End.\n"
         "- **Starfall:** two pieces halve fall damage; the full set turns a fall of over 5 blocks into a starlight "
         "shockwave that strikes foes nearby.\n"
         "- **Constellation Step** (full set): jump again in mid-air.\n"
         "- **Boots:** sneak while falling to glide on starlight.",
         "Crafted in the vanilla armour shapes from Starlace Cloth (24 for the set): 1 zenithite shard and 2 string give 2 "
         "cloth; 1 Star Fragment and 3 string give 4."),
        ("Armatura stellare",
         "Filo di zenithite che ricorda ancora il cielo notturno: robusta come il ferro, con poteri che crescono con i "
         "pezzi che indossi.\n"
         "- **Luce stellare:** +5% di velocità per pezzo di notte a cielo aperto, o nell'End.\n"
         "- **Caduta stellare:** due pezzi dimezzano i danni da caduta; il set completo trasforma una caduta di oltre 5 "
         "blocchi in un'onda di luce stellare che colpisce i nemici vicini.\n"
         "- **Passo della costellazione** (set completo): salta di nuovo a mezz'aria.\n"
         "- **Stivali:** accovacciati mentre cadi per planare sulla luce stellare.",
         "Si fabbrica con le forme del gioco base dal Tessuto stellare (24 per il set): 1 scheggia di zenithite e 2 fili "
         "danno 2 tessuti; 1 Frammento di stella e 3 fili ne danno 4."),
    ),
    "starlace_bandage": (
        (None,
         "Strips of starlace: what they bind lies still and silent. Use bandages on a creature to wrap it into a "
         "**mummy**: one for peaceful creatures, two for hostile or large ones, three for large hostile ones. A mummy "
         "cannot move, attack or make a sound, never despawns and takes less damage. Shears unwrap it exactly as it was, "
         "and some bandages come back.\n"
         "Players, bosses and other people's pets refuse.",
         "Crafted: 3 Starlace Cloth and 1 string give 3 bandages."),
        (None,
         "Strisce di tessuto stellare: ciò che legano giace immobile e muto. Usa le bende su una creatura per avvolgerla "
         "in una **mummia**: una per le creature pacifiche, due per quelle ostili o grandi, tre per quelle grandi e "
         "ostili. Una mummia non può muoversi, attaccare né fare rumore, non scompare mai e subisce meno danni. Le cesoie "
         "la sbendano esattamente com'era, e alcune bende tornano indietro.\n"
         "Giocatori, boss e animali di altri giocatori rifiutano.",
         "Si fabbrica: 3 Tessuti stellari e 1 filo danno 3 bende."),
    ),
    "fractured_pearl": (
        (None,
         "Two halves that never stopped looking for each other. Use the pearl to split it into a linked pair:\n"
         "- **Arrival Shard:** throw it; where it lands it anchors as a floating crystal (break the anchor to get the "
         "shard back).\n"
         "- **Departure Shard:** use it to travel to that anchor, from any dimension, but never into or out of the "
         "Forgotten Land.\n"
         "Give one half to a friend. Both shards are mended with ender pearls.",
         "Crafted: an ender pearl among 4 rubies and 4 divine glass shards (DRD / RPR / DRD). The Last Merchant sells "
         "them for 10 rubies."),
        (None,
         "Due metà che non hanno mai smesso di cercarsi. Usa la perla per dividerla in una coppia collegata:\n"
         "- **Scheggia d'arrivo:** lanciala; dove atterra si ancora come un cristallo fluttuante (rompi l'ancora per "
         "riavere la scheggia).\n"
         "- **Scheggia di partenza:** usala per raggiungere quell'ancora, da qualunque dimensione, ma mai per entrare o "
         "uscire dalla Terra Dimenticata.\n"
         "Regala una metà a un amico. Entrambe le schegge si riparano con perle di Ender.",
         "Si fabbrica: una perla di Ender tra 4 rubini e 4 frammenti di vetro divino (DRD / RPR / DRD). L'Ultimo "
         "mercante le vende per 10 rubini."),
    ),
    "comically_large_bag": (
        (None,
         "Bigger on the outside. Mostly. Empty, use it on a creature, a minecart, a boat or an armour stand to stuff it "
         "inside; sneak + use on a block to bag it, contents and all. Full, use it on a block face to let it out exactly "
         "as it went in: the very same creature, chest or furnace.\n"
         "With PvP on, it carries off a player for 15 seconds. Bosses, two-block blocks and too much nested loot won't "
         "fit, and a full bag cannot be destroyed.",
         "Crafted: 2 string, 5 leather, an ender pearl and a lead (SLS / LPL / LCL). Also 2% of village mint strongboxes."),
        (None,
         "Più grande fuori. Quasi. Vuoto, usalo su una creatura, un carrello, una barca o un supporto per armature per "
         "infilarli dentro; accovacciati + usa su un blocco per insaccarlo con tutto il contenuto. Pieno, usalo sulla "
         "faccia di un blocco per farne uscire il contenuto esattamente com'era: la stessa creatura, lo stesso forziere o "
         "fornace.\n"
         "Con il PvP attivo, si porta via un giocatore per 15 secondi. Boss, blocchi alti due e troppo bottino annidato "
         "non entrano, e un sacco pieno non si può distruggere.",
         "Si fabbrica: 2 fili, 5 pelli, una perla di Ender e un guinzaglio (SLS / LPL / LCL). Anche nel 2% delle "
         "casseforti delle zecche dei villaggi."),
    ),
    "chrono_hourglass": (
        (None,
         "Every grain is a borrowed second. Feed this two-block hourglass Chronal Shards: 60 seconds of sand each, up to "
         "10 minutes (sneak to pour the whole stack). While the sand runs, everything within 4 blocks lives **four times "
         "faster**: furnaces, crops, saplings, young animals. Water and lava flow twice as fast, and players there heal "
         "twice as fast.\n"
         "A redstone signal holds the sand; a comparator reads the time left. Redstone timing, spawners and portals are "
         "never sped up.",
         "Crafted: 4 gold ingots, 2 chronal shards, 2 glass and sand (GCG / #S# / GCG)."),
        (None,
         "Ogni granello è un secondo preso in prestito. Nutri questa clessidra alta due blocchi con Schegge cronali: 60 "
         "secondi di sabbia ciascuna, fino a 10 minuti (accovacciati per versare tutta la pila). Finché la sabbia scorre, "
         "tutto entro 4 blocchi vive **quattro volte più in fretta**: fornaci, coltivazioni, arboscelli, cuccioli. Acqua "
         "e lava scorrono al doppio, e lì i giocatori guariscono al doppio.\n"
         "Un segnale di pietrarossa trattiene la sabbia; un comparatore legge il tempo rimasto. Temporizzazioni di "
         "pietrarossa, generatori e portali non accelerano mai.",
         "Si fabbrica: 4 lingotti d'oro, 2 schegge cronali, 2 vetri e della sabbia (GCG / #S# / GCG)."),
    ),
    "clockwork_decoy": (
        (None,
         "Throw it: it unfolds into a brass mannequin, and every monster within 16 blocks that was after you (or after "
         "nothing at all) chases it instead for 15 seconds. Piglins, hoglins and wardens are not fooled.",
         "Crafted (shapeless): 3 Clockwork Gears, a redstone dust and a white wool."),
        (None,
         "Lanciala: si apre in un manichino d'ottone, e ogni mostro entro 16 blocchi che ti inseguiva (o non inseguiva "
         "nessuno) dà la caccia a lei per 15 secondi. Piglin, hoglin e sorveglianti non ci cascano.",
         "Si fabbrica (senza forma): 3 Ingranaggi a orologeria, una polvere di pietrarossa e una lana bianca."),
    ),
    "repair_kit": (
        (None,
         "Hold a damaged item in your other hand and use the kit: it restores a quarter of the item's durability. Totems "
         "wear out for good and refuse it.",
         "Crafted: 2 Umbral Steel ingots, a Clockwork Gear, 2 leather and an iron ingot (UGU / LIL). The Smiths' Guild "
         "sells them too."),
        (None,
         "Tieni un oggetto danneggiato nell'altra mano e usa il kit: ripristina un quarto della sua durabilità. I totem "
         "si consumano per sempre e lo rifiutano.",
         "Si fabbrica: 2 lingotti di acciaio umbrale, un Ingranaggio a orologeria, 2 pelli e un lingotto di ferro "
         "(UGU / LIL). Li vende anche la Gilda dei fabbri."),
    ),
    "wayfarers_chart": (
        (None,
         "Wherever you wander, it remembers the Overworld. Hold it like a map: a live chart of the Overworld around you "
         "that scrolls as you walk, with markers for the world spawn, your bed and where your banishment began. From the "
         "Nether or the Forgotten Land it shows the Overworld under your **translated position** (×8, ×12).\n"
         "Land seen lately is inked in full colour; the rest is sketched faintly. Sneak + use to change the scale, from 1 "
         "to 16 blocks per pixel.",
         "Crafted: a Divine Glass Shard over an empty map between 2 rubies, above a compass (.D. / RMR / .C.)."),
        (None,
         "Ovunque tu vada, ricorda il Mondo normale. Tienila come una mappa: una carta viva del Mondo normale intorno a "
         "te che scorre mentre cammini, con i segni del punto di rinascita del mondo, del tuo letto e del luogo da cui è "
         "iniziato il tuo esilio. Dal Nether o dalla Terra Dimenticata mostra il Mondo normale sotto la tua **posizione "
         "tradotta** (×8, ×12).\n"
         "La terra vista di recente è tracciata a pieni colori; il resto è abbozzato a tinte tenui. Accovacciati + usa per "
         "cambiare la scala, da 1 a 16 blocchi per pixel.",
         "Si fabbrica: un Frammento di vetro divino sopra una mappa vuota tra 2 rubini, sopra una bussola "
         "(.D. / RMR / .C.)."),
    ),

    # ============================================================================================ materials & ores
    "ruby": (
        (None,
         "A gem the mountains cried, and the coin of everything forgotten: the Last Merchant takes nothing else. Rubies "
         "also grip every Umbral Steel tool, frame Forgotten Portals and go into this Almanac, the Wayfarer's Chart, the "
         "Fractured Pearl and the Totem of Banishing. Nine make a Block of Ruby.",
         "Ruby ore deep underground (Y -64 to 16, like diamonds; iron pickaxe, Fortune helps). The Last Merchant pays "
         "rubies for the ores you mine; Gemcutters, obelisks, ruin caches, bell towers, hoards and mints have some too."),
        (None,
         "Una gemma pianta dalle montagne, e la moneta di tutto ciò che è dimenticato: l'Ultimo mercante non accetta "
         "altro. I rubini sono anche nell'impugnatura di ogni attrezzo di acciaio umbrale, nelle cornici dei Portali "
         "dimenticati, in questo almanacco, nella Carta del viandante, nella Perla fratturata e nel Totem dell'esilio. "
         "Nove fanno un Blocco di rubino.",
         "Minerale di rubino in profondità (Y da -64 a 16, come i diamanti; piccone di ferro, Fortuna aiuta). L'Ultimo "
         "mercante paga in rubini i minerali che scavi; ne hanno anche i tagliagemme, gli obelischi, i forzieri delle "
         "rovine, le torri, i tesori e le zecche."),
    ),
    "umbral_steel": (
        ("Umbral Steel",
         "A dark, violet-veined steel that grows heavier in the dark. Smelt or blast the raw steel into ingots; nine "
         "nuggets make an ingot, nine ingots a block. It makes Umbral Steel tools and Repair Kits, and mends the "
         "Eclipser, the Gravemaw Greatsword, the Null Lantern and the Pocket Watch.",
         "Umbral Steel ore below Y 0 in the Overworld (iron pickaxe). Spiders and cave spiders drop a nugget 5% of the "
         "time (player kills, +Looting). Ingots: the Last Merchant (4 rubies) and the Smiths' Guild."),
        ("Acciaio umbrale",
         "Un acciaio scuro venato di viola che si fa più pesante al buio. Fondi o raffina l'acciaio grezzo in lingotti; "
         "nove pepite fanno un lingotto, nove lingotti un blocco. Serve per gli attrezzi di acciaio umbrale e i Kit di "
         "riparazione, e ripara l'Eclissatore, lo Spadone Fauce-di-tomba, la Lanterna nulla e l'Orologio.",
         "Minerale di acciaio umbrale sotto Y 0 nel Mondo normale (piccone di ferro). Ragni e ragni delle grotte "
         "lasciano una pepita il 5% delle volte (uccisi da un giocatore, +Saccheggio). Lingotti: l'Ultimo mercante (4 "
         "rubini) e la Gilda dei fabbri."),
    ),
    "divine_glass": (
        ("Divine Glass",
         "Glass still warm with old light. Four shards make a block of **Divine Glass**: it glows softly and is a window "
         "into the stone. Look through it from close by and the stone behind falls away: only ores, chests, spawners "
         "and creatures remain. Six blocks make sixteen panes. Shards also mend the Magnifying Glass and the Mirror.",
         "Divine glass clusters grow only in the Forgotten Land (1 shard, +Fortune; Silk Touch keeps the cluster). Also "
         "ruin caches, bell towers, hoards and obelisks, the Last Merchant (4 rubies) and the Gemcutters' Guild (5 coins)."),
        ("Vetro divino",
         "Vetro ancora caldo di luce antica. Quattro frammenti fanno un blocco di **Vetro divino**: brilla piano ed è "
         "una finestra nella pietra. Guardaci attraverso da vicino e la pietra dietro svanisce: restano solo minerali, "
         "forzieri, generatori e creature. Sei blocchi fanno sedici pannelli. I frammenti riparano anche la Lente e lo "
         "Specchio.",
         "I grappoli di vetro divino crescono solo nella Terra Dimenticata (1 frammento, +Fortuna; con Tocco di velluto "
         "resta il grappolo). Anche forzieri delle rovine, torri, tesori e obelischi, l'Ultimo mercante (4 rubini) e la "
         "Gilda dei tagliagemme (5 monete)."),
    ),
    "zenithite": (
        ("Zenithite",
         "A sky-blue crystal found only where waterfalls run upward. Spun with string it becomes Starlace Cloth, and it "
         "mends the Echo Dagger. Nine shards make a Block of Zenithite.",
         "Zenithite ore on the floating islands of the Inverted Falls (1-2 shards, +Fortune). The Last Merchant sells 2 "
         "for 3 rubies; the Gemcutters' Guild, one for 6 coins."),
        ("Zenithite",
         "Un cristallo azzurro cielo che si trova solo dove le cascate salgono. Filato con lo spago diventa Tessuto "
         "stellare, e ripara il Pugnale dell'eco. Nove schegge fanno un Blocco di zenithite.",
         "Minerale di zenithite sulle isole fluttuanti delle Cascate rovesciate (1-2 schegge, +Fortuna). L'Ultimo "
         "mercante ne vende 2 per 3 rubini; la Gilda dei tagliagemme, una per 6 monete."),
    ),
    "exilite": (
        ("Exilite",
         "The buried stone the Totem of Banishing was carved from. Smelt the debris into a shard; 4 shards and 4 gold "
         "ingots make an **Exilite Ingot**, which never burns and pays a beacon. One ingot between two crying obsidian "
         "makes two Forgotten Portal Frames: a way home built from the land itself. Nine ingots make a Block of Exilite.",
         "Exilite Debris, in small veins hidden in the stone below Y -8 of the Forgotten Land, about one every four chunks "
         "(diamond pickaxe; no explosion breaks it). Shards turn up in ruin caches too."),
        ("Esilite",
         "La pietra sepolta da cui fu scolpito il Totem dell'esilio. Fondi i detriti in un frammento; 4 frammenti e 4 "
         "lingotti d'oro fanno un **Lingotto di esilite**, che non brucia mai e paga un faro. Un lingotto tra due "
         "ossidiane piangenti fa due Cornici del portale dimenticato: una via di casa costruita con la terra stessa. Nove "
         "lingotti fanno un Blocco di esilite.",
         "Detriti di esilite, in piccole vene nascoste nella pietra sotto Y -8 della Terra Dimenticata, circa una ogni "
         "quattro chunk (piccone di diamante; nessuna esplosione li rompe). I frammenti si trovano anche nei forzieri "
         "delle rovine."),
    ),
    "star_rock": (
        ("Star Rock & Fragments",
         "A fallen star leaves a block that cools in three stages; Silk Touch keeps each one.\n"
         "- **Molten Star Rock:** burns to stand on; water or rain cool it at once, or it takes a minute or two. Mined: 1-2 "
         "Star Fragments, 3% a Solar Flare Fragment.\n"
         "- **Prime Star Rock:** glitters and chimes at night. Mined: 1-3 fragments (+Fortune) and, 1% of the time "
         "without Silk Touch, a Lacunar Quintessence. It burns out after about three days.\n"
         "- **Extinguished:** grey moon rock, at most one fragment.\n"
         "Star Fragments mend the Starcaller Trumpet and weave Starlace Cloth.",
         "Wherever a star fell: natural falling stars on clear nights, or the ones you call with the Starcaller Trumpet."),
        ("Roccia stellare e frammenti",
         "Una stella caduta lascia un blocco che si raffredda in tre stadi; Tocco di velluto li conserva tutti.\n"
         "- **Roccia stellare fusa:** brucia se ci stai sopra; acqua o pioggia la raffreddano subito, altrimenti ci "
         "vogliono un paio di minuti. Scavata: 1-2 Frammenti di stella, il 3% un Frammento di brillamento solare.\n"
         "- **Roccia stellare viva:** scintilla e tintinna di notte. Scavata: 1-3 frammenti (+Fortuna) e, l'1% delle "
         "volte senza Tocco di velluto, una Quintessenza lacunare. Si spegne dopo circa tre giorni.\n"
         "- **Spenta:** grigia roccia lunare, al massimo un frammento.\n"
         "I Frammenti di stella riparano la Tromba Richiamastelle e tessono il Tessuto stellare.",
         "Dove è caduta una stella: le stelle cadenti naturali nelle notti serene, o quelle che chiami con la Tromba "
         "Richiamastelle."),
    ),
    "solar_flare": (
        (None,
         "Four fragments of a lost noon, made whole around a block of Divine Glass. On its own a Solar Flare does nothing: "
         "the Last Merchant sets it into **Sun's Fury** for 32 rubies, at every visit.",
         "Crafted: 4 Solar Flare Fragments around Divine Glass. Fragments: Mirror Hoards 10%, Molten Star Rock 3%, ruin "
         "caches 1.5%, and the Last Merchant in 8% of his visits (16 rubies and 8 divine glass shards)."),
        (None,
         "Quattro frammenti di un mezzogiorno perduto, di nuovo interi attorno a un blocco di Vetro divino. Da solo un "
         "Brillamento solare non fa nulla: l'Ultimo mercante ne forgia la **Furia del Sole** per 32 rubini, a ogni "
         "visita.",
         "Si fabbrica: 4 Frammenti di brillamento solare attorno a un Vetro divino. Frammenti: tesori del Labirinto degli "
         "specchi 10%, Roccia stellare fusa 3%, forzieri delle rovine 1,5%, e l'Ultimo mercante nell'8% delle visite (16 "
         "rubini e 8 frammenti di vetro divino)."),
    ),
    "chronal_shard": (
        (None,
         "It ticks when nobody listens. It runs the Chrono Hourglass (60 seconds of sand each) and, set into the "
         "Chronoshard Pocket Watch, shortens its cooldown.",
         "Buried watches in the Clockwork Dunes (1-2 each); husks 4% (player kills, +Looting); ruin caches, bell towers, "
         "hoards and obelisks; the Last Merchant (6 rubies) and the Scholars' Guild (8 coins)."),
        (None,
         "Ticchetta quando nessuno ascolta. Fa scorrere la Clessidra cronale (60 secondi di sabbia ciascuna) e, inserita "
         "nell'Orologio da taschino, ne accorcia la ricarica.",
         "Orologi sepolti nelle Dune a orologeria (1-2 ciascuno); husk 4% (uccisi da un giocatore, +Saccheggio); forzieri "
         "delle rovine, torri, tesori e obelischi; l'Ultimo mercante (6 rubini) e la Gilda degli studiosi (8 monete)."),
    ),
    "echo_fragment": (
        (None,
         "A memory that refused to fade. Each Echo called by the Whisperbone Flute leaves one behind as it fades, and "
         "each mends the flute by 20 uses at an anvil.",
         "The Echoes of the Whisperbone Flute; Mirror Hoards."),
        (None,
         "Un ricordo che ha rifiutato di svanire. Ogni Eco richiamata dal Flauto d'ossosussurro ne lascia uno quando "
         "svanisce, e ciascuno ripara il flauto di 20 utilizzi all'incudine.",
         "Le Eco del Flauto d'ossosussurro; i tesori del Labirinto degli specchi."),
    ),
    "ember": (
        (None,
         "Burns for a very long time: one Ember smelts eight items. It also mends the Ember Triangle, 4 uses each.",
         "Cindervine leaves drop it 5% of the time; the Last Merchant sells 4 for a ruby."),
        (None,
         "Brucia molto a lungo: una Brace cuoce otto oggetti. Ripara anche il Triangolo di brace, 4 utilizzi ciascuna.",
         "Le foglie di Vitecenere la lasciano il 5% delle volte; l'Ultimo mercante ne vende 4 per un rubino."),
    ),
    "crystal_willow_shard": (
        (None,
         "Glassy splinters of the Crystal Willow. Four of them make a block of plain glass, no furnace needed.",
         "Crystal Willow leaves drop them 5% of the time."),
        (None,
         "Schegge vitree del Salice di cristallo. Quattro fanno un blocco di vetro comune, senza fornace.",
         "Le foglie del Salice di cristallo le lasciano il 5% delle volte."),
    ),
    "silk_dust": (
        (None,
         "Right-click a surface to leave a soft glowing mark (light 9) that fades after three minutes. It also mends the "
         "Wand of Quickening.",
         "Lantern Moths drop 0-2 (+Looting)."),
        (None,
         "Clic destro su una superficie per lasciare un segno che brilla piano (luce 9) e svanisce dopo tre minuti. "
         "Ripara anche la Bacchetta del Risveglio.",
         "Le Falene lanterna ne lasciano 0-2 (+Saccheggio)."),
    ),
    "clockwork_gear": (
        (None,
         "Still turning, a little. Three make a Clockwork Decoy, and one goes into every Repair Kit.",
         "Husks drop 0-2 and pillagers 0-1 (+Looting)."),
        (None,
         "Gira ancora, un poco. Tre fanno un'Esca a orologeria, e uno entra in ogni Kit di riparazione.",
         "Gli husk ne lasciano 0-2 e i predoni 0-1 (+Saccheggio)."),
    ),

    # ============================================================================================ blocks & building
    "cindervine": (
        ("Cindervine",
         "Charcoal-black wood split by ember-orange cracks that glow faintly. It never burns, yet its planks are good fuel; "
         "its leaves smoulder and drop Ember. A complete wood family: planks, stairs, slabs, fences, gates, doors, "
         "trapdoors, buttons and pressure plates.",
         "Savannas, badlands and windswept hills; bare spires in the Cinderfrost Flats. Saplings from the leaves."),
        ("Vitecenere",
         "Legno nero come il carbone, spaccato da crepe color brace che brillano appena. Non brucia mai, eppure le sue "
         "assi sono un buon combustibile; le foglie covano il fuoco e lasciano Brace. Una famiglia di legno completa: "
         "assi, scale, lastre, staccionate, cancelli, porte, botole, pulsanti e placche a pressione.",
         "Savane, badlands e colline ventose; guglie spoglie nelle Distese di cenere gelata. Arboscelli dalle foglie."),
    ),
    "crystal_willow": (
        ("Crystal Willow",
         "Translucent, pale green wood that glows softly, glassy leaves and hanging strands that sway beneath them. Its "
         "leaves drop Crystal Willow Shards. A complete wood family.",
         "Cherry groves and meadows (rare); the floating islands and the ground of the Inverted Falls."),
        ("Salice di cristallo",
         "Legno traslucido verde pallido che brilla piano, foglie vitree e fili pendenti che ondeggiano sotto di esse. Le "
         "foglie lasciano Schegge di salice di cristallo. Una famiglia di legno completa.",
         "Boschi di ciliegi e prati (rari); le isole fluttuanti e il suolo delle Cascate rovesciate."),
    ),
    "tidebloom": (
        ("Tidebloom",
         "A red and teal mangrove standing on tangled roots. Tidebloom Roots splash when broken, and now and then a fish "
         "appears in the water beside them. A complete wood family.",
         "Swamps, mangrove swamps and rivers; the Whispering Marsh."),
        ("Fiordimarea",
         "Una mangrovia rossa e verde acqua ritta su radici intrecciate. Le Radici di fiordimarea schizzano quando si "
         "rompono, e ogni tanto nell'acqua accanto compare un pesce. Una famiglia di legno completa.",
         "Paludi, paludi di mangrovie e fiumi; la Palude sussurrante."),
    ),
    "echo_birch": (
        ("Echo Birch",
         "Pale ivory bark traced with faint blue runes; chopping it chimes. Plant two saplings within 4 blocks of each "
         "other and they grow together, joined by a bridge of leaves. A complete wood family.",
         "Birch forests; groves in the Forgotten Plains, a few trees in the Clockwork Dunes."),
        ("Betulla dell'eco",
         "Corteccia color avorio segnata da deboli rune azzurre; tagliarla tintinna. Pianta due arboscelli a non più di 4 "
         "blocchi l'uno dall'altro e crescono insieme, uniti da un ponte di foglie. Una famiglia di legno completa.",
         "Foreste di betulle; boschetti nelle Pianure dimenticate, qualche albero nelle Dune a orologeria."),
    ),
    "exilite_stonework": (
        ("Exilite Stonework",
         "A pale, lichen-flecked stone family: Exilite, Polished Exilite, Exilite Bricks with their stairs, slab and wall, "
         "and Chiseled Exilite, carved with the totem's open hand. Cut them at a crafting table or a stonecutter.",
         "One Exilite Shard in a ring of eight calcite gives eight Exilite."),
        ("Opere in esilite",
         "Una famiglia di pietra pallida picchiettata di licheni: Esilite, Esilite levigata, Mattoni di esilite con "
         "scale, lastra e muretto, ed Esilite cesellata, che porta incisa la mano aperta del totem. Si lavorano al banco "
         "da lavoro o al tagliapietre.",
         "Un frammento di esilite in un anello di otto calciti dà otto Esilite."),
    ),

    # ============================================================================================ villages
    "civic_villages": (
        ("Civic Villages",
         "Villages remember how you treat them. Each one keeps your **reputation**, two laws, a council that sets a "
         "weekly policy and a coin of its own, and newly generated villages grow a civic square with a village hall, a "
         "mint, a guild hall and a schoolhouse.\n"
         "Three new villagers find work there: the **Coiner**, the **Magistrate** and the **Guildmaster**.",
         "Every vanilla village; the civic square only in newly generated ones. A Village Ledger can found one anywhere."),
        ("Villaggi civici",
         "I villaggi ricordano come li tratti. Ognuno conserva la tua **reputazione**, due leggi, un consiglio che "
         "decide una politica settimanale e una moneta tutta sua, e i villaggi appena generati hanno una piazza civica "
         "con municipio, zecca, sala delle gilde e scuola.\n"
         "Tre nuovi villici vi trovano lavoro: il **Coniatore**, il **Magistrato** e il **Maestro di gilda**.",
         "Ogni villaggio del gioco base; la piazza civica solo in quelli appena generati. Un Registro del villaggio può "
         "fondarne uno ovunque."),
    ),
    "village_ledger": (
        (None,
         "The village's memory. Right-click it for its screen: your standing and prices, the laws, the council and "
         "today's record of what you did. Placed outside every village, it **founds** a village of its own around "
         "itself, and the villagers there start living by the civic rules.",
         "In every village hall. Crafted (shapeless): a lectern, a book and quill and an emerald."),
        (None,
         "La memoria del villaggio. Clic destro per aprirne la schermata: la tua posizione e i prezzi, le leggi, il "
         "consiglio e il registro di ciò che hai fatto oggi. Posato fuori da ogni villaggio, ne **fonda** uno tutto suo, "
         "e i villici attorno iniziano a vivere secondo le regole civiche.",
         "In ogni municipio. Si fabbrica (senza forma): un leggio, un libro e penna e uno smeraldo."),
    ),
    "reputation": (
        ("Reputation",
         "From -100 to 100 in each village: Hostile, Wary, Neutral, Friendly (10+) and Honored (50+). Each point is 0.25% "
         "off (or on) your villager prices.\n"
         "- **Raises it:** trading (+1, twice a day), slaying monsters in the village (+3), ringing its bell at dawn (+2), "
         "guild contracts (+3), paying fines.\n"
         "- **Lowers it:** hurting (-10) or killing (-20) a villager, breaking protected blocks, opening the villagers' "
         "containers or harvesting their crops (both free once Honored), breaking the laws.\n"
         "Hostile visitors are attacked by the village's golems.",
         "On the Village Ledger and every civic screen; each change flashes above your hotbar."),
        ("Reputazione",
         "Da -100 a 100 in ogni villaggio: Ostile, Diffidente, Neutrale, Amichevole (10+) e Onorato (50+). Ogni punto "
         "toglie (o aggiunge) lo 0,25% ai prezzi dei villici.\n"
         "- **La alzano:** commerciare (+1, due volte al giorno), uccidere mostri nel villaggio (+3), suonare la sua "
         "campana all'alba (+2), i contratti di gilda (+3), pagare le multe.\n"
         "- **La abbassano:** ferire (-10) o uccidere (-20) un villico, rompere blocchi protetti, aprire i contenitori dei "
         "villici o raccoglierne i raccolti (entrambi liberi da Onorato), infrangere le leggi.\n"
         "I visitatori Ostili vengono attaccati dai golem del villaggio.",
         "Sul Registro del villaggio e in ogni schermata civica; ogni cambiamento compare sopra la barra rapida."),
    ),
    "laws": (
        ("Laws & the Bell",
         "Each village keeps two of four laws:\n"
         "- No drawn weapons inside the village.\n"
         "- Curfew: strangers indoors after dusk (friends of the village may walk).\n"
         "- No breaking blocks inside the village.\n"
         "- The bell rings only at dawn.\n"
         "A warning comes first; then every few seconds of law-breaking costs reputation, and a guard walks over. Every "
         "village likes its bell rung at dawn, but not more than three times a day.",
         "The Laws tab of the Village Ledger names this village's two laws."),
        ("Leggi e campana",
         "Ogni villaggio osserva due leggi su quattro:\n"
         "- Niente armi sguainate nel villaggio.\n"
         "- Coprifuoco: gli stranieri al chiuso dopo il tramonto (gli amici del villaggio possono girare).\n"
         "- Vietato rompere blocchi nel villaggio.\n"
         "- La campana suona solo all'alba.\n"
         "Prima arriva un avvertimento; poi ogni pochi secondi di infrazione costano reputazione, e una guardia si "
         "avvicina. Ogni villaggio gradisce la campana suonata all'alba, ma non più di tre volte al giorno.",
         "La scheda Leggi del Registro del villaggio indica le due leggi di questo villaggio."),
    ),
    "council": (
        ("Council & Policies",
         "Every seven days at dawn the council meets and sets a policy for the week, swayed by how well-liked its "
         "visitors were:\n"
         "- **Free Market:** villager prices -10%.\n"
         "- **Austerity:** prices +10%.\n"
         "- **Festival:** reputation gains doubled, fireworks over the square.\n"
         "- **Martial Law:** golems hit 50% harder, laws bite twice as fast.\n"
         "The Council Table shows the village's mood and the odds of each policy.",
         "The Council Table stands in the village hall. Crafted: 3 carpets over 3 wooden slabs over a log (CCC / SSS / .L.)."),
        ("Consiglio e politiche",
         "Ogni sette giorni all'alba il consiglio si riunisce e decide una politica per la settimana, influenzato da "
         "quanto erano benvoluti i suoi visitatori:\n"
         "- **Libero mercato:** prezzi dei villici -10%.\n"
         "- **Austerità:** prezzi +10%.\n"
         "- **Festa:** guadagni di reputazione raddoppiati, fuochi d'artificio sulla piazza.\n"
         "- **Legge marziale:** i golem colpiscono il 50% più forte, le leggi mordono il doppio più in fretta.\n"
         "Il Tavolo del consiglio mostra l'umore del villaggio e le probabilità di ogni politica.",
         "Il Tavolo del consiglio sta nel municipio. Si fabbrica: 3 tappeti sopra 3 lastre di legno sopra un tronco "
         "(CCC / SSS / .L.)."),
    ),
    "village_coin": (
        ("Coins & the Mint",
         "Every village mints its own coin. The **Coiner**, a villager who works at a Mint Press, turns raw ore and ingots "
         "into coins; coins pay fines, guild fees and the trades of the Magistrate and the Guildmasters.\n"
         "The coin floats between 0.60 and 1.40: every 16 ore minted weakens it by 2%, and it recovers a little each day. "
         "A strong coin buys more.",
         "Coiners' trades, mint strongboxes and guild contracts. Mint Press: 3 iron ingots, a piston and 3 smooth stone "
         "(III / .P. / SSS)."),
        ("Monete e zecca",
         "Ogni villaggio conia la propria moneta. Il **Coniatore**, un villico che lavora a una Pressa della zecca, "
         "trasforma minerali grezzi e lingotti in monete; le monete pagano multe, quote di gilda e gli scambi del "
         "Magistrato e dei Maestri di gilda.\n"
         "La moneta oscilla tra 0,60 e 1,40: ogni 16 minerali coniati la indeboliscono del 2%, e si riprende un poco ogni "
         "giorno. Una moneta forte compra di più.",
         "Gli scambi dei Coniatori, le casseforti delle zecche e i contratti di gilda. Pressa della zecca: 3 lingotti di "
         "ferro, un pistone e 3 pietre lisce (III / .P. / SSS)."),
    ),
    "court": (
        ("Court & Fines",
         "Fallen out with a village? At the **Judge's Bench** you can pay your way back: each village coin restores 5 "
         "reputation, up to 0 (the rest you earn with good deeds). The court hears you once every three days. A "
         "**Magistrate** works there and trades paper, books and name tags for coins.",
         "In the village hall. Judge's Bench: wooden slabs and a book over planks (SBS / PPP / P.P)."),
        ("Tribunale e multe",
         "In rotta con un villaggio? Al **Banco del giudice** puoi pagare per rimediare: ogni moneta del villaggio "
         "restituisce 5 di reputazione, fino a 0 (il resto lo guadagni con le buone azioni). Il tribunale ti ascolta una "
         "volta ogni tre giorni. Lì lavora un **Magistrato**, che scambia carta, libri ed etichette per monete.",
         "Nel municipio. Banco del giudice: lastre di legno e un libro sopra assi (SBS / PPP / P.P)."),
    ),
    "guilds": (
        ("Guilds & Contracts",
         "Three guilds, one oath each: the **Smiths'**, the **Gemcutters'** and the **Scholars'**. Join at a Guild Board "
         "for 20 coins once you are a friend of the village (reputation 10+).\n"
         "Members trade with their guild's **Guildmaster** (Smiths: Umbral Steel and repair kits; Gemcutters: rubies, "
         "divine glass and zenithite; Scholars: chronal shards, echo shards and enchanted books) and take **contracts**: "
         "deliver the goods for coins and +3 reputation, up to three contracts a day.",
         "Guild boards hang in the guild hall. Crafted: sticks and paper around a gray (Smiths), red (Gemcutters) or "
         "purple (Scholars) banner."),
        ("Gilde e contratti",
         "Tre gilde, un giuramento ciascuna: la **Gilda dei fabbri**, quella dei **tagliagemme** e quella degli "
         "**studiosi**. Iscriviti a una Bacheca della gilda per 20 monete quando sei amico del villaggio (reputazione "
         "10+).\n"
         "I membri commerciano con il **Maestro di gilda** della loro gilda (fabbri: acciaio umbrale e kit di "
         "riparazione; tagliagemme: rubini, vetro divino e zenithite; studiosi: schegge cronali, frammenti d'eco e libri "
         "incantati) e accettano **contratti**: consegna la merce per monete e +3 di reputazione, fino a tre contratti "
         "al giorno.",
         "Le bacheche sono appese nella sala delle gilde. Si fabbricano: bastoni e carta attorno a uno stendardo grigio "
         "(fabbri), rosso (tagliagemme) o viola (studiosi)."),
    ),
    "schools": (
        ("Schools",
         "Villagers learn. Every dawn, working villagers within 24 blocks of a School Desk or a lectern gain a lesson's "
         "experience, up to the third level; the rest they learn by trading.",
         "In the schoolhouse. School Desk: 3 wooden slabs over a book between two sticks (SSS / /B/)."),
        ("Scuole",
         "I villici imparano. Ogni alba, i villici con un mestiere entro 24 blocchi da un Banco di scuola o da un leggio "
         "guadagnano l'esperienza di una lezione, fino al terzo livello; il resto lo imparano commerciando.",
         "Nella scuola. Banco di scuola: 3 lastre di legno sopra un libro tra due bastoni (SSS / /B/)."),
    ),

    # ============================================================================================ creatures & events
    "lantern_moth": (
        (None,
         "A small, glowing moth that flutters around bright light: a lamp, a torch or a player holding one (the Null "
         "Lantern does not count). Harmless; when it dies it leaves Silk Dust.",
         "Forests and swamps at night under the open sky, and the Whispering Marsh."),
        (None,
         "Una piccola falena luminosa che svolazza attorno alle luci forti: una lampada, una torcia o un giocatore che ne "
         "tiene una (la Lanterna nulla non conta). Innocua; quando muore lascia Polvere di seta.",
         "Foreste e paludi di notte a cielo aperto, e la Palude sussurrante."),
    ),
    "last_merchant": (
        (None,
         "He remembers every price but his own. He deals **only in rubies**: he buys the ores you mined most since his "
         "last visit (that is how ore turns into rubies), sells shards, ingots, ember and Fractured Pearls, brings **two "
         "random relics**, forges Sun's Fury from a Solar Flare and, one visit in five, sells a Lacunar Quintessence.\n"
         "Kill him near a village and its prices rise.",
         "Near a player in the Overworld, by day: an attempt every 3 days at 25%, about one visit in 12 days. He stays three "
         "days."),
        (None,
         "Ricorda ogni prezzo tranne il proprio. Tratta **solo in rubini**: compra i minerali che hai scavato di più "
         "dalla sua ultima visita (così il minerale diventa rubini), vende schegge, lingotti, brace e Perle fratturate, "
         "porta **due reliquie a caso**, forgia la Furia del Sole da un Brillamento solare e, una visita su cinque, vende "
         "una Quintessenza lacunare.\n"
         "Uccidilo vicino a un villaggio e i suoi prezzi salgono.",
         "Vicino a un giocatore nel Mondo normale, di giorno: un tentativo ogni 3 giorni al 25%, circa una visita ogni 12 "
         "giorni. Resta tre giorni."),
    ),
    "reflection": (
        (None,
         "You, three seconds late. Woken on the ring of a Mirror Labyrinth, it walks exactly the path you walked and hurts "
         "whatever it touches (6 damage; 20 health). It vanishes if you leave, die or log out, and the ring waits for "
         "you again. Defeat it and it always leaves a **Lacunar Quintessence**.",
         "Only in Mirror Labyrinths, one per labyrinth."),
        (None,
         "Tu, con tre secondi di ritardo. Destato sull'anello di un Labirinto degli specchi, ripercorre esattamente i tuoi "
         "passi e ferisce ciò che tocca (6 danni; 20 salute). Svanisce se te ne vai, muori o esci dal gioco, e l'anello "
         "ti riaspetta. Sconfiggilo e lascia sempre una **Quintessenza lacunare**.",
         "Solo nei Labirinti degli specchi, uno per labirinto."),
    ),
    "falling_stars": (
        ("Falling Stars",
         "On clear Overworld nights (never the first) a star sometimes falls near a player, on open natural ground away "
         "from villages and buildings: roughly one night in three. A message says which way it fell, and the rock chimes "
         "softly. It leaves a Molten Star Rock to mine, and one star in five carries a **Lacunar Quintessence**, resting "
         "glowing at the impact.",
         "Look up on clear nights and follow the chime. Stars called with the Starcaller Trumpet never carry one."),
        ("Stelle cadenti",
         "Nelle notti serene del Mondo normale (mai la prima) a volte una stella cade vicino a un giocatore, su terreno "
         "naturale e aperto lontano da villaggi ed edifici: più o meno una notte su tre. Un messaggio dice in che "
         "direzione è caduta, e la roccia tintinna piano. Lascia una Roccia stellare fusa da scavare, e una stella su "
         "cinque porta una **Quintessenza lacunare**, che riposa luminosa nel punto d'impatto.",
         "Guarda in alto nelle notti serene e segui il tintinnio. Le stelle chiamate con la Tromba Richiamastelle non ne "
         "portano mai."),
    ),
    "sunken_bell_tower": (
        ("Sunken Bell Tower",
         "A drowned tower rising from the ocean floor. Its base chest holds prismarine, metals, rubies, divine glass and "
         "one rare roll: a Lacunar Quintessence (12%) or a relic.\n"
         "At the top hangs the **Sunken Bell**: ring it and the nearest ores within 24 blocks glow through the stone for 8 "
         "seconds (once a minute).",
         "Oceans and deep oceans of the Overworld. The bell drops itself and rings wherever you hang it."),
        ("Torre della campana sommersa",
         "Una torre annegata che si alza dal fondale. Il forziere alla base contiene prismarino, metalli, rubini, vetro "
         "divino e un'estrazione rara: una Quintessenza lacunare (12%) o una reliquia.\n"
         "In cima pende la **Campana sommersa**: suonala e i minerali più vicini entro 24 blocchi brillano attraverso la "
         "pietra per 8 secondi (una volta al minuto).",
         "Oceani e oceani profondi del Mondo normale. La campana lascia sé stessa e suona ovunque la appendi."),
    ),
    "obelisk": (
        ("Obelisk of Unreadable Names",
         "A black pillar carved with names that move when you look away. Right-click an inscription to read one of its "
         "lines. Now and then (12%) something falls from a crack: rubies, divine glass shards or a chronal shard, once per "
         "player and obelisk.",
         "Rare, in the Overworld's forests, taigas, savannas, jungles, badlands, hills, plains, deserts and snowy plains; "
         "broken ones stand among the Forgotten Land's ruins."),
        ("Obelisco dei nomi illeggibili",
         "Un pilastro nero inciso di nomi che si muovono quando distogli lo sguardo. Clic destro su un'iscrizione per "
         "leggerne una riga. Ogni tanto (12%) qualcosa cade da una crepa: rubini, frammenti di vetro divino o una "
         "scheggia cronale, una volta per giocatore e obelisco.",
         "Raro, nelle foreste, taighe, savane, giungle, badlands, colline, pianure, deserti e pianure innevate del Mondo "
         "normale; obelischi spezzati si ergono tra le rovine della Terra Dimenticata."),
    ),
    "treasure": (
        ("Bosses & Treasure",
         None,  # generated: the boss and chest relic chances (m19_progression)
         "Boss chances need a player kill; Looting adds 1% per level. The chest chances are per chest."),
        ("Boss e tesori",
         None,
         "Le probabilità dei boss richiedono che sia un giocatore a uccidere; Saccheggio aggiunge l'1% per livello. Quelle "
         "dei forzieri valgono per forziere."),
    ),
    "common_creatures": (
        ("Ordinary Creatures",
         "A few common creatures carry Lacuna's materials:\n"
         "- **Spiders and cave spiders:** an Umbral Steel Nugget, 5%.\n"
         "- **Husks:** 0-2 Clockwork Gears, a Chronal Shard 4%.\n"
         "- **Pillagers:** 0-1 Clockwork Gear.\n"
         "- **Lantern Moths:** 0-2 Silk Dust.\n"
         "The rare drops need a player kill; Looting helps all of them.",
         "Spiders everywhere after dark, husks in deserts, pillagers at outposts and raids."),
        ("Creature comuni",
         "Alcune creature comuni portano i materiali di Lacuna:\n"
         "- **Ragni e ragni delle grotte:** una Pepita di acciaio umbrale, 5%.\n"
         "- **Husk:** 0-2 Ingranaggi a orologeria, una Scheggia cronale 4%.\n"
         "- **Predoni:** 0-1 Ingranaggio a orologeria.\n"
         "- **Falene lanterna:** 0-2 Polvere di seta.\n"
         "I bottini rari richiedono che sia un giocatore a uccidere; Saccheggio aiuta sempre.",
         "Ragni ovunque col buio, husk nei deserti, predoni negli avamposti e nelle incursioni."),
    ),
    "illusioner": (
        ("Illusioner",
         "The evoker's forgotten sibling, robed in starcloth under a pointed hood. It keeps its **bow** and its "
         "**blindness** spell, but its own spell is **duplication**: when it spots you, and every 20-25 seconds, it raises "
         "its arms and **3-4 duplicates** step out of a burst of blue smoke around it; half the time it slips into one of "
         "their places itself. The duplicates strafe and shoot as it does, but their arrows are illusions; **one hit pops "
         "a duplicate**, and they all fade after 15 seconds or when the real one falls.\n"
         "Killed by a player, it drops a **Totem of Confusion** 25% of the time (+5% per Looting level).",
         "Raids: the last waves on normal and hard. Dark forests at night (rare). Some of the mages of woodland mansions."),
        ("Illusionista",
         "Il fratello dimenticato dell'evocatore, avvolto nella stoffa stellata sotto un cappuccio a punta. Tiene il suo "
         "**arco** e l'incantesimo di **cecità**, ma il suo incantesimo è la **duplicazione**: quando ti vede, e ogni 20-25 "
         "secondi, alza le braccia e **3-4 duplicati** escono da uno sbuffo di fumo blu intorno a lui; una volta su due "
         "prende lui stesso il posto di uno di loro. I duplicati si spostano e tirano come lui, ma le loro frecce sono "
         "illusioni; **un colpo dissolve un duplicato**, e svaniscono tutti dopo 15 secondi o quando cade quello vero.\n"
         "Se a ucciderlo è un giocatore, lascia un **Totem della Confusione** il 25% delle volte (+5% per livello di "
         "Saccheggio).",
         "Incursioni: le ultime ondate a difficoltà normale e difficile. Foreste scure di notte (raro). Alcuni dei maghi "
         "delle magioni della foresta."),
    ),
}
