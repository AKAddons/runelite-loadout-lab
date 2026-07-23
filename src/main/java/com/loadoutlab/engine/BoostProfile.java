// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

/**
 * A potion/prayer-free stat boost applied on top of the base levels.
 *
 * <p>Every non-live profile is the same shape - floor(flat + level * factor)
 * added to a fixed set of skills - so each constant just carries its flat
 * term, its factor, and which skills it touches as letters over the alphabet
 * a(ttack) s(trength) d(efence) r(anged) m(agic). Magic potion's flat +4 is
 * encoded as factor 0.0, which makes floor(4 + level * 0.0) exactly 4.
 */
public enum BoostProfile
{
	NONE("No boosts", 0, 0, ""),
	LIVE_CURRENT("Current boosted levels", 0, 0, ""),
	// Label deliberately avoids " + " - the assumes chips split on it.
	F2P_COMBAT("Attack & strength potions", 3, 0.10, "as"),
	SUPER_COMBAT("Super combat", 5, 0.15, "asd"),
	// Divine variants boost identically; they hold the boost at ceiling
	// for 5 minutes instead of decaying, so they are the preferred assumption.
	DIVINE_SUPER_COMBAT("Divine super combat", 5, 0.15, "asd"),
	RANGING("Ranging potion", 4, 0.10, "r"),
	DIVINE_RANGING("Divine ranging potion", 4, 0.10, "r"),
	SUPER_RANGING("Super ranging", 5, 0.15, "r"),
	SATURATED_HEART("Saturated heart", 4, 0.10, "m"),
	IMBUED_HEART("Imbued heart", 1, 0.10, "m"),
	MAGIC("Magic potion", 4, 0.0, "m"),
	DIVINE_MAGIC("Divine magic potion", 4, 0.0, "m"),
	SUPER_MAGIC("Super magic", 5, 0.15, "m"),
	OVERLOAD("Overload", 5, 0.13, "asdrm"),
	OVERLOAD_PLUS("Overload (+)", 6, 0.16, "asdrm"),
	SMELLING_SALTS("Smelling salts", 11, 0.16, "asdrm");

	private final String label;
	private final int flat;
	private final double factor;
	/** Which skills this profile boosts, as letters from "asdrm". */
	private final String skills;

	BoostProfile(String label, int flat, double factor, String skills)
	{
		this.label = label;
		this.flat = flat;
		this.factor = factor;
		this.skills = skills;
	}

	public PlayerLevels apply(PlayerLevels base, PlayerLevels current)
	{
		PlayerLevels source = base == null ? PlayerLevels.MAXED : base;
		if (this == LIVE_CURRENT)
		{
			return current == null ? source : current;
		}
		// NONE boosts nothing and must hand back the SAME instance: the
		// general path below always allocates a fresh PlayerLevels.
		if (skills.isEmpty())
		{
			return source;
		}
		return source.withBoosts(
			boostIf('a', source.getAttack()),
			boostIf('s', source.getStrength()),
			boostIf('d', source.getDefence()),
			boostIf('r', source.getRanged()),
			boostIf('m', source.getMagic()));
	}

	/** True when this profile boosts the skill letter (from "asdrm") - the
	 * assume-chip picker filters style-relevant boosts with it. */
	public boolean boosts(char skill)
	{
		return skills.indexOf(skill) >= 0;
	}

	/** floor(flat + level * factor), or 0 when this profile skips the skill. */
	private int boostIf(char skill, int level)
	{
		return skills.indexOf(skill) < 0 ? 0 : (int) Math.floor(flat + level * factor);
	}

	@Override
	public String toString()
	{
		return label;
	}
}
