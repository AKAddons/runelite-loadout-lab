---
status: proposed
date: 2026-08-09
decision-makers: ajkatz
---

# Core and Companion: the two-plugin architecture

## Context and Problem Statement

The hub caps main source at 200k tokens PER PLUGIN; after the wave-4
diet the base plugin sits at ~192k with its new-code fat gone, and the
only remaining single-plugin levers were rejected for good reasons
(the mascot JSON-VM's maintainability tax, logic-moving table
rewrites). Meanwhile AKAddons already ships multiple plugins and the
PluginMessage contract plumbing is field-proven. Andrew, 2026-08-09:
"loadout lab - core (essential data + most thin UI layer possible for
barebones), then loadout lab - companion (UI prettification,
animations). Core will always give you everything you need but its
not pretty."

## Decision Outcome (proposed)

Two plugins with one boundary rule: **function lives in Core,
appearance lives in Companion.**

- **Loadout Lab (Core)**: the engine, all data resources, and the
  thinnest UI that still delivers EVERYTHING - every answer, control,
  chip, report, and store. Standalone-complete by definition; a user
  with only Core loses nothing but polish. (This also satisfies hub
  reviewers: no shell plugins.)
- **Loadout Lab Companion**: the pretty layer - mascot loading
  animations (all five plus the attic seasonals, home permanently),
  painted icon sets, styled borders/chips/cards, future skins. Pure
  overlay, enhancement-only; the Resource-packs hub plugin is the
  cosmetic-overlay precedent.
- **The seam**: Core exposes SURFACES (compute-wait slot first, then
  icon/border/card painters) and publishes lifecycle messages on the
  loadoutlab PluginMessage namespace; Companion registers painters
  back (same JVM, object references in the message map, shapes
  documented in the contracts registry, additive-only versioning).
  Companion absent = Core's plain fallback rendering.

### Phasing (each phase ships alone)

1. **Mascots** (~12.8k freed) - the proven, lowest-risk seam: one
   surface, the frame-hash battery moves with the code. After 0.3.6.
2. **Painted icons and chrome** (~3-5k) - the icon painters the audit
   wanted rasterized go to Companion as rich painters; Core keeps
   text/basic fallbacks. Kills the fractional-scaling objection too:
   Companion can afford BOTH painters and PNG packs.
3. **Card/border styling surfaces** - only once the surface API has
   proven stable across a few releases; this is where the contract
   cost lives, so it waits for evidence.

End state: Core ~175-178k with years of data-first feature runway;
Companion carries every future cosmetic ambition with its own
untouched 200k.

### Consequences

* Two hub plugins to release-manage (drafts park as usual; the
  one-active-review throttle applies per author, not per plugin -
  stagger the PRs).
* The surface API is a semi-public cross-plugin contract: additive
  changes only, versioned like the DWMS storages contract.
* Core PRs SHRINK at each phase - reviewer goodwill.
* Users who never install Companion still get the whole product -
  the boundary rule is the guarantee, enforced at review time by
  asking of every moved piece: "does Core lose a CAPABILITY?"
