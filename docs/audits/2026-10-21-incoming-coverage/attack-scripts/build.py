"""Build draft_attacks.json from hand-read wiki notes (2026-10-21). All entries verified=false."""
import json, sys, os, collections

FIELDS = ["style", "maxHit", "minHit", "hits", "share", "speedTicks", "accuracy",
          "prayerFactor", "wrongPrayerFactor", "avoidable", "chip", "dot", "dragonfire"]

def A(name, style, maxHit, src, **kw):
    a = {"name": name, "style": style, "maxHit": maxHit}
    for f in FIELDS[3:]:
        a.setdefault(f, None)
    a["minHit"] = kw.pop("minHit", None)
    a["hits"] = kw.pop("hits", 1)
    a["dragonfire"] = kw.pop("dragonfire", "none")
    a.update(kw)
    a["source"] = src
    a["verified"] = False
    order = ["name"] + FIELDS + ["note", "unknown", "source", "verified"]
    return {k: a.get(k) for k in order if k in a or k in FIELDS}

B = {}
def boss(key, page, version, speed, attacks, notes, unknowns):
    B[key] = {"wikiPage": page, "version": version, "speedTicks": speed,
              "attacks": attacks, "notes": notes, "unknowns": unknowns}

# ---------------- Moons of Peril ----------------
MOP = "Moons of Peril/Strategies#Standard attacks"
def moon_std(extra_note=""):
    return [
        A("3-hit standard (hit 1)", "typeless", 4, MOP, speedTicks=6, accuracy="roll",
          prayerFactor=1.0, avoidable=False,
          note="Rolls vs SLASH defence (+100 att bonus, 258 Att). Chain: a miss on hit N makes all later hits miss. Prayer does nothing." + extra_note),
        A("3-hit standard (hit 2)", "typeless", 8, MOP, speedTicks=6, accuracy="roll",
          prayerFactor=1.0, avoidable=False, note="Only lands if hit 1 landed (conditional accuracy)."),
        A("3-hit standard (hit 3)", "typeless", 20, MOP, speedTicks=6, accuracy="roll",
          prayerFactor=1.0, avoidable=False, note="Only lands if hits 1 and 2 landed."),
        A("Off-glyph curse tick", "typeless", 16, "Moons of Peril/Strategies#Eyatlalli's glyph",
          minHit=1, prayerFactor=1.0, avoidable=True, chip=None,
          note="1/tick doubling to 16/tick when off the imbued sigil; competent play = 0."),
    ]
boss("blood moon", "Blood Moon", None, 6, moon_std() + [
    A("Blood Rain pool", "typeless", 9, "Moons of Peril/Strategies#Blood Moon", prayerFactor=1.0, avoidable=True,
      note="Heals boss 2x damage dealt."),
    A("Blood Jaguar summon", "typeless", 15, "Moons of Peril/Strategies#Blood Moon", prayerFactor=1.0, avoidable=False,
      note="Boss deals up to 15 to create the jaguars; reads as unavoidable (once per jaguar special)."),
    A("Blood jaguar cleave", "typeless", 9, "Moons of Peril/Strategies#Blood Moon", prayerFactor=1.0, avoidable=True,
      note="6 attacks per special; heals boss 3x."),
    A("Jaguar-phase blood pool", "typeless", 6, "Moons of Peril/Strategies#Blood Moon", prayerFactor=1.0, avoidable=True),
], ["Every hit heals the boss (1x/2x/3x, 3rd hit heals 30-50 flat) - damage taken feeds kill time; defence matters twice.",
    "Rotation: 6 standard attacks then a special (alternating Rain/Jaguar); 156-tick cycle.",
    "Standard-attack share by attack count ~6/7, by time unknown."],
   ["unknown: special-attack duration in ticks (needed for time shares)",
    "unknown: prayerFactor is 1.0 by statement, but style 'typeless slash' means it rolls vs slash defence - the model needs a 'defence-roll style' separate from the 'prayer style'"])
boss("blue moon", "Blue Moon", None, 6, moon_std() + [
    A("Weapon Freeze failure", "typeless", 28, "Moons of Peril/Strategies#Blue Moon", prayerFactor=1.0, avoidable=True,
      note="Only if the ice block is not broken in time."),
    A("Ice shard shatter", "typeless", None, "Moons of Peril/Strategies#Blue Moon", prayerFactor=1.0, avoidable=True,
      unknown="unknown: 'light damage', no number"),
    A("No-weapon freeze", "typeless", 15, "Moons of Peril/Strategies#Blue Moon", minHit=3, prayerFactor=1.0, avoidable=True),
    A("Frost Storm tornado", "typeless", None, "Moons of Peril/Strategies#Blue Moon", prayerFactor=1.0, avoidable=True,
      unknown="unknown: storm damage not stated"),
], ["Curse cancels your next attack every 18 stacks (offence, not damage-taken).",
    "Rotation: 6 standard attacks then a special (alternating Freeze/Storm)."],
   ["unknown: shard and storm damage"])
boss("eclipse moon", "Eclipse Moon", "Regular", 6, moon_std() + [
    A("Searing Rays", "typeless", None, "Moons of Peril/Strategies#Eclipse Moon", prayerFactor=1.0, avoidable=True,
      unknown="unknown: 'minor damage', no number"),
    A("Clone attack (unparried)", "typeless", None, "Moons of Peril/Strategies#Eclipse Moon", hits=15,
      prayerFactor=1.0, avoidable=True, unknown="unknown: clone damage not stated; parry = 0"),
], ["Curse makes your attacks glance (max hit -12) - an offence effect driven by hits taken.",
    "Rotation: 6 standard attacks then a special (alternating Rays/Clones)."],
   ["unknown: rays and clone damage"])

