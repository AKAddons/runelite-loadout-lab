"""Read-only coverage measurement of the incoming-damage model for loadout-lab.
Emulates DataService.loadMonsters() collapse + IncomingDpsCalculator.kindOf()/maxHitFor()."""
import gzip, json, re, csv, sys, collections as C, os

ROOT = r"C:\Users\Andrew\Development\runelite-loadout-lab\src\main\resources\com\loadoutlab"
OUT = os.path.dirname(os.path.abspath(__file__))
D = os.path.join(ROOT, "data")

rows = json.load(gzip.open(os.path.join(D, "monsters.json.gz"), "rt", encoding="utf-8"))
overrides = json.load(open(os.path.join(D, "boss_incoming.json"), encoding="utf-8"))
groups = json.load(open(os.path.join(D, "monster_groups.json"), encoding="utf-8"))
raids = json.load(open(os.path.join(D, "raid_boosts.json"), encoding="utf-8"))
wild = json.load(open(os.path.join(D, "wilderness_monsters.json"), encoding="utf-8"))
mood = [l.split("\t")[0] for l in open(os.path.join(ROOT, "render", "mood_routes.tsv"), encoding="utf-8")
        if l.strip() and not l.startswith("#")]

MELEE = {"stab", "slash", "crush", "melee"}
RANGED = {"ranged", "range", "ranged magic", "ranged melee"}
MAGIC = {"magic", "magical ranged", "magical melee", "magic melee"}

def kind(s):
    s = s.lower()
    if s in MELEE: return "melee"
    if s in RANGED: return "ranged"
    if s in MAGIC: return "magic"
    return None

def g(o, k, fb):
    v = (o or {}).get(k)
    if v is None: return fb
    try: return int(v)
    except Exception: return fb

def npc_max(level, bonus):
    return ((level + 9) * (bonus + 64) + 320) // 640

def plugin_max(r, k):
    sk, of = r.get("skills") or {}, r.get("offensive") or {}
    if k == "melee": return npc_max(g(sk, "str", 1), g(of, "str", 0))
    if k == "ranged": return npc_max(g(sk, "ranged", 1), g(of, "ranged_str", 0))
    if k == "magic": return npc_max(g(sk, "magic", 1), g(of, "magic_str", 0))
    return 0

def styles_of(r):
    return [s for s in (r.get("style") or []) if s]

def mh_class(v):
    if v is None: return "missing"
    s = str(v).strip()
    if re.fullmatch(r"\d+", s): return "zero" if int(s) == 0 else "numeric"
    nums = re.findall(r"\d+", s)
    if not nums:
        return "none_text" if s.lower() in ("", "n/a", "none", "?", "unknown") else "text"
    if s.startswith("0") and len(nums) == 1: return "zero"
    if len(set(nums)) > 1 or "<br" in s or "plainlist" in s: return "multi"
    return "annotated_single"

def mh_numbers(v):
    return [int(n) for n in re.findall(r"\d+", str(v))] if v is not None else []

def is_echo(r):
    return "(Echo)" in r["name"] or "(Nightmare Zone)" in r["name"]

def stat_key(r):
    return json.dumps([g(r.get("skills"), "def", 1), g(r.get("skills"), "magic", 1), g(r.get("offensive"), "magic", 0),
                       r.get("defensive"), r.get("attributes"), r.get("is_slayer_monster"), r.get("weakness"),
                       r.get("size", 1), "Core-ejected" if r.get("version") == "Core-ejected" else ""], sort_keys=True)

# --- plugin collapse emulation ---
live = [r for r in rows if not is_echo(r)]
keys_by_name = C.defaultdict(list); maxlvl = {}
for r in live:
    nk = r["name"].lower(); sk = stat_key(r)
    if sk not in keys_by_name[nk]: keys_by_name[nk].append(sk)
    gk = nk + "|" + sk; maxlvl[gk] = max(maxlvl.get(gk, -10**9), g(r, "level", 0))
emitted, seen = [], set()
for r in live:
    nk = r["name"].lower(); gk = nk + "|" + stat_key(r)
    if g(r, "level", 0) != maxlvl[gk] or gk in seen: continue
    seen.add(gk)
    e = dict(r); e["_pver"] = r["version"] if len(keys_by_name[nk]) > 1 else ""
    emitted.append(e)

def override_for(e):
    v = e["_pver"]
    if v:
        k = (e["name"].lower() + "|" + v).lower()
        if k in overrides: return k
    return e["name"].lower() if e["name"].lower() in overrides else None

def model(styles):
    if not styles: return "NONE(no styles)"
    ks = [kind(s) for s in styles]
    n = sum(k is not None for k in ks)
    return "ALL" if n == len(ks) else ("SOME" if n else "NONE")

# --- sets ---
group_names = {m["name"].lower() for grp in groups for m in grp.get("members", [])}
raid_names = {n for k, v in raids.items() if isinstance(v, list) for n in v}
wild_names = set(wild["names"]) | set(wild["exclusive"])
mood_prefixes = [m.lower() for m in mood]
over_names = {k.split("|")[0] for k in overrides}

def in_boss_set(e):
    n = e["name"].lower()
    return (n in group_names or n in raid_names or n in over_names
            or any(n.startswith(p) for p in mood_prefixes))

