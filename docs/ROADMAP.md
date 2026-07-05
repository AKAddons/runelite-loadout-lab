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

- **D1 (open): engine source.** Options under research: (a) port the OSRS Wiki
  DPS calculator's formulas to Java (canonical math, license permitting);
  (b) reuse/interop with an existing hub plugin's engine; (c) remote calls
  (wiki/MCP) — likely rejected: network at query time kills the cache goal and
  hub UX. Resolution criteria: license, coverage of gear nuances (#7), port size.
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
