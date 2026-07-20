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

## v0.3.0 — the multi-mob canvas (headline arc, decided 2026-07-16)

Structure (user-specified 2026-07-16): PAGE -> RESULT CARDS -> MOB
SECTIONS. A result is one QUERY (single mob or a group); Save and
close (X) live on the RESULT card, not on mobs. Examples: result 1 =
Graardor alone; result 2 = Zulrah's three forms; result 3 = a wave
sequence (bat, blob, ... Jad) - groups must scale to long raid/wave
lists, so mob sections collapse individually and the result header
summarizes deep lists. Saved RESULTS are the favorites concept: a
saved result re-runs its query (mobs + params) against current gear.
M-4's hybrid answer renders at the RESULT level above its mobs.
Design calls: top toggles stay GLOBAL as defaults, each mob section
resolves them against its own monster (the applySelection gating);
collapsed levels show one-line best-style summaries (vertical-budget
rule); OptimizerService needs a page-scoped supersession ticket +
progressive fill-in; combat styles render as GAME SPRITE ICONS
(SpriteManager skill icons - attack/ranged/magic - the AssumeIcons
pattern; sprites not glyphs per the Tahoe tofu rule, tooltips carry
the words) everywhere: kit tabs, mob-row kit chips, and today's
[Melee]/[Ranged]/[Magic] card headers (the last is a small
standalone win that pre-builds the M-1 assets).

- **M-1 List refactor** - results area renders a list of RESULT
  cards, each holding 1..N mob sections; a single-mob result is
  pixel-identical to today. Compute plumbing takes a set. (M)
- **M-2 Multi-add UX** - "add to view" on search hits (new result vs
  add-mob-to-result), result close/collapse/reorder + Save,
  progressive loading, pages join back/forward history. (M)
  REDESIGN (field-driven 2026-07-16, after the first multi-result
  build): (a) STYLE TABS replace the stacked style cards - a tab is
  just the skill icon + dps, best tab selected by default, ONE detail
  body flips beneath; assume chips + the set menu move into the
  detail header; auto-collapse dies (tabs subsume it); the BiS
  section lives per tab. At M-4 kits are simply more tabs - with
  hybrid/tribrid the option count can reach 7, which stacked cards
  cannot carry. (b) PARAMETERS BECOME PER-RESULT: each card carries a
  compact param chip row (on-task, wilderness, optimize mode;
  risk/budget behind the card menu), seeded from defaults at add
  time and owned by ResultEntry; computeEntry reads them; the global
  row keeps only what is truly global (search, back/forward, F2P,
  exclusions). Saved results serialize (mobs + own params) with no
  global state to untangle. (M-2c)
THE RESULT CARD ANATOMY (user spec 2026-07-16 - the M-2c/M-3/M-4
target, top to bottom):
1. MOB LIST - name + hp per mob; rows are an INFORMATIONAL LENS:
   clicking one flips which mob's numbers (dps, max hit, damage
   taken...) display below. Multi-mob: ONE set per style, optimized
   ACROSS the list ("the best melee set that works on both"), same
   for ranged/magic - the set is shared, only the numbers flip.
   Kits/swaps layer on top via the swap parameters.
2. PARAMETER ZONE (per result): max swaps (0/1/2), swappable-item
   count, best-prayer toggle (default on) vs pick-a-prayer, boost
   assumption toggle, spellbook, weight cap, max worn cost, upgrade
   budget, wilderness toggles, antifire mode (gear shield / assume
   potion / DETECT from inventory via the ledger), optimization
   strategy. Params that exist globally today move per-result.
3. STYLE DPS TABS (as built) - icon + dps.
4. YOURS | BIS toggle, tab-style, above the item view.
5. ITEM VIEW/STATS - the classic grid + info tiles (bonuses, damage
   done, damage taken).
6. SHOW IN BANK / FILTER BANK per result.

