// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.google.gson.JsonObject;
import com.loadoutlab.data.*;
import java.util.*;
import com.loadoutlab.engine.WeaponStyles.MeleeStyle;
import static com.loadoutlab.data.GearSlot.*;
import static com.loadoutlab.data.JsonResources.*;
import static com.loadoutlab.engine.CombatStyle.*;
import static com.loadoutlab.engine.MonsterMechanics.*;
import static com.loadoutlab.engine.RollMath.*;
import static java.lang.Math.*;
import com.google.gson.JsonElement;

public class DpsCalculator {
    /** Conditional bonuses counted during the CURRENT calculate() call -
     * source -> exact parts ("+16.7% accuracy"), both insertion-ordered,
     * attached to the result for user assurance (single-threaded use,
     * matching how the optimizer drives this class). Melee tries several
     * stance variants per calculate; the part set dedupes the re-adds. */
    final LinkedHashMap<String, LinkedHashSet<String>> counted = new LinkedHashMap<>();
    /** The beam turns collection off: only results that can reach the panel
     * (rescore, fill, spec) need the bonus list, and the map churn +
     * per-result copy is pure waste across beam trials. */
    boolean collectCounted = true;
    /** The sunfire floor of the CURRENT magic calculation: a tenth of the
     * pre-tome max hit of a standard fire spell cast with sunfire runes
     * (official calc MIN_HIT_SUNFIRE, before the tome step), else 0. */
    int sunfireMinHit;
    /** applyRules' lists cut to the rules whose monster gate holds (lever
     * 5): the gate reads only the monster, so each list is cut once per
     * monster met, in file order (the group stop and products unchanged). */
    MonsterStats rulesMob;
    final Map<List<Rule>, List<Rule>> cutRules = new IdentityHashMap<>();

    /** Toggle counted-bonus collection (optimizer beam: off). */
    void setCollectCounted(boolean collect) { collectCounted = collect; }

    /** Record one exact bonus ("+16.7% accuracy") under its source. */
    void counted(String source, String part) {
        if (collectCounted) counted.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(part);
    }

    /** The assurance lines: "source: +X% accuracy, +Y% damage" per source. */
    List<String> countedLines() {
        List<String> lines = new ArrayList<>(counted.size());
        for (var entry : counted.entrySet()) lines.add(entry.getKey() + ": " + String.join(", ", entry.getValue()));
        return lines;
    }

    /** Powered staves (powered_staves.json): an offset on magic/3 or a flat base. */
    static final Map<String, Integer> STAFF_OFFSET = new LinkedHashMap<>();
    static final Map<String, Integer> STAFF_FIXED = new LinkedHashMap<>();

    static {
        JsonObject root = object("powered_staves.json");
        stringIntMap(root, "offset", STAFF_OFFSET);
        stringIntMap(root, "fixed", STAFF_FIXED);
    }

    public DpsResult calculate(OptimizationRequest request, Loadout loadout) {
        counted.clear();
        if (!weaponUsable(request.monster, request.style, loadout.getWeapon())
            || isImmune(request.monster, request.style, loadout, request.spell)) {
            return null;
        }
        DpsResult result;
        switch (request.style) {
            case RANGED:
                result = calculateRanged(request, loadout);
                break;
            case MAGIC:
                result = calculateMagic(request, loadout);
                break;
            case MELEE:
            default:
                result = calculateMelee(request, loadout);
        }
        if (result == null) return null;
        result = VampyreRules.apply(request.monster, request.style, loadout, result); // ISS-01M5RM2A7FFR9XWKATPX877TDD
        double factor = TormentedDemonRules.damageFactor(
                request.monster, request.style, loadout.getWeapon(), result.getSpellName())
            * MonsterMechanics.damageFactor(request.monster, request.style,
                loadout, result.attackType, request.spell);
        // Zulrah rerolls any hit above 50 down to 45-50 (Jagex-confirmed);
        // scale the expectation accordingly and cap the displayed max.
        if (result.maxHit > 50 && "zulrah".equals(request.monster.nameLower)) {
            int max = result.maxHit;
            double cm = 0;
            for (int d = 0; d <= max; d++) cm += d <= 50 ? d : 47.5;
            cm /= (max + 1);
            double cf = cm / (max / 2.0);
            result = result.withHitModel(result.dps * cf, result.accuracy, result.expectedHit * cf, 50);
        }
        // The Fragment of Seren: a hit lands as min(hit, 22..24) (reference
        // linearMinTransformer(2, 22); ISS-01M5RVDTMR5XG16HM6G2JTF7FF).
        if (result.maxHit > 22 && "fragment of seren".equals(request.monster.nameLower)) {
            int max = result.maxHit;
            double capped = 0;
            for (int d = 0; d <= max; d++) capped += (min(d, 22) + min(d, 23) + min(d, 24)) / 3.0;
            double f = capped / (max + 1) / (max / 2.0);
            result = result.withHitModel(result.dps * f, result.accuracy, result.expectedHit * f, min(max, 24));
        }
        // Tormented demons: guaranteed hits in the official default phase -
        // scale the expectation up to accuracy 1 (all hit models here are
        // linear in accuracy).
        double ao = TormentedDemonRules.applies(request.monster)
            && result.accuracy > 0 ? 1.0 / result.accuracy : 1.0;
        if (factor < 1.0 || ao != 1.0) {
            result = result.withHitModel(result.dps * factor * ao,
                min(1.0, result.accuracy * ao), result.expectedHit * factor * ao,
                (int) (result.maxHit * factor));
        }
        // The Sire's vents, after every other factor so the ranking sees
        // it: a demonbane hit destroys the vent outright (expected damage
        // per attack = accuracy x its full hp), and every other landed hit
        // deals at least half its max (a 1.5x on the plain expectation -
        // every weapon that can reach the vents rolls plainly, so the
        // scale is exact). Ordinary dps math ranked a blowpipe above the
        // Scorching bow (field report 2026-08-05).
        if (isRespiratorySystem(request.monster)) {
            // Melee pays the walk between the four spread vents; ranged and
            // magic hit every vent from one spot. With one-shots a kill IS
            // one attack, so the walk lands directly on the interval - the
            // per-hit numbers (max, expected) stay honest, only time does
            // the separating. This is what puts the Scorching bow ahead of
            // a melee demonbane of near-identical per-attack dps.
            int reach = request.style == MELEE ? meleeReachPenaltyTicks(request.monster) : 0;
            if (reach > 0 && result.dps > 0) {
                counted("vent spacing", "melee walks between the four vents" + " (+" + reach + " ticks per kill)");
            }
            double interval = (result.attackSpeed + reach) * SECONDS_PER_TICK;
            if (isDemonbane(loadout.getWeapon())) {
                int hp = max(1, request.monster.hitpoints);
                counted("demonbane one-shot", "any landed hit destroys the vent");
                result = result.withHitModel(result.accuracy * hp / interval, result.accuracy, result.accuracy * hp, hp);
            } else if (result.dps > 0) {
                counted("vent min-hit", "every landed hit deals at least half its max");
                result = result.withHitModel(result.expectedHit * 1.5 / interval,
                    result.accuracy, result.expectedHit * 1.5, result.maxHit);
            }
        }
        // The nibbler trio: three spawn per wave and one barrage cast hits
        // all of them, so an AoE cast's effective output is 3x the
        // single-target number. EVERY barrage is a 3x3 AoE, so a kit
        // re-show of, say, Smoke Barrage earns the trio too - the Ice/Blood
        // lock in spellsFor is pool curation, deliberately narrower than
        // this mechanic. This is what makes the barrage WIN the nibbler row
        // on real math instead of a blowpipe headlining the mob nobody
        // blowpipes (field decision 2026-08-06).
        if (request.style == MAGIC && isNibbler(request.monster)
            && result.getSpellName() != null && result.getSpellName().endsWith("Barrage")) {
            counted("nibbler trio", "one barrage cast hits all three");
            result = result.withHitModel(result.dps * 3, result.accuracy, result.expectedHit * 3, result.maxHit);
        }
        // Salarin the twisted: a landed Strike always deals a FLAT 9-12,
        // set by the highest strike tier unlocked - never by gear ("not
        // affected by items that would normally increase spell damage").
        // The isImmune gate has already filtered everything but Strike
        // casts, so what remains is rebuilt as guaranteed flat damage.
        if (isSalarin(request.monster)) {
            int magic = request.getLevels().magic;
            int flat = magic >= 13 ? 12 : magic >= 9 ? 11 : magic >= 5 ? 10 : 9;
            // The one item Salarin honours (wiki): the elemental amulet's +2.
            if (wearing(loadout, "elemental amulet")) {
                flat += 2;
                counted("elemental amulet", "+2 max hit - the one item Salarin honours");
            }
            counted("salarin", "strikes deal a flat " + flat + " - other gear does not matter");
            result = result.withHitModel( flat / (result.attackSpeed * SECONDS_PER_TICK), 1.0, flat, flat);
        }
        // The Wardens' ejected core (ToA P2, wiki): melee "will always
        // deal their max hit" - no accuracy roll, no damage roll. That
        // certainty is why the core is THE spec-dump phase; SpecialAttack
        // mirrors the same rule so multi-hit specs price honestly.
        if (request.style == MELEE && isWardenCore(request.monster) && result.maxHit > 0) {
            counted("warden core", "melee always deals its max hit");
            result = result.withHitModel( result.maxHit / (result.attackSpeed * SECONDS_PER_TICK),
                1.0, result.maxHit, result.maxHit);
        }
        // A prayer-based melee immunity (KQ airborne): everything is
        // blocked EXCEPT Verac's proc - a quarter of attacks land,
        // guaranteed, +1 damage. Without the full set nothing lands, but
        // the result stays a zero rather than a null so the beam can keep
        // partial Verac states alive long enough to complete the set.
        if (request.style == MELEE && request.monster.hasAttribute("immune_melee")
            && request.monster.hasAttribute("prayer_immunity")) {
            if (fullVeracSet(loadout)) {
                counted("verac's set", "only the 25% proc lands through the prayer");
                double exp = 0.25 * normalExpectedHit(1.0, result.maxHit + 1);
                result = result.withHitModel( exp / (result.attackSpeed * SECONDS_PER_TICK), 0.25, exp, result.maxHit + 1);
            } else {
                result = result.withHitModel(0, 0, 0, result.maxHit);
            }
        }
        if (!counted.isEmpty()) result = result.withCountedBonuses(countedLines());
        return healed(request, loadout, result);
    }

