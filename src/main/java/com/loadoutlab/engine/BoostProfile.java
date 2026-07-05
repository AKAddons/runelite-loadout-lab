// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

public enum BoostProfile
{
	NONE("No boosts"),
	LIVE_CURRENT("Current boosted levels"),
	SUPER_COMBAT("Super combat"),
	RANGING("Ranging potion"),
	SUPER_RANGING("Super ranging"),
	SATURATED_HEART("Saturated heart"),
	IMBUED_HEART("Imbued heart"),
	MAGIC("Magic potion"),
	SUPER_MAGIC("Super magic"),
	OVERLOAD("Overload"),
	OVERLOAD_PLUS("Overload (+)"),
	SMELLING_SALTS("Smelling salts");

	private final String label;

	BoostProfile(String label)
	{
		this.label = label;
	}

	PlayerLevels apply(PlayerLevels base, PlayerLevels current)
	{
		PlayerLevels source = base == null ? PlayerLevels.MAXED : base;
		switch (this)
		{
			case LIVE_CURRENT:
				return current == null ? source : current;
			case SUPER_COMBAT:
				return source.withBoosts(
					boost(source.getAttack(), 5, 0.15),
					boost(source.getStrength(), 5, 0.15),
					boost(source.getDefence(), 5, 0.15),
					0,
					0);
			case RANGING:
				return source.withBoosts(0, 0, 0, boost(source.getRanged(), 4, 0.10), 0);
			case SUPER_RANGING:
				return source.withBoosts(0, 0, 0, boost(source.getRanged(), 5, 0.15), 0);
			case SATURATED_HEART:
				return source.withBoosts(0, 0, 0, 0, boost(source.getMagic(), 4, 0.10));
			case IMBUED_HEART:
				return source.withBoosts(0, 0, 0, 0, boost(source.getMagic(), 1, 0.10));
			case MAGIC:
				return source.withBoosts(0, 0, 0, 0, 4);
			case SUPER_MAGIC:
				return source.withBoosts(0, 0, 0, 0, boost(source.getMagic(), 5, 0.15));
			case OVERLOAD:
				return source.withBoosts(
					boost(source.getAttack(), 5, 0.13),
					boost(source.getStrength(), 5, 0.13),
					boost(source.getDefence(), 5, 0.13),
					boost(source.getRanged(), 5, 0.13),
					boost(source.getMagic(), 5, 0.13));
			case OVERLOAD_PLUS:
				return source.withBoosts(
					boost(source.getAttack(), 6, 0.16),
					boost(source.getStrength(), 6, 0.16),
					boost(source.getDefence(), 6, 0.16),
					boost(source.getRanged(), 6, 0.16),
					boost(source.getMagic(), 6, 0.16));
			case SMELLING_SALTS:
				return source.withBoosts(
					boost(source.getAttack(), 11, 0.16),
					boost(source.getStrength(), 11, 0.16),
					boost(source.getDefence(), 11, 0.16),
					boost(source.getRanged(), 11, 0.16),
					boost(source.getMagic(), 11, 0.16));
			case NONE:
			default:
				return source;
		}
	}

	private static int boost(int level, int base, double factor)
	{
		return (int) Math.floor(base + level * factor);
	}

	@Override
	public String toString()
	{
		return label;
	}
}