# ---------------- Tormented Demon ----------------
TDS = "Tormented Demon/Strategies#Fight overview"
boss("tormented demon", "Tormented Demon", None, 6, [
    A("Melee auto", "melee", 31, TDS, speedTicks=6, accuracy="roll", prayerFactor=0.0, share=None,
      note="Infobox: 31 (auto). Uses one style at a time; switches only after the fire-bomb attack."),
    A("Magic auto", "magic", 31, TDS, speedTicks=6, accuracy="roll", prayerFactor=0.0,
      note="After a melee cycle the next style (magic/ranged) must be guessed - one wrong-prayer hit per switch is plausible."),
    A("Ranged auto", "ranged", 31, TDS, speedTicks=6, accuracy="roll", prayerFactor=0.0),
    A("Fire bombs", "typeless", 45, "Tormented Demon/Strategies#Fire bombs", minHit=40, hits=2, prayerFactor=1.0,
      avoidable=True, note="Every ~60 ticks (~10 attacks); 2 ticks to step off; 'can be unavoidable' near walls without ctrl-run."),
], ["Cycle = ~10 autos of ONE style then a bomb; each style's share depends on the cycle, so per-kill share is a mixture, not 1/3 each.",
    "First attack does ~75% less if prayed and shield down (opening-hit special case).",
    "Multi-demon fights change behaviour (one melee, one magic/ranged, third only bombs)."],
   ["unknown: style-selection probabilities (melee if reachable, else random magic/ranged)",
    "unknown: chance of a wrong-prayer hit on style switch"])

# ---------------- The Whisperer (post-quest) ----------------
WS = "The Whisperer/Strategies#Fight overview"
boss("the whisperer", "The Whisperer", "Post-quest", 6, [
    A("Ranged volley", "ranged", 40, WS, hits=3, accuracy="roll", prayerFactor=0.0,
      note="3 projectiles per attack; pattern R-R-R -> M-M-R/R-R-M after specials -> M-R-M late, so in mixed volleys ONE prayer cannot block all 3 unless flicked per projectile."),
    A("Magic volley", "magic", 36, WS, hits=3, accuracy="roll", prayerFactor=0.0,
      note="Bucket max hit list says 40 ranged / 36 magic for post-quest."),
    A("Melee double hit", "melee", 42, WS, hits=2, accuracy="always", prayerFactor=None, avoidable=True,
      note="Only if adjacent; 2nd hit mirrors the 1st; 'only partially reduced by prayer'. Awakened: 50-72 -> 37-52 with prayer (~0.72).",
      unknown="unknown: post-quest prayerFactor (awakened ratio ~0.72)"),
    A("Tentacles", "typeless", 20, WS, prayerFactor=1.0, avoidable=True, note="After every regular attack; -20% sanity. Dodged by L-move."),
    A("Corrupted Seeds failure", "typeless", 75, "The Whisperer/Strategies#Special attacks", prayerFactor=1.0, avoidable=True,
      note="Dark seed step: 15-25."),
    A("Screech (x3)", "typeless", 45, "The Whisperer/Strategies#Special attacks", hits=3, prayerFactor=1.0, avoidable=True),
    A("Soul Siphon failure", "typeless", 50, "The Whisperer/Strategies#Special attacks", prayerFactor=1.0, avoidable=True),
], ["Infobox attack speed 10 conflicts with volley cadence; enrage drops to 2 ticks per attack, alternating style every 2 attacks.",
    "Shadow Realm costs 3 prayer points per tick-group (prayer drain is a defensive cost the model doesn't track).",
    "Sanity is a second HP bar - tentacle/special hits also cost sanity."],
   ["unknown: true auto speed outside enrage", "unknown: melee prayer factor (normal mode)",
    "unknown: shares - specials at 80/55/30% HP, not per-attack"])

# ---------------- Vardorvis (post-quest) ----------------
VS = "Vardorvis/Strategies#Fight overview"
boss("vardorvis", "Vardorvis", "Post-quest", 5, [
    A("Melee auto", "slash", 43, VS, minHit=None, speedTicks=5, accuracy="roll", prayerFactor=0.25,
      note="Max 32 at full HP rising linearly to 43 at low HP (strength scales with missing HP). Protect from Melee -75% (8-10). Heals 50% of damage.",
      unknown="unknown: accuracy - 280 Att +190 bonus, 'very accurate regardless of armour'"),
    A("Swinging Axes", "typeless", 35, "Vardorvis/Strategies#Special attacks", prayerFactor=0.5, avoidable=True,
      dot={"type": "bleed", "total": 15, "perHit": 3, "everyTicks": 3, "durationTicks": 14},
      note="1-3 axes by HP; Protect from Melee ~halves. Bleed 15+ over 14 ticks."),
    A("Darting Spikes", "typeless", 25, "Vardorvis/Strategies#Special attacks", prayerFactor=1.0, avoidable=True,
      note="Heals boss 50%."),
    A("Strangle", "typeless", None, "Vardorvis/Strategies#Special attacks", prayerFactor=1.0, avoidable=True,
      unknown="unknown: 'high damage' (awakened text implies ~25 normal, ~40 awakened)"),
    A("Head gaze", "ranged", 0, "Vardorvis/Strategies#Special attacks", prayerFactor=0.0, avoidable=True,
      note="No damage; failing drains 10 prayer and disables overheads for 3 ticks (lets the body hit unprayed)."),
], ["Special attacks are RANDOM, not a rotation - shares are unknowable from the wiki.",
    "Enrage at 33% HP: more axes/heads."],
   ["unknown: special frequency", "unknown: strangle damage normal mode"])

