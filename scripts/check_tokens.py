#!/usr/bin/env python3
"""Plugin Hub source-size gate.

The RuneLite Plugin Hub reviewer bot reads the plugin's MAIN source (comments
stripped; tests, resources and the readme excluded) and declines automatic
review above 200,000 tokens. This script estimates the bot's count so a
breach is caught BEFORE the hub PR, not by the bot's comment on it.

Calibration (2026-10-31, two PRs): the bot's figure sits 0.9-1.6% above a
proper comment strip (a small lexer: strings kept whole, every // and block
comment out) counted with tiktoken's o200k_base - 202k at 198,835 (0.7.1,
plugin-hub #19484), 204k at 202,192 (0.8.0, #19799). The old regex strip
undercounted a further 1.2% (it cut code after a "//" inside a string). So:
bot ~= lexed o200k x 1.015, and THAT estimate is what the gate compares to
the bot's 200,000. Without tiktoken a char/token estimate stands in, high.

Run directly, or via `./gradlew checkTokens` (and it's part of `preSubmit`).
"""
import glob
import os
import sys

CAP = 200_000          # the bot's line, in the BOT's tokens
BOT_OVER_LEXED = 1.015 # the bot's count over our lexed o200k count (see above)
WARN_AT = 195_000      # plan the next externalisation above this (bot tokens)
CHARS_PER_TOKEN = 3.6  # fallback only; low = conservative

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def strip_comments(src: str) -> str:
    """Every // and /* */ comment out; string and char literals kept whole."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if c in "\"'":
            j = i + 1
            while j < n and src[j] != c:
                if src[j] == "\\":
                    j += 1
                j += 1
            out.append(src[i:j + 1])
            i = j + 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def main() -> int:
    try:
        import tiktoken
        enc = tiktoken.get_encoding("o200k_base")
        count = lambda s: len(enc.encode(s, disallowed_special=()))
        method = "tiktoken o200k_base, lexed, x%.3f" % BOT_OVER_LEXED
    except Exception:
        count = lambda s: round(len(s) / CHARS_PER_TOKEN)
        method = f"char/{CHARS_PER_TOKEN} estimate (install tiktoken for the real count)"

    files = glob.glob(os.path.join(ROOT, "src/main/java/**/*.java"), recursive=True)
    per = []
    lexed = 0
    for p in files:
        t = count(strip_comments(open(p, encoding="utf-8", errors="ignore").read()))
        lexed += t
        per.append((t, os.path.relpath(p, ROOT)))
    total = round(lexed * BOT_OVER_LEXED)

    pct = total / CAP * 100
    print(f"Plugin Hub source size - {method}")
    print(f"  lexed count: {lexed:,}")
    print(f"  main source: {total:,} / {CAP:,} tokens  ({pct:.0f}% of the bot's cap, {CAP - total:,} headroom)")
    print(f"  (tests, resources and the readme are not counted by the bot)")

    if total >= WARN_AT:
        print("\n  largest files (externalize hardcoded data -> resources to shrink):")
        for t, rel in sorted(per, reverse=True)[:8]:
            print(f"    {round(t * BOT_OVER_LEXED):>7,}  {rel.replace(os.sep, '/').split('com/loadoutlab/')[-1]}")

    if "estimate" in method and total >= WARN_AT:
        print("\nx Within reach of the cap on the ESTIMATE only - the verdict needs the exact count.")
        print("  pip install tiktoken (and run without python -I), then run again.")
        return 1
    if total >= CAP:
        print(f"\nx OVER the bot's {CAP:,}: automatic review is declined; manual review is the fallback.")
        print("  Reduce before pinning - data tables to resources, prose to text.tsv, real dedup.")
        return 1
    if total >= WARN_AT:
        print(f"\n! Approaching the cap (warn at {WARN_AT:,}). Plan the next externalization.")
        return 0
    print("\nok - under the bot's cap.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
