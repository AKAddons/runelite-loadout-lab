# DT2 incoming-damage notes (Whisperer, Leviathan, Phantom Muspah Teleporting)

Sources: the wiki main pages, their `?action=raw` wikitext (infobox parameters quoted exactly) and the
`/Strategies` pages, fetched 2026-10-24. Every number below is quoted or derived from a quote; every
assumption is labelled as one. Fields the wiki does not give carry `"uncertain": true` plus an
`uncertainReason` in the JSON. Two extra top-level fields (`cycleArithmetic`, `uncertainReason`) and
the per-row `uncertainReason` are additive and can be dropped by the parser.

Share convention used in every file: a cycle is one attack action of the boss (volley, orb, special,
melee attempt...). Rows that ride on another cycle (tentacles on every volley, boulders inside the
Lightning Barrage, debris under a roar) get `share: 0` so the sum stays <= 1 - they are all
`avoidable: true` anyway. The Whisperer's bind projectiles are counted as cycles but left unassigned
(no damage), which is why those files sum to ~0.89.

## The Whisperer (Post-quest 900 / Awakened 2700 / Quest 660)

1. Infobox (raw): `attack speed = 10` (one value, all versions), `attack style = All`;
   `max hit1 = 42 (x2) (Melee), 40 (Ranged), 36 (Magic), 45 (Screech), 50 (Soul Siphon)`;
   `max hit2 = 70+ (x2) (Melee), 53 (Ranged), 45 (Magic), 70 (Screech), 75 (Soul Siphon)`;
   `max hit3 = 30 (x2) (Melee), 28 (Ranged), 26 (Magic), 45 (Screech), 50 (Soul Siphon)`.
2. Regular attack: "shooting three projectiles per attack in either Ranged or Magic"; "Protection
   prayers will fully block the attacks"; "up to 40 and 36 damage respectively". Awakened: "five
   projectiles rather than three", "upwards of 45" magic / "upwards of 53" ranged, "Praying
   correctly fully negates the damage."
3. Mixing: single style until the first special; "M-M-R or R-R-M" after one or two specials;
   "M-R-M or R-M-R" after three. Awakened starts mixed ("R-R-R-M-M"), then "R-M-M-R-R" after two
   specials, "R-M-R-M-R" after the seventh. The leak values (0.1 Normal, 0.17 Awakened) are my
   estimates of how often a flick is late; the wiki gives no hit rates.
4. Melee: "hits twice, with the second hit dealing the same as the first and is only partially
   reduced by prayer"; "two heavy hits in two ticks"; "hits through prayer, always hits". Awakened
   gives the only prayed numbers: "between 50-72 damage if not prayed against", "If prayed against,
   it deals between 37-52 damage" -> prayerFactor 0.72. Applied to Normal/Quest as an assumption.
5. The melee only reaches you after a bind: "After each special attack, the Whisperer will usually
   launch a binding projectile at the player that lasts for ~8 seconds. She will then move towards
   the player in an attempt to melee them." Freeze ("any ice spell or standard spellbook binding
   spell will successfully freeze her") or stand 10+ tiles from the launch point to unbind. I priced
   one landed double hit per 3 binds (Normal) / 2 per 8 (Awakened); the no-freeze alternative is in
   each note (share 0.103 / 0.110).
6. Tentacles after every regular attack: 20 damage + 20% sanity (30/30 Awakened), "only one
   tentacle can hit the player at a time", dodged by an L movement; in Awakened the "+" formation is
   NOT dodged by the L movement. Listed as avoidable, share 0.
7. Specials queue at 80/55/30% (720/495/270 HP), eight in Awakened (~2400, 2120, 1820, 1600, 1310,
   1050, 800, 500). Screech 45 (+45% sanity; Awakened "five hits at 70"), Soul Siphon fail 50 (+heals
   her 100; Awakened 75, 16 souls, 21 s), Corrupted Seeds timeout 75 (+75% sanity; seeds 15-25,
   Awakened 60-75). All dodged by standard play (pillar / souls / realm) -> avoidable rows.
