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
 * The Wardens' core is THE spec-dump phase (field report 2026-08-06:
 * "ToA is missing the crucial heart phase with the DDS spec") - with a
 * dagger in the bank, the melee card at the core must surface its spec
 * at a positive win-over-replacement.
 */
public class WardenCoreSpecTest
{
	@Test
	public void ddsSpecSurfacesAtTheCore() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats core = data.searchMonsters("tumeken's warden", 8).stream()
			.filter(m -> m.getVersion().startsWith("Core-ejected"))
			.findFirst().orElseThrow();

		GearItem dds = null;
		GearItem whip = null;
		GearItem chally = null;
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().equals("dragon dagger") && g.isStandardGear())
			{
				dds = g;
			}
			if (g.getNameLower().equals("abyssal whip"))
			{
				whip = g;
			}
			if (g.getNameLower().equals("crystal halberd") && g.isStandardGear())
			{
				chally = g;
			}
		}
		Assert.assertNotNull(dds);
		Assert.assertNotNull(chally);

		// The chally competes and must LOSE: the 1x1 core denies its sweep
		// the second hit (field report 2026-08-06: "it should recommend DDS
		// instead of chally").
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(whip.getId(), 1);
		owned.put(dds.getId(), 1);
		owned.put(chally.getId(), 1);

		OptimizerService service = new OptimizerService(data);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
		Map<CombatStyle, Set<Integer>> excludedByStyle = new java.util.EnumMap<>(CombatStyle.class);
		ServiceCalls.bestPerStyle(service, core,
			PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, new OwnedItems(owned, true), 1,
			false, false, "", excludedByStyle, -1,
			com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0,
			Collections.emptyMap(), null, Collections.emptySet(),
			results ->
			{
				out.set(results);
				done.countDown();
			});
		Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
		OptimizerService.StyleResult melee = out.get().get(CombatStyle.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertNotNull("the dagger must enter the spec competition at the core",
			melee.specWeapon);
		Assert.assertTrue("the classic dump must be the pick, was: "
				+ melee.specWeapon.label(),
			melee.specWeapon.getNameLower().startsWith("dragon dagger"));
		Assert.assertTrue("the spec must price a positive win-over-replacement",
			melee.specDpsAdded > 0);
	}
}
