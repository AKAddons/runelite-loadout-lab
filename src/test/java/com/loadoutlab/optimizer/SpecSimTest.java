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
import org.junit.Test;

/**
 * Sims are pretend-owned everywhere - including the SPEC pool, which
 * checked raw ownership only and silently kept simmed spec weapons out
 * of the competition (field report 2026-08-06: simmed Burning claws vs
 * a Barrows brother still recommended the owned abyssal dagger).
 */
public class SpecSimTest
{
	@Test
	public void aSimmedSpecWeaponCompetes() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats guthan = data.searchMonsters("guthan", 1).get(0);

		GearItem claws = null;
		GearItem whip = null;
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().startsWith("burning claws") && g.isStandardGear())
			{
				claws = g;
			}
			if (g.getNameLower().equals("abyssal whip"))
			{
				whip = g;
			}
		}
		Assert.assertNotNull(claws);

		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(whip.getId(), 1);

		OptimizerService service = new OptimizerService(data);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
		Map<CombatStyle, Set<Integer>> excludedByStyle = new java.util.EnumMap<>(CombatStyle.class);
		ServiceCalls.bestPerStyle(service, guthan,
			PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, new OwnedItems(owned, true), 1,
			false, false, "", excludedByStyle, -1,
			com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
			false, false, Set.of(claws.getId()), 0,
			Collections.emptyMap(), null, Collections.emptySet(),
			results ->
			{
				out.set(results);
				done.countDown();
			});
		Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
		OptimizerService.StyleResult melee = out.get().get(CombatStyle.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertNotNull("a simmed spec weapon must enter the spec competition",
			melee.specWeapon);
		Assert.assertTrue("the sim was the only spec weapon available, picked: "
				+ melee.specWeapon.label(),
			melee.specWeapon.getNameLower().startsWith("burning claws"));
	}
}
