package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Dps;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RangedAmmo;
import com.ll.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew, client test 2026-10-20: "i'm simming ascension crossbows and it
 * is still giving me blowpipe for gemstone crab". The crossbows fire only
 * ascension bolts; a sim of the weapon alone had no ammo it could use, so
 * the line died. A simmed weapon with exclusive ammo brings that ammo.
 */
class SimPairedAmmoTest {
	@Test
	@DisplayName("a simmed weapon with exclusive ammo brings it; other sims are untouched")
	void pairedAmmoJoinsTheSim() {
		Set<Integer> sims = RangedAmmo.withPairedAmmo(Set.of(34681, 12926));
		assertTrue(sims.containsAll(Set.of(34681, 12926, 34684, 34690, 34696, 34702, 34708)), sims.toString());
		assertEquals(Set.of(12926), RangedAmmo.withPairedAmmo(Set.of(12926)));
		assertEquals(Set.of(), RangedAmmo.withPairedAmmo(Set.of()));
	}

	@Test
	@DisplayName("simming only the crossbows, the Gemstone Crab's ranged card wears them with ascension bolts")
	void simmedCrossbowsFire() throws Exception {
		Tables data = new DataService().load();
		Monster crab = data.searchMonsters("gemstone crab", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (String n : new String[]{"toxic blowpipe", "masori body (f)", "masori chaps (f)", "necklace of anguish", "zaryte vambraces"}) {
			Gear item = data.getGearItems().stream().filter(g -> g.getLname().equals(n) && g.isStandardGear())
				.findFirst().orElseThrow(() -> new AssertionError("missing " + n));
			owned.put(item.getId(), 1);
		}
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Service.RosterResult> out = new AtomicReference<>();
			ServiceCalls.bestPerStyleAcross(service, List.of(crab), Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(owned, true), 1, false, false, "", Collections.emptyMap(), -1,
				Req.DEFAULT_RISK_BUDGET_GP, false, false, Set.of(34681), 0, 1, Collections.emptyMap(), null,
				Collections.emptySet(), r -> {
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS));
			Dps best = out.get().pm.get(0).get(Style.RANGED).owned.get(0);
			assertEquals("Ascension crossbows", best.getLoadout().getWeapon().getName());
			assertTrue(best.getLoadout().get(Slot.AMMO).getLname().contains("ascension bolts"),
				best.getLoadout().get(Slot.AMMO).getName());
		} finally {
			service.shutdown();
		}
	}
}