- **M-3 Monster groups** - curated table searchable as virtual hits
  that expand into a result. A group is the ROSTER of distinct mob
  types featured in the content, not the wave sequence. Flagship
  presets: FIGHT CAVES (Tz-Kih, Tz-Kek, Tok-Xil, Yt-MejKot, Ket-Zek,
  TzTok-Jad + Yt-HurKot healers - you gear to attack them on Jad
  phase) and INFERNO (Jal-Nib, Jal-MejRah, Jal-Ak, Jal-ImKot,
  Jal-Xil, Jal-Zek, JalTok-Jad, TzKal-Zuk + Jal-MejJak healers);
  also "Zulrah (all forms)", "Dagannoth Kings", GWD rooms, Barrows.
  Rosters verified present in the corpus 2026-07-16. MUST be built
  against LOADED rows, not raw wiki names (the stat-key collapse
  merges versions - e.g. Zuk Normal/Enraged is ONE loaded row;
  Tz-Kek has two level variants). (S-M)
- **M-4 Group lookup: the hybrid/tribrid assistant** - a group is
  QUERYABLE, not just viewable: one answer optimized against all
  members. STRUCTURE (user-refined 2026-07-16): the KIT is the
  structural unit, naturally capped at 3 (tribrid) or 2 (hybrid) -
  one per combat style in use. Kit TYPE is labeled by the attack
  style actually USED against each mob, not the weapon's category
  (nuances: a melee-cast staff labels melee; a salamander is ONE item
  serving up to three kits - the ultimate swap-saver, and the merge
  must discover it). The BUDGET knob (slider + text entry) counts
  SWAP ITEMS - the intentional extra inventory slots - not kits.
  MAX SWAPS parameter (added 2026-07-16): the search parameter area
  gains "Max swaps: 0/1/2" = max kit TRANSITIONS (kits - 1): 0 mono /
  1 hybrid / 2 tribrid. Orthogonal to the item budget (swaps bound
  the kit count; the slider bounds the slots spent achieving it).
  Max swaps 0 on a group is meaningful: ONE worn set for the whole
  roster, zero switch items - style may still vary per mob when the
  weapon permits (salamander). A plugin CONFIG option hides the
  control and locks 0 for every search (DisplayOptions pattern, like
  the budget/wildy-risk rows) for users who never want hybrid
  suggestions. As a panel parameter it joins the back/forward stream
  and cache keys via the existing recording machinery.
  Algorithm: greedy-merge from the members' cached independent bests,
  pricing each compromise until within the item budget. UI: TAB VIEW
  between kits at the result level (worn view per kit, shared pieces
  stay put, per-kit "+N items" on the tab); collapsed mob rows show
  just which kit to bring + dps. BiS LAYER (added same day, corrected):
  the BiS group answer is computed under the SAME swap-item budget
  and at your levels (existing gear-gap philosophy) and is a COMPLETE
  INDEPENDENT SOLUTION - it may resolve different styles per mob, a
  different kit count (your 3-kit tribrid vs a BiS 2-kit Shadow
  hybrid), different swaps. Therefore the Yours|BiS toggle sits a
  LEVEL ABOVE the kit tabs: it swaps the whole answer block (tab
  strip, per-mob kit assignments, swap composition). Header keeps the
  dual verdict ("94% of your max" = compromise cost, "78% of BiS" =
  gear gap); gold borders carry over (you own the BiS pick);
  collapsed mob rows always show the dps pair (9.4 / 12.1) with the
  kit chip following the currently viewed side. Engine prerequisites this makes
  load-bearing: salamander multi-style support and the staff-in-melee
  dead path (audit A2.8). No curated hybrid tables: void tribrid must
  EMERGE from slot-sharing economics. FLAGSHIP VALIDATION: the
  Inferno preset - a credible Inferno tribrid proves the model;
  Fight Caves is the smaller sibling case. Open call remaining:
  objective per group (weighted-sum with rotation shares vs
  maximize-min); the budget unit is RESOLVED (swap items = inventory
  slots, ties into v0.4). The raid and slayer-task-planner
  foundation. (L)

