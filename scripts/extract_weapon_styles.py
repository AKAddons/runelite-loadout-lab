#!/usr/bin/env python3
"""Extract WeaponStyles.BY_CATEGORY into a bundled JSON resource.

Mechanical: regexes the put(...) rows out of the Java source as they stand -
no category name, attack type, or stance number is typed by hand here. The
stance map mirrors the three private factories in WeaponStyles (accurate =
+3 attack, aggressive = +3 strength, controlled = +1 both). The count
assertions are the safety net: if the parse ever misses a row the script
fails instead of silently shipping a short table.

Run from the repo root:  python3 scripts/extract_weapon_styles.py
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src/main/java/com/loadoutlab/engine/WeaponStyles.java"
TARGET = ROOT / "src/main/resources/com/loadoutlab/data/weapon_styles.json"

EXPECTED_CATEGORIES = 24
EXPECTED_STYLES = 56
# The unknown-category fallback list, stored under a key no weapon category
# can collide with.
FALLBACK_KEY = "*"
EXPECTED_FALLBACK = 9

# The three stance factories in WeaponStyles: (attackStance, strengthStance).
STANCES = {"accurate": (3, 0), "aggressive": (0, 3), "controlled": (1, 1)}

ROW = re.compile(r'BY_CATEGORY\.put\("([^"]+)",\s*List\.of\((.*?)\)\);')
ENTRY = re.compile(r'(accurate|aggressive|controlled)\("([a-z]+)"\)')
FALLBACK = re.compile(r'List<MeleeStyle> ALL = List\.of\((.*?)\);', re.DOTALL)


def styles_of(body):
    return [
        {
            "attackType": attack_type,
            "attackStance": STANCES[stance][0],
            "strengthStance": STANCES[stance][1],
        }
        for stance, attack_type in ENTRY.findall(body)
    ]


def main():
    text = SOURCE.read_text(encoding="utf-8")
    table = {}
    total = 0
    for category, body in ROW.findall(text):
        entries = styles_of(body)
        if not entries:
            sys.exit("no style entries parsed for category %r" % category)
        if category in table:
            sys.exit("duplicate category %r" % category)
        table[category] = entries
        total += len(entries)

    if len(table) != EXPECTED_CATEGORIES:
        sys.exit("expected %d categories, parsed %d" % (EXPECTED_CATEGORIES, len(table)))
    if total != EXPECTED_STYLES:
        sys.exit("expected %d style entries, parsed %d" % (EXPECTED_STYLES, total))

    fallback = FALLBACK.search(text)
    if not fallback:
        sys.exit("could not find the ALL fallback list")
    table[FALLBACK_KEY] = styles_of(fallback.group(1))
    if len(table[FALLBACK_KEY]) != EXPECTED_FALLBACK:
        sys.exit("expected %d fallback styles, parsed %d"
                 % (EXPECTED_FALLBACK, len(table[FALLBACK_KEY])))

    TARGET.write_text(json.dumps(table, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print("wrote %s (%d categories, %d styles, %d fallback)"
          % (TARGET, EXPECTED_CATEGORIES, total, EXPECTED_FALLBACK))


if __name__ == "__main__":
    main()
