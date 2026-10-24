#!/usr/bin/env python3
"""Intake monsters and gear from the wiki's infoboxes when weirdgloop lags.

weirdgloop's cdn (refresh_data.py's source) updates weeks after a release;
the wiki's Infobox Monster / Infobox Bonuses carry the same stats on day
one. This script fetches the listed pages, maps the infobox fields onto
the corpus row schema refresh_data.py writes, and MERGES the rows into
src/main/resources/com/loadoutlab/data/{monsters,gear_prices}.json.gz by
id (replacing a row with the same id, keeping every other row). Ids are
cross-checked against the table below, which carries the gameval
constants (runelite-api ItemID/NpcID) - never a wiki number alone.

Tradeable rows take the official GE catalogue's guide price first
(priceSource "geGuide"): a release's first trades are noise (the
Obligator's first latest-high was 5,999,999,999). With no guide price
they fall back to the wiki's real-time latest high, then low. A price is
what lets a reward into the game-best pool (ALL_STANDARD needs one).

Usage: python3 scripts/wiki_intake.py [--dry-run]
"""
import argparse
import gzip
import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(REPO, "src/main/resources/com/loadoutlab/data")
UA = {"User-Agent": "loadout-lab wiki intake (github.com/AKAddons; RuneLite plugin)"}

# The Fractured Archive (released 2026-10-20). (page, {version: gameval npc id
# or a list of ids sharing the stats}). A version of "" means the page's
# single infobox. Re-run 2026-10-22 (ISS-01M5JBYYKPE7192Z24NTR432M8): the wiki's fixes since
# launch, the Guardian's grounded form, the Dagon'hai trio and the adds.
MONSTERS = [
    ("Archive Guardian", {"Flying": 16659, "Grounded": 16660}),  # NpcID.TFA_GUARDIAN / _UNSHIELDED (the wiki gives both 16659)
    ("Ezekiel", {"": 16652}),                   # NpcID.TFA_EZEKIEL
    ("Bork", {"": 16640}),                      # NpcID.TFA_BORK
    ("The Smorks", {"": 16641}),                # NpcID.TFA_SMORK ("Smorks" lost its infobox, 2026-10-20)
    ("Dreadnoth the Jellied", {"": 16665}),     # NpcID.TFA_JELLY_BOSS
    ("Moia", {"": 16667}),                      # NpcID.TFA_MOIA
    ("Xarth", {"": 16684}),                     # NpcID.TFA_XARTH
    ("Ocellus", {"Phase 1": 16674, "Phase 2": 16675, "Phase 3": 16677}),  # NpcID.TFA_OCELLUS_P1/P2/P3
    ("Dagon'hai Elder", {"": [16643, 16644]}),  # NpcID.TFA_DAGONHAI_LEADER / _UNSHIELDED
    ("Dagon'hai Monk", {"Staff": [16645, 16646], "Book": [16647, 16648]}),  # NpcID.TFA_DAGONHAI_MINION_1/2 (+ _UNSHIELDED)
    ("Jelly (Dreadnoth)", {"": 16666}),         # NpcID.TFA_JELLY_MINION
    ("Undead binder", {"": 16670}),             # NpcID.TFA_MOIA_MINION
    ("Void Rift", {"": 16678}),                 # NpcID.TFA_OCELLUS_P3_BOMBPORTAL_BASIC
    ("Void Augmenter", {"": 16680}),            # NpcID.TFA_OCELLUS_P3_BOMBPORTAL_BUFF
    ("Corpse (Xarth)", {"Normal": 16687, "Doused": 16688}),  # NpcID.TFA_XARTH_CORPSE / _DOUSED
]

# Rows the wiki fixed that weirdgloop's data (refresh_data.py) still lags:
# Nex's crush defence went 60 -> 35 on 2026-10-20 (Jagex; wiki Nex, Changes).
PATCHES = {11278: {"defensive": {"crush": 35}}}

