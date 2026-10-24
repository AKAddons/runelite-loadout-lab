// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.Getter;
import lombok.ToString;
import net.runelite.api.Skill;
import java.util.Map;

/** The seven combat levels; toString() is the optimizer's cache-key form. */
@ToString
public final class PlayerLevels {
	public static final PlayerLevels MAXED = new PlayerLevels(99, 99, 99, 99, 99, 99, 99);

	@Getter
	private final int attack;
	@Getter
	private final int strength;
	@Getter
	private final int defence;
	@Getter
	private final int ranged;
	@Getter
	private final int magic;
	@Getter
	private final int prayer;
	@Getter
	private final int hitpoints;

	public PlayerLevels(int attack, int strength, int defence, int ranged, int magic, int prayer, int hitpoints) {
		this.attack = clamp(attack);
		this.strength = clamp(strength);
		this.defence = clamp(defence);
		this.ranged = clamp(ranged);
		this.magic = clamp(magic);
		this.prayer = clamp(prayer);
		this.hitpoints = clamp(hitpoints);
	}

	public static PlayerLevels from(RequirementProfile profile) {
		if (profile == null) {
			return MAXED;
		}
		return new PlayerLevels(
			profile.level(Skill.ATTACK),
			profile.level(Skill.STRENGTH),
			profile.level(Skill.DEFENCE),
			profile.level(Skill.RANGED),
			profile.level(Skill.MAGIC),
			profile.level(Skill.PRAYER),
			profile.level(Skill.HITPOINTS));
	}

	public static PlayerLevels from(Map<Skill, Integer> levels) {
		if (levels == null || levels.isEmpty()) {
			return MAXED;
		}
		return new PlayerLevels(
			levels.getOrDefault(Skill.ATTACK, 1),
			levels.getOrDefault(Skill.STRENGTH, 1),
			levels.getOrDefault(Skill.DEFENCE, 1),
			levels.getOrDefault(Skill.RANGED, 1),
			levels.getOrDefault(Skill.MAGIC, 1),
			levels.getOrDefault(Skill.PRAYER, 1),
			levels.getOrDefault(Skill.HITPOINTS, 10));
	}

	public PlayerLevels withBoosts(int attackBoost, int strengthBoost, int defenceBoost, int rangedBoost, int magicBoost) {
		return new PlayerLevels(
			attack + attackBoost,
			strength + strengthBoost,
			defence + defenceBoost,
			ranged + rangedBoost,
			magic + magicBoost,
			prayer,
			hitpoints);
	}

	/** These levels with each named sim in place of your own (seven, in
	 * attack/strength/defence/ranged/magic/prayer/hitpoints order, 0 =
	 * yours): the stat sim (ISS-01M5QY13K6YHTYDW1CCYD30CSY). */
	public PlayerLevels overridden(int[] sims) {
		if (sims == null || sims.length < 7) {
			return this;
		}
		int[] mine = {attack, strength, defence, ranged, magic, prayer, hitpoints};
		for (int i = 0; i < mine.length; i++) {
			if (sims[i] > 0) {
				mine[i] = sims[i];
			}
		}
		return new PlayerLevels(mine[0], mine[1], mine[2], mine[3], mine[4], mine[5], mine[6]);
	}

	/** Element-wise max - never assume a boost below what is already live. */
	public PlayerLevels max(PlayerLevels other) {
		if (other == null) {
			return this;
		}
		return new PlayerLevels(
			Math.max(attack, other.attack),
			Math.max(strength, other.strength),
			Math.max(defence, other.defence),
			Math.max(ranged, other.ranged),
			Math.max(magic, other.magic),
			Math.max(prayer, other.prayer),
			Math.max(hitpoints, other.hitpoints));
	}

	public PlayerLevels boosted(BoostProfile boostProfile, PlayerLevels liveCurrent) {
		return (boostProfile == null ? BoostProfile.NONE : boostProfile).apply(this, liveCurrent);
	}

	private static int clamp(int value) {
		return Math.max(1, Math.min(126, value));
	}
}
