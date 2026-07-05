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

5. **Special attacks** — entire category absent (our #8 differentiator).
6. **Dharok's set** (HP-scaled max hit).
7. **Bolt procs** (ruby/diamond/dstone(e), Zaryte cbow multiplier, diary bonus) — materially changes high-defence ranged answers.
8. **Elite void magic +2.5% damage** (only the accuracy part is modeled).
9. **Tomes** (fire/water/earth), smoke battlestaff, chaos gauntlets, sunfire runes, Mark of Darkness + demonbane interplay.
10. **Fang in ToA** (two-roll on defence too); ToA invocation / CoX-ToB party scaling generally.
11. **Wilderness weapons** (craw's/webweaver +50%).
12. Oddballs: colossal blade scaling, soulreaper stacks, dual macuahuitl / blood moon proc, atlatl melee-str scaling, burning claws burn, venator ricochet.
13. **Monster nuances**: immunities (snapshot stripped the field - restore on data regen), defence-drain (DWH/BGS, blocked on #5), NPCs using Defence level vs magic, phase resistances.
14. Slayer-monster detection is a name heuristic — fixed properly by regenerating data WITH `is_slayer_monster` (the loader already reads it).

## Data snapshot

Bundled JSON = weirdgloop data enriched with GE prices, snapshot **2026-05-13**
(2,829 monsters; missing post-May content incl. Maggot King; drops
`immunities`/`is_slayer_monster`/`max_hit`/`speed`/`style` fields). Regeneration
task: consume weirdgloop's published cdn/json directly + a small merge step for
prices/requirements, keep ALL fields, gzip into resources.
