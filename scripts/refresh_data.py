#!/usr/bin/env python3
"""Refresh the vendored gear/monster/spell data from live sources.

Rebuilds src/main/resources/com/loadoutlab/data/*.json.gz from:
  - weirdgloop/osrs-dps-calc cdn JSON (equipment, monsters, spells) - the
    wiki team's auto-updated stat data. DATA ONLY: never copy weirdgloop
    CODE into this repo (their code is GPL-3; see CLAUDE.md).
  - prices.runescape.wiki mapping + latest APIs - item metadata (examine,
    members, alch, buy limit) and GE prices for the enrichment fields the
    engine's schema expects (originally produced by best-dps's snapshot).

Carry-over rules:
  - isStandardGear is best-dps's curated usable-state flag; it is PRESERVED
    for known item ids. New ids default to standard unless their version/
    name marks an unusable variant (Inactive, deadman/beta/bh).
  - Untradeables are absent from the wiki mapping; their examine/members
    carry over from the previous snapshot when known.
  - equipment_requirements.json.gz is NOT regenerated (curated); new items
    have no requirements until added there.

Leagues filtering intentionally does NOT happen here - DataService drops
leagues rewards and unselectable effect spells at load, so a data refresh
can never reintroduce them.

Usage: python3 scripts/refresh_data.py [--cache-dir DIR]
"""

import argparse
import gzip
import io
import json
import os
import sys
import time
import urllib.request

WG_BASE = "https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/master/cdn/json"
WIKI_MAPPING = "https://prices.runescape.wiki/api/v1/osrs/mapping"
WIKI_LATEST = "https://prices.runescape.wiki/api/v1/osrs/latest"
USER_AGENT = "loadout-lab data refresh (github.com/AKAddons; RuneLite plugin)"

# Monster stat corrections applied AFTER fetching weirdgloop. weirdgloop
# encodes a boss's HP-scaling combat stats as 0 (the wiki DPS calc applies the
# scaling itself); we don't yet (task #14), so a scaling Defence reads as 0,
# which makes the player 98% accurate and silently flips melee BiS from
# Oathplate to Torva. Substitute the FULL-HP Defence level (wiki-verified) so
# accuracy and gear picks are right at the start of the fight - the
# conservative, highest-Defence end. When task #14 models the scaling, this
# table shrinks. Keyed by (name, version); each dict overrides skills fields.
MONSTER_STAT_OVERRIDES = {
    # Vardorvis' Defence AND Strength scale 215 -> 145 with remaining HP
    # (wiki: General/Vardorvis). Only Defence affects our output (accuracy);
    # full-HP Defence is 215 for every version. Awakened is harder via HP and
    # damage, so >= 215 only strengthens the Oathplate call - 215 is safe.
    ("Vardorvis", "Post-quest"): {"def": 215},
    ("Vardorvis", "Awakened"): {"def": 215},
    ("Vardorvis", "Quest"): {"def": 215},
}


def apply_monster_overrides(monsters):
    keyed = {(m.get("name"), m.get("version", "")): m for m in monsters}
    applied = 0
    for (name, version), fields in MONSTER_STAT_OVERRIDES.items():
        row = keyed.get((name, version))
        if row is None:
            print("  WARN override miss: %s [%s]" % (name, version))
            continue
        row.setdefault("skills", {}).update(fields)
        applied += 1
    print("monster stat overrides applied: %d/%d" % (applied, len(MONSTER_STAT_OVERRIDES)))
    return monsters

RES_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "com", "loadoutlab", "data")

NONSTANDARD_VERSIONS = {"inactive", "broken", "locked"}
NONSTANDARD_NAME_MARKS = ("(deadman)", "(beta)", "(bh)", "(dmm)")

# Ids best-dps froze as non-standard while unreleased that are now live,
# usable gear. The preserve rule would keep the stale False forever, so
# these WIN over both preserve and the heuristic. Verified against the
# wiki (released, tradeable, ordinary equip). Oathplate is a slash-
# accuracy set with NO passive (wiki-confirmed 2026-07); it earns its
# place on raw stats, it just has to be allowed into the pool first.
STANDARD_OVERRIDES = {
    30750: "Oathplate helm",
    30753: "Oathplate chest",
    30756: "Oathplate legs",
    31106: "Confliction gauntlets",
}


def fetch(url, cache_dir, cache_name):
    path = os.path.join(cache_dir, cache_name)
    if os.path.exists(path) and time.time() - os.path.getmtime(path) < 6 * 3600:
        with open(path, "rb") as f:
            return json.load(f)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as resp:
        raw = resp.read()
    with open(path, "wb") as f:
        f.write(raw)
    return json.loads(raw)


def load_old_gear():
    path = os.path.join(RES_DIR, "gear_prices.json.gz")
    if not os.path.exists(path):
        return {}
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return {row["id"]: row for row in json.load(f)}


def default_standard(name, version):
    if (version or "").strip().lower() in NONSTANDARD_VERSIONS:
        return False
    lname = (name or "").lower()
    return not any(mark in lname for mark in NONSTANDARD_NAME_MARKS)


