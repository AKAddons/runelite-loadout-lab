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
	/** The retired risksRebuild() overloads: the friction-only case
	 * of risksUnprotected(), i.e. no protect-only flags. They had no
	 * production callers, so they live here as delegates instead. */
	private static boolean risksRebuild(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots)
	{
		return risksRebuild(loadout, carriedSpecWeapon, keptSlots, java.util.Collections.emptySet());
	}

	private static boolean risksRebuild(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots,
		java.util.Set<Integer> pinnedIds)
	{
		return PvpRisk.risksUnprotected(loadout, carriedSpecWeapon, keptSlots, pinnedIds,
			java.util.Collections.emptySet());
	}

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
	public void avariceSkullsTheWearerSoDefaultProtectionVanishes()
	{
		// The amulet of avarice keeps the wearer permanently skulled:
		// keep-3 becomes keep-0, keep-4 (Protect Item) becomes keep-1.
		GearItem avarice = new GearItem(9, "Amulet of avarice", "", GearSlot.NECK, "", 0,
			false, true, true, true, 500_000, StatBlock.ZERO, StatBlock.ZERO, StatBlock.ZERO, null);
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			item(2, GearSlot.BODY, true, 80),
			avarice);

		PvpRisk.Assessment skulled = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(0, skulled.kept.size());
		Assert.assertEquals(100 + 80 + 500_000, skulled.riskGp);

		PvpRisk.Assessment protect = PvpRisk.assess(loadout, null, 4);
		Assert.assertEquals(1, protect.kept.size());
		Assert.assertEquals("the amulet itself is the most valuable piece",
			9, protect.kept.get(0).getId());
	}

	@Test
	public void costFreeBreakersChargeTheirRebuildFriction()
	{
		// Salve amulet(ei): gp cost 0 (free tomb re-obtain, imbue points
		// refunded) but the death still costs the rebuild errand - the
		// curated 150k friction charges into the risk total and the risk
		// cap, so it can never ride into a low-risk set as "safe".
		Loadout loadout = worn(
			item(1, GearSlot.WEAPON, true, 100),
			untradeable(2, "salve amulet(ei)", GearSlot.NECK, SOME_DEFENCE));
		PvpRisk.Assessment fates = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals(1, fates.untradeableCharges.size());
		Assert.assertEquals(2, fates.untradeableCharges.get(0).item.getId());
		Assert.assertEquals(150_000, fates.untradeableCharges.get(0).costGp);
		Assert.assertEquals(150_000, fates.riskGp);
	}

	@Test
	public void risksRebuildFlagsUnprotectedFrictionItemsOnly()
	{
		// Salve: unprotectable in the model - always a risked rebuild.
		Loadout salve = worn(
			item(1, GearSlot.WEAPON, true, 100),
			untradeable(2, "salve amulet(ei)", GearSlot.NECK, SOME_DEFENCE));
		Assert.assertTrue(risksRebuild(salve, null, 3));

		// Warrior ring (i) with cheap tradeables: it ranks into the kept
		// slots - protected, so no risked rebuild.
		Loadout protectedRing = worn(
			item(1, GearSlot.WEAPON, true, 100),
			untradeable(3, "warrior ring (i)", GearSlot.RING, SOME_DEFENCE));
		Assert.assertFalse(risksRebuild(protectedRing, null, 3));

		// The same ring displaced from the kept slots by pricier gear IS
		// a risked rebuild.
		Loadout displacedRing = worn(
			item(1, GearSlot.WEAPON, true, 100_000_000),
			item(2, GearSlot.BODY, true, 50_000_000),
			item(4, GearSlot.LEGS, true, 20_000_000),
			untradeable(3, "warrior ring (i)", GearSlot.RING, SOME_DEFENCE));
		Assert.assertTrue(risksRebuild(displacedRing, null, 3));

		// No friction anywhere: cheap early exit, never flagged.
		Loadout plain = worn(item(1, GearSlot.WEAPON, true, 100));
		Assert.assertFalse(risksRebuild(plain, null, 3));
	}

	@Test
	public void imbuedConvertsCarryTheirReimbueFrictionInThePool()
	{
		// Warrior ring (i): the killer gets the 60k base ring and the
		// victim owes a re-imbue visit - loss price is 60k + 50k friction.
		Loadout loadout = worn(
			untradeable(3, "warrior ring (i)", GearSlot.RING, SOME_DEFENCE));
		PvpRisk.Assessment fates = PvpRisk.assess(loadout, null, 0);
		Assert.assertEquals(110_000, fates.riskGp);
		Assert.assertEquals(110_000, fates.valueOf(
			loadout.get(GearSlot.RING)));
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
		// 1.5M black mask component + 50k re-imbue rebuild friction.
		Assert.assertEquals(1_550_000L, risk.valueOf(risk.kept.get(0)));
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
		// 1.5M black mask component + 50k re-imbue rebuild friction.
		Assert.assertEquals(1_550_000L, risk.riskGp);
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

	/** The assess()-derived meaning of risksRebuild - the lean production
	 * implementation must agree with this everywhere, ties included. */
	private static boolean rebuildViaAssess(Loadout loadout, GearItem spec, int kept,
		java.util.Set<Integer> pinnedIds)
	{
		PvpRisk.Assessment fates = PvpRisk.assess(loadout, spec, kept);
		for (PvpRisk.Charge charge : fates.untradeableCharges)
		{
			if (UntradeableDeathCosts.frictionFor(charge.item) > 0
				&& !pinnedIds.contains(charge.item.getId()))
			{
				return true;
			}
		}
		for (GearItem lost : fates.lost)
		{
			if (UntradeableDeathCosts.frictionFor(lost) > 0
				&& !pinnedIds.contains(lost.getId()))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void theLeanRisksRebuildMatchesTheAssessmentSemanticsExactly()
	{
		// The beam calls risksRebuild() per candidate set; it must agree with
		// the full assessment's kept/lost/charge classification everywhere -
		// charge-class friction (salve), convertible friction inside and
		// outside the kept slots, pins, the avarice skull, and value TIES at
		// the kept boundary (stable order decides which of two equals wins
		// the slot).
		GearItem tieA = item(21, GearSlot.BODY, true, 110_000);
		GearItem tieRing = untradeable(3, "warrior ring (i)", GearSlot.RING, SOME_DEFENCE);
		// warrior ring (i) pools at 60k component + 50k friction = 110k -
		// an exact tie with tieA; body precedes ring in slot order.
		Loadout[] loadouts = {
			worn(item(1, GearSlot.WEAPON, true, 100),
				untradeable(2, "salve amulet(ei)", GearSlot.NECK, SOME_DEFENCE)),
			worn(item(1, GearSlot.WEAPON, true, 100), tieRing),
			worn(item(1, GearSlot.WEAPON, true, 100_000_000),
				item(2, GearSlot.BODY, true, 50_000_000),
				item(4, GearSlot.LEGS, true, 20_000_000), tieRing),
			worn(item(1, GearSlot.WEAPON, true, 200_000), tieA, tieRing),
			worn(item(1, GearSlot.WEAPON, true, 100),
				new GearItem(9, "Amulet of avarice", "", GearSlot.NECK, "", 0, false,
					true, true, true, 500_000, StatBlock.ZERO, StatBlock.ZERO,
					StatBlock.ZERO, null),
				untradeable(6, "Slayer helmet (i)", GearSlot.HEAD, SOME_DEFENCE)),
			worn(item(1, GearSlot.WEAPON, true, 100),
				untradeable(5, "Fire cape", GearSlot.CAPE, SOME_DEFENCE),
				untradeable(6, "Slayer helmet (i)", GearSlot.HEAD, SOME_DEFENCE),
				untradeable(8, "Amulet of the damned", GearSlot.NECK, SOME_DEFENCE)),
			worn(item(1, GearSlot.WEAPON, true, 100)),
		};
		GearItem specTradeable = item(9, GearSlot.WEAPON, true, 500_000);
		GearItem specFriction = untradeable(10, "salve amulet(i)", GearSlot.WEAPON, SOME_DEFENCE);
		java.util.List<java.util.Set<Integer>> pinSets = java.util.List.of(
			java.util.Collections.emptySet(),
			java.util.Set.of(2, 3),
			java.util.Set.of(6, 10));
		for (Loadout loadout : loadouts)
		{
			for (GearItem spec : new GearItem[]{null, specTradeable, specFriction})
			{
				// kept=2 lands ON the tieA/tieRing boundary: exactly one of
				// the two 110k equals wins the slot, by stable pool order.
				for (int kept : new int[]{0, 1, 2, 3, 4})
				{
					for (java.util.Set<Integer> pins : pinSets)
					{
						Assert.assertEquals(
							"kept=" + kept + " pins=" + pins,
							rebuildViaAssess(loadout, spec, kept, pins),
							risksRebuild(loadout, spec, kept, pins));
					}
				}
			}
		}
	}

	@Test
	public void protectOnlyItemsAreVetoedWhenTheyLandInTheLostPile()
	{
		// User flag "only bring if protected on death": a plain tradeable
		// with no friction is normally fine to risk, but once flagged the
		// set is vetoed exactly when the item ranks OUTSIDE the kept slots.
		java.util.Set<Integer> flag = java.util.Set.of(2);
		java.util.Set<Integer> noPins = java.util.Collections.emptySet();

		// Kept: the flagged body ranks into the top-3, so it is protected.
		Loadout kept = worn(
			item(1, GearSlot.WEAPON, true, 100),
			item(2, GearSlot.BODY, true, 80),
			item(3, GearSlot.LEGS, true, 60));
		Assert.assertFalse("protected flagged item is fine",
			PvpRisk.risksUnprotected(kept, null, 3, noPins, flag));

		// Displaced: pricier gear pushes the flagged body into the lost pile.
		Loadout lost = worn(
			item(1, GearSlot.WEAPON, true, 100_000_000),
			item(4, GearSlot.LEGS, true, 50_000_000),
			item(5, GearSlot.HEAD, true, 20_000_000),
			item(2, GearSlot.BODY, true, 80));
		Assert.assertTrue("flagged item in the lost pile vetoes the set",
			PvpRisk.risksUnprotected(lost, null, 3, noPins, flag));

		// A pinned flagged item is the player's explicit pick - never vetoes.
		Assert.assertFalse("pinned overrides the protect-only flag",
			PvpRisk.risksUnprotected(lost, null, 3, java.util.Set.of(2), flag));

		// Unflagged: the same displaced set is perfectly legal.
		Assert.assertFalse("unflagged tradeables may be risked",
			PvpRisk.risksUnprotected(lost, null, 3, noPins, noPins));
	}

	@Test
	public void protectOnlyStacksWithTheFrictionVeto()
	{
		// Both vetoes live in the same pass: a salve (friction) AND a
		// separately-flagged tradeable in the lost pile each trip it.
		java.util.Set<Integer> noPins = java.util.Collections.emptySet();
		Loadout salveSet = worn(
			item(1, GearSlot.WEAPON, true, 100),
			untradeable(2, "salve amulet(ei)", GearSlot.NECK, SOME_DEFENCE));
		Assert.assertTrue("friction alone still vetoes with an empty flag set",
			PvpRisk.risksUnprotected(salveSet, null, 3, noPins, noPins));

		// The lean protect-only path must agree with risksRebuild for the
		// pure-friction case (no user flags).
		Assert.assertEquals(
			risksRebuild(salveSet, null, 3, noPins),
			PvpRisk.risksUnprotected(salveSet, null, 3, noPins, noPins));
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