# ---------------- Duke Sucellus (post-quest) ----------------
DS = "Duke Sucellus/Strategies#Battle phase"
boss("duke sucellus", "Duke Sucellus", "Post-quest (Awake)", 5, [
    A("Icicles (melee range)", "crush", 11, DS, speedTicks=5, accuracy="roll", prayerFactor=0.45,
      note="11 unprayed, 5 with Protect from Melee (stated as max values; ratio 0.45). Ward of Arceuus capped."),
    A("Slam", "crush", 56, DS, accuracy="always", prayerFactor=0.75, avoidable=True,
      note="42 through prayer; always hits; dodged by stepping off (standard play: attack 2 of 3 cycles)."),
    A("Magic spit (out of melee)", "magic", 48, DS, accuracy="roll", prayerFactor=None, avoidable=True,
      note="'Slightly less' with Protect from Magic; awakened 71 -> 36 through prayer (~0.5). Avoided by staying in melee.",
      unknown="unknown: post-quest prayerFactor"),
    A("Freezing Gaze", "typeless", 101, "Duke Sucellus/Strategies#Battle phase", prayerFactor=1.0, avoidable=True,
      note="Every 5th standard attack; hide behind a pillar."),
    A("Gas Flare", "typeless", 12, "Duke Sucellus/Strategies#Battle phase", prayerFactor=1.0, avoidable=True,
      note="Reduced by slayer helmet / gas mask (gear-dependent damage reduction)."),
], ["Gaze share = 1 in 5 attack slots (stated). Enrage <25%: speed 4.",
    "Prep phase (ice/extremities/vents) damage before the fight not modelled."],
   ["unknown: post-quest magic prayer factor", "unknown: whether competent players take icicles every cycle or avoid them"])

# ---------------- The Leviathan (post-quest) ----------------
LS = "The Leviathan/Strategies#Fight overview"
boss("the leviathan", "The Leviathan", "Post-quest", None, [
    A("Magic orb", "magic", 32, LS, accuracy="roll", prayerFactor=0.0, note="Volley speed 3 -> 2 -> 1 tick as volleys progress."),
    A("Ranged orb", "ranged", 24, LS, accuracy="roll", prayerFactor=0.0),
    A("Melee orb", "melee", 50, LS, accuracy="roll", prayerFactor=0.0, note="Only in volleys 3,5,6,7."),
    A("Bite", "melee", 50, LS, prayerFactor=0.0, avoidable=True, note="Only if adjacent to the pool; no warning."),
    A("Roar chip", "typeless", 7, LS, prayerFactor=1.0, avoidable=False,
      note="End of every volley - unavoidable chip (per volley, not per second)."),
    A("Falling rocks", "typeless", 25, LS, prayerFactor=1.0, avoidable=True),
    A("Smoke Blast", "typeless", 59, "The Leviathan/Strategies#Special attacks", prayerFactor=1.0, avoidable=True,
      note="Infobox says 60."),
    A("Lightning Barrage", "typeless", None, "The Leviathan/Strategies#Special attacks", prayerFactor=1.0, avoidable=True,
      unknown="unknown: damage not stated"),
], ["Volley sequence (stated): 3t mag/rng, 2t mag/rng, 2t all three, 1t mag/rng, 1t all three, faster projectiles x2; stuns reset progress.",
    "Enrage at 20%: 2-tick mag/rng; outside the pathfinder AoE attacks PIERCE prayer (position-gated prayerFactor)."],
   ["unknown: volley lengths (attacks per volley) -> shares",
    "unknown: pierce % when outside pathfinder", "unknown: lightning damage"])

# ---------------- Nex ----------------
NS = "Nex/Strategies#Fight overview"
boss("nex", "Nex", None, 4, [
    A("Melee (3-hit chain)", "crush", 30, NS, hits=3, accuracy="roll", prayerFactor=0.2,
      note="Up to 30 (infobox 27); follows up with two more melee hits if target stays in MD. Prayer -80% (-66% in Zaros phase -> 0.34)."),
    A("Magic (Ancients, AoE)", "magic", 33, NS, accuracy="roll", prayerFactor=0.5,
      note="Prayer -50%, -66% in blood phase (0.34). Zaros phase: 0.5 if prayer up before cast, 0.75 if after. Smoke phase poisons; blood heals Nex; ice freezes/drains prayer."),
    A("Shadow shots (shadow phase)", "ranged", 60, "Nex/Strategies#Shadow phase", accuracy="roll", prayerFactor=0.5,
      note="Damage scales with proximity; 30 max through prayer up close."),
    A("Smoke Dash", "typeless", 50, "Nex/Strategies#Smoke phase", prayerFactor=1.0, avoidable=True),
    A("Shadow Smash", "typeless", 50, "Nex/Strategies#Shadow phase", prayerFactor=1.0, avoidable=True),
    A("Blood Sacrifice", "magic", 50, "Nex/Strategies#Blood phase", prayerFactor=1.0, avoidable=True,
      note="'through Protect from Magic'; bystanders take up to 12."),
    A("Containment", "typeless", 60, "Nex/Strategies#Ice phase", prayerFactor=None, avoidable=True,
      note="Protect from Missiles halves it if frozen (prayer of a different 'style' reduces a typeless hit)."),
    A("Ice Prison", "typeless", 75, "Nex/Strategies#Ice phase", prayerFactor=1.0, avoidable=True),
    A("Wrath (on death)", "typeless", 50, "Nex/Strategies#Zaros phase", prayerFactor=1.0, avoidable=True),
    A("Smoke Rush poison", "magic", None, "Nex/Strategies#Smoke phase", dot={"type": "poison", "start": 8}, avoidable=False,
      note="Bucket: poisonous Yes (8)."),
], ["Team boss: incoming damage depends on targeting (nearest player, lowest defence roll) and team size - per-player share is not a property of the boss.",
    "Prayer factor varies BY PHASE for the same style (melee 0.2 -> 0.34, magic 0.5 -> 0.34 / 0.75)."],
   ["unknown: style shares per phase (melee-distancing changes them)", "unknown: Zaros-phase Turmoil max hit"])

