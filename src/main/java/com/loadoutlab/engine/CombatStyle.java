// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.Getter;

public enum CombatStyle
{
	ANY("Any"),
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic");

	@Getter
	private final String label;

	CombatStyle(String label)
	{
		this.label = label;
	}

	public static CombatStyle[] concreteValues()
	{
		return new CombatStyle[]{MELEE, RANGED, MAGIC};
	}

	@Override
	public String toString()
	{
		return label;
	}
}
