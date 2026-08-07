package com.loadoutlab.data;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Version-label normalization (field spec 2026-07-18): the noise goes
 * ("Awake", "Post-quest"), quest-only rows read "QUEST" loudly and sort
 * to the bottom of a bare-name search - the everyday fight owns the
 * bare name.
 */
class QuestVersionTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static List<String> versions(String name)
	{
		return data.searchMonsters(name, 10).stream()
			.filter(m -> m.getName().equalsIgnoreCase(name))
			.map(MonsterStats::getVersion)
			.collect(Collectors.toList());
	}

	@Test
	@DisplayName("Vorkath: the everyday fight is bare, the DS2 fight reads QUEST and sorts last")
	void vorkath()
	{
		List<String> versions = versions("Vorkath");
		assertEquals(2, versions.size());
		assertEquals("", versions.get(0), "post-quest owns the bare name and leads");
		assertEquals("QUEST", versions.get(1));
	}

	@Test
	@DisplayName("Duke Sucellus: bare / Awakened / QUEST, noise tokens gone")
	void dukeSucellus()
	{
		List<String> versions = versions("Duke Sucellus");
		assertEquals(3, versions.size());
		assertEquals("", versions.get(0), "the everyday fight leads");
		assertTrue(versions.contains("Awakened"), versions.toString());
		assertTrue(versions.contains("QUEST"), versions.toString());
		for (String v : versions)
		{
			assertFalse(v.toLowerCase().contains("awake,")
				|| v.toLowerCase().contains(", awake")
				|| v.toLowerCase().contains("post-quest"), "noise token survived: " + v);
		}
	}
}
