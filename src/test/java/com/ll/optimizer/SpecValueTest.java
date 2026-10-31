package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
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
public class SpecValueTest {
	/**
	 * The origin bug: a defence-drain spec (dragon warhammer) was valued for
	 * MELEE main-hands only, so it never surfaced on a ranged or magic set
	 * even though the drain lowers the Defence LEVEL that every style rolls
	 * against. On a ranged set versus a high-defence dragon it must now
	 * surface with positive added DPS.
	 */
	@Test
	public void defenceDrainIsValuedOnRangedSetsNotOnlyMelee() throws Exception {
		Tables data = new DataService().load();
		Monster vorkath = data.searchMonsters("vorkath", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(25886, 1);  // bow of faerdhinen - a ranged main with NO spec,
		                      // so the warhammer is the only spec candidate
		owned.put(13576, 1);  // dragon warhammer - the drain spec
		Service service = new Service(data);
		try {
			Service.Pick ranged = rangedResult(service, vorkath, owned);
			Assert.assertNotNull("a ranged set must exist", ranged);
			Assert.assertNotNull("the drain spec must surface on a ranged set", ranged.spec);
			Assert.assertEquals("Dragon warhammer", ranged.spec.getDisplayName());
			Assert.assertTrue("the drain must add positive dps on ranged",
				ranged.sda > 0);
		} finally {
			service.shutdown();
		}
	}

	private static Service.Pick rangedResult(Service service,
		Monster monster, Map<Integer, Integer> owned) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, monster, Levels.MAXED, Levels.MAXED,
			Prayers.ALL, RequirementProfile.MAXED, new Owned(owned, true), owned.hashCode(),
			false, false, "", java.util.Collections.emptySet(), -1,
			Req.DEFAULT_RISK_BUDGET_GP, false, java.util.Collections.emptySet(), 0,
			results -> { out.set(results); done.countDown(); });
		Assert.assertTrue(done.await(120, TimeUnit.SECONDS));
		return out.get().get(Style.RANGED);
	}
}
