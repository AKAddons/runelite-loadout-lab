# Incoming-damage notes: Sol Heredit, The Hueycoatl, Tormented Demon, Mysterious Figure, Moia, Xarth, Dreadnoth the Jellied

Source of truth: oldschool.runescape.wiki, fetched 2026-10-24. Every number below is a wiki quote or
arithmetic on one; anything the wiki does not give is labelled ESTIMATE, PROXY or PLACEHOLDER and the
JSON row carries `"uncertain": true`. Conventions used in the JSON files:

- `maxHit` of a counted row that the wiki does not give = a labelled PLACEHOLDER/PROXY (note says which).
- `maxHit` 0 = unknown on an uncounted row (share 0 and/or avoidable), kept for the tooltip only.
- Parallel hazards (lasers, puddles, minions, DoTs) have `share` 0 and their own `speedTicks`.
- The four Fractured Archive files are 4 days after release; the wiki itself says "some information may still be missing".

---

## Sol Heredit (1500 HP) - `sol_heredit.json`

1. Infobox: HP 1,500, attack speed "Variable", max hits "44 (Typeless AOE)", "44 (Grapple)", "15-25-35 (Triple Parry 1)", "15-30-45 (Triple Parry 2)".
2. Primary rotation = spear stabs (7-tick) and shield slams (6-tick), both 1 tick faster below 75% HP; all typeless AoE, "unaffected by prayer", dodged by stepping (strategies: "up to 45 Typeless Melee damage", infobox 44).
3. Triple Attack (under 90% HP): 15/25/35, from phase 4 (50%) 15/30/45; hits 3 ticks apart (4 for the last below 50%); blocked only by a tick-perfect Protect from Melee; "Activating any protection prayer too early will disable it and force you to take the damage".
4. Grapple (below 75% HP): click the called equipment slot within 4 ticks or take "~45" ("up to 45"); perfect parry on the last tick = guaranteed max hit next.
5. Phase transitions at 90/75/50/25/10%: 6 beams -> molten sand after 2 ticks, crystal sphere "up to 75 damage" (strategies "70+"), 3 ticks to react (2 in enrage).
6. Molten sand "~6-8 damage ... per tick"; enrage (10%, 150 HP) adds a sand every 3 ticks and lasers "about every 7 seconds".
7. A clean kill is a zero-damage kill; the model's only counted damage is Triple Attack leak (estimate 10%).
8. Least sure: how OFTEN the Triple Attack and the Grapple happen - the wiki gives no frequency, so shares 0.1 + 0.1 are mine; also the 44-vs-45 and the enrage "4.2 ticks (7 seconds)" inconsistencies are the wiki's own.
9. Prayer: AoEs - not at all ("does not reduce his AOE damage"); Triple Attack - fully, but only on the exact tick; Grapple, beams, sand - the wiki says nothing (treated as typeless/positional).
10. Cadence note for the model: no sheet speed; spear 7 -> 6 ticks, shield 6 -> 5 ticks after 75%.

## The Hueycoatl (2500 HP) - `the_hueycoatl__normal.json`, `the_hueycoatl__shielded.json`

1. Infobox: HP 2,500, max hit 14, speed "Variable", styles Melee/Magic/Ranged; states Normal, Shielded ("It's shielding itself"), Defeated share one stat block.
2. Two standard attacks all fight: rain fire (coloured feather projectiles: red melee, green ranged, blue magic) and glowing symbols (lightning).
3. Rain fire: "If correctly prayed against, the damage is reduced to 0-2; otherwise, the attacks deal up to 14 damage" -> prayerFactor 2/14 = 0.143, a PARTIAL block.
4. Lightning: "After 4 ticks, lightning will strike any player still standing on them for up to 16 damage" -> avoidable.
5. Tail shockwave: tail infobox max 25, "Typeless"; "20-25 damage" each time the tail reappears, "approximately every 20 seconds" in the tail phase, "one every ~15 or so seconds" in the final phase; dodged by running through it.
6. Shielded = phase 3: at 50% HP "she will shield herself and become invulnerable"; break the 300-HP tail (damage cap 4 or 9 per hit) while rain fire continues (inferred from "will only be damaged by the rain fire attack" near the entrance in the last three phases).
7. Final phase: "attack speed will significantly increase" (no factor given) plus shockwaves; body segments "Does not attack".
8. Perfect Hueycoatl CA defines avoidable damage: "lightning attack, tail slam attack or off-prayer projectile attacks".
9. Least sure: the tick cadence (wiki only says "Variable" - 5 ticks is my estimate), the rain-fire:lightning ratio and the per-colour split (even splits assumed), and whether lightning is cast during the shield phase.
10. Prayer: rain fire - partial (0-2 of 14); lightning, shockwave - the wiki never says (modelled as unprayable, dodged); nothing is described as piercing prayer.