# ---------------- Abyssal Sire ----------------
SS = "Abyssal Sire/Strategies#Standard attacks"
boss("abyssal sire", "Abyssal Sire", "Phase 2", 7, [
    A("Arm swipe", "melee", None, SS, accuracy="roll", prayerFactor=None, note="6 through Protect from Melee.",
      unknown="unknown: unprayed max ('moderately high')"),
    A("Single tendril flick", "melee", None, SS, accuracy="roll", prayerFactor=None, note="6 through Protect from Melee.",
      unknown="unknown: unprayed max"),
    A("Double tendril flick", "melee", 66, SS, accuracy="roll", prayerFactor=0.39, note="26 through Protect from Melee (26/66)."),
    A("Miasma pool", "typeless", 30, "Abyssal Sire/Strategies#Miasma Pools", minHit=10, prayerFactor=1.0, avoidable=True,
      dot={"type": "poison", "start": 8}, note="Per tick on centre tile, 2-8 on edges; poison protection does not help."),
    A("Spawn ranged", "ranged", 9, "Abyssal Sire/Strategies#Minions", prayerFactor=0.0, avoidable=False),
    A("Scion ranged", "ranged", 16, "Abyssal Sire/Strategies#Minions", prayerFactor=0.0, avoidable=False,
      note="Phase 3 has ~15 minions alive by design; pray Missiles."),
    A("Scion melee", "melee", 10, "Abyssal Sire/Strategies#Minions", prayerFactor=1.0, avoidable=False,
      note="Taken unprayed while praying Missiles (spawn melee: 4)."),
    A("Tentacles", "melee", 30, "Abyssal Sire/Strategies#Respiratory systems, tentacles, and stunning",
      prayerFactor=None, avoidable=True, note="'reduced slightly by Protect from Melee'; stun avoids."),
    A("Phase 3 explosion", "typeless", 96, "Abyssal Sire/Strategies#Stage II", prayerFactor=1.0, avoidable=True),
    A("Forced teleport blast", "typeless", 60, "Abyssal Sire/Strategies#Forced Teleport and Explosion", prayerFactor=1.0, avoidable=True),
], ["Phase-based: P1 vents (no Sire melee), P2 melee, P3 miasma + minions. Damage profile per phase differs completely.",
    "Minions are an independent damage source with their own counts; phase 3 'do not kill minions' strategy keeps 15 alive."],
   ["unknown: swipe/single-flick unprayed max", "unknown: style shares of the three melee attacks",
    "unknown: minion attack speed and count over time"])

# ---------------- Dagannoth Kings ----------------
DKS = "Dagannoth Kings/Strategies#The Kings"
boss("dagannoth rex", "Dagannoth Rex", None, 4, [
    A("Melee", "slash", 26, "Dagannoth Rex#Strategy", speedTicks=4, accuracy="roll", prayerFactor=0.0,
      note="Standard play safespots him -> 0 damage taken (strategy-dependent)."),
    A("Spinolyp ranged (moat)", "ranged", None, DKS, prayerFactor=0.0, dot={"type": "poison", "start": 6},
      note="Spinolyps attack constantly even while safespotting.", unknown="unknown: spinolyp max hit"),
    A("Spinolyp magic (moat)", "magic", None, DKS, note="Drains 1 prayer on hit.", unknown="unknown: spinolyp max hit"),
], ["Kill-order and safespots dominate; damage depends on which kings are aggro at once."], ["unknown: spinolyp numbers"])
boss("dagannoth prime", "Dagannoth Prime", None, 4, [
    A("Magic (3x3 AoE)", "magic", 50, "Dagannoth Prime#Strategy", speedTicks=4, accuracy="roll", prayerFactor=0.0),
], ["Infobox max 50; plugin had 26."], ["unknown: none for the boss itself"])
boss("dagannoth supreme", "Dagannoth Supreme", None, 4, [
    A("Ranged", "ranged", 30, "Dagannoth Supreme#Strategy", speedTicks=4, accuracy="roll", prayerFactor=0.0,
      dot=None, note="Bucket: poisonous 'Yes' (start not given)."),
], [], ["unknown: poison start damage"])

# ---------------- The Nightmare ----------------
NMS = "The Nightmare/Strategies#Standard attacks"
boss("the nightmare", "The Nightmare", None, 6, [
    A("Melee claw", "slash", 60, NMS, accuracy="always", prayerFactor=None, wrongPrayerFactor=1.2, avoidable=True,
      note="Infobox '50/60' (likely solo vs group). Always hits; avoided by not standing in front/under her.",
      unknown="unknown: prayed damage fraction ('less damage')"),
    A("Ranged", "ranged", 39, NMS, accuracy="roll", prayerFactor=None, wrongPrayerFactor=None,
      note="Infobox '33/39'.", unknown="unknown: prayed fraction; wrong-prayer 1.2x 'not confirmed' for ranged/magic"),
    A("Magic", "magic", 39, NMS, accuracy="roll", prayerFactor=None, wrongPrayerFactor=None,
      unknown="unknown: prayed fraction"),
    A("Grasping Claws", "typeless", 50, "The Nightmare/Strategies#Special attacks", prayerFactor=1.0, avoidable=True),
    A("Sleepwalker blast", "typeless", 70, "The Nightmare/Strategies#Special attacks", minHit=5, prayerFactor=1.0, avoidable=False,
      note="Min 5 even if all sleepwalkers die; scales with absorbed sleepwalkers. Phase transitions only."),
    A("Corpse flowers", "typeless", 20, "The Nightmare/Strategies#Special attacks", prayerFactor=1.0, avoidable=True, note="Per tick, heals boss 2x."),
    A("Parasite burst", "typeless", None, "The Nightmare/Strategies#Special attacks", prayerFactor=1.0, avoidable=False,
      note="Weakened by Sanfew/Relicym's - consumable-dependent.", unknown="unknown: 'massive damage', no number"),
    A("Surge", "typeless", 60, "The Nightmare/Strategies#Special attacks", prayerFactor=1.0, avoidable=True),
    A("Husk magic/ranged", "magic", None, "The Nightmare/Strategies#Special attacks", unknown="unknown: husk max hits"),
], ["Only target known to state a wrong-prayer multiplier (melee 1.2x).",
    "Phase 2 Curse shuffles prayers - 'correct prayer' becomes a UI problem, not a gear one.",
    "Target selection: highest defence roll of any type gets focused - better armour attracts the boss."],
   ["unknown: correct-prayer fraction for all three styles", "unknown: what 50/60 and 33/39 split on", "unknown: style shares"])

