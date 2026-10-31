package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.Style;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RangedAmmo;
import com.loadoutlab.engine.RequirementProfile;
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
		LoadoutData data = new DataService().load();
		Monster crab = data.searchMonsters("gemstone crab", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (String n : new String[]{"toxic blowpipe", "masori body (f)", "masori chaps (f)", "necklace of anguish", "zaryte vambraces"}) {
			Gear item = data.getGearItems().stream().filter(g -> g.getNameLower().equals(n) && g.isStandardGear())
				.findFirst().orElseThrow(() -> new AssertionError("missing " + n));
			owned.put(item.getId(), 1);
		}
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
			ServiceCalls.bestPerStyleAcross(service, List.of(crab), PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 1, false, false, "", Collections.emptyMap(), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false, Set.of(34681), 0, 1, Collections.emptyMap(), null,
				Collections.emptySet(), r -> {
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS));
			DpsResult best = out.get().perMob.get(0).get(Style.RANGED).owned.get(0);
			assertEquals("Ascension crossbows", best.getLoadout().getWeapon().getName());
			assertTrue(best.getLoadout().get(GearSlot.AMMO).getNameLower().contains("ascension bolts"),
				best.getLoadout().get(GearSlot.AMMO).getName());
		} finally {
			service.shutdown();
		}
	}
}
