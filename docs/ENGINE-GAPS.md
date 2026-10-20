# Engine gaps & known issues (vs the wiki DPS calculator)

From the 2026-07-05 vendoring analysis of guccifurs/best-dps, updated as
fixes land.

## Current state (2026-10-19, 99 harness vectors)

90 of 99 vectors agree with the official calculator within 0.5%; most
are exact. What is still open, largest first - every one is pinned by a
vector, so none can drift further unnoticed:

| Vector | Delta | Note |
|---|---|---|
| `kodai-firesurge-bloodmoon` | -53% | the moons MAGIC family; we under-price, so it never wins a seat it should not |
| `barronite-greygolem` | +15% | a deliberate wiki-vs-official disagreement (see below) |
| `eyeofayak-td` / `bofa-td` / `kodai-watersurge-td` / `shadow-td` | +2.6 to +3.4% | the Tormented Demon elemental family |
| `purging-demonbane-td` / `purging-darkdemonbane-duke` | +0.7 to +0.9% | demonbane spells: we scale the max and roll uniform, they transform every roll |
| `watersurge-tome-drake` | -0.9% | a deliberate wiki-vs-official disagreement on the tome's accuracy (see below); the max hit is exact |

Everything else - flat armour (both signs), the twisted bow, twinflame,
Inquisitor's, the quiver, salve/avarice, autocast stance, demonbane
vulnerability, sanguinesti - is 0.0-0.3%.

The sanguinesti gap (-11.2%) and the tbow+quiver gap (+0.7%) that used
to sit here are CLOSED (2026-08-22).

## Verification harness (the dispute resolver)

`python3 scripts/verify_official.py` runs BOTH engines - ours and the wiki
calculator's own (local clone of weirdgloop/osrs-dps-calc; see
scripts/official-harness/README.md) - on the scenario table in
`OfficialVectorExport.java` and prints per-scenario deltas. As of 2026-07-05
every melee scenario matches within 0.2%; the exact remaining deltas are
listed below.

## Harness-verified deltas (2026-07-05)

- ~~Weapon stance availability~~ FIXED: upstream tried every attack type and
  stance on every weapon (whip aggressive, barronite mace "slashing" a Grey
  golem past its 300 crush defence). `WeaponStyles` now models the real
  per-category style table; melee converged to <=0.2%.
- ~~Tumeken's shadow~~ FIXED: the shadow now triples the magic ATTACK bonus
  too (was damage only); shadow-zulrah converged to -2.3%.
- ~~Seeking arrows~~ FIXED 2026-10-06: a landed seeking arrow deals at least
  3 (vectors msb-seekingrune-goblin, msb-seekingdragon-graardor).
- ~~ToA-only stats~~ FIXED 2026-10-01: the shadow multiplies by four inside
  the Tombs of Amascut; the keris partisan of Amascut drops to the base
  partisan's stats outside them (vectors shadow-akkha, kerisamascut-*).
- ~~Sanguinesti / prayer stacking~~ CORRECTED 2026-07-16: the earlier
  "fix" stacked Augury (4%) + Mystic Vigour (3%) to 7% magic damage and
  converged vs the official calc - but only because the harness fed the
  wiki engine both prayers, and it stacks whatever it is given. In game
  all magic prayers share one prayer group, so only one can be active;
  the engine now assumes Augury alone (4%) when unlocked.
- ~~Autocast speed~~ FIXED: casting is 5 ticks (harmonised 4), not the
  wand's melee speed - upstream overstated every autocast DPS by 25%.
- ~~Demonbane~~ FIXED to official/wiki values: spells +20% accuracy only
  (Purging staff doubles to 40%; the damage bonus needs Mark of Darkness -
  not modeled, matching the official default), silverlight/darklight +60%
  damage only (upstream also boosted accuracy), bone/burning claws +5%
  acc+dmg added. kodai-demonbane and purging-demonbane exact 0.0%.
- ~~Elemental spell class scaling~~ ADDED (June 2025 rebalance): elemental
  spells share a class-wide max scaled by Magic level (Water Surge at 95+
  hits like Fire Surge) - this is what makes weakness-matched spell picks
  win vs elemental-weak monsters.
