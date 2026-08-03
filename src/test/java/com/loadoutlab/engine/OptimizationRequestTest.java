package com.loadoutlab.engine;

import org.junit.Assert;
import org.junit.Test;

/**
 * Guards the null-normalisation the with- helpers do. The helpers clone the
 * request rather than routing through a normalising constructor, so each
 * nullable-argument helper has to normalise at its own call site - this test
 * is what catches a new helper that forgets.
 */
public class OptimizationRequestTest
{
	private static OptimizationRequest base()
	{
		return TestRequests.of(
			null, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, 1);
	}

	@Test
	public void withExcludedItemsNullYieldsAnEmptySetNotNull()
	{
		OptimizationRequest r = base().withExcludedItems(null);
		Assert.assertNotNull(r.getExcludedItems());
		Assert.assertTrue(r.getExcludedItems().isEmpty());

		// isExcluded would NPE if the field had been left null.
		Assert.assertFalse(r.isExcluded(4151));
	}

	@Test
	public void theOtherNullableWithersNormaliseToo()
	{
		OptimizationRequest r = base()
			.withDreamItems(null)
			.withProtectOnlyItems(null)
			.withPinnedItems(null)
			.withSpellbookLock(null);
		// No getter for the dream set - isDream would NPE if it were null.
		Assert.assertFalse(r.isDream(4151));
		Assert.assertTrue(r.getProtectOnlyItems().isEmpty());
		Assert.assertTrue(r.getPinnedItems().isEmpty());
		Assert.assertEquals("", r.getSpellbookLock());
	}
}
