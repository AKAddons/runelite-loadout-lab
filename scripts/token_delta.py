#!/usr/bin/env python3
"""Per-file token deltas against a saved baseline.

The hub cap is a whole-codebase number, so a reduction arc needs to see
WHERE tokens moved, not just the total - a refactor that saves 3k in one
file while quietly adding 2k of loader elsewhere reads as a win on the
total alone. Counts exactly like scripts/check_tokens.py (tiktoken
o200k_base, comments stripped, src/main/java only).

    python3 scripts/token_delta.py --save baseline.json    # snapshot now
    python3 scripts/token_delta.py baseline.json           # report deltas

This totals a few hundred tokens BELOW check_tokens.py because it encodes
each file separately while the gate encodes the whole corpus at once (token
boundaries differ at the joins). check_tokens.py stays the authority on
"will the bot accept it"; this script is for WHERE the tokens moved.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def strip_comments(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"^\s*//.*$", "", src, flags=re.M)
    return re.sub(r"//.*$", "", src, flags=re.M)


def counts() -> dict:
    try:
        import tiktoken
        enc = tiktoken.get_encoding("o200k_base")
        measure = lambda s: len(enc.encode(s))
    except ImportError:
        print("! tiktoken missing - falling back to the char estimate", file=sys.stderr)
        measure = lambda s: int(len(s) / 3.6)
    out = {}
    for path in glob.glob(os.path.join(ROOT, "src/main/java/**/*.java"), recursive=True):
        rel = os.path.relpath(path, ROOT)
        with open(path, encoding="utf-8") as handle:
            out[rel] = measure(strip_comments(handle.read()))
    return out


def main() -> int:
    args = sys.argv[1:]
    if args and args[0] == "--save":
        target = args[1] if len(args) > 1 else "token-baseline.json"
        data = counts()
        with open(target, "w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=1, sort_keys=True)
        print(f"saved {len(data)} files, {sum(data.values()):,} tokens -> {target}")
        return 0

    if not args:
        print(__doc__)
        return 2
    with open(args[0], encoding="utf-8") as handle:
        base = json.load(handle)
    now = counts()

    rows = []
    for rel in sorted(set(base) | set(now)):
        before, after = base.get(rel, 0), now.get(rel, 0)
        if before != after:
            rows.append((after - before, before, after, rel))
    rows.sort()

    total_before, total_after = sum(base.values()), sum(now.values())
    for delta, before, after, rel in rows:
        tag = "DELETED" if after == 0 else ("NEW" if before == 0 else "")
        short = rel.replace("src/main/java/com/loadoutlab/", "")
        print(f"{delta:+8d}  {before:7d} -> {after:7d}  {short} {tag}")
    if not rows:
        print("no per-file changes")
    print()
    print(f"TOTAL {total_before:,} -> {total_after:,}  ({total_after - total_before:+,})")
    print(f"cap 200,000 | submit-blocker 195,000 | headroom {200_000 - total_after:,}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