- ~~Elemental weakness accuracy stacking~~ FIXED 2026-07-23 (the Wiki
  calc button's first field catch, Iron dragon + Earth Surge): the
  +severity% accuracy bonus adds severity% OF THE BASE ROLL after the
  conditional multipliers (official-verified), not a multiplier on the
  boosted roll - with slayer helm + dragon hunter wand active the old
  order over-credited ~1.4% dps. Damage side was already additive-from-
  base and unchanged. Harness vector: dhw-irondragon.
- ~~Tormented demons~~ ADDED (TormentedDemonRules, matching the official
  default phase): guaranteed hits; 20% damage reduction bypassed by
  demonbane and abyssal weapons. All TD scenarios within 3.4%.
- ~~Bone staff vs Scurrius / systematic magic accuracy~~ FIXED 2026-07-06:
  the +2 accurate stance was missing from effective magic level - every
  magic sweep row converged to 0.0%. (The same fix originally layered
  Mystic Vigour's x1.18 accuracy after Augury's floor; that second step
  died with the prayer-stacking correction above.)
- ~~Style immunities / NPC rules~~ ADDED 2026-07-06 (MonsterMechanics,
  id lists vendored from the official calc): magic/ranged/melee immunity
  lists (Zulrah meleeable with a polearm - both engines now agree at
  6.390 dps exactly; Dusk ranged/magic immune), leafy gating, salamander-
  only and pickaxe-only (CoX Guardians) monsters, Corporeal Beast halving
  (full damage: stab spears/halberds/fang-stab/magic), Kraken ranged 1/7,
  Tekton magic 1/5, Ice demon 1/3 without fire, Slagilith, zogres, and
  the defends-magic-with-Defence-level list. Sweep mode
  (verify_official.py --sweep) adjudicates the optimizer's own full-gear
  picks across a 14-monster battery: all rows within 3.7%, mostly exact.
- ~~Twisted bow vs Zulrah~~ RESOLVED 2026-07-06: #3 was misdiagnosed -
  the tbow polynomial and caps were identical to the official engine all
  along (tbow-hydra converges exactly). The 62-vs-50 was ZULRAH's
  mechanic: hits above 50 reroll to 45-50 (Jagex-confirmed) - now
  modeled; tbow-zulrah exact 0.0%. Similar per-monster hit transforms
  NOT yet modeled: Fragment of Seren, Kraken ranged halving.
- **Barronite mace accuracy** (+15%): we apply the wiki-stated 15% accuracy
  bonus; the official calc applies only the damage part. Wiki page
  explicitly says both - keeping ours, watch upstream.
- Residual ~2.6% on guaranteed-hit TD rows: EV rounding in the 0.8x
  reduction (they floor per-hitsplat with min 1; we scale the mean).
- TD emberlight melee -3.7% (max 52 vs 54): demonbane add-factor rounding
  order. Scythe rows: our maxHit reports the first hit, theirs the 3-hit
  total - display-only, dps exact.

## Osmumten's fang inside the Tombs - CLOSED 2026-10-18

Vectors fang-baba-i300 and fang-warden3-i300 (invocation 300) ran the
fang 17.4% and 23.7% under the calc on hit chance while the sulphur
blades matched exactly (TRIPLEKILLTRENT, YouTube: "salad blade is
better than my fang at toa"). Inside the Tombs the fang rolls accuracy
twice (1 - (1-p)^2, official PLAYER_ACCURACY_FANG_TOA); we applied the
outside reroll formula everywhere. Both vectors are exact now.

## The Fractured Archive (released 2026-10-20)

In the corpus from release day through scripts/wiki_intake.py (the
wiki's infoboxes, ids from runelite-api 1.13.3 gameval; weirdgloop's
cdn still carried the 2026-09-02 data): the Archive Guardian, Ezekiel,
Bork, The Smorks, Dreadnoth the Jellied, Moia, Xarth and Ocellus (three
phases, shared hitpoints) as the raid group, and the Obligator, Zorya's
tome, the Ascension crossbows, the Rondache and the five ascension bolts
as gear. Modelled: Zeal (25% accuracy / 28% strength at 84 Prayer, 75
Attack and 75 Strength, gated on VarbitID.PRAYER_ZEAL_UNLOCKED), the
tome as a 3-tick powered staff with a flat base max hit of 18, and the
crossbows as a 3-tick crossbow (2 on rapid) that fires ascension bolts
only. Not modelled, each priced as plain: the Obligator's Lunge
multi-roll and its 3-tick Smash (it prices as a 5-tick crush
two-hander), the tome's special (150% accuracy / 60% damage and three
buffed 2-tick follow-ups; no energy cost published), the Rondache's +2
flat armour on the player and its special, the ascension bolts' gem
procs, the Elemental fragments (+2 max hit per element, account-wide),
team-size hitpoint scaling, and the Dagon'hai Elder and Monk (no
published stats; out of the group until the wiki carries them). No
official-calculator vectors yet: the calculator had none of the raid on
release day.

