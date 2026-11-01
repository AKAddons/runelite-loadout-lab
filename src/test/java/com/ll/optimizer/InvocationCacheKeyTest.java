package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.MonsterMechanics;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

/**
 * The single-mob cache key must carry the ToA invocation level (found
 * by a token-diet agent 2026-08-09: optimizeKey had it, baseKeyFor did
 * not - so flipping the Invocation chip on a Warden card served the
 * PREVIOUS level's cached results). The defence scaling
 * (x(250+invo)/250) guarantees the two levels' numbers must differ.
 */
public class InvocationCacheKeyTest {
	private static Map<Style, Service.Pick> best(
		Service service, Monster monster) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Service.Pick>> out =
			new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, monster,
			Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, Owned.EMPTY, 1,
			false, false, "", new EnumMap<>(Style.class), -1,
			com.ll.engine.Req.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0,
			Collections.emptyMap(), null, Collections.emptySet(),
			r -> { out.set(r); done.countDown(); });
		Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
		return out.get();
	}

	@Test
	public void flippingTheInvocationLevelNeverServesStaleResults() throws Exception {
		Tables data = new DataService().load();
		Monster warden = data.searchMonsters("tumeken's warden", 6).stream()
			.filter(m -> MonsterMechanics.toaScaled(m))
			.findFirst().orElseThrow();
		Service service = new Service(data);
		try {
			double atZero = best(service, MonsterMechanics.atToaInvocation(warden, 0))
				.get(Style.RANGED).overallBest.getDps();
			double at300 = best(service, MonsterMechanics.atToaInvocation(warden, 300))
				.get(Style.RANGED).overallBest.getDps();
			Assert.assertNotEquals("invocation 300 must not serve the cached"
					+ " invocation-0 numbers (defence x2.2 has to move the dps)",
				atZero, at300, 1e-9);
			Assert.assertTrue("higher invocation means lower dps",
				at300 < atZero);
		} finally {
			service.shutdown();
		}
	}
}
