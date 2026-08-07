---
status: accepted
date: 2026-07-16
decision-makers: ajkatz
---

# The results area is a multi-mob canvas: a page of result cards, each holding 1..N mobs

> Migrated in substance from the pre-MADR `DECISIONS.md` entries of
> 2026-07-16 (headline arc + hierarchy clarification + card-anatomy spec).
> See [ADR-0001](0001-follow-the-madr-convention.md).

## Context and Problem Statement

Field request during the audit-fixes/back-forward session: "each result is
in a collapsible card and we can show multiple results on a page... a
precursor for raids, all Zulrah versions at once, all Dagannoth Kings at
once." The results area rendered exactly one monster's result; every
raid/slayer-planner ambition needed a multi-mob surface first.

## Decision Drivers

* Combat actually involves multiple mobs per trip; the UI modeled one.
* The audit's highest-value gaps (raid scaling, phase-weighted bosses,
  slayer task planning) all presuppose a multi-mob surface.
* The sidebar's vertical budget is scarce — hierarchy must collapse well.

## Considered Options

* A list of collapsible result cards, single result as the degenerate case
* Keep the single-result view and add a separate "compare" screen
* Per-monster tabs
* Jump straight to raid support without a general canvas

## Decision Outcome

Chosen option: "a list of collapsible result cards" — a list of one is
pixel-identical to the previous UI, so the general case subsumes the old
one instead of sitting beside it.

The canvas is three levels: the PAGE holds RESULT cards; each result is
one QUERY holding 1..N MOB sections. Save and close are result-level
affordances; favorites = saved results (query + mobs + params, re-run
against current gear on load). Saved PAGES replace saved single loadouts.
Mob sections collapse individually and result headers summarize deep lists
(groups must scale to long wave sequences — bat, blob, ... Jad).

Card anatomy, top to bottom: mob list (rows are an informational LENS —
one shared set per style optimized across the list; clicking a mob flips
which mob's numbers display); per-result parameter zone; style dps tabs;
Yours|BiS toggle; item view with info tiles; per-result bank show/filter.
Global toggles act as DEFAULTS resolved per-card against each monster's
own gating. Monster groups are curated against LOADED data rows, not raw
wiki names (the stat-key collapse merges versions). Delivery was phased
M-1 (list refactor) → M-2 (multi-add UX) → M-3 (curated groups) → M-4
(group synthesis, [ADR-0005](0005-m4-group-answers-are-kit-based.md)).

### Consequences

* Good, because it matches how combat works and is the enabling layer for
  raids, multi-form bosses, and slayer planning.
* Good, because the single view is the degenerate case — no parallel
  renderer to maintain.
* Bad, because result-scoped state (params, history, supersession) had to
  be rebuilt per-card: OptimizerService needed a page-scoped supersession
  ticket with progressive card fill-in.

### Confirmation

Golden and roster capture-and-diff nets exercise the card renderer; panel
tests cover result-entry lifecycle. Layout itself is review-only.

## Pros and Cons of the Options

### Separate "compare" screen

* Bad, because a second surface duplicates the style-card renderer and
  splits history/pins/notes semantics.

### Per-monster tabs

* Bad, because tabs hide the cross-mob comparison that is the point (DKs,
  Zulrah forms) and fight the sidebar's vertical model.

### Straight to raid support

* Bad, because every raid/slayer feature needs a multi-mob surface anyway;
  building it once as the general case avoids a raids-only dead end.