# ---------------- Corrupted Hunllef ----------------
CH = "Corrupted Hunllef#Protection against its attacks"
boss("corrupted hunllef", "Corrupted Hunllef", None, 5, [
    A("Ranged", "ranged", 68, CH, hits=1, share=0.5, accuracy="roll", prayerFactor=0.235,
      note="Prayed max 16 with no armour (16/68). With T1/T2/T3 corrupted armour: prayed 13/10/8, unprayed 55/45/35. 4 attacks per style, starts ranged."),
    A("Magic", "magic", 68, CH, share=0.5, accuracy="roll", prayerFactor=0.235,
      note="Some magic attacks disable prayers (next hit unprayed if not re-enabled)."),
    A("Stomp (under boss)", "typeless", 68, "Corrupted Hunllef#Attacks", prayerFactor=1.0, avoidable=True),
    A("Tornado", "typeless", 30, CH, minHit=15, prayerFactor=1.0, avoidable=True,
      note="Per tick on contact; 15-30 no armour, 7-15 T3. Counts as one of the 4 attacks."),
    A("Damaging floor", "typeless", 20, "Corrupted Hunllef#Damaging floor", minHit=10, prayerFactor=1.0, avoidable=True,
      note="Per tick on orange tiles (strategy page says 10-25)."),
], ["Gear inside the Gauntlet is fixed crystal/corrupted armour - the optimizer's bank gear is irrelevant; model by armour tier instead.",
    "share 0.5/0.5 is stated by the 4-and-switch rule (tornado/prayer-disable attacks consume slots)."],
   ["unknown: tornado and prayer-disable frequency"])

# ---------------- Phantom Muspah ----------------
PMS = "Phantom Muspah/Strategies#Ranged"
boss("phantom muspah", "Phantom Muspah", "Ranged / Melee / Shielded", 5, [
    A("Ranged form: ranged", "ranged", 61, PMS, speedTicks=6, accuracy="roll", prayerFactor=0.0,
      note="'more common'; calculated on attack start (prayer must be up before)."),
    A("Ranged form: magic orb", "magic", 72, PMS, speedTicks=6, accuracy="roll", prayerFactor=0.0,
      note="Late prayer still reduces 'a significant amount'; applies corruption (18 prayer over 18 s) even when prayed."),
    A("Melee form: stab", "stab", 34, "Phantom Muspah/Strategies#Melee", speedTicks=5, accuracy="roll", prayerFactor=0.0,
      avoidable=True, note="Damage increases the longer it stands still (chained hits); freeze/step-back avoids."),
    A("Shielded: ranged", "ranged", 72, "Phantom Muspah/Strategies#Shielded phase", speedTicks=5, prayerFactor=0.0,
      note="Shielded cycle: ranged on tick 1 and magic on tick 4 of every 5 - BOTH styles every cycle (flick)."),
    A("Shielded: magic", "magic", 79, "Phantom Muspah/Strategies#Shielded phase", speedTicks=5, prayerFactor=0.0),
    A("Lightning clouds", "typeless", 20, "Phantom Muspah/Strategies#Fight overview", prayerFactor=1.0, avoidable=True),
    A("Shockwave", "typeless", 80, "Phantom Muspah/Strategies#Shielded phase", prayerFactor=1.0, avoidable=True),
], ["Shielded phase heals Muspah 50% of damage dealt (Soul Split).",
    "Form switches are damage-threshold driven (100 ranged / 80 melee), so form share depends on PLAYER DPS."],
   ["unknown: ranged:magic ratio in ranged form ('occasionally')", "unknown: prayed fraction of ranged ('reduce' vs 'avoid')"])

# ---------------- Araxxor ----------------
AXS = "Araxxor/Strategies#Fight overview"
boss("araxxor", "Araxxor", "In combat", 6, [
    A("Melee", "crush", 38, AXS, speedTicks=6, accuracy="roll", prayerFactor=0.13,
      note="Protect from Melee caps it at 5 (stated as a max, ratio 5/38). Used when in melee range."),
    A("Ranged", "ranged", 34, AXS, speedTicks=6, accuracy="roll", prayerFactor=0.53,
      note="Prayer ~45% reduction, max 18. Drains 9% Defence on hit. Aranea boots block once per 8 ticks."),
    A("Magic", "magic", 21, AXS, speedTicks=6, accuracy="roll", prayerFactor=0.5,
      note="~50% reduction; drains 9% prayer on hit."),
    A("Enrage cleave", "melee", 38, "Araxxor/Strategies#Enrage phase", speedTicks=4, prayerFactor=0.34, avoidable=True,
      note="Below 255 HP; ~66% prayer reduction; dodgeable on 'Skree!'."),
    A("Acid pools", "typeless", None, AXS, minHit=4, prayerFactor=1.0, avoidable=True,
      dot={"type": "venom"}, note="Damage ramps from 4 the longer you stand on them."),
    A("Acid Ball", "typeless", 18, AXS, prayerFactor=1.0, avoidable=True, dot={"type": "venom"}),
    A("Acidic araxyte ranged", "ranged", 15, AXS, prayerFactor=None, avoidable=True,
      note="Some players ignore/tank them."),
    A("Ruptura explosion", "typeless", 70, AXS, prayerFactor=1.0, avoidable=True, note="-21 per tile distance."),
    A("Mirrorback recoil", "typeless", None, AXS, prayerFactor=1.0, avoidable=False,
      note="Player takes ~10% of the damage THEY deal to Araxxor while a mirrorback lives - damage taken scales with player DPS."),
], ["STYLE CHOICE IS GEAR-DRIVEN: out of melee he uses whichever of magic/ranged the player's defence roll is LOWER against.",
    "Eggs hatch every 6 standard attacks; one special type fixed per kill."],
   ["unknown: venom ramp values", "unknown: melee vs ranged share (player-controlled positioning)"])

