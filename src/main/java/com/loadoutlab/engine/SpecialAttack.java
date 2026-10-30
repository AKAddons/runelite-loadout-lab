package com.loadoutlab.engine;

import lombok.*;
import com.loadoutlab.data.*;
import java.util.*;
import static java.lang.Math.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

// Special-attack definitions and expected-damage math.
// <p>Every entry's cost/modifiers were verified against the OSRS Wiki
// "Special attacks" page (and individual weapon pages) on 2026-07-05.
// Accuracy modifiers multiply the ATTACK ROLL (not the final hit chance),
// matching in-game mechanics; damage modifiers scale the max hit.
// <p>Expected damage is per single use of the special attack, against the
// monster the base result was computed for. Utility effects (defence
// drains, heals, bolt procs) are described in {@link #getNote()} but not
// folded into the number.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class SpecialAttack {
    public enum Kind {
        // One hit: accuracy and/or damage multipliers.
        SINGLE,
        // Two fully independent hits (dragon dagger, dragon knife).
        DOUBLE_INDEPENDENT,
        // Two hits that share one accuracy outcome (abyssal dagger).
        LINKED_DOUBLE,
        // Guaranteed damage, uniform 50-150% of max (voidwaker).
        VOIDWAKER,
        // Up to 4 accuracy rolls; damage tier by which roll landed (dragon claws).
        CLAWS,
        // Two hits, boosted damage roll with a minimum per hit, capped at 48 (dark bow).
        DARK_BOW,
        // Two arrows; custom prayer-less max-hit formula, 10/7 accuracy (magic shortbow).
        MSB_SNAPSHOT,
        // +10% damage; a second hit at 75% accuracy vs large monsters (halberds).
        HALBERD_SWEEP,
        // An instant extra normal attack (granite maul).
        EXTRA_ATTACK,
        // Level-scaled unique damage roll (volatile nightmare staff).
        VOLATILE,
        // One accuracy roll; a hit deals EXACTLY damageMultiplier * max, no damage roll (sunspear).
        FIXED_FRACTION,
        // Four independent accuracy rolls; damage tier scales with successes (crimson kisten).
        MULTI_ROLL_TIERED,
        // Claws-style cascade with tier means 1.25/1.00/0.75 of max (burning claws).
        CASCADE_CLAWS,
        // A landed cast buffs the next three, fired every two ticks (Zorya's tome).
        BUFFED_FOLLOWUPS,
    }

    static final int DARK_BOW_CAP = 48;

    final String[] namePrefixes;
    final String displayName;
    public final CombatStyle style;
    final Kind kind;
    public final int energyCost;
    final double accuracyMultiplier;
    final double damageMultiplier;
    final String note;
    // Defence drained on a damaging hit, as a fraction of CURRENT defence
    // (DWH 0.30, elder maul 0.35); 0 for non-drain specs. BGS drains by
    // damage dealt - modeled via {@link #drainsByDamage}.
    final double defenceDrainFraction;
    final boolean drainsByDamage;
    // Drains DEFENCE by this fraction of the target's MAGIC level per
    // landed spec (Tonalztics of ralos' Division - wiki 2026-08-09:
    // 12.5% per glaive hit, two glaives charged = 25% per spec).
    final double magicDrainFraction;
    // Targets the spec hits in a multi area (wiki); 0 = single-target.
    final int multiTargets;

    // Wiki-verified entries live in special_attacks.json (hub token cap:
    // data rides resources, not source). Order matters: more specific
    // prefixes ("magic shortbow (i)") come first in the file.
    static final List<SpecialAttack> REGISTRY = loadRegistry();

    // A special_attacks.json row as Gson reads it.
    static class Row {
        List<String> prefixes = List.of();
        String name, style, kind, note;
        int cost, multi;
        double accuracy, damage, drainFraction, drainMagicFraction;
        boolean drainsByDamage;
    }

    static List<SpecialAttack> loadRegistry() {
        List<SpecialAttack> specs = new ArrayList<>();
        try {
            for (Row row : JsonResources.read("special_attacks.json", Row[].class)) {
                specs.add(new SpecialAttack(row.prefixes.toArray(new String[0]), row.name, CombatStyle.valueOf(row.style), Kind.valueOf(row.kind),
                    row.cost, row.accuracy, row.damage, row.note, row.drainFraction, row.drainsByDamage, row.drainMagicFraction, row.multi));
            }
        } catch (Exception e) {
            // A malformed table must never take the client down; the spec
            // tests fail loudly on an empty registry instead.
            return List.of();
        }
        return specs;
    }


    // The definition for this weapon at this combat style, or null.
    public static SpecialAttack match(GearItem item, CombatStyle style) {
        SpecialAttack spec = match(item);
        return spec != null && spec.style == style ? spec : null;
    }

    // Style-free match: the spec swap is its own weapon switch, so every
    // spec weapon is a candidate for ANY set (field request 2026-07-17 -
    // "sometimes you want to use magic + chally").
    public static SpecialAttack match(GearItem item) {
        if (item == null || item.slot != GearSlot.WEAPON) return null;
        String name = item.nameLower;
        for (SpecialAttack spec : REGISTRY) {
            for (String prefix : spec.namePrefixes) if (name.startsWith(prefix)) return spec;
        }
        return null;
    }

    // Expected damage of ONE special attack, given the normal-attack result
    // for the same loadout (source of max hit and attack/defence rolls).
    // The spec's multiplier at a stack of targets (the Multi card, Andrew
    // 2026-10-19): the stack capped at the targets it hits; 1 single-target.
    public double multiFactor(int stack) { return multiTargets <= 0 ? 1 : max(1, min(stack, multiTargets)); }

    public double expectedDamage(DpsResult base, MonsterStats monster, PlayerLevels levels) {
        // The Wardens' ejected core (wiki): melee "will always deal their
        // max hit" - hitChance and every damage roll collapse to their
        // caps. Without this, the rolls below would halve every spec's
        // value at the one place the game is built around spec dumping.
        if (style == CombatStyle.MELEE && MonsterMechanics.isWardenCore(monster)) return coreMaxDamage(base, monster);
        long attackRoll = base.attackRoll;
        long defenceRoll = base.defenceRoll;
        double hitChance = RollMath.normalAccuracy( (long) (attackRoll * accuracyMultiplier), defenceRoll);
        int max = base.maxHit;
        // One boosted hit's mean, and its expectation over the accuracy roll.
        double boosted = mean((int) (max * damageMultiplier));
        double hit = hitChance * boosted;

        switch (kind) {
            case SINGLE:
                return hit;
            case DOUBLE_INDEPENDENT:
                return 2 * hit;
            case LINKED_DOUBLE:
                // One accuracy outcome for both hits.
                return hit * 2;
            case VOIDWAKER:
                // Uniform 50-150% of max, no accuracy roll: averages exactly max.
                return max;
            case CLAWS:
                return clawsExpected(hitChance, max);
            case DARK_BOW:
                return darkBowExpected(hitChance, max, usesDragonArrows(base));
            case MSB_SNAPSHOT:
                return 2 * hitChance * mean(snapshotMax(base, levels));
            case HALBERD_SWEEP:
                // A second hit at 75% accuracy vs large monsters.
                return monster != null && monster.size > 1
                    ? hit + RollMath.normalAccuracy((long) (attackRoll * 0.75), defenceRoll) * boosted : hit;
            case EXTRA_ATTACK:
                return base.expectedHit;
            case FIXED_FRACTION:
                // No damage roll: a hit deals exactly damageMultiplier * max.
                return hitChance * damageMultiplier * max;
            case MULTI_ROLL_TIERED:
                return multiRollTieredExpected(hitChance, max);
            case CASCADE_CLAWS:
                return cascadeClawsExpected(hitChance, max);
            case BUFFED_FOLLOWUPS:
                return hit * (1 + 3 * hitChance);
            case VOLATILE:
            default:
                return hitChance * mean(volatileMax(base, levels));
        }
    }

    // Every melee hit at the Warden's core deals its max (wiki) - each
    // kind collapses to its hit count at the TOP of its damage range:
    // doubles land both boosted hits, the claw cascades sit at their
    // tier-1 cap, the voidwaker's uniform roll caps at 150%. The 1x1
    // core denies the halberd sweep its second hit - which is exactly
    // why the DDS dump beats a chally there (field report 2026-08-06).
    double coreMaxDamage(DpsResult base, MonsterStats monster) {
        int max = base.maxHit;
        switch (kind) {
            case HALBERD_SWEEP:
                return (monster.size > 1 ? 2.0 : 1.0)
                    * (int) (max * damageMultiplier);
            case DOUBLE_INDEPENDENT:
            case LINKED_DOUBLE:
                return 2.0 * (int) (max * damageMultiplier);
            case CLAWS:
                return 2.0 * max; // tier-1 cascade cap (max + halves/quarters)
            case CASCADE_CLAWS:
                return 1.5 * max; // tier-1 cap of the 1.25-mean uniform
            case MULTI_ROLL_TIERED:
                return 1.7 * max; // four successes, top of the k=4 range
            case VOIDWAKER:
                return 1.5 * max;
            case EXTRA_ATTACK:
                return base.expectedHit; // already max via calculate()
            case SINGLE:
            case FIXED_FRACTION:
            default:
                return (int) (max * damageMultiplier);
        }
    }

    // Dragon claws cascade: accuracy is rolled up to four times; the tier of
    // the successful roll sets the damage-roll modifier (100/75/50/25% of
    // max added to the minimum). Expected totals per tier: 1.5max-1,
    // 1.25max, max, 0.75max; a full miss averages ~1.
    static double clawsExpected(double p, int max) {
        double miss = 1 - p;
        return p * (1.5 * max - 1) + miss * p * (1.25 * max) + miss * miss * p * max
            + miss * miss * miss * p * (0.75 * max) + pow(miss, 4) * 1.0;
    }

    // Crimson kisten Brutal Swing: four independent accuracy rolls; with
    // k >= 1 successes damage is uniform in [(50+20k)%, (90+20k)%] of max
    // (mean (0.7 + 0.2k) * max); zero successes deal nothing.
    static double multiRollTieredExpected(double p, int max) {
        // Binomial coefficients C(4, k) for k = 0..4.
        int[] choose = {1, 4, 6, 4, 1};
        double expected = 0;
        for (int k = 1; k <= 4; k++) {
            expected += choose[k] * pow(p, k) * pow(1 - p, 4 - k)
                * (0.7 + 0.2 * k) * max;
        }
        return expected;
    }

    // Burning claws cascade: the first successful roll (of up to three)
    // sets the tier - uniform damage with means 1.25/1.00/0.75 of max; a
    // full miss deals 0/1/2 with 20/40/40% odds (expected 1.2). The burn
    // damage-over-time is described in the note, not counted here.
    static double cascadeClawsExpected(double p, int max) {
        double miss = 1 - p;
        return p * (1.25 * max) + miss * p * (1.00 * max) + miss * miss * p * (0.75 * max) + miss * miss * miss * 1.2;
    }

    // Dark bow: two hits, each rolled 0..boostedMax then clamped [min, 48].
    static double darkBowExpected(double p, int max, boolean dragonArrows) {
        int boosted = (int) (max * (dragonArrows ? 1.5 : 1.3));
        int min = dragonArrows ? 8 : 5;
        double sum = 0;
        for (int d = 0; d <= boosted; d++) sum += min(DARK_BOW_CAP, max(min, d));
        return 2 * p * (sum / (boosted + 1));
    }

    static boolean usesDragonArrows(DpsResult base) {
        GearItem ammo = base.loadout.get(GearSlot.AMMO);
        return ammo != null && ammo.nameLower.startsWith("dragon arrow");
    }

    // Snapshot max hit ignores prayers and void:
    // floor(0.5 + (visible ranged + 10) * (ammo ranged str + 64) / 640).
    static int snapshotMax(DpsResult base, PlayerLevels levels) {
        GearItem ammo = base.loadout.get(GearSlot.AMMO);
        int ammoStrength = ammo == null ? 0 : ammo.bonuses.rangedStrength;
        return (int) floor(0.5 + (levels.ranged + 10) * (ammoStrength + 64) / 640.0);
    }

    // Volatile spec max: spell max is min(floor(58 * level / 99) + 1, 58)
    // - 58 from level 98 up - then the loadout's magic damage bonus
    // (tenths of a percent) scales it. Wiki anchors: level 84 gives spell
    // max 50 (57 with the staff's 15% bonus); level 99 gives 58 (66).
    static int volatileMax(DpsResult base, PlayerLevels levels) {
        int spellMax = min((int) floor(58.0 * levels.magic / 99.0) + 1, 58);
        double gearBonus = 1 + base.loadout.bonuses.magicDamage / 1000.0;
        return (int) floor(spellMax * gearBonus);
    }

    static double mean(int maxHit) { return maxHit / 2.0; }

    // Ticks a spec spends beyond its own cast: the tome's follow-ups.
    public double extraTicks(DpsResult base) { return kind == Kind.BUFFED_FOLLOWUPS ? 6 * landChance(base) : 0; }

    // Chance this spec lands a damaging hit, from the base result's rolls.
    public double landChance(DpsResult base) {
        if (kind == Kind.VOIDWAKER) return 1.0;
        return RollMath.normalAccuracy( (long) (base.attackRoll * accuracyMultiplier), base.defenceRoll);
    }

    // The monster's Defence level after ONE successful use of this spec, or
    // the unchanged level for non-drain specs. BGS drains by damage dealt.
    public int drainedDefence(int currentDefence, double specExpectedDamage) {
        if (drainsByDamage) return max(0, currentDefence - (int) specExpectedDamage);
        if (defenceDrainFraction > 0) return currentDefence - (int) (currentDefence * defenceDrainFraction);
        return currentDefence;
    }

    // Monster-aware drain: the Division spec measures its DEFENCE drain
    // off the target's MAGIC level, so it shines exactly where a DWH
    // cannot reach (the wiki's example: Zulrah's 300 Magic).
    public int drainedDefence(MonsterStats monster, double specExpectedDamage) {
        if (magicDrainFraction > 0) return max(0, monster.defence - (int) (monster.magic * magicDrainFraction));
        return drainedDefence(monster.defence, specExpectedDamage);
    }

    public boolean drainsDefence() { return drainsByDamage || defenceDrainFraction > 0 || magicDrainFraction > 0; }
}
