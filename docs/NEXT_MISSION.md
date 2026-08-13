# Next mission: UI polish to classic parity

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
