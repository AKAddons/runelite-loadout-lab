# multi_mobs.json - the mobs fought in multi-combat areas

Generated 2026-10-19 for the multi-combat card (Mural DES-01M5B2AGKDBE6G3J3WR06TBSW2).
The file lists corpus monsters (name + version, matching monsters.json.gz) that a
typical player fights in a multi-combat area, with the locations. Nothing in the
engine reads it yet.

## Sources, in the order they decide

1. The wiki location table on each monster page (every LocLine row, with its spawn tiles).
2. The crowdsourced multicombat varbit tiles the wiki collected (varbit 4605, served by
   chisel.weirdgloop.org/varbs/mapdata, value 1 = multi, value 0 = single): each spawn tile
   votes by its nearest observed tile within six tiles on the same plane; a location is multi
   when the multi votes outnumber the single votes.
3. Where no tiles were observed near a location, the area's own wiki page statement
   ("the entire dungeon is a multicombat area", "it is not a multi-combat area") or the
   wiki's Multicombat area list page. The source field on each location says which.
4. Anything else is unknown and left out (listed below for review).

The Deadman Annihilation multicombat polygon module on the wiki was checked and rejected:
it is the Deadman map and marks the Catacombs single.

## Inclusion rule

- At least one location decides multi, and that location must hold a crowd: three or more spawns in the wiki table (Andrew 2026-10-19: a boss standing alone in a multi area has no multi capability; the GWD generals and their bodyguards, the Dagannoth Kings, the Kalphite Queen, the Giant Mole and the wilderness bosses drop out on this rule).
- Combat level 50 and up (93 lower-level entries dropped: cows, chickens, bats, rats, goblins and the like).
- No quest-only variants (Temple Trekking, Vault of War, Melzar's Maze, Zemouregal's Base and Fort, The Grand Tree, The Scar).
- No instanced boss fights even when the lobby is multi (Abyssal Sire, Kraken, Zalcano, Bryophyta, Obor, Scurrius, Skotizo, Sarachnis, Hespori, Mimic, Jad, Zuk, Damien Leucurte).
- Revenants are singles-plus and out.
- Entries are keyed the way the Java loader sees the corpus (stat-identical location clones collapse into one row with a blank version): a level version (Level 95) is listed when a multi location's level range includes it; a named version (GWD, Wilderness) when its name appears in a multi location; otherwise the blank row; when none of those apply, every version of the name (the spiritual creatures in GWD).

## Surprises worth knowing

- Chasm of Fire is single-way by its own page (the greater demons there are out).
- Stronghold Slayer Cave voted single on every observed tile (bloodvelds, fire giants, hellhounds).
- Brutal dragons sit in the Catacombs' single-way pocket (page and tiles agree).
- Jormungand's Prison: the dagannoth are multi, the basilisks single (page).
- Smoke Dungeon: only the western dust devil area is multi (page).

## Included (151 entries, 96 names)

