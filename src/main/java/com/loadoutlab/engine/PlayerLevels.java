// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import net.runelite.api.Skill;

public final class PlayerLevels
{
	public static final PlayerLevels MAXED = new PlayerLevels(99, 99, 99, 99, 99, 99, 99);

	private final int attack;
	private final int strength;
	private final int defence;
	private final int ranged;
	private final int magic;
	private final int prayer;
	private final int hitpoints;

	public PlayerLevels(int attack, int strength, int defence, int ranged, int magic, int prayer, int hitpoints)
	{
		this.attack = clamp(attack);
		this.strength = clamp(strength);
		this.defence = clamp(defence);
		this.ranged = clamp(ranged);
		this.magic = clamp(magic);
		this.prayer = clamp(prayer);
		this.hitpoints = clamp(hitpoints);
	}

	public static PlayerLevels from(RequirementProfile profile)
	{
		if (profile == null)
		{
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

	public static PlayerLevels from(java.util.Map<Skill, Integer> levels)
	{
		if (levels == null || levels.isEmpty())
		{
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

	public PlayerLevels withBoosts(int attackBoost, int strengthBoost, int defenceBoost, int rangedBoost, int magicBoost)
	{
		return new PlayerLevels(
			attack + attackBoost,
			strength + strengthBoost,
			defence + defenceBoost,
			ranged + rangedBoost,
			magic + magicBoost,
			prayer,
			hitpoints);
	}

	/** Element-wise max - never assume a boost below what is already live. */
	public PlayerLevels max(PlayerLevels other)
	{
		if (other == null)
		{
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

	public PlayerLevels boosted(BoostProfile boostProfile, PlayerLevels liveCurrent)
	{
		return (boostProfile == null ? BoostProfile.NONE : boostProfile).apply(this, liveCurrent);
	}

	private static int clamp(int value)
	{
		return Math.max(1, Math.min(126, value));
	}

	public int getAttack()
	{
		return attack;
	}

	public int getStrength()
	{
		return strength;
	}

	public int getDefence()
	{
		return defence;
	}

	public int getRanged()
	{
		return ranged;
	}

	public int getMagic()
	{
		return magic;
	}

	public int getPrayer()
	{
		return prayer;
	}

	public int getHitpoints()
	{
		return hitpoints;
	}
}
