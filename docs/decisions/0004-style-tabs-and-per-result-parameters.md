---
status: accepted
date: 2026-07-16
decision-makers: ajkatz
---

# Style cards become a tab strip; search parameters move into each result

> Migrated in substance from the pre-MADR `DECISIONS.md` entry "2026-07-16
> (redesign): style tabs + per-result parameters (M-2c)". See
> [ADR-0001](0001-follow-the-madr-convention.md).

## Context and Problem Statement

First multi-result field look at the canvas
([ADR-0003](0003-results-area-is-a-multi-mob-canvas.md)): "with
tribrid/hybrid this is going to up the number of options from 3 to
possibly 7... buttons/tabs with just the style + dps... a toggle that
flips a single gear view... each mob should have its own parameter options
per search." Stacked style cards and a single global parameter row both
stopped scaling the moment a page held more than one result.

## Considered Options

* Tab strip per result + per-result parameter zone
* Keep stacked, individually-collapsible style cards with auto-collapse
* Keep parameters global for the whole page

## Decision Outcome

Chosen option: "tab strip per result + per-result parameter zone".

(a) The stacked style cards become a TAB STRIP (skill icon + dps per tab,
best selected by default) over one flipping detail body; assume chips and
the set menu move into the detail header; auto-collapse is removed. The
same strip renders kits at M-4 — hybrid/tribrid can push the option count
to ~7, which stacked cards cannot carry.

(b) Parameters move from the global row into each result: a compact
per-card chip row (on-task, wilderness, optimize mode; risk/budget behind
the card menu), owned by ResultEntry and read by computeEntry. The global
row keeps only search, back/forward, F2P and exclusions. Saved results
then serialize (mobs + own params) cleanly.

### Consequences

* Good, because the option count scales to hybrid/tribrid without eating
  vertical budget.
* Good, because a saved result is self-contained — its parameters travel
  with it.
* Bad, because two mobs on one page can now run under different
  parameters, which the UI must keep legible per card.

### Confirmation

Panel tests cover ResultEntry parameter ownership and serialization; tab
rendering is review-only.

## Pros and Cons of the Options

### Stacked cards with auto-collapse

* Bad, because ~7 options per result cannot stack in a sidebar; collapse
  churn replaces one scrolling problem with another.

### Global parameters

* Bad, because one row cannot express per-mob intent (on-task for the
  slayer target, wilderness for the rev card), and saved results would not
  round-trip their own settings.
