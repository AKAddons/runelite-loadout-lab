package com.loadoutlab.data;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exact-name search used to return versions in corpus (alphabetical)
 * order, so the DEFAULT fight was usually the wrong one: "vorkath" gave
 * the Dragon Slayer II quest version, "vardorvis" gave Awakened, "zulrah"
 * gave Magma, "tzkal-zuk" gave Enraged (2026-07 player audit A3.3). The
 * headless harness and every name-based link-in take hits.get(0), so the
 * first exact hit must be the version a player means by the bare name.
 */
class MonsterVersionDefaultTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static String defaultVersion(String query)
	{
		List<MonsterStats> hits = data.searchMonsters(query, 5);
		assertFalse(hits.isEmpty(), "no hits for " + query);
		return hits.get(0).getVersion() == null ? "" : hits.get(0).getVersion();
	}

	@Test
	@DisplayName("bare boss names default to the everyday version, not quest/awakened/enraged")
	void everydayVersionsWin()
	{
		assertEquals("Post-quest", defaultVersion("vorkath"));
		assertEquals("Post-quest", defaultVersion("vardorvis"));
		assertEquals("Post-quest", defaultVersion("the whisperer"));
		assertEquals("Post-quest", defaultVersion("the leviathan"));
		// Normal and Enraged Zuk share a stat block, so the loader collapses
		// them into one unversioned row - either label would compute the same.
		assertEquals("", defaultVersion("tzkal-zuk"));
		assertEquals("Serpentine", defaultVersion("zulrah"));
	}

	@Test
	@DisplayName("multi-mode bosses default to the everyday-numbers row, delves to the first")
	void modesAndDelves()
	{
		// ToB's Normal-mode rows stat-collapse into the Hard-labeled ones
		// (identical defensive block), so the hard-labeled Phase 1 row IS the
		// everyday fight's numbers; Entry mode (genuinely weaker) never leads.
		assertTrue(defaultVersion("verzik vitur").toLowerCase().startsWith("hard mode, phase 1"),
			"got: " + defaultVersion("verzik vitur"));
		assertFalse(defaultVersion("doom of mokhaiotl").toLowerCase().contains("deep"),
			"deep delve must not be the default: " + defaultVersion("doom of mokhaiotl"));
	}

	@Test
	@DisplayName("asking for a specific version still finds it")
	void explicitVersionStillReachable()
	{
		List<MonsterStats> hits = data.searchMonsters("zulrah", 5);
		assertTrue(hits.stream().anyMatch(m -> "Magma".equals(m.getVersion())),
			"all versions stay reachable in the hit list");
		assertTrue(hits.stream().anyMatch(m -> "Tanzanite".equals(m.getVersion())));
	}
}
