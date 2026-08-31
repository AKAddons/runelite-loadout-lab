package com.loadoutlab.data;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The naval roster is a CURATED list (the corpus carries no naval flag), so
 * this net proves every name still resolves to a real corpus row - a corpus
 * regeneration that renames a sea monster goes red here instead of silently
 * un-navaling it.
 */
class NavalCombatTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	@Test
	@DisplayName("every curated naval name resolves to a corpus row with heavy defence")
	void rosterResolvesAgainstTheCorpus()
	{
		assertFalse(NavalCombat.navalNames().isEmpty());
		StringBuilder missing = new StringBuilder();
		for (String name : NavalCombat.navalNames())
		{
			List<MonsterStats> hits = data.searchMonsters(name, 1);
			if (hits.isEmpty() || !hits.get(0).getName().equalsIgnoreCase(name))
			{
				missing.append("\n  ").append(name)
					.append(hits.isEmpty() ? " (no hit)" : " (top hit: " + hits.get(0).getName() + ")");
			}
		}
		assertEquals(0, missing.length(),
			"curated naval names without a matching corpus row:" + missing);
	}

	@Test
	@DisplayName("naval detection is name-exact and case-insensitive")
	void detection()
	{
		assertTrue(NavalCombat.isNaval("Hammerhead shark"));
		assertTrue(NavalCombat.isNaval("hammerhead SHARK"));
		assertFalse(NavalCombat.isNaval("General Graardor"));
		assertFalse(NavalCombat.isNaval(null));
		// The land shark stays a land monster: only the exact curated names
		// count, never substring matches.
		assertFalse(NavalCombat.isNaval("Shark"));
	}
}