    /** The monster last asked whether it heals, and the heal model per
     * request (the beam alternates a few spell requests per set, so each
     * keeps its own; ISS-01M6415DTE84ZZ45C4YD3MZRBJ). */
    private MonsterStats healMob;
    private boolean heals;
    private final Map<OptimizationRequest, IncomingDpsCalculator.Prepared> heal = new IdentityHashMap<>();

    /** The result with the boss's heal off this set taken off its net dps
     * (the dps itself stays what the set deals); the result as it is where
     * nothing heals. */
    DpsResult healed(OptimizationRequest request, Loadout loadout, DpsResult result) {
        if (request.monster != healMob) {
            healMob = request.monster;
            heals = BossIncomingOverrides.heals(healMob);
        }
        if (!heals) return result;
        if (heal.size() > 64) heal.clear();
        IncomingDpsCalculator.Prepared model = heal.computeIfAbsent(request, IncomingDpsCalculator.Prepared::of);
        return result.withNet(result.dps - model.healRate(loadout));
    }

    DpsResult calculateMelee(OptimizationRequest request, Loadout loadout) {
        DpsResult best = null;
        // Only the styles this weapon actually has: a whip cannot go
        // aggressive, a mace cannot slash (see WeaponStyles).
        String only = request.setRules == null ? null : request.setRules.attackType;
        for (MeleeStyle style : WeaponStyles.melee(loadout.getWeapon())) {
            if (only != null && !only.equals(style.attackType)) continue; // a task's one attack type
            best = DpsResult.better(best, meleeVariant(request, loadout, style.attackType, style.attackStance, style.strengthStance));
        }
        return best;
    }

    /** All four Verac pieces worn - the set effect's gate. The flail can
     * only sit in the weapon slot, so that one cached-field check runs
     * first and skips the three armour scans for the ~all loadouts not
     * holding it - this gate sits inside every melee stance variant (the
     * blood moon set check's precedent: its weapon leads the list). */
    static boolean fullVeracSet(Loadout loadout) {
        GearItem weapon = loadout.getWeapon();
        return weapon != null && weapon.nameLower.startsWith("verac's flail") && wearsAll(loadout, VERAC);
    }

