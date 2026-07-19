#!/usr/bin/env python3
"""Generate assume_icons.json from the AssumeIcons.java lookup tables.

Every value is produced MECHANICALLY: the .put() lines are regex-parsed out of
the Java source, and each symbolic value (SpriteID.Prayeron.PIETY and friends)
is resolved by `javap -p -constants` against the runelite-api jar that Gradle
actually puts on the compile classpath. No sprite id is ever typed by hand.

The inline tables no longer exist in the working tree (that is the point of
the extraction), so the source is read out of git at TABLES_REF - the last
revision where AssumeIcons.java still held the 90 .put() lines. That keeps the
generator reproducible for anyone re-verifying the extraction.

Usage:
    python3 scripts/gen_assume_icons.py <path-to-runelite-api.jar> [git-ref]

Re-run this after a RuneLite bump if AssumeIconsDriftTest goes red; the test
reflects the same constants at build time, so a sprite renumbering upstream
fails the build rather than silently showing the wrong icon.
"""
import collections
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = "src/main/java/com/loadoutlab/ui/AssumeIcons.java"
# Last revision with the tables inline.
TABLES_REF = "0c93ff915e370b3414f03451d589bb6625e5be8a"
OUT = os.path.join(ROOT, "src/main/resources/com/loadoutlab/data/assume_icons.json")
# Symbol map lives in the TEST tree (free: the hub bot only counts main source).
# It records which SpriteID constant each frozen int came from, so the drift
# test can reflect the exact field rather than merely checking membership.
SYMBOLS_OUT = os.path.join(
    ROOT, "src/test/resources/com/loadoutlab/data/assume_icons_symbols.json")

EXPECTED = {"prayers": 21, "boostItems": 8, "spells": 61}
MAP_KEY = {"PRAYERS": "prayers", "BOOST_ITEMS": "boostItems", "SPELLS": "spells"}

PUT = re.compile(
    r'^\s*(PRAYERS|BOOST_ITEMS|SPELLS)\.put\("([^"]+)",\s*([^)]+?)\);'
)
SYMBOL = re.compile(r'^SpriteID\.(\w+)\.(\w+)$')

_cache = {}


def constants(jar, inner):
    """All `static final int` constants of SpriteID$<inner>, via javap."""
    if inner not in _cache:
        out = subprocess.run(
            ["javap", "-p", "-constants", "-cp", jar,
             "net.runelite.api.gameval.SpriteID$" + inner],
            capture_output=True, text=True, check=True).stdout
        _cache[inner] = {
            m.group(1): int(m.group(2))
            for m in re.finditer(r'static final int (\w+) = (-?\d+);', out)
        }
    return _cache[inner]


def main():
    if len(sys.argv) not in (2, 3):
        sys.exit("usage: gen_assume_icons.py <runelite-api.jar> [git-ref]")
    jar = sys.argv[1]
    ref = sys.argv[2] if len(sys.argv) == 3 else TABLES_REF
    source = subprocess.run(
        ["git", "-C", ROOT, "show", ref + ":" + SRC],
        capture_output=True, text=True, check=True).stdout

    tables = {v: collections.OrderedDict() for v in MAP_KEY.values()}
    symbols = {v: collections.OrderedDict() for v in MAP_KEY.values()}
    parsed = 0
    for line in source.splitlines():
        m = PUT.match(line)
        if not m:
            continue
        table, name, expr = m.group(1), m.group(2), m.group(3).strip()
        # Strip any trailing line comment inside the argument list.
        expr = expr.split("//")[0].strip()
        sym = SYMBOL.match(expr)
        if sym:
            pool = constants(jar, sym.group(1))
            if sym.group(2) not in pool:
                sys.exit("UNRESOLVED: " + expr)
            value = pool[sym.group(2)]
            symbols[MAP_KEY[table]][name] = sym.group(1) + "." + sym.group(2)
        elif re.fullmatch(r'-?\d+', expr):
            value = int(expr)
        else:
            sys.exit("UNPARSEABLE value: " + expr)
        tables[MAP_KEY[table]][name] = value
        parsed += 1

    total = sum(EXPECTED.values())
    assert parsed == total, "parsed %d .put() lines, expected %d" % (parsed, total)
    for key, want in EXPECTED.items():
        got = len(tables[key])
        assert got == want, "%s: %d entries, expected %d" % (key, got, want)

    with open(OUT, "w") as fh:
        json.dump(tables, fh, indent=1)
        fh.write("\n")
    os.makedirs(os.path.dirname(SYMBOLS_OUT), exist_ok=True)
    with open(SYMBOLS_OUT, "w") as fh:
        json.dump(symbols, fh, indent=1)
        fh.write("\n")
    print("wrote %s (%d/%d resolved: %s)" % (
        OUT, parsed, total,
        ", ".join("%s=%d" % (k, len(tables[k])) for k in EXPECTED)))


if __name__ == "__main__":
    main()
