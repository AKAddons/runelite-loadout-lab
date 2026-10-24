package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.PlayerLevels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stat sim through the engine (ISS-01M5QY13K6YHTYDW1CCYD30CSY): one
 * sim-stats command carries the seven levels and the max cape tick, is one
 * recompute and one undo step; the live levels ride the params node; the
 * report prints the sim while it is on.
 */
class StatSimParamsTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static final class Link extends CompanionLink {
		private Map<String, Object> last;

		@Override
		public void publishPage(Map<String, Object> page) {
			last = page;
		}

		@Override
		public Map<String, Object> lastPage() {
			return last;
		}
	}

	private static Map<String, Object> stats(int attack, int strength, int defence, int ranged, int magic, int prayer,
		int hitpoints, boolean maxCape) {
		return Map.of("attack", attack, "strength", strength, "defence", defence, "ranged", ranged, "magic", magic,
			"prayer", prayer, "hitpoints", hitpoints, "maxCape", maxCape);
	}

	@Test
	@DisplayName("sim-stats with 99 Attack and the max cape: one recompute carrying simLevels 99/0/0/0/0/0/0 and the cape; one undo step takes it back; Mine clears it")
	void simStatsIsOneStep() {
		AtomicInteger computes = new AtomicInteger();
		AtomicReference<ComputeParams> last = new AtomicReference<>();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
			computes.incrementAndGet();
			last.set(p);
		}, new Link());
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		assertEquals(1, computes.get());
		assertNull(last.get().simLevels, "nothing simmed: your own levels");

		assertTrue(engine.execute("sim-stats", stats(99, 0, 0, 0, 0, 0, 0, true)));
		assertEquals(2, computes.get(), "one recompute for the whole row");
		assertArrayEquals(new int[]{99, 0, 0, 0, 0, 0, 0}, last.get().simLevels);
		assertTrue(last.get().simMaxCape);
		assertEquals(99, ((Number) state.paramsNode().get("simAttack")).intValue());

		// Undo restores the page it had, no recompute: the state goes back.
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(0, ((Number) state.paramsNode().get("simAttack")).intValue(), "undone in one step");
		assertFalse((Boolean) state.paramsNode().get("simMaxCape"));
		assertNull(state.computeParams().simLevels);

		assertTrue(engine.execute("sim-stats", stats(0, 0, 0, 0, 0, 0, 50, false)));
		assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 50}, last.get().simLevels, "lower works too");
		assertTrue(engine.execute("sim-stats", stats(0, 0, 0, 0, 0, 0, 0, false)), "Mine");
		assertNull(last.get().simLevels);
	}

	@Test
	@DisplayName("the live levels ride the params node as myLevels, and the report prints the sim only while it is on")
	void liveLevelsAndTheReport() {
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, new Link());
		engine.setLevels(new PlayerLevels(70, 70, 70, 80, 85, 60, 80));
		assertEquals("70/70/70/80/85/60/80", state.paramsNode().get("myLevels"));

		MonsterStats demon = data.searchMonsters("abyssal demon", 1).get(0);
		String off = ReportBuilder.build("test", state, List.of(demon), List.of(Map.of()), 0,
			Map.of(), null, null, List.of(), false, List.of(Map.of("id", demon.getId())));
		assertFalse(off.contains("Stats:"), off);

		assertTrue(engine.execute("sim-stats", stats(99, 0, 0, 0, 0, 0, 0, true)));
		String on = ReportBuilder.build("test", state, List.of(demon), List.of(Map.of()), 0,
			Map.of(), null, null, List.of(), false, List.of(Map.of("id", demon.getId())));
		assertTrue(on.contains("Stats: simmed 99/70/70/80/85/60/80 + max cape"), on);
	}
}
