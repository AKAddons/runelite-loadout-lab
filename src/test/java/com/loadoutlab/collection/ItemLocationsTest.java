package com.loadoutlab.collection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemLocationsTest
{
	private static ItemLocations locations(Map<String, Map<Integer, Integer>> origins)
	{
		// Variant map for the tests: 100 and 101 are interchangeable.
		return new ItemLocations(origins,
			id -> id == 100 || id == 101 ? Set.of(100, 101) : Set.of(id));
	}

	private static Map<String, Map<Integer, Integer>> origins()
	{
		Map<String, Map<Integer, Integer>> origins = new LinkedHashMap<>();
		origins.put("equipped", Map.of(1, 1));
		origins.put("bank", Map.of(2, 1));
		origins.put("STASH", Map.of(3, 1, 101, 1));
		origins.put("POH costume room", Map.of(3, 1));
		origins.put("STASH" + ItemLocations.VIA_DWMS, Map.of(3, 1, 4, 1));
		origins.put("death storage" + ItemLocations.VIA_DWMS, Map.of(5, 1));
		return origins;
	}

	@Test
	@DisplayName("where() lists every origin holding the item, in display order")
	void whereListsOriginsInOrder()
	{
		assertEquals(List.of("STASH", "POH costume room", "STASH" + ItemLocations.VIA_DWMS),
			locations(origins()).where(3));
		assertTrue(locations(origins()).where(99).isEmpty());
	}

	@Test
	@DisplayName("a stored variant answers for the base id the optimizer suggests")
	void variantAnswersForBase()
	{
		assertEquals(List.of("STASH"), locations(origins()).where(100));
	}

	@Test
	@DisplayName("no hint when the item is at hand or not owned at all")
	void noHintWhenAtHandOrUnowned()
	{
		ItemLocations locations = locations(origins());
		assertEquals("", locations.fetchHint(1), "equipped is at hand");
		assertEquals("", locations.fetchHint(2), "banked is at hand");
		assertEquals("", locations.fetchHint(99), "unowned items get no hint");
	}

	@Test
	@DisplayName("the hint names remote storages, preferring native labels over DWMS twins")
	void hintNamesRemoteStores()
	{
		ItemLocations locations = locations(origins());
		assertEquals("stored in STASH + POH costume room", locations.fetchHint(3));
		assertEquals("stored in STASH" + ItemLocations.VIA_DWMS, locations.fetchHint(4),
			"a DWMS-only origin still names itself");
		assertEquals("stored in death storage" + ItemLocations.VIA_DWMS, locations.fetchHint(5));
	}

	@Test
	@DisplayName("the primary label folds DWMS twins onto native names and buckets the rest")
	void primaryLabels()
	{
		ItemLocations locations = locations(origins());
		assertEquals("equipped", locations.primary(1));
		assertEquals("bank", locations.primary(2));
		assertEquals("STASH", locations.primary(3), "first origin in display order wins");
		assertEquals("STASH", locations.primary(4), "DWMS twin folds onto the native name");
		assertEquals("DWMS", locations.primary(5), "DWMS-only family buckets as DWMS");
		assertEquals("", locations.primary(99), "unknown location has no label");
	}

	@Test
	@DisplayName("without a variant map, matching falls back to exact ids")
	void exactMatchingWithoutDataset()
	{
		ItemLocations locations = new ItemLocations(origins(), null);
		assertTrue(locations.where(100).isEmpty(), "101 no longer answers for 100");
		assertEquals(List.of("STASH"), locations.where(101));
	}
}
