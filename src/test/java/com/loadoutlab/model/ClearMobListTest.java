package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, 2026-09-18: "for each of the exclude/sim/item filters can you
 * have a reset all in list option". One command, one recompute, one undo
 * step that puts every entry back. */
class ClearMobListTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	/** Store ops with a real per-mob exclusion list. */
	private static final class ListOps extends TestStoreOps
	{
		final List<Map<String, Object>> exclusions = new ArrayList<>();

		@Override
		public List<Map<String, Object>> mobExclusions(int monsterId)
		{
			return new ArrayList<>(exclusions);
		}

		@Override
		public void removeMobExclusion(int monsterId, String scope, int itemId)
		{
			exclusions.removeIf(e -> ((Number) e.get("id")).intValue() == itemId && scope.equals(e.get("scope")));
		}

		@Override
		public void excludeForMob(int monsterId, String scope, int itemId)
		{
			exclusions.add(Map.of("id", itemId, "name", "item " + itemId, "scope", scope));
		}
	}

	@Test
	@DisplayName("clear-mob-list empties the mob's exclusions in one recompute and undo restores every entry")
	void clearAndUndo()
	{
		java.util.concurrent.atomic.AtomicInteger computes = new java.util.concurrent.atomic.AtomicInteger();
		CommandEngine engine = new CommandEngine(data, new PageState(),
			(mob, f2p, onTask, wild, book, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) -> computes.incrementAndGet(), new CompanionLink());
		ListOps ops = new ListOps();
		ops.exclusions.add(Map.of("id", 4151, "name", "Abyssal whip", "scope", "ALL"));
		ops.exclusions.add(Map.of("id", 11802, "name", "Armadyl godsword", "scope", "MELEE"));
		engine.setStoreOps(ops);
		assertTrue(engine.execute("select", Map.of("query", "goblin")));
		int before = computes.get();

		assertTrue(engine.execute("clear-mob-list", Map.of("kind", "mobExclusions")));
		assertTrue(ops.exclusions.isEmpty(), "both entries gone");
		assertEquals(before + 1, computes.get(), "one recompute for the whole clear");

		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(2, ops.exclusions.size(), "undo puts every entry back");
		assertTrue(ops.exclusions.stream().anyMatch(e -> "MELEE".equals(e.get("scope"))), "with its scope");
	}
}
