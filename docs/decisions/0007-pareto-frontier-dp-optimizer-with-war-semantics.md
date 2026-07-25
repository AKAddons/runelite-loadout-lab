---
status: accepted
date: 2026-07-24
decision-makers: ajkatz
---

# Replace the beam-search core with a Pareto-frontier DP carrying WAR semantics

> Migrated in substance from the pre-MADR `DECISIONS.md` entry "D-7". See
> [ADR-0001](0001-follow-the-madr-convention.md).

## Context and Problem Statement

The beam search is heuristic: its width cuts and the candidateScore
conditional-nudge system starve pools of items whose value lives only in
DpsCalculator's multipliers and set bonuses (a salve amulet has no attack
bonus, so domination prunes it before evaluation sees its multiplier).
Andrew also set a results contract (2026-07-24): "think of every result as
a ranked/WAR kind of assessment even if we aren't presenting it as such."

## Decision Drivers

* Exactness: the mode-necessity map measured real beam misses and real
  naive-frontier losses — both engines are wrong in different places.
* The hub token cap forbids shipping two engines.
* Ranking/WAR data must exist in every result even while the UI shows
  only #1.

## Considered Options

* Pareto-frontier DP per weapon × mode, beam deleted after migration
* Keep the beam and patch its nudge system per conditional
* Naive frontier without modes

## Decision Outcome

Chosen option: "Pareto-frontier DP per weapon × mode". Per weapon and
multiplier MODE (each mutually-exclusive conditional regime —
salve/slayer/avarice chain, void sets, dragonbane, wilderness weapons —
fixes its multipliers and slot constraints), slots fold into a frontier of
non-dominated bonus-sum states; full DPS evaluates only on the final
frontier. Exact where the beam is heuristic — the candidateScore
conditional-nudge system and its pool-starving bug class die with the
beam.

**WAR contract**: results carry per-slot replacement deltas (best set
without the chosen item, via prefix/suffix frontier composition) and
ranked-set margins (#1 vs #2). UI may show only #1; the margins exist in
the result.

**Migration**: differential harness first (DP ≥ beam on every golden +
randomized request; strict improvements cataloged as beam misses), then
swap and DELETE the beam — no dual-engine shipping (token cap, one
truth). Goldens re-baseline with every diff line justified as an
improvement — a deliberate exception to the byte-identical rule.

### Consequences

* Good, because conditional items can no longer be starved out of the
  search — mode eligibility equals pool eligibility by construction.
* Good, because WAR data falls out of the frontier structure instead of
  requiring extra search passes.
* Bad, because goldens must re-baseline at the swap, spending the
  byte-identical safety net for one migration.

### Confirmation

The differential harness (test-tree: ParetoPocTest exactness,
ParetoModeMapTest mode-necessity map) enforces DP ≥ beam until the swap;
after it, the re-baselined goldens gate as usual.

## Pros and Cons of the Options

### Patch the beam's nudge system per conditional

* Bad, because each new conditional item reopens the same pool-starving
  bug class; the nudges are the disease, not the cure.

### Naive frontier without modes

* Bad, because the mode-necessity map measured it losing up to ~15% on
  salve/avarice monsters — multiplicative conditionals defeat domination
  pruning by design.