# (page, slot, category, isTwoHanded, {version: (gameval item id, standard)}, requirements)
GEAR = [
    ("Obligator", "weapon", "Blunt", True, {"Charged": (34715, True), "Uncharged": (34717, True)}, {"attack": 85}),
    ("Zorya's tome", "weapon", "Powered Staff", True, {"Charged": (34725, True), "Uncharged": (34727, True)}, {"magic": 85}),
    ("Ascension crossbows", "weapon", "Crossbow", True, {"": (34681, True)}, {"ranged": 80}),
    ("Rondache", "shield", "", False, {"": (34719, True)}, {}),
    ("Ascension bolts", "ammo", "", False, {"": (34684, True)}, {}),
    ("Diamond ascension bolts", "ammo", "", False, {"": (34690, True)}, {}),
    ("Diamond ascension bolts (e)", "ammo", "", False, {"": (34696, True)}, {}),
    ("Onyx ascension bolts", "ammo", "", False, {"": (34702, True)}, {}),
    ("Onyx ascension bolts (e)", "ammo", "", False, {"": (34708, True)}, {}),
]


LATEST = {}


def latest_prices():
    if not LATEST:
        req = urllib.request.Request("https://prices.runescape.wiki/api/v1/osrs/latest", headers=UA)
        with urllib.request.urlopen(req, timeout=30) as r:
            LATEST.update(json.load(r)["data"])
    return LATEST


def guide_price(item_id):
    """The official GE catalogue's guide price ("163.0m"), or None."""
    url = "https://secure.runescape.com/m=itemdb_oldschool/api/catalogue/detail.json?item=%d" % item_id
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
            text = str(json.load(r)["item"]["current"]["price"]).replace(",", "").strip().lower()
    except Exception as e:
        print("  no guide price for %d: %s" % (item_id, e))
        return None
    scale = {"k": 1e3, "m": 1e6, "b": 1e9}.get(text[-1:], 1)
    return int(round(float(text.rstrip("kmb")) * scale))


def price_fields(item_id, tradeable):
    """(estimatedPrice, priceSource, high, low, highTime, lowTime)."""
    if not tradeable:
        return None, "none", None, None, None, None
    p = latest_prices().get(str(item_id)) or {}
    high, low = p.get("high"), p.get("low")
    guide = guide_price(item_id)
    time.sleep(0.5)
    if guide:
        return guide, "geGuide", high, low, p.get("highTime"), p.get("lowTime")
    if high:
        return high, "latestHigh", high, low, p.get("highTime"), p.get("lowTime")
    if low:
        return low, "latestLow", high, low, p.get("highTime"), p.get("lowTime")
    return None, "none", None, None, None, None


def wikitext(title):
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode({
        "action": "parse", "page": title, "prop": "wikitext", "format": "json",
        "formatversion": 2, "redirects": 1})
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)["parse"]["wikitext"]


def infobox(text, name):
    # Brace-count to the template's own close: editors end it on its own
    # line or on the last field's ("|flatarmour=2}}", The Smorks 2026-10-20).
    m = re.search(r"\{\{Infobox " + name + r"\b", text)
    if not m:
        return {}
    depth, i = 0, m.start()
    while i < len(text) - 1:
        pair = text[i:i + 2]
        if pair == "{{":
            depth += 1
            i += 2
            continue
        if pair == "}}":
            depth -= 1
            i += 2
            if depth == 0:
                break
            continue
        i += 1
    body = text[m.end():i - 2]
    fields = {}
    for k, v in re.findall(r"\|\s*([\w ]+?)\s*=\s*([^|\n}]*)", body):
        v = re.sub(r"<!--.*?-->", "", v).strip()
        if v:
            fields[k.strip().lower()] = v
    return fields


