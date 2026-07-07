package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.StatBlock;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class PvpRiskTest
{
	private static GearItem item(int id, GearSlot slot, boolean tradeable, int price)
	{
		return new GearItem(id, "Item" + id, "", slot, "", 0, false, true,
			tradeable, true, price, StatBlock.ZERO, StatBlock.ZERO, StatBlock.ZERO, null);
	}

	private static Loadout worn(GearItem... items)
	{
		Map<GearSlot, GearItem> gear = new HashMap<>();
		for (GearItem g : items)
		{
			gear.put(g.getSlot(), g);
		}
		return new Loadout(gear);
	}

	@Test
	public void tradeablesBeyondTheKeptSlotsAreTheRisk()
	{
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			item(2, GearSlot.BODY, true, 80),
			item(3, GearSlot.LEGS, true, 60),
			item(4, GearSlot.HEAD, true, 40),
			item(5, GearSlot.FEET, true, 20));
		PvpRisk.Assessment three = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(60, three.riskGp);
		Assert.assertEquals(3, three.kept.size());
		Assert.assertEquals(2, three.lost.size());
		Assert.assertEquals(20, PvpRisk.assess(loadout, null, 4).riskGp);
	}

	@Test
	public void untradeablesAreKeptOutsideTheSlotRanking()
	{
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			item(2, GearSlot.BODY, false, 999_999),
			item(3, GearSlot.LEGS, false, 999_999));
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(0, risk.riskGp);
		Assert.assertEquals(1, risk.kept.size());
	}

	@Test
	public void theCarriedSpecWeaponCompetesForKeptSlotsAndCanDisplaceCheaperGear()
	{
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			item(2, GearSlot.BODY, true, 80),
			item(3, GearSlot.LEGS, true, 60));
		// Without the spec weapon: everything kept.
		Assert.assertEquals(0, PvpRisk.assess(loadout, null, 3).riskGp);
		// A pricey carried spec weapon takes a kept slot; the 60 is lost.
		GearItem spec = item(9, GearSlot.WEAPON, true, 500);
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, spec, 3);
		Assert.assertEquals(60, risk.riskGp);
		Assert.assertEquals("Item9", risk.kept.get(0).getName());
	}

	@Test
	public void gpFormattingReadsLikeAPlayerWouldSayIt()
	{
		Assert.assertEquals("950", PvpRisk.formatGp(950));
		Assert.assertEquals("820k", PvpRisk.formatGp(820_400));
		Assert.assertEquals("45.3M", PvpRisk.formatGp(45_300_000));
		Assert.assertEquals("1.20B", PvpRisk.formatGp(1_200_000_000L));
	}
}
