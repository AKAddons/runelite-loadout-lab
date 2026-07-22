package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSearchTestSupport;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

/** Per-mob sims scope to their mob (field bug 2026-07-18): "Sim here"
 * on one group member must equip that mob's answer WITHOUT leaking the
 * pretend-owned item into the rest of the roster's shared answers. */
public class GroupSimScopingTest
{
	@Test
	public void perMobSimReachesTheRosterAnswer() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats vorkath = data.searchMonsters("vorkath", 1).get(0);
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		int whip = 4151;
		int rapier = GearSearchTestSupport.searchGear(data, "ghrazi rapier", 1).get(0).getId();
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(whip, 1);
		// Sim the rapier for the GOBLIN only.
		Map<Integer, java.util.Set<Integer>> dreamsByMob =
			Collections.singletonMap(goblin.profileId(), Collections.singleton(rapier));
		OptimizerService service = new OptimizerService(data);
		try
		{
			for (int maxSwaps : new int[]{1, 3})
			{
				CountDownLatch done = new CountDownLatch(1);
				AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
				service.bestPerStyleAcross(
					Arrays.asList(vorkath, goblin), PlayerLevels.MAXED, PlayerLevels.MAXED,
					PrayerUnlocks.ALL, RequirementProfile.MAXED,
					new OwnedItems(owned, true), 1, false, false, "",
					Collections.emptyMap(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
					false, 0, true, Collections.emptyMap(), Collections.emptyMap(), false, Collections.emptySet(), 0, OptimizerService.OptimizeMode.MAX_DPS,
					maxSwaps, Collections.emptyMap(), dreamsByMob, true,
					Collections.emptyMap(), null, Collections.emptySet(),
					roster -> { out.set(roster); done.countDown(); });
				Assert.assertTrue("timed out", done.await(90, TimeUnit.SECONDS));
				List<Map<CombatStyle, OptimizerService.StyleResult>> perMob = out.get().perMob;
				OptimizerService.StyleResult vsGoblin = perMob.get(1).get(CombatStyle.MELEE);
				OptimizerService.StyleResult vsVorkath = perMob.get(0).get(CombatStyle.MELEE);
				System.out.println("maxSwaps=" + maxSwaps
					+ " goblin weapon=" + name(vsGoblin)
					+ " vorkath weapon=" + name(vsVorkath));
				Assert.assertEquals("the simmed rapier answers the goblin (maxSwaps=" + maxSwaps + ")",
					rapier, vsGoblin.owned.get(0).getLoadout().get(GearSlot.WEAPON).getId());
				Assert.assertEquals("vorkath stays on the real bank",
					whip, vsVorkath.owned.get(0).getLoadout().get(GearSlot.WEAPON).getId());
			}
		}
		finally
		{
			service.shutdown();
		}
	}

	private static String name(OptimizerService.StyleResult r)
	{
		if (r == null || r.owned == null || r.owned.isEmpty())
		{
			return "EMPTY";
		}
		return r.owned.get(0).getLoadout().get(GearSlot.WEAPON) == null ? "none"
			: r.owned.get(0).getLoadout().get(GearSlot.WEAPON).getNameLower();
	}
}
