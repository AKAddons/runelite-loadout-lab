package com.loadoutlab.engine;

import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class OwnedItemsTest
{
	private static OwnedItems of(Map<Integer, Integer> quantities)
	{
		return new OwnedItems(quantities, true);
	}

	@Test
	public void presenceFingerprintIgnoresQuantityChanges()
	{
		// The optimizer's answers depend only on owns() (quantity > 0), so
		// shooting arrows or scaling ether must NOT invalidate its cache.
		Map<Integer, Integer> before = new HashMap<>();
		before.put(884, 1200); // arrows
		before.put(4151, 1);
		Map<Integer, Integer> after = new HashMap<>(before);
		after.put(884, 3); // most of the stack shot away

		Assert.assertEquals(of(before).presenceFingerprint(), of(after).presenceFingerprint());
	}

	@Test
	public void presenceFingerprintChangesWhenOwnershipActuallyChanges()
	{
		Map<Integer, Integer> base = new HashMap<>();
		base.put(4151, 1);

		Map<Integer, Integer> gained = new HashMap<>(base);
		gained.put(11832, 1); // new item: 0 -> 1
		Assert.assertNotEquals(of(base).presenceFingerprint(), of(gained).presenceFingerprint());

		Map<Integer, Integer> lost = new HashMap<>(gained);
		lost.put(11832, 0); // lost to zero: 1 -> 0
		Assert.assertEquals("a zero-quantity row means NOT owned - same as absent",
			of(base).presenceFingerprint(), of(lost).presenceFingerprint());
		Assert.assertNotEquals(of(gained).presenceFingerprint(), of(lost).presenceFingerprint());
	}

	@Test
	public void presenceFingerprintMatchesOwnsSemanticsExactly()
	{
		// owns() is the contract: any two ledgers agreeing on owns() for
		// every id must fingerprint identically.
		Map<Integer, Integer> a = new HashMap<>();
		a.put(1, 5);
		a.put(2, 0);
		a.put(3, 1);
		Map<Integer, Integer> b = new HashMap<>();
		b.put(1, 1);
		b.put(3, 999);
		Assert.assertEquals(of(a).presenceFingerprint(), of(b).presenceFingerprint());
	}
}
