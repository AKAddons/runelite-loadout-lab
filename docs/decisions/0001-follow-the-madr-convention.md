---
status: accepted
date: 2026-07-29
decision-makers: ajkatz
---

# This repo records architecture decisions as MADR files

## Context and Problem Statement

Decisions lived in a single append-only `DECISIONS.md`. By 0.3.x it had grown
into a 200+ line scroll where founding architecture (the engine fork) sits
beside same-day design corrections, with no per-decision status, dates buried
in headings, and no way to cite one decision from another. The goal-planner
repos adopted [MADR](https://adr.github.io/madr/) for exactly this; the
question was whether to follow suit here.

## Considered Options

* Adopt MADR: one file per decision under `docs/decisions/`, `DECISIONS.md`
  becomes an index
* Keep the append-only `DECISIONS.md` log

## Decision Outcome

Chosen option: "Adopt MADR", matching the convention runelite-goal-planner
set (its ADR-0001). One file per decision in `docs/decisions/NNNN-*.md`,
started from [`0000-adr-template.md`](0000-adr-template.md). Everything
except Context, Considered Options, and Decision Outcome is optional.
Numbers are identifiers, not chronology — `date:` in the frontmatter carries
the real ordering. `DECISIONS.md` stays at the repo root as a pure index.

The pre-MADR log was migrated in substance, consolidated by decision: a
same-day chain of addendum/clarification/correction entries collapses into
one ADR whose rejected intermediate designs become considered options.

Numbering restarts per repo. Decisions in other repos are cited in full —
`runelite-goal-planner ADR-0001` — never as a bare number, since a bare
number means something different in each repo.

### Consequences

* Good, because each decision carries its own status, date, and alternatives,
  and can be superseded without rewriting a shared file.
* Good, because merge conflicts on the decision log disappear — parallel
  branches add files instead of editing one.
* Bad, because in-flight branches that appended to the old `DECISIONS.md`
  must convert their entry to an ADR on merge.

### Confirmation

Review only: new significant decisions get an ADR (the `/decision` flow),
and `DECISIONS.md` gains only index rows.
