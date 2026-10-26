#!/usr/bin/env python3
"""Which monsters drop enough herbs, seeds, planks, gems or coal to bring
the bag that holds them (Andrew 2026-10-22: "for monsters that are
predomant droppers of things that can be bags, suggest to bring bags in
inventory" - "killerwatts have a predominant herb drop table").

Reads the wiki's drop data (Bucket 'dropsline': page, item, rarity), sums
each category's expected drops per kill per monster, keeps the monsters at
or over the category's threshold and writes
src/main/resources/com/loadoutlab/data/loot_containers.json. Container ids
come from runelite-api gameval ItemID.

Usage: python3 scripts/gen_loot_containers.py [--dry-run]
"""
import argparse
import json
import os
import re
import time
import urllib.parse
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src/main/resources/com/loadoutlab/data/loot_containers.json")
UA = {"User-Agent": "loadout-lab loot containers (github.com/AKAddons; RuneLite plugin)"}

HERBS = ["Grimy guam leaf", "Grimy marrentill", "Grimy tarromin", "Grimy harralander", "Grimy ranarr weed",
         "Grimy toadflax", "Grimy irit leaf", "Grimy avantoe", "Grimy kwuarm", "Grimy snapdragon",
         "Grimy cadantine", "Grimy lantadyme", "Grimy dwarf weed", "Grimy torstol"]
SEEDS = ["Guam seed", "Marrentill seed", "Tarromin seed", "Harralander seed", "Ranarr seed", "Toadflax seed",
         "Irit seed", "Avantoe seed", "Kwuarm seed", "Snapdragon seed", "Cadantine seed", "Lantadyme seed",
         "Dwarf weed seed", "Torstol seed", "Potato seed", "Onion seed", "Cabbage seed", "Tomato seed",
         "Sweetcorn seed", "Strawberry seed", "Watermelon seed", "Snape grass seed", "Marigold seed",
         "Rosemary seed", "Nasturtium seed", "Woad seed", "Limpwurt seed", "White lily seed", "Barley seed",
         "Hammerstone seed", "Asgarnian seed", "Jute seed", "Yanillian seed", "Krandorian seed",
         "Wildblood seed", "Redberry seed", "Cadavaberry seed", "Dwellberry seed", "Jangerberry seed",
         "Whiteberry seed", "Poison ivy seed", "Acorn", "Willow seed", "Maple seed", "Yew seed", "Magic seed",
         "Apple tree seed", "Banana tree seed", "Orange tree seed", "Curry tree seed", "Pineapple seed",
         "Papaya tree seed", "Palm tree seed", "Calquat tree seed", "Spirit seed", "Teak seed",
         "Mahogany seed", "Dragonfruit tree seed", "Redwood tree seed", "Celastrus seed", "Mushroom spore",
         "Belladonna seed", "Cactus seed", "Potato cactus seed", "Seaweed spore"]
PLANKS = ["Plank", "Oak plank", "Teak plank", "Mahogany plank"]
GEMS = ["Uncut sapphire", "Uncut emerald", "Uncut ruby", "Uncut diamond", "Uncut dragonstone"]
COAL = ["Coal"]
# Bones a monster's drop table holds (the bonecrusher skips tertiary drops -
# long and curved bones - wiki 2026-10-25); bonemeal, blessed and enriched
# bones and quest bones never drop from a kill.
BONES = ["Bones", "Babydragon bones", "Bat bones", "Big bones", "Burnt bones", "Dagannoth bones", "Dragon bones",
         "Drake bones", "Fayrg bones", "Frost dragon bones", "Gorilla bones", "Bearded gorilla bones", "Hydra bones",
         "Jogre bones", "Lava dragon bones", "Monkey bones", "Ourg bones", "Raurg bones", "Shaikahan bones",
         "Strykewyrm bones", "Superior dragon bones", "Wolf bones", "Wyrm bones", "Wyrmling bones", "Wyvern bones",
         "Zogre bones", "Large zombie monkey bones", "Small zombie monkey bones", "Small ninja monkey bones",
         "Medium ninja monkey bones", "Monodon bones"]
# The demonic ashes the ash sanctifier scatters (wiki 2026-10-25).
ASHES = ["Fiendish ashes", "Vile ashes", "Malicious ashes", "Abyssal ashes", "Infernal ashes"]
# The ensouled heads the soul bearer banks (wiki Ensouled head, 2026-10-25).
HEADS = ["Ensouled " + h + " head" for h in ("goblin", "monkey", "imp", "minotaur", "scorpion", "bear", "unicorn",
         "dog", "chaos druid", "giant", "ogre", "elf", "troll", "horror", "kalphite", "dagannoth", "bloodveld",
         "tzhaar", "demon", "hellhound", "aviansie", "abyssal", "dragon")]

