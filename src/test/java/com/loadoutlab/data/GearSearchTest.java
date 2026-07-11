package com.loadoutlab.data;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GearSearchTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	@Test
	@DisplayName("an exact item name is the first result")
	void exactNameFirst()
	{
		List<GearItem> results = data.searchGear("abyssal whip", 10);
		assertFalse(results.isEmpty());
		assertEquals("abyssal whip", results.get(0).getNameLower());
	}

	@Test
	@DisplayName("prefix matches rank ahead of substring matches and the limit holds")
	void prefixBeforeContainsWithinLimit()
	{
		List<GearItem> results = data.searchGear("abyssal w", 5);
		assertFalse(results.isEmpty());
		assertTrue(results.size() <= 5);
		assertTrue(results.get(0).labelLower().startsWith("abyssal w"),
			"first result should be a prefix match, got: " + results.get(0).label());
		for (GearItem item : results)
		{
			assertTrue(item.labelLower().contains("abyssal w"));
		}
	}

	@Test
	@DisplayName("a numeric query matches the item id exactly")
	void idQueryMatches()
	{
		List<GearItem> results = data.searchGear("4151", 5);
		assertFalse(results.isEmpty());
		assertEquals(4151, results.get(0).getId());
	}

	@Test
	@DisplayName("blank and unknown queries return no matches")
	void blankAndUnknownReturnEmpty()
	{
		assertTrue(data.searchGear("   ", 10).isEmpty());
		assertTrue(data.searchGear(null, 10).isEmpty());
		assertTrue(data.searchGear("zzz not an item zzz", 10).isEmpty());
	}

	@Test
	@DisplayName("queries are case-insensitive")
	void caseInsensitive()
	{
		List<GearItem> lower = data.searchGear("dragon dagger", 5);
		List<GearItem> upper = data.searchGear("DRAGON Dagger", 5);
		assertFalse(lower.isEmpty());
		assertEquals(lower.get(0).getId(), upper.get(0).getId());
		assertEquals("dragon dagger",
			lower.get(0).getNameLower().toLowerCase(Locale.ROOT));
	}
}
