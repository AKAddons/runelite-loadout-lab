import json, os, csv
OUT = os.path.dirname(os.path.abspath(__file__))
rows = json.load(open(os.path.join(OUT, "rows.json"), encoding="utf-8"))
T = ["Vorkath", "Zulrah", "General Graardor", "Kree'arra", "Commander Zilyana", "K'ril Tsutsaroth", "Nex",
     "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme", "Cerberus", "Alchemical Hydra", "Dusk", "Dawn",
     "Abyssal Sire", "Kraken", "Thermonuclear smoke devil", "Sarachnis", "Kalphite Queen", "Corporeal Beast",
     "Crystalline Hunllef", "Corrupted Hunllef", "The Nightmare", "Phosani's Nightmare",
     "The Maiden of Sugadinti", "Pestilent Bloat", "Nylocas Vasilias", "Sotetseg", "Xarpus", "Verzik Vitur",
     "Tekton", "Vasa Nistirio", "Vespula", "Vanguard", "Muttadile", "Great Olm",
     "Ba-Ba", "Kephri", "Akkha", "Zebak", "Tumeken's Warden", "Elidinis' Warden",
     "Blood Moon", "Blue Moon", "Eclipse Moon", "Tormented Demon", "Araxxor", "Duke Sucellus", "The Leviathan",
     "The Whisperer", "Vardorvis", "Phantom Muspah", "TzTok-Jad", "TzKal-Zuk", "Sol Heredit", "Scurrius",
     "The Hueycoatl", "Amoxliatl", "Branda the Fire Queen", "Eldric the Ice King", "Yama", "Doom of Mokhaiotl",
     "Callisto", "Vet'ion", "Venenatis", "King Black Dragon", "Giant Mole", "Skotizo", "Demonic gorilla",
     "Gargoyle", "Rune dragon"]
res = []
for t in T:
    es = [r for r in rows if r["name"] == t]
    if not es:
        res.append((t, "MISSING")); continue
    styles = sorted({s for e in es for s in e["styles"].split(";") if s})
    kinds = sorted({k for e in es for k in e["kinds"].split(";") if k})
    mods = {e["modelable"] for e in es}
    ov = sorted({e["override_key"] for e in es if e["override_key"]})
    nov = sum(1 for e in es if e["override_key"])
    cmh = sorted({e["corpus_max_hit"][:40] for e in es})
    pmax = sorted({str(e["plugin_max_top"]) for e in es})
    sp = sorted({str(e["speed_raw"]) for e in es})
    if ov and nov == len(es): m = "yes(override)"
    elif mods == {"ALL"}: m = "yes(sheet)"
    elif mods & {"ALL", "SOME"}: m = "partial"
    else: m = "NO"
    res.append((t, len(es), "/".join(styles), "/".join(kinds), " | ".join(cmh), "/".join(pmax), "/".join(sp), f"{nov}/{len(es)}", m))
w = csv.writer(open(os.path.join(OUT, "top_targets.csv"), "w", newline="", encoding="utf-8"))
w.writerow(["name", "entries", "styles", "kinds", "corpus_max_hit", "plugin_max", "speed", "override", "modelable"])
for r in res:
    w.writerow(r); print(" ;; ".join(map(str, r)))
