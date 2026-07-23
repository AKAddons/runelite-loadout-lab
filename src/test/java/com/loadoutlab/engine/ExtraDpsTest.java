package com.loadoutlab.engine;

import org.junit.Assert;
import org.junit.Test;

/**
 * Thrall display-dps math (field direction 2026-07-21): wiki-verified
 * tier values at their Magic boundaries.
 */
public class ExtraDpsTest
{
	@Test
	public void thrallTiersMatchTheWikiTableAtTheirBoundaries()
	{
		Assert.assertEquals(0.625, ExtraDps.thrallDps(99), 1e-9);
		Assert.assertEquals(0.625, ExtraDps.thrallDps(76), 1e-9);
		Assert.assertEquals(0.416, ExtraDps.thrallDps(75), 1e-9);
		Assert.assertEquals(0.416, ExtraDps.thrallDps(57), 1e-9);
		Assert.assertEquals(0.208, ExtraDps.thrallDps(56), 1e-9);
		Assert.assertEquals(0.208, ExtraDps.thrallDps(38), 1e-9);
		Assert.assertEquals(0, ExtraDps.thrallDps(37), 1e-9);
		Assert.assertEquals("Greater", ExtraDps.thrallTier(76));
		Assert.assertEquals("Superior", ExtraDps.thrallTier(60));
		Assert.assertEquals("Lesser", ExtraDps.thrallTier(38));
		Assert.assertNull(ExtraDps.thrallTier(20));
	}

}
