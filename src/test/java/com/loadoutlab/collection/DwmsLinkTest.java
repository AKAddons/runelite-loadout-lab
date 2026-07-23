package com.loadoutlab.collection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DwmsLinkTest
{
	/** A well-formed version-1 response payload, as DWMS builds it. */
	private static Map<String, Object> response(Object storages)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("source", "Dude, Where's My Stuff?");
		data.put("target", "Loadout Lab");
		data.put("version", 1);
		data.put("storages", storages);
		return data;
	}

	private static Map<String, Object> storage(String category, String name, Object items)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("category", category);
		data.put("name", name);
		data.put("lastUpdated", 1720549342765L);
		data.put("items", items);
		return data;
	}

	private static Map<String, Object> item(Object id, Object quantity)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("quantity", quantity);
		return data;
	}

	@Test
	@DisplayName("families group items by response category for the location hints")
	void familiesGroupByCategory()
	{
		DwmsLink link = new DwmsLink();
		assertTrue(link.accept(response(List.of(
			storage("stash", "Hosidius STASH", List.of(item(11864, 1))),
			storage("stash", "Falador STASH", List.of(item(11864, 1))),
			storage("death", "Grave", List.of(item(4151, 1)))))));
		assertEquals(Map.of(11864, 2), link.families().get("stash"),
			"same-category storages merge");
		assertEquals(Map.of(4151, 1), link.families().get("death"));
		assertEquals(2, link.families().size());
		link.reset();
		assertTrue(link.families().isEmpty(), "reset drops the families too");
	}

	@Test
	@DisplayName("a well-formed response goes live, merging duplicate items across storages")
	void wellFormedResponseParses()
	{
		DwmsLink link = new DwmsLink();
		assertFalse(link.isLive(), "not live before the first response");

		assertTrue(link.accept(response(List.of(
			storage("stash", "STASH Units", List.of(item(4151, 1L), item(995, 100L))),
			storage("poh", "Armour Case", List.of(item(4151, 2L)))))));

		assertTrue(link.isLive());
		assertEquals(2, link.count());
		assertEquals(3, link.snapshot().get(4151), "same item across storages sums");
		assertEquals(100, link.snapshot().get(995));
	}

	@Test
	@DisplayName("responses addressed to another plugin are ignored")
	void otherTargetsAreIgnored()
	{
		DwmsLink link = new DwmsLink();
		Map<String, Object> data = response(List.of(storage("stash", "STASH Units",
			List.of(item(4151, 1L)))));
		data.put("target", "Bank Memory");

		assertFalse(link.accept(data));
		assertFalse(link.isLive());
		assertTrue(link.snapshot().isEmpty());
	}

	@Test
	@DisplayName("an unknown contract version is rejected, leaving the config-read fallback in charge")
	void unknownVersionIsRejected()
	{
		DwmsLink link = new DwmsLink();
		Map<String, Object> data = response(List.of(storage("stash", "STASH Units",
			List.of(item(4151, 1L)))));
		data.put("version", 2);

		assertFalse(link.accept(data));
		assertFalse(link.isLive());
	}

	@Test
	@DisplayName("malformed payloads and entries are dropped, never guessed at")
	void defensiveParsingDropsMalformedEntries()
	{
		DwmsLink link = new DwmsLink();
		assertFalse(link.accept(null));
		assertFalse(link.accept(Map.of()));

		Map<String, Object> data = response("not a list");
		assertFalse(link.accept(data));

		assertTrue(link.accept(response(List.of(
			"not a map",
			storage("stash", "STASH Units", "not a list"),
			storage("poh", "Armour Case", List.of(
				"not a map",
				item("4151", 1L),
				item(4151, "1"),
				item(null, 1L),
				item(4151, null),
				item(-1, 1L),
				item(0, 1L),
				item(4151, 0L),
				item(4151, -3L),
				item(11832, 2L)))))));

		assertTrue(link.isLive(), "a valid response with junk entries still goes live");
		assertEquals(Map.of(11832, 2), link.snapshot());
	}

	@Test
	@DisplayName("quantities beyond int range clamp instead of overflowing")
	void hugeQuantitiesClamp()
	{
		DwmsLink link = new DwmsLink();
		assertTrue(link.accept(response(List.of(
			storage("coins", "Coffer", List.of(item(995, 999_999_999_999L)))))));
		assertEquals(Integer.MAX_VALUE, link.snapshot().get(995));
	}

	@Test
	@DisplayName("an empty response still counts as live - DWMS answered, it just tracks nothing")
	void emptyResponseIsLive()
	{
		DwmsLink link = new DwmsLink();
		assertTrue(link.accept(response(List.of())));
		assertTrue(link.isLive());
		assertEquals(0, link.count());
	}

	@Test
	@DisplayName("a newer response replaces the previous snapshot wholesale")
	void newerResponseReplaces()
	{
		DwmsLink link = new DwmsLink();
		assertTrue(link.accept(response(List.of(
			storage("stash", "STASH Units", List.of(item(4151, 1L)))))));
		assertTrue(link.accept(response(List.of(
			storage("stash", "STASH Units", List.of(item(11832, 1L)))))));

		assertEquals(Map.of(11832, 1), link.snapshot());
	}

	@Test
	@DisplayName("reset (identity change) drops the snapshot and live status")
	void resetClears()
	{
		DwmsLink link = new DwmsLink();
		assertTrue(link.accept(response(List.of(
			storage("stash", "STASH Units", List.of(item(4151, 1L)))))));

		link.reset();

		assertFalse(link.isLive());
		assertTrue(link.snapshot().isEmpty());
		assertEquals(0, link.count());
	}

	@Test
	@DisplayName("the request payload carries our display name as the source")
	void requestCarriesSource()
	{
		assertEquals("Loadout Lab", DwmsLink.request().get("source"));
	}

	@Test
	@DisplayName("merge folds the live items into an owned map, summing quantities")
	void mergeSumsIntoOwned()
	{
		DwmsLink link = new DwmsLink();
		assertTrue(link.accept(response(List.of(
			storage("stash", "STASH Units", List.of(item(4151, 1L)))))));

		Map<Integer, Integer> owned = new HashMap<>(Map.of(4151, 2));
		link.mergeInto(owned);
		assertEquals(3, owned.get(4151));
	}
}
