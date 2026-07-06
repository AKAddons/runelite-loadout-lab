package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class ExclusionTest
{
	private static LoadoutData data;

	private static LoadoutData data()
	{
		if (data == null)
		{
			data = new DataService().load();
		}
		return data;
	}

	@Test
	public void excludingDragonDartsDropsTheBlowpipeToTheNextOwnedTier()
	{
		MonsterStats hydra = data().searchMonsters("alchemical hydra", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(12926, 1);   // toxic blowpipe
		owned.put(11230, 500); // dragon darts - the protected rares
		owned.put(25849, 500); // amethyst darts - the everyday ammo
		OptimizationRequest request = new OptimizationRequest(
			hydra, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1);

		DpsResult unrestricted = new LoadoutOptimizer().optimize(data(), request).get(0);
		Assert.assertTrue(unrestricted.getAttackType().contains("dragon darts"));

		DpsResult protectedDarts = new LoadoutOptimizer().optimize(data(),
			request.withExcludedItems(Set.of(11230))).get(0);
		Assert.assertTrue(protectedDarts.getAttackType().contains("amethyst darts"));
		Assert.assertTrue(unrestricted.getDps() > protectedDarts.getDps());
	}

	@Test
	public void excludedItemsNeverAppearInAnySlot()
	{
		MonsterStats goblin = data().searchMonsters("goblin", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			goblin, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		DpsResult best = new LoadoutOptimizer().optimize(data(), request).get(0);
		int weaponId = best.getLoadout().getWeapon().getId();

		DpsResult without = new LoadoutOptimizer().optimize(data(),
			request.withExcludedItems(Set.of(weaponId))).get(0);
		Assert.assertNotEquals(weaponId, without.getLoadout().getWeapon().getId());
		for (GearSlot slot : GearSlot.values())
		{
			if (without.getLoadout().get(slot) != null)
			{
				Assert.assertNotEquals(weaponId, without.getLoadout().get(slot).getId());
			}
		}
	}
}
