# Core <-> UI Companion contract (v1 draft)

Working draft of the seam decided in
[ADR-0008](decisions/0008-mascot-companion-plugin.md). Generated from
the full seam inventory of LoadoutLabPanel.java (2026-08-09,
8,643 lines): every adapter method the panel reads, every mutation it
initiates, and every implicit rendering parameter is accounted for
here. This document is the source of truth for both repos while the
split is built; it moves to the contracts registry on Companion 1.0.

## Transport

- RuneLite PluginMessage, namespace `loadoutlab`.
- Payloads: JSON-safe values ONLY (String, Number, Boolean, List,
  Map). Hub plugins load in separate classloaders - no shared
  classes, no object references.
- Every message carries `v` (int, contract version). Additive-only
  evolution: fields may be added, never renamed/removed/retyped.

### Lifecycle

| Message | Direction | Purpose |
|---|---|---|
| `ui-hello` {v} | UI -> Core | on startup and on Core's `core-hello`; asks for a model push |
| `core-hello` {v, coreVersion} | Core -> UI | on startup; lets the UI distinguish "Core absent" (no reply ever) from "Core too old" (pre-model Core 0.3.5 never speaks this namespace, so silence = absent-or-old; the nudge text covers both: "install or update Loadout Lab") |
| `model` {v, page} | Core -> UI | the full render-model (below); pushed after every compute/mutation |
| `status` {v, computing, label} | Core -> UI | compute-in-flight signal (drives the UI's waiting/mascot state) |
| `command` {v, name, args} | UI -> Core | one entry from the command catalog; Core executes through the same layer its bare UI uses, records history, recomputes as needed, re-publishes `model` |
| `search` {v, query} / `search-results` {v, matches[]} | UI <-> Core | monster/group typeahead (mirrors LoadoutData.searchMonsters + MonsterGroups.search) |
| `item-search` {v, prompt, token} / `item-picked` {v, token, itemId, name} | UI <-> Core | Core owns the native chatbox item search (client access); the UI requests it and gets the pick back |

## Render model (`page`)

Root mirrors today's page of ResultEntry objects. Principles:

1. **Pre-resolved, not raw**: everything the panel computes from
   static domain helpers (MonsterNotes, RequiredGear,
   RecommendedBring, TripSupplies, PvpRisk, boost/prayer options,
   spellbook plates, undo labels...) arrives as display-ready
   strings/lists in the model. The Companion renders; it never
   re-derives game logic.
2. **The implicit EDT parameters become explicit fields**: the
   `rendering*` fields (style, bis, incoming, chips,
   mechanicsNote, protectItem, riskLine, riskSpecWeapon, riskKeep,
   riskConsumables, upgradeLine) each become a named field on the
   per-card node.
3. **Dead payload stays home**: KitCurve is computed but never read
   by the panel - not in the model.
4. **Model carries what the panel renders today**; unused DpsResult
   accessors (expectedHit, attackSpeed, rolls) join later
   additively if a renderer wants them.

Node sketch (field groups, exact JSON schema generated in code):

- `page`: entries[], activeIndex, counts {excluded, simmed, stored,
  pinned}, sourceLegend[], globalChips, historyState {canUndo,
  canRedo, undoLabel, redoLabel}, f2pWorld, magicLevel, spellbook,
  developerMode, displayOptions (the 40-odd booleans/enums verbatim)
- `entry`: mobs[] {id, profileId, name, label, hp, taskOnly,
  wilderness, invocationScaled}, group {name, label} | null, params
  (every param chip's state + availability + tooltip: onTask,
  wilderness, protectItem, raidBoost, antifireMode, invocation,
  thralls, spec, deathCharge, budget, riskCap, pins, maxSwaps,
  spellbookLock), folded, lensIndex, note {text, collapsed},
  viewingBis, selectedTab, supplyChips[], consumableChips[],
  inventoryRow[], reportText
- `card` (per mob x style x yours|bis): dps breakdown {shown, set,
  thralls, spec}, maxHit, accuracy, attackTypeText, spellName,
  boostLabel, assumesChips[], countedBonuses[], gearGrid (slot ->
  {itemId, name, iconId, tooltip, menuEntries[]}), quiverAmmo,
  specCell {weaponId, name, tooltip, pinned, menuEntries[]},
  statPanel lines[], incoming {totalDps, unprayedDps, protectPrayer,
  threats[], fullyModeled, overrideNote}, riskLine, upgradeLine,
  kitBacked, antifireAssumed, noSetMessage
- `bankViews`: highlight ids[] | null, filter {ids[], layout[]} |
  null - published by Core so its own overlays (bank highlight and
  filter stay IN CORE - they are client function, not presentation)
  and the UI's buttons agree on state.

Item icons: the model carries item ids; the Companion resolves
images through its own ItemManager/SpriteManager injection (both
plugins share the client). Sprite-id constants used today are listed
in the model as ids, not images.

## Command catalog (`command.name`)

From the panel's complete write surface. Args always include enough
identity to act statelessly (monsterId AND profileId where relevant,
scope, style).

**Global stores**: `toggle-exclusion`, `toggle-sim`, `toggle-stored`,
`toggle-protect-only`, `add-always-filter`, `remove-always-filter`,
`set-supply-default`

**Per-monster profile**: `pin`, `unpin`, `set-note`,
`add-filter-item`, `remove-filter-item`, `set-pinned-spell`,
`set-pinned-spec`, `exclude-for-mob(s)`, `remove-mob-exclusion`,
`sim-for-mob(s)`, `remove-mob-sim`, `set-supply-override`

**Selection / page lifecycle**: `select` {monsterId | groupName,
replacePage}, `add-mob`, `remove-mob`, `add-to-view`, `close-result`,
`clear-selection`, `set-active`, `reload-entry`

**Entry view-state** (Core owns ALL state, including view state, so
the bare UI and any renderer see the same world): `set-param`
{entry, param, value} covering the 2e list (viewingBis, selectedTab,
folded, lensIndex, noteCollapsed, thralls, deathCharge, specWeapon,
antifireMode, raidBoost, maxSwaps, toaInvocation, riskCap,
upgradeBudget, spellbookIndex, onSlayerTask, inWilderness,
protectItem, prayerPick, boostPick)

**History**: `undo`, `redo` (labels come back in the model; the
label text for each recorded step is generated CORE-SIDE using the
inventory's label vocabulary, so undo history reads identically in
both UIs)

**Bank**: `set-bank-views` {show, filter}

**Misc**: `copy-report` (Core builds the text - it already does -
and returns it in the model as `reportText`; the UI puts it on the
clipboard), `open-discord`, `record-usage` {label}

## Rulings from the inventory (bind both sides)

1. **Identity key**: `profileId()` becomes THE profile key for every
   write. Today pins/note/pinned-spell/spec use `getId()` while the
   local trio and supply overrides use `profileId()`. Core migrates
   the `getId()` call sites and dual-reads old keys from the store
   for one release.
2. **Hidden legacy controls collapse**: the invisible Swing
   checkboxes the chips `doClick()` through are deleted; chips (in
   both UIs) emit commands, and history recording moves into the
   command layer.
3. **`f2pOnly` moves onto the entry** like every other parameter
   (last panel-global compute input).
4. **Rendering must be side-effect-free**: `applyBankViews` is
   called from inside card rendering today; in the split, bank view
   changes happen only via explicit command/state change.
5. **Mascot ACCENT**: the shared accent colour constant moves into
   the model (`page.accentColor`) so Core's bare UI and the
   Companion agree without sharing MascotArt.
6. **The 13 painted-icon classes** (tofu-glyph workarounds) move to
   the Companion wholesale; Core's bare UI is text and standard
   Swing only (ASCII-safe per the glyph gate).

## Testing

- **Model-snapshot golden**: serialize `model` for the golden
  scenarios; byte-stable like rosterGolden. Lives in Core.
- **Contract fixtures**: the Companion repo tests its renderers
  against checked-in model JSON captured from Core's golden run -
  the two repos never import each other, the fixtures ARE the
  integration test.
- Standalone QA: Core alone (bare UI capability checklist = the
  command catalog), Companion alone (silence -> nudge state).

## Open items

- Command-arg schemas per command (generate alongside the model
  classes).
- `status`/progress granularity for the mascot waiting states.
- Whether `search-results` carries enough for the group dropdown's
  rich rows (member counts, link matches).