# ---------------- The Hueycoatl ----------------
HS = "The Hueycoatl/Strategies#Fight overview"
boss("the hueycoatl", "The Hueycoatl", "Normal", None, [
    A("Rain Fire (melee projectile)", "melee", 14, HS, prayerFactor=0.14, note="Prayed: 0-2 (stated range)."),
    A("Rain Fire (ranged projectile)", "ranged", 14, HS, prayerFactor=0.14),
    A("Rain Fire (magic projectile)", "magic", 14, HS, prayerFactor=0.14),
    A("Glowing symbols lightning", "typeless", 16, HS, prayerFactor=1.0, avoidable=True),
    A("Tail shockwave", "typeless", 25, "The Hueycoatl/Strategies#Phase three (tail phase)", minHit=20, prayerFactor=1.0, avoidable=True),
], ["Teams of 3+ deliberately CAMP a fixed prayer for the 30% damage buff - correct-prayer assumption is wrong by design in groups.",
    "Infobox attack speed 0/-1 = not meaningful; attack frequency increases in phase 4."],
   ["unknown: attack speed", "unknown: style shares (random colours)"])

# ---------------- Royal Titans ----------------
def titan(elem):
    RT = "Royal Titans/Strategies#Attacks"
    return [
        A("Stomp", "crush", 26, RT, speedTicks=7, accuracy="roll", prayerFactor=0.0,
          note="Only in melee distance (phase 1); fully blocked by Protect from Melee. After the other titan dies: 10 prayer-piercing."),
        A(f"{elem} wall", "typeless", 12, RT, prayerFactor=1.0, avoidable=True),
        A(f"{elem} elemental melee", "melee", None, RT, prayerFactor=None, unknown="unknown: elemental max hit"),
        A(f"{elem} elemental self-destruct", "typeless", 10, RT, minHit=8, prayerFactor=1.0, avoidable=True),
        A("Elemental Blast (x3)", "typeless", 30, RT, hits=3, prayerFactor=1.0, avoidable=True, note="Phase 2; one safe tile."),
        A("Titan landing knockback", "typeless", 30, "Royal Titans/Strategies#Phase 2", minHit=15, prayerFactor=1.0, avoidable=True),
    ]
for k, e in (("branda the fire queen", "Fire"), ("eldric the ice king", "Ice")):
    boss(k, k.title(), None, 7, titan(e), ["Duo-designed; 'most damage taken is avoidable' (stated)."], ["unknown: elemental melee max"])

# ---------------- Amoxliatl ----------------
AMS = "Amoxliatl/Strategies#Fight overview"
boss("amoxliatl", "Amoxliatl", "Post-quest", 8, [
    A("Ice spikes (initial)", "magic", 22, AMS, speedTicks=8, accuracy="roll", prayerFactor=0.0,
      note="Protect from Magic blocks the initial hit."),
    A("Ice spikes (follow-up)", "typeless", None, AMS, prayerFactor=1.0, avoidable=None,
      unknown="unknown: typeless follow-up damage; unclear if dodgeable"),
    A("Icy pool", "typeless", 10, AMS, minHit=6, prayerFactor=1.0, avoidable=True, note="Per tick."),
    A("Icicle Crash", "magic", 34, AMS, prayerFactor=0.0, avoidable=None,
      note="Disables prayers first; re-enable Protect from Magic to negate (reaction-dependent)."),
], ["Special every 2-4 standard attacks, alternating Unstable Ice / Icicle Crash."],
   ["unknown: typeless spike damage"])

# ---------------- Yama ----------------
YS = "Yama/Strategies#Fight overview"
boss("yama", "Yama", "Normal (phases 1-2)", 8, [
    A("Melee axe", "slash", 46, YS, speedTicks=8, accuracy="roll", prayerFactor=0.13,
      note="Max 6 through Protect from Melee (6/46); hits everyone within 3 tiles. Phase 3: 22 through prayer (0.48)."),
    A("Magic snap", "magic", 46, YS, speedTicks=8, accuracy="roll", prayerFactor=0.0,
      note="Fully blocked in P1-2; P3: max 3 through prayer (flat cap, not a %)."),
    A("Ranged snap", "ranged", 46, YS, speedTicks=8, accuracy="roll", prayerFactor=0.0, note="P3: max 3 through prayer."),
    A("Glyph of Fire step", "typeless", 12, "Yama/Strategies#Phase 1", minHit=2, prayerFactor=1.0, avoidable=False,
      note="Self-inflicted cost of negating every fire special: 2/6/9/12 by distance to Yama."),
    A("Void flare explosion", "typeless", 44, "Yama/Strategies#Phase 1", minHit=42, prayerFactor=1.0, avoidable=True),
    A("Shadow wave", "typeless", 20, "Yama/Strategies#Phase 1", prayerFactor=1.0, avoidable=True,
      note="Also disables protection prayers briefly."),
    A("Meteor Strike (P2)", "typeless", 80, "Yama/Strategies#Phase 2", prayerFactor=1.0, avoidable=True,
      note="Instant death at centre; 80/40 at edges."),
    A("Shadow Stomp (P2)", "typeless", 60, "Yama/Strategies#Phase 2", prayerFactor=None, avoidable=None,
      unknown="unknown: whether glyph negates it (text implies glyphs negate P1-2 specials)"),
    A("Shadow Crash (P3)", "typeless", 20, "Yama/Strategies#Phase 3", hits=3, prayerFactor=1.0, avoidable=True),
    A("Judge fire spells", "typeless", 30, "Yama/Strategies#Judge of Yama", prayerFactor=1.0, avoidable=True),
    A("Judge cavern shake", "typeless", 7, "Yama/Strategies#Judge of Yama", minHit=5, hits=2, prayerFactor=1.0, avoidable=False),
    A("Poison", "magic", None, YS, dot={"type": "poison", "start": 20}, avoidable=None,
      unknown="unknown: which attack poisons"),
], ["Every 3 regular attacks -> special (stated); P3 alternates ranged/magic each attack at 7 ticks.",
    "Stance depends on the player's last attack style (melee chases, magic = static) - offensive choice changes incoming style mix.",
    "Prayer penetration expressed as FLAT caps (6, 3, 22), not percentages."],
   ["unknown: style share of autos in P1-2", "unknown: poison source"])

