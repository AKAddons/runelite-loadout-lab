package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
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
		Tables data = new DataService().load();
		Monster guthan = data.searchMonsters("guthan", 1).get(0);

		Gear claws = null;
		Gear whip = null;
		for (Gear g : data.getGearItems()) {
			if (g.getLname().startsWith("burning claws") && g.isStandardGear()) {
				claws = g;
			}
			if (g.getLname().equals("abyssal whip")) {
				whip = g;
			}
		}
		Assert.assertNotNull(claws);

		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(whip.getId(), 1);

		Service service = new Service(data);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
		Map<Style, Set<Integer>> excludedByStyle = new java.util.EnumMap<>(Style.class);
		ServiceCalls.bestPerStyle(service, guthan,
			Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, new Owned(owned, true), 1,
			false, false, "", excludedByStyle, -1,
			com.ll.engine.Req.DEFAULT_RISK_BUDGET_GP,
			false, false, Set.of(claws.getId()), 0,
			Collections.emptyMap(), null, Collections.emptySet(),
			results -> {
				out.set(results);
				done.countDown();
			});
		Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
		Service.Pick melee = out.get().get(Style.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertNotNull("a simmed spec weapon must enter the spec competition",
			melee.specWeapon);
		Assert.assertTrue("the sim was the only spec weapon available, picked: "
				+ melee.specWeapon.label(),
			melee.specWeapon.getLname().startsWith("burning claws"));
	}
}