    DpsResult meleeVariant(OptimizationRequest request, Loadout loadout, String attackType, int attackStance, int strengthStance) {
        PlayerLevels levels = request.getLevels();
        PrayerBonuses prayers = request.prayers;
        int effectiveAttack = effectiveLevel(levels.attack, prayers.meleeAccuracy, attackStance);
        int effectiveStrength = effectiveLevel(levels.strength, prayers.meleeStrength, strengthStance);
        if (wearing(loadout, "soulreaper axe")) {
            // Soul stacks (wiki): +6% of the boosted strength level per
            // stack, added flat AFTER the prayer factor ("does not stack
            // multiplicatively with prayers"), strength only. Stacks build
            // per swing (even misses) and persist between kills with a 30s
            // idle decay, so sustained grinding sits at the 5-stack cap.
            counted("soul stacks", "+30% strength level at 5 stacks (sustained)");
            effectiveStrength += levels.strength * 30 / 100;
        }

        // The keris partisan of Amascut is +108 stab / +67 strength only
        // INSIDE the Tombs; outside it is the base partisan's +58 / +45
        // (wiki; official calc; Kendyy and Micah, Discord 2026-09-29: the
        // "black keris" won cards it had no business on).
        boolean dulled = wearing(loadout, "keris partisan of amascut") && !inToa(request);
        long attackRoll = attackRoll(effectiveAttack,
            loadout.offensive.getAttackBonus(attackType) - (dulled && attackType.equals("stab") ? 50 : 0));
        long baseAttackRoll = attackRoll;
        int mh = maxHitFromEffective(effectiveStrength, loadout.bonuses.strength - (dulled ? 22 : 0));
        int bmh = mh;
        attackRoll = applyMelee(request, loadout, attackRoll, baseAttackRoll, attackType, false);
        mh = (int) applyMelee(request, loadout, mh, bmh, attackType, true);
        mh += RatBoneRules.flatMaxHitBonus(request.monster, loadout.getWeapon());

        long defenceRoll = npcDefenceRoll(request.monster, attackType, loadout.getWeapon());
        // The fang rerolls a miss against a reduced defence roll - except
        // inside the Tombs, where it simply rolls twice (wiki; official calc
        // PLAYER_ACCURACY_FANG_TOA; TRIPLEKILLTRENT 2026-10-18: the outside
        // formula ran the fang 17-24% under the calc at invocation 300).
        double normal = normalAccuracy(attackRoll, defenceRoll);
        boolean fang = wearing(loadout, "osmumten's fang") && "stab".equals(attackType);
        double accuracy = !fang ? normal : inToa(request) ? 1 - (1 - normal) * (1 - normal) : fangAccuracy(attackRoll, defenceRoll);
        int minHit = 0;
        if (fang) {
            minHit = (int) floor(mh * 3.0 / 20.0);
            mh -= minHit;
        }
        // Flat armour is PER HITSPLAT (official calc: a hit-distribution
        // transformer, accurate hits only) - so multi-hit weapons take it
        // on EVERY hitsplat. Blue Moon's -5 armour is the payoff case
        // (wiki: "weapons which deal damage via multiple hitsplats are
        // particularly effective here"; field report 2026-08-10): the
        // dual macuahuitl gets +5 twice, and the old split-after-armour
        // order credited it only once.
        // The expectation takes RAW bounds and clamps each roll itself;
        // maxHit keeps an armoured copy because the shown max hit (and
        // the Verac branch below) still reads it. minHit is only ever
        // raw - nothing downstream reads an armoured floor.
        int rm = mh;
        int fa = request.monster.defensive.getFlatArmour();
        mh = applyFlatArmour(request, mh);
        double expected = expectedHitWithFlatArmour(accuracy, minHit, rm, fa);
        boolean chained = wearing(loadout, "dual macuahuitl");
        String melee = name(loadout.getWeapon());
        if (chained || Set.of("sulphur blades", "glacial temotli", "earthbound tecpatl", "torag's hammers").contains(melee)) {
            // Two hits on the halved max, armour on each hitsplat's own
            // bounds. The macuahuitl CHAINS them (official calc model): the
            // second only rolls when the first lands. The twin weapons and
            // Torag's hammers roll both independently (wiki Multi-hit
            // weapons; N, Discord 2026-09-26).
            int fr = rm / 2;
            int secondRaw = rm - fr;
            expected = expectedHitWithFlatArmour(accuracy, 0, fr, fa) + (chained ? accuracy : 1)
                    * RollMath.expectedHitWithFlatArmour(accuracy, 0, secondRaw, fa);
        }
        if (wearing(loadout, "scythe of vitur")) {
            if (request.monster.size >= 2) {
                expected += expectedHitWithFlatArmour(accuracy, 0, rm / 2, fa);
            }
            if (request.monster.size >= 3) {
                expected += expectedHitWithFlatArmour(accuracy, 0, rm / 4, fa);
            }
        }
        // Keris partisan of the sun (official calc): inside the Tombs it rolls
        // accuracy x5/4 below a quarter of the target's hitpoints - the last
        // quarter of the kill goes at the boosted rate (ISS-01M5WV8S2V5HEVEZN4716KV5YD).
        if (expected > 0 && inToa(request) && wearing(loadout, "keris partisan of the sun")) {
            counted("keris partisan of the sun", "+25% accuracy below 25% hitpoints");
            double boosted = expectedHitWithFlatArmour(normalAccuracy(attackRoll * 5 / 4, defenceRoll), minHit, rm, fa);
            expected = 1 / (0.75 / expected + 0.25 / boosted);
        }
        // Every keris (official calc): one hit in 51 deals triple damage to a kalphite.
        if (wearing(loadout, "keris") && request.monster.hasAttribute("kalphite")) {
            counted("keris vs kalphites", "1/51 triple hit");
            expected = expected * 53 / 51;
        }
        // Verac's set (wiki): 25% of attacks are a guaranteed hit with +1
        // damage - a quarter of the expectation swaps its accuracy for 1.0.
        // The prayer-immune override in calculate() replaces this wholesale
        // when only the proc can land at all.
        if (fullVeracSet(loadout)) {
            counted("verac's set", "25% guaranteed hit, +1 damage");
            expected = 0.75 * expected + 0.25 * normalExpectedHit(1.0, mh + 1);
        }
        int speed = attackSpeed(loadout, MELEE);
        String option = "";
        if (melee.contains("obligator")) {
            // Lunge (5t): best of 2-4 damage rolls by crush's rank among the
            // melee defences, +20/40% by size. Smash (3t): one plain roll.
            MonsterDefences d = request.monster.defensive;
            int crush = d.get("crush");
            int size = request.monster.size;
            int lm = rm * (size >= 3 ? 7 : size == 2 ? 6 : 5) / 5;
            double lunge = bestOfExpected(accuracy, lm,
                2 + (d.get("stab") > crush ? 1 : 0) + (d.get("slash") > crush ? 1 : 0), fa);
            boolean smash = expected / 3 >= lunge / 5;
            speed = smash ? 3 : 5;
            option = smash ? ", smash" : ", lunge";
            if (!smash) {
                expected = lunge;
                mh = applyFlatArmour(request, lm);
            }
        }
        double es = speed;
        // Bloodrager: all three blood moon pieces + the dual macuahuitl. The
        // wiki confirms the effect activates even on broken pieces, and every
        // New/Used/Broken variant shares the clean item name.
        if (wearsAll(loadout, BLOOD_MOON)) {
            // Bloodrager (wiki): each successful macuahuitl hit has a 1/3
            // chance to make the NEXT attack come one tick earlier; with
            // the chained hits that is acc/3 + acc^2 * 2/9 per attack
            // (matches the official calc's expected attack speed).
            double proc = accuracy / 3.0 + accuracy * accuracy * 2.0 / 9.0;
            counted("bloodrager set", String.format("%.2f ticks faster on average", proc));
            es = speed - proc;
        }
        String stance = attackStance == 3 ? "accurate" : strengthStance == 3 ? "aggressive" : "controlled";
        return new DpsResult(loadout, expected / (es * SECONDS_PER_TICK), accuracy, expected, mh, speed, attackType + " (" + stance + option + ")", attackRoll, defenceRoll);
    }

    DpsResult calculateRanged(OptimizationRequest request, Loadout loadout) {
        return DpsResult.better(rangedVariant(request, loadout, 0, true), rangedVariant(request, loadout, 3, false));
    }

    DpsResult rangedVariant(OptimizationRequest request, Loadout loadout, int sb, boolean rapid) {
        PlayerLevels levels = request.getLevels();
        PrayerBonuses prayers = request.prayers;
        int ea = effectiveLevel(levels.ranged, prayers.rangedAccuracy, sb);
        int ed = effectiveLevel(levels.ranged, prayers.rangedStrength, sb);

        if (isWearingRangedVoid(loadout)) {
            boolean elite = wearsAll(loadout, ELITE_VOID);
            counted("void set", "+10% accuracy");
            counted("void set", elite ? "+12.5% damage" : "+10% damage");
            ea = (int) floor(ea * 1.10);
            ed = (int) floor(ed * (elite ? 1.125 : 1.10));
        }

        long attackRoll = attackRoll(ea, loadout.offensive.ranged + QuiverAmmo.accuracy(loadout));
        boolean a2 = wearing(loadout, "eclipse atlatl");
        if (a2) {
            // Official calc: the atlatl swaps the DAMAGE side to melee -
            // Strength level (ranged prayer factors and void still apply)
            // and the worn MELEE strength bonuses - while accuracy stays
            // pure ranged.
            ed = effectiveLevel(levels.strength, prayers.rangedStrength, sb);
            if (isWearingRangedVoid(loadout)) {
                ed = (int) floor(ed
                    * (wearsAll(loadout, ELITE_VOID) ? 1.125 : 1.10));
            }
        }
        int mh = maxHitFromEffective(ed, a2 ? loadout.bonuses.strength
                : effectiveRangedStrength(loadout) + BlowpipeDarts.strength(request, loadout.getWeapon()) + QuiverAmmo.strength(loadout));
        attackRoll = applyRanged(request, loadout, attackRoll, false);
        mh = (int) applyRanged(request, loadout, mh, true);
        mh += RatBoneRules.flatMaxHitBonus(request.monster, loadout.getWeapon());
        // Tonalztics roll 75% of the max (every version); the charged pair
        // throws twice and the dark bow fires two arrows, each its own full
        // roll (wiki Multi-hit weapons; official calc: two standard dists).
        String bow = name(loadout.getWeapon());
        boolean g = bow.startsWith("tonalztics");
        if (g) mh = mh * 3 / 4;
        int rrm = mh;
        mh = applyFlatArmour(request, mh);

        long defenceRoll = npcDefenceRoll(request.monster, "ranged", loadout.getWeapon());
        double accuracy = normalAccuracy(attackRoll, defenceRoll);
        double expected = expectedHitWithFlatArmour(accuracy, 0, rrm, request.monster.defensive.getFlatArmour());
        GearItem arrows = loadout.get(AMMO) != null ? loadout.get(AMMO) : loadout.quiverAmmo;
        if (arrows != null && name(arrows).startsWith("seeking") && rrm >= 3
            && RangedAmmo.strengthApplies(arrows, loadout.getWeapon()) && request.monster.defensive.getFlatArmour() == 0) {
            // Seeking arrows (The Blood Moon Rises; wiki): a landed arrow
            // deals at least 3. The expectation already counts a landed 0
            // as 1, so the 0, 1 and 2 rolls lift by 2, 2 and 1 (official
            // calc: the clamp runs before the accurate-zero rule).
            counted("seeking arrows", "minimum hit 3");
            expected += accuracy * 5.0 / (rrm + 1);
        }
        if (bow.startsWith("dark bow") || g && !"uncharged".equals(loadout.getWeapon().versionLower)) {
            expected *= 2;
        }
        // Eclipse: all three eclipse moon pieces + the atlatl (broken counts,
        // same as the other moon sets).
        if (wearsAll(loadout, ECLIPSE_MOON) && !request.monster.hasAttribute("burn_immune")) {
            // Eclipse (wiki): each successful hit has a 20% chance to apply
            // a 10-damage burn - "roughly 2 burn damage per successful hit"
            // sustained (their own average; the 5-stack cap and target
            // death truncate a little in practice).
            counted("eclipse set", "~2 burn damage per landed hit");
            expected += accuracy * 2.0;
        }
        int speed = attackSpeed(loadout, RANGED);
        if (rapid) speed = max(2, speed - 1);
        String attackType = rapid ? "ranged rapid" : "ranged accurate";
        String dn = BlowpipeDarts.tierName(request, loadout.getWeapon());
        if (dn != null) attackType += " - " + dn;
        return new DpsResult(loadout, expected / (speed * SECONDS_PER_TICK), accuracy, expected, mh, speed, attackType, attackRoll, defenceRoll);
    }

