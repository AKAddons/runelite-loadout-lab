# Loadout Lab — merciless player audit (2026-07-15)

Auditor stance: veteran OSRS player (maxed main / iron / UIM / 1-def pure / F2P alts), adversarial.
Evidence: full engine source read (`com.loadoutlab.engine`, `.data`, `.optimizer`), bundled data inspection,
headless probes (`./gradlew query`) against four synthetic persona profiles + the saved real profile,
the local weirdgloop official-calc clone (`~/Development/osrs-dps-calc`), the OSRS Wiki, and the
BiS Gear competitor clone (`~/Development/bis-gear-plugin`).
Synthetic profiles used in repros live at
`/private/tmp/claude-501/-Users-andrewkatz-Development-goalplanner-share-mcp/97fbc877-a14b-4548-a153-d6841dc0c27c/scratchpad/profiles/`
(`fresh.json` 40 atk/40 str/30 def/30 rng/25 mag; `mid_main.json` 75/75/75/80/82, KR only;
`late_main.json` maxed w/ fang-bowfa-shadow tier; `pure.json` 60 atk/90 str/1 def; `wildy.json`;
`f2p_mage.json`; `salve_*.json` minimal pairs).

---

## 1. Executive summary — the 10 findings that matter most

1. **[CRITICAL] Equipment wear-requirements data stops ~early-2022** — Osmumten's fang (needs 82 Atk), Torva, Masori, Tumeken's shadow, ZCB, Venator bow, Voidwaker, DT2 rings, Oathplate, Noxious halberd, keris partisans etc. carry NO requirement rows, so they are recommended to accounts that cannot legally wear them (proven: fang suggested to a 40-Attack fresh account; a 500m upgrade budget tells the same account to buy Oathplate + Masori + Virtus).
2. **[CRITICAL] Wilderness-weapon +50% buff fires by monster NAME, not location** — killing Hellhounds/Abyssal demons/Dust devils/Nechryaels/Ankou in Taverley or the Catacombs shows Ursine chainmace / Craw's bow picks with wilderness-buffed DPS (proven: Ursine 10.16 dps beats whip at generic "Hellhound").
3. **[CRITICAL-adjacent] Dragonfire constraint half-applies when no protective shield is owned** — vs Vorkath/green dragons the beam truncates at the shield slot: 2h weapons (tbow/shadow/bowfa/scythe) are banned AND the shown set still has an EMPTY shield (no protection anyway), losing defender/Ultor-class items in the process (proven: maxed profile at post-quest Vorkath gets Sanguinesti 2.71 dps on the magic card while the owned Shadow computes ~7.5; melee card loses Avernic+Ultor).
4. **[MAJOR] Salve amulet(ei) is mis-tiered for melee (16.7% instead of 20%) and salve imbues grant NO magic damage at all** — proven with A/B profiles vs Ankou; also makes the game-best ceiling change with what you own (owning a Salve (e) LOWERS your ranged game-best from 15.64 to 13.97).
5. **[MAJOR] Default monster version is alphabetical, and it's usually the wrong one** — "vorkath" → quest Vorkath (lvl 392), "vardorvis/whisperer/duke/leviathan" → Awakened, "zulrah" → Magma, "tzkal-zuk" → Enraged, Verzik → Entry mode. Cross-plugin name link-ins silently compute the wrong fight.
6. **[MAJOR] F2P mode assumes members boosts/prayers/spells** — F2P card vs Lesser demon recommends autocasting members-only Arceuus "Dark Demonbane" and labels assume Super combat / Ranging potion / Saturated heart; no spellbook-unlock gating exists anywhere (Ice Barrage suggested without DT1).
7. **[MAJOR] Piety/Chivalry assumed without their Defence requirements** — a 45-def zerker with King's Ransom is priced at Piety (needs 70 Def); Chivalry needs 65 Def. Pure/zerker numbers inflated.
8. **[MAJOR] Leaf-bladed battleaxe's +17.5% vs turoths/kurasks is missing** — the actual melee meta pick at kurask is understated and can lose to a whip-tier weapon.
9. **[MAJOR] Upgrade-budget can recommend buying weapons that literally cannot attack** — "Webweaver bow (Uncharged)" picked as a purchase (charged version is untradeable so only the dead uncharged twin enters the budget pool); budget also has zero value for ironmen (GE-only thinking).
10. **[MAJOR, gap] No prayer/boost override** — the DPS always assumes best offensive prayer + potion; a player camping Protect from Magic at Zulrah (i.e. everyone) can't see their real number. Competitor (BiS Gear) ships prayer/potion overrides incl. "no prayer" and raid boosts, plus pre-landed DWH/BGS stacks and Vulnerability — none of which Loadout Lab exposes.

