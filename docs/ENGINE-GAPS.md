# Engine gaps & known issues (vs the wiki DPS calculator)

From the 2026-07-05 vendoring analysis of guccifurs/best-dps. The vendor is
verbatim — none of these are fixed yet. Items 1-4 are correctness bugs to fix
FIRST (verify each against the wiki calculator's source before patching —
analysis confidence was medium on the exact multipliers).

## Correctness bugs in the vendored engine

1. **Dragon hunter wand**: engine uses 7/4 acc AND dmg; wiki: +50% acc, +20% dmg.
2. **Silverlight/darklight**: engine applies 8/5 to accuracy AND damage; wiki: +60% damage only.
3. **Twisted bow**: missing the 140%/250% damage/accuracy caps on the polynomial.
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
7. **Bolt procs** (ruby/diamond/dstone(e), Zaryte cbow multiplier, diary bonus) — materially changes high-defence ranged answers.
8. **Elite void magic +2.5% damage** (only the accuracy part is modeled).
9. **Tomes** (fire/water/earth), smoke battlestaff, chaos gauntlets, sunfire runes, Mark of Darkness + demonbane interplay.
10. **Fang in ToA** (two-roll on defence too); ToA invocation / CoX-ToB party scaling generally.
11. **Wilderness weapons** (craw's/webweaver +50%).
12. Oddballs: colossal blade scaling, soulreaper stacks, dual macuahuitl / blood moon proc, atlatl melee-str scaling, burning claws burn, venator ricochet.
13. **Monster nuances**: defence-drain (DWH/BGS, blocked on #5), NPCs using Defence level vs magic, phase resistances. (`immunities` is back in the data since the 2026-07-05 regen but not consumed yet.)
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
