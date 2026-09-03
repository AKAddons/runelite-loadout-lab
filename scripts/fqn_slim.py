"""Mechanical slim pass: fully-qualified class names used inline become
imports + simple names, when that is a net token win and no name clashes.
Usage: fqn_slim.py <src-root> [apply]   (dry run prints the plan)."""
import pathlib, re, sys, collections
ROOT = pathlib.Path(sys.argv[1]); APPLY = "apply" in sys.argv
PREFIX = r"(?:com\.loadoutlab|java\.util|java\.awt|javax\.swing|net\.runelite|java\.io|java\.nio|java\.util\.function|java\.util\.concurrent)"
FQN = re.compile(r"(?<![\w.])(" + PREFIX + r"(?:\.[a-z][a-z0-9_]*)*)\.([A-Z][A-Za-z0-9_]*)\b")
def in_string(line, idx):
    q = 0; i = 0
    while i < idx:
        if line[i] == '"' and (i == 0 or line[i - 1] != "\\"): q ^= 1
        i += 1
    return q == 1
total_saved_est = 0
for path in sorted(ROOT.rglob("*.java")):
    src = path.read_text(); lines = src.split("\n")
    pkg = next((l.split()[1].rstrip(";") for l in lines if l.startswith("package ")), "")
    imports = {}
    for l in lines:
        m = re.match(r"import (?:static )?([\w.]+)\.(\w+|\*);", l)
        if m and m.group(2) != "*" and "static" not in l: imports[m.group(2)] = m.group(1)
    # simple names already present in the file (declared types, other imports) block a clash
    declared = set(re.findall(r"\b(?:class|interface|enum|record)\s+([A-Z]\w*)", src))
    same_pkg = {q.stem for q in path.parent.glob("*.java")}
    uses = collections.defaultdict(list)   # (pkg, Class) -> [(lineno, start, end)]
    for i, line in enumerate(lines):
        if line.startswith("import ") or line.startswith("package "): continue
        for m in FQN.finditer(line):
            if in_string(line, m.start()): continue
            uses[(m.group(1), m.group(2))].append((i, m.start(), m.end()))
    plan = []
    for (pk, cls), sites in uses.items():
        if pk == pkg: plan.append((pk, cls, sites, None)); continue   # same package: no import needed
        if cls in imports and imports[cls] != pk: continue           # clashes with an existing import
        if cls in declared or (cls in same_pkg and pk != pkg): continue
        if cls not in imports and len(sites) < 2: continue          # one use: an import costs as much as it saves
        plan.append((pk, cls, sites, None if cls in imports else f"import {pk}.{cls};"))
    if not plan: continue
    # a class name may appear under two packages (java.util.List vs java.awt.List): keep neither
    names = collections.Counter(cls for _, cls, _, _ in plan)
    plan = [p for p in plan if names[p[1]] == 1 and not (p[1] in imports and imports[p[1]] != p[0])]
    saved = sum(len(sites) * (len(pk.split(".")) + 1) for pk, cls, sites, imp in plan) - sum(6 for p in plan if p[3])
    total_saved_est += saved
    print(f"{path.relative_to(ROOT)}: {len(plan)} names, {sum(len(p[2]) for p in plan)} sites, ~{saved} tokens")
    if not APPLY: continue
    # apply: replace right-to-left per line, then add imports after the last import
    edits = collections.defaultdict(list)
    for pk, cls, sites, imp in plan:
        for (i, a, b) in sites: edits[i].append((a, b, cls))
    for i, es in edits.items():
        line = lines[i]
        for a, b, cls in sorted(es, reverse=True): line = line[:a] + cls + line[b:]
        lines[i] = line
    new_imports = sorted({p[3] for p in plan if p[3]})
    if new_imports:
        last = max(i for i, l in enumerate(lines) if l.startswith("import ")) if any(l.startswith("import ") for l in lines) else next(i for i, l in enumerate(lines) if l.startswith("package ")) + 0
        lines[last + 1:last + 1] = new_imports if any(l.startswith("import ") for l in lines) else [""] + new_imports
    path.write_text("\n".join(lines))
print("estimated total tokens saved:", total_saved_est)
