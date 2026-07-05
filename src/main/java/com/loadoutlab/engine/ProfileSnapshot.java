// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Prayer;

public final class ProfileSnapshot
{
	public static final ProfileSnapshot MAXED = new ProfileSnapshot(
		RequirementProfile.MAXED,
		PlayerLevels.MAXED,
		PlayerLevels.MAXED,
		Collections.emptySet(),
		"Maxed assumptions");

	private final RequirementProfile requirements;
	private final PlayerLevels baseLevels;
	private final PlayerLevels currentLevels;
	private final Set<Prayer> activePrayers;
	private final String source;

	public ProfileSnapshot(
		RequirementProfile requirements,
		PlayerLevels baseLevels,
		PlayerLevels currentLevels,
		Set<Prayer> activePrayers,
		String source)
	{
		this.requirements = requirements == null ? RequirementProfile.MAXED : requirements;
		this.baseLevels = baseLevels == null ? PlayerLevels.MAXED : baseLevels;
		this.currentLevels = currentLevels == null ? this.baseLevels : currentLevels;
		this.activePrayers = activePrayers == null || activePrayers.isEmpty()
			? Collections.emptySet()
			: Collections.unmodifiableSet(EnumSet.copyOf(activePrayers));
		this.source = source == null ? "" : source;
	}

	public RequirementProfile getRequirements()
	{
		return requirements;
	}

	public PlayerLevels getBaseLevels()
	{
		return baseLevels;
	}

	public PlayerLevels getCurrentLevels()
	{
		return currentLevels;
	}

	public Set<Prayer> getActivePrayers()
	{
		return activePrayers;
	}

	public String getSource()
	{
		return source;
	}
}