---

## 2. Part A — Engine / functional defects

### A1. Requirements & eligibility

**A1.1 [CRITICAL] Wear requirements missing for ~4 years of content.**
`equipment_requirements.json.gz` (1,876 rows, curated, not regenerated) has no entries for: Osmumten's fang
(82 Atk), Torva (80 Def), Tumeken's shadow (85 Mag), Masori (80 Rng), Zaryte crossbow (80 Rng), Venator bow,
Voidwaker (75 Atk, wiki-verified), Lightbearer/DT2 rings, Soulreaper axe, Amulet of rancour, Noxious halberd,
Emberlight, Scorching bow (77 Rng, wiki-verified), Purging staff, Eye of ayak, Warped sceptre, keris partisans,
Ancient sceptres, accursed/webweaver/ursine, atlatl, macuahuitl, zombie axe, Oathplate, Confliction gauntlets,
Avernic treads, Araxyte slayer helmet, Virtus, and more. Missing rows ⇒ `GearRequirements.NONE` ⇒
`RequirementProfile.canEquip()` returns true (`RequirementProfile.java:42-64`, `DataService.java:184-189`).
`docs/ENGINE-GAPS.md` claims only "post-May items" are affected — the real cutoff is ~Nex-era Feb 2022.
- Repro 1: `./gradlew -q query -Pargs="general graardor --profile .../fresh.json"` → melee card:
  `WEAPON Osmumten's fang` on a 40-Attack account.
- Repro 2: `--budget 500000000` on the same profile → buy-list of Oathplate helm/chest/legs, Amulet of rancour,
  Noxious halberd, Masori mask (f)/body/chaps, Webweaver bow, Virtus set, Confliction gauntlets, Avernic treads,
  Elidinis' ward, Bellator/Venator/Magus rings — none equippable at 40/40/30/30/25.
- Personas: new/mid × all types (worst for budget users and anyone who acquired a high item early, e.g. a fang on a pure).

**A1.2 [MAJOR] No spellbook access gating.**
Spell candidates are filtered only by magic level (`LoadoutOptimizer.spellsForUnfiltered:705-721`); nothing checks
Desert Treasure for Ancients, Arceuus unlock for grasp/demonbane, or membership for the book. The manual
"spellbook lock" toggle is the only mitigation and defaults to Auto.
- Repro: `lesser demon --profile f2p_mage.json --f2p` → `spell Dark Demonbane` (members-only Arceuus spell) in
  F2P mode, on a Staff of fire.
- Personas: new/mid mains, F2P, any account without DT1/Arceuus.

**A1.3 [MAJOR] Piety/Chivalry lack their Defence gates.**
`PrayerBonuses.bestAvailable` (`PrayerBonuses.java:50-61`) keys Piety on prayer 70 + King's Ransom only.
In game Piety needs 70 Defence and Chivalry 65 Defence. A 45-def zerker with KR done gets Piety-priced numbers.
- Personas: pure/zerker/med-level (the exact users the tool should protect).

**A1.4 [MINOR] QuestUnlocks curation holes.** `QuestUnlocks.java` gates ~30 name families but misses e.g.
Ancient sceptre (DT1 line), Iban's staff wield (Underground Pass), Barronite mace (Below Ice Mountain).
Mostly shadowed by A1.1's bigger hole.

### A2. Gear-effect correctness (wrong numbers)

**A2.1 [MAJOR] Salve amulet(ei) melee tier wrong (16.7% vs 20%).**
`DpsCalculator.applyMeleeAccuracyBonuses:478-487` / `applyMeleeDamageBonuses:552-561` match
`"salve amulet (e)"` (with space) then fall back to `"salve amulet"`; the item is named `Salve amulet(ei)`
(no space) so (ei) lands in the 7/6 bucket. Wiki: (ei) is 20% to all three styles.
- Repro: `ankou --profile salve_ei.json` → 7.63 dps max 40; `--profile salve_e.json` → 8.03 dps max 42
  (same account, strictly-better item scores lower).

**A2.2 [MAJOR] Salve imbues grant no MAGIC damage.**
`applyMagicDamageBonuses:791-838` has avarice and slayer-helm branches but NO salve branch (accuracy path has
it). Wiki: salve(i) +15% / salve(ei) +20% magic damage.
- Repro: `ankou --profile salve_ei_magic.json` → 6.34 dps max 31 vs `salve_none_magic.json` → 6.31 dps max 31
  (max hit unchanged; only ~1% accuracy moved).
- Personas: everyone who mages undead (Vorkath, revs, shades, Cerberus ghosts...).