    /** Twinflame's double hit applies to elemental Bolt/Blast/Wave tiers
     * only (wiki excludes Strike and Surge explicitly). */
    static boolean twinflameDoubles(SpellStats spell) {
        if (spell.element == null || spell.element.isEmpty()) return false;
        String name = Names.lower(spell.name);
        return name.endsWith("bolt") || name.endsWith("blast") || name.endsWith("wave");
    }

    DpsResult calculateMagic(OptimizationRequest request, Loadout loadout) {
        OptimizationRequest er = isPoweredStaff(loadout) && request.spell != null ? request.withSpell(null) : request;
        PlayerLevels levels = er.getLevels();
        PrayerBonuses prayers = er.prayers;
        // Prayer floor, +2 accurate stance, +9 - matches the official
        // calc's effective level.
        // Stance: powered staves fight on Accurate (+2); AUTOCAST casts
        // carry NO stance bonus (official-verified 2026-08-21, vector
        // dhwslayer-adamantdragon: the flat +2 ran every roll one
        // effective level hot - invisible at 99% acc saturation, +0.5%
        // acc against real defence).
        int ea = (int) floor(levels.magic * prayers.magicAccuracy) + (isPoweredStaff(loadout) ? 2 : 0) + 9;
        boolean mv = wearing(loadout, "void mage helm") && isWearingVoidRobes(loadout);
        if (mv) {
            counted("void set", "+45% accuracy");
            ea = (int) floor(ea * 1.45);
        }

        int mab = loadout.offensive.magic;
        if (wearing(loadout, "tumeken")) {
            // The shadow triples the magic attack bonus of ALL equipment,
            // quadruples it inside the Tombs of Amascut (wiki) - and the
            // magic damage with it, in the damage path.
            mab *= inToa(request) ? 4 : 3;
        }
        long attackRoll = attackRoll(ea, mab);
        int mh = magicMaxHit(er, loadout);
        attackRoll = applyMagicAccuracyBonuses(er, loadout, attackRoll);
        mh = applyMagicDamageBonuses(er, loadout, mh);
        if (mv && wearsAll(loadout, ELITE_VOID)) {
            // Elite magic void: +2.5% magic damage (was missing - the set
            // only got its accuracy; wiki-standard values).
            counted("void set", "+2.5% damage");
            mh = (int) floor(mh * 1.025);
        }
        // Twinflame staff (wiki, verified 2026-07-18): +10% accuracy and
        // damage on standard-spellbook spells, and Bolt/Blast/Wave
        // elementals fire a SECOND hit at 40% of the first (no extra
        // runes) - Strike and Surge spells do not double.
        SpellStats ts2 = er.spell;
        // Their isWearingSmokeStaff family: the smoke battlestaff and its
        // mystic sibling grant the SAME +10 accuracy / +100 magic damage
        // on standard casts as the twinflame (they had nothing until
        // 2026-08-22); only the twinflame also splits its hit.
        boolean smokeStaff = wearsAny(loadout, SMOKE_STAVES, false);
        boolean t4 = smokeStaff && ts2 != null && "standard".equalsIgnoreCase(ts2.spellbook);
        if (t4) {
            // The damage half rides the magic-damage POOL (additive,
            // applyMagicDamageBonuses) exactly like theirs - a separate
            // multiplier here floors differently (verified 2026-08-21,
            // vector twinflame-firegiant: 44 vs their 42 max).
            counted(wearing(loadout, "twinflame") ? "twinflame staff" : "smoke staff", "+10% accuracy and damage on standard spells");
            attackRoll = (long) floor(attackRoll * 1.10);
        }
        // The official calc applies flat armour to melee and ranged ONLY
        // (flatAddTransformer is gated styleType !== 'magic', verified
        // 2026-08-21) - magic hits the raw bounds.

        long defenceRoll = npcDefenceRoll(er.monster, "magic", loadout.getWeapon());
        double accuracy = normalAccuracy(attackRoll, defenceRoll);
        GearItem mw = loadout.getWeapon();
        if (wearing(loadout, "confliction gauntlets") && (mw == null || !mw.twoHanded)) {
            // A second accuracy roll when casting one-handed (field
            // report 2026-08-22: a Zulrah magic BiS wearing them read
            // -0.56% with every other component identical).
            counted("confliction gauntlets", "a second accuracy roll (one-handed)");
            accuracy = conflictionAccuracy(attackRoll, defenceRoll);
        }
        double expected = expectedHit(accuracy, sunfireMinHit, mh);
        // Only the Twinflame itself splits the hit: the smoke battlestaff and
        // the mystic smoke staff share its +10% and nothing more (wiki;
        // ISS-01M5PHE6WD81W8542RW71EXSHC - the phantom second hit had an
        // Earth Wave off a smoke staff reading ~28% high, and the strip
        // calling an owned smoke staff an upgrade over the Purging staff).
        if (t4 && wearing(loadout, "twinflame") && twinflameDoubles(ts2)) {
            // Their exact model (verified 2026-08-21, vector
            // twinflame-firegiant): the second splat rolls NOTHING of its
            // own - it is trunc(0.4 x the first hit's roll), truncated
            // PER ROLL. The old smooth 1.4x kept the fractions and ran
            // +6.3% hot on a Fire giant Water Wave.
            counted("twinflame staff", "second hit at 40% of the roll (Bolt/Blast/Wave)");
            long ts = 0;
            for (int k = sunfireMinHit; k <= mh; k++) ts += k * 2L / 5;
            expected += accuracy * ((double) ts / (mh - sunfireMinHit + 1));
        }
        if (wearing(loadout, "sanguinesti")) {
            // The staff's passive (official PlayerVsNPCCalc): a fifth of
            // LANDED hits carry +8 flat - entirely unmodelled here until
            // 2026-08-22, the other half of the sang-goblin gap.
            counted("sanguinesti staff", "+8 on a fifth of landed hits");
            expected += accuracy * 0.2 * 8;
        }
        int speed = attackSpeed(loadout, MAGIC);
        String sn = er.spell == null ? "" : er.spell.name;
        double f2 = frostweaverBonus(er, loadout);
        if (f2 > 0) {
            counted("frostweaver set", "chance of a free spear hit per cast");
            expected += f2;
        }
        String pn = sn.isEmpty() && isPoweredStaff(loadout) && loadout.getWeapon() != null ? loadout.getWeapon().name : "";
        String attackType = sn.isEmpty() ? pn.isEmpty() ? "magic" : "magic: " + pn : er.spell.attack;
        return new DpsResult(loadout, expected / (speed * SECONDS_PER_TICK), accuracy, expected, mh, speed, attackType, attackRoll, defenceRoll, loadout.cost, sn);
    }

    int magicMaxHit(OptimizationRequest request, Loadout loadout) {
        GearItem weapon = loadout.getWeapon();
        String wn = name(weapon);
        int ml = request.getLevels().magic;
        SpellStats spell = request.spell;
        boolean ps = isPoweredStaff(loadout);
        if (spell != null && !ps) {
            if ("Magic Dart".equals(spell.name)) {
                int base = ml / 10 + 10;
                return wearing(loadout, "slayer's staff (e)") ? ml / 6 + 13 : base;
            }
            return elementalSpellMax(spell, ml) + flatSpellBonus(loadout, spell);
        }
        if (!ps) return 0;
        if (wn.contains("warped sceptre")) return max(1, (8 * ml + 96) / 37);
        if (wn.contains("bone staff")) return max(1, ml / 3 - 5) + 10;
        for (var e : STAFF_FIXED.entrySet()) {
            if (wn.contains(e.getKey())) return e.getValue();
        }
        for (var e : STAFF_OFFSET.entrySet()) {
            if (wn.contains(e.getKey())) return max(1, ml / 3 + e.getValue());
        }
        return max(1, ml / 3 + 1);
    }

