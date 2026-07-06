#!/usr/bin/env python3
"""Compare Loadout Lab's engine against the official wiki DPS calculator.

The official calculator (dps.osrs.wiki) has no compute API; its engine is
open source. This runs BOTH engines on the same scenario vectors and prints
the deltas - the dispute resolver.

Requires a local clone of weirdgloop/osrs-dps-calc (GPL-3; kept OUTSIDE this
BSD repo - only scenario JSON crosses the boundary) with the
LoadoutLabVectors.test.ts harness and yarn deps installed.

Usage: python3 scripts/verify_official.py [--calc-dir ~/Development/osrs-dps-calc]
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_HOME = "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home"


def run(cmd, cwd, env=None):
    e = dict(os.environ, JAVA_HOME=JAVA_HOME)
    if env:
        e.update(env)
    result = subprocess.run(cmd, cwd=cwd, env=e, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout[-3000:])
        print(result.stderr[-3000:])
        raise SystemExit("command failed: " + " ".join(cmd))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--calc-dir", default=os.path.expanduser("~/Development/osrs-dps-calc"))
    parser.add_argument("--sweep", action="store_true",
                        help="adjudicate the optimizer's own game-best picks across a monster battery")
    args = parser.parse_args()

    work = tempfile.mkdtemp(prefix="loadout-lab-verify-")
    print("workdir:", work)

    print("1/3 exporting vectors from the Loadout Lab engine...")
    export_env = {"LOADOUT_LAB_VECTORS": work}
    if args.sweep:
        export_env["LOADOUT_LAB_SWEEP"] = "1"
    run(["./gradlew", "cleanTest", "test", "--tests", "*OfficialVectorExport"],
        cwd=REPO, env=export_env)

    print("2/3 running the official calculator on the same vectors...")
    run(["corepack", "yarn", "jest", "src/tests/harness/LoadoutLabVectors.test.ts"],
        cwd=args.calc_dir,
        env={
            "LOADOUT_LAB_VECTORS": os.path.join(work, "vectors.json"),
            "LOADOUT_LAB_RESULTS": os.path.join(work, "official.json"),
        })

    print("3/3 comparing...\n")
    ours = {r["name"]: r for r in json.load(open(os.path.join(work, "ours.json")))}
    official = {r["name"]: r for r in json.load(open(os.path.join(work, "official.json")))}

    header = "%-24s %10s %10s %7s | %6s %6s | %7s %7s" % (
        "scenario", "our dps", "wiki dps", "delta", "our mx", "wik mx", "our acc", "wik acc")
    print(header)
    print("-" * len(header))
    worst = []
    for name, mine in ours.items():
        theirs = official.get(name)
        if not theirs or "error" in theirs:
            print("%-24s %10.3f %10s" % (name, mine["dps"],
                  "ERROR: " + theirs["error"][:60] if theirs else "missing"))
            continue
        delta = (mine["dps"] - theirs["dps"]) / theirs["dps"] * 100 if theirs["dps"] else 0.0
        print("%-24s %10.3f %10.3f %6.1f%% | %6d %6d | %7.3f %7.3f %s" % (
            name, mine["dps"], theirs["dps"], delta,
            mine["maxHit"], theirs["maxHit"], mine["accuracy"], theirs["accuracy"],
            mine.get("weapon", "")))
        worst.append((abs(delta), name))
    worst.sort(reverse=True)
    if worst:
        print("\nlargest deltas:", ", ".join("%s (%.1f%%)" % (n, d) for d, n in worst[:5]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
