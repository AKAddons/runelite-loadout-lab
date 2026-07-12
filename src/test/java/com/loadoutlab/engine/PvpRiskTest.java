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

	private static final StatBlock SOME_DEFENCE =
		new StatBlock(0, 0, 0, 0, 0, 0, 0, 0, 2);

	private static GearItem untradeable(int id, String name, GearSlot slot, StatBlock bonuses)
	{
		return new GearItem(id, name, "", slot, "", 0, false, true,
			false, true, null, StatBlock.ZERO, StatBlock.ZERO, bonuses, null);
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
	public void costFreeBreakersStillChargeSoTheUiNeverShowsThemAsKept()
	{
		// Salve amulet(ei): curated reclaim cost is 0 (free tomb re-obtain,
		// imbue points refunded) but it is category 2 - it BREAKS on a
		// wilderness death. It must appear in the charges (so the panel can
		// mark it) while adding nothing to the gp risk total.
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			untradeable(2, "salve amulet(ei)", GearSlot.NECK, SOME_DEFENCE));
		PvpRisk.Assessment fates = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(1, fates.untradeableCharges.size());
		Assert.assertEquals(2, fates.untradeableCharges.get(0).item.getId());
		Assert.assertEquals(0, fates.untradeableCharges.get(0).costGp);
		Assert.assertEquals(0, fates.riskGp);
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
	public void aTrouverClassUntradeableAddsTheFlat500kManglFeeToTheRisk()
	{
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			untradeable(2, "Infernal cape", GearSlot.CAPE, SOME_DEFENCE));
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(500_000L, risk.riskGp);
		Assert.assertEquals(1, risk.untradeableCharges.size());
		Assert.assertEquals("Infernal cape", risk.untradeableCharges.get(0).item.getName());
		Assert.assertEquals(500_000L, risk.untradeableCharges.get(0).costGp);
	}

	@Test
	public void aBreakableUntradeableAddsItsOwnWikiRepairFee()
	{
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			untradeable(2, "Fire cape", GearSlot.CAPE, SOME_DEFENCE),
			untradeable(3, "Rune defender", GearSlot.SHIELD, SOME_DEFENCE));
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(150_000L + 35_000L, risk.riskGp);
		// Biggest fee first, for the panel's itemised breakdown.
		Assert.assertEquals("Fire cape", risk.untradeableCharges.get(0).item.getName());
		Assert.assertEquals("Rune defender", risk.untradeableCharges.get(1).item.getName());
	}

	@Test
	public void untradeablesWithoutCombatStatsStillCostNothing()
	{
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			untradeable(2, "Graceful hood", GearSlot.HEAD, StatBlock.ZERO),
			untradeable(3, "Completely unknown cosmetic", GearSlot.CAPE, StatBlock.ZERO));
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(0, risk.riskGp);
		Assert.assertTrue(risk.untradeableCharges.isEmpty());
	}

	@Test
	public void anUncuratedCombatUntradeableDefaultsToTheConservative500k()
	{
		// Unknowns must not smuggle into "low-risk" sets for free.
		Loadout loadout = worn(
			untradeable(2, "Some future combat relic", GearSlot.BODY, SOME_DEFENCE));
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(UntradeableDeathCosts.UNKNOWN_COMBAT_UNTRADEABLE_GP, risk.riskGp);
	}

	@Test
	public void anUntradeableCarriedSpecWeaponAddsItsFeeToo()
	{
		Loadout loadout = worn(item(1, GearSlot.WEAPON, true, 100));
		GearItem spec = untradeable(9, "Void knight mace", GearSlot.WEAPON, SOME_DEFENCE);
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, spec, 3);
		Assert.assertEquals(20_000L, risk.riskGp);
		Assert.assertEquals("Void knight mace", risk.untradeableCharges.get(0).item.getName());
	}

	@Test
	public void aConvertClassUntradeableCompetesForProtectionAndIsFreeWhenItWins()
	{
		// Slayer helmet (converts to a 1.5M black mask) worn with two cheap
		// tradeables: the helm ranks FIRST into the kept slots - protected,
		// no drop, no fee. This is why wildy slayers count it as one of
		// their 3 protected items.
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100_000),
			item(2, GearSlot.BODY, true, 50_000),
			untradeable(3, "Slayer helmet (i)", GearSlot.HEAD, SOME_DEFENCE));
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(0, risk.riskGp);
		Assert.assertEquals("Slayer helmet (i)", risk.kept.get(0).getName());
		Assert.assertEquals(1_500_000L, risk.valueOf(risk.kept.get(0)));
		Assert.assertTrue(risk.untradeableCharges.isEmpty());
	}

	@Test
	public void aConvertClassUntradeableOutrankedByPricierItemsDropsAtItsComponentValue()
	{
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100_000_000),
			item(2, GearSlot.BODY, true, 50_000_000),
			item(4, GearSlot.LEGS, true, 20_000_000),
			untradeable(3, "Slayer helmet (i)", GearSlot.HEAD, SOME_DEFENCE));
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(1_500_000L, risk.riskGp);
		Assert.assertEquals("Slayer helmet (i)", risk.lost.get(0).getName());
	}

	@Test
	public void untradeableFeesStackOnTopOfTheTradeableLosses()
	{
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			item(2, GearSlot.BODY, true, 80),
			item(3, GearSlot.LEGS, true, 60),
			item(4, GearSlot.HEAD, true, 40),
			untradeable(5, "Fire cape", GearSlot.CAPE, SOME_DEFENCE));
		// Kept 3 -> the 40 is lost, plus the fire cape repair fee.
		PvpRisk.Assessment risk = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(40L + 150_000L, risk.riskGp);
		Assert.assertEquals(3, risk.kept.size());
		Assert.assertEquals(1, risk.lost.size());
	}

	@Test
	public void theLeanRiskGpMatchesTheFullAssessmentExactly()
	{
		// The beam calls riskGp() thousands of times per optimize; it must
		// be the same number assess() reports, for every item class at once.
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100_000),
			item(2, GearSlot.BODY, true, 80_000),
			item(3, GearSlot.LEGS, true, 60_000),
			item(4, GearSlot.RING, true, 40),
			untradeable(5, "Fire cape", GearSlot.CAPE, SOME_DEFENCE),
			untradeable(6, "Slayer helmet (i)", GearSlot.HEAD, SOME_DEFENCE),
			untradeable(7, "Rune defender", GearSlot.SHIELD, SOME_DEFENCE),
			untradeable(8, "Amulet of the damned", GearSlot.NECK, SOME_DEFENCE));
		GearItem spec = item(9, GearSlot.WEAPON, true, 500_000);
		for (GearItem carried : new GearItem[]{null, spec})
		{
			for (int kept : new int[]{0, 1, 3, 4, 10})
			{
				Assert.assertEquals(
					PvpRisk.assess(loadout, carried, kept).riskGp,
					PvpRisk.riskGp(loadout, carried, kept));
			}
		}
		// Empty set and the destroyed-on-death class too.
		Loadout empty = worn(item(1, GearSlot.WEAPON, true, 0));
		Assert.assertEquals(PvpRisk.assess(empty, null, 3).riskGp,
			PvpRisk.riskGp(empty, null, 3));
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
