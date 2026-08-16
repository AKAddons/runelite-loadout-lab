# Bank Memory as an owned-items source — scouting report (2026-08-05)

Prompted by a clan-mate field report: they use "gearscape" fed by the
**Bank Memory** hub plugin, suggesting its stored bank snapshots as an
ingestion source for Loadout Lab's ownership ledger.

**Verdict: reachable today with a ~200-line read-only config read, no
upstream cooperation required. Worth building as a low-priority
Connections toggle, but after the 0.3.x release train — it widens
cold-start coverage (alts, pre-install history), not depth.**

## What Bank Memory actually stores (verified at the hub pin)

Source: `Lazyfaith/runelite-bank-memory-plugin` @ `3174826` (the current
hub pin; last upstream commit **2024-01-30**, v1.3.0 — dormant ~2.5 years).

- **Persistence: plain ConfigManager, GLOBAL scope** (not RSProfile-keyed).
  Group `bankMemory`, keys `currentList` (latest bank per account),
  `snapshotList` (named saves), `nameMap` (identifier → display name).
  `data/ConfigReaderWriter.java`.
- **One JSON list for all accounts**, each `BankSave` carrying:
  - `accountIdentifier` — `"accId#hash1#" + Client.getAccountHash()`
    (`data/AccountIdentifier.java`; legacy saves may carry a username via
    the `alternate = userName` fallback)
  - `worldType` — `DEFAULT` / `LEAGUE` / `SEASONAL` / `DEADMAN` /
    `DEADMAN_TOURNAMENT` (`data/BankWorldType.java`)
  - `dateTimeString` — display-formatted (`"HH:mm:ss, d MMM uuuu"`), fine
    for a tooltip, unpleasant to sort by
  - `itemData` — **a CSV string, not a JSON array**: `"id,qty,id,qty,"`
    (`data/ItemDataParser.java`). BankItem is `{itemId, quantity}`.
- **No PluginMessage surface, no API** — consumers are the panel and a
  clipboard export (`CopyItemsToClipboardAction`). "gearscape" is **not a
  hub plugin**; it is almost certainly an external site fed by that
  clipboard export. Nothing for us to integrate with on that side.

## What it would add over what we have

| Source today | Needs |
|---|---|
| Live bank scan | each account opens its bank once, post-install |
| DWMS contract | DWMS installed and 2.11.5+ |
| STASH / POH / cargo / looting bag | native, already automatic |

Bank Memory's `currentList` gives us a bank per account **from before
Loadout Lab was ever installed** — every alt the user has banked on since
installing Bank Memory. The value case is exactly cold start: install LL,
get owned-gear answers for all alts with zero bank visits. For the main
account the user actively plays, our own scan supersedes it within one
bank visit. Bank-only coverage (no equipment/inventory), which our other
sources already handle.

## Integration options

**A. Read-only config read (recommended).** Read
`getConfiguration("bankMemory", "currentList")` on profile load, lenient
JsonParser walk (do NOT copy their Gson classes — the CSV `itemData`
needs ~15 lines of bespoke parsing anyway), match
`accId#hash1#<accountHash>`, map `DEFAULT→std` / `LEAGUE|SEASONAL→seasonal`,
**drop DMM outright**, feed the ledger as a new source `bankMemory` behind
a Connections toggle (default off, like DWMS). Precedence: strictly below
our own bank scan — the moment LL has scanned that account's bank, the
Bank Memory source is ignored for it. Surface `dateTimeString` in the
location-hint tooltip so stale data is visibly stale.

**B. Upstream PluginMessage contract.** The principled path per the
runelite-dev skill (namespace = their config group, versioned, source-
attributed — the same `storages-request/response` shape we already speak
with DWMS, and could answer symmetrically). **Not viable in practice: the
repo has been dormant since Jan 2024**, so a contract PR likely never
merges.

The tension worth naming: we retired exactly this kind of config scraping
for DWMS (2026-07-22) in favour of the contract. The difference is that
DWMS is actively maintained and authored its contract upstream; Bank
Memory's dormancy makes a contract unmergeable — and simultaneously makes
its schema stable to scrape. The DWMS arc itself ran config-read-first,
contract-later; option A follows the same arc and can be superseded the
same way if the plugin ever wakes up.

## Risks

- **Schema drift** — low (dormant; their own parser tolerates legacy
  shapes), and a lenient parser that drops-never-guesses fails to "no
  source" rather than wrong data.
- **Stale banks presented as owned** — mitigated by scan precedence + the
  dated tooltip; residual risk is an alt's long-sold gear counting as
  owned until that alt banks once.
- **accountHash -1 / username-era saves** — skip both; hash-keyed saves
  only.
- **Hub review** — read-only ConfigManager access to another group is
  ordinary API use; no new capability class (no network, no reflection).

## Cost

~150–200 source lines + a fixture-JSON test (~1.5–2k tokens against the
current 15.9k headroom at 0.3.4). No engine changes — a new ledger source
slots into the existing `collection.<source>` scheme and the location-hint
palette.

## Next steps (none taken)

1. Decide whether the cold-start value earns a slot after the release
   train (my read: yes, it is cheap and self-contained; priority low).
2. If built: option A as specced, Connections toggle default-off,
   README data-sharing section gains a "sources we read" paragraph.
3. No upstream PR to Bank Memory — dormant, and per the standing rule
   nothing is submitted anywhere without Andrew's explicit okay.

*Scouting only — no Loadout Lab source was modified.*
