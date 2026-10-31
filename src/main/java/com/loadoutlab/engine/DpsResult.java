// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;
import lombok.*;
import java.util.List;

@Getter
public class DpsResult {
public final Loadout loadout;
public final double dps;
// DPS net of the boss's heal off this set - the dps where nothing
// heals; what every ranking reads (ISS-01M6415DTE84ZZ45C4YD3MZRBJ).
@With
public final double net;
public final double accuracy;
public final double expectedHit;
public final int maxHit;
public final int attackSpeed;
public final String attackType;
final long attackRoll;
final long defenceRoll;
@With
public final int purchaseCost;
final String spellName;
// Conditional bonuses the calculator actually counted for this set
// (salve, wilderness weapon, crystal set...) - user assurance that
// the situational math is happening.
@With
public final List<String> countedBonuses;
// True when the optimizer could not satisfy a dragonfire shield
// constraint (no protective shield in the pool) and fell back to the
// unconstrained set - the panel must say "assumes a super antifire".
@With
public final boolean antifireAssumed;
// The damage per attack (HitDist), built for a shown card's kill time only.
@With
public final double[] hitDist;

DpsResult( Loadout loadout, double dps, double accuracy, double expectedHit, int maxHit, int attackSpeed,
    String attackType, long attackRoll, long defenceRoll) {
    this(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType, attackRoll, defenceRoll, loadout == null ? 0 : loadout.cost, "");
}

DpsResult( Loadout loadout, double dps, double accuracy, double expectedHit, int maxHit, int attackSpeed,
    String attackType, long attackRoll, long defenceRoll, int purchaseCost, String spellName) {
    this(loadout, dps, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType,
        attackRoll, defenceRoll, purchaseCost, spellName, List.of(), false, null);
}

DpsResult( Loadout loadout, double dps, double net, double accuracy, double expectedHit, int maxHit, int attackSpeed,
    String attackType, long attackRoll, long defenceRoll, int purchaseCost, String spellName,
    List<String> countedBonuses, boolean antifireAssumed, double[] hitDist) {
    this.hitDist = hitDist;
    this.loadout = loadout;
    this.dps = dps;
    this.net = net;
    this.accuracy = accuracy;
    this.expectedHit = expectedHit;
    this.maxHit = maxHit;
    this.attackSpeed = attackSpeed;
    this.attackType = attackType;
    this.attackRoll = attackRoll;
    this.defenceRoll = defenceRoll;
    this.purchaseCost = Math.max(0, purchaseCost);
    this.spellName = spellName == null ? "" : spellName;
    this.countedBonuses = countedBonuses == null ? List.of() : countedBonuses;
    this.antifireAssumed = antifireAssumed;
}


// The higher-NET-dps of two nullable results; the first wins ties. The
// one comparison rule for "keep the better candidate" - previously
// duplicated by DpsCalculator and LoadoutOptimizer.
public static DpsResult better(DpsResult first, DpsResult second) {
    if (second == null) return first;
    if (first == null || second.net > first.net) return second;
    return first;
}

// Copy with a reshaped hit model - the per-monster overrides in
// DpsCalculator.calculate() (Zulrah cap, vent one-shot, nibbler trio,
// Salarin flat, Verac proc) change only these four numbers and the
// distribution behind them.
DpsResult withHitModel(double dps, double accuracy, double expectedHit, int maxHit, double[] dist) {
    return new DpsResult(loadout, dps, dps, accuracy, expectedHit, maxHit, attackSpeed,
        attackType, attackRoll, defenceRoll, purchaseCost, spellName, countedBonuses, antifireAssumed, dist);
}

// The autocast spell, or null for powered staves / non-magic - the
// calculator passes "" internally for the powered path, which made
// null-keyed consumers (the panel's built-in-spell line) miss.
public String getSpellName() { return spellName == null || spellName.isEmpty() ? null : spellName; }
}
