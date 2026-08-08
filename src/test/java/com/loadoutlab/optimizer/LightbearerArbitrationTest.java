package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The Lightbearer's stat line is empty, so it can never win the ring
 * slot on dps - its value is doubled spec regen. The arbitration prices
 * both ring candidates on the TOTAL (set dps + spec dps-added) and the
 * argmax wins (field report 2026-08-08: "it can be really impactful in
 * fights with big spec utility").
 */
public class LightbearerArbitrationTest
{
	private static LoadoutData data;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
	}

	private static GearItem byName(String nameLower)
	{
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().equals(nameLower) && g.isStandardGear())
			{
				return g;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower);
	}

	private static OptimizerService.StyleResult melee(MonsterStats monster,
		Map<Integer, Integer> owned) throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, monster,
				PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 1,
				false, false, "", new java.util.EnumMap<>(CombatStyle.class), -1,
				com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
				false, false, Collections.emptySet(), 0,
				Collections.emptyMap(), null, Collections.emptySet(),
				results ->
				{
					out.set(results);
					done.countDown();
				});
			Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
			return out.get().get(CombatStyle.MELEE);
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void lightbearerWinsALongFightWhenTheRingCostsNoDps() throws Exception
	{
		// Ring of dueling and the Lightbearer are both stat-less: equal set
		// dps, so the doubled regen's extra claws are pure profit and the
		// Lightbearer MUST take the slot at a long fight.
		MonsterStats nex = data.searchMonsters("nex", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (String n : new String[]{"abyssal whip", "dragon claws",
			"ring of dueling", "lightbearer"})
		{
			owned.put(byName(n).getId(), 1);
		}
		OptimizerService.StyleResult melee = melee(nex, owned);
		Assert.assertNotNull(melee);
		GearItem ring = melee.owned.get(0).getLoadout().get(com.loadoutlab.data.GearSlot.RING);
		Assert.assertNotNull("a ring must be worn", ring);
		Assert.assertTrue("doubled regen with no dps cost must take the slot, was: "
			+ ring.label(), ring.getNameLower().contains("lightbearer"));
		Assert.assertNotNull("the claws still ride as the spec", melee.specWeapon);
	}

	@Test
	public void aDpsRingKeepsTheSlotWhenSpecValueIsTiny() throws Exception
	{
		// A goblin dies before a second spec fires - the Berserker ring's
		// strength is worth more than regen nobody uses.
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (String n : new String[]{"abyssal whip", "dragon claws",
			"berserker ring (i)", "lightbearer"})
		{
			owned.put(byName(n).getId(), 1);
		}
		OptimizerService.StyleResult melee = melee(goblin, owned);
		Assert.assertNotNull(melee);
		GearItem ring = melee.owned.get(0).getLoadout().get(com.loadoutlab.data.GearSlot.RING);
		Assert.assertNotNull(ring);
		Assert.assertTrue("no spec value, no swap - the dps ring stands, was: "
			+ ring.label(), ring.getNameLower().contains("berserker"));
	}

	@Test
	public void doubledRegenRaisesDrainValueWhenTheBudgetLimitsFishing() throws Exception
	{
		// A synthetic high-defence dummy: the DWH land chance is low, so
		// P(landed) = 1-(1-p)^attempts is budget-bound - exactly where the
		// Lightbearer's extra specs buy real drain probability. v1 priced
		// ONE attempt, making drain value blind to regen.
		com.loadoutlab.data.MonsterStats dummy = new com.loadoutlab.data.MonsterStats(
			-42, "Drain Dummy", "", 500, 800, 3, 350, 200, 0,
			new com.loadoutlab.data.MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60),
			null, java.util.Collections.emptyList(), false, "", 0);
		com.loadoutlab.engine.OptimizationRequest request =
			com.loadoutlab.engine.TestRequests.of(dummy,
				CombatStyle.MELEE, PlayerLevels.MAXED,
				com.loadoutlab.engine.PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
				null, 0, com.loadoutlab.engine.CandidateMode.ALL_STANDARD,
				true, false, OwnedItems.EMPTY, 1);

		java.util.EnumMap<com.loadoutlab.data.GearSlot, GearItem> worn =
			new java.util.EnumMap<>(com.loadoutlab.data.GearSlot.class);
		worn.put(com.loadoutlab.data.GearSlot.WEAPON, byName("abyssal whip"));
		com.loadoutlab.engine.DpsResult main = new com.loadoutlab.engine.DpsCalculator()
			.calculate(request, new com.loadoutlab.engine.Loadout(worn));
		Assert.assertNotNull(main);

		java.util.EnumMap<com.loadoutlab.data.GearSlot, GearItem> specWorn =
			new java.util.EnumMap<>(com.loadoutlab.data.GearSlot.class);
		specWorn.put(com.loadoutlab.data.GearSlot.WEAPON, byName("dragon warhammer"));
		com.loadoutlab.engine.DpsResult specBase = new com.loadoutlab.engine.DpsCalculator()
			.calculate(request, new com.loadoutlab.engine.Loadout(specWorn));
		Assert.assertNotNull(specBase);
		com.loadoutlab.engine.SpecialAttack dwh =
			com.loadoutlab.engine.SpecialAttack.match(byName("dragon warhammer"));
		Assert.assertNotNull(dwh);
		double expected = dwh.expectedDamage(specBase, dummy, PlayerLevels.MAXED);

		OptimizerService service = new OptimizerService(data);
		try
		{
			double without = service.specDpsAdded(new com.loadoutlab.engine.DpsCalculator(),
				dwh, specBase, expected, request, main, dummy, false);
			double with = service.specDpsAdded(new com.loadoutlab.engine.DpsCalculator(),
				dwh, specBase, expected, request, main, dummy, true);
			Assert.assertTrue("more attempts must buy drain probability: "
				+ without + " -> " + with, with > without + 1e-9);
		}
		finally
		{
			service.shutdown();
		}
	}
}
