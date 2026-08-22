---
status: accepted
date: 2026-08-18
decision-makers: ajkatz
---

# The merge-back: Loadout Lab ships as one plugin

## Context and Problem Statement

[ADR-0008](0008-mascot-companion-plugin.md) split the plugin in two to
live under the hub's 200k-token-per-plugin cap: a core that computes
and a companion that draws. Both halves were built and the companion
was submitted (plugin-hub PR #15129).

The hub refused it: a plugin "needs to be a single plugin and cannot be
split like this". No written rule covers this - the decision is
maintainer discretion, and it is final.

## Decision

Merge the companion's renderer back into the plugin and ship ONE
plugin. The animations do not come with it.

## Consequences

- The renderer that had been lead in the companion repo transplants
  into `com.loadoutlab.render`; the PluginMessage seam, the bare
  fallback surface and the install hook are deleted.
- All 16 mascot moods (~27k tokens of animation) are dropped. The
  compute animation returns later as ASCII frames in a resource, so
  the art costs no source tokens at all.
- `CompanionLink` survives as the in-process page store between the
  command engine and the renderer - the name is now a fossil.
- Token budget: 152,276 (core alone) + 63,286 (companion) with 34,372
  of that dead on arrival, landing at 177,481 for the merged plugin -
  roughly 15k LIGHTER than the 0.3.5 build then in review.
- The companion repository retires as a product.

## Considered and rejected

- **Pushing back.** Quest Helper (2.43M tokens), 117HD (337k) and
  Xtreme Tasker (206k) all exceed the cap today, and OSRS TCG passed
  review recently at 191k - our exact size. A draft reply was written
  and never sent: the ruling is discretionary, and shipping matters
  more than being right about it.
- **Reverting to the pre-split renderer.** It predates months of
  field fixes that live only in the companion's copy.