    /**
     * June 2025 magic rebalance: elemental spells share a class-wide max
     * hit scaled by the caster's Magic level - Water Surge cast at 95+
     * hits like Fire Surge. The chosen element then only matters for
     * elemental-weakness matching, which is what makes weakness-exploiting
     * spell picks viable. Verified against the official calculator.
     */
    /** Package-private: the optimizer prunes dominated elementals with the
     * same effective-max computation the DPS chain uses. */
    static int elementalSpellMax(SpellStats spell, int ml) {
        if (spell.element.isEmpty()) return spell.maxHit;
        // The two-word parse is precomputed on SpellStats - split() here
        // allocated a String[] per spell per trial.
        String tw = spell.nameSecondWord;
        if (tw == null || !isElementWord(spell.nameFirstWord)) return spell.maxHit;
        int row = TIER_WORDS.indexOf(tw);
        if (row < 0) return spell.maxHit;
        // Class max hits: wind/water/earth/fire per tier - the class's base,
        // plus its step at each Magic level the tier table lists.
        int[] tier = TIERS[row];
        int max = tier[0];
        for (int i = 2; i < tier.length; i++) {
            if (ml >= tier[i]) max += tier[1];
        }
        return max;
    }

    /** The elemental classes and, per class: {max hit at the first tier,
     * the step per tier, the Magic levels of the three higher tiers}. */
    static final List<String> TIER_WORDS = List.of("Strike", "Bolt", "Blast", "Wave", "Surge");
    static final int[][] TIERS = {
        {2, 2, 5, 9, 13}, {9, 1, 23, 29, 35}, {13, 1, 47, 53, 59}, {17, 1, 65, 70, 75}, {21, 1, 85, 90, 95}};

    /** Flat adds before the magic damage %: chaos gauntlets +3 on bolts,
     * elemental amulet +2 on every element, amulet of X +2 on its own
     * (official-verified 2026-09-08). */
    int flatSpellBonus(Loadout loadout, SpellStats spell) {
        String element = spell.element;
        if (element.isEmpty()) return 0;
        int bonus = 0;
        if ("Bolt".equals(spell.nameSecondWord) && wearing(loadout, "chaos gauntlets")) {
            counted("chaos gauntlets", "+3 max hit on bolt spells");
            bonus += 3;
        }
        String amulet = wearing(loadout, "elemental amulet") ? "elemental amulet" : spell.amulet;
        if (wearing(loadout, amulet)) {
            counted(amulet, "+2 max hit");
            bonus += 2;
        }
        return bonus;
    }

    static boolean isElementWord(String word) {
        return "Wind".equals(word) || "Water".equals(word) || "Earth".equals(word) || "Fire".equals(word);
    }

    /**
     * Plain standard-book elemental combat spell ("Wind Strike".."Fire
     * Surge"): legal on every non-powered staff (spellAllowed has no rule
     * for them), and the DPS chain reads nothing from it beyond the
     * effective max hit and the weakness element - which makes same-class
     * spells interchangeable, and dominated ones prunable per optimize.
     */
    static boolean isPlainElemental(SpellStats spell) {
        return !spell.element.isEmpty() && spell.nameSecondWord != null && isElementWord(spell.nameFirstWord);
    }

    long npcDefenceRoll(MonsterStats monster, String attackType, GearItem weapon) {
        // Most NPCs defend magic with their Magic level; a curated list
        // (vendored from the official calc) uses Defence level instead.
        int level = "magic".equals(attackType) && !magicDefenceUsesDefenceLevel(monster) ? monster.magic : monster.defence;
        int bonus;
        if ("ranged".equals(attackType)) {
            bonus = monster.defensive.get(rangedDefenceType(weapon));
        } else {
            bonus = monster.defensive.get(attackType);
        }
        long roll = defenceRoll(level + 9, bonus);
        int invocation = monster.getToaInvocationLevel();
        if (invocation > 0) {
            // ToA invocation scaling (official engine rule): defence rolls
            // x (250+invocation)/250, truncated - long math multiplies
            // before dividing so the result matches theirs exactly. This
            // is what flips bowfa over a blowpipe at the Wardens at any
            // real raid level (field report 2026-08-06).
            roll = roll * (250 + invocation) / 250;
        }
        return roll;
    }

    static String rangedDefenceType(GearItem weapon) {
        String category = weapon == null ? "" : weapon.categoryLower;
        if (category.contains("thrown")) return "light";
        if (category.contains("crossbow") || category.contains("chinchompa")) return "heavy";
        return "standard";
    }

    static int effectiveRangedStrength(Loadout loadout) {
        int rs = loadout.bonuses.rangedStrength;
        GearItem ammo = loadout.get(AMMO);
        if (ammo != null && ammo.bonuses.rangedStrength > 0 && !RangedAmmo.strengthApplies(ammo, loadout.getWeapon())) {
            rs -= ammo.bonuses.rangedStrength;
        }
        return rs;
    }

    static int attackSpeed(Loadout loadout, CombatStyle style) {
        GearItem weapon = loadout.getWeapon();
        if (weapon == null || weapon.speed <= 0) return style == MAGIC ? 5 : 4;
        if (style == MAGIC && !isPoweredStaff(loadout)) {
            // Casting a spell is 5 ticks regardless of the staff's melee
            // speed (upstream billed autocasts at the wand's 4 ticks - a
            // 25% dps overstatement). Harmonised nightmare staff: 4 ticks
            // on standard spells.
            // Twinflame casts at 6 ticks (wiki: "attack and cast speed of
            // 6") - its damage bonuses more than pay the tick back.
            return name(weapon).contains("harmonised") ? 4 : name(weapon).contains("twinflame") ? 6 : 5;
        }
        return max(1, weapon.speed);
    }

    /** The melee chain for one side (damage = the max hit, else the roll):
     * void, the pre rules, avarice/salve/slayer, the post rules, and the
     * inquisitor set on crush. */
    long applyMelee(OptimizationRequest request, Loadout loadout, long value, long base, String attackType, boolean damage) {
        String what = damage ? "damage" : "accuracy";
        if (isWearingMeleeVoid(loadout)) value = step(value, "void set", what, Factor.PLUS_10);
        value = applyRules(request, loadout, value, base, damage ? MELEE_DAMAGE_PRE : MELEE_ACCURACY_PRE);
        value = salveOrSlayer(request, loadout, value, what, MELEE, false);
        value = applyRules(request, loadout, value, base, damage ? MELEE_DAMAGE_POST : MELEE_ACCURACY_POST);
        if (!damage) value = VampyreRules.accuracy(request.monster, loadout, value);
        return "crush".equals(attackType) ? applyInquisitorBonus(loadout, value) : value;
    }

    /** The ranged chain for one side: the far Titans' accuracy, the crystal
     * set, avarice/salve/slayer, the post rules and the twisted bow. */
    long applyRanged(OptimizationRequest request, Loadout loadout, long value, boolean damage) {
        String what = damage ? "damage" : "accuracy";
        // Royal Titans out of melee distance (wiki, ref'd to Mod Husky):
        // hidden +500% ranged accuracy - a 6x multiplier against their
        // +700 ranged defence, lasting until they re-enter melee range.
        // The far phase is exactly when ranged is meant to be used, and
        // without this the "(Out of reach)" forms would price ranged as
        // hopeless and recommend nothing sensible.
        if (!damage && isFarTitan(request)) {
            counted("titans out of reach", "6x ranged accuracy");
            value = multiply(value, 6, 1);
        }
        // Crystal armour scales the base roll BEFORE salve/slayer - the wiki
        // calc devs verified the flooring order in-game (bofa + slayer helm
        // + crystal body/legs maxes 36, not the 37 the reverse order gives).
        // x(20+n)/20 accuracy, x(40+n)/40 damage: each helm/legs/body
        // weight is +5% accuracy and +2.5% damage (the label in tenths of a
        // percent so 7.5 prints exactly, 15 prints without .0).
        if (wearsAny(loadout, CRYSTAL_BOW, true)) {
            int pieces = setWeight(loadout, CRYSTAL, true);
            int den = damage ? 40 : 20;
            if (pieces > 0) {
                int tenths = pieces * (damage ? 25 : 50);
                counted("crystal armour set", "+" + tenths / 10 + (tenths % 10 == 0 ? "" : "." + tenths % 10) + "% " + what);
            }
            value = multiply(value, den + pieces, den);
        }
        value = salveOrSlayer(request, loadout, value, what, RANGED, damage && wearing(loadout, "eclipse atlatl"));
        value = applyRules(request, loadout, value, 0, damage ? RANGED_DAMAGE_POST : RANGED_ACCURACY_POST);
        if (wearing(loadout, "twisted bow")) return floorDiv(value * tbowScaling(request, !damage, what), 100);
        return value;
    }

