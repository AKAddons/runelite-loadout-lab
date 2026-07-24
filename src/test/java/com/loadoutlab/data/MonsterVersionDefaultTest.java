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
		assertEquals("", defaultVersion("vorkath"));
		assertEquals("", defaultVersion("vardorvis"));
		assertEquals("", defaultVersion("the whisperer"));
		assertEquals("", defaultVersion("the leviathan"));
		// Normal and Enraged Zuk share a stat block, so the loader collapses
		// them into one unversioned row - either label would compute the same.
		assertEquals("", defaultVersion("tzkal-zuk"));
		assertEquals("Serpentine", defaultVersion("zulrah"));
	}

	@Test
	@DisplayName("multi-mode bosses default to the everyday-numbers row, delves to the first")
	void modesAndDelves()
	{
		// ToB's Normal-mode rows now stay distinct from Hard/Entry (issue #2),
		// so the everyday Normal Phase 1 row leads; Hard and Entry (genuinely
		// harder/weaker) never lead but stay reachable below.
		assertTrue(defaultVersion("verzik vitur").toLowerCase().startsWith("normal mode, phase 1"),
			"got: " + defaultVersion("verzik vitur"));
		assertFalse(defaultVersion("doom of mokhaiotl").toLowerCase().contains("deep"),
			"deep delve must not be the default: " + defaultVersion("doom of mokhaiotl"));
	}

	@Test
	@DisplayName("ToB bosses keep distinct Entry/Normal/Hard rows; phase spawns still collapse (issue #2)")
	void tobDifficultiesStaySeparate()
	{
		// Before the fix, the Normal defensive block collapsed into the
		// higher-level Hard row and vanished. Each difficulty must now be its
		// own selectable entry.
		for (String boss : new String[]{"the maiden of sugadinti", "verzik vitur"})
		{
			List<MonsterStats> hits = data.searchMonsters(boss, 20);
			assertTrue(hits.stream().anyMatch(m -> tierOf(m).equals("entry")),
				boss + " lost its Entry entry: " + versionsOf(hits));
			assertTrue(hits.stream().anyMatch(m -> tierOf(m).equals("normal")),
				boss + " lost its Normal entry: " + versionsOf(hits));
			assertTrue(hits.stream().anyMatch(m -> tierOf(m).equals("hard")),
				boss + " lost its Hard entry: " + versionsOf(hits));
		}
		// The Maiden's "30%/50%/70% Health" phase rows are same-difficulty
		// duplicates of Normal - they must NOT surface as selectable entries.
		List<MonsterStats> maiden = data.searchMonsters("the maiden of sugadinti", 20);
		assertTrue(maiden.stream().noneMatch(m -> m.getVersion().toLowerCase().contains("health")),
			"phase-health rows must collapse into Normal: " + versionsOf(maiden));
		// The surviving Normal row carries the full-health numbers, not a
		// phase row's reduced HP.
		MonsterStats normal = maiden.stream()
			.filter(m -> tierOf(m).equals("normal")).findFirst().orElseThrow(AssertionError::new);
		assertEquals("Normal", normal.getVersion());
		assertEquals(3500, normal.getHitpoints());
	}

	private static String tierOf(MonsterStats m)
	{
		String v = m.getVersion() == null ? "" : m.getVersion().toLowerCase();
		if (v.contains("entry"))
		{
			return "entry";
		}
		if (v.contains("hard"))
		{
			return "hard";
		}
		return "normal";
	}

	private static String versionsOf(List<MonsterStats> hits)
	{
		StringBuilder sb = new StringBuilder();
		for (MonsterStats m : hits)
		{
			sb.append('[').append(m.getVersion()).append(']');
		}
		return sb.toString();
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
