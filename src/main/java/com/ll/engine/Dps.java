// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.ll.engine;
import lombok.*;
import java.util.List;

@Getter
public class Dps {
public Kit loadout;
public double dps;
// DPS net of the boss's heal off this set - the dps where nothing
// heals; what every ranking reads (ISS-01M6415DTE84ZZ45C4YD3MZRBJ).
@With
public double net;
public double accuracy;
public double eh;
public int maxHit;
public int attackSpeed;
public String attackType;
long ar;
long dr;
@With
public int purchaseCost;
String spellName;
// Conditional bonuses the calculator actually counted for this set
// (salve, wilderness weapon, crystal set...) - user assurance that
// the situational math is happening.
@With
public List<String> countedBonuses;
// True when the optimizer could not satisfy a dragonfire shield
// constraint (no protective shield in the pool) and fell back to the
// unconstrained set - the panel must say "assumes a super antifire".
@With
public boolean antifireAssumed;
// The damage per attack (Dist), built for a shown card's kill time only.
@With
public double[] hd;

Dps( Kit loadout, double dps, double accuracy, double eh, int maxHit, int attackSpeed,
    String attackType, long ar, long dr) {
    this(loadout, dps, accuracy, eh, maxHit, attackSpeed, attackType, ar, dr, loadout == null ? 0 : loadout.cost, "");
}

Dps( Kit loadout, double dps, double accuracy, double eh, int maxHit, int attackSpeed,
    String attackType, long ar, long dr, int purchaseCost, String spellName) {
    this(loadout, dps, dps, accuracy, eh, maxHit, attackSpeed, attackType,
        ar, dr, purchaseCost, spellName, List.of(), false, null);
}

Dps( Kit loadout, double dps, double net, double accuracy, double eh, int maxHit, int attackSpeed,
    String attackType, long ar, long dr, int purchaseCost, String spellName,
    List<String> countedBonuses, boolean antifireAssumed, double[] hd) {
    this.hd = hd;
    this.loadout = loadout;
    this.dps = dps;
    this.net = net;
    this.accuracy = accuracy;
    this.eh = eh;
    this.maxHit = maxHit;
    this.attackSpeed = attackSpeed;
    this.attackType = attackType;
    this.ar = ar;
    this.dr = dr;
    this.purchaseCost = Math.max(0, purchaseCost);
    this.spellName = spellName == null ? "" : spellName;
    this.countedBonuses = countedBonuses == null ? List.of() : countedBonuses;
    this.antifireAssumed = antifireAssumed;
}


// The higher-NET-dps of two nullable results; the first wins ties. The
// one comparison rule for "keep the better candidate" - previously
// duplicated by Calc and LoadoutOptimizer.
public static Dps better(Dps first, Dps second) {
    if (second == null) return first;
    if (first == null || second.net > first.net) return second;
    return first;
}

// Copy with a reshaped hit model - the per-monster overrides in
// Calc.calculate() (Zulrah cap, vent one-shot, nibbler trio,
// Salarin flat, Verac proc) change only these four numbers and the
// distribution behind them.
Dps withHit(double dps, double accuracy, double eh, int maxHit, double[] dist) {
    return new Dps(loadout, dps, dps, accuracy, eh, maxHit, attackSpeed,
        attackType, ar, dr, purchaseCost, spellName, countedBonuses, antifireAssumed, dist);
}

// The autocast spell, or null for powered staves / non-magic - the
// calculator passes "" internally for the powered path, which made
// null-keyed consumers (the panel's built-in-spell line) miss.
public String getSpellName() { return spellName == null || spellName.isEmpty() ? null : spellName; }
}