    long applyMagicAccuracyBonuses(OptimizationRequest request, Loadout loadout, long roll) {
        // The elemental-weakness bonus adds severity% OF THIS BASE ROLL
        // after every conditional multiplier (official-verified 2026-07-23:
        // multiplying the boosted roll instead over-credited an Iron dragon
        // Earth Surge by ~1.4% dps - field cross-check via the Wiki calc
        // button, its first catch).
        long br = roll;
        roll = salveOrSlayer(request, loadout, roll, "accuracy", MAGIC, false);
        roll = applyRules(request, loadout, roll, br, MAGIC_ACCURACY_POST);
        roll = weakness(request, roll, br, "% of base accuracy");
        // Only the water tome adds accuracy; fire and earth boost damage alone
        // (the three wiki pages, 2026-10-19; Kendyy's follow-up).
        if (tomeMatches(request, loadout) && "water".equals(request.spell.element)) {
            roll = step(roll, loadout.get(SHIELD).nameLower, "accuracy", Factor.PLUS_10);
        }
        return demonbaneSpell(request, loadout, roll, 40, 80, "accuracy");
    }

    /** The elemental-weakness bonus adds severity% OF THE BASE value after
     * every conditional multiplier (official-verified 2026-07-23:
     * multiplying the boosted roll instead over-credited an Iron dragon
     * Earth Surge by ~1.4% dps - field cross-check via the Wiki calc
     * button, its first catch). */
    long weakness(OptimizationRequest request, long value, long base, String label) {
        SpellStats spell = request.spell;
        if (spell == null || !spell.element.equals(request.monster.weaknessElement)) return value;
        int severity = request.monster.weaknessSeverity;
        // The label only when collecting: the optimizer's beam (collection
        // off) built and dropped two strings per weak-monster magic trial.
        if (collectCounted) counted("elemental weakness", "+" + severity + label);
        return value + multiply(base, severity, 100);
    }

    /** The demonbane spells assume Mark of Darkness (standard practice - it
     * is a cheap self-buff and the purging staff quintuples its duration):
     * +40% accuracy and +25% damage, 80 / 50 with the purging staff, scaled
     * by the monster's demonbane vulnerability like every other demonbane
     * (field 2026-08-21: the spells bypassed the table, +31% vs the
     * 70%-resistant Duke). */
    long demonbaneSpell(OptimizationRequest request, Loadout loadout, long value, int plain, int purging, String what) {
        if (!request.monster.hasAttribute("demon") || request.spell == null
            || !request.spell.name.contains("Demonbane")) {
            return value;
        }
        return demonbane(request, value, wearing(loadout, "purging staff") ? purging : plain, what, "demonbane spell (Mark of Darkness)");
    }

    int applyMagicDamageBonuses(OptimizationRequest request, Loadout loadout, int mh) {
        int bmh = mh;
        sunfireMinHit = 0;
        int md = loadout.bonuses.magicDamage;
        if (wearing(loadout, "tumeken")) md = min(1000, md * (inToa(request) ? 4 : 3));
        if (wearsAny(loadout, SMOKE_STAVES, false) && request.spell != null
            && "standard".equalsIgnoreCase(request.spell.spellbook)) {
            md += 100;
        }
        // Salve and avarice join the POOL additively (+200/+150 units,
        // official-verified 2026-08-21 vector salvedhw-vorkath: the old
        // separate 6/5 multiplier COMPOUNDED with gear magic damage,
        // +6% on a Vorkath fire surge). The slayer head stays a separate
        // 23/20 factor AFTER the pool - exactly their blackMaskBonus.
        boolean undead = request.monster.hasAttribute("undead");
        GearItem head = isSlayerTaskEligible(request) ? slayerHead(loadout, true) : null;
        boolean shb = false;
        if (isRevenant(request) && wearing(loadout, "amulet of avarice")) {
            counted("amulet of avarice", "+20% damage");
            md += 200;
        } else if (undead && wearing(loadout, "salve amulet(ei)")) {
            counted("salve amulet(ei)", "+20% damage");
            md += 200;
        } else if (undead && wearing(loadout, "salve amulet(i)")) {
            counted("salve amulet(i)", "+15% damage");
            md += 150;
        } else if (head != null) {
            counted(head.nameLower, "+15% damage");
            shb = true;
        }
        mh = (int) floor(mh * (1.0 + (md + request.prayers.magicDamagePercent * 10.0) / 1000.0));
        if (shb) mh = (int) multiply(mh, 23, 20);
        mh = (int) applyRules(request, loadout, mh, bmh, MAGIC_DAMAGE_POST);
        mh = (int) weakness(request, mh, bmh, "% base damage");
        // Sunfire runes (wiki; Kendyy 2026-10-19): a landed fire spell deals
        // at least a tenth of the max hit - the official calc takes the
        // tenth here, after the weakness and BEFORE the tome step.
        if (request.sunfireRunes && request.spell != null && "fire".equals(request.spell.element)) {
            counted("sunfire runes", "minimum hit a tenth of the max");
            sunfireMinHit = (int) multiply(mh, 1, 10);
        }
        // The tome multiplies the whole max hit and roll, weakness included
        // (official calc, vector watersurge-tome-drake 2026-10-18).
        if (tomeMatches(request, loadout)) {
            mh = (int) step(mh, loadout.get(SHIELD).nameLower, "damage", Factor.PLUS_10);
        }
        return (int) demonbaneSpell(request, loadout, mh, 25, 50, "damage");
    }

    static final List<String> SMOKE_STAVES = List.of("twinflame", "smoke battlestaff", "mystic smoke staff");

    /** Any worn item's name holds the marker. Markers are lower-case where
     * they are written (code and data), so no per-call lower-casing. */
    static boolean wearing(Loadout loadout, String marker) { return loadout.wears(marker, false); }

    /** Revenants: the avarice and wilderness-weapon conditionals key off
     * them (wiki calc: monster name starts with "Revenant").
     * Package-private: RevenantConditionalsTest exercises the gate. */
    static boolean isRevenant(OptimizationRequest request) { return request.monster.isRevenantMonster(); }

    /**
     * Wilderness weapon passive: +50% accuracy AND damage against monsters
     * in the Wilderness, CHARGED version only (wiki calc BaseCalc
     * .isRevWeaponBuffApplicable, applied after the avarice/salve/slayer
     * chain). Keyed on the REQUEST's in-Wilderness flag - name membership
     * alone buffed Catacombs hellhounds and Taverley dungeon staples
     * (audit A3.1); wilderness-exclusive monsters default the flag on.
     */
    static boolean revWeaponBuff(OptimizationRequest request, Loadout loadout, String... weapons) {
        if (!request.inWilderness) return false;
        GearItem weapon = loadout.get(WEAPON);
        if (weapon == null || !"charged".equals(weapon.versionLower)) return false;
        for (String name : weapons) {
            if (weapon.nameLower.equals(name)) return true;
        }
        return false;
    }

    static String name(GearItem item) { return item == null ? "" : item.nameLower; }

    /** Set membership by name fragment (gear_sets.json): the pieces a set
     * effect needs, the alternatives one role accepts, and the piece
     * weights of the scaling sets. */
    static final JsonObject SETS = objectOrThrow("gear_sets.json");
    static final List<String> BLOOD_MOON = set("all", "bloodMoon"), ECLIPSE_MOON = set("all", "eclipseMoon"),
        BLUE_MOON = set("all", "blueMoon"), VERAC = set("all", "verac"), ELITE_VOID = set("all", "eliteVoid"),
        CRYSTAL_BOW = set("any", "crystalBow"), VOID_TOP = set("any", "voidTop"), VOID_ROBE = set("any", "voidRobe");
    static final Map<String, Integer> CRYSTAL = new LinkedHashMap<>();
    static final Map<String, Integer> INQUISITOR = new LinkedHashMap<>();

    static {
        stringIntMap(SETS.getAsJsonObject("weights"), "crystal", CRYSTAL);
        stringIntMap(SETS.getAsJsonObject("weights"), "inquisitor", INQUISITOR);
    }

