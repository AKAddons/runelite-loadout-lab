package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A weapon pin binds only its own styles (ISS-01M5TH7V74GYQKRN231XRCM6BE,
 * Andrew 2026-10-25, the Enraged Warden with a trident pinned: "i only have a
 * magic set availalbe but clearly have a more applicaable range set with the
 * bowfa").
 */
class WeaponPinStyleTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static int id(String name, String version) {
		return data.getGearItems().stream()
			.filter(g -> g.getLname().equals(name) && g.getVersion().equalsIgnoreCase(version))
			.findFirst().orElseThrow(() -> new AssertionError(name + " (" + version + ")")).getId();
	}

	private static String weapon(Service.Pick result) {
		Gear weapon = result.owned.get(0).getLoadout().get(Slot.WEAPON);
		return weapon == null ? "none" : weapon.getLname();
	}

	@Test
	@DisplayName("a trident pinned for every style: magic wears it, ranged still builds with the bow of faerdhinen")
	void pinKeepsToItsStyle() throws Exception {
		Monster warden = data.searchMonsters("tumeken's warden", 1).get(0);
		int trident = id("trident of the seas", "charged");
		int bowfa = id("bow of faerdhinen", "charged");
		Owned owned = new Owned(Map.of(trident, 1, bowfa, 1), true);
		Map<Style, Map<Slot, Integer>> pins = new EnumMap<>(Style.class);
		for (Style style : Style.values()) { pins.put(style, Map.of(Slot.WEAPON, trident)); }
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			service.bestPerStyle(warden, Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, owned, 1, new ComputeParams(), Collections.emptyMap(), Collections.emptySet(),
				pins, null, 0, Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			assertEquals("trident of the seas", weapon(out.get().get(Style.MAGIC)));
			Service.Pick ranged = out.get().get(Style.RANGED);
			assertFalse(ranged.owned.isEmpty(), "ranged still builds");
			assertEquals("bow of faerdhinen", weapon(ranged));
		} finally {
			service.shutdown();
		}
	}
}