Favorites/save (the former 0.3.0 wishlist item) is subsumed: a saved
RESULT ("my Zulrah setup", "DKs trip" - the query + mobs + params,
re-run against current gear) replaces saved single loadouts.
Undo/redo + history shipped 2026-07-15/16 as unified back/forward.

## v0.4 — the full trip plan

| # | Requirement | Approach |
|---|---|---|
| 9 | "Full inventory" suggestion | Switches (the 2nd/3rd style set from the same optimizer), spec weapon, runes/ammo, then consumables. Builds on M-4's carry-set synthesis. |
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
- **Slayer task toggle** - DONE: "On slayer task" checkbox in the panel
  (with not-assignable tooltips per monster); engine models it via
  OptimizationRequest.onSlayerTask.
- **Prayer rating in output** - DONE: set prayer bonus shown on the card
  (prayerBonus display option).
- **Offense/defense modes** - best-offense (current), best-defense, and a
  smart hybrid: emphasize offense but make style-aware defensive picks
  (e.g. ranged armour vs magic attackers). Needs monster attack styles
  (in the data: 'style' field, currently unparsed).
- **BiS proximity borders** - DONE (first tier, 2026-07-08): a gold
  border + "best available" tooltip when your slot's item matches the
  game-best set's pick. Remaining: the gradient by %-towards-BiS for
  non-matching slots (needs per-slot dps attribution).
- **Quest rewards in the budget pool** - DONE (2026-07-08): with the
  upgrade budget on, curated quest rewards (quest_rewards.json, wiki
  verified) join the green pool at 0 gp, labeled "quest: X" instead of
  a price; they never charge the gp budget or the upgrade-cost sum.
- **Slot alternates** - expandable per-slot list of runner-up items
  (owned and BiS variants).
- **Spec-throughput vs ring slot** - jointly optimize the ring: a
  Lightbearer doubles spec regen (sustained spec dps is now shown in the
  spec tooltip), so vs a Venator/Ultor ring the right pick depends on
  the spec weapon's net gain. Optimizer should compare the two totals.
- **Constraint parameters + parameterized link-in API (added 2026-07-16)** -
  broader query constraints: max total gear WEIGHT (data prereq: carry the
  weirdgloop weight field through refresh_data.py - not vendored today) and
  max total WORN VALUE gp (distinct from the upgrade budget, which caps
  acquisition of unowned gear; this caps the whole set, owned or not).
  Both are additive caps - the riskGp beam pattern. Single weapon/set is
  already covered by pinning. Then extend the loadoutlab/"search"
  PluginMessage with OPTIONAL keys (maxWeightKg, maxSetValueGp,
  pinnedItems, style, slayer, wilderness) - additive keys keep the README
  stability promise; the panel applies them like back/forward replays so
  deep-linked params are visible and steppable, and they join cache keys.
  Payoff: deep links from restriction-bearing COMBAT ACHIEVEMENTS via a
  curated CA -> (monster, params) table (Goal Planner or a CA-interface
  right-click as senders). Same plumbing later serves pure/zerker
  constraint profiles (no-Defence-xp styles, level caps).
- **Rune-aware spell auto-selection (added 2026-07-16)** - the OWNED
  card's auto-spell must be castable with runes you actually have: no
  Surge suggestions without wrath runes. Data prereq: a spell_runes
  table (spell -> rune ids+qty; wiki-generated in refresh_data.py or
  curated once - the vendored spells.json has no costs). Gate logic:
  each required rune is either PROVIDED by the wielded staff
  (elemental + combo battlestaves, kodai, tomes) or OWNED per the
  ledger with combination-rune substitution (lava covers fire+earth)
  plus rune-pouch contents via RuneLite varbits (pouch runes are in
  no scanned container). Membership check, not quantity (ledger
  model). Pinned spells BYPASS the gate (explicit intent, like pins);
  game-best/BiS stays ungated (runes are cheap consumables there -
  the assumed-potion philosophy). Pairs with the ironman-aware
  direction from the 2026-07 audit.