    static List<String> set(String group, String key) {
        List<String> names = new ArrayList<>();
        strings(SETS.getAsJsonObject(group), key, names);
        return names;
    }

    /** Every piece worn (the list order is the check's short-circuit order). */
    static boolean wearsAll(Loadout loadout, List<String> pieces) {
        for (String piece : pieces) {
            if (!wearing(loadout, piece)) return false;
        }
        return true;
    }

    /** Any of the alternatives worn; active = "Inactive" versions never count.
     * Indexed: the lists are of two kinds (List.of and ArrayList), so the
     * for-each iterator escaped and was 13% of a roster's allocation. */
    static boolean wearsAny(Loadout loadout, List<String> names, boolean active) {
        for (int i = 0; i < names.size(); i++) {
            if (loadout.wears(names.get(i), active)) return true;
        }
        return false;
    }

    /** The summed weight of the set's worn pieces. */
    static int setWeight(Loadout loadout, Map<String, Integer> weights, boolean active) {
        int total = 0;
        for (var piece : weights.entrySet()) {
            if (loadout.wears(piece.getKey(), active)) {
                total += piece.getValue();
            }
        }
        return total;
    }

    /** One gated multiplier rule of conditional_bonuses.json (see applyRules). */
    static class Rule {
        /** "accuracy" or "damage": the word the demonbane label carries. */
        final String what;
        final String attr;
        final String group;
        final String source;
        final String label;
        final String[] rev;
        final List<String> any;
        final List<String> anySet;
        final List<String> allSet;
        final int num;
        final int den;
        final int demonbane;
        final boolean base;

        Rule(JsonObject row, String what) {
            this.what = what;
            attr = str(row, "attr");
            group = str(row, "group");
            source = str(row, "source");
            label = str(row, "label");
            List<String> weapons = names(row, "rev");
            rev = weapons == null ? null : weapons.toArray(new String[0]);
            any = names(row, "any");
            anySet = row.has("anySet") ? set("any", str(row, "anySet")) : null;
            allSet = row.has("allSet") ? set("all", str(row, "allSet")) : null;
            num = integer(row, "num", 0);
            den = integer(row, "den", 0);
            demonbane = integer(row, "demonbane", 0);
            base = row.has("base");
        }
    }

    static String str(JsonObject row, String key) { return row.has(key) ? row.get(key).getAsString() : null; }

    /** The string list at row[key], or null when the row has none. */
    static List<String> names(JsonObject row, String key) {
        if (!row.has(key)) return null;
        List<String> list = new ArrayList<>();
        strings(row, key, list);
        return list;
    }

    /** One phase's rules ("melee.damage.post"), in file order. */
    static List<Rule> rules(String key) {
        List<Rule> list = new ArrayList<>();
        String what = key.split("\\.")[1];
        for (JsonElement row : BONUSES.getAsJsonArray(key)) {
            list.add(new Rule(row.getAsJsonObject(), what));
        }
        return list;
    }

    /** The gated multiplier rules, resolved once (they sit on the beam's
     * path): pre runs before the avarice/salve/slayer chain, post after. */
    static final JsonObject BONUSES = objectOrThrow("conditional_bonuses.json");
    static final List<Rule> MELEE_ACCURACY_PRE = rules("melee.accuracy.pre"),
        MELEE_ACCURACY_POST = rules("melee.accuracy.post"), MELEE_DAMAGE_PRE = rules("melee.damage.pre"),
        MELEE_DAMAGE_POST = rules("melee.damage.post"), RANGED_ACCURACY_POST = rules("ranged.accuracy.post"),
        RANGED_DAMAGE_POST = rules("ranged.damage.post"), MAGIC_ACCURACY_POST = rules("magic.accuracy.post"),
        MAGIC_DAMAGE_POST = rules("magic.damage.post");

    /**
     * One phase of the gated multiplier rules (conditional_bonuses.json),
     * walked in file order - each multiply floors, so the order IS the
     * number (the statement order the rules had as code). A row applies
     * when every gate it names holds: a monster attribute, a CHARGED
     * wilderness weapon (revWeaponBuff), a worn item fragment, a gear set
     * - and a group stops at its first applying row (the keris pair). It
     * then multiplies by num/den, adds num/den of the phase's BASE value
     * instead (the obsidian set), or applies a vulnerability-scaled
     * demonbane percent that labels itself.
     */
    long applyRules(OptimizationRequest request, Loadout loadout, long value, long base, List<Rule> rules) {
        MonsterStats mob = request.monster;
        if (mob != rulesMob) {
            cutRules.clear();
            rulesMob = mob;
        }
        List<Rule> cut = cutRules.get(rules);
        if (cut == null) {
            cut = new ArrayList<>();
            for (Rule rule : rules) if (rule.attr == null || mob.hasAttribute(rule.attr)) cut.add(rule);
            cutRules.put(rules, cut);
        }
        String applied = null;
        for (Rule rule : cut) {
            if (rule.group != null && rule.group.equals(applied)
                || rule.rev != null && !revWeaponBuff(request, loadout, rule.rev) || rule.any != null && !wearsAny(loadout, rule.any, false)
                || rule.anySet != null && !wearsAny(loadout, rule.anySet, false)
                || rule.allSet != null && !wearsAll(loadout, rule.allSet)) {
                continue;
            }
            applied = rule.group;
            if (rule.demonbane > 0) {
                value = demonbane(request, value, rule.demonbane, rule.what, rule.source);
                continue;
            }
            counted(rule.source, rule.label);
            value = rule.base ? value + multiply(base, rule.num, rule.den) : multiply(value, rule.num, rule.den);
        }
        return value;
    }

    static boolean isWearingMeleeVoid(Loadout loadout) {
        return wearing(loadout, "void melee helm") && isWearingVoidRobes(loadout);
    }

    static boolean isWearingRangedVoid(Loadout loadout) {
        return wearing(loadout, "void ranger helm") && isWearingVoidRobes(loadout);
    }

    static boolean isWearingVoidRobes(Loadout loadout) {
        return wearsAny(loadout, VOID_TOP, false) && wearsAny(loadout, VOID_ROBE, false) && wearing(loadout, "void knight gloves");
    }

    /** A charged elemental tome worn with a standard-spellbook spell of its
     * element: +10% damage against NPCs, and for the water tome +10%
     * accuracy too (wiki, the three tome pages; rakaykhan1512's drakes,
     * Kendyy's follow-up). Empty tomes, off-element spells and Ancients
     * get nothing. */
    static boolean tomeMatches(OptimizationRequest request, Loadout loadout) {
        GearItem shield = loadout.get(SHIELD);
        SpellStats spell = request.spell;
        // "charged" first: the name test builds a string, once per magic trial
        // with any shield before.
        return shield != null && spell != null && spell.element != null && "standard".equalsIgnoreCase(spell.spellbook)
            && "charged".equals(shield.versionLower) && shield.nameLower.equals("tome of " + spell.element);
    }

    /** The twisted bow's magic-level scaling, counted on the card. */
    int tbowScaling(OptimizationRequest request, boolean accuracy, String what) {
        int cap = request.monster.hasAttribute("xerician") ? 350 : 250;
        int magic = min(cap, max(request.monster.magic, request.monster.offensiveMagic));
        int bonus = tbowBonus(magic, accuracy);
        counted("twisted bow scaling", (bonus >= 100 ? "+" : "") + (bonus - 100) + "% " + what);
        return bonus;
    }

    /** A chain step's factor and the label it counts ("+16.7% accuracy"). */
    enum Factor {
        PLUS_20("+20% ", 6, 5), PLUS_16_7("+16.7% ", 7, 6), PLUS_15("+15% ", 23, 20), PLUS_10("+10% ", 11, 10);

        final String label;
        final int num;
        final int den;

        Factor(String label, int num, int den) {
            this.label = label;
            this.num = num;
            this.den = den;
        }
    }

    long step(long value, String source, String what, Factor factor) {
        counted(source, factor.label + what);
        return multiply(value, factor.num, factor.den);
    }

