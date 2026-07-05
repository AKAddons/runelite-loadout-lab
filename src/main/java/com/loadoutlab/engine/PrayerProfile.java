// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

public enum PrayerProfile
{
	NONE("No prayers"),
	BEST_AVAILABLE("Best available"),
	CURRENT_ACTIVE("Current active");

	private final String label;

	PrayerProfile(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