| Monster | Version | Locations |
|---|---|---|
| Abyssal demon | - | Catacombs of Kourend; Wilderness Slayer Cave (page) |
| Ammonite Crab | - | Mushroom Forest; Fossil Island Volcano |
| Ankou | Level 95 | Catacombs of Kourend; Wilderness Slayer Cave |
| Ankou | Level 98 | Catacombs of Kourend; Wilderness Slayer Cave |
| Araxyte | Level 146 | Morytania Spider Cave (page); Morytania Spider Cave (task-only area) (page) |
| Araxyte | Level 96 | Morytania Spider Cave (page); Morytania Spider Cave (task-only area) (page) |
| Aviansie | Level 131 | God Wars Dungeon (page); Wilderness God Wars Dungeon (page) |
| Aviansie | Level 148 | God Wars Dungeon (page); Wilderness God Wars Dungeon (page) |
| Aviansie | Level 79 (2) | God Wars Dungeon (page); Wilderness God Wars Dungeon (page) |
| Aviansie | Level 84 | God Wars Dungeon (page); Wilderness God Wars Dungeon (page) |
| Aviansie | Level 97 (1) | God Wars Dungeon (page); Wilderness God Wars Dungeon (page) |
| Aviansie | Level 97 (2) | God Wars Dungeon (page); Wilderness God Wars Dungeon (page) |
| Baby black dragon | Myths' Guild | Charred Dungeon (page) |
| Baby black dragon | Normal | Charred Dungeon (page) |
| Bandit | Level 130 | Bandit Camp (Wilderness) |
| Black demon | Level 178 | Catacombs of Kourend; Charred Dungeon (page); Wilderness Slayer Cave |
| Black demon | Level 184 | Catacombs of Kourend; Charred Dungeon (page); Wilderness Slayer Cave |
| Black demon | Level 188 | Catacombs of Kourend; Charred Dungeon (page); Wilderness Slayer Cave |
| Black dragon | Level 227 | Charred Dungeon (page); Wilderness Slayer Cave |
| Black dragon | Level 247 | Charred Dungeon (page); Wilderness Slayer Cave |
| Bloodveld | - | God Wars Dungeon (page); Meiyerditch Laboratories; Wilderness God Wars Dungeon (page) |
| Bronze dragon | Catacombs of Kourend | Catacombs of Kourend |
| Cyclops (Catacombs of Kourend) | Level 56 | Catacombs of Kourend |
| Cyclops (Catacombs of Kourend) | Level 76 | Catacombs of Kourend |
| Cyclops (God Wars Dungeon) | - | God Wars Dungeon (page) |
| Dagannoth | Level 74 (1) | Jormungand's Prison; Catacombs of Kourend; Lighthouse |
| Dagannoth | Level 92 (1) | Jormungand's Prison; Catacombs of Kourend; Lighthouse |
| Dagannoth (Waterbirth Island) | Level 88 | Waterbirth Island Dungeon (Area before Dagannoth Kings) (page); Waterbirth Island Dungeon (Sub Levels) (page) |
| Dagannoth (Waterbirth Island) | Level 90 | Waterbirth Island Dungeon (Area before Dagannoth Kings) (page); Waterbirth Island Dungeon (Sub Levels) (page) |
| Dagannoth fledgeling | - | Waterbirth Island Dungeon (page) |
| Dark warrior | Level 145 | Dark Warriors' Fortress (page); Dark Warriors' Fortress (floor 1) (page); Woods between Lovakengj and Arceuus |
| Dark warrior | Level 51 | Dark Warriors' Fortress (page); Dark Warriors' Fortress (floor 1) (page); Woods between Lovakengj and Arceuus |
| Dark warrior | Level 62 | Dark Warriors' Fortress (page); Dark Warriors' Fortress (floor 1) (page); Woods between Lovakengj and Arceuus |
| Death wing | - | Shaman Caves |
| Demonic gorilla | - | Crash Site Cavern |
| Deviant spectre | - | Catacombs of Kourend |
| Dust devil | - | Wilderness Slayer Cave (page); Catacombs of Kourend |
| Earthen Nagua | Normal | Tonali Cavern - Southern Cavern (middle floor) (page); Tonali Cavern - Southern Cavern (bottom floor) (page); Tonali Cavern - Sun Chamber (page) |
| Earthen Nagua | Reinforced | Tonali Cavern - Southern Cavern (middle floor) (page); Tonali Cavern - Southern Cavern (bottom floor) (page); Tonali Cavern - Sun Chamber (page) |
| Earthen Nagua | Weakened | Tonali Cavern - Southern Cavern (middle floor) (page); Tonali Cavern - Southern Cavern (bottom floor) (page); Tonali Cavern - Sun Chamber (page) |
| Elder Chaos druid | - | Chaos Temple (Wilderness) |
| Ent | Wilderness | North of Chaos Temple (Wilderness); Woodcutting Guild Ent Dungeon (page) |
| Ent | Woodcutting Guild | North of Chaos Temple (Wilderness); Woodcutting Guild Ent Dungeon (page) |
| Feral Vampyre | Level 77 (God Wars Dungeon) | God Wars Dungeon (page) |
| Fire giant | Level 104 | Catacombs of Kourend; Charred Dungeon (page); Smoke Dungeon (page) |
| Fire giant | Level 109 | Catacombs of Kourend; Charred Dungeon (page); Smoke Dungeon (page) |
| Frenzied ice troll female | - | Ice Troll Caves (page) |
| Frenzied ice troll grunt | - | Ice Troll Caves (page) |
| Frenzied ice troll male | - | Ice Troll Caves (page) |
| Frenzied ice troll runt | - | Ice Troll Caves (page) |
| Giant Rock Crab | 2 | Waterbirth Island Dungeon sublevel 2; Waterbirth Island Dungeon sublevel 3; Tonali Cavern (page) |
| Greater Nechryael | - | Catacombs of Kourend; Wilderness Slayer Cave (page) |
| Greater demon | Level 100 | Wilderness near Demonic Ruins; Wilderness Slayer Cave; Catacombs of Kourend |
| Greater demon | Level 104 (Wilderness Slayer Cave) | Wilderness near Demonic Ruins; Wilderness Slayer Cave; Catacombs of Kourend |
| Greater demon | Level 113 | Wilderness near Demonic Ruins; Wilderness Slayer Cave; Catacombs of Kourend |
| Green dragon | Level 79 | West of the Dark Warriors' Fortress (page); North of the Graveyard of Shadows (page); South of the Lava Maze (page); Wilderness Slayer Cave |
| Green dragon | Level 88 | West of the Dark Warriors' Fortress (page); North of the Graveyard of Shadows (page); South of the Lava Maze (page); Wilderness Slayer Cave |
| Grimy Lizard | - | Tonali Cavern - Moon Chamber (page); Tonali Cavern - Southern Chamber (floor 1) (page) |
| Grizzly bear (Escape Caves) | - | Escape Caves (page) |
| Hellhound | Level 127 | Catacombs of Kourend; Charred Dungeon (page); Buccaneers' Laboratory (page); God Wars Dungeon (page); Wilderness Slayer Cave |
| Hellhound | Level 136 | Catacombs of Kourend; Charred Dungeon (page); Buccaneers' Laboratory (page); God Wars Dungeon (page); Wilderness Slayer Cave |
| Ice giant | - | Settlement Ruins; White Wolf Mountain On top; Wilderness Slayer Cave |
| Ice troll | Level 120 | Trollweiss Dungeon (page) |
| Ice troll | Level 121 | Trollweiss Dungeon (page) |
| Ice troll | Level 123 | Trollweiss Dungeon (page) |
| Ice troll | Level 124 | Trollweiss Dungeon (page) |
| Ice troll female | Variant 1 | Fremennik Isles |
| Ice troll female | Variant 2 | Fremennik Isles |
| Ice troll grunt | - | Fremennik Isles; Ice Troll Caves (page) |
| Ice troll male | Variant 1 | Fremennik Isles |
| Ice troll male | Variant 2 | Fremennik Isles |
| Ice troll runt | Variant 1 | Fremennik Isles |
| Ice troll runt | Variant 2 | Fremennik Isles |
| Ice warrior | - | Ice Queen's Lair |
| Ice wolf | - | Settlement Ruins; Ice Path |
| Jelly | - | Wilderness Slayer Cave (page) |
| Jungle Wolf | - | Kharazi Jungle (page) |
| Kalphite Guardian | - | Kalphite Lair (page); Kalphite Cave (on Slayer task) (page) |
| Kalphite Soldier | - | Kalphite Lair (page); Kalphite Cave (on Slayer task) (page) |
| King Sand Crab | - | Catacombs of Kourend |
| Lava dragon | - | Lava Dragon Isle (page) |
| Lesser demon | Level 94 | Charred Dungeon (page); Wilderness - King Black Dragon Lair entrance; Wilderness Slayer Cave (page); Catacombs of Kourend |
| Lesser demon | Level 94 (Wilderness Slayer Cave) | Charred Dungeon (page); Wilderness - King Black Dragon Lair entrance; Wilderness Slayer Cave (page); Catacombs of Kourend |
| Lizardman | Level 53 | Shayziens' Wall; Lizardman Canyon, east side |
| Lizardman | Level 62 | Shayziens' Wall; Lizardman Canyon, east side |
| Lizardman brute | - | Shayzien's Wall |
| Locust rider | Lancer | Sophanem Dungeon (cavern level) (page) |
| Locust rider | Ranger | Sophanem Dungeon (cavern level) (page) |
| Mammoth | - | South-east of Ferox Enclave |
| Maniacal monkey | - | Kruk's Dungeon (Upper Level); Kruk's Dungeon (Lower Level) |
| Monkey Archer | - | Marim; Marim gates |
| Monkey Guard | - | Temple of Marimbo; Temple of Marimbo (floor 1) |
| Monkey Zombie | Level 129 | Ape Atoll Dungeon; Temple of Marimbo Dungeon |
| Monkey Zombie | Level 98 | Ape Atoll Dungeon; Temple of Marimbo Dungeon |
| Mountain troll | Level 69 | South of Mount Quidamortem |
| Mourner | level 108 | Mourner Tunnels |
| Mutated Bloodveld | - | Buccaneers' Laboratory (page); Catacombs of Kourend; Meiyerditch Laboratories |
| Nechryael | - | Charred Dungeon (page) |
| Ogre | GWD | God Wars Dungeon (page) |
| Ogre | Level 63 | God Wars Dungeon (page) |
| Pirate | Cabin Fever | Port Piscarilius |
| Red dragon | - | Charred Dungeon (page) |
| Rock Crab | - | North of Rellekka |
| Rogue | Level 135 | Rogues' Castle (floor 0) (page); Rogues' Castle (floor 2) (page) |
| Sand Crab | - | Crabclaw Caves; Crabclaw Isle; East of the Hosidius vinery; Hosidius - southern coast |
| Scarab Mage | Level 93 | Sophanem Dungeon (maze level) (page) |
| Shade | Catacombs of Kourend | Catacombs of Kourend |
| Shadow Hound | - | Shadow Dungeon |
| Skeleton (Ape Atoll) | - | Ape Atoll Dungeon |
| Skeleton (Lucien's camp) | - | Lucien's camp |
| Skeleton (Shayzien Crypts) | Magic | Shayzien Crypts - upper level (page); Shayzien Crypts - middle level (page); Shayzien Crypts - lower level (page) |
| Skeleton (Shayzien Crypts) | Melee | Shayzien Crypts - upper level (page); Shayzien Crypts - middle level (page); Shayzien Crypts - lower level (page) |
| Skeleton (Shayzien Crypts) | Range | Shayzien Crypts - upper level (page); Shayzien Crypts - middle level (page); Shayzien Crypts - lower level (page) |
| Skeleton Mage | Level 83 | Piscatoris Fishing Colony |
| Smoke devil | - | Smoke Devil Dungeon |
| Spiritual mage | Armadyl | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual mage | Bandos | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual mage | Saradomin | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual mage | Zamorak | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual mage | Zaros | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual ranger | Armadyl | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual ranger | Bandos | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual ranger | Saradomin | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual ranger | Zamorak | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual ranger | Zaros | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual warrior | Armadyl | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual warrior | Bandos | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual warrior | Saradomin | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual warrior | Zamorak | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Spiritual warrior | Zaros | God Wars Dungeon; God Wars Dungeon - Ancient Prison (page); Wilderness God Wars Dungeon |
| Suqah | - | North Lunar Isle |
| Swamp Crab | - | Haunted Woods |
| Temple Spider | - | Forthos Dungeon |
| Thrower Troll | - | Death Plateau |
| Thrower troll (Trollheim) | - | Trollheim |
| Twisted Banshee | - | Catacombs of Kourend |
| TzHaar-Hur | - | Mor Ul Rek |
| TzHaar-Ket | Level 149 | Mor Ul Rek |
| TzHaar-Ket | Level 221 | Mor Ul Rek |
| TzHaar-Mej | - | Mor Ul Rek |
| TzHaar-Xil | - | Mor Ul Rek (Ranged); Mor Ul Rek (Melee) |
| Warped Jelly | - | Catacombs of Kourend |
| Werewolf | Alexis | God Wars Dungeon (page) |
| Werewolf | God Wars Dungeon (1) | God Wars Dungeon (page) |
| Wolf | Level 64 | Wilderness, south-east of the Deserted Keep; White Wolf Mountain; South of Forthos Dungeon, near the saltpetre mines; Stronghold of Security first level |
| Zombie (Kourend) | Level 56 | Woods between Lovakengj and Arceuus |
| Zombie (Kourend) | Level 70 | Woods between Lovakengj and Arceuus |
| Zombie (Kourend) | Level 76 | Woods between Lovakengj and Arceuus |
| Zombie (Shayzien Crypts) | Magic | Shayzien Crypts - lower level (page); Shayzien Crypts - upper level (page); Shayzien Crypts - middle level (page) |
| Zombie (Shayzien Crypts) | Melee | Shayzien Crypts - lower level (page); Shayzien Crypts - upper level (page); Shayzien Crypts - middle level (page) |
| Zombie (Shayzien Crypts) | Range | Shayzien Crypts - lower level (page); Shayzien Crypts - upper level (page); Shayzien Crypts - middle level (page) |

## Lone spawns in multi areas (out on the crowd rule)

Multi-area mobs whose every multi location has fewer than three spawns; the number is the spawns the wiki table lists.

- Acidic Araxyte: Morytania Spider Cave (0)
- Araxxor: Morytania Spider Cave (1)
- Balfrug Kreeyath: God Wars Dungeon (Zamorak Boss room) (1)
- Big Wolf: White Wolf Mountain (1)
- Chaos Elemental: West of the Rogues' Castle (1)
- Chaos Fanatic: West of the Lava Maze (1)
- Commander Zilyana: God Wars Dungeon (Saradomin boss room) (0)
- Dagannoth Prime: Waterbirth Island Dungeon (1); Waterbirth Island Dungeon (Slayer lair) (1)
- Dagannoth Rex: Waterbirth Island Dungeon (1); Waterbirth Island Dungeon (Slayer lair) (1)
- Dagannoth Supreme: Waterbirth Island Dungeon (1); Waterbirth Island Dungeon (Slayer lair) (1)
- Flight Kilisa: God Wars Dungeon (Armadyl boss room) (1)
- Flockleader Geerin: God Wars Dungeon (Armadyl boss room) (1)
- General Graardor: God Wars Dungeon (Bandos boss room) (1)
- Giant Mole: Mole Hole (Under Falador Park) (1)
- Iron dragon [Catacombs of Kourend]: Catacombs of Kourend (2)
- K'ril Tsutsaroth: God Wars Dungeon (Zamorak boss room) (1)
- Kalphite Queen [Airborne]: Kalphite Lair (1)
- Kalphite Queen [Crawling]: Kalphite Lair (1)
- Kree'arra: God Wars Dungeon (Armadyl boss room) (1)
- Poison spider [Level 64]: South of the Demonic Ruins (0)
- Scorpia: Cave in the Scorpion Pit (north-east Wilderness) (1)
- Sergeant Grimspike: God Wars Dungeon (Bandos boss room) (1)
- Sergeant Steelwill: God Wars Dungeon (Bandos boss room) (1)
- Sergeant Strongstack: God Wars Dungeon (Bandos boss room) (1)
- Shadow spider: Buccaneers' Laboratory (2)
- Steel dragon [Level 274]: Catacombs of Kourend (2)
- Thermonuclear smoke devil: Smoke Devil Dungeon (1)
- Tstanon Karlak: God Wars Dungeon (Zamorak boss room) (1)
- Venenatis: Silk Chasm (1)
- Zakl'n Gritch: God Wars Dungeon (Zamorak boss room) (1)

## Judgment calls and unknowns for review

Unknown means no observed tiles near any spawn and no page statement. Flagged ones first:

- Mithril dragon: Ancient Cavern: no tiles observed, the page says nothing; the cavern is widely treated as multi - likely in
- Waterfiend: Ancient Cavern, same as the mithril dragon
- King Black Dragon: KBD lair: no tiles observed; the lair is commonly treated as multi - likely in
- Callisto: Callisto's Den: no tiles; the Escape Caves next door are multi
- Vet'ion: Vet'ion's Rest: no tiles
- Artio: singles-plus lair - out
- Corporeal Beast: instanceable multi boss: no tiles
- The Hueycoatl: group boss in multi: no tiles
- Lizardman shaman: Lizardman Settlement / Canyon: no tiles for the shamans themselves
- Gargoyle: Slayer Tower top floor voted single; the basement had no tiles
- Aberrant spectre: Slayer Tower single; Stronghold Slayer Cave voted single for its neighbours
- Tormented Demon: Ancient Guthixian Temple: the game calls it single-way but up to three demons can be fought at once (wiki) - the card may want a special case
- Phantom Muspah: single, instanced - out
- Vorkath: single - out
- Duke Sucellus: single - out
- The Leviathan: single - out
- The Whisperer: single - out
- Vardorvis: single - out
- Amoxliatl: single - out
- Blood Moon: Moons of Peril: no tiles
- Blue Moon: Moons of Peril: no tiles
- Eclipse Moon: Moons of Peril: no tiles
- Chaos Golem: no tiles
- Frost Nagua: no tiles
- Sulphur Nagua: no tiles
- Frost dragon: no tiles

- Nightmare Zone versions (0): the arena is multi but no tiles are observed; include if the card should cover NMZ barraging.

All unknown-only names: Aberrant spectre, Adamant dragon, Amoxliatl, Aquanite, Arrg, Artio, Baby blue dragon, Bear Cub, Berry, Black Guard Berserker, Black bear, Blessed giant rat, Blessed spider, Blood Moon, Blue Moon, Blue dragon, Bouncer, Brutal blue dragon, Buffalo, Callisto, Catablepon, Cave bug, Cave crawler, Cave goblin (monster), Cave slime, Chaos Golem, Chaos druid, Chaos druid warrior, Chilled jelly, Corporeal Beast, Crazy archaeologist, Crocodile, Crypt rat, Crypt spider, Cyclops, Cyclops (Ardougne Zoo), Dad, Deranged archaeologist, Desert Lizard, Desert Wolf, Dire Wolf, Dire Wolf Alpha, Donny the lad, Duckling, Duke Sucellus, Dungeon rat, Earth warrior, Eclipse Moon, Elf Archer, Enclave guard, Entrana firebird, Fever spider, Flesh Crawler, Forgotten Soul, Frost Crab, Frost Nagua, Frost dragon, Gargoyle, Ghost (Melzar's Maze), Ghoul, Giant bat (Brine Rat Cavern), Giant crypt rat, Giant crypt spider, Giant lobster, Giant skeleton (Shadow Dungeon), Goblin (Goblin Village), Goblin guard, Gorad, Grizzly bear, Grizzly bear cub, Gryphon, Guard (Prifddinas), Harpie Bug Swarm, Hobgoblin (The Slug Menace), Ice spider, Icelord, Iorwerth Archer, Iorwerth Warrior, Jackal, Killerwatt, King Black Dragon, Kolodion, Lava Strykewyrm, Lesser demon (Melzar's Maze), Lizard, Lizardman shaman, Lobstrosity, Mithril dragon, Mogre (sea), Monkey (monster), Moonlight Cockatrice, Moss Guardian, Ogre chieftain, Otherworldly being, Phantom Muspah, Phrin Shade, Pit Scorpion, Pyrelord, Revenant cyclops, Revenant dark beast, Revenant dragon, Revenant goblin, Revenant hellhound, Revenant hobgoblin, Revenant knight, Rooster, Salarin the twisted, Scorpia's offspring (monster), Sea Snake Hatchling, Sea Snake Young, Seagull, Shadow warrior, Skeleton (Barrows), Skeleton (Melzar's Maze), Skeleton (Stronghold of Security), Skeleton (mage), Skeleton brute, Skeleton fremennik, Skeleton thug, Small Lizard, Small scarab, Sorebones, Sourhog, Speedy Keith, Spider (Ungael), Sulphur Nagua, Temple Guardian, Terror dog, Terrorbird, The Hueycoatl, The Leviathan, The Whisperer, Tormented Demon, Tortured soul, Troll general, Troll spectator, Twig, Undead one, Urium Shade, Vampyre Juvenile, Vampyre Juvinate, Vardorvis, Venator, Vet'ion, Vorkath, Vulture, Vyrewatch, Warped Terrorbird, Warped Tortoise, Waterfiend, Wild dog, Wingman Skree, Wyrm, Wyrmling, Zombie (Entrana Dungeon), Zombie (Melzar's Maze), Zombie (Sepulchre of Death), Zombie (Stronghold of Security), Zombie pirate (Braindeath Island), Zombie pirate (Harmony Island), Zombie rat, Zombie swab, Zygomite.

