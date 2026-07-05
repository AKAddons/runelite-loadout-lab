# Loadout Lab — project instructions

A RuneLite plugin: best-in-slot sets from the gear the player OWNS, per enemy
and combat style, with exact DPS. Plan lives in `docs/ROADMAP.md`.

**Load the `runelite-dev` skill (user-level, `~/.claude/skills/runelite-dev/`)
before any RuneLite work in this repo** — it carries the verified threading
model, perf costs, hub rules, PluginMessage contracts, and release/ID
conventions this project is built on. Update the skill when new RuneLite facts
are learned here.

## Build & verify

```bash
./gradlew run        # dev client with the plugin (--developer-mode --debug -ea)
./gradlew preSubmit  # test + checkTokens (hub 200k cap) + checkGlyphs (ASCII UI strings)
```

The user runs builds themselves — stop at the branch/artifact and hand over
the `run` command.

## Non-negotiable constraints (inherited from goal-planner, all learned the hard way)

- **Threading:** game-state reads on the client thread; Swing on the EDT;
  EventBus handlers run on the POSTER's thread. Never do real work per-event —
  dirty flags drained once per tick (`ItemContainerChanged` is an event storm:
  one event per bank slot).
- **Persistence:** ConfigManager writes amplify (flushed + synced remotely).
  Save per-entity, gate aggregate writes on real dirtiness, scope stored data
  per RuneLite config profile.
- **Data:** big tables ship as resource TSV/JSON (exempt from the hub token
  cap) with regeneration scripts. Numeric game IDs come from the RuneLite
  `gameval` classes or the cache — NEVER from wiki text fetches.
- **Strings:** ASCII only in UI/chat/hub-description strings (macOS Tahoe
  tofu); `checkGlyphs` enforces.
- **Releases:** build against `latest.release`; dev-on-snapshot only for
  verifying new-content data, revert before committing to a shippable branch.
- **Perf:** the optimizer/caching design assumes: engine calls are pure and
  memoizable; the owned-collection has a fingerprint that changes only on real
  container changes; no network calls at query time.

## Conventions

- Test-first for anything other code calls (per user's global rules); real
  stores against in-memory fakes, Mockito for stateless collaborators.
- Feature changes update README + CHANGELOG in the same change.
- Player-facing text is written for players, not developers.
- License note: if the DPS engine is derived from weirdgloop/osrs-dps-calc
  (GPL-3.0), this plugin must be GPL-3.0 and keep upstream notices; if derived
  from best-dps (BSD-2-Clause), attribution per its license. Decide at D1 and
  record here.