- **Undo/redo for mutations** - DONE (2026-07-15, 0.2.4): goal planner's
  command pattern ported as `com.loadoutlab.command` (Command.apply()/
  revert(), CommandHistory bounded at 50, CompositeCommand + ref-counted
  compounds; session-only, EDT-only, cleared on profile change).
  Header-inline undo/redo arrows with peek tooltips. Every deliberate
  mutation routes through Commands at the plugin-adapter seam: global +
  per-mob exclusions, pins (undo restores the PRIOR pin), notes, pinned
  spells, dream items, stored-elsewhere, protect-only, trip supplies
  (undo restores the persisted name). EXPANDED 2026-07-16 (field
  feedback): the arrows are BACK/FORWARD over one stream - monster
  selections and panel parameters (toggles, dropdowns, budget, antifire
  flip) record as steps alongside the store mutations; replays drive
  the real controls behind a reentrancy guard. Scan-driven mutations
  stay off the stack by design. SaveBatchingCommand deliberately not
  ported until a bulk op exists. Command core kept in lockstep with GP.

Shipped since (outside this list): 0.2.2 (2026-07-14: set-card
scroll-expand fix, classic equipment-tab gear layout option, NPC
right-click menu toggle); 0.2.3 (2026-07-15, merged to main: date-aware
mascot loading-animation roster - six moods, weighted calendar windows,
dev-mode gallery).

