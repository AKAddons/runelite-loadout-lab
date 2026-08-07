package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The leaf-bladed battleaxe deals +17.5% damage to turoths and kurasks
 * (wiki-verified passive, stacks with the slayer helmet; 2026-07 player
 * audit A2.4). Damage only - accuracy is untouched. Per the pool lesson,
 * the conditional also needs a candidate-score boost and an
 * optimizer-level test proving the axe actually gets PICKED.
 */
class LeafBladedTest
{
	private static LoadoutData data;
	private static MonsterStats kurask;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		kurask = data.searchMonsters("kurask", 1).get(0);
		assertTrue(kurask.hasAttribute("leafy"), "test premise: kurask is leafy");
	}

	private static GearItem byName(String nameLower)
	{
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals(nameLower))
			{
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower);
	}

	private static DpsResult calc(MonsterStats monster, GearItem weapon)
	{
		OptimizationRequest request = new OptimizationRequest(monster,
			CombatStyle.MELEE, PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, true,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, weapon);
		return new DpsCalculator().calculate(request, new Loadout(gear));
	}

	@Test
	@DisplayName("the battleaxe hits 17.5% harder at a kurask than its raw stats say")
	void damageBoostApplies()
	{
		GearItem axe = byName("leaf-bladed battleaxe");
		GearItem sword = byName("leaf-bladed sword");

		// Same weapon vs a non-leafy target of any defence: max hit is a pure
		// function of strength gear, so the leafy bump shows in maxHit alone.
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		int plainMax = calc(goblin, axe).getMaxHit();
		int leafyMax = calc(kurask, axe).getMaxHit();
		assertEquals(plainMax * 47 / 40, leafyMax,
			"kurask max hit must be the plain max hit boosted 17.5%");

		// The sword carries no such passive - identical max hit both places.
		assertEquals(calc(goblin, sword).getMaxHit(), calc(kurask, sword).getMaxHit());
	}

	@Test
	@DisplayName("the optimizer PICKS the battleaxe at kurask (pool lesson)")
	void optimizerPicksTheAxe()
	{
		OptimizationRequest request = new OptimizationRequest(kurask,
			CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, true,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 3);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		assertFalse(results.isEmpty());
		assertEquals("leaf-bladed battleaxe",
			results.get(0).getLoadout().getWeapon().getNameLower(),
			"the +17.5% must make the axe the melee pick, not just a dps footnote");
	}
}