**A2.3 [MAJOR] Game-best ceiling changes with ownership via stat-tie dedupe.**
All salve variants stat-tie at zero, share one `StatKey` (`LoadoutOptimizer.java:1220-1269`), and
`betterEquivalent` prefers the OWNED variant (`:1271-1279`) — so ONE salve survives into the ALL_STANDARD
(game-best) pool. Owning a Salve (e) evicted the (ei): ranged game best fell from 15.64 → 13.97 dps in the
A2.1 repro. The ceiling should be ownership-independent; any conditional-effect family that stat-ties
(salves today; tomes/damned later) is exposed.

**A2.4 [MAJOR] Leaf-bladed battleaxe +17.5% vs turoths/kurasks missing.**
No rule anywhere in `DpsCalculator`/`MonsterMechanics` (wiki-verified passive, stacks with slayer helm). The
kurask/turoth melee card understates the actual meta weapon; the game-best melee number
(`kurask --profile mid_main.json --slayer` → 7.29) is wrong by up to 17.5%.
- Personas: mid/late slayers of every account type.

**A2.5 [MAJOR] Brimstone ring magic effect missing.**
Official calc: 25% of casts roll NPC defence at 90% (`PlayerVsNPCCalc.ts:1155-1162`). No "brimstone" reference
exists in the plugin source. Changes the mid-game magic ring answer (brimstone vs seers (i)/magus) at
high-magic-defence targets.

**A2.6 [MINOR] Twinflame staff double-cast missing.**
Wiki: fires two hits on elemental Bolt/Blast/Wave spells, second at 40% damage (~+28% effective). The staff
is in the corpus (id 30634) with flat stats only — a 2025 mid-game (60 Magic) weapon whose whole identity is
the passive. Not in ENGINE-GAPS' oddball list.

**A2.7 [MINOR] Special attacks roll the wrong defence style in some cases.**
`SpecialAttack.expectedDamage` reuses the NORMAL attack's defence roll (`SpecialAttack.java:211-215`).
Official calc overrides per weapon: godswords/claws/DDS/dragon halberd roll SLASH, Arclight/dragon sword STAB,
Voidwaker MAGIC, Dragon mace CRUSH (`PlayerVsNPCCalc.ts:148-168`). Vs a stab-weak monster the engine's DDS
spec is priced against stab defence; the game rolls slash.

**A2.8 [MINOR] Salamander ranged-defence class wrong.**
`rangedDefenceType` (`DpsCalculator.java:407-419`) returns "standard" for salamanders; official uses the
MIXED average of light/standard/heavy (`PlayerCombatStyle.ts:27-45`). Also melee `isWeaponFor(MELEE)`
(`GearItem.java:113-118`) excludes category "Staff", so Toktz-mej-tal can never be a melee candidate even
though `WeaponStyles` and `candidateScore` both model it (dead code path; obby-pure niche).

**A2.9 [MINOR] DWH failed-spec 5% drain (Aug 2023 rework) not modeled** — `drainedDefence`
(`SpecialAttack.java:409-420`) only models the on-hit 30%; the failed-spec consolation drain slightly raises
DWH's drain value on tanky bosses.

**A2.10 [MINOR] Zogres: comp ogre bow + brutal arrows should deal FULL damage.**
`MonsterMechanics.damageFactor:240-247` quarters everything except Crumble Undead. Niche but it's the entire
zogre strategy.

**A2.11 [MINOR] Efaritay's aid missing** — post-May-2024: +15% acc/+10% dmg for silver weapons at all vampyre
tiers and half damage (instead of none/half caps) for normal weapons vs T1/2. `VampyreRules` doesn't know the
ring; vyre-farmers' blisterwood numbers are understated.

**A2.12 [COSMETIC] Prayer assumptions below tier-3 are unlabeled.**
`PrayerBonuses.bestAvailable:62-92` only names parts at prayer ≥31, but still applies 1.05/1.10 multipliers
from level 4+ — the "assumes:" chip omits prayers that the number includes (seen on the fresh profile:
"assumes: Super combat" while 1.10/1.10 prayers are folded in).

### A3. Optimizer / candidate-pool defects (wrong picks)