# --- CSV ---
out_rows = []
for e in emitted:
    st = styles_of(e)
    ks = [kind(s) for s in st]
    ov = override_for(e)
    pmax = {k: plugin_max(e, k) for k in sorted({k for k in ks if k})}
    nums = mh_numbers(e.get("max_hit"))
    out_rows.append({
        "name": e["name"], "corpus_version": e["version"], "plugin_version": e["_pver"], "id": e["id"],
        "level": e["level"], "styles": ";".join(st), "kinds": ";".join(k or "UNMODELED" for k in ks),
        "modelable": model(st), "corpus_max_hit": str(e.get("max_hit")).replace("\n", " "),
        "max_hit_class": mh_class(e.get("max_hit")),
        "corpus_max_hit_top": max(nums) if nums else "",
        "plugin_max_melee": pmax.get("melee", ""), "plugin_max_ranged": pmax.get("ranged", ""),
        "plugin_max_magic": pmax.get("magic", ""), "plugin_max_top": max(pmax.values()) if pmax else "",
        "speed_raw": e.get("speed"), "speed_plugin": max(1, e.get("speed") if e.get("speed") is not None else 4),
        "atk": g(e.get("skills"), "atk", 1), "str": g(e.get("skills"), "str", 1), "ranged": g(e.get("skills"), "ranged", 1),
        "magic": g(e.get("skills"), "magic", 1), "atk_bonus": g(e.get("offensive"), "atk", 0),
        "str_bonus": g(e.get("offensive"), "str", 0), "ranged_bonus": g(e.get("offensive"), "ranged", 0),
        "ranged_str": g(e.get("offensive"), "ranged_str", 0), "magic_bonus": g(e.get("offensive"), "magic", 0),
        "magic_str": g(e.get("offensive"), "magic_str", 0),
        "slayer": e.get("is_slayer_monster"), "boss_set": in_boss_set(e),
        "wilderness": e["name"].lower() in wild_names, "override_key": ov or "",
    })
csvp = os.path.join(OUT, "incoming_coverage.csv")
with open(csvp, "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=list(out_rows[0])); w.writeheader(); w.writerows(out_rows)

P = print
P("raw rows", len(rows), "distinct names", len({r['name'] for r in rows}),
  "echo/NMZ rows dropped", len(rows) - len(live), "plugin entries", len(emitted),
  "plugin distinct names", len({e['name'] for e in emitted}),
  "plugin entries w/ version label", sum(1 for e in emitted if e['_pver']))
P("raw names with >1 row", sum(1 for n, c in C.Counter(r['name'] for r in rows).items() if c > 1))

def breakdown(label, sub):
    sty = C.Counter(s for r in sub for s in styles_of(r))
    mc = C.Counter(model(styles_of(r)) for r in sub)
    hc = C.Counter(mh_class(r.get("max_hit")) for r in sub)
    sp = C.Counter("ok" if (r.get("speed") or 0) > 0 else ("<=0" if r.get("speed") is not None else "missing") for r in sub)
    ov = sum(1 for r in sub if override_for(r))
    P(f"\n== {label}: n={len(sub)} names={len({r['name'] for r in sub})}  modelable={dict(mc)}  max_hit={dict(hc)}  speed={dict(sp)}  override={ov}")
    return sty

sty = breakdown("ALL plugin entries", emitted)
P("style strings top 40:", sty.most_common(40))
P("unmodeled style strings:", C.Counter(s for r in emitted for s in styles_of(r) if not kind(s)).most_common(30))
P("raw corpus style strings (incl echo/NMZ) top 40:", C.Counter(s for r in rows for s in styles_of(r)).most_common(40))
P("raw corpus modelable:", C.Counter(model(styles_of(r)) for r in rows))
P("raw max_hit classes:", C.Counter(mh_class(r.get('max_hit')) for r in rows))
P("raw speed:", C.Counter(r.get('speed') for r in rows).most_common(20))
boss = [e for e in emitted if in_boss_set(e)]
breakdown("BOSS/raid/curated set", boss)
breakdown("SLAYER (corpus flag)", [e for e in emitted if e.get("is_slayer_monster")])
breakdown("WILDERNESS", [e for e in emitted if e["name"].lower() in wild_names])
P("override keys matching no plugin entry:", [k for k in overrides if not any(override_for(e) == k for e in emitted)])

# agreement: plugin computed max vs corpus max_hit, numeric single-kind entries
agree = C.Counter(); diffs = []
for o in out_rows:
    ks = {k for k in o["kinds"].split(";") if k and k != "UNMODELED"}
    if o["max_hit_class"] not in ("numeric", "annotated_single") or len(ks) != 1 or o["modelable"] != "ALL": continue
    k = ks.pop(); pm = o[f"plugin_max_{k}"]; cm = o["corpus_max_hit_top"]
    if cm == 0: continue
    r = pm / cm
    b = "exact" if pm == cm else ("within10%" if abs(r - 1) <= .1 else ("plugin_low" if r < 1 else "plugin_high"))
    agree[(k, b)] += 1
    diffs.append((abs(pm - cm), o["name"], o["plugin_version"], k, pm, cm))
P("\nplugin max vs corpus max (single-kind, numeric):", sorted(agree.items()))
P("biggest disagreements:", sorted(diffs, reverse=True)[:25])
# where plugin max would be 0 although monster attacks
z = [(o["name"], o["plugin_version"], o["kinds"], o["corpus_max_hit"]) for o in out_rows
     if o["modelable"] in ("ALL", "SOME") and o["plugin_max_top"] == 0]
P("modeled entries whose plugin max is 0:", len(z), z[:15])
bad_speed = [(o["name"], o["plugin_version"], o["speed_raw"], o["kinds"]) for o in out_rows
             if (o["speed_raw"] is None or o["speed_raw"] <= 0) and o["modelable"] in ("ALL", "SOME")]
P("modeled entries with speed<=0 (plugin uses 1 tick):", len(bad_speed), bad_speed[:20])

json.dump(out_rows, open(os.path.join(OUT, "rows.json"), "w", encoding="utf-8"))
