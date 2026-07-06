package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class SlayerInvariantTest
{
	/**
	 * Turning the slayer task ON only ADDS options (the slayer helm), so
	 * the resulting DPS can never be lower. This regressed once: with the
	 * helm taking the head slot, the beam pruned lone crystal-body states
	 * before the legs completed the set (whale-bank repro from the field).
	 */
	@Test
	public void slayerTaskNeverLowersTheAnswer()
	{
		LoadoutData data = new DataService().load();
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (GearItem g : data.getGearItems())
		{
			String n = g.getName().toLowerCase();
			if (!n.contains("twisted bow") && !n.contains("dragon knife")
				&& !n.contains("masori") && !n.contains("scorching"))
			{
				owned.put(g.getId(), 100);
			}
		}
		double offTask = best(data, graardor, owned, false);
		double onTask = best(data, graardor, owned, true);
		Assert.assertTrue("on-task dps (" + onTask + ") must be >= off-task (" + offTask + ")",
			onTask >= offTask - 1e-9);
	}

	private static double best(LoadoutData data, MonsterStats monster, Map<Integer, Integer> owned, boolean task)
	{
		OptimizationRequest request = new OptimizationRequest(
			monster, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, task,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1);
		return new LoadoutOptimizer().optimize(data, request).get(0).getDps();
	}
}
