package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellRunes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sunfire assumption on the card (Kendyy, Discord 2026-10-19): a
 * page param seeded from ownership at each selection, toggled from the
 * card's sunfire plate, passed to every compute, re-seeded by Detect.
 */
class SunfireAssumptionTest
{
	private static final int SUNFIRE_RUNE = 28929;
	private static final int FIRE_RUNE = 554;
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static CommandEngine engine(AtomicReference<Boolean> seen, boolean ownsSunfire)
	{
		CommandEngine engine = new CommandEngine(data, new PageState(),
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, sunfire, dc, spec,
				boosts, prayers, budget, swaps, onDone) -> seen.set(sunfire),
			new CompanionLink());
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> ownsSunfire && id == SUNFIRE_RUNE);
		return engine;
	}

	@Test
	@DisplayName("selection seeds the sunfire assumption from ownership and passes it to the compute")
	void seededFromOwnership()
	{
		AtomicReference<Boolean> seen = new AtomicReference<>();
		CommandEngine engine = engine(seen, true);
		MonsterStats araxxor = data.searchMonsters("araxxor", 1).get(0);
		engine.execute("select", Map.of("id", araxxor.getId()));
		assertEquals(Boolean.TRUE, seen.get(), "owned sunfire runes are assumed");

		AtomicReference<Boolean> none = new AtomicReference<>();
		engine(none, false).execute("select", Map.of("id", araxxor.getId()));
		assertEquals(Boolean.FALSE, none.get(), "no sunfire runes, no assumption");
	}

	@Test
	@DisplayName("a simmed sunfire rune counts as owned for the seed (Andrew, client test 2026-10-19)")
	void simmedRuneSeeds()
	{
		AtomicReference<Boolean> seen = new AtomicReference<>();
		CommandEngine engine = engine(seen, false);
		engine.setCounts(() -> Map.of("simmedItems",
			List.of(Map.of("id", SUNFIRE_RUNE, "name", "Sunfire rune"))));
		MonsterStats araxxor = data.searchMonsters("araxxor", 1).get(0);
		engine.execute("select", Map.of("id", araxxor.getId()));
		assertEquals(Boolean.TRUE, seen.get(), "a sim is 'count this as owned'");
		engine.execute("set-param", Map.of("param", "sunfireRunes", "value", false));
		assertTrue(engine.execute("detect-sunfire", Map.of()));
		assertEquals(Boolean.TRUE, seen.get(), "Detect best sees the sim too");
	}

	@Test
	@DisplayName("the plate's toggle sticks and Detect re-seeds it")
	void toggleAndDetect()
	{
		AtomicReference<Boolean> seen = new AtomicReference<>();
		CommandEngine engine = engine(seen, true);
		MonsterStats araxxor = data.searchMonsters("araxxor", 1).get(0);
		engine.execute("select", Map.of("id", araxxor.getId()));
		engine.execute("set-param", Map.of("param", "sunfireRunes", "value", false));
		assertEquals(Boolean.FALSE, seen.get(), "the manual pick reaches the compute");
		assertTrue(engine.execute("detect-sunfire", Map.of()));
		assertEquals(Boolean.TRUE, seen.get(), "Detect runs the ownership seed again");
	}

	@Test
	@DisplayName("with the assumption on, the trip runes carry sunfire runes in place of fire runes")
	void tripRunesSwap()
	{
		List<Map<String, Object>> runes = SpellRunes.costFor("Fire Surge", null, null);
		assertTrue(ids(runes).contains(FIRE_RUNE), "fixture: a bare Fire Surge costs fire runes");
		List<Map<String, Object>> swapped = SpellRunes.withSunfire(runes);
		assertFalse(ids(swapped).contains(FIRE_RUNE));
		assertTrue(ids(swapped).contains(SUNFIRE_RUNE));
		Map<String, Object> sunfire = swapped.stream()
			.filter(r -> ((Number) r.get("id")).intValue() == SUNFIRE_RUNE).findFirst().orElseThrow();
		assertEquals("Sunfire rune", sunfire.get("name"));
		assertEquals(runes.stream().filter(r -> ((Number) r.get("id")).intValue() == FIRE_RUNE)
			.findFirst().orElseThrow().get("qty"), sunfire.get("qty"), "same count per cast");
		List<Map<String, Object>> water = SpellRunes.costFor("Water Surge", null, null);
		assertEquals(ids(water), ids(SpellRunes.withSunfire(water)), "a water spell's runes are untouched");
	}

	private static List<Integer> ids(List<Map<String, Object>> runes)
	{
		List<Integer> out = new ArrayList<>();
		for (Map<String, Object> r : runes)
		{
			out.add(((Number) r.get("id")).intValue());
		}
		return out;
	}
}
