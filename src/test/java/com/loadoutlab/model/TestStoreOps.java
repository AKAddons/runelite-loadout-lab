package com.loadoutlab.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A do-nothing StoreOps for engine tests - the page decoration that
 * attaches notes, pins and utility runes only runs when stores are
 * wired, so a test without one silently loses those facts.
 *
 * <p>{@code acceptedToggles} caps how many store TOGGLES succeed; past
 * it they refuse, which is how a revert is made to fail on purpose.
 */
class TestStoreOps implements CommandEngine.StoreOps
{
	private final int acceptedToggles;
	private int toggles;

	TestStoreOps()
	{
		this(Integer.MAX_VALUE);
	}

	TestStoreOps(int acceptedToggles)
	{
		this.acceptedToggles = acceptedToggles;
	}

	public boolean toggleExclusion(int itemId)
	{
		return toggles++ < acceptedToggles;
	}

	public boolean toggleSim(int itemId)
	{
		return toggles++ < acceptedToggles;
	}

	public void toggleAlwaysFilter(int itemId)
	{
	}

	public void setSupplyDefault(String category, String choice)
	{
	}

	public void pin(int monsterId, String slot, int itemId)
	{
	}

	public void unpin(int monsterId, String slot)
	{
	}

	public void showInBank(java.util.Set<Integer> itemIds)
	{
	}

	public void filterBank(java.util.Set<Integer> itemIds, int[] layout)
	{
	}

	public String pinnedSpell(int monsterId)
	{
		return null;
	}

	public void setPinnedSpell(int monsterId, String spellName)
	{
	}

	public int pinnedSpec(int monsterId)
	{
		return -1;
	}

	public void setPinnedSpec(int monsterId, int itemId)
	{
	}

	public String note(int monsterId)
	{
		return null;
	}

	public void setNote(int monsterId, String note)
	{
	}

	public void excludeForMob(int monsterId, String scope, int itemId)
	{
	}

	public void simForMob(int monsterId, int itemId)
	{
	}

	public List<Map<String, Object>> mobExclusions(int monsterId)
	{
		return Collections.emptyList();
	}

	public List<Map<String, Object>> mobSims(int monsterId)
	{
		return Collections.emptyList();
	}

	public List<Map<String, Object>> mobFilters(int monsterId)
	{
		return Collections.emptyList();
	}

	public void removeMobExclusion(int monsterId, String scope, int itemId)
	{
	}

	public void removeMobSim(int monsterId, int itemId)
	{
	}

	public void addMobFilter(int monsterId, int itemId)
	{
	}

	public void removeMobFilter(int monsterId, String scope, int itemId)
	{
	}

	public Map<String, String> supplyOverrides(int profileId)
	{
		return Collections.emptyMap();
	}

	public void setSupplyOverride(int profileId, String category, String choice)
	{
	}
}
