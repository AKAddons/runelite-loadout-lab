// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.ll.engine;

import lombok.*;
import net.runelite.api.Skill;
import java.util.Map;
import static net.runelite.api.Skill.*;

// The seven combat levels; toString() is the optimizer's cache-key form.
@ToString
@Getter
public class Levels {
public static final Levels MAXED = new Levels(99, 99, 99, 99, 99, 99, 99);

public int attack;
public int strength;
public int defence;
public int ranged;
public int magic;
public int prayer;
public int hitpoints;

public Levels(int attack, int strength, int defence, int ranged, int magic, int prayer, int hitpoints) {
    this.attack = clamp(attack);
    this.strength = clamp(strength);
    this.defence = clamp(defence);
    this.ranged = clamp(ranged);
    this.magic = clamp(magic);
    this.prayer = clamp(prayer);
    this.hitpoints = clamp(hitpoints);
}

public static Levels from(RequirementProfile profile) {
    if (profile == null) return MAXED;
    return new Levels( profile.level(ATTACK), profile.level(STRENGTH), profile.level(DEFENCE),
        profile.level(RANGED), profile.level(MAGIC), profile.level(PRAYER), profile.level(HITPOINTS));
}

public static Levels from(Map<Skill, Integer> levels) {
    if (levels == null || levels.isEmpty()) return MAXED;
    return new Levels( levels.getOrDefault(ATTACK, 1), levels.getOrDefault(STRENGTH, 1),
        levels.getOrDefault(DEFENCE, 1), levels.getOrDefault(RANGED, 1), levels.getOrDefault(MAGIC, 1),
        levels.getOrDefault(PRAYER, 1), levels.getOrDefault(HITPOINTS, 10));
}

Levels withBoosts(int attackBoost, int strengthBoost, int defenceBoost, int rangedBoost, int magicBoost) {
    return new Levels( attack + attackBoost, strength + strengthBoost, defence + defenceBoost,
        ranged + rangedBoost, magic + magicBoost, prayer, hitpoints);
}

// These levels with each named sim in place of your own (seven, in
// attack/strength/defence/ranged/magic/prayer/hitpoints order, 0 =
// yours): the stat sim (ISS-01M5QY13K6YHTYDW1CCYD30CSY).
public Levels overridden(int[] sims) {
    if (sims == null || sims.length < 7) return this;
    int[] mine = {attack, strength, defence, ranged, magic, prayer, hitpoints};
    for (int i = 0; i < mine.length; i++) if (sims[i] > 0) mine[i] = sims[i];
    return new Levels(mine[0], mine[1], mine[2], mine[3], mine[4], mine[5], mine[6]);
}

// Element-wise max - never assume a boost below what is already live.
public Levels max(Levels other) {
    if (other == null) return this;
    return new Levels( Math.max(attack, other.attack), Math.max(strength, other.strength),
        Math.max(defence, other.defence), Math.max(ranged, other.ranged), Math.max(magic, other.magic),
        Math.max(prayer, other.prayer), Math.max(hitpoints, other.hitpoints));
}

public Levels boosted(BoostProfile boostProfile, Levels liveCurrent) {
    return (boostProfile == null ? BoostProfile.NONE : boostProfile).apply(this, liveCurrent);
}

static int clamp(int value) { return Math.max(1, Math.min(126, value)); }
}
