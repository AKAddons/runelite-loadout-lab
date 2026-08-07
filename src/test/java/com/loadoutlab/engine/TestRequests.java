package com.loadoutlab.engine;

import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;

/**
 * Test-only factory for the requirement-profile-less request shape. This
 * used to be an 11-arg convenience constructor on OptimizationRequest whose
 * only job was to supply {@link RequirementProfile#MAXED}, but no main-source
 * caller ever used it (OptimizerService always passes a profile explicitly),
 * so it lived on only for the tests while counting against the Plugin Hub's
 * main-source token cap. Moved here, where test sources are free.
 */
public final class TestRequests
{
	private TestRequests()
	{
	}

	/** A request with the requirement profile defaulted to MAXED. */
	public static OptimizationRequest of(
		MonsterStats monster,
		CombatStyle style,
		PlayerLevels levels,
		PrayerBonuses prayers,
		SpellStats spell,
		int budget,
		CandidateMode candidateMode,
		boolean includeUntradeables,
		boolean onSlayerTask,
		OwnedItems ownedItems,
		int resultLimit)
	{
		return new OptimizationRequest(
			monster,
			style,
			levels,
			prayers,
			spell,
			budget,
			candidateMode,
			includeUntradeables,
			onSlayerTask,
			ownedItems,
			RequirementProfile.MAXED,
			resultLimit);
	}
}