## Tormented Demon (600 HP) - `tormented_demon.json` (one entry; four infobox versions IDs 13599-13602 have identical stats)

1. Infobox: HP 600, speed 6 ticks, max hit "31 (auto)" and "45 (special)", styles "All", level 450.
2. How "All" rotates: melee if it can reach you; if not, "randomly fire magic and ranged attacks" - and it KEEPS that style until its next fire bomb ("always change their combat style after a fire bomb attack").
3. Fire bombs + bind "roughly every 60 ticks (36s; 10 attacks)": one bomb on your tile, one adjacent; "2 ticks (1.2s) to move to a safe tile ... or they will take 40+ damage"; bombs still land if the demon dies mid-air.
4. After a bomb: "If it approaches, expect melee"; otherwise the other of magic/ranged; if it last used melee "you will have to guess and/or correct your protection prayer".
5. So the per-cycle leak is ~1 guessed attack per 10 (wrong half the time) -> leak 0.05 on the style rows; bombs are dodged (avoidable) unless the spot has fewer than four safe tiles.
6. Two demons: "one will use melee if possible, and the other magic or ranged, and their attacks start out staggered so they can be prayed against"; a third only bombs.
7. Damage TO the demon (not incoming): fire shield -20% for non-demonbane/non-abyssal, dropped after the first hit and after each bomb; prayer swap every 150 HP lost (then it "stops attacking for 6 ticks"); defenceless (100% accuracy) windows.
8. Least sure: that the 45 "special" is the fire bomb (the wiki never labels it), whether the bomb is typeless (not stated), and the melee:ranged:magic split (0.2/0.4/0.4 is mine).
9. What pierces prayer per the wiki: nothing documented. The styles "can be prayed against"; the only odd sentence is the opening attack dealing "~75% less damage" when prayed with the shield down.
10. Prayer: melee/ranged/magic - full (no partial wording anywhere); fire bombs - not a prayer matter, dodge them.

## Mysterious Figure (450 HP) - `mysterious_figure.json`

1. Page exists under that exact name (DT2 assassin; the search landed on the Demonic Pacts League copy, hatnote to Lord Ieuan Amlodd). Infobox: HP 450, speed 4 ticks, max 18, styles "All", level 271, released 26 July 2023.
2. No Fight/Strategies section on the NPC page; mechanics come from the Desert Treasure II quest guide ("Testing Your Medal", "Recidivism").
3. "She can attack with all three attack styles, dealing up to 20 damage each time" (final fight) vs infobox 18 and the earlier "hitting up to 18 damage with each" - 18 used, 20 noted.
4. "It is recommended to pray Protect from Magic at all times, as her magic attack is by far the most accurate, damaging, and effective, draining your stats when it hits" -> melee and ranged are what gets through under a camped prayer.
5. "Every ten or so attacks, she will stun you with either melee (shove) or mage (Ice Barrage - this can splash)" then "a large purple orb, which can deal up to 35 typeless damage if not evaded".
6. Dodge: "the freeze or stun will wear off two ticks before the orb lands" - spam-click away -> avoidable.
7. She Tele Blocks, freezes, heals with blood spells, swaps her own protection prayer ("you need at least two attack styles"), is immune to venom, poison and recoil.
8. Final fight is mandatory and inescapable by teleport ("The barrier is the only way out"); earlier ambushes end with her teleporting away.
9. Least sure: how she picks a style each attack (wiki silent; 1/3 each assumed) and whether her prayed hits are fully blocked (not stated; assumed full).
10. Prayer: all three styles - assumed full (no partial wording); purple orb - typeless, dodge it.

## Moia (785 HP) - `moia.json`

1. New 20 October 2026, The Fractured Archive 3rd encounter (alternative: Dreadnoth). Infobox: HP 785, speed 5 ticks, max hit "?", styles Ranged/Magic, level 1,064, poisonous.
2. Main attack: fast ranged projectile from outside the arena ("A ranged or magic weapon is required as she is too far to reach with melee or halberds").
3. Periodic teleport + "slow blue projectile that deals magic damage"; the ranged shot launched after it "will hit first despite being launched second".
4. Prayer dance: "Do not switch to protect against magic until she attacks with ranged again", then "quickly switch to protect from magic once the second attack lands".
5. Cough: smoke-cloud projectile -> "a cough that deals intermittent poison damage, similar to Nex"; Serpentine helm "significantly mitigate[s]" it; spreads to teammates/binders, "immense damage over time".
6. Undead binders (level 135, 5-tick, max hit "?"): "will melee attack anyone within two tiles of them"; "protect from melee reduces the damage they deal" (REDUCES - partial, no fraction).
7. 33% HP enrage: binders in a diamond, charge bar; if any binder survives when it fills "she will deal massive damage to all of the players in the arena".
8. Hitpoints: infobox 785 with no table, but the raid page says all bosses scale with players and solo counts as two.
9. Least sure: EVERY max hit (none on the wiki - placeholders 25), the ranged:magic ratio (0.75/0.25 assumed), whether the cough projectile can be dodged.
10. Prayer: ranged/magic - presumably full (wiki does not say); binder melee - partial ("reduces"); cough and enrage blast - not prayer matters.