**A3.1 [CRITICAL] Wilderness weapon buff fires on shared-name monsters everywhere.**
`revWeaponBuff` (`DpsCalculator.java:860-879`) keys on `WildernessMonsters` NAME membership
(`WildernessMonsters.java:23-85`, cached on `MonsterStats.java:91`). The curated list includes Catacombs/
Slayer-tower staples: abyssal demon, dust devil, greater nechryael, hellhound, bloodveld, ankou, jelly,
greater/black/lesser demon, green/black dragon, fire giant... The +50% applies wherever you actually fight
them, and the candidate-pool boost (+6000, `LoadoutOptimizer.candidateScore:953-957`) guarantees the weapon
surfaces.
- Repro: `hellhound --profile wildy.json` → melee `Ursine chainmace (Charged)` 10.16 dps (a whip set is ~7);
  ranged `Craw's bow` 10.32. Correct only inside the Wilderness; most hellhound/abby/nechryael tasks aren't.
- Official calc uses an explicit "in wilderness" user toggle (`BaseCalc.isRevWeaponBuffApplicable`). The
  competitor exposes the same toggle.

**A3.2 [CRITICAL] Dragonfire "shield required" half-applies when no protective shield is owned.**
`LoadoutOptimizer.optimize:449-455`: when the owned shield pool has no anti-dragon/DFS/ward, the slot loop
`break`s — but the partial states (built through HANDS; slot order `:32-43`) are then rescored and RETURNED
(`:526-540`). Net effect vs any Dragonfire monster with the antifire toggle off:
1) all 2h weapons excluded (`:426-429`) — tbow/bowfa/shadow/scythe vanish;
2) the shown set has an EMPTY shield anyway (no protection delivered);
3) slots after SHIELD (cape/feet/ring) are only refilled by `fillDpsNeutralSlots`, whose
   `utilityScore(item) <= wornUtility` filter (`:100-107`) rejects pure-damage items — Ultor ring (+12 str,
   0 defence) and Avernic defender can never come back; a Tyrannical (i) fills the ring instead.
- Repro: `vorkath post-quest --profile late_main.json` → magic card = Sanguinesti 2.71 dps (owned Shadow
  computes ~7.5 but is 2h); melee card = DHL with no shield, no defender, Tyrannical (i) ring, 7.59 dps.
  With `--antifire-potion` the real sets return.
- The real-game default at Vorkath IS super antifire + no shield; the panel's flip is a right-click on the
  shield cell (`LoadoutLabPanel.java:421-423`) that a new user will not find. Wrong-by-default on the most
  farmed boss in the game; strictly self-inconsistent (constraint costs dps but protects nothing).
- Personas: everyone; worst for mid accounts without a DFS.

**A3.3 [MAJOR] Default monster version = alphabetical first.**
`DataService.loadMonsters` sorts by name/version (`DataService.java:272`), `searchMonsters` returns exact
matches in corpus order (`LoadoutData.java:122-156`), headless and `selectExternal` name link-ins take
`hits.get(0)` (`LoadoutLabPanel.java:903-915`). Results:
"vorkath" → Dragon Slayer II (392); "vardorvis"/"the whisperer"/"duke sucellus"/"the leviathan" → Awakened;
"zulrah" → Magma; "tzkal-zuk" → Enraged; "verzik vitur" → Entry mode P1; "doom of mokhaiotl" → Deep Delve.
The in-panel dropdown lets a knowing user choose, but the Goal-Planner boss-card link-in and any name-based
integration silently gets the wrong fight.
- Repro: `./gradlew -q query -Pargs="vorkath --maxed"` → `=== vs Vorkath (Dragon Slayer II) - lvl 392 ===`.

**A3.4 [MAJOR] Upgrade budget suggests unusable uncharged wilderness weapons.**
Charged Craw's/Webweaver/Ursine/Accursed are untradeable (no GE price) so `affordable()` rejects them; the
tradeable Uncharged twins — which cannot attack at all until fed 1,000 ether — pass and get picked
(`LoadoutOptimizer.allowedByMode:1124-1156`; `badVersion` is only a tie-break, not a usability gate).
- Repro: `general graardor --profile fresh.json --budget 500000000` → `WEAPON Webweaver bow (Uncharged)`.

**A3.5 [MAJOR] Boost model ignores account reality.**
`BoostSelector.bestFor` (`BoostSelector.java:34-55`) always assumes Super combat (melee) and Ranging potion
(ranged) — members-only, herblore-gated consumables — even for F2P mode and level-3 accounts; only magic
hearts gate on ownership. F2P game-best labels show "Saturated heart" / "Deadeye" / "Ranging potion"
(repro: `lesser demon --profile f2p_mage.json --f2p`). F2P should use Strength/Ranging-less tiers; fresh
accounts should get tiered combat potions. Also no Divine variants, Forgotten brew, or Dragon battleaxe.
- Personas: F2P (all cards), new, low-herblore irons.