# ---------------- Doom of Mokhaiotl ----------------
DMS = "Doom of Mokhaiotl/Strategies#Mechanics"
boss("doom of mokhaiotl", "Doom of Mokhaiotl", "Delve 1 (scales to delve 8)", 6, [
    A("Standard projectile (magic)", "magic", 47, DMS, accuracy="roll", prayerFactor=0.0,
      note="47 at delve 1 rising to 65 at delve 8 (bucket per-delve: 47/50/50/?/53/55-?/56-57/58-65). Speed 6 -> 4 by delve 7. Calculated on impact (late prayer OK)."),
    A("Standard projectile (ranged)", "ranged", 47, DMS, accuracy="roll", prayerFactor=0.0),
    A("Standard projectile (melee)", "melee", 47, DMS, accuracy="roll", prayerFactor=0.0, note="Delve 2+ only."),
    A("Tongue whip (adjacent)", "melee", 40, DMS, accuracy="roll", prayerFactor=0.5, avoidable=None,
      note="Melee-punish requires standing adjacent at times."),
    A("Rock Throw debris", "typeless", 21, "Doom of Mokhaiotl/Strategies#Rock Throw", prayerFactor=1.0, avoidable=True,
      note="Also disables overheads; launches 1-3 extra mag/rng projectiles (2 sets at delve 8)."),
    A("Special Beam Cannon", "typeless", 60, "Doom of Mokhaiotl/Strategies#Special Beam Cannon", prayerFactor=1.0, avoidable=True,
      note="60 (d1-3), 80 (d4-5), 99 (d6+)."),
    A("Shockwave", "typeless", 42, "Doom of Mokhaiotl/Strategies#Shockwave", minHit=30, hits=None, prayerFactor=1.0, avoidable=True,
      note="1-5 slams by delve; earthen shield negates."),
    A("Acid blood", "typeless", 7, "Doom of Mokhaiotl/Strategies#Acid blood", prayerFactor=1.0, avoidable=True,
      dot={"type": "venom"}, note="Delve 3+; persists across delves."),
    A("Car trample", "typeless", 40, "Doom of Mokhaiotl/Strategies#Table of attacks by delve level", minHit=None,
      prayerFactor=1.0, avoidable=True, note="10/20/30/40 at delve 5/6/7/8."),
], ["Needs a per-delve dimension (max hit, speed, projectile count) - one row per boss is not enough.",
    "Projectile damage on impact -> prayer flicking per projectile; 'hits 4 times in 4 ticks' at delve 4+."],
   ["unknown: style shares", "unknown: larvae damage"])

# ---------------- Sarachnis ----------------
boss("sarachnis", "Sarachnis", None, 4, [
    A("Melee", "melee", 31, "Sarachnis#Fight overview", speedTicks=4, accuracy="roll", prayerFactor=0.0,
      note="Heals 5 per successful melee hit (even zeros)."),
    A("Ranged", "ranged", 31, "Sarachnis#Fight overview", speedTicks=4, accuracy="roll", prayerFactor=0.0,
      note="Heals 10 per successful hit. 'If done perfectly, players take no damage'."),
    A("Spawn magic", "magic", 11, "Sarachnis/Strategies#Mechanics", prayerFactor=None, note="Usually killed."),
    A("Spawn melee", "melee", 13, "Sarachnis/Strategies#Mechanics", prayerFactor=1.0, avoidable=False,
      note="Strategy: tank it while praying ranged."),
], ["Web every 4 attacks; aranea boots negate."], ["unknown: style share (positioning-driven)"])

# ---------------- Kalphite Queen ----------------
KQ = "Kalphite Queen#Fight overview"
boss("kalphite queen", "Kalphite Queen", "Crawling / Airborne", 4, [
    A("Magic (bounces)", "magic", 31, KQ, speedTicks=4, accuracy="always", prayerFactor=0.0),
    A("Ranged (spines)", "ranged", 31, KQ, speedTicks=4, accuracy="always", prayerFactor=0.0,
      note="Drains 1 prayer even when prayed. ALWAYS HITS - and you can only pray one of magic/ranged, so the other lands at 100% accuracy."),
    A("Melee", "stab", 31, KQ, speedTicks=4, accuracy="roll", prayerFactor=0.0,
      note="Standing in melee range trades mag/rng for a rolled melee hit (strategy: if >50% of melee misses)."),
], ["Damage-taken is dominated by the UNPRAYED always-hit style; armour only helps vs melee."],
   ["unknown: style shares", "unknown: chance of melee vs ranged/magic when adjacent"])

