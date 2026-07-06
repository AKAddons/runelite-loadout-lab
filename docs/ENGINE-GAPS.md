# Engine gaps & known issues (vs the wiki DPS calculator)

From the 2026-07-05 vendoring analysis of guccifurs/best-dps, updated as
fixes land. Items 1-4 are correctness bugs to fix FIRST.

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
- ~~Sanguinesti / prayer stacking~~ FIXED: Augury (4%) + Mystic Vigour (3%)
  stack to 7% magic damage; sang-goblin converged to -0.5%.
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
- ~~Tormented demons~~ ADDED (TormentedDemonRules, matching the official
  default phase): guaranteed hits; 20% damage reduction bypassed by
  demonbane and abyssal weapons. All TD scenarios within 3.4%.
- ~~Bone staff vs Scurrius / systematic magic accuracy~~ FIXED 2026-07-06:
  Mystic Vigour also grants x1.18 magic ACCURACY (applied with its own
  floor after Augury) plus the +2 accurate stance - every magic sweep row
  converged to 0.0%.
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

## Correctness bugs in the vendored engine

1. **Dragon hunter wand**: engine uses 7/4 acc AND dmg; wiki: +50% acc, +20% dmg.
2. **Silverlight/darklight**: engine applies 8/5 to accuracy AND damage; wiki: +60% damage only.
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
6b. ~~Crystal armour + Bofa~~ MIS-AUDIT (2026-07-06): the vendored engine
   already modeled it (isCrystalBow/crystalArmourPieces) - sweep Bofa rows
   converge exactly. Ahrim's 25% proc (+30% dmg) still missing.
7. **Bolt procs** (ruby/diamond/dstone(e), Zaryte cbow multiplier, diary bonus) — materially changes high-defence ranged answers.
8. **Elite void magic +2.5% damage** (only the accuracy part is modeled).
9. **Tomes** (fire/water/earth), smoke battlestaff, chaos gauntlets, sunfire runes. (~~Mark of Darkness + demonbane~~ MODELED 2026-07-06: demonbane assumes MoD active - 40%/80% accuracy and +25%/+50% damage with the Purging staff; harness vectors carry a markOfDarkness flag so the official engine verifies under the same assumption. Purging staff is now the magic pick vs demons - TD magic converges at +0.7%.)
10. **Fang in ToA** (two-roll on defence too); ToA invocation / CoX-ToB party scaling generally.
11. **Wilderness weapons** (craw's/webweaver +50%).
12. Oddballs: colossal blade scaling, soulreaper stacks, dual macuahuitl / blood moon proc, atlatl melee-str scaling, burning claws burn, venator ricochet.
13. **Monster nuances**: ~~defence-drain~~ MODELED 2026-07-06 (DWH -30%, elder maul -35%, BGS by damage: valued as land-chance x main-set dps gain at drained defence x remaining fight from monster HP - drains now win the spec slot on tanky bosses and lose it on trash). NPCs using Defence level vs magic, phase resistances. (`immunities` is back in the data since the 2026-07-05 regen but not consumed yet. Vampyre tiers ARE modeled - `VampyreRules`, 2026-07-05: tier 3 hard-gated to the vyre weapon set, tier 2 halves non-silver damage; silver bolts vs tier 2 not modeled. Flying IS modeled - `FlyingRules`, 2026-07-05: melee blocked vs `flying` monsters except Polearm/Salamander category, per the Kree'arra June-2025 rule.)
15. **Style immunities not in the data**: Dusk is immune to Magic (wiki) but weirdgloop encodes neither that nor gargoyle-type finishing requirements (rock/granite hammer). A curated per-monster note table would cover: Dusk magic immunity + hammer-to-finish, gargoyles, rockslugs/salt, etc.
14. ~~Slayer-monster detection is a name heuristic~~ FIXED 2026-07-05: the data regen restored `is_slayer_monster` (the loader reads it; the name fallback remains as a safety net).

## Data snapshot

Bundled JSON regenerated **2026-07-05** by `scripts/refresh_data.py`:
weirdgloop cdn/json (equipment/monsters/spells, ALL fields kept — 2,851
monsters incl. Maggot King; 41 new items incl. Necklace of rupture) merged
with wiki mapping metadata + live GE prices. `isStandardGear` carries over
from the best-dps snapshot for known ids (it is a curated usable-state flag,
NOT a main-game flag). `equipment_requirements.json.gz` is curated, not
regenerated — post-May items list no wear requirements until added there.
Re-run the script whenever content feels stale; the loader's leagues and
effect-spell filters survive regeneration.
