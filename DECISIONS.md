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

## 2026-07-16 (refinement): M-4 kits, item budget, and tab UI

**Decision:** The kit is M-4's structural unit - capped naturally at 3
(tribrid) / 2 (hybrid), one per combat style in use. Kit type derives from
the attack style USED against each mob, not weapon category (melee-cast
staves label melee; a salamander is one item serving multiple kits). The
budget control (slider + text entry) counts SWAP ITEMS - intentional extra
inventory slots - not kits. UI: tabs between kits on the result card, each
tab showing the worn view for that kit with shared pieces constant;
collapsed mob rows show only which kit to bring + dps.

**Context:** User refinement over the first result-card mockup: "we max
out at 3 swaps (tribrid) or 2 (hybrid)... show the type of swap by the
style used (account for a staff in melee or a salamander)... slider/text
entry = number of swap items... tab view between swaps, collapsed mobs
showing which swap to bring."

## 2026-07-16 (refinement): M-4 BiS layer under the same budget

**Decision:** Group results keep the ceiling comparison. The BiS group
answer is computed under the SAME swap-item budget and at the player's own
levels, so the percentage isolates the gear gap (a BiS tribrid at N=4 is
the fair ceiling for your tribrid at N=4). Header carries a dual verdict:
"% of your max" (the compromise cost) and "% of BiS" (the gear gap). Each
kit tab hosts a Yours|BiS chip toggle instead of a second stacked grid
(vertical-budget rule); the gold BiS-proximity border language carries
over per slot; collapsed mob rows show the yours/BiS dps pair.

**Context:** User: "we also need to show the BiS versions as well and
account for that in design" - extending the single-result game-best
comparison into the multi-mob canvas.

## 2026-07-16 (correction): Yours|BiS toggles the whole answer, not a tab view

**Decision:** The BiS group answer is a complete independent solution - it
may use a different style combo, kit count, and swap composition than your
best available (a BiS 2-kit Shadow hybrid vs your 3-kit tribrid). The
Yours|BiS toggle therefore sits ABOVE the kit tabs and swaps the entire
answer block: tab strip, per-mob kit assignments, swaps. The dual header
verdict and per-mob dps pairs render regardless of the viewed side; mob
kit chips follow the viewed side.

**Context:** User caught the flaw in the kit-tab-level toggle: "the best
bis hybrid may not be the same style combo as your best available."

## 2026-07-16 (refinement): "Max swaps" search parameter + config lock

**Decision:** The search parameter area gains "Max swaps: 0/1/2" - the max
kit transitions (kits - 1): 0 mono-style, 1 hybrid, 2 tribrid. It is
orthogonal to the swap-item budget: swaps bound the kit count, the item
slider bounds the inventory slots spent achieving it. Max swaps 0 on a
group still answers: one worn set for the whole roster (style may vary
per mob only where the weapon permits, e.g. salamanders). A plugin config
option hides the control and locks 0 for every search (DisplayOptions
visibility pattern) for users who never want hybrid suggestions. The
parameter records into back/forward and cache keys like every other
panel parameter.

**Context:** User: "in the parameter area for search we should be able to
specify max swaps: 0/1/2 and then in the options we should be able to
hide this option (lock in 0 for every search)."

## 2026-07-16 (redesign): style tabs + per-result parameters (M-2c)

**Decision:** (a) The stacked, individually-collapsible style cards become
a TAB STRIP (skill icon + dps per tab, best selected by default) over one
flipping detail body; assume chips and the set menu move into the detail
header; auto-collapse is removed. The same strip renders kits at M-4 -
hybrid/tribrid can push the option count to ~7, which stacked cards cannot
carry. (b) Parameters move from the global row into each result: a compact
per-card chip row (on-task, wilderness, optimize mode; risk/budget behind
the card menu), owned by ResultEntry and read by computeEntry; the global
row keeps only search, back/forward, F2P and exclusions. Saved results
then serialize (mobs + own params) cleanly.

**Context:** First multi-result field look: "with tribrid/hybrid this is
going to up the number of options from 3 to possibly 7... buttons/tabs
with just the style + dps... a toggle that flips a single gear view...
each mob should have its own parameter options per search."

## 2026-07-16 (spec): the result card anatomy

**Decision:** The card reads top-to-bottom: mob list (name + hp; rows are
an informational LENS - one shared set per style optimized across the
list, clicking a mob flips which mob's numbers display); per-result
parameter zone (swaps, swappable items, best-prayer toggle default-on vs
pick-a-prayer, boost toggle, spellbook, weight, max worn cost, budget,
wilderness, antifire mode incl. detect-from-inventory, strategy); style
dps tabs; Yours|BiS tab toggle; item view with info tiles; per-result
bank show/filter. Simplifies M-4: the group answer is one set per style
across mobs - kits/swaps layer on top via parameters, and mob rows only
change the lens, never the set.

**Context:** User UX spec after the M-2c tab/tile field test.

## 2026-07-18 (impl): OptimizationRequest copies by clone, fields non-final

**Decision:** The 14 with- helpers on `OptimizationRequest` now copy via a
single `Object.clone()` instead of a hand-written mirror class, which
required dropping `final` from the 22 fields. That forfeits the Java Memory
Model's final-field safe-publication guarantee. Cleared as safe today:
requests are built on the coordinating thread and reach worker threads only
through `ExecutorService.invokeAll` or a single-thread executor, both of
which establish happens-before, and src/main contains no `parallelStream`,
ForkJoin, or `CompletableFuture`. **This constraint becomes load-bearing the
moment the optimizer adopts parallel streams or any other publication path
without a happens-before edge** - at that point the fields must go back to
final and the mirror (or a real builder) comes back.

**Context:** Plugin Hub main-source token cap (200k, tiktoken o200k_base,
comments stripped). The mirror was O(number of fields); clone is O(1).
