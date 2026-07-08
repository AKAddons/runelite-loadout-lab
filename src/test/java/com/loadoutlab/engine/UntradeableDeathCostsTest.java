package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.StatBlock;
import org.junit.Assert;
import org.junit.Test;

public class UntradeableDeathCostsTest
{
	private static final StatBlock COMBAT = new StatBlock(0, 0, 0, 0, 0, 4, 0, 0, 2);

	private static GearItem gear(String name, boolean tradeable, StatBlock bonuses)
	{
		return new GearItem(1, name, "", GearSlot.CAPE, "", 0, false, true,
			tradeable, true, null, StatBlock.ZERO, StatBlock.ZERO, bonuses, null);
	}

	@Test
	public void nullAndTradeableItemsCostNothingHere()
	{
		Assert.assertEquals(0, UntradeableDeathCosts.costFor(null));
		// Tradeable losses are priced by the kept-slot ranking instead.
		Assert.assertEquals(0, UntradeableDeathCosts.costFor(gear("Fire cape", true, COMBAT)));
	}

	@Test
	public void trouverClassItemsCostTheFlatMangleFee()
	{
		Assert.assertEquals(500_000, UntradeableDeathCosts.costFor(gear("Infernal cape", false, COMBAT)));
		Assert.assertEquals(500_000, UntradeableDeathCosts.costFor(gear("Elite void top", false, COMBAT)));
		Assert.assertEquals(500_000, UntradeableDeathCosts.costFor(gear("Dizana's quiver", false, COMBAT)));
		Assert.assertEquals(3, UntradeableDeathCosts.categoryFor(gear("Infernal cape", false, COMBAT)));
	}

	@Test
	public void breakableItemsCostTheirOwnWikiRepairFee()
	{
		Assert.assertEquals(150_000, UntradeableDeathCosts.costFor(gear("Fire cape", false, COMBAT)));
		Assert.assertEquals(96_000, UntradeableDeathCosts.costFor(gear("Imbued saradomin cape", false, COMBAT)));
		Assert.assertEquals(35_000, UntradeableDeathCosts.costFor(gear("Rune defender", false, COMBAT)));
		Assert.assertEquals(600_000, UntradeableDeathCosts.costFor(gear("Avernic defender", false, COMBAT)));
		Assert.assertEquals(2, UntradeableDeathCosts.categoryFor(gear("Fire cape", false, COMBAT)));
	}

	@Test
	public void componentDropItemsArePricedAtTheComponentTheKillerGets()
	{
		Assert.assertEquals(6_000_000, UntradeableDeathCosts.costFor(gear("Crystal body", false, COMBAT)));
		Assert.assertEquals(1_500_000, UntradeableDeathCosts.costFor(gear("Slayer helmet (i)", false, COMBAT)));
		Assert.assertEquals(3_000_000, UntradeableDeathCosts.costFor(gear("Berserker ring (i)", false, COMBAT)));
	}

	@Test
	public void cheapReclaimsAndFreeUntradeablesStayCheap()
	{
		// Trivial reclaims (diary rewards, 200gp at Perdu) are FREE TIER:
		// noise that must not evict them from a zero-risk-cap set.
		Assert.assertEquals(0, UntradeableDeathCosts.costFor(gear("Ardougne cloak 4", false, COMBAT)));
		Assert.assertEquals(0, UntradeableDeathCosts.costFor(gear("Rada's blessing 4", false, COMBAT)));
		Assert.assertEquals(0, UntradeableDeathCosts.costFor(gear("Salve amulet(ei)", false, COMBAT)));
		Assert.assertEquals(130_000, UntradeableDeathCosts.costFor(gear("Barrows gloves", false, COMBAT)));
	}

	@Test
	public void nameMatchingIgnoresCase()
	{
		Assert.assertEquals(150_000, UntradeableDeathCosts.costFor(gear("FIRE CAPE", false, COMBAT)));
	}

	@Test
	public void unknownCombatUntradeablesFallBackToTheConservativeDefault()
	{
		Assert.assertEquals(UntradeableDeathCosts.UNKNOWN_COMBAT_UNTRADEABLE_GP,
			UntradeableDeathCosts.costFor(gear("Some future combat relic", false, COMBAT)));
		Assert.assertEquals(0, UntradeableDeathCosts.categoryFor(gear("Some future combat relic", false, COMBAT)));
	}

	@Test
	public void unknownStatlessUntradeablesCostNothing()
	{
		Assert.assertEquals(0, UntradeableDeathCosts.costFor(gear("Party hood", false, StatBlock.ZERO)));
	}
}
