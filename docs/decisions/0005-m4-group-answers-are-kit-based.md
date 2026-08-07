---
status: accepted
date: 2026-07-16
decision-makers: ajkatz
---

# M-4 group answers: kit-based sets under a swap budget, greedy-merged, with BiS under the same budget

> Migrated in substance from the pre-MADR `DECISIONS.md` chain of
> 2026-07-16 M-4 entries (addendum + kits/budget/tab UI + BiS layer +
> Yours|BiS correction + max-swaps parameter). The chain's intermediate
> designs appear below as rejected options. See
> [ADR-0001](0001-follow-the-madr-convention.md).

## Context and Problem Statement

Field refinement of the multi-mob canvas
([ADR-0003](0003-results-area-is-a-multi-mob-canvas.md)), same day:
"another concept of a multi-result lookup where you are actually looking
up a set that is effective against the group... hybrid/tribrid with a
minimum of N gear swaps." Beyond showing multiple results, a monster group
should be queryable as ONE answer: what do I actually wear and carry for
the whole trip?

## Decision Drivers

* The answer must be explainable slot by slot, not a black-box search.
* Inventory slots are the real currency — the budget must count items, not
  abstractions.
* A ceiling comparison is only fair if BiS plays under the same rules.
* Hybrid knowledge must not become a curated maintenance treadmill.

## Considered Options

* Greedy-merge from cached per-monster results, kit-structured, swap-item
  budget, BiS under the same budget (chosen composite)
* Joint beam over the full multi-monster space
* Curated hybrid-set tables
* Budget counts kits or switch clicks instead of swap items
* Yours|BiS as a per-kit-tab toggle (the corrected intermediate design)

## Decision Outcome

Chosen option: the composite design.

**Search:** greedy-merge from the members' independent best sets, pricing
each slot compromise until the swap budget holds — every step explainable,
and it runs on already-cached per-monster results. Hybrid knowledge is NOT
curated: void tribrid must emerge from slot-sharing economics (the
validation case).

**Kits:** the kit is M-4's structural unit — capped naturally at 3
(tribrid) / 2 (hybrid), one per combat style in use. Kit type derives from
the attack style USED against each mob, not weapon category (melee-cast
staves label melee; a salamander is one item serving multiple kits).

**Budget:** the budget control (slider + text entry) counts SWAP ITEMS —
intentional extra inventory slots — not kits or switch clicks; it shows
the dps retained at each budget and ties into inventory planning. A
separate "Max swaps: 0/1/2" search parameter bounds kit TRANSITIONS
(kits − 1): 0 mono-style, 1 hybrid, 2 tribrid — orthogonal to the item
budget. Max swaps 0 on a group still answers with one worn set for the
whole roster. A config option hides the control and locks 0 for every
search (DisplayOptions visibility pattern); the parameter records into
back/forward and cache keys like every other panel parameter.

**BiS layer:** the BiS group answer is computed under the SAME swap-item
budget and at the player's own levels, so the percentage isolates the gear
gap. The header carries a dual verdict: "% of your max" (the compromise
cost) and "% of BiS" (the gear gap). The Yours|BiS toggle sits ABOVE the
kit tabs and swaps the entire answer block — the BiS answer is a complete
independent solution that may use a different style combo, kit count, and
swap composition than your best available.

**UI:** the result card's tab strip
([ADR-0004](0004-style-tabs-and-per-result-parameters.md)) renders kits;
each tab shows the worn view for that kit with shared pieces constant;
collapsed mob rows show which kit to bring + the yours/BiS dps pair.

Deferred to build time: the objective per group (weighted-sum with
rotation shares vs maximize-min dps).

### Consequences

* Good, because the answer decomposes into auditable slot decisions and
  reuses the per-monster cache.
* Good, because BiS-under-same-budget makes the ceiling comparison fair
  rather than aspirational.
* Bad, because greedy merge is not globally optimal; if it visibly misses,
  the joint search question reopens.

### Confirmation

Not yet built (the M-4 phase of the canvas arc). On implementation: an
optimizer-level test asserting void tribrid emerges from slot-sharing
economics without curation is the validation case this ADR names.

## Pros and Cons of the Options

### Joint beam over the full multi-monster space

* Bad, because of combinatorial blowup, and the result resists slot-level
  explanation.

### Curated hybrid-set tables

* Bad, because a maintenance treadmill — and the engine can discover
  sharing (salve/void/barrows-gloves class items) from slot economics
  directly.

### Budget counts kits or switch clicks

* Bad, because players reason in inventory slots; kits are already bounded
  at 2-3 and clicks don't cost bag space.

### Yours|BiS as a per-kit-tab toggle

* Bad, because "the best bis hybrid may not be the same style combo as
  your best available" — a tab-level toggle presumes matching kit
  structure, which the correction entry caught before build.
