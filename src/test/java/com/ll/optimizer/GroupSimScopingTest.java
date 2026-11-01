package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.GearSearchTestSupport;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
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
public class GroupSimScopingTest {
	@Test
	public void perMobSimReachesTheRosterAnswer() throws Exception {
		Tables data = new DataService().load();
		Monster vorkath = data.searchMonsters("vorkath", 1).get(0);
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		int whip = 4151;
		int rapier = GearSearchTestSupport.searchGear(data, "ghrazi rapier", 1).get(0).getId();
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(whip, 1);
		// Sim the rapier for the GOBLIN only.
		Map<Integer, java.util.Set<Integer>> dreamsByMob =
			Collections.singletonMap(goblin.profileId(), Collections.singleton(rapier));
		Service service = new Service(data);
		try {
			for (int maxSwaps : new int[]{1, 3}) {
				CountDownLatch done = new CountDownLatch(1);
				AtomicReference<Service.RosterResult> out = new AtomicReference<>();
				com.ll.engine.ComputeParams p = new com.ll.engine.ComputeParams();
				p.maxSwaps = maxSwaps;
				service.bestPerStyleAcross(
					Arrays.asList(vorkath, goblin), Levels.MAXED, Levels.MAXED,
					Prayers.ALL, RequirementProfile.MAXED,
					new Owned(owned, true), 1, p,
					Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), dreamsByMob,
					Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(),
					roster -> { out.set(roster); done.countDown(); });
				Assert.assertTrue("timed out", done.await(90, TimeUnit.SECONDS));
				List<Map<Style, Service.Pick>> pm = out.get().pm;
				Service.Pick vsGoblin = pm.get(1).get(Style.MELEE);
				Service.Pick vsVorkath = pm.get(0).get(Style.MELEE);
				System.out.println("maxSwaps=" + maxSwaps
					+ " goblin weapon=" + name(vsGoblin)
					+ " vorkath weapon=" + name(vsVorkath));
				Assert.assertEquals("the simmed rapier answers the goblin (maxSwaps=" + maxSwaps + ")",
					rapier, vsGoblin.owned.get(0).getLoadout().get(Slot.WEAPON).getId());
				Assert.assertEquals("vorkath stays on the real bank",
					whip, vsVorkath.owned.get(0).getLoadout().get(Slot.WEAPON).getId());
			}
		} finally {
			service.shutdown();
		}
	}

	private static String name(Service.Pick r) {
		if (r == null || r.owned == null || r.owned.isEmpty()) {
			return "EMPTY";
		}
		return r.owned.get(0).getLoadout().get(Slot.WEAPON) == null ? "none"
			: r.owned.get(0).getLoadout().get(Slot.WEAPON).getLname();
	}
}
