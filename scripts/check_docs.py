#!/usr/bin/env python3
"""Documentation-gap auditor for the feature registry.

Cross-checks docs/features.json against the README feature guide, docs/img/,
and the source tree, then prints a gap report:

  STRUCTURAL (exit 1) — registry/guide drift that must be fixed:
    * registry entry whose README section is missing (anchor check)
    * README feature heading (### in the feature guide) with no registry entry
    * media referenced by the README but absent from the registry
    * code/test path in the registry that no longer exists

  GAPS (exit 0, reported) — work outstanding, tracked on purpose:
    * media files not yet recorded
    * features with no automated tests
    * orphaned files in docs/img/ no feature references

The feature guide is the part of the README under the "# Feature guide"
heading; ### headings only count as features there (so README sections like
## Getting started don't trip the audit). Run via `./gradlew checkDocs`.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
SRC_MAIN = ROOT / "src/main/java/com/loadoutlab"
SRC_TEST = ROOT / "src/test/java/com/loadoutlab"


def slugify(heading: str) -> str:
    s = heading.strip().lower()
    s = re.sub(r"[^\w\s-]", "", s)
    return re.sub(r"[\s]", "-", s)


def feature_guide(readme: str) -> str:
    """The README region from '# Feature guide' to the next '# ' (or EOF)."""
    m = re.search(r"^# Feature guide\s*$", readme, re.M)
    if not m:
        return ""
    rest = readme[m.end():]
    nxt = re.search(r"^# ", rest, re.M)
    return rest[: nxt.start()] if nxt else rest


def main() -> int:
    registry = json.loads((DOCS / "features.json").read_text())["features"]
    guide = feature_guide((ROOT / "README.md").read_text())

    guide_anchors = {slugify(m) for m in re.findall(r"^### (.+)$", guide, re.M)}
    guide_media = set(re.findall(r"!\[[^\]]*\]\((docs/img/[^)]+)\)", guide))

    structural: list[str] = []
    gaps: list[str] = []

    seen_anchors, seen_media = set(), set()
    for f in registry:
        fid = f["id"]
        seen_anchors.add(f["anchor"])
        if f["anchor"] not in guide_anchors:
            structural.append(f"[{fid}] README feature guide has no '### …' section with anchor '{f['anchor']}'")
        if f.get("media"):
            seen_media.add(f["media"])
            if not (ROOT / f["media"]).exists():
                cap = f.get("capture", "")
                gaps.append(f"[{fid}] media not recorded yet: {f['media']}" + (f"  (capture: {cap})" if cap else ""))
        for rel in f.get("code", []):
            if not (SRC_MAIN / rel).exists():
                structural.append(f"[{fid}] code path gone: src/main/java/com/loadoutlab/{rel}")
        for rel in f.get("tests", []):
            if not (SRC_TEST / rel).exists():
                structural.append(f"[{fid}] test path gone: src/test/java/com/loadoutlab/{rel}")
        if not f.get("tests") and f.get("code"):
            gaps.append(f"[{fid}] no automated tests ({f.get('notes', 'no notes')})")

    for anchor in sorted(guide_anchors - seen_anchors):
        structural.append(f"README feature '#{anchor}' has no registry entry")
    for media in sorted(guide_media - seen_media):
        structural.append(f"README references {media} but no registry entry claims it")
    img_dir = DOCS / "img"
    if img_dir.is_dir():
        for p in sorted(img_dir.iterdir()):
            if p.name.startswith("."):
                continue  # .gitkeep and other dotfiles are not media
            if p.name == "hero.png":
                continue  # the README masthead lives above the audited guide
            if p.is_file() and f"docs/img/{p.name}" not in seen_media:
                gaps.append(f"orphaned media: docs/img/{p.name} (no registry entry)")

    recorded = sum(1 for f in registry if f.get("media") and (ROOT / f["media"]).exists())
    with_media = sum(1 for f in registry if f.get("media"))
    print(f"feature registry: {len(registry)} features · media {recorded}/{with_media} recorded")

    if structural:
        print(f"\nSTRUCTURAL ({len(structural)}) — fix before shipping docs:")
        for s in structural:
            print(f"  x {s}")
    if gaps:
        print(f"\nGAPS ({len(gaps)}) — outstanding, tracked:")
        for g in gaps:
            print(f"  - {g}")
    if not structural and not gaps:
        print("no drift, no gaps — fully documented.")
    return 1 if structural else 0


if __name__ == "__main__":
    sys.exit(main())
