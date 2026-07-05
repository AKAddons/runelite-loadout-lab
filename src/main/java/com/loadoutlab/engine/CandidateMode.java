// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

public enum CandidateMode
{
	BUDGET("Budget gear"),
	OWNED_OR_BUDGET("Owned + budget gear"),
	OWNED_ONLY("Owned only"),
	ALL_STANDARD("All standard gear");

	private final String label;

	CandidateMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
