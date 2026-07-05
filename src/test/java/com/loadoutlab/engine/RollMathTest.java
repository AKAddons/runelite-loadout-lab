// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import org.junit.Assert;
import org.junit.Test;

public class RollMathTest
{
	@Test
	public void normalAccuracyMatchesOsrsFormulaWhenAttackRollWins()
	{
		double accuracy = RollMath.normalAccuracy(20_000, 10_000);
		Assert.assertEquals(0.7499625018749062, accuracy, 1e-12);
	}

	@Test
	public void normalAccuracyMatchesOsrsFormulaWhenDefenceRollWins()
	{
		double accuracy = RollMath.normalAccuracy(10_000, 20_000);
		Assert.assertEquals(0.24998750062496874, accuracy, 1e-12);
	}

	@Test
	public void expectedHitIncludesAccurateZeroAsOne()
	{
		double expected = RollMath.normalExpectedHit(1.0, 10);
		Assert.assertEquals(5.090909090909091, expected, 1e-12);
	}

	@Test
	public void maxHitUsesStandardEffectiveFormula()
	{
		Assert.assertEquals(21, RollMath.maxHitFromEffective(107, 64));
	}
}
