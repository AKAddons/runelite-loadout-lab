package com.ll.engine;

import com.ll.data.Monster;
import com.ll.data.Spell;

/**
 * Test-only factory for the requirement-profile-less request shape. This
 * used to be an 11-arg convenience constructor on Req whose
 * only job was to supply {@link RequirementProfile#MAXED}, but no main-source
 * caller ever used it (Service always passes a profile explicitly),
 * so it lived on only for the tests while counting against the Plugin Hub's
 * main-source token cap. Moved here, where test sources are free.
 */
public final class TestRequests {
	private TestRequests() {
	}

	/** A request with the requirement profile defaulted to MAXED. */
	public static Req of(
		Monster monster,
		Style style,
		Levels levels,
		PrayerBonuses prayers,
		Spell spell,
		int budget,
		CandidateMode candidateMode,
		boolean includeUntradeables,
		boolean onSlayerTask,
		Owned oi,
		int resultLimit) {
		return new Req(
			monster,
			style,
			levels,
			prayers,
			spell,
			budget,
			candidateMode,
			includeUntradeables,
			onSlayerTask,
			oi,
			RequirementProfile.MAXED,
			resultLimit);
	}
}