**A3.6 [MINOR] Low-risk mode can never recommend the Amulet of avarice rev setup.**
With avarice worn, kept slots drop to 0/1 (`PvpRisk.effectiveKeptSlots:84-96` — correctly modeled), so
avarice's own GE value busts the 75k default risk budget (`OptimizationRequest.DEFAULT_RISK_BUDGET_GP:269`)
and every avarice set is vetoed. The standard rev-farming loadout (avarice + all-untradeables, risk ≈ the
amulet) is unreachable without manually raising the cap; the card silently shows a fury at −20% dps.
Repro: `revenant dragon --profile wildy.json --low-risk 3` → fury on all three cards.

**A3.7 [MINOR] Golembane weapons get no candidate-pool boost** (`candidateScore` boosts salve/DHL/demonbane/
keris/wildy/avarice but not granite hammer/barronite mace vs golems), so on gear-rich accounts the +30%/+30%
granite hammer can be pruned by the WEAPON_LIMIT=24 rough-score cut before evaluation vs Grey/Flaming golems.

**A3.8 [MINOR] `spellsForUnfiltered` falls back to ALL spells when none are castable** (`:720`) — dead in
practice (Wind Strike is level 1) but a data regen that changes level parsing would silently un-gate spells.

### A4. Monster data / mechanics

**A4.1 [MAJOR] Multi-form bosses have no phase-weighted answer.** Zulrah's three forms, Vorkath's acid/fireball
cadence, Hydra's phases, Muspah's shield are separate rows or absent; the tool answers "vs one frozen stat
block". Players comparing to dps.osrs.wiki get per-form parity, but the tool's promise ("pick a monster →
what to bring") needs a rotation-weighted composite for at least Zulrah (fight-long expected dps per style
across forms). Tracked only obliquely (ENGINE-GAPS #13 "phase resistances"), so listed here.

**A4.2 [MINOR] `monsterStatKey` collapses spawns ignoring HP/attack levels/speed/styles**
(`DataService.java:289-301` — keys def/magic/off-magic/defensive/attributes/slayer/weakness/size only).
Same-name spawns that differ ONLY in those fields collapse to the highest-level row; offensive sheets and HP
(drain-value math, `OptimizerService.drainValue:733`) can silently belong to a different spawn than shown.

**A4.3 [MINOR] Wyvern icy breath needs no shield** — `DragonfireRules.breathesFire` only matches style
"Dragonfire"; Skeletal/Fossil-Island wyverns' breath (which in game requires DFS/ancient wyvern shield or
elemental shields) imposes no constraint and no incoming component. Inconsistent with the dragon treatment.

**A4.4 [MINOR] Incoming magic-defence formula folds the +9 oddly** — `magicEffective = magic*0.7 +
(defence+9)*0.3` (`IncomingDpsCalculator.java:151`); the game computes effective def (incl. +9 and stance)
and effective magic separately. Small systematic skew in every "Boss dps to you" number.

**A4.5 [COSMETIC] Zulrah (Magma) styles = ["Typeless"] in the sheet** — without the curated override the
incoming card would show nothing modeled; the override carries it. Fine today, fragile if the curated list
key ever misses a rename.

### A5. Claims vs behavior (docs)

**A5.1 [COSMETIC] ENGINE-GAPS #2 (silverlight/darklight acc+dmg bug) is already fixed in code**
(`DpsCalculator.java:572-577`, damage-only) but still listed as an open correctness bug.
**A5.2 [COSMETIC] ENGINE-GAPS data note "post-May items list no wear requirements"** dramatically understates
A1.1 (the hole starts ~Feb 2022).
**A5.3 [COSMETIC] README "engine verified against the official wiki DPS calculator"** — true for the harness
scenario battery; overbroad as a blanket claim while A2.x items exist.

---

## 3. Part B — Functionality gaps, ranked by player value

1. **Prayer/boost override, incl. "no offensive prayer"** — who: literally every bracket; at most bosses you
   camp a protection prayer and the shown DPS (Piety/Rigour-assumed) is a number you'll never do. Today:
   players re-check dps.osrs.wiki; competitor has full override incl. raid boosts. The enum groundwork
   (BoostProfile.OVERLOAD/SALTS) already exists unexposed. **Effort: S-M.**
2. **Fight-state inputs: pre-landed DWH/Elder maul/BGS/Arclight/Tonalztics/Seercull/ayak stacks, Vulnerability,
   accursed drain, soulreaper stacks** — who: late-game bossers (Corp, GWD, ToA prep), exactly the audience
   with gear worth optimizing. Competitor checklist item — they model ALL of these; Loadout Lab models a
   single prospective drain spec. **Effort: M** (monster stat mutation + a small panel section).
