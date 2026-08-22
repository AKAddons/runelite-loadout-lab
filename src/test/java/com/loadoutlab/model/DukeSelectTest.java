package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Shared-npc-id selection (the Duke disease, id-collision family
 * member four): Awakened and Post-quest Duke are both id 12191 with
 * Awakened first in corpus order. */
class DukeSelectTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static CommandEngine engine(AtomicReference<MonsterStats> computed)
	{
		PageState state = new PageState();
		return new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) -> computed.set(mob),
			new CompanionLink());
	}

	@Test
	@DisplayName("a versioned dropdown pick selects exactly its row")
	void versionedPickSelectsItsRow()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandEngine engine = engine(computed);
		assertTrue(engine.execute("select",
			Map.of("id", 12191, "version", "Awakened")));
		assertEquals("Awakened", computed.get().getVersion());
		assertTrue(engine.execute("select", Map.of("id", 12191, "version", "")));
		assertEquals("", computed.get().getVersion());
	}

	@Test
	@DisplayName("a bare shared id selects the search-ranked everyday row")
	void bareIdPrefersTheEverydayRow()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandEngine engine = engine(computed);
		assertTrue(engine.execute("select", Map.of("id", 12191)));
		assertEquals("", computed.get().getVersion(),
			"the post-quest Duke, never Awakened");
	}

	@Test
	@DisplayName("the dropdown rows carry their version")
	void dropdownRowsCarryVersion()
	{
		CommandEngine engine = engine(new AtomicReference<>());
		boolean sawVersioned = engine.searchOptions("duke sucellus").stream()
			.anyMatch(o -> "Awakened".equals(o.get("version")));
		assertTrue(sawVersioned, "the Awakened row declares itself");
	}
}
