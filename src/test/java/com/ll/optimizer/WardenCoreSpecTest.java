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
 * The Wardens' core is THE spec-dump phase (field report 2026-08-06:
 * "ToA is missing the crucial heart phase with the DDS spec") - with a
 * dagger in the bank, the melee card at the core must surface its spec
 * at a positive win-over-replacement.
 */
public class WardenCoreSpecTest {
	@Test
	public void ddsSpecSurfacesAtTheCore() throws Exception {
		Tables data = new DataService().load();
		Monster core = data.searchMonsters("tumeken's warden", 8).stream()
			.filter(m -> m.getVersion().startsWith("Core-ejected"))
			.findFirst().orElseThrow();

		Gear dds = null;
		Gear whip = null;
		Gear chally = null;
		for (Gear g : data.getGearItems()) {
			if (g.getLname().equals("dragon dagger") && g.isStandardGear()) {
				dds = g;
			}
			if (g.getLname().equals("abyssal whip")) {
				whip = g;
			}
			if (g.getLname().equals("crystal halberd") && g.isStandardGear()) {
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

		Service service = new Service(data);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
		Map<Style, Set<Integer>> excludedByStyle = new java.util.EnumMap<>(Style.class);
		ServiceCalls.bestPerStyle(service, core,
			Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, new Owned(owned, true), 1,
			false, false, "", excludedByStyle, -1,
			com.ll.engine.Req.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0,
			Collections.emptyMap(), null, Collections.emptySet(),
			results -> {
				out.set(results);
				done.countDown();
			});
		Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
		Service.Pick melee = out.get().get(Style.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertNotNull("the dagger must enter the spec competition at the core",
			melee.specWeapon);
		Assert.assertTrue("the classic dump must be the pick, was: "
				+ melee.specWeapon.label(),
			melee.specWeapon.getLname().startsWith("dragon dagger"));
		Assert.assertTrue("the spec must price a positive win-over-replacement",
			melee.sda > 0);
	}
}
