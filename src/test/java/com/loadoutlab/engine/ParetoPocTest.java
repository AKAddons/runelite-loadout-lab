package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-7 phase-1 proof of concept: on a conditional-free melee request the
 * Pareto-frontier fold must find a set whose calculated DPS equals or
 * beats the beam's - exactness is the whole point. Weapon fixed (the
 * frontier runs per weapon/attack-type in the real design); armour
 * slots fold through the frontier; final DPS comes from the SAME
 * DpsCalculator the beam uses, so agreement is apples to apples.
 */
class ParetoPocTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static GearItem byName(String name)
	{
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals(name))
			{
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + name);
	}

	@Test
	@DisplayName("frontier DP matches the beam on a conditional-free melee bag")
	void frontierMatchesBeam()
	{
		MonsterStats goblin = data.searchMonsters("goblin", 5).stream()
			.filter(m -> m.getName().equalsIgnoreCase("Goblin"))
			.findFirst().orElseThrow();

		// A curated owned bag with real slot competition and NO
		// conditional items (no salve/void/slayer/dragonbane).
		Map<Integer, Integer> bag = new HashMap<>();
		for (String name : new String[]{
			"abyssal whip", "amulet of torture", "amulet of glory",
			"bandos chestplate", "fighter torso", "bandos tassets",
			"obsidian platelegs", "ferocious gloves", "barrows gloves",
			"primordial boots", "dragon boots", "berserker ring",
			"warrior ring", "fire cape", "obsidian cape",
			"helm of neitiznot", "berserker helm", "dragon defender"})
		{
			bag.put(byName(name).getId(), 1);
		}

		OptimizationRequest request = TestRequests.of(goblin, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null,
			0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(bag, true), 1);

		DpsResult beamBest = new LoadoutOptimizer().optimize(data, request).get(0);

		// --- Frontier side: fixed weapon, armour folds, exact evaluation.
		GearItem whip = beamBest.getLoadout().getWeapon();
		assertNotNull(whip);
		String attackType = beamBest.getAttackType().split(" ")[0];

		Map<GearSlot, List<GearItem>> bySlot = new EnumMap<>(GearSlot.class);
		for (int id : bag.keySet())
		{
			GearItem item = data.getGear(id);
			if (item == null || item.getSlot() == null || item.getSlot() == GearSlot.WEAPON)
			{
				continue;
			}
			bySlot.computeIfAbsent(item.getSlot(), s -> new ArrayList<>()).add(item);
		}

		ParetoFrontier frontier = new ParetoFrontier();
		for (Map.Entry<GearSlot, List<GearItem>> slot : bySlot.entrySet())
		{
			frontier.fold(slot.getKey(), slot.getValue(),
				item -> item.getOffensive().getAttackBonus(attackType),
				item -> item.getBonuses().getStrength());
		}

		DpsCalculator calculator = new DpsCalculator();
		DpsResult dpBest = null;
		for (ParetoFrontier.State state : frontier.states())
		{
			Map<GearSlot, GearItem> gear = new EnumMap<>(state.picks);
			gear.put(GearSlot.WEAPON, whip);
			DpsResult result = calculator.calculate(request, new Loadout(gear));
			if (dpBest == null || result.getDps() > dpBest.getDps())
			{
				dpBest = result;
			}
		}

		assertNotNull(dpBest);
		// The invariant that carries the whole migration: DP >= beam.
		assertTrue(dpBest.getDps() >= beamBest.getDps() - 1e-9,
			"DP " + dpBest.getDps() + " must not lose to beam " + beamBest.getDps());
		// And on a conditional-free bag they should agree EXACTLY.
		assertEquals(beamBest.getDps(), dpBest.getDps(), 1e-9,
			"beam and frontier disagree on a conditional-free bag");
	}
}
