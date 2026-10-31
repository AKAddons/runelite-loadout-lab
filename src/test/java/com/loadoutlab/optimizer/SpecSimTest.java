package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.Style;
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
public class SpecSimTest {
	@Test
	public void aSimmedSpecWeaponCompetes() throws Exception {
		LoadoutData data = new DataService().load();
		Monster guthan = data.searchMonsters("guthan", 1).get(0);

		Gear claws = null;
		Gear whip = null;
		for (Gear g : data.getGearItems()) {
			if (g.getNameLower().startsWith("burning claws") && g.isStandardGear()) {
				claws = g;
			}
			if (g.getNameLower().equals("abyssal whip")) {
				whip = g;
			}
		}
		Assert.assertNotNull(claws);

		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(whip.getId(), 1);

		OptimizerService service = new OptimizerService(data);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
		Map<Style, Set<Integer>> excludedByStyle = new java.util.EnumMap<>(Style.class);
		ServiceCalls.bestPerStyle(service, guthan,
			PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, new OwnedItems(owned, true), 1,
			false, false, "", excludedByStyle, -1,
			com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
			false, false, Set.of(claws.getId()), 0,
			Collections.emptyMap(), null, Collections.emptySet(),
			results -> {
				out.set(results);
				done.countDown();
			});
		Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
		OptimizerService.StyleResult melee = out.get().get(Style.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertNotNull("a simmed spec weapon must enter the spec competition",
			melee.specWeapon);
		Assert.assertTrue("the sim was the only spec weapon available, picked: "
				+ melee.specWeapon.label(),
			melee.specWeapon.getNameLower().startsWith("burning claws"));
	}
}