8. Enrage at 0 HP: heals 140 (250 Awakened), pulls you into the Shadow Realm, "increase her attack
   speed to 2 ticks (1.2 seconds)" (Normal only - Awakened enrage speed is not stated), styles flip
   every two attacks starting ranged (Awakened: every attack, starting magic), one tentacle per
   attack (Awakened two or three every two attacks). Prayer blocking is not restated for the enrage.
9. Least sure: kill length in cycles (16 / 45 / 12 volleys assumed), how often the bind actually
   ends in a melee, the Normal-mode prayed melee factor, the enrage projectile count and the
   Awakened enrage cadence, the tentacle/special damage style (filed as typeless), and every Quest
   mechanic (the wiki gives the Quest version only an infobox column).
10. Prayer summary: ranged/magic projectiles fully blocked when the prayer is up before impact;
    melee "hits through prayer" (partial, ~0.72 gets through); tentacles, Screech, Soul Siphon,
    seeds: the wiki gives no prayer interaction at all - position/mechanics only. The perfect-kill
    list counts "unprotected melee" as avoidable, i.e. prayed melee damage is treated as taken.

## The Leviathan (Post-quest 900 / Awakened 2700 / Quest 720)

1. Infobox (raw): `attack speed = Varies`, `attack style = Melee, Ranged, Magic`;
   `max hit1 = 50 (Melee orb & Bite), 24 (Ranged orb), 32 (Magic orb), 60 (Smoke Blast)`;
   `max hit2 = 86 (Melee orb & Bite), 41 (Ranged orb), 55 (Magic orb), 75 (Smoke Blast)`;
   `max hit3 = 34 (Melee orb & Bite), 16 (Ranged orb), 22 (Magic orb), 41+ (Smoke Blast)` (the
   "+" has no footnote). sheetSpeedTicks is filed as 2 (modal orb cadence) and flagged.
2. Volleys: "random volleys of coloured orbs - magic (blue), ranged (green), and melee (orange) - at
   varying attack speeds"; "protection prayers will fully block these attacks". Seven stages in
   order: "3 tick attacks with magic and ranged", "2 tick attacks with magic and ranged", "2 tick
   attacks with all three styles", "1 tick attacks with magic and ranged", "1 tick attacks with all
   three styles", "All three styles with faster projectile time (2 ticks to react)", "All three
   styles with even faster projectile time (1 tick to react)". "Leviathan will progress down this
   list ... whether you stun or not!"
3. The wiki never says how many orbs make a volley, nor that a special follows every roar. I assumed
   10 orbs per volley and one special per roar; the `cycleArithmetic` field lets you rescale.
4. Roar after each volley: "Players will take up to 7 chip damage from the roar but can avoid the
   debris by moving at least 1 tile away." Filed as an unavoidable typeless 7 (the debris hit itself
   has no damage figure anywhere and is avoidable).
5. Bite: "when in melee range which Protect from Melee will fully block"; "rarely used since it
   interrupts the volley sequence, but it has no warning animation" -> avoidable, share 0.
