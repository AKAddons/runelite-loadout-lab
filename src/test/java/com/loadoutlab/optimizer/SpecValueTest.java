package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

/**
 * The spec value-over-replacement model (field direction 2026-07-19): a
 * spec is ranked and shown by the DPS it ADDS to the kill over just
 * attacking - marginal, regen-aware and drain-inclusive for every style.
 */
public class SpecValueTest
{
	/**
	 * The origin bug: a defence-drain spec (dragon warhammer) was valued for
	 * MELEE main-hands only, so it never surfaced on a ranged or magic set
	 * even though the drain lowers the Defence LEVEL that every style rolls
	 * against. On a ranged set versus a high-defence dragon it must now
	 * surface with positive added DPS.
	 */
	@Test
	public void defenceDrainIsValuedOnRangedSetsNotOnlyMelee() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats vorkath = data.searchMonsters("vorkath", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(25886, 1);  // bow of faerdhinen - a ranged main with NO spec,
		                      // so the warhammer is the only spec candidate
		owned.put(13576, 1);  // dragon warhammer - the drain spec
		OptimizerService service = new OptimizerService(data);
		try
		{
			OptimizerService.StyleResult ranged = rangedResult(service, vorkath, owned);
			Assert.assertNotNull("a ranged set must exist", ranged);
			Assert.assertNotNull("the drain spec must surface on a ranged set", ranged.spec);
			Assert.assertEquals("Dragon warhammer", ranged.spec.getDisplayName());
			Assert.assertTrue("the drain must add positive dps on ranged",
				ranged.specDpsAdded > 0);
		}
		finally
		{
			service.shutdown();
		}
	}

	private static OptimizerService.StyleResult rangedResult(OptimizerService service,
		MonsterStats monster, Map<Integer, Integer> owned) throws Exception
	{
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, monster, PlayerLevels.MAXED, PlayerLevels.MAXED,
			PrayerUnlocks.ALL, RequirementProfile.MAXED, new OwnedItems(owned, true), owned.hashCode(),
			false, false, "", java.util.Collections.emptySet(), -1,
			OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, java.util.Collections.emptySet(), 0,
			results -> { out.set(results); done.countDown(); });
		Assert.assertTrue(done.await(120, TimeUnit.SECONDS));
		return out.get().get(CombatStyle.RANGED);
	}
}