    /**
     * The avarice / salve / slayer-head chain: exclusive with EACH OTHER
     * only - the weapon bonuses around it (arclight, dhl...) stack on top.
     * The order mirrors the wiki calc: avarice first at revenants, then
     * the 20% salves, the lesser salve this style accepts, then the head.
     * wearing() is a substring match, so the stronger spellings are tested
     * first ("salve amulet" also matches the (i) - on a melee roll both
     * are the same 7/6). Melee takes any salve and any head at 7/6; ranged
     * and magic need the imbued (i) salve and an imbued head - the (i) is
     * 7/6 on a ranged roll, 23/20 on a magic one, the head 23/20 on both.
     * atlatl: the eclipse atlatl's melee-side damage accepts the UNIMBUED
     * melee salve variants (official calc scalesWithStr branches) and a
     * bare black mask at the melee 7/6 - an imbued head keeps 23/20.
     */
    long salveOrSlayer(OptimizationRequest request, Loadout loadout, long value, String what, CombatStyle style, boolean a2) {
        boolean melee = style == MELEE;
        if (isRevenant(request) && wearing(loadout, "amulet of avarice")) {
            return step(value, "amulet of avarice", what, Factor.PLUS_20);
        }
        if (request.monster.hasAttribute("undead")) {
            if (wearing(loadout, "salve amulet(ei)")) {
                // Named WITHOUT a space, unlike "salve amulet (e)" - a contains
                // match on the (e) string misses it and demotes it a tier.
                return step(value, "salve amulet(ei)", what, Factor.PLUS_20);
            }
            if ((melee || a2) && wearing(loadout, "salve amulet (e)")) {
                // The unimbued (e) is a melee-only 20%; ranged and magic fall to salve(i).
                return step(value, "salve amulet (e)", what, Factor.PLUS_20);
            }
            if (!melee && wearing(loadout, "salve amulet(i)")) {
                return step(value, "salve amulet(i)", what, style == MAGIC ? Factor.PLUS_15 : Factor.PLUS_16_7);
            }
            if ((melee || a2) && wearing(loadout, "salve amulet")) {
                return step(value, "salve amulet", what, Factor.PLUS_16_7);
            }
        }
        if (isSlayerTaskEligible(request)) {
            GearItem head = slayerHead(loadout, !melee);
            if (head != null) {
                return step(value, head.nameLower, what, melee ? Factor.PLUS_16_7 : Factor.PLUS_15);
            }
            if (a2 && wearing(loadout, "black mask")) return step(value, "black mask", what, Factor.PLUS_16_7);
        }
        return value;
    }

    /** The worn slayer-head item (helm, mask, imbued variants; imbued = only
     * the imbue ranged and magic need), or null - the counted-bonus line
     * names the ACTUAL item (a bare black mask must not be reported as
     * "slayer helmet"). */
    static GearItem slayerHead(Loadout loadout, boolean imbued) {
        for (GearItem item : loadout.getGear().values()) {
            if (item != null && (imbued ? item.isImbuedSlayerHead() : item.isSlayerHead())) return item;
        }
        return null;
    }

    static boolean inToa(OptimizationRequest request) {
        return "toa".equals(RaidBoosts.raidKey(request.monster));
    }

    static boolean isSlayerTaskEligible(OptimizationRequest request) {
        return request.onSlayerTask && request.monster.slayerMonster;
    }

    static boolean isPoweredStaff(Loadout loadout) { return isPoweredStaff(loadout.getWeapon()); }

    /** THE powered-staff check - the optimizer must agree with the
     * calculator or a staff gets evaluated down the wrong path (the
     * optimizer once had its own copy missing eye of ayak). The rule
     * itself is precomputed per item (GearItem ctor) - this ran the
     * name-fragment chain per trial and topped the profile. */
    static boolean isPoweredStaff(GearItem weapon) { return weapon != null && weapon.poweredStaff; }

    /** Apply a demonbane bonus scaled by the monster's effectiveness
     * (Duke resists: +70% lands as +49%; Yama amplifies). The source
     * NAMES itself (2026-08-22): routing every demonbane through one
     * label made the scorching bow and the demonbane spells both report
     * "demonbane weapon" - naming gear the player is not even wearing. */
    long demonbane(OptimizationRequest request, long value, int bonusPercent, String what, String source) {
        int scaled = DemonbaneVulnerability.scaledBonus(request.monster, bonusPercent);
        int effectiveness = DemonbaneVulnerability.percentFor(request.monster);
        counted(source, "+" + scaled + "% " + what + (effectiveness == 100 ? "" : " (" + effectiveness + "% effectiveness)"));
        return multiply(value, 100 + scaled, 100);
    }

    /** A Royal Titan in its out-of-reach phase (the synthetic melee-immune
     * group variant carries the attribute; the base near-phase rows never
     * do). */
    static boolean isFarTitan(OptimizationRequest request) {
        MonsterStats monster = request.monster;
        if (monster == null || !monster.hasAttribute("immune_melee")) return false;
        String name = monster.name.toLowerCase();
        return name.contains("branda") || name.contains("eldric");
    }

    /**
     * Frostweaver (wiki): after casting a spell that binds, freezes or
     * roots, the spear has a chance to land an instant melee attack - 20%
     * for Standard-book binds and Ancient ice spells, 50% for Arceuus
     * grasps, "even if it does not bind a target". Expected bonus damage
     * per cast = chance x the spear's plain melee expected hit (stab, no
     * melee prayer - you are on a magic prayer while casting).
     */
    double frostweaverBonus(OptimizationRequest request, Loadout loadout) {
        SpellStats spell = request.spell;
        // Frostweaver: all three blue moon pieces + the spear.
        if (spell == null || !wearsAll(loadout, BLUE_MOON)) return 0;
        String sn = spell.name;
        String book = spell.spellbook == null ? "" : Names.lower(spell.spellbook);
        double chance;
        if ("arceuus".equals(book) && "Grasp".equalsIgnoreCase(spell.nameSecondWord)) {
            chance = 0.5;
        } else if ("ancient".equals(book) && "Ice".equalsIgnoreCase(spell.nameFirstWord)) {
            chance = 0.2;
        } else if ("standard".equals(book) && ("Bind".equalsIgnoreCase(sn)
            || "Snare".equalsIgnoreCase(sn) || "Entangle".equalsIgnoreCase(sn))) {
            chance = 0.2;
        } else {
            return 0;
        }
        PlayerLevels levels = request.getLevels();
        int effAttack = effectiveLevel(levels.attack, 1.0, 0);
        long meleeRoll = attackRoll(effAttack, loadout.offensive.getAttackBonus("stab"));
        int effStrength = effectiveLevel(levels.strength, 1.0, 0);
        int meleeMax = maxHitFromEffective(effStrength, loadout.bonuses.strength);
        long defenceRoll = npcDefenceRoll(request.monster, "stab", loadout.getWeapon());
        double meleeAccuracy = normalAccuracy(meleeRoll, defenceRoll);
        return chance * normalExpectedHit(meleeAccuracy, meleeMax);
    }

    static int applyFlatArmour(OptimizationRequest request, int hit) {
        return max(0, hit - request.monster.defensive.getFlatArmour());
    }

    /** Inquisitor's set, official-calc semantics (verified 2026-08-21,
     * vector inqfull-cow: the old model TRIPLED the factor with the
     * mace - the cow BiS ran +4.3% hot): crush accuracy AND damage,
     * helm +1 / hauberk +2 / plateskirt +2 over 200, mace-agnostic. */
    static long applyInquisitorBonus(Loadout loadout, long value) {
        int bonus = setWeight(loadout, INQUISITOR, false);
        return bonus == 0 ? value : multiply(value, 200 + bonus, 200);
    }

    /** The twisted bow's percent multiplier (roll or hit becomes bonus% of
     * itself) at this monster magic level - exposed as the number so the
     * counted-bonus assurance can print exactly what applied. */
    static int tbowBonus(int magic, boolean am) {
        int factor = am ? 10 : 14;
        int base = am ? 140 : 250;
        // Truncate TOWARD ZERO like theirs (floorDiv would round a
        // negative numerator down at magic <= 3).
        int t2 = (3 * magic - factor) / 100;
        int inner = 3 * magic / 10 - 10 * factor;
        int t3 = inner * inner / 100;
        // The CLAMP (at the base) their formula carries and ours never did
        // (field 2026-08-21: at the 250 magic cap the accuracy bonus
        // computes to 141% - Zulrah and Hydra ran +0.71% on the roll, and
        // the tbow won BiS seats it had not earned).
        return max(0, min(base, base + t2 - t3));
    }

    static long multiply(long value, int numerator, int denominator) {
        return (long) floor((double) value * numerator / denominator);
    }
}