## Multi-combat card (2026-10-19)

Not a vector family: the official calculator has no multi, so only the
per-target rows (the normal sets with a tool pinned) are verifiable
against it. The stack multiplier is explicit on the card; the venator
chain factor follows the wiki's bounce rule (two bounces at two-thirds
of the max hit, each on its own accuracy roll), not a measured model of
how often a bounce finds a second target. Multi specs are priced as the
stack capped at the spec's reach: the bulwark's second hit on its target
and the rune thrownaxe's Chainhit (energy per hit, its own damage rule,
ends on a miss) are not modelled.

## Sunfire runes (2026-10-19, vectors firesurge-sunfire-araxxor, firesurge-sunfire-tome-araxxor)

Exact. The official calc takes minHit = trunc(maxHit / 10) for a fire
spell after the magic damage pool and the elemental weakness and before
the tome step (MIN_HIT_SUNFIRE, "applied pre-tome"), then rolls uniform
over [min, max]; we do the same (DpsCalculator.sunfireMinHit,
RollMath.expectedHit). The official harness carries it as the
usingSunfireRunes buff; verify_official.py now syncs the harness into
the clone before running, since a stale clone copy read the two vectors
as +9.9% / +9.0% (the plain cast) on the first run.

## Elemental tomes (2026-10-18, vector watersurge-tome-drake)

The wiki gives a charged tome +10% damage on a standard-spellbook spell
of its element against NPCs for all three tomes, and the Tome of Water
+10% accuracy as well (29 May 2024 update; the fire and earth pages
carry no accuracy term). The official calc multiplies the max hit by
11/10 for all three (we match it exactly, 44/44) and applies a 6/5
accuracy factor to the Tome of Water alone, before the elemental
weakness, under its own "todo does this go here?" comment
(PlayerVsNPCCalc.ts). We apply the wiki's 10% to the water tome after
the weakness step, so this vector sits 0.9% under the calc on hit
chance. If the calc moves to 11/10 the gap closes by itself.

## Correctness bugs in the vendored engine

