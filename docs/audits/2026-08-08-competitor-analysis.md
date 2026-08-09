# Competitor analysis: spec-weapon and sustained modeling (2026-08-08)

Two-agent sweep: source-level reads of `~/Development/osrs-dps-calc` (the
wiki calculator) and `~/Development/bis-gear-plugin` (hub competitor),
plus a web survey of the wider ecosystem. Trigger: "how are other
engines doing this and what are we missing" during the 0.3.5 draft
window.

## The field

| Tool | Spec modeling | Sustained / KPH | Notes |
|---|---|---|---|
| **dps.osrs.wiki** (weirdgloop) | Full second calc in "spec mode": per-weapon accuracy/max multipliers, defence-style swaps (godswords/claws roll slash), exact multi-hit distributions (claws 4-roll cascade, dark bow floor), guaranteed-hit specs (Voidwaker), amortized `specFullDps` by regen with Lightbearer halving. NO value-over-replacement, no per-kill budget, no carried-spec concept. Spec epic #141 open since Jan 2024. | Exact HTK/TTK from hit distributions, TTK graphs. No KPH/overkill/trips. | Per-boss DEFENCE FLOORS for DWH/maul stacking (Nex 250, Sotetseg 100, Zebak 50...). Per-step audit trail (DetailKey). Comparator curves with "DWH x1/x2" annotations. |
| **BiS Gear** (hub, Diogo-G-Dias) | Stubbed: `Result.spec = null` after the GPL-port relicense; expected-value-only clean-room engine. Spec weapons survive pruning via an effect-item whitelist but score as autos. | None. | Multi-seed hill climb + pinned complete sets + 2-slot pair pass (gearscape lineage). Sprite spinner rows for defence reductions; "assumed" provenance lines. |
| **Best DPS** (hub, guccifurs) | None. | None. | GP-budget knob, quest-unlock gating (`QuestUnlocks.java`), auto spell pick. ~1.3k installs. |
| **Bitterkoekje sheet / OSRS Genie** | Manual what-if (type the post-DWH defence yourself). | Avg HP/dps kill time. | Legacy; superseded. |
| **RuneBuddy** (web) | Monte Carlo boss simulator: specs in rotation, 10%/30s regen, Lightbearer doubling, Death Charge + Rite, drain stacking. | TTK percentiles (median/90th/99th), **kills/hour headline**, phase-aware sims with per-phase swaps. | Closest conceptual overlap; simulation where we are analytic; web-only, no in-client, no trip planning, no bring-verdict. |
| **Hub otherwise** | Special Attack Counter (tracks landed drains — tracking, not planning); LlemonDuck's calc archived. | — | The in-client optimizer field is effectively Best DPS + BiS Gear + us. |

## Where we are alone

No tool anywhere does: analytic **spec value-over-replacement**, the
**per-kill sustained energy budget** (fractional specs/kill), **drain
FISHING expected value** (land probability, expected specs spent),
**Lightbearer opportunity-cost arbitration**, **carried spec weapon
competing for a seat**, or **trip planning** (rosters, swaps, supplies,
required gear). The wiki's own spec epic is stalled; the hub competitor
deliberately stubbed specs.

## Gaps in US (verified against their sources)

1. **Per-boss defence floors in drain fishing** (wiki calc
   `DefenceReduction.ts:22-64`): our `drainedDefence` has no floor, so
   DWH value is overstated at floored bosses (Nex floors at its base
   250 - drain there is worth ZERO; Sotetseg 100, Nightmare 120, Akkha
   70, Baba 60, Kephri 60, Zebak 50, Warden P3 120, ToA obelisk 60,
   Araxxor 90, Hueycoatl 120, Yama 145, Verzik/Vardorvis base).
   CORRECTNESS BUG in the shipped fishing model. Small fix: floor
   table as a resource + clamp in drainedDefence.
2. **Overkill**: nobody models it, including us - spec expected damage
   is credited in full even when it exceeds remaining HP. A cheap
   analytic haircut (cap expected by average remaining HP at spec
   time, or `min(expected, hp/2)` style bound) would trim burst-spec
   value at low-hp mobs. Affects claws-at-task-mobs pricing.
3. **Spec-mode fidelity audit** vs their multiplier table
   (`PlayerVsNPCCalc.ts:305-1000`): verify our SpecialAttack registry
   agrees on defence-style swaps (godswords/claws roll SLASH on spec),
   guaranteed hits (Voidwaker), min-hit rules (Voidwaker min = half
   max, dark bow 48 floor with dragon arrows), and the Sunspear x0.7.
   Audit task, not a rewrite.
4. **Kills-per-hour as a surfaced number** (RuneBuddy's headline): we
   HAVE the sustained machinery - ttk, specs/kill, supplies - but
   surface dps-added only. Every field dispute today was argued in
   kph currency; show it (card stat line or tooltip: "~237 kills/hr").
5. **TTK from hit distributions + percentiles** (wiki exact HTK,
   RuneBuddy percentiles): our ttk = hp/dps average. Real fidelity gap
   but heavy (distribution machinery, token cost) - roadmap tier.
6. **Quest-unlock gating** of candidates (Best DPS): we gate on levels
   (RequirementProfile), not quest lines (Barrows gloves...). Small,
   data-driven, roadmap.
7. Raid party-size/CM scaling beyond ToA invocation - already task #14.
8. Phase-aware Monte Carlo simulation (RuneBuddy) - out of scope for a
   hub plugin; our analytic identity is the differentiator, keep it.

Already have, contrary to first impressions: GP upgrade budget, auto
spell selection, incoming-damage calc, elemental weakness, Death
Charge + Rite in the energy budget, share-to-wiki-calc (0.3.6 arc).

## Recommended order

1. Defence floors (correctness, small) - next batch.
2. Overkill haircut (correctness, small-medium) - with it.
3. Spec-fidelity audit vs their table (verification pass).
4. KPH surfacing (product, cheap, matches the user's mental model).
5. Roadmap: TTK percentiles, quest gating.
