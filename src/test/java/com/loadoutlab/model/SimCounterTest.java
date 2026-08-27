package com.loadoutlab.model;

import com.google.gson.Gson;
import com.loadoutlab.collection.DreamStore;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-08-27: "when i add things to my sim the counter is not
 * going up."
 *
 * <p>The green "+N" pill reads counts.simmedItems off the PUBLISHED page, not
 * off the store, so a toggle that persists correctly can still leave the pill
 * stale if the page is not rebuilt from the live counts. This wires the real
 * DreamStore behind the engine and asserts the number the pill would draw.
 */
class SimCounterTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	/** The count the "+N" pill renders, straight off the published page. */
	private static int pillCount(CompanionLink link, String listKey)
	{
		drainEdt();
		Map<String, Object> page = link.lastPage();
		assertNotNull(page, "nothing was published");
		Map<?, ?> counts = (Map<?, ?>) page.get("counts");
		assertNotNull(counts, "the page carried no counts node");
		return ((List<?>) counts.get(listKey)).size();
	}

	/** republish() hops to the EDT, so the page lands after execute() returns. */
	private static void drainEdt()
	{
		try
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	@Test
	@DisplayName("the sim pill counts up as items are simmed")
	void simPillTracksTheStore()
	{
		DreamStore dreams = new DreamStore(InMemoryConfigManager.create(), new Gson());
		dreams.loadScope("std.1111");

		PageState state = new PageState();
		CompanionLink link = new CompanionLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.setCounts(() ->
		{
			List<Map<String, Object>> simmed = new ArrayList<>();
			for (int id : dreams.snapshot())
			{
				simmed.add(Map.of("id", id, "name", "item " + id));
			}
			Map<String, Object> counts = new LinkedHashMap<>();
			counts.put("simmed", simmed.size());
			counts.put("simmedItems", simmed);
			return counts;
		});
		engine.setStoreOps(new TestStoreOps()
		{
			@Override
			public boolean toggleSim(int itemId)
			{
				dreams.toggle(itemId);
				return true;
			}
		});

		engine.execute("toggle-sim", Map.of("itemId", 22325, "label", "item"));
		assertTrue(dreams.isDreamed(22325), "the store did not take the sim");
		assertEquals(1, pillCount(link, "simmedItems"),
			"the sim pill did not count up after the first sim");

		engine.execute("toggle-sim", Map.of("itemId", 25975, "label", "item"));
		assertEquals(2, pillCount(link, "simmedItems"),
			"the sim pill did not count up after the second sim");

		// ...and back down, since the command is self-inverse.
		engine.execute("toggle-sim", Map.of("itemId", 22325, "label", "item"));
		assertEquals(1, pillCount(link, "simmedItems"),
			"unsimming did not count the pill back down");
	}
}