3. **"In the Wilderness?" toggle / location awareness** — fixes A3.1 and makes the risk UI coherent (also
   auto-detectable from the player's world position at query time — RuneLite knows). **Effort: S.**
4. **Ironman-aware acquisition paths** — who: iron/HCIM/UIM (a huge RuneLite demographic). The upgrade budget
   is GE-only, i.e. meaningless for irons; dream items are the only tool. Wanted: "next upgrade" ranked by
   marginal DPS with SOURCE (boss drop, quest, slayer unlock, shop) instead of price, and a "buyable from
   shops only" filter. Today: osrsbestinslot + spreadsheets. **Effort: M-L** (drop-source table; the
   quest-rewards pattern already proves the shape).
5. **Consumables & full trip planning (roadmap v0.3, not yet started)** — runes per hour for the suggested
   autocast, arrows/bolts count, scales/darts for the blowpipe, potion doses, food. The panel already knows
   the spell and ammo; players currently keep bank tag layouts by hand. **Effort: L.**
6. **Raid support** — ToA invocation level input (official calc scales NPC defence by (250+inv)/250 —
   `PlayerVsNPCCalc.ts:194-197`), CoX/ToB party scaling, per-room BiS. Today the tool can't answer "what do I
   bring to a 300 ToA" — its wardens are fixed stat rows. Tracked as a gap; ranked here for value. **Effort: L.**
7. **Time-to-kill / overkill-aware metrics** — DPS alone misleads on trash (a 4-hit kill vs 5-hit kill matters
   more than 5% dps); official calc surfaces TTK from the full hit distribution. Also enables "kills per trip"
   and better spec valuation. **Effort: M** (the engine already has accuracy/max; needs distribution math).
8. **Phase/rotation-weighted boss answers** (A4.1) — one "Zulrah" card weighting forms by rotation share,
   Vorkath with acid-phase dead time, Hydra per-phase. Today: players alt-tab to dps.osrs.wiki per phase.
   **Effort: M-L** (curated rotation shares; the boss_incoming pattern shows the way).
9. **Loadout export/share + saved loadouts** — share codes (the goalplanner-share-mcp pattern next door!),
   Inventory Setups / Bank Tag layout export, "save this loadout" (already on the roadmap backlog). Who: group
   irons, clan mentors, content creators. **Effort: M.**
10. **Slayer task planner** — pick a task list (or read the current task via slayer plugin), get ONE carry-set
    + swaps that covers the task's monsters, with the on-task toggle auto-set. Today: nobody does this well;
    natural extension of the per-style engine. **Effort: M.**
11. **Level/defence what-if ("plan mode")** — pures/zerkers/progression planning: "what does this card look
    like at 70 Attack / if I keep 1 Def". Levels come from the profile with no override; dream items exist but
    dream LEVELS don't. **Effort: S-M** (the headless harness already proves arbitrary-profile queries).
12. **GIM shared-storage tracking** — group storage isn't a tracked container (DWMS import may partially
    cover it); GIM players' best gear frequently lives there. **Effort: S** (another container id).
13. **Degradable cost-of-use** (tracked in gaps as charges; ranked for value) — scythe/blood-fury/blowpipe
    gp-per-hour next to their DPS edge: "is +0.4 dps worth 900k/hr to you?" is THE real question for mid
    accounts. **Effort: M.**
14. **"Why this pick" depth** — counted-bonus lines exist (good, competitor lacks them); missing: per-slot
    runner-up alternates with dps deltas (backlog) and "what would beat this" hints. **Effort: M.**
