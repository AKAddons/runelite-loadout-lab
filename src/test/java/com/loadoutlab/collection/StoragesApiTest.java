package com.loadoutlab.collection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoragesApiTest
{
	private static final IntUnaryOperator IDENTITY = IntUnaryOperator.identity();

	@Test
	@DisplayName("a request needs a non-empty source; anything else has no requester")
	void requesterValidation()
	{
		assertEquals("Some Plugin", StoragesApi.requester(Map.of("source", "Some Plugin")));
		assertNull(StoragesApi.requester(null));
		assertNull(StoragesApi.requester(Map.of()));
		assertNull(StoragesApi.requester(Map.of("source", "")));
		assertNull(StoragesApi.requester(Map.of("source", 42)));
	}

	@Test
	@DisplayName("a storage entry carries category, name, lastUpdated, and the item list")
	void storageEntryShape()
	{
		Map<String, Object> entry = StoragesApi.storage(
			"collection", "bank", -1L, Map.of(4151, 1), IDENTITY);

		assertNotNull(entry);
		assertEquals("collection", entry.get("category"));
		assertEquals("bank", entry.get("name"));
		assertEquals(-1L, entry.get("lastUpdated"));
		List<?> items = (List<?>) entry.get("items");
		assertEquals(1, items.size());
		Map<?, ?> item = (Map<?, ?>) items.get(0);
		assertEquals(4151, item.get("id"));
		assertEquals(1L, item.get("quantity"), "quantities go out as Long, per the contract");
	}

	@Test
	@DisplayName("ids are canonicalized, and variants that collapse to one id sum quantities")
	void canonicalizationMergesVariants()
	{
		// Pretend 4151 (whip) and 12773 (whip (or)) both canonicalize to 4151.
		IntUnaryOperator canonicalize = id -> id == 12773 ? 4151 : id;
		Map<Integer, Integer> items = new HashMap<>();
		items.put(4151, 1);
		items.put(12773, 2);

		Map<String, Object> entry = StoragesApi.storage(
			"collection", "bank", -1L, items, canonicalize);

		assertNotNull(entry);
		List<?> list = (List<?>) entry.get("items");
		assertEquals(1, list.size());
		Map<?, ?> item = (Map<?, ?>) list.get(0);
		assertEquals(4151, item.get("id"));
		assertEquals(3L, item.get("quantity"));
	}

	@Test
	@DisplayName("empty or all-junk storages produce no entry, and junk items are dropped")
	void emptyStoragesProduceNoEntry()
	{
		assertNull(StoragesApi.storage("collection", "bank", -1L, Map.of(), IDENTITY));

		Map<Integer, Integer> junk = new HashMap<>();
		junk.put(-1, 5);
		junk.put(0, 5);
		junk.put(4151, 0);
		junk.put(11832, -2);
		assertNull(StoragesApi.storage("collection", "bank", -1L, junk, IDENTITY));
	}

	@Test
	@DisplayName("the response envelope echoes the requester and states version 1")
	void responseEnvelope()
	{
		List<Map<String, Object>> storages = List.of(
			StoragesApi.storage("collection", "bank", -1L, Map.of(4151, 1), IDENTITY));

		Map<String, Object> response = StoragesApi.response("Some Plugin", storages);

		assertEquals("Loadout Lab", response.get("source"));
		assertEquals("Some Plugin", response.get("target"));
		assertEquals(1, response.get("version"));
		assertEquals(storages, response.get("storages"));
	}
}
