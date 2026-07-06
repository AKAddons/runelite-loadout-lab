package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class BlowpipeTest
{
	@Test
	public void blowpipeWithDragonDartsBeatsBareThrownDarts()
	{
		LoadoutData data = new DataService().load();
		MonsterStats hydra = data.searchMonsters("alchemical hydra", 1).get(0);
		OptimizationRequest game = new OptimizationRequest(
			hydra, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 3);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data, game);
		Assert.assertFalse(results.isEmpty());
		for (DpsResult r : results)
		{
			Assert.assertFalse("bare thrown darts must never outrank a loaded blowpipe: "
					+ r.getLoadout().getWeapon().getName(),
				r.getLoadout().getWeapon().getName().endsWith(" dart"));
		}
	}

	@Test
	public void ownedBlowpipeUsesTheBestOwnedDartTierAndNeedsDarts()
	{
		LoadoutData data = new DataService().load();
		MonsterStats hydra = data.searchMonsters("alchemical hydra", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(12926, 1);  // toxic blowpipe (charged)
		owned.put(811, 500);  // rune darts
		OptimizationRequest request = new OptimizationRequest(
			hydra, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1);
		DpsResult withRune = new LoadoutOptimizer().optimize(data, request).get(0);
		Assert.assertTrue(withRune.getAttackType().contains("rune darts"));

		owned.put(11230, 500); // dragon darts: better tier takes over
		DpsResult withDragon = new LoadoutOptimizer().optimize(data, new OptimizationRequest(
			hydra, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1)).get(0);
		Assert.assertTrue(withDragon.getAttackType().contains("dragon darts"));
		Assert.assertTrue(withDragon.getDps() > withRune.getDps());
	}
}
