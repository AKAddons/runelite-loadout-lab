# Loadout Lab

Your gear, your enemy, your best kit. Pick a monster and Loadout Lab
computes the strongest set you actually OWN for every combat style -
exact DPS included - from live knowledge of your bank, inventory, and
equipment.

## What it does

- **Best owned set per style** (melee / ranged / magic) vs any monster,
  with exact DPS, max hit, and accuracy - engine verified against the
  official wiki DPS calculator.
- **Game-best comparison**: see the true ceiling set and how close your
  gear is, with gold borders on slots where you already own best-in-slot
  (stat-identical analogs count).
- **What to bring**: the prayer and boost the numbers assume (icons), the
  spell to autocast, the special-attack weapon to weave, and what to PRAY
  against the boss - including bosses whose attacks partially pierce
  protection prayers.
- **Incoming damage**: how hard the boss hits YOU in that set, with
  curated per-boss attack data (GWD, Zulrah, Vorkath, Cerberus, the
  wilderness ring, and more).
- **Optimize modes**: Max DPS, Balanced (best damage-out per
  damage-taken), or Tanky (least damage taken).
- **Wilderness risk**: low-risk sets built around the items-kept-on-death
  rules - your 3-4 most valuable items ride protected, everything else
  stays under an adjustable gp risk cap, with per-item death fates
  (halo = protected, skull = lost, coins = repair fee) and honest gp
  totals including untradeable repair/mangle fees.
- **Dream items and upgrade budgets**: consider unowned gear ("what if I
  had a tbow?") or let a gp budget suggest buyable upgrades - quest
  rewards join free with their source quest named.
- **Bank tools**: "Show in bank" outlines the set's items; "Filter bank"
  shows only them (uses the core Bank Tags plugin).
- **Exclusions**: right-click any suggestion to protect rare supplies
  (dragon darts) from being recommended.

## Getting started

1. Open your bank once so the plugin can learn what you own.
2. Search a monster in the sidebar panel and pick a style card.
3. Right-click items for exclusions and dream items; use the toggles for
   slayer tasks, spellbook locks, wilderness risk, and optimize modes.

## Privacy

Everything is local. The plugin writes two files under
`.runelite/loadout-lab/` on your machine only: `profile.json` (your
levels/bank snapshot, useful for bug reports) and `usage.tsv` (your own
search history). Nothing is ever sent anywhere.

## License

BSD 2-Clause. DPS engine derived from
[best-dps](https://github.com/guccifurs/best-dps) (BSD-2-Clause);
monster and gear data from the OSRS Wiki.

# Feature guide

One section per user-facing feature. Each heading below is mirrored in
`docs/features.json`; `./gradlew checkDocs` audits the two against the
source tree and flags drift or missing screenshots.

### Best owned set per style

Pick a monster and Loadout Lab computes the strongest set you actually
OWN for melee, ranged, and magic - with exact DPS, max hit, and accuracy,
verified against the official wiki calculator.

### Game-best ceiling comparison

Every style card can show the true best-in-slot ceiling set beside yours,
so you see how close your kit is. Slots where you already own the best (or
a stat-identical analog) get a gold border.

### Optimize modes

Choose Max DPS, Balanced (best damage-out per damage-taken), or Tanky
(least damage taken). The mode note tells you the frontier trade the
chosen set made.

### Owned-gear ledger (profile-aware)

Your owned gear is learned from your bank, inventory, and equipment as you
play, and remembered per account so suggestions always reflect what THIS
character actually has.

### Incoming damage and protection prayer

See how hard the boss hits YOU in the chosen set, from curated per-boss
attack data, plus which protection prayer to use - including bosses whose
attacks partially pierce prayer.

### Spell and spellbook recommendation

On the magic card, Loadout Lab shows the spell to autocast. Lock the
spellbook to your setup and the suggested spell and set adjust to match.

### Dream items

Right-click any suggestion you do not own ("what if I had a tbow?") to
highlight it as an aspirational pick and see the set it would build.

### Upgrade budget

Enter a gp budget and Loadout Lab suggests buyable upgrades within it; use
"-" for the unlimited ceiling. Quest rewards join for free with their
source quest named.

### Wilderness low-risk sets

Build low-risk sets around the items-kept-on-death rules: your most
valuable items ride protected while everything else stays under an
adjustable gp risk cap. Per-item death fates and honest kept/lost gp
totals include untradeable repair and mangle fees.

### Slayer task toggle

Flip the slayer-task toggle to fold in slayer-helm bonuses; bosses locked
behind an active task are greyed out.

### Exclude items from suggestions

Right-click a suggestion to protect rare supplies (like dragon darts) so
the optimizer stops recommending them.

### Bank tools: show and filter

"Show in bank" outlines the set's items in your bank; "Filter bank" shows
only them. Uses the core Bank Tags plugin.

### Search in Loadout Lab (cross-plugin)

Other plugins can send a monster straight to Loadout Lab: the panel opens
and computes the best owned set for it. Goal Planner's boss cards use this
for a right-click "Search in Loadout Lab" (rolling out).

### Community Discord

The header Options menu has a "Join our Discord" link to the plugin's
community server.