def versioned(fields, key, n):
    """fieldN for version n, else the unversioned field, else version 1's
    (a phased boss lists its shared hitpoints on the first phase only)."""
    if n and key + str(n) in fields:
        return fields[key + str(n)]
    if key in fields:
        return fields[key]
    return fields.get(key + "1") if n else None


def num(v, default=0):
    if v is None:
        return default
    m = re.search(r"-?\d+", v.replace(",", ""))
    return int(m.group(0)) if m else default


def monster_rows(page, ids):
    fields = infobox(wikitext(page), "Monster")
    name = fields.get("name", page)
    rows = []
    versions = [(n, fields.get("version%d" % n)) for n in range(1, 10) if fields.get("version%d" % n)]
    if not versions:
        versions = [(0, "")]
    for n, version in versions:
        if version not in ids:
            print("  skip %s [%s]: no gameval id in the table" % (name, version))
            continue
        gids = ids[version] if isinstance(ids[version], list) else [ids[version]]
        wiki_id = num(versioned(fields, "id", n), 0)
        if wiki_id and wiki_id not in gids:
            print("  WARNING %s [%s]: wiki id %d != gameval %d - gameval wins" % (name, version, wiki_id, gids[0]))
        f = lambda key, d=0: num(versioned(fields, key, n), d)
        styles = [s.strip().capitalize() for s in re.sub(r"\[\[([^\]|]*\|)?([^\]]*)\]\]", r"\2",
                  versioned(fields, "attack style", n) or "").replace(" and ", ",").split(",") if s.strip()]
        weakness_type = (versioned(fields, "elementalweaknesstype", n) or "").lower()
        # No cross-version fallback for attributes: Ocellus sets Demon on
        # phases 1 and 3 and leaves phase 2 blank (2026-10-20).
        own = fields.get("attributes%d" % n) if n else None
        attrs = [a.strip().lower() for a in (own if own is not None else fields.get("attributes", "")).split(",") if a.strip()]
        row = {
            "id": gids[0], "name": name, "version": version,
            "image": name + ".png", "level": f("combat"), "speed": f("attack speed"),
            "style": styles, "size": f("size", 1),
            "max_hit": versioned(fields, "max hit", n) or "",
            "skills": {"atk": f("att"), "def": f("def"), "hp": f("hitpoints"), "magic": f("mage"),
                       "ranged": f("range"), "str": f("str")},
            "offensive": {"atk": f("attbns"), "magic": f("amagic"), "magic_str": f("mbns"),
                          "ranged": f("arange"), "ranged_str": f("rngbns"), "str": f("strbns")},
            "defensive": {"flat_armour": f("flatarmour"), "crush": f("dcrush"), "magic": f("dmagic"),
                          "heavy": f("dheavy"), "standard": f("dstandard"), "light": f("dlight"),
                          "slash": f("dslash"), "stab": f("dstab")},
            "attributes": attrs, "immunities": {"burn": None},
            "is_slayer_monster": False,
            "weakness": {"element": weakness_type, "severity": f("elementalweaknesspercent")} if weakness_type else None,
            "source": "wiki_intake",
        }
        for gid in gids:
            rows.append(dict(row, id=gid))
    return rows


