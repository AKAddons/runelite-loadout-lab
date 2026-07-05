#!/usr/bin/env python3
"""Plugin Hub source-size gate.

The RuneLite Plugin Hub review bot reads the plugin's MAIN source (the whole
codebase, comments stripped, tests EXCLUDED) and refuses to review it above a
hard 200,000-token cap. This script estimates that count so we catch a breach
BEFORE submitting / re-pinning the hub PR — not after every edit.

It prefers the real OpenAI tokenizer (`pip install tiktoken`) for an exact
count; without it, it falls back to a calibrated char/token estimate that
deliberately runs a touch high (fails safe). Either way it leaves a buffer
under 200k for tokenizer differences between this and the bot.

Run directly, or via `./gradlew checkTokens` (and it's part of `preSubmit`).
"""
import glob
import os
import re
import sys

CAP = 200_000          # the bot's hard limit
FAIL_AT = 195_000      # block submit here. The bot's tokenizer counts a touch
                       # LOWER than ours (~260k vs our 268k pre-reduction), so a
                       # 195k reading here is comfortably under the bot's 200k.
WARN_AT = 188_000      # getting close; plan a data externalization soon
CHARS_PER_TOKEN = 3.6  # calibrated on this Java codebase (real ratio ~3.66); low = conservative

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def strip_comments(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"^\s*//.*$", "", src, flags=re.M)
    return re.sub(r"//.*$", "", src, flags=re.M)


def main() -> int:
    try:
        import tiktoken
        enc = tiktoken.get_encoding("o200k_base")
        count = lambda s: len(enc.encode(s))
        method = "tiktoken o200k_base (exact)"
    except Exception:
        count = lambda s: round(len(s) / CHARS_PER_TOKEN)
        method = f"char/{CHARS_PER_TOKEN} estimate (install tiktoken for exact)"

    files = glob.glob(os.path.join(ROOT, "src/main/java/**/*.java"), recursive=True)
    per = []
    total = 0
    for p in files:
        t = count(strip_comments(open(p, encoding="utf-8", errors="ignore").read()))
        total += t
        per.append((t, os.path.relpath(p, ROOT)))

    pct = total / CAP * 100
    print(f"Plugin Hub source size — {method}")
    print(f"  main source: {total:,} / {CAP:,} tokens  ({pct:.0f}% of cap, {CAP - total:,} headroom)")
    print(f"  (tests are NOT counted by the bot)")

    if total >= WARN_AT:
        print("\n  largest files (externalize hardcoded data → resources to shrink):")
        for t, rel in sorted(per, reverse=True)[:8]:
            print(f"    {t:>7,}  {rel.split('com/goalplanner/')[-1]}")

    if total >= FAIL_AT:
        print(f"\n✗ OVER the submit threshold ({FAIL_AT:,}). The hub bot will refuse to review.")
        print("  Reduce before submitting — move big static data tables out of .java into")
        print("  resource files loaded at startup (TSV for flat, JSON via Gson for nested).")
        return 1
    if total >= WARN_AT:
        print(f"\n⚠ Approaching the cap (warn at {WARN_AT:,}). Plan a data externalization soon.")
        return 0
    print("\n✓ Comfortably under the cap.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
