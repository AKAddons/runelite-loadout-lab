---
status: superseded by [ADR-0009](0009-merge-back-single-plugin.md)
date: 2026-08-09
decision-makers: ajkatz
---

# Core and Companion: the render-model split

## Context and Problem Statement

The hub caps main source at 200k tokens PER PLUGIN; the base plugin
sits at ~192.7k with its new-code fat gone, and the only remaining
single-plugin levers were rejected for good reasons (the mascot
JSON-VM's maintainability tax, logic-moving table rewrites). The
token map names the real hostage: `ui` is 65.5k (34% of the budget),
and LoadoutLabPanel.java alone is 60.4k - one Swing monolith worth a
third of the plugin. Meanwhile AKAddons already ships multiple
plugins and the PluginMessage contract plumbing is field-proven.

Andrew, 2026-08-09: "the core package is pure API and engine, jam
packed and dense with all calculations ... we should produce a
generic output and apply a UI layer based on the instructions in the
ui companion. the basic ui output should heavily nudge the user to
install the UI companion but still output the most basic usable
output in just text"; "in the UI app we can even define multiple
different UIs and have all of the animations live there."

An earlier draft of this ADR proposed a cosmetics-only Companion
(mascots + painters registered back into Core's panel). Superseded:
that kept the 60k panel in Core, which is exactly backwards.

## Decision Outcome (revised 2026-08-10: single-plugin first)

Reviewer risk surfaced (Andrew: "i don't know if reviewers are
liking the dual addon approach"), and the numbers say the seam work
pays either way: a model-driven renderer rebuilt against the page
model is estimated at 20-30k where the organically-grown monolith is
60k. DECIDED - Path C: the renderer lands IN CORE (package
com.loadoutlab.render), consuming the same model and command engine,
mounted in the same one-surface host as the DEFAULT renderer; the
external Companion plugin (repo runelite-loadout-lab-ui) is PARKED
as the future split vehicle - its surface-register message still
overrides the internal renderer, so lifting the renderer out later
is a move, not a rewrite. Icons convert to game sprites/PNG
resources as views port (painters never port; resources are exempt
from the token cap). End state single-plugin: Core ~150-155k after
the monolith swap. The two-plugin ship decision is DEFERRED until
real reviewer signal exists (research hub precedents / ask in the
hub Discord before any Companion PR).

## Original decision (dual-plugin end state, retained as the target
if reviewer signal turns positive)

Two plugins, one seam: **Core publishes a generic render-model;
renderers live in the Companion.** One model, N renderers.

- **Loadout Lab (Core)**: engine, optimizer, all data resources,
  profiles/sims/pins/undo - every calculation and store - plus a
  RENDER-MODEL BUILDER: a versioned, JSON-safe structure carrying
  everything a UI needs to draw (per-style cards, cells, chip states,
  breakdowns, roster views, report text). Core's own surface is the
  cheapest usable rendering of that model: copy-report-style text
  (the report generator is already a working text renderer), plain
  controls for every capability (search field, chip checkboxes,
  simple dialogs for pin/exclude/sim), and a prominent nudge to
  install the Companion. Bare-UI depth decision: FULL CAPABILITY,
  CLUNKY - nothing is impossible with only Core installed; the
  guarantee reviewers and users rely on, enforced by asking of every
  moved piece "does Core lose a capability, or only a presentation?"
- **Loadout Lab UI (Companion)**: the renderers. The current rich
  panel ports here wholesale, all five mascots plus the attic
  seasonals come home permanently, and future presentation ambitions
  (compact mode, dashboard layouts, skins, animation sets) are
  additional renderers over the same model, spending the Companion's
  own untouched 200k.
- **Hosting (decided 2026-08-09, after the two-panel prototype
  confused the field test)**: ONE surface, Core hosts it. Core owns
  the single sidebar icon and panel shell; when the Companion is
  present it registers a renderer (a JDK `Function<page, JComponent>`
  passed by reference - same JVM, JDK types only, so it legally
  crosses the classloader seam) and Core mounts the rich content
  inside its own shell. No Companion = Core mounts its plain
  rendering of the same model in the same spot. The Companion shows
  its own (tiny) panel ONLY when Core is absent - the install nudge -
  and hides it the moment Core hosts. Rejected: Companion as a pure
  instruction pack that Core interprets (Core would need a renderer
  powerful enough to execute the instructions - the rejected mascot
  JSON-VM tax at full-UI scope, and Core's tokens would not drop).
- **The seam**: the `loadoutlab` PluginMessage namespace. Core
  publishes model + lifecycle messages; the Companion renders and
  sends command messages back (search, pin, exclude, sim, chip
  toggles), which Core executes through the same command layer its
  bare UI uses, then re-publishes the model. Payloads are JSON-safe
  maps/lists/primitives ONLY (hub plugins load in separate
  classloaders - no shared classes), shapes documented in the
  contracts registry, versioned additive-only. Companion absent =
  Core's text surface; Core absent = Companion shows "install Core".

### Phasing: big-bang panel move (decided over staged-by-surface)

One arc, one release pair - Core 0.3.6 + Companion 1.0.0:

1. Render-model + builder in Core (inventory of everything the
   panel reads today IS the model's field list).
2. Command layer exposed on the seam (Commands.java already reifies
   the actions).
3. Core's bare surface: text renderer + capability controls + nudge.
4. Companion repo: port LoadoutLabPanel + mascots, replace direct
   adapter calls with the contract client.
5. A model-snapshot golden joins the nets: the serialized model for
   the golden scenarios locks the contract like rosterGolden locks
   the engine.
6. Test both apart: Core standalone (bare UI, full capability),
   Companion standalone (waiting-for-Core message), then the pair.
7. Hub, COMPANION FIRST: the Companion's new-plugin PR lands while
   Core 0.3.5 is still live (the Companion waits patiently for a
   model-publishing Core); only once it is installable does Core
   0.3.6 ship and drop the panel. No user ever sits in the window
   where the rich UI left Core but its replacement can't be
   installed. Both PRs draft-staged, explicit approval, one review
   slot at a time.

Rationale vs staged: staging leaves the panel half-in-half-out
across public releases and churns the contract repeatedly; the
big-bang pays the risk once, and the golden nets plus the
capability-parity rule are the safety harness.

End state: Core ~135-140k with years of data-first runway (0.4.0 sea
combat fits trivially); Companion starts ~70k with room for every
renderer and animation ambition.

### Consequences

* Every interaction becomes a message round-trip (same JVM, so
  latency is negligible; the real cost is that the command surface
  is now a versioned public contract).
* Two hub plugins to release-manage; the one-active-review throttle
  applies per author, so the Companion PR staggers behind Core's.
* Core PRs shrink massively - reviewer goodwill.
* Version skew is a supported state: the Companion checks the model
  version and degrades additively; mismatch never breaks Core.
* The Resource-packs hub plugin is the presentation-layer precedent;
  Core standing alone fully functional answers the shell-plugin
  objection from either side.