# ---------------- Thermonuclear smoke devil ----------------
boss("thermonuclear smoke devil", "Thermonuclear smoke devil", None, 2, [
    A("Magical ranged", "typeless", 8, "Thermonuclear smoke devil/Strategies#Attack styles", speedTicks=2, share=1.0,
      accuracy="roll", prayerFactor=1.0,
      note="Accuracy = its ranged attack vs player's MAGIC defence. 'very accurate'. Prayer does nothing."),
    A("Smoke (no face protection)", "typeless", None, "Thermonuclear smoke devil/Strategies#Smoke", prayerFactor=1.0,
      avoidable=True, chip=None, note="Requires facemask/slayer helm; gear slot constraint rather than damage.",
      unknown="unknown: smoke damage"),
], ["Ideal defensive test case: one attack, 2-tick, typeless, rolls vs magic defence - gear choice fully determines damage."], [])

# ---------------- Grotesque Guardians ----------------
GGS = "Grotesque Guardians/Strategies"
boss("dawn", "Dawn", None, 6, [
    A("Ranged", "ranged", 15, GGS + "#Phase 1", speedTicks=6, accuracy="roll", prayerFactor=0.0),
    A("Melee", "melee", 15, GGS + "#Phase 1", speedTicks=6, accuracy="roll", prayerFactor=0.0, note="Only when adjacent."),
    A("Stone Ball", "typeless", 15, GGS + "#Phase 1", prayerFactor=1.0, avoidable=True, note="Also a 10-tick freeze."),
    A("Rockfall (during Dusk phase)", "typeless", 22, GGS + "#Phase 2", prayerFactor=1.0, avoidable=True),
    A("Lightning vortices", "typeless", 10, GGS + "#Phase 3", minHit=5, prayerFactor=1.0, avoidable=True, note="Per tick."),
], ["Dawn + Dusk attack together in phase 1 (off-ticked by start position)."], ["unknown: Dawn/Dusk P1 prayer prayable?"])
boss("dusk", "Dusk", "Second form", 6, [
    A("Phase 2 melee", "slash", 15, "Dusk#Infobox", speedTicks=6, accuracy="roll", prayerFactor=0.0),
    A("Blinding explosion", "typeless", 33, GGS + "#Phase 2", prayerFactor=1.0, avoidable=True, note="Infobox 33 (special)."),
    A("Trample (under)", "typeless", 40, GGS + "#Phase 2", prayerFactor=1.0, avoidable=True),
    A("Phase 4 melee", "slash", 26, GGS + "#Phase 4", speedTicks=6, accuracy="roll", prayerFactor=0.0),
    A("Phase 4 ranged orb 1", "ranged", 9, "Dusk#Mechanics", minHit=9, accuracy="roll", prayerFactor=0.5,
      note="Fixed 60% of 15 if accuracy succeeds (no damage roll); prayer HALVES it (not blocks)."),
    A("Phase 4 ranged orb 2", "ranged", 15, "Dusk#Mechanics", accuracy="roll", prayerFactor=0.5,
      note="Double-roll: rand(0, rand(0,max)) - non-uniform distribution; prayer halves."),
    A("Prison of flames", "typeless", 65, "Dusk#Infobox", prayerFactor=1.0, avoidable=True, note="Heals Dusk."),
], ["Non-uniform damage distributions exist (fixed-value first orb, nested-roll second orb)."], [])

# ---------------- Scurrius ----------------
SCS = "Scurrius/Strategies#Fight overview"
boss("scurrius", "Scurrius", "Solo", 4, [
    A("Tail swipe", "melee", 13, SCS, speedTicks=4, accuracy="roll", prayerFactor=0.0, note="Phases 1-2 only; no warning."),
    A("Bolts of electricity", "magic", 8, SCS, accuracy="roll", prayerFactor=0.0, note="14 in group mode."),
    A("Flying fur", "ranged", 7, SCS, accuracy="roll", prayerFactor=0.0, note="13 in group mode."),
    A("Falling bricks", "typeless", 22, "Scurrius/Strategies#Special attacks", minHit=15, prayerFactor=1.0, avoidable=True),
    A("Giant rat", "melee", 4, "Scurrius/Strategies#Special attacks", prayerFactor=0.25, avoidable=False,
      note="0-1 through Protect from Melee."),
], ["Phase 3 (<30%) ranged+magic only at a faster rate."], ["unknown: phase-3 speed"])

out = sys.argv[1]
meta = {"generated": "2026-10-21", "source": "OSRS Wiki (Bucket infobox_monster + monster pages + /Strategies), hand-read",
        "verified": False,
        "conventions": {"prayerFactor": "fraction of damage that gets THROUGH the correct protection prayer (0 = fully blocked, 1 = prayer does nothing); null = wiki silent",
                        "minHit": "only where the wiki states one; null otherwise (game default is 0)",
                        "share": "only where the wiki states a rotation; null otherwise",
                        "avoidable": "assumes competent play; null = unclear",
                        "hits": "hits per attack"}}
json.dump({"_meta": meta, "bosses": B}, open(os.path.join(out, "draft_attacks.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)

# stats
cnt = collections.Counter(); n = 0
for k, b in B.items():
    for a in b["attacks"]:
        n += 1
        for f in FIELDS:
            if a.get(f) is not None:
                cnt[f] += 1
print("bosses", len(B), "attacks", n, "avg", round(n / len(B), 2))
for f in FIELDS:
    print(f"{f:18s} {cnt[f]:4d} {100*cnt[f]/n:5.1f}%")
# among non-avoidable/unknown-avoid attacks (the ones that matter for damage-taken)
core = [a for b in B.values() for a in b["attacks"] if a.get("avoidable") is not True]
c2 = collections.Counter()
for a in core:
    for f in FIELDS:
        if a.get(f) is not None: c2[f] += 1
print("core (not avoidable) attacks", len(core))
for f in ["maxHit", "share", "speedTicks", "accuracy", "prayerFactor", "minHit", "wrongPrayerFactor"]:
    print(f"  {f:18s} {100*c2[f]/len(core):5.1f}%")
unk = sum(len(b["unknowns"]) for b in B.values()) + sum(1 for b in B.values() for a in b["attacks"] if a.get("unknown"))
print("unknown notes", unk)