Hub cadence: hub submissions are not roadmap items. Next hub submission
lands with 0.2.4 (this batch: audit fixes, back/forward, style icons -
0.2.3's mascots ride along in it).

Player audit (2026-07-15, docs/audits/2026-07-15-player-audit.md): a
merciless full-engine review from every persona (new/mid/max x main/
iron/UIM/F2P/pure). Quick-wins batch landed same day: requirements
backfill (452 rows, ~4 years of gear), salve(ei) tiers + magic damage,
wilderness toggle, dragonfire honesty, uncharged-weapon gate, default
monster versions, leaf-bladed battleaxe, F2P assumptions, sub-31
prayer labels. Remaining audit findings (fight-state inputs, prayer/
boost override, ironman acquisition paths, TTK, raid scaling, spec
defence styles, brimstone/twinflame...) feed the backlog above.

Done from this list: BiS section show/hide (v/> header, 2026-07-05);
monster duplicate collapsing (2026-07-05); item exclusions/protection
(2026-07-06: right-click any suggested item -> excluded globally and
persisted; 'Excluded items: N' manager under the F2P toggle; blowpipe
dart tiers respect it). Follow-up: per-monster exclusion scope.

Set bonuses status (asked 2026-07-05, corrected 2026-07-08): modeled -
Void (all styles, elite ranged), Obsidian set + berserker necklace,
Inquisitor's, slayer helm, salve, AND crystal armour + Bofa/crystal bow
scaling (helm +5% acc/+2.5% dmg, legs +10%/+5%, body +15%/+7.5%; verified
vs the official engine 2026-07-08 at 0.0% delta per piece and full set -
this list previously claimed it was missing, which was wrong; two real
defects WERE found and fixed: the multiplier floored after salve/slayer
instead of before, and inactive pieces counted). NOT modeled - Dharok's,
Ahrim's proc, elite void magic damage. Tracked in docs/ENGINE-GAPS.md.

## Performance round (2026-07-09)

- Optimizer hot paths measured (JFR) and fixed with byte-identical golden
  outputs (`./gradlew golden`/`benchmark`, QueryBenchmark medians):
  bestPerStyle ~3.8x faster across the Graardor/Zulrah/Callisto x
  MAX_DPS/BALANCED x risk matrix (Graardor max-dps 8.6s -> 2.3s, balanced
  10.6s -> 2.8s, low-risk 3.9s -> 1.1s). Headline wins: precomputed
  lowercase name/category/label on GearItem (per-call toLowerCase was 64%
  of CPU), newline-joined name blobs on Loadout for wearing(), lean
  PvpRisk.riskGp for the beam, prepared IncomingDpsCalculator context,
  per-slot corpus index, D-4 sweep candidate-pool reuse, per-weapon spell
  prefilter.

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
  antifire-aware dragonfire, spiderling prayer degradation. Kraken added
  2026-07-17 (magic accuracy roll, prayer-piercing damage - wiki-quoted).
  TODO (field request 2026-07-17): curate the Moons of Peril bosses -
  Blood Moon, Blue Moon, Eclipse Moon (sheet styles are compound
  "Typeless slash" with staged max hits "32 total 4+8+20", so the v1
  model shows DTPS: ? today); parse each boss's wiki page and quote the
  mechanic sentences in the entry notes.
- **W-1 Wilderness low-risk sets (DONE 2026-07-07)** - for the curated
  wilderness boss list (WildernessMonsters), a "Low-risk" toggle caps the
  set to 3 tradeable items (4 with the Protect Item toggle) - exactly the
  kept-on-death items, everything else untradeable - via
  OptimizationRequest.maxTradeables enforced in the beam, the neutral
  fill, and the spec pick. Cards show "Risky items: N/K" with the
  tradeables listed. Per-item untradeable death costs (DONE 2026-07-07):
  untradeable_death.json + UntradeableDeathCosts price every worn
  untradeable per the June 2026 IKoD rework - wiki repair fees for
  breakables, the flat 500k trouver-mangle fee for the strong tier,
  component values for seed/mask/ring-style drops, and a conservative
  500k default for uncurated combat untradeables - all summed into
  PvpRisk riskGp (itemised in Assessment.untradeableCharges), so
  "low-risk" sets can no longer smuggle mangle-class gear for free.
  Follow-ups: skull awareness (keep 0/1), risk-value display in gp,
  panel itemisation of the untradeable charges.
- **D-3 Threshold-constrained search** - OptimizationRequest gains
  defensive constraints ("ranged def >= 100"); optimizer returns the best
  dps set satisfying them.
- **D-4 Ratio frontier (DONE 2026-07-08)** - the beam gains a defense
  weight (score = dps - w * incoming); OptimizerService sweeps four
  weights per style, traces the (dps out, dps in) frontier, and the
  "Optimize:" selector picks the point: Max DPS (endpoint, default),
  Balanced (the knee - farthest from the endpoint line), Tanky (best
  out/in ratio holding >= half max dps). The chosen set flows through
  every existing display (incoming, risk, spec, grids) plus a trade
  note ("Balanced: -7% less dps for -34% less damage taken").
  Remaining: D-3 explicit stat thresholds (subsumed for most uses by
  the modes), per-boss threshold derivation from the knees.

## Spec registry follow-ups (deferred, from the 2026-07 wiki audit)

Weapons the audit surfaced that need engine work beyond a registry row:

- Osmumten's fang Eviscerate - needs true-max recovery (the fang's normal
  attack already reshapes the damage roll; the spec math needs the
  unmodified max back).
- Tonalztics of ralos + Arclight/Emberlight - need new drain modes
  (flat/percentage stat drains that aren't defence-by-damage or
  defence-by-fraction).
- Dinh's bulwark - hit linkage unverified (single vs multi-target rolls).
- Dual macuahuitl - blood moon set gate; spec depends on worn set pieces.
- Eldritch nightmare staff - damage formula unconfirmed against the wiki.
- Webweaver bow - needs an N-hit kind (four hits at reduced max).
- Granite hammer - flat +5 damage rider on the spec hit.
- Dragon hasta - energy-scaled accuracy/damage (spends 5% per boost step).
- Soulreaper axe - stack-based (spec power scales with built-up stacks).
- Magic longbow Powershot - guaranteed-hit normal roll (accuracy skipped,
  normal damage roll).