1. ~~Dragon hunter wand~~ FIXED 2026-07-06: official-verified values are 7/4 accuracy and 7/5 damage (the gap note's wiki numbers were also wrong); upstream applied 7/4 to both. Surfaced when the early-return stacking fix let the wand's bonus compound.
2. ~~Silverlight/darklight~~ FIXED (found stale by the 2026-07-15 player audit A5.1): the code already applies 8/5 to damage only (DpsCalculator melee damage chain).
3. ~~Twisted bow caps~~ NOT A BUG (see harness-verified deltas: identical to official; Zulrah's reroll was the difference).
4. **Keris partisan of amascut** 1.15x looks invented; missing the 1/51 triple-hit proc (EV x1.0392).

## Missing features (priority order for v0.2)

5. ~~**Special attacks** — entire category absent~~ MVP SHIPPED 2026-07-05:
   `engine/SpecialAttack.java` models 20 spec weapons (wiki-verified costs and
   roll modifiers; DDS, claws cascade, voidwaker, godswords, DWH, dark bow,
   MSB snapshot formula, halberd sweep, blowpipe, volatile...). The optimizer
   surfaces the best OWNED spec weapon per style, swapped into the best owned
   set. Still missing: burning claws / eldritch NS (cascade + prayer-restore
   EVs), utility value of drains (DWH/BGS worth beyond the hit), spec-weapon
   purchase advice, ZCB bolt-proc damage (blocked on #7).
6. **Dharok's set** (HP-scaled max hit).
6b. ~~Crystal armour + Bofa~~ MIS-AUDIT (2026-07-06), then VERIFIED AND
   HARDENED (2026-07-08): the engine's isCrystalBow/crystalArmourPieces
   scaling (acc x(20+n)/20, dmg x(40+n)/40; helm 1 / legs 2 / body 3) is
   correct - harness scenarios bofa-graardor, bofahelm/body/legs/set-,
   cbowset- and bofasetslayer-graardor all 0.0% delta vs the official
   engine. Two real defects fixed 2026-07-08: (a) the multiplier floored
   AFTER salve/slayer; the game applies it to the base roll/max hit first
   (official engine has this in-game verified: bofa + slayer helm (i) +
   crystal body/legs maxes 36 with crystal-first, 37 the other way - at
   maxed+Rigour the two orders coincide, which is why the harness had not
   caught it), and (b) name-substring matching counted INACTIVE crystal
   pieces (same name, version "Inactive") as active, a false +30%/+15%.
   Pinned by CrystalSetTest (exact per-piece multipliers, inactive no-op,
   flooring order, blade-of-saeldor no-scale guard). Ahrim's 25% proc
   (+30% dmg) still missing.
7. **Bolt procs** (ruby/diamond/dstone(e), Zaryte cbow multiplier, diary bonus) — materially changes high-defence ranged answers.
8. **Elite void magic +2.5% damage** (only the accuracy part is modeled).
9. **Tomes** (fire/water/earth), smoke battlestaff, sunfire runes. (~~chaos gauntlets~~ MODELED 2026-09-08 with the elemental amulet family: +3 on bolt spells / +2 on the element, flat adds before the magic damage % - Mike's Discord report. Salarin honours the elemental amulet alone: flat 9-12 becomes 11-14 with it, nothing else counts.) (~~Mark of Darkness + demonbane~~ MODELED 2026-07-06: demonbane assumes MoD active - 40%/80% accuracy and +25%/+50% damage with the Purging staff; harness vectors carry a markOfDarkness flag so the official engine verifies under the same assumption. Purging staff is now the magic pick vs demons - TD magic converges at +0.7%.)
10. **Fang in ToA** (two-roll on defence too); ToA invocation / CoX-ToB party scaling generally.
11. **Wilderness weapons** (craw's/webweaver +50%).
12. Oddballs: colossal blade scaling, soulreaper stacks, dual macuahuitl / blood moon proc, atlatl melee-str scaling, burning claws burn, venator ricochet.
13. **Monster nuances**: ~~defence-drain~~ MODELED 2026-07-06 (DWH -30%, elder maul -35%, BGS by damage: valued as land-chance x main-set dps gain at drained defence x remaining fight from monster HP - drains now win the spec slot on tanky bosses and lose it on trash). NPCs using Defence level vs magic, phase resistances. (`immunities` is back in the data since the 2026-07-05 regen but not consumed yet. Vampyre tiers ARE modeled - `VampyreRules`, 2026-07-05: tier 3 hard-gated to the vyre weapon set, tier 2 halves non-silver damage; silver bolts vs tier 2 not modeled. Flying IS modeled - `FlyingRules`, 2026-07-05: melee blocked vs `flying` monsters except Polearm/Salamander category, per the Kree'arra June-2025 rule.)
15. **Style immunities not in the data**: Dusk is immune to Magic (wiki) but weirdgloop encodes neither that nor gargoyle-type finishing requirements (rock/granite hammer). A curated per-monster note table would cover: Dusk magic immunity + hammer-to-finish, gargoyles, rockslugs/salt, etc.
14. ~~Slayer-monster detection is a name heuristic~~ FIXED 2026-07-05: the data regen restored `is_slayer_monster` (the loader reads it; the name fallback remains as a safety net).


**Leagues filter (2026-09-10):** the corpus drops Leagues-only rewards by name list (`leagues_items.json`) plus the examine text where the wiki carries it. Drygore blowpipe (Demonic Pacts League) joined the list after a Discord BiS report; Rosewood/Blazing blowpipes are main-game and stay.

## Data snapshot

Bundled JSON regenerated **2026-10-06** by `scripts/refresh_data.py`
(weirdgloop corpus @ 89c3e25, 2026-09-02: 37 gear rows and 6 monster rows
new, 9 monster stat changes, dragon hunter wand speed 4; the never-released
Wristbands of the Arena join the non-standard overrides). Previous
regeneration 2026-07-05:
weirdgloop cdn/json (equipment/monsters/spells, ALL fields kept — 2,851
monsters incl. Maggot King; 41 new items incl. Necklace of rupture) merged
with wiki mapping metadata + live GE prices. `isStandardGear` carries over
from the best-dps snapshot for known ids (it is a curated usable-state flag,
NOT a main-game flag). `equipment_requirements.json.gz` is curated, not
regenerated. BACKFILLED 2026-07-15 (player audit A1.1): the file's
coverage had stopped ~Feb 2022, not "post-May" — 452 wiki-cited rows
added for fang/Torva/Masori/Shadow through Varlamore/Yama gear, plus
stale-row fixes (bowfa 80 Ranged + 70 Agility, saeldor 80 Attack).
Citations: docs/audits/2026-07-15-req-backfill.json. New items still
need rows added HERE when they release.
Re-run the script whenever content feels stale; the loader's leagues and
effect-spell filters survive regeneration.

## Amulet of the damned / barrows set effects (2026-07-08)

The damned amulet is stat-identical to a glory but crumbles on ANY death
(category 5 in untradeable_death.json), so it loses every stat tie to the
glory by design (LoadoutOptimizer.betterEquivalent). Its real value -
enhancing barrows set effects (Dharok's, Ahrim's, Guthan's...) - is NOT
modeled; when set effects land in DpsCalculator, the damned will re-earn
its slot through dps with full barrows worn, no special-casing needed.
Until then it only surfaces when no glory is owned.

## Moons of Peril sets (2026-07-17)

All three set effects are modeled (broken pieces count, per the wiki):

- **Bloodrager** (blood moon + dual macuahuitl): the weapon's two hits
  are CHAINED (first rolls max/2, second remainder only when the first
  lands) and the full set's expected attack interval is
  `speed - (acc/3 + acc^2*2/9)` - both mirror the official calc.
- **Eclipse** (eclipse moon + atlatl): the atlatl's BASE damage side is
  official-mirrored (Strength level, worn melee strength bonuses, melee
  salve/black-mask variants, ranged void kept; accuracy stays ranged).
  The burn set effect goes BEYOND the official calc (it flags moon set
  effects unsupported): wiki average of ~2 burn damage per landed hit,
  added as `acc x 2` expected, gated on the corpus burn-immunity column
  (Normal/Strong immunity blocks it, Weak does not).
- **Frostweaver** (blue moon + spear): also beyond-official - Standard
  binds and Ancient ice casts add a 20% (Arceuus grasps 50%) chance of
  an instant spear hit, modeled as chance x the spear's plain melee
  expectation with no melee prayer (you are on a magic prayer while
  casting). Chips name all three effects.

Not modeled: burn stack-cap/target-death truncation (the wiki's own
average ignores it too), and Frostweaver's manual-trigger variants.

## Sustain / healing not modelled (DELIBERATE - Max DPS limitation, 2026-07-19)

The engine models raw DPS and, since 2026-07-19, a spec's DPS added over
just attacking. It models ZERO survivability value: a heal-on-hit or a
heal-spec (Saradomin godsword, toxic blowpipe, Saradomin sword, Guthan's)
is worth nothing under Max DPS. This is CORRECT for a pure-DPS metric - a
heal is not damage - and it is why our spec/weapon picks match the wiki on
Graardor for the top DPS options while the wiki also lists SGS (heal) and
the blowpipe (heal off minions), which we rank low. It is NOT a bug; do
not "fix" it by crediting healing inside Max DPS - that was verified to
move the ranking away from the wiki. The proper home is the roadmapped
"Smart" mode (see ROADMAP.md), which values HP restored as offensive-
equivalent DPS scaled by the mob's incoming DTPS.

## Moons magic accuracy (found 2026-08-21, vector kodai-firesurge-bloodmoon)
Ours 0.47 vs official 0.98 dps (Kodai + Fire Surge vs Blood Moon) - a
~2x magic gap on the moons family, PRE-EXISTING (the 2026-08-21
flat-armour fix moved it only ~3 points). Suspect: our magic defence
roll vs their monster magic-level/defensive-magic pairing on these
rows. The harness vector stands as the dispute pin.

## Tbow + quiver sub-1% residual - CLOSED 2026-08-21
It was the twisted bow's missing accuracy CLAMP (141% vs their 140%
at the 250 magic cap), not the quiver at all. tbowquiver-zuk and
tbow-zulrah/hydra are exact; tbowseeking-dukeawakened sits at
-0.27%.
