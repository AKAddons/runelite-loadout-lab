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