## Spec value: win-over-replacement (shipped 2026-07-19, refines v0.2 #8)

The spec recommendation was re-modelled from gross output to the DPS a
spec ADDS to the kill over just attacking - marginal (each spec fired
gives up a main-hand hit), regen-aware over the fight length (10%/30s,
doubled by a Lightbearer), and drain-inclusive for every style (a defence
drain lowers the Defence LEVEL that ranged and magic roll against too, not
just melee). The card shows "adds ~X.XX dps" per spec; a spec that adds
nothing is not carried. This is the Max DPS view of a spec's value, and it
is calibrated: at General Graardor our game-best specs are dragon claws
(1.60) ~ voidwaker (1.56), matching the wiki's top pair, with the
warhammer correctly mid-tier.

## Future: "Smart" mode - model sustain, not just raw DPS

**The gap it fills.** Max DPS (and its spec value-over-replacement) is
correct by construction to ignore healing: a heal is not damage. But the
wiki's real-play recommendations lean on survivability the DPS number
can't see - the Saradomin godsword is wiki-#2 at Graardor for its heal,
and the toxic blowpipe is brought "for healing off the minions." Under a
pure-DPS metric those rank far too low. This is a deliberate limitation of
Max DPS, not a bug (see ENGINE-GAPS.md), and the fix is a distinct mode,
not a weight tweak - forcing sustain into the DPS number would move Max
DPS AWAY from the wiki (verified 2026-07-19), where it currently agrees.

**Distinct from Tanky.** Tanky/Balanced already trade DPS for LESS DAMAGE
TAKEN (avoidance - lower incoming DTPS). Smart mode is about RECOVERY:
turning HP restored into offensive-equivalent value, plus the realistic
play that raw DPS ignores.

**Proposed model (sketch, to design when built):**
- A heal-on-hit / heal-spec is worth the food-and-time it saves. The
  cleanest unit: HP restored per second -> equivalent DPS at THIS mob's
  incoming DTPS, so a heal is worth a lot at a hard-hitting boss and
  ~nothing at a mob that never lands. (SGS ~16.7% HP + 33% prayer on hit,
  blowpipe/Sara sword heals, guthan's, etc.)
- Realistic-play factors the same mode could fold in: food/brew inventory
  slots saved (freeing them for more supplies or a longer trip), the spec
  bar spent on sustain rather than burst, and prayer upkeep.
- Surface as a fourth OptimizeMode next to Max DPS / Balanced / Tanky, or
  as a sustain overlay on the existing frontier. Own branch off main, its
  own golden re-baseline, like the spec-value work.

## Future: teleport items in the trip kit (field ask 2026-07-20)

Always-filter common teleport items - construction cape, max cape,
crafting cape and friends - plus the SPECIFIC teleport that reaches the
searched boss/raid (e.g. a Rada's blessing for Mount Karuulm, a xeric's
talisman for CoX). Curated boss -> teleport table as a resource; joins
the trip-supply system's filter/layout the same way food does.

## Future: runes, spellbooks, and the "bring alchs" default (field ask 2026-07-20)

Rune planning is dictated by the spellbook brought, so it layers on a
spellbook model:

- **Bring alchs** default: always carry the nature/fire runes to alch
  drops - preferring a rune pouch (divine rune pouch when owned) EXCEPT
  in the Wilderness (never risk the pouch), and an explorer's ring as
  the runeless alch alternative when Magic is high enough and the
  spellbook is not standard.
- **Spellbook lock**: let the user pin a result to Arceuus (thralls -
  book of the dead + thrall runes in the kit) or Lunar (vengeance -
  its runes in the kit). Vengeance itself is a Smart-mode-adjacent
  modelling question (damage returned = DPS added) - model it when the
  spellbook lock lands.
