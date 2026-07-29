---
status: accepted
date: 2026-07-05
decision-makers: ajkatz
---

# Fork best-dps's BSD engine and vendor wiki monster data; all computation local

> Migrated in substance from the pre-MADR `DECISIONS.md` entry "D1 — DPS
> engine source". See [ADR-0001](0001-follow-the-madr-convention.md).

## Context and Problem Statement

Founding architecture decision for the new plugin. Research (2026-07-05)
found guccifurs/best-dps already ships ~90% of our planned v0.1 on the
Plugin Hub — which reframed the project from "build a DPS calculator" to
"build the 10% that doesn't exist." The question was where the DPS engine
and its data come from.

## Decision Drivers

* Licensing: the plugin must stay unencumbered (GPL contamination is a
  one-way door).
* Hub UX and cache design rule out network at query time.
* Engine maintenance (weekly game-update formula drift) is a permanent tax —
  minimize the owned surface.

## Considered Options

* Fork best-dps's BSD-2-Clause engine + vendor weirdgloop JSON data
* Port weirdgloop/osrs-dps-calc to Java
* Interop with an existing plugin's engine over PluginMessage
* Remote calculation (dps.osrs.wiki / an MCP service)

## Decision Outcome

Chosen option: "Fork best-dps's BSD-2-Clause engine + vendor weirdgloop
JSON data". Its `calc/` (DpsCalculator + RollMath + BestDpsOptimizer,
~1,200 tested lines, current mechanics) is the engine seed. Monster data:
the wiki team's weirdgloop JSON (monsters + equipment aliases) vendored as
gzipped resources, refreshed per release. Player gear bonuses come from
RuneLite core `ItemManager.getItemStats` (zero-maintenance). All
computation is local; no network at query time.

Effort then goes into Loadout Lab's actual identity — best-dps's verified
gaps: persistent cross-session ownership ledger (untradeables included),
spec-weapon-in-set, full-inventory trip planning, exhaustive-not-beam
search where tractable.

### Consequences

* Good, because a working, current calc arrives for free under a permissive
  license.
* Good, because the differentiator list is grounded in verified gaps, not
  speculation.
* Bad, because we inherit an engine written for another plugin's shape and
  own its evolution from day one.
* Licensing rules recorded in CLAUDE.md: never copy GPL weirdgloop code;
  its data JSON is wiki content (CC BY-NC-SA, keep attribution); retain
  best-dps's BSD-2 license text with derived code.

### Confirmation

The golden/roster capture-and-diff nets plus the official-calc harness
(`verify_official.py` running the wiki calc engine locally against ours)
keep the forked engine honest against live mechanics.

## Pros and Cons of the Options

### Port weirdgloop/osrs-dps-calc to Java

* Good, because it is the reference implementation players trust.
* Bad, because GPL-3.0 would force the whole plugin GPL.
* Bad, because the port is ~6,100 lines / 2-4 weeks before any product work
  (the unpublished `Alexsbuchanan/bossbis` port + parity corpus proves
  feasibility but inherits the license and weekly formula-drift
  maintenance).

### Interop with an existing plugin's engine (PluginMessage)

* Bad, because hub plugins cannot compile against each other
  (`@PluginDependency` works only toward core plugins) and best-dps exposes
  no message surface.
* Bad, because a compute-over-messages hop defeats the caching requirement.

### Remote calculation

* Bad, because the wiki calculator is fully client-side (static export, no
  compute API).
* Bad, because network-at-query-time kills both cache design and hub UX.
