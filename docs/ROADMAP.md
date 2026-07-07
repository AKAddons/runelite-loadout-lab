# Loadout Lab — roadmap

The founding requirements, phased. v0.1 = **BiS-from-owned vs enemy**: pick a
monster, get your strongest owned set per combat style with exact DPS.

## v0.1 — BiS from what you own

| # | Requirement | Approach |
|---|---|---|
| 1 | DPS calculator | Local engine (decision pending research: port of the wiki calculator's formulas vs alternatives — see Decisions below). Pure functions, no network at query time. |
| 2 | Owned-gear tracking | `collection/`: scan bank/inventory/equipment (patterns proven in goal-planner's ItemTracker: per-tick coalescing, bank-seen gating, logout caching), persist the collection per RuneLite config profile. |
| 3 | Gear knowledge per combat situation | `data/`: equipment stats via RuneLite `ItemManager` where core provides them; curated resource files for what core lacks. Resources don't count against the hub's 200k-token cap — big tables go there. |
| 4 | Exact DPS of the best set per style | Optimizer output is (set, style) → dps/max-hit/accuracy from the engine. |
| 5 | Caching | Optimizer results keyed by (collection fingerprint, monster id, style, boosts); collection fingerprint invalidates on container change (coalesced per tick). Engine-level memoization for repeated (gear, monster) pairs. Design in from day one. |
| 6 | Dynamic BiS vs a specific enemy | Slot-wise search over owned items with pruning (only Pareto-optimal owned items per slot per style enter the combinatorial step); monster traits (defence stats, attributes like dragon/demon/undead/kalphite, immunities) drive the engine. |

## v0.2 — the sharp edges

| # | Requirement | Approach |
|---|---|---|
| 7 | Specialized-weapon nuances | Gear-effect registry in the engine: slayer helm/salve (and their non-stacking), DHL/DHCB vs dragons, Tbow scaling, Shadow multiplier, Keris vs kalphites, Arclight/Emberlight vs demons, Inquisitor's, Void, set effects. The wiki calculator's coverage list is the checklist. |
| 8 | Spec weapon in the best set | Model special attacks (damage/accuracy modifiers, spec cost/regen) and recommend a spec weapon alongside the main set when it beats the alternative use of that switch slot. |

## v0.3 — the full trip plan

| # | Requirement | Approach |
|---|---|---|
| 9 | "Full inventory" suggestion | Switches (the 2nd/3rd style set from the same optimizer), spec weapon, runes/ammo, then consumables. |
| 9a | Potion vs food healing tiers | Config-selectable supply strategy (potion-heavy / balanced / food-heavy); explicitly deferred by design. |

## Decisions

- **D1 (RESOLVED 2026-07-05): fork best-dps's engine.** guccifurs/best-dps
  (hub, BSD-2-Clause, current mechanics) ships a working owned-gear DPS calc +
  beam-search optimizer (~1,200 lines, tested). We fork its `calc/` as the seed
  - BSD-2 keeps our licensing free, unlike a weirdgloop port (GPL-3, ~6,100
  lines). Data plan: vendor the wiki team's weirdgloop JSON (monsters +
  equipment aliases) as gzipped resources exactly as best-dps does; player gear
  bonuses come free from RuneLite `ItemManager.getItemStats`. Rationale + the
  duplication analysis: best-dps covers ~90% of v0.1, so **Loadout Lab's
  identity is being the plugin best-dps isn't** - persistent cross-session
  ownership ledger (untradeables included), spec-weapon-in-set, full-inventory
  trip planning, exhaustive-not-beam search where tractable. Attribution: keep
  best-dps's BSD-2 license text with the derived code.
- **D2 (made): local-first.** All computation client-side; data ships as
  resources; refreshed per release like goal-planner's data files.
- **D3 (made): reuse goal-planner's operational lessons.** Hub gates
  (checkTokens/checkGlyphs/preSubmit), per-profile persistence, event
  coalescing, snapshot-dev/release-ship for new-content data, gameval jar as
  the authoritative ID source. See the `runelite-dev` skill.

## Non-goals (for now)

- Prayer/boost scheduling, tick-perfect rotations, PvP.
- Telling other people what to buy: this is about what YOU own (a "next
  upgrade" advisor is a natural later feature: best set if you bought X).


## v0.2 backlog (user wishlist, 2026-07-05)

- **Save/lookup loadouts** - persist a computed loadout under a name;
  browse saved loadouts. (Config-backed like goal planner's SavedPlanStore.)
- **Issue reporting** - "report a problem with this loadout" dialog that
  sends a direct report to the author. Build as a REUSABLE module so goal
  planner can adopt it (shared repo or copy-in library - decide at build).
- **Slayer task toggle** - on-task checkbox (swaps in slayer helm etc.;
  engine already models it via OptimizationRequest.onSlayerTask).
- **Prayer rating in output** - show the set's total prayer bonus.
- **Offense/defense modes** - best-offense (current), best-defense, and a
  smart hybrid: emphasize offense but make style-aware defensive picks
  (e.g. ranged armour vs magic attackers). Needs monster attack styles
  (in the data: 'style' field, currently unparsed).
- **BiS proximity borders** - per-slot border: BiS gets a distinct border,
  others a gradient by % towards BiS.
- **Slot alternates** - expandable per-slot list of runner-up items
  (owned and BiS variants).
- **Spec-throughput vs ring slot** - jointly optimize the ring: a
  Lightbearer doubles spec regen (sustained spec dps is now shown in the
  spec tooltip), so vs a Venator/Ultor ring the right pick depends on
  the spec weapon's net gain. Optimizer should compare the two totals.

Done from this list: BiS section show/hide (v/> header, 2026-07-05);
monster duplicate collapsing (2026-07-05); item exclusions/protection
(2026-07-06: right-click any suggested item -> excluded globally and
persisted; 'Excluded items: N' manager under the F2P toggle; blowpipe
dart tiers respect it). Follow-up: per-monster exclusion scope.

Set bonuses status (asked 2026-07-05): modeled - Void (all styles, elite
ranged), Obsidian set + berserker necklace, Inquisitor's, slayer helm,
salve. NOT modeled - crystal armour + Bofa/crystal bow scaling (material:
Bofa is underrated without it), Dharok's, Ahrim's proc, elite void magic
damage. Tracked in docs/ENGINE-GAPS.md.

## Defensive arc (started 2026-07-07)

The other half of the equation: what the boss does to YOU. End state: a
recommended set optimizing the ratio (your dps out / boss dps in), with
per-boss defensive thresholds. Phased:

- **D-1 Incoming dps engine (DONE 2026-07-07)** - IncomingDpsCalculator:
  standard NPC rolls (eff+9, roll=eff*(bonus+64), max=(eff*(bonus+64)+320)/640)
  vs the loadout's summed defensive bonuses + real Def/Magic levels.
  Assumptions v1: protection prayer blocks the worst modeled style fully,
  uniform rotation share across the monster's listed styles, no defensive
  boost/stance. Unmodeled styles (Typeless/Dragonfire) surfaced, not
  dropped. Shown per style card: "Boss: ~X.XX DPS to you" + breakdown
  tooltip. Data: MonsterOffence parsed from the vendored wiki sheet
  (skills, offensive bonuses, speed, style list); collapsed spawn groups
  now emit their highest-level row so offence matches the shown level.
- **D-2 Per-boss overrides (DONE 2026-07-07)** - curated layer:
  `boss_incoming.json` (wiki-sourced, name-keyed) + `BossIncomingOverrides`
  replace the uniform model with real attack lists - scripted max hits
  (Graardor ranged 35 vs derived 58), rotation shares, prayer-pierce /
  partial-block flags (K'ril smash, Corp magic, Callisto/Artio melee,
  Vet'ion/Calvar'ion dodge-based), and typeless chip damage (dragonfire,
  Cerberus souls). Accuracy still rolls from the stat sheet; overrides only
  replace the damage term. Seeded: the 5 GWD/wildy boss pairs, Zulrah,
  Vorkath, Cerberus, Chaos Elemental, KBD, Hydra, Corp (17 bosses).
  Follow-ups: phase-aware rotations (Zulrah patterns, Hydra enrage),
  antifire-aware dragonfire, spiderling prayer degradation.
- **W-1 Wilderness low-risk sets (DONE 2026-07-07)** - for the curated
  wilderness boss list (WildernessMonsters), a "Low-risk" toggle caps the
  set to 3 tradeable items (4 with the Protect Item toggle) - exactly the
  kept-on-death items, everything else untradeable - via
  OptimizationRequest.maxTradeables enforced in the beam, the neutral
  fill, and the spec pick. Cards show "Risky items: N/K" with the
  tradeables listed. Follow-ups: skull awareness (keep 0/1), per-item
  repair costs for untradeables, risk-value display in gp.
- **D-3 Threshold-constrained search** - OptimizationRequest gains
  defensive constraints ("ranged def >= 100"); optimizer returns the best
  dps set satisfying them.
- **D-4 Ratio frontier** - sweep the owned-gear Pareto frontier of
  (dps out, dps in); find knee points where a small dps sacrifice buys a
  large defensive gain; auto-derive per-boss thresholds from the knees and
  feed them into D-3; recommend the "optimized" balanced set.