def gear_rows(page, slot, category, two_handed, ids, requirements):
    text = wikitext(page)
    item = infobox(text, "Item")
    bonuses = infobox(text, "Bonuses")
    name = item.get("name", page)
    rows, reqs = [], []
    versions = [(n, bonuses.get("version%d" % n)) for n in range(1, 10) if bonuses.get("version%d" % n)]
    if not versions:
        versions = [(0, "")]
    for n, version in versions:
        if version not in ids:
            print("  skip %s [%s]: no gameval id in the table" % (name, version))
            continue
        item_id, standard = ids[version]
        wiki_id = num(versioned(item, "id", n), 0)
        if wiki_id and wiki_id != item_id:
            print("  WARNING %s [%s]: wiki id %d != gameval %d - gameval wins" % (name, version, wiki_id, item_id))
        b = lambda key: num(versioned(bonuses, key, n), 0)
        # tradeable1/tradeable2 split by version (an uncharged twin trades,
        # the charged weapon does not)
        tradeable = (versioned(item, "tradeable", n) or "No").lower() == "yes"
        estimated, source, high, low, high_t, low_t = price_fields(item_id, tradeable)
        rows.append({
            "id": item_id, "name": name, "version": version, "slot": slot,
            "image": name + ".png", "speed": b("speed") if slot == "weapon" else 0,
            "category": category, "weight": float(item.get("weight", "0") or 0),
            "isTwoHanded": two_handed,
            # the wiki's mdmg is a percent; the corpus keeps tenths (Kodai 15% = 150)
            "bonuses": {"str": b("str"), "ranged_str": b("rstr"), "magic_str": b("mdmg") * 10, "prayer": b("prayer")},
            "offensive": {"stab": b("astab"), "slash": b("aslash"), "crush": b("acrush"), "magic": b("amagic"), "ranged": b("arange")},
            "defensive": {"stab": b("dstab"), "slash": b("dslash"), "crush": b("dcrush"), "magic": b("dmagic"), "ranged": b("drange")},
            "tradeable": tradeable, "members": True,
            "examine": item.get("examine"), "buyLimit": None, "wikiValue": None, "wikiIcon": None,
            "highAlch": None, "lowAlch": None, "high": high, "low": low, "highTime": high_t, "lowTime": low_t,
            "estimatedPrice": estimated, "priceSource": source, "isStandardGear": standard,
            "source": "wiki_intake",
        })
        if requirements:
            reqs.append({"id": item_id, "skills": dict(requirements)})
    return rows, reqs


def read_gz(name):
    with gzip.open(os.path.join(RES_DIR, name), "rb") as f:
        return json.load(f)


def write_gz(name, payload):
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
        gz.write(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    with open(os.path.join(RES_DIR, name), "wb") as f:
        f.write(buf.getvalue())
    print("wrote %s (%d records)" % (name, len(payload)))


def merge(existing, rows):
    by_id = {r["id"]: r for r in rows}
    out = [r for r in existing if r.get("id") not in by_id]
    replaced = len(existing) - len(out)
    out.extend(rows)
    return out, replaced


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    monsters, gear, reqs = [], [], []
    for page, ids in MONSTERS:
        rows = monster_rows(page, ids)
        print("monster %s: %d row(s)" % (page, len(rows)))
        for r in rows:
            print("   %s [%s] id=%d hp=%d def=%d weak=%s" % (r["name"], r["version"], r["id"], r["skills"]["hp"], r["skills"]["def"], r["weakness"]))
        monsters.extend(rows)
        time.sleep(0.3)
    for page, slot, category, two_handed, ids, requirements in GEAR:
        rows, rq = gear_rows(page, slot, category, two_handed, ids, requirements)
        print("gear %s: %d row(s)" % (page, len(rows)))
        for r in rows:
            print("   %s [%s] id=%d tradeable=%s price=%s (%s)" % (r["name"], r["version"], r["id"], r["tradeable"], r["estimatedPrice"], r["priceSource"]))
        gear.extend(rows)
        reqs.extend(rq)
        time.sleep(0.3)
    if args.dry_run:
        return 0
    m, rm = merge(read_gz("monsters.json.gz"), monsters)
    for row in m:
        for key, fix in PATCHES.get(row.get("id"), {}).items():
            row[key].update(fix)
    g, rg = merge(read_gz("gear_prices.json.gz"), gear)
    q, rq = merge(read_gz("equipment_requirements.json.gz"), reqs)
    write_gz("monsters.json.gz", m)
    write_gz("gear_prices.json.gz", g)
    write_gz("equipment_requirements.json.gz", q)
    print("replaced: %d monsters, %d gear, %d requirements" % (rm, rg, rq))
    return 0


if __name__ == "__main__":
    sys.exit(main())