15. **Defence-tab presets / attack-style XP awareness** — pick controlled/accurate for xp goals; pures need
    "no Defence xp" styles only (a 1-def pure told to use "stab (controlled)" on a Spear gains Def xp —
    currently the engine happily picks controlled stances; a "block Defence xp" toggle is a pure's #1 ask).
    **Effort: S.** (Note: this is also a latent CORRECTNESS trap for pures — controlled/longrange picks are
    account-breaking advice for them, e.g. Zulrah polearm melee = controlled stab.)

---

## 4. Part C — Quick wins (high value ÷ low effort)

1. **Fix salve(ei)/(i) melee+magic tiers** — one string match + one damage branch (A2.1/A2.2); pins the
   game-best-vs-ownership anomaly (A2.3) for the salve family too.
2. **Backfill wear requirements for the ~50 meta items since 2022** (fang, shadow, tbow-era is fine, Torva,
   Masori, Virtus, ZCB, Venator bow, Voidwaker, DT2 rings, rancour, noxious, emberlight/scorching/purging,
   keris partisans, moons gear, Oathplate, treads, confliction) — a curated-file edit, no code (A1.1).
3. **Gate uncharged/inactive weapon versions out of candidate pools** — `badVersion()` already exists; use it
   as a filter for WEAPON slot candidates, not just a tie-break (A3.4).
4. **"In the Wilderness" checkbox** (default off outside wildy bosses, auto-on for revs/wildy-only bosses) —
   kills A3.1 with one request flag the engine already routes (`revWeaponBuff`).
5. **Default-version preference** — prefer "Post-quest" > unversioned > others, and de-prefer
   "Awakened"/"Enraged"/"Entry mode"/quest-tagged rows in `searchMonsters` exact-match ordering (A3.3).
6. **Leaf-bladed battleaxe 17.5%** — one conditional in the melee damage chain (A2.4).
7. **Dragonfire honesty** — when no protective shield is owned, either return the unconstrained set with a
   loud "needs super antifire" note or return nothing; never the half-constrained partial set (A3.2). Also
   surface the antifire flip as a visible toggle, not a shield-cell right-click.
8. **F2P boost/prayer labels** — F2P mode: strength/attack/defence potions tier, no hearts, no members prayers
   in game-best (A3.5).
9. **Label sub-31 prayer assumptions** (A2.12) — string-only change.
10. **Docs**: correct ENGINE-GAPS #2 (already fixed) and the "post-May" requirements claim (A5).

---

## 5. Appendix

### 5.1 Already-tracked items confirmed during this audit (not re-reported above)
- Bolt procs (ruby/diamond/dstone/onyx, ZCB proc, diary bonus) — ENGINE-GAPS #7. Observed consequence: DHCB
  card at Vorkath picks plain "Dragon bolts (Poison)" over ruby (e) — the actual meta answer is understated.
- Dharok's set / Ahrim's proc / amulet of the damned synergies — #6 and the 2026-07-08 note.
- Elite void magic +2.5% damage — #8.
- Tomes, smoke battlestaff, chaos gauntlets, sunfire runes — #9 (god-spell Charge is missing too; same family).
- Fang two-roll defence in ToA; ToA invocation / party scaling — #10 (official clone scales defence by
  (250+inv)/250; confirmed absent here).
- Keris 1/51 triple proc + 1.15 amascut factor question — #4.
- Wilderness weapons' spec kinds & deferred spec list (fang eviscerate, tonalztics, dinh's, macuahuitl,
  eldritch, webweaver, granite hammer, hasta, soulreaper, magic longbow) — ROADMAP deferred list. Also absent
  but unlisted there: Abyssal bludgeon "Penance" and Barrelchest anchor (minor).
- Oddballs #12: colossal blade, soulreaper stacks, macuahuitl/blood moon proc, atlatl scaling, burning-claws
  burn, venator ricochet.
