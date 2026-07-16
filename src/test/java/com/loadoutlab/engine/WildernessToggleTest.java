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
 * The wilderness-weapon +50% used to fire by monster NAME, so Catacombs
 * hellhounds and Slayer-tower staples got Ursine-chainmace picks with
 * buffed numbers (2026-07 player audit A3.1). The buff now keys on the
 * request's in-Wilderness flag: wilderness-EXCLUSIVE monsters (revs, the
 * boss ring) default it on; shared-name monsters default off and take
 * the panel toggle.
 */
class WildernessToggleTest
{
	private static LoadoutData data;
	private static MonsterStats hellhound;
	private static MonsterStats revDemon;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		hellhound = data.searchMonsters("hellhound", 1).get(0);
		revDemon = data.searchMonsters("revenant demon", 1).get(0);
	}

	private static OptimizationRequest request(MonsterStats monster)
	{
		return new OptimizationRequest(monster, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null, 0, CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static Loadout ursine()
	{
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals("ursine chainmace")
				&& "charged".equals(item.getVersionLower()))
			{
				EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
				gear.put(GearSlot.WEAPON, item);
				return new Loadout(gear);
			}
		}
		throw new AssertionError("corpus is missing a charged ursine chainmace");
	}

	@Test
	@DisplayName("a hellhound is not the Wilderness by default - the toggle opts in")
	void sharedNameMonstersNeedTheToggle()
	{
		OptimizationRequest atHellhound = request(hellhound);
		assertFalse(atHellhound.isInWilderness(), "hellhounds also live in the Catacombs");
		assertFalse(DpsCalculator.revWeaponBuff(atHellhound, ursine(), "ursine chainmace"));

		OptimizationRequest inWildy = atHellhound.withInWilderness(true);
		assertTrue(DpsCalculator.revWeaponBuff(inWildy, ursine(), "ursine chainmace"),
			"the same fight WITH the toggle gets the +50%");
	}

	@Test
	@DisplayName("wilderness-exclusive monsters default the flag on")
	void exclusiveMonstersDefaultOn()
	{
		assertTrue(request(revDemon).isInWilderness());
		assertTrue(DpsCalculator.revWeaponBuff(request(revDemon), ursine(), "ursine chainmace"));
	}

	@Test
	@DisplayName("the optimizer no longer hands Catacombs hellhound tasks an Ursine chainmace")
	void hellhoundMeleeIsNotUrsine()
	{
		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request(hellhound));
		assertFalse(results.isEmpty());
		assertNotEquals("ursine chainmace",
			results.get(0).getLoadout().getWeapon().getNameLower());
	}
}
