package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.Spell;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Discord (Apathy, 2026-09-06): "pin another item -> warped sceptre
 * doesn't pin it over my items ... I have to exclude every other staff".
 * A weapon pin forces the slot; the set is optimised around it. */
class PinnedPoweredStaffTest {
	private static Tables data;
	private static Monster zulrah;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		zulrah = data.searchMonsters("Zulrah", 1).get(0);
	}

	@Test
	@DisplayName("a pinned warped sceptre holds the weapon slot at Zulrah with the spell on auto")
	void autoSpell() throws Exception {
		Service.Pick magic = magic(null);
		assertEquals(28585, magic.owned.get(0).getLoadout().getWeapon().getId());
	}

	@Test
	@DisplayName("a pinned warped sceptre still holds the slot when a spell is pinned too - the staff's built-in spell wins")
	void pinnedSpell() throws Exception {
		Spell fireBolt = data.getSpells().stream().filter(s -> s.getName().equals("Fire Bolt")).findFirst().orElseThrow();
		Service.Pick magic = magic(fireBolt);
		assertNotNull(magic);
		assertTrue(!magic.owned.isEmpty(), "the card must still have an answer");
		assertEquals(28585, magic.owned.get(0).getLoadout().getWeapon().getId());
	}

	private static Service.Pick magic(Spell pinnedSpell) throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(11905, 1); // trident of the seas
		owned.put(28585, 1); // warped sceptre
		owned.put(1387, 1); // staff of fire
		owned.put(12002, 1); // occult necklace
		Map<Slot, Integer> pins = new EnumMap<>(Slot.class);
		pins.put(Slot.WEAPON, 28585);
		Map<Style, Map<Slot, Integer>> byStyle = new EnumMap<>(Style.class);
		byStyle.put(Style.MAGIC, pins);
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, zulrah, Levels.MAXED, Levels.MAXED,
				Prayers.ALL, RequirementProfile.MAXED, new Owned(owned, true), 1, false, false, "",
				Collections.emptySet(), -1, Req.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
				byStyle, pinnedSpell, results -> {
					out.set(results);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS));
			return out.get().get(Style.MAGIC);
		} finally {
			service.shutdown();
		}
	}
}
