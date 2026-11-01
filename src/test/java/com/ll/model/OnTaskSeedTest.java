package com.ll.model;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew (2026-10-19): "for monsters that could be perceived as slayer,
 * we should default to the slayer chip on because most people encounter
 * things through slayer". Selection seeds On task from the mob: on for
 * a slayer monster, off otherwise, always off for a raid.
 */
class OnTaskSeedTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static CommandEngine engine(PageState state) {
		CommandEngine engine = new CommandEngine(data, state,
			(mob, p, onDone) -> {
			},
			new CompanionLink());
		engine.setStoreOps(new TestStoreOps());
		return engine;
	}

	private static Monster mob(String query, String name) {
		Monster mob = data.searchMonsters(query, 1).get(0);
		assertEquals(name, mob.getName());
		return mob;
	}

	@Test
	@DisplayName("a slayer monster seeds On task on; a non-slayer mob after it seeds it off")
	void slayerMonsterSeedsOn() {
		PageState state = new PageState();
		CommandEngine engine = engine(state);
		Monster demon = mob("abyssal demon", "Abyssal demon");
		Monster orca = mob("orca", "Orca");
		assertTrue(demon.isSlayerMonster(), "fixture: the demon is a slayer monster");
		assertFalse(orca.isSlayerMonster(), "fixture: the orca is not");
		engine.execute("select", Map.of("id", demon.getId()));
		assertEquals(true, state.pn().get("onTask"), "a slayer monster opens on task");
		engine.execute("select", Map.of("id", orca.getId()));
		assertEquals(false, state.pn().get("onTask"), "a non-slayer mob opens off task");
	}

	@Test
	@DisplayName("a task-only boss is on task and a raid is never on task")
	void taskOnlyAndRaid() {
		PageState state = new PageState();
		CommandEngine engine = engine(state);
		Monster sire = mob("abyssal sire", "Abyssal Sire");
		engine.execute("select", Map.of("id", sire.getId()));
		assertEquals(true, state.pn().get("onTask"));
		engine.execute("select", Map.of("query", "Chambers of Xeric"));
		assertNotNull(state.rmobs(), "fixture: the raid selected as a roster");
		assertEquals(false, state.pn().get("onTask"), "raids are never a task context");
	}
}
