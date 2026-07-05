#!/usr/bin/env python3
"""Tofu-glyph guard for Swing / game-chat strings.

macOS Tahoe's Java font fallback renders certain BMP *symbol* glyphs as tofu
(empty boxes) in Swing components — bullets, arrows, triangles, the midline
ellipsis. The plugin draws real symbols as ShapeIcons and keeps UI/chat STRINGS
plain ASCII. This has bitten us twice (bullets, then arrows), so this guard
flags risky glyphs in Java string literals before they ship.

It scans double-quoted string literals in src/main/java, skipping comments and
log statements (log output isn't rendered by Swing, so arrows there are fine).
Plain punctuation that renders correctly — the ellipsis (…), em/en dashes,
smart quotes — is intentionally NOT flagged.

Run directly or via `./gradlew checkGlyphs` (part of `preSubmit`).
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Symbol + General-Punctuation glyphs known/suspected to tofu on macOS Tahoe's
# Swing font fallback. Keep UI/chat strings to ASCII: '->' '-' '...' '|' etc.
BAD_CHARS = set(
	"→←↑↓↔•◦‣⁃▶▼◀▲▸◂▾▴⋯☰≡"  # arrows, bullets, triangles, midline ellipsis
	"…—–·‘’“”"               # ellipsis, em/en dash, middle dot, smart quotes
)
BAD_ENTITIES = ["&rarr;", "&larr;", "&uarr;", "&darr;", "&bull;", "&middot;",
				"&hellip;", "&mdash;", "&ndash;", "&lsquo;", "&rsquo;", "&ldquo;", "&rdquo;"]
STRING_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
LOG_CALL = re.compile(r'\blog\.\w+\(')
# Same glyphs written as Java \uXXXX escapes (e.g. "Options…") render the
# same tofu at runtime but are pure ASCII in source, so match them too.
BAD_ESCAPE = re.compile(
	r'\\u(' + '|'.join(sorted({f"{ord(c):04x}" for c in BAD_CHARS})) + r')',
	re.IGNORECASE)

# ASCII replacements to suggest.
HINT = "use ASCII: '->' for arrows, '-'/'*' for bullets, '...' for ellipsis, or a ShapeIcon"


def main() -> int:
    hits = []
    for path in glob.glob(os.path.join(ROOT, "src/main/java/**/*.java"), recursive=True):
        for num, line in enumerate(open(path, encoding="utf-8", errors="ignore"), 1):
            stripped = line.strip()
            if stripped.startswith(("//", "*", "/*")) or LOG_CALL.search(line):
                continue
            text = "".join(STRING_LITERAL.findall(line))
            if not text:
                continue
            found = sorted({c for c in text if c in BAD_CHARS})
            found += [e for e in BAD_ENTITIES if e in text]
            found += sorted({"\\u" + m for m in BAD_ESCAPE.findall(text)})
            if found:
                rel = os.path.relpath(path, ROOT)
                hits.append((rel, num, "".join(found), stripped[:100]))

    if hits:
        print("✗ Tofu-prone glyphs in UI/chat string literals (macOS Tahoe renders these as boxes):")
        print(f"  {HINT}\n")
        for rel, num, found, snippet in hits:
            print(f"  {rel}:{num}  [{found}]  {snippet}")
        return 1
    print("✓ No tofu-prone glyphs in UI/chat strings.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
