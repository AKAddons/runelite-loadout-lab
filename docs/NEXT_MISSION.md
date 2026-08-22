# Next mission: UI polish to classic parity

> **HISTORICAL (2026-08-12).** Written during the core/companion split,
> which the hub rejected; the merge-back retired the companion and this
> plugin is single again. The two-plugin ship order below never
> happened and must not be followed. The remaining ITEMS are still a
> fair polish backlog - read them as one-plugin work.

Andrew's list, 2026-08-12 (end of the Core/UI split session). All of
this is COMPANION-ONLY renderer work under the end-state rule -
Core's renderer is frozen and Core grows only facts (sprite ids,
flags, numbers), never pixels. Sync rule stays: if a fact is missing,
add it to `model/RenderModel.java` (or the engine), then draw it in
`runelite-loadout-lab-ui`.

Ordered roughly by how visible each is while daily-driving.

1. **Discord + Copy report move to the bottom.** Both currently sit in
   the chip row; the classic panel kept them as the footer actions
   under the results. Move them below the cards.

2. **Drop the stray "+ Sim" button** from the chip area - the `+N`
   green pill's menu already carries "Sim an item (consider as
   owned)...", so the extra button is duplicate surface.

3. **Inventory slider returns.** Currently a combo (`Inv 0/1/3/8`);
   the classic control was a slider over the bench size, which reads
   better and hits values between the presets. Param is `maxSwaps`.

4. **Prayer/boost selectors become icon dropdowns.** Today they are
   text combos on the card. The classic pickers showed the prayer
   sprite and the potion item icon; the model already publishes both
   (`assume.prayerSprite`, `assume.boostItem`) plus the option lists
   (`assumeOptions`). Make the control an icon + dropdown of icons.

5. **Spell selection and the spellbook lock merge.** Two controls
   today (the magic tab's spell dropdown, the chip row's "Auto book"
   combo). The classic had one spell row: the lock lives with the
   spell picker, shown only on Auto. Merge them onto the magic card.

6. **"Filter in bank" back in its proper spot** - it belongs with the
   bank actions on the card (beside Show in bank), not loose among
   the per-card buttons.

7. **Spellbook chip becomes an icon with a RED background when you
   are not on that book.** The classic spellbook plate did this: the
   sprite for the required book, red plate when your live spellbook
   does not match. Needs a fact: the live spellbook (Core already
   tracks it via VarbitID.SPELLBOOK - it was dropped when the panel
   died, so re-add it as a model field) plus the book each answer
   assumes.

8. **Icons for the stat lines** - max hit, accuracy, the protect
   prayer, prayer bonus. The classic painted these (hitsplat,
   crosshair, prayer sprite); the icon classes are gone by design, so
   use game sprites where they exist (AssumeIcons.prayerSprite covers
   the prayer) and small PNG resources otherwise - resources do not
   count against the hub token cap.

## Also open

- **BUG (highest priority): Virtus robe top beats Ancestral robe top**
  on Vorkath magic BiS. Impossible on our own corpus (same magic
  attack, ancestral has more magic damage; prayer is dps-irrelevant),
  so it is a candidate-pool/dedupe tiebreak bug - look at
  `LoadoutOptimizer.betterEquivalent` and `candidateScore`, not the
  calculator. Same family as the salve base-over-(ei) and poison-tier
  bugs.
- `docs/features.json` still lists `stored-elsewhere`; checkDocs will
  flag it at the next docs pass.
- The Companion is not on the hub, so Core still carries the rich
  renderer. When the dual-plugin question gets an answer, strip
  Core's copy to the bare fallback (one commit) and the duplication
  ends.

## Feature idea born from the Virtus bug (2026-08-13)

**Breakpoints.** The Virtus/Ancestral tie exposed the thing Loadout
Lab is uniquely able to answer: an upgrade can be worth ZERO. The 1%
magic damage gap never cleared max-hit truncation, so at these levels
against this monster the ~100m item buys literally nothing.

Surface it:
- Mark a slot when the shown item and the next tier price IDENTICALLY
  ("no gain here"), so a player does not buy a dead upgrade.
- Conversely, show what WOULD move it - the level or gear threshold at
  which the upgrade starts paying (the breakpoint).
- The engine already has everything needed: it prices arbitrary sets;
  this is a comparison pass plus a model fact, not new game modelling.

Andrew, on the bug's resolution: "identifying breakpoints like that is
WHY loadout lab exists."

## Runes in the Supplies row (Andrew, 2026-08-15)

Autocast answers should list their runes among the supplies. Needs a
WIKI-VERIFIED rune-cost table (spell -> runes per cast) - combat
spells are systematic (Strike=mind, Bolt=chaos, Blast=death,
Wave=blood, Surge=wrath + elementals; Ancients have fixed
soul/blood/death/chaos sets) but every family must be verified, and
staff-provided runes (elemental staves negate their element) should
be respected. Facts land on the card (model), the Companion renders
rune icons in the Supplies row.

## Kit ammo-slot flexibility (Andrew, 2026-08-15, engine)

In hybrid/kit results the melee view refuses ranged ammo in the ammo
slot - but keeping arrows equipped while meleeing is standard play,
and sometimes Rada's blessing is exactly the right thing to swap
OUT for the ranged switch. The kit solve should treat the ammo slot
as shareable across styles (arrows ride the melee set at zero swap
cost when a ranged switch exists) and let the blessing-vs-ammo choice
compete on dps. Engine work: per-style ammo pools + kit slot
unification.

## Piece-by-piece dps breakdown (Andrew, 2026-08-15, roadmap)

Replace one-off lines like the spec dps with a real BREAKDOWN view:
per contribution (set base, each situational bonus, spec, thralls),
what it adds to the shown number - the engine already computes these
as counted bonuses + fold terms; the model would carry a
contributions list per card and the Companion renders it (likely
behind the Stats hover or an expandable row). Pairs with the
breakpoints feature: contribution granularity is the same data.

## Ship-order rule for 0.3.6 (2026-08-17)

The bare fallback's Get Loadout Lab UI button resolves: enable-if-
disabled -> ExternalPluginManager.install('loadout-lab-ui') ->
browser to the hub page. Both later tiers assume the companion IS
PUBLISHED. Therefore: the companion ships to the hub FIRST, core
0.3.6 second. Also verify on a live client whether install() on an
unknown id throws (handled) or fails silently async (would need a
timeout message).
