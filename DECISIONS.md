# Decisions

## 2026-07-05: D1 — DPS engine source

**Decision:** Fork guccifurs/best-dps's BSD-2-Clause engine (its `calc/`
DpsCalculator + RollMath + BestDpsOptimizer, ~1,200 tested lines, current
mechanics) as Loadout Lab's engine seed. Monster data: vendor the wiki team's
weirdgloop JSON (monsters + equipment aliases) as gzipped resources, refreshed
per release. Player gear bonuses: RuneLite core `ItemManager.getItemStats`
(zero-maintenance). All computation local; no network at query time.

**Alternatives considered:**
- Port weirdgloop/osrs-dps-calc to Java — rejected: GPL-3.0 would force the
  whole plugin GPL, and the port is ~6,100 lines / 2-4 weeks before any
  product work (the unpublished `Alexsbuchanan/bossbis` port + parity corpus
  proves feasibility but inherits the license and weekly formula-drift
  maintenance).
- Interop with an existing plugin's engine (PluginMessage) — rejected: hub
  plugins cannot compile against each other (`@PluginDependency` works only
  toward core plugins), best-dps exposes no message surface, and a
  compute-over-messages hop defeats the caching requirement.
- Remote calculation (dps.osrs.wiki / MCP) — rejected: the wiki calculator is
  fully client-side (static export, no compute API), and network-at-query-time
  kills both cache design and hub UX.

**Rationale:** Research (2026-07-05) found best-dps already ships ~90% of our
v0.1 on the hub. Forking its BSD engine gets a working, current calc for free
and keeps licensing unencumbered — so effort goes into Loadout Lab's actual
identity: the plugin best-dps isn't. Differentiators = its verified gaps:
persistent cross-session ownership ledger (untradeables included),
spec-weapon-in-set, full-inventory trip planning, exhaustive-not-beam search
where tractable.

**Context:** Founding architecture decision for the new plugin; the duplication
discovery reframed the project from "build a DPS calculator" to "build the 10%
that doesn't exist." Licensing rule recorded in CLAUDE.md: never copy GPL
weirdgloop code; its data JSON is wiki content (CC BY-NC-SA, keep attribution);
retain best-dps's BSD-2 license text with derived code.

## 2026-07-16: Results area becomes a multi-mob canvas (v0.3.0 headline arc)

**Decision:** The results area renders a LIST of collapsible monster cards
instead of a single monster's result. A list of one is pixel-identical to
today's UI. Phased: M-1 list refactor → M-2 multi-add UX (add-to-view,
close/collapse/reorder, pages join back/forward history) → M-3 curated
monster groups ("Zulrah (all forms)", "Dagannoth Kings", GWD rooms,
Barrows) → M-4 group synthesis (one carry set scored across the page +
per-room switches). Favorites/save is subsumed: saved PAGES replace saved
single loadouts. The old v0.3 trip plan renumbers to v0.4 and builds on M-4.

**Alternatives considered:**
- Keep single-result view, add a separate "compare" screen — rejected: a
  second surface duplicates the style-card renderer and splits history/
  pins/notes semantics; the list generalizes the existing view instead.
- Per-monster tabs — rejected: tabs hide the cross-mob comparison that is
  the point (DKs, Zulrah forms) and fight the sidebar's vertical model.
- Jump straight to raid support without the canvas — rejected: every raid/
  slayer-planner feature needs a multi-mob surface first; building it once
  as the general case avoids a raids-only dead end.

**Rationale:** Matches how combat actually works (multiple mobs per trip);
the style-card level already collapses, so this adds one level above with
the single view as the degenerate case. It is the enabling layer for the
audit's highest-value gaps (raid scaling, phase-weighted bosses, slayer
task planning).

**Key design calls:** global toggles act as DEFAULTS resolved per-card
against each monster's own gating (the applySelection rules, per card);
monster cards collapse to a one-line best-style summary (vertical-budget
rule); OptimizerService gets a page-scoped supersession ticket with
progressive card fill-in; monster groups must be curated against LOADED
data rows, not raw wiki names (the stat-key collapse merges versions).

**Context:** Field request 2026-07-16 during the audit-fixes/back-forward
session: "each result is in a collapsible card and we can show multiple
results on a page... a precursor for raids, all Zulrah versions at once,
all Dagannoth Kings at once."

## 2026-07-16 (addendum): M-4 is a group LOOKUP - the hybrid/tribrid assistant

**Decision:** Beyond showing multiple results (M-1..M-3), a monster group is
itself queryable: one answer against the whole group - a worn base kit plus
a carried swap set under a swap budget N. Implementation: greedy-merge from
the members' independent best sets, pricing each slot compromise, until the
swap budget holds; surfaced as a slider showing the dps retained at each
budget. Hybrid knowledge is NOT curated - void tribrid must emerge from
slot-sharing economics (the validation case).

**Alternatives considered:**
- Joint beam over the full multi-monster space - rejected for v1:
  combinatorial blowup; the greedy merge works on already-cached
  per-monster results and every step is explainable.
- Curated hybrid-set tables - rejected: a maintenance treadmill, and the
  engine can discover sharing (salve/void/barrows-gloves class items)
  from the slot economics directly.

**Open (deferred to build time):** objective per group (weighted-sum with
rotation shares vs maximize-min dps); whether N counts carried switch
items (lean - ties into v0.4 inventory planning) or switch clicks.

**Context:** Field refinement of the multi-mob canvas, same day: "another
concept of a multi-result lookup where you are actually looking up a set
that is effective against the group... hybrid/tribrid with a minimum of N
gear swaps."

## 2026-07-16 (clarification): canvas hierarchy is Page -> Results -> Mobs

**Decision:** The multi-mob canvas is three levels: the PAGE holds RESULT
cards; each result is one QUERY holding 1..N MOB sections. Save and close
(X) are RESULT-level affordances. Favorites = saved results (query + mobs
+ params, re-run against current gear on load). Groups must scale to long
wave sequences (bat, blob, ... Jad), so mob sections collapse individually
and result headers summarize deep lists. M-4's hybrid/swap-budget answer
renders at the result level, above its mob sections.

**Context:** User sketch, same session: "result 1 (Save / X): Graardor;
result 2: Zulrah Tanzanite/Serpentine/Magma; result 3: bat, blob, ... Jad."