# key, name, gameval ids (closed first, then open/variants), what it holds,
# the items, the per-kill threshold, and what it does with them (the trip
# kit's reason line; "holds them" when absent).
CONTAINERS = [
    ("herbSack", "Herb sack", [13226, 24478, 33135, 33137], "herbs", HERBS, 0.08),
    ("seedBox", "Seed box", [13639, 24482], "seeds", SEEDS, 0.08),
    ("plankSack", "Plank sack", [24882], "planks", PLANKS, 0.02),
    ("gemBag", "Gem bag", [12020, 24481], "gems", GEMS, 0.04),
    ("coalBag", "Coal bag", [12019, 24480], "coal", COAL, 0.05),
    # Andrew 2026-10-25: "the soul bearer, ash sanctifier, and bone crusher
    # should all have options to be brought for certain mobs that qualify
    # for those drops". Bones and ashes drop on (nearly) every kill; heads
    # are a 1-in-30-odd drop.
    ("bonecrusher", "Bonecrusher", [13116, 22986], "bones", BONES, 0.5, "crushes them for Prayer xp"),
    ("ashSanctifier", "Ash sanctifier", [25781], "ashes", ASHES, 0.5, "scatters them for Prayer xp"),
    ("soulBearer", "Soul bearer", [19634], "ensouled heads", HEADS, 0.02, "sends them to the bank"),
]


def rate(text):
    """'1/15.9' -> 0.0629; '2/139' -> 0.0144; 'Always' -> 1; anything else 0."""
    if not text:
        return 0.0
    t = text.replace(",", "").strip()
    if t.lower() == "always":
        return 1.0
    m = re.match(r"^(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)$", t)
    if m and float(m.group(2)) > 0:
        return float(m.group(1)) / float(m.group(2))
    return 0.0


def drops_of(item):
    """[(page_name, per-kill rate)] for one item, combat drops only."""
    out, offset = [], 0
    while True:
        q = ("bucket('dropsline').select('page_name','item_name','drop_json')"
             ".where('item_name','%s').limit(500).offset(%d).run()" % (item.replace("'", "\\'"), offset))
        url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(
            {"action": "bucket", "format": "json", "query": q})
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
            rows = json.load(r).get("bucket", [])
        for row in rows:
            dj = row.get("drop_json")
            try:
                dj = json.loads(dj) if isinstance(dj, str) else (dj or {})
            except ValueError:
                dj = {}
            if dj.get("Drop type", "combat") != "combat":
                continue
            out.append((row["page_name"], rate(dj.get("Rarity"))))
        if len(rows) < 500:
            return out
        offset += 500


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    containers = []
    for key, name, ids, what, items, threshold, *does in CONTAINERS:
        # Sum per page VERSION, then keep each monster's best version: two
        # drop tables (abyssal demons' standard and Catacombs) are not two
        # drops per kill (2026-10-25: the sum read 2 ashes per kill).
        # One item listed once per version on the same page is one drop, not
        # several: the highest rate per (page, item), then summed over items.
        per_version = {}
        for item in items:
            best = {}
            for page, r in drops_of(item):
                best[page] = max(best.get(page, 0.0), r)
            for page, r in best.items():
                per_version[page] = per_version.get(page, 0.0) + r
            time.sleep(0.2)
        per_mob = {}
        for page, r in per_version.items():
            mob = page.split("#")[0].strip().lower()
            per_mob[mob] = max(per_mob.get(mob, 0.0), r)
        kept = {m: round(r, 4) for m, r in sorted(per_mob.items()) if r >= threshold}
        print("%s: %d monsters over %.2f/kill (of %d dropping any)" % (name, len(kept), threshold, len(per_mob)))
        for m in ("killerwatt", "gargoyle", "abyssal demon"):
            if m in per_mob:
                print("   %s: %.3f/kill%s" % (m, per_mob[m], "" if m in kept else " (under)"))
        row = {"key": key, "name": name, "ids": ids, "holds": what, "perKillThreshold": threshold}
        if does:
            row["does"] = does[0]
        row["monsters"] = kept
        containers.append(row)
    payload = {
        "_comment": "Loot containers worth carrying (Andrew 2026-10-22): per container the monsters whose "
                    "combat drops average at least perKillThreshold of what it holds per kill, summed from "
                    "the wiki's drop data (Bucket dropsline) by scripts/gen_loot_containers.py. Suggested "
                    "only when owned. The looting bag rides on any wilderness monster (no drop data needed). "
                    "Ids from runelite-api 1.13.3 gameval ItemID.",
        "lootingBag": {"key": "lootingBag", "name": "Looting bag", "ids": [11941, 22586]},
        "containers": containers,
    }
    if args.dry_run:
        return 0
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, indent=1, sort_keys=False)
        f.write("\n")
    print("wrote", OUT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