def build_gear(wg_equipment, mapping_by_id, latest_by_id, old_by_id):
    result = []
    for row in wg_equipment:
        item_id = row.get("id")
        old = old_by_id.get(item_id)
        meta = mapping_by_id.get(item_id)
        price = latest_by_id.get(str(item_id)) or {}

        high = price.get("high")
        low = price.get("low")
        wiki_value = meta.get("value") if meta else (old or {}).get("wikiValue")
        if high is not None:
            estimated, source = high, "latestHigh"
        elif low is not None:
            estimated, source = low, "latestLow"
        elif meta and wiki_value:
            estimated, source = wiki_value, "wikiValue"
        else:
            estimated, source = None, "none"

        out = {
            # Stats: always the fresh wiki-team numbers.
            "id": item_id,
            "name": row.get("name"),
            "version": row.get("version") or "",
            "slot": row.get("slot"),
            "image": row.get("image"),
            "speed": row.get("speed"),
            "category": row.get("category"),
            "weight": row.get("weight"),
            "isTwoHanded": bool(row.get("isTwoHanded")),
            "bonuses": row.get("bonuses"),
            "offensive": row.get("offensive"),
            "defensive": row.get("defensive"),
            # Metadata: wiki mapping, falling back to the prior snapshot
            # for untradeables the mapping doesn't cover.
            "tradeable": meta is not None,
            "members": (meta.get("members") if meta is not None
                        else (old or {}).get("members", True)),
            "examine": (meta.get("examine") if meta is not None
                        else (old or {}).get("examine", "")),
            "buyLimit": meta.get("limit") if meta else (old or {}).get("buyLimit"),
            "wikiValue": wiki_value,
            "wikiIcon": meta.get("icon") if meta else (old or {}).get("wikiIcon"),
            "highAlch": meta.get("highalch") if meta else (old or {}).get("highAlch"),
            "lowAlch": meta.get("lowalch") if meta else (old or {}).get("lowAlch"),
            # Prices: fresh GE numbers.
            "high": high,
            "low": low,
            "highTime": price.get("highTime"),
            "lowTime": price.get("lowTime"),
            "estimatedPrice": estimated,
            "priceSource": source,
            # Curated usable-state flag: an explicit override wins (stale
            # pre-release Falses); else preserve; else heuristic for new ids.
            "isStandardGear": (True if item_id in STANDARD_OVERRIDES
                               else old["isStandardGear"] if old is not None
                               else default_standard(row.get("name"), row.get("version"))),
        }
        result.append(out)
    return result


def supplement_aliases(aliases, wg_equipment):
    """Weirdgloop treats corrupted '(c)' rows as their own base; for
    OWNERSHIP crediting they equal the Charged base (identical stats,
    e.g. Bow of faerdhinen (c) -> Charged). Add those links, then
    flatten chains so one-hop canonicalization lands on the final base
    (hue variants -> (c) -> Charged must become hue -> Charged)."""
    def stats(row):
        import json as _json
        return (row.get("slot"), row.get("speed"), row.get("isTwoHanded"),
                _json.dumps(row.get("offensive"), sort_keys=True),
                _json.dumps(row.get("defensive"), sort_keys=True),
                _json.dumps(row.get("bonuses"), sort_keys=True))
    by_name = {}
    for row in wg_equipment:
        by_name.setdefault(row.get("name"), []).append(row)
    for row in wg_equipment:
        name = row.get("name") or ""
        if not name.endswith(" (c)") or str(row["id"]) in aliases:
            continue
        for base in by_name.get(name[:-4], []):
            if base.get("version") in ("Charged", "", "Active", "New") \
                    and stats(base) == stats(row):
                aliases[str(row["id"])] = base["id"]
                break
    for key in list(aliases):
        target = aliases[key]
        seen = {int(key)}
        while str(target) in aliases and aliases[str(target)] != target \
                and target not in seen:
            seen.add(target)
            target = aliases[str(target)]
        aliases[key] = target
    return aliases


def write_gz(name, payload):
    path = os.path.join(RES_DIR, name)
    buf = io.BytesIO()
    # mtime=0 keeps the archive byte-reproducible for identical inputs.
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
        gz.write(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    with open(path, "wb") as f:
        f.write(buf.getvalue())
    print("wrote %s (%d bytes, %d records)" % (name, len(buf.getvalue()), len(payload)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", default="/tmp/loadout-lab-data-cache",
                        help="download cache (6h TTL)")
    args = parser.parse_args()
    os.makedirs(args.cache_dir, exist_ok=True)

    wg_equipment = fetch(WG_BASE + "/equipment.json", args.cache_dir, "wg_equipment.json")
    wg_monsters = fetch(WG_BASE + "/monsters.json", args.cache_dir, "wg_monsters.json")
    wg_spells = fetch(WG_BASE + "/spells.json", args.cache_dir, "wg_spells.json")
    # variant id -> base id (ornaments, locked, degraded states with the
    # same stats); the loader uses it to suggest base versions only.
    wg_aliases = fetch(WG_BASE + "/equipment_aliases.json", args.cache_dir, "wg_aliases.json")
    mapping = fetch(WIKI_MAPPING, args.cache_dir, "wiki_mapping.json")
    latest = fetch(WIKI_LATEST, args.cache_dir, "wiki_latest.json")["data"]

    mapping_by_id = {m["id"]: m for m in mapping}
    old_by_id = load_old_gear()

    gear = build_gear(wg_equipment, mapping_by_id, latest, old_by_id)
    new_ids = [g for g in gear if g["id"] not in old_by_id]
    print("equipment: %d rows (%d new since last snapshot)" % (len(gear), len(new_ids)))
    for g in sorted(new_ids, key=lambda x: x["name"])[:40]:
        print("  new: %s [%s] id=%d std=%s tradeable=%s" % (
            g["name"], g["version"], g["id"], g["isStandardGear"], g["tradeable"]))
    if len(new_ids) > 40:
        print("  ... and %d more" % (len(new_ids) - 40))

    write_gz("gear_prices.json.gz", gear)
    write_gz("monsters.json.gz", apply_monster_overrides(wg_monsters))
    write_gz("spells.json.gz", wg_spells)
    write_gz("equipment_aliases.json.gz",
             supplement_aliases(wg_aliases, wg_equipment))
    print("done - run ./gradlew test to validate the snapshot")
    return 0


if __name__ == "__main__":
    sys.exit(main())