- Monster `immunities` field vendored but unconsumed (stat-drain immunity vs DWH advice) — #13.
- Dusk-style curated immunity/finisher notes — #15 (MonsterNotes covers Dusk/gargoyle/rockslug/lizard/zygomite).
- TD residual EV rounding, scythe max-hit display, barronite accuracy dispute — harness-verified deltas list.
- Blood-moon-class charge/degradable economics — "degradables/charges" roadmap area.
- D-3 explicit defensive thresholds; skull awareness (keep 0/1) — defensive-arc follow-ups (avarice skull IS
  modeled; general skulled-state toggle isn't).

### 5.2 Things checked and found CORRECT (trust these)
- Zamorakian hasta at Corp correctly gets NO spear exemption — matches official calc and Jagex Ash ruling
  (only name-contains-"spear" + halberds + fang-stab + magic). Corp probe numbers coherent (tbow 1.60 halved,
  fang 7.57 full, shadow full).
- Ancient-warrior gear (Statius's warhammer, Vesta's) correctly absent — PvP-world-only in OSRS.
- Twisted bow polynomial + caps (140/250 base, 250/350 xerician cap) — matches wiki formula exactly; Zulrah
  >50 reroll modeled and harness-pinned.
- ACB spec cost 50% ✓, blowpipe spec 2x acc/1.5x dmg ✓, granite maul 60/50 ornate ✓, DDS/claws/AGS/BGS/DWH/
  elder maul/voidwaker/dark-bow/MSB numbers ✓ vs wiki; claws cascade EV formula standard.
- Araxxor task-locked list ✓ (wiki: spider/araxyte task only), Kraken/Cerberus/Thermy/Hydra/Sire/Dusk ✓.
- Ammo tier tables: shortbow can't fire steel arrows (probe proved the gate), broad bolts need Slayer 55 +
  Ranged 61 (present in requirements), amethyst broad bolts vs kurask picked correctly on-task, seeking
  arrows (July 2026) present. Scorching bow correctly fires dragon arrows (tier-60 list).
- Ballista = heavy ranged-defence class (category Crossbow) matching official; chins heavy; thrown light.
- Leafy gating (turoth/kurask): non-leaf melee/non-broad ammo/non-magic-dart correctly yield "no usable set";
  broad ammo + slayer helm interplay correct.
- Flying (Kree'arra): melee blocked except polearm/salamander incl. specs; probe confirmed "no usable set"
  for a scythe-owning maxed profile.
- Vampyre tier 3 hard gate + tier 2 halving; TD guaranteed-hit + 20%-reduction bypass (arclight pick + claws
  spec + 100% acc all coherent in probe).
- Slayer on-task monotonicity guarantee (off-task fold-in) — sound design, tested (SlayerInvariantTest).
- On-task gating requires `isSlayerMonster` + imbued head for ranged/magic ✓; bare black mask melee ✓.
- Crystal armour/bofa scaling incl. flooring order and inactive-piece exclusion (CrystalSetTest, official-
  verified); obsidian + berserker necklace additive-then-multiplicative order matches wiki; Inquisitor's
  post-rework per-piece/mace math matches wiki.
- Void (all styles incl. elite ranged 12.5%, magic 45% acc), avarice chain order, demonbane spell MoD
  assumption + purging staff doubling, elemental weakness severity, magic-defends-with-Defence NPC list,
  Corp/Kraken/Tekton/Ice-demon/Slagilith/zogre damage factors (except A2.10), autocast 5-tick/harmonised
  4-tick, powered-staff max-hit formulas incl. bone staff +10 and Zulrah cap.
- Ancients autocast staff gating (incl. harmonised exclusion, blue moon spear, purging staff) — better than
  upstream best-dps which allowed any staff.
- PvP risk math: kept-slot ranking, avarice skull → 0/1 kept, untradeable fee categories (489 curated rows),
  rev weapons priced at component value, "risk 0 gp (3 kept)" correct for ≤3 tradeables; friction/protect-only
  vetoes coherent in code and tests.
- Prayer unlock gating for Rigour/Augury/Deadeye/Vigour by varbit + level (probe: mid profile without scrolls
  correctly shows Eagle Eye, game best correctly Deadeye at 70 prayer).
- Incoming model: hellhound melee-only → 0.00 prayed ✓; Graardor/Zulrah/Kree overrides plausible and
  curated-note honest; ethereum bracelet zeroing revenant damage modeled both directions.
- Level-requirement gating WORKS where data exists (tbow denied at 30 ranged, fighter torso/rune platebody/
  black d'hide denied at 1 def, elite void denied at 42) — the failures in A1.1 are data holes, not logic.
- LoadoutLab implements several specs the official calc still marks UNIMPLEMENTED (dorgeshuun, bone dagger,
  ACB, dragon crossbow, granite maul, ursine) — ahead of the reference there.

### 5.3 Probe log (all repros)
```
./gradlew -q query -Pargs="zulrah --maxed"                                  # Magma default
./gradlew -q query -Pargs="vorkath --maxed"                                 # quest-version default
./gradlew -q query -Pargs="general graardor --profile fresh.json"          # fang @ 40 Attack
./gradlew -q query -Pargs="general graardor --profile fresh.json --budget 500000000"  # illegal buy-list
./gradlew -q query -Pargs="kurask --profile mid_main.json --slayer"        # leafy + broad + Eagle Eye
./gradlew -q query -Pargs="scurrius --profile pure.json"                   # pure gating + fang hole
./gradlew -q query -Pargs="lesser demon --profile f2p_mage.json --f2p"     # F2P Dark Demonbane + members boosts
./gradlew -q query -Pargs="ankou --profile salve_{ei,e,none}.json"         # salve melee tier + ceiling shift
./gradlew -q query -Pargs="ankou --profile salve_{ei_magic,none_magic}.json"  # salve magic dmg missing
./gradlew -q query -Pargs="hellhound --profile wildy.json"                 # wilderness buff false positive
./gradlew -q query -Pargs="vorkath post-quest --profile late_main.json"    # dragonfire half-constraint
./gradlew -q query -Pargs="green dragon --profile fresh.json [--antifire-potion]"
./gradlew -q query -Pargs="revenant dragon --profile wildy.json --low-risk 3"  # avarice never picked
./gradlew -q query -Pargs="kree'arra --profile late_main.json"             # flying melee gate
./gradlew -q query -Pargs="tormented demon --profile late_main.json"       # TD rules
./gradlew -q query -Pargs="zulrah serpentine --profile late_main.json --mode balanced"  # D-4 modes
```