6. Specials alternate (first one told by the first roar's debris: east-west = Lightning Barrage,
   north-south = Smoke Blast). Lightning Barrage: NO damage figure on either page ("Run in a tight
   circle to stay ahead of the electric stroke"); its boulders are the table row "Rocks - None; run
   away from shadows - 25". Smoke Blast "up to 59 damage" (infobox 60; Awakened "70+", infobox 75),
   avoided behind the placed debris. "Damage dealt to the Leviathan is reduced by 33% for the
   duration of each special attack."
7. Stuns: shadow spells, "15 ticks (9 seconds)", "Damage against it is capped at 10 when stunned";
   stun + back-wound hit or 25% of its health -> "reset to the 2nd attack style on the list" (then
   3rd, ...), and it "use[s] a special attack as retaliation". Awakened: "Stuns are 50% shorter."
8. Enrage at 180 HP (20%): "attack speed will now stay at two ticks, losing the ability to launch
   orange melee orbs (but still being able to bite players)"; abyssal pathfinder 5x5 (7x7 Awakened):
   "otherwise, its attacks will pierce through prayer". Awakened: "The enrage starts at 540 HP",
   "Enraged attack speed is doubled to one tick (0.6 seconds)"; tornado at 1,350 HP, "Contact deals
   40+ damage"; five blocker debris in a "+"; boulders "+-shaped"; "the player is expected to take
   no damage from the lightning current or shockwave attacks".
9. Least sure: orbs per volley and specials per volley (both assumed), the enrage orb count
   (15 / 50 / 12 assumed), the lightning's damage (unknown, placeholder 0), the leak per stage
   (0.05 at 3 ticks up to 0.25-0.3 at 1 tick, my estimates), whether the 7 chip is truly
   unavoidable, the Quest enrage threshold (180 or 144 - not stated).
10. Prayer summary: each orb colour is fully blocked by its matching prayer (table), the bite is
    fully blocked by Protect from Melee; in the enrage the orbs pierce prayer entirely when you are
    outside the pathfinder's area and are fully blocked inside it; rocks/boulders "None"; lightning,
    smoke, debris, roar chip and tornado have no prayer interaction on the wiki (position only).

## Phantom Muspah - Teleporting form (850 HP)

1. Raw infobox: `version3 = Teleporting`, `hitpoints3 = 850`, `attack style3 = [[Typeless]]`,
   `attack speed3 = 5`, `max hit3 = 20 (Lightning cloud)`. Combat level 741 (one value).
2. Answer to the question: the "Typeless" lightning is NOT a rotation member. It is the stat column
   for one special attack, Lightning Clouds, which the prose only ever calls a special attack: "It
   has two special attacks that it uses at 637 (75%) and 425 (50%) health. If it spawns in ranged
   form it uses Lightning Clouds first, otherwise it uses Homing Spikes first." Strategies: "Ranged
   spawns will trigger Lightning clouds at 75%, melee spawns will trigger homing spikes at 75%.
   The unused special will always occur at 50% health." So it fires exactly once per kill.
3. Behaviour: "The Phantom Muspah will rapidly teleport around the arena, leaving behind lightning
   clouds that travel across the area, which also must be avoided." "Getting hit by a cloud will
   deal up to 20 damage."
4. Standard play takes nothing: "they can be avoided by standing on a specific tile" (the wiki has a
   map button for it); "Muspah will always appear to the north-east when the move is finished";
   "a weapon with an attack range of 10 ensures the player is never dragged out of the tile whilst
   attacking". Filed `avoidable: true`, share 1.0 inside the form, so the form prices at 0.
5. You keep attacking: "Muspah has combat stats equal to Range form, and can be attacked during this
   phase." "Damage dealt during this phase does not count towards the damage threshold for a form
   swap."
6. Not on the wiki: how long the phase lasts, how many clouds/teleports, whether the normal
   ranged/magic autos continue (nothing says they do), and whether any prayer reduces a cloud hit -
   "Typeless" is the only hint, filed as not prayable and flagged.
7. Companion special (for the other spawn): Homing Spikes, "1-4 spikes to slowly home in on the
   player for ~20 seconds" (Strategies "2-4", "1 tile per 2 ticks", harden after "34 ticks
   (20.4s)"; changelog 22 July 2026 cut it "from 50 ticks (30s) to 34 ticks (20.4s)"). No damage
   figure; a spike knocks you back and heals the boss.
8. Cross-check for the forms already priced (raw infobox): Ranged `attack speed1 = 6`,
   `max hit1 = 61 (Ranged), 72 (Magic)`; Melee `attack speed2 = 5`, `max hit2 = 34`; Shielded
   `hitpoints4 = 75`, `attack speed4 = 5`, `max hit4 = 72 (Ranged), 79 (Magic), 80 (Shockwave)`;
   Post-shield `attack speed5 = 5`, `max hit5 = 72 (Ranged), 79 (Magic)`.
9. No quest infobox column exists: "The Phantom Muspah is stronger than its quest variant, but
   otherwise shares the same attacks and mechanics."
10. Prayer summary (whole boss, from the Strategies): ranged fully blocked by Protect from Missiles,
    magic by Protect from Magic ("switch ... immediately" on the charge animation; applies
    corruption = prayer drain, "Ward of Arceuus can prevent it"); melee "deal[s] increasingly more
    damage the longer the Muspah stays still, even through prayer"; lightning clouds, homing spikes
    and the 80 shockwave are position-only (stand on the tile / behind a spike) with no prayer note.