Dropped for combat level under 50: Baby red dragon [], Bandit [Level 22], Bat [], Bird [Level 5], Black Heather [], Black Knight [], Cave goblin guard [Level 24], Cave goblin guard [Level 26], Cave goblin miner [], Chaos dwarf [], Chicken [], Cow [], Cow calf [1], Cow calf [Farmland], Crawling Hand [Level 7], Crawling Hand [Level 8 (1)], Dagannoth spawn [], Dark warrior [Level 37], Dark warrior [Level 8], Deadly red spider [], Duck [], Dwarf [Mining Guild (1)], Dwarf [Mining Guild (2)], Dwarf [Mining Guild (3)], Dwarf [Standard (Level 10)], Dwarf [Standard (Level 20)], Dwarf [Worker (3)], Dwarf [Worker (6)], Dwarf [Worker (8)], Dwarf gang member [Level 44], Dwarf gang member [Level 48], Dwarf gang member [Level 49], Ghost [Level 19], Giant bat [], Giant rat [Level 6], Giant spider [Level 2], Giant spider [Level 27], Goblin [Level 2], Goblin [Level 2 (armed)], Goblin [Level 5], Goblin (God Wars Dungeon) [Level 12], Goblin (God Wars Dungeon) [Level 13], Goblin (God Wars Dungeon) [Level 15], Goblin (God Wars Dungeon) [Level 17], Guard Bandit [], Guard dog [], Gunthor the brave [], Hill Giant [], Hobgoblin [Hobgoblin], Hobgoblin [Hobgoblin (GWD)], Hobgoblin [Hobgoblin (armed)], Icefiend [Level 18], Jungle spider [], Jungle spider (Ape Atoll) [], Kalphite Worker [], King Scorpion [], Magic axe [], Minotaur [Level 12], Minotaur [Level 27], Moss giant [], Mounted terrorbird gnome [Level 49], Mourner [level 11], Oomlie bird [], Penguin (monster) [], Pirate [Asgarnian Ice Dungeon], Pirate [Brimhaven], Pirate [Pirate's Cove], Poison spider (Escape Caves) [], Pyrefiend [], Rat [], Respiratory system [], Rockslug [], Rogue [Level 15], Scorpion [Level 14], Scorpion [Level 38], Skeleton [Level 25, 1], Skeleton [Level 29, 1], Skeleton [Level 45, 1], Skeleton (Catacombs of Kourend) [], Skeleton (Forthos Ruin) [], Skeleton Mage [Level 16], Skogre [], Spider [Common], Spider [Stronghold of Security], Spider (Ape Atoll) [], White wolf [Level 25], White wolf [Level 38], Wolf [Level 14], Wolf [Level 25], Zogre [], Zombie [Level 13, 1], Zombie [Level 24, 1], Zombie pirate [].
