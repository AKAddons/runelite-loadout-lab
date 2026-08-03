#!/usr/bin/env python3
"""Generate name_rules.json from the inline tables in DataService/QuestUnlocks.

Three tables are extracted MECHANICALLY, with hard count assertions:

  spellLevels  - the 48-case switch in DataService.spellLevel. Fall-through
                 case labels (three gods sharing level 60) are expanded so each
                 spell gets its own row.
  slayerTerms  - the OR-chain in DataService.knownSlayerMonster. Order is
                 irrelevant (pure OR), but the single startsWith term ("jal-")
                 is kept in its own list because its semantics differ.
  questRules   - every requireIf line in QuestUnlocks. ORDER IS PRESERVED as a
                 JSON array: the code builds a LinkedHashSet, and two quests
                 appear twice - DRAGON_SLAYER_II (ava's assembler, mythical
                 cape) and IN_AID_OF_THE_MYREQUE (ivandis flail, gadderhammer)
                 - so each must still dedup at its first position.

The tables no longer exist in the working tree, so the source is read out of
git at TABLES_REF - the last revision where they were still inline.

Usage:
    python3 scripts/gen_name_rules.py [git-ref]
"""
import collections
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_SERVICE = "src/main/java/com/loadoutlab/data/DataService.java"
QUEST_UNLOCKS = "src/main/java/com/loadoutlab/data/QuestUnlocks.java"
OUT = os.path.join(ROOT, "src/main/resources/com/loadoutlab/data/name_rules.json")

# Last revision with the tables inline.
TABLES_REF = "0c93ff915e370b3414f03451d589bb6625e5be8a"

# Counts verified against the source at TABLES_REF. NOTE: the switch has 48
# case labels, not the ~60 an earlier estimate suggested, and the OR-chain is
# 29 contains terms PLUS one startsWith - assert the two separately, since
# they carry different matching semantics.
EXPECT_SPELLS = 48
EXPECT_SLAYER_CONTAINS = 29
EXPECT_SLAYER_STARTSWITH = 1
EXPECT_QUESTS = 35

CASE = re.compile(r'^\s*case "([^"]+)":\s*$')
RETURN = re.compile(r'^\s*return (\d+);\s*$')
CONTAINS = re.compile(r'normalized\.contains\("([^"]+)"\)')
STARTSWITH = re.compile(r'normalized\.startsWith\("([^"]+)"\)')
REQUIRE_IF = re.compile(r'^\s*requireIf\(quests,\s*(.*?),\s*Quest\.(\w+)\);\s*$')
ITEM_CONTAINS = re.compile(r'item\.contains\("([^"]+)"\)')


def show(ref, path):
    return subprocess.run(["git", "-C", ROOT, "show", ref + ":" + path],
                          capture_output=True, text=True, check=True).stdout


def spell_levels(source):
    """Expand the switch, including fall-through labels sharing one return."""
    body = source[source.index("private static int spellLevel("):]
    body = body[:body.index("\n\tprivate static ", 1)]
    levels = collections.OrderedDict()
    pending = []
    for line in body.splitlines():
        case = CASE.match(line)
        if case:
            pending.append(case.group(1))
            continue
        ret = RETURN.match(line)
        if ret and pending:
            for name in pending:
                levels[name] = int(ret.group(1))
            pending = []
    assert not pending, "unconsumed case labels: %s" % pending
    return levels


def slayer_terms(source):
    body = source[source.index("private static boolean knownSlayerMonster("):]
    body = body[:body.index("\n\t}")]
    return CONTAINS.findall(body), STARTSWITH.findall(body)


def quest_rules(source):
    """Ordered [{terms: [...], quest: NAME}] - one row per requireIf line."""
    rules = []
    for line in source.splitlines():
        m = REQUIRE_IF.match(line)
        if not m:
            continue
        terms = ITEM_CONTAINS.findall(m.group(1))
        assert terms, "requireIf with no item.contains term: " + line
        # Every condition in this table is a pure OR of item.contains(...);
        # assert that so a future mixed predicate cannot be silently dropped.
        rebuilt = " || ".join('item.contains("%s")' % t for t in terms)
        assert rebuilt == m.group(1).strip(), \
            "condition is not a plain contains-OR chain:\n  %s\n  %s" % (m.group(1), rebuilt)
        rules.append(collections.OrderedDict(
            [("terms", terms), ("quest", m.group(2))]))
    return rules


def main():
    ref = sys.argv[1] if len(sys.argv) > 1 else TABLES_REF
    data_service = show(ref, DATA_SERVICE)
    quest_unlocks = show(ref, QUEST_UNLOCKS)

    levels = spell_levels(data_service)
    contains, starts = slayer_terms(data_service)
    rules = quest_rules(quest_unlocks)

    assert len(levels) == EXPECT_SPELLS, \
        "spell rows: %d, expected %d" % (len(levels), EXPECT_SPELLS)
    assert len(contains) == EXPECT_SLAYER_CONTAINS, \
        "slayer contains terms: %d, expected %d" % (len(contains), EXPECT_SLAYER_CONTAINS)
    assert len(starts) == EXPECT_SLAYER_STARTSWITH, \
        "slayer startsWith terms: %s, expected %d" % (starts, EXPECT_SLAYER_STARTSWITH)
    assert len(rules) == EXPECT_QUESTS, \
        "quest rules: %d, expected %d" % (len(rules), EXPECT_QUESTS)

    # The duplicated quests are load-bearing for LinkedHashSet ordering: each
    # must still collapse onto its FIRST occurrence, which the ordered array
    # preserves. Pin the exact set so a new duplicate cannot sneak in unnoticed.
    quests = [r["quest"] for r in rules]
    dupes = sorted(q for q, n in collections.Counter(quests).items() if n > 1)
    assert dupes == ["DRAGON_SLAYER_II", "IN_AID_OF_THE_MYREQUE"], \
        "unexpected duplicate quests: %s" % dupes

    root = collections.OrderedDict([
        ("spellLevels", levels),
        ("slayerContains", contains),
        ("slayerStartsWith", starts),
        ("questRules", rules),
    ])
    with open(OUT, "w") as fh:
        json.dump(root, fh, indent=1)
        fh.write("\n")
    print("wrote %s (spells=%d, slayer=%d contains + %d startsWith, quests=%d)"
          % (OUT, len(levels), len(contains), len(starts), len(rules)))


if __name__ == "__main__":
    main()