## Xarth (2600 HP) - `xarth.json`

1. New 20 October 2026, 4th encounter (alternative: Dagon'hai trio). Infobox: HP 2,600 solo (9,230 at 8), speed 4 ticks, max hit "?", styles Mage/Ranged/Melee, level 912, demon attribute, 85% water weakness.
2. "Unlike other Tormented Demons, Xarth does not have a shield to block incoming damage, however he does attack much faster." No prayer-swap mechanic is documented either.
3. Phase 1: "attacks exclusively with magic", closest player; "His magic attacks will inflict a burn which can stack over time" (Burn page: 1 HP per 4 ticks per stack, up to 5 stacks; Xarth's listed under "Similar mechanics", blasts burn "starting at 3 damage").
4. Phase 1 special on a fixed timer: bind all, two searing projectiles, explosive blast - hide behind a pillar; a player acting as the pillar takes "the full ~45 damage and those behind will take ~7"; DDing = everyone takes full.
5. Phase 2 begins "when all pillars are gone": "He will no longer use magic attacks, instead swapping to ranged and melee", static in the centre, "always attacks the player closest to him", ranged damage "calculated as the projectile hits you".
6. Phase 2 hazards: flames that "deal damage and inflict burns if stepped on" and spread; corpses (62 HP) walk in and heal him; "If the corpses heal Xarth for 200 health, he will respond with a large AoE blast across the entire arena, dealing heavy damage and inflicting a burn".
7. Corpse control: freeze or water spells (1-tick), never fire spells ("Fire spells spawn more corpses"); the blast "can be venged" and is reduced behind the tank.
8. Hotfix 21 Oct 2026: 60% Defence-reduction cap. Dev intent: "for players to be meleeing his final phase".
9. Least sure: all three auto max hits (PROXY 31 from the regular Tormented Demon), the phase-1:phase-2 time split (0.5/0.5), whether a prayed magic hit still applies its burn.
10. Prayer: the guide recommends NO prayer anywhere and never says full/partial; burns ignore Protect from Missiles per the Burn page; blasts/flames are positional.

## Dreadnoth the Jellied (2100 HP) - `dreadnoth_the_jellied.json`

1. New 20 October 2026, 3rd encounter (alternative: Moia). Infobox: HP 2,100 solo (7,460 at 8), speed 4 ticks, max hit "?", styles Melee/Ranged, level 888, 5x5, demon attribute, 85% fire weakness, 40% freeze resistance.
2. "He primarily attacks with melee, which will hit everyone around him, so it is recommended to range or mage him from afar"; he moves "around one tile every two ticks" -> his regular cycle is avoidable by kiting.
3. At 85%, 65%, 45% HP: "an arena-wide slam followed by three ranged attacks"; "Each sequential slam will have an additional hit, up to three slams".
4. "Ensure Protect from Melee is active for the slam, and Protect from Range is active for the projectiles."
5. Slams spawn "a lot of jellies" (Jelly (Dreadnoth): 61 HP, 5-tick, max hit 17 crush); "they will drain prayer with each attack, even if blocked".
6. Jelly puddles where jellies die and periodically: "6-8 damage per tick when stepped on".
7. 25% HP: "*glub glub*", jump-slam on a random player; "If Protect from Melee is not active, players will take damage regardless of location"; heals him ~30-40 if it lands; then one ranged + one jelly-splatter projectile ("keep moving").
8. Death spawns ~10 jellies; kill them to end the encounter.
9. Least sure: every boss max hit (uniform PLACEHOLDER 25), the per-kill shares of the HP-triggered specials (built on an assumed ~110 four-tick cycles per solo kill), and the jelly splatter's style.
10. Prayer: slams and jump - Protect from Melee (full implied, not stated); post-slam projectiles - Protect from Range (full implied); jelly melee - blocked but still drains prayer; puddles/splatter - positional.
