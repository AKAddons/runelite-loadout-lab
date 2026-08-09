package com.loadoutlab.data;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The required-gear registry is name-keyed like the notes, so every
 * curated key is pinned against LOADED rows - a corpus rename must
 * fail here, not silently orphan a requirement (grZ field request
 * 2026-08-08, every row wiki-verified before encoding).
 */
class RequiredGearTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	@Test
	@DisplayName("every curated monster key binds to a loaded corpus row")
	void keysBindToCorpus()
	{
		for (String key : RequiredGear.monsterKeys())
		{
			List<MonsterStats> hits = data.searchMonsters(key, 10);
			assertTrue(hits.stream().anyMatch(
				m -> m.getName().equalsIgnoreCase(key)),
				"no corpus row named: " + key);
		}
	}

	@Test
	@DisplayName("every rule's items resolve to standard gear in its slot")
	void itemsResolve()
	{
		for (String key : RequiredGear.monsterKeys())
		{
			MonsterStats mob = data.searchMonsters(key, 10).stream()
				.filter(m -> m.getName().equalsIgnoreCase(key))
				.findFirst().orElseThrow();
			RequiredGear.Rule rule = RequiredGear.ruleFor(mob);
			assertNotNull(rule, key);
			Set<Integer> ids = rule.ids(data);
			assertFalse(ids.isEmpty(), key + ": no item resolved");
			for (int id : ids)
			{
				GearItem item = data.getGear(id);
				assertNotNull(item, key + ": id " + id);
				assertEquals(rule.slot, item.getSlot(),
					key + ": " + item.label() + " is not in the rule's slot");
			}
			assertNotNull(rule.note, key);
		}
	}

	@Test
	@DisplayName("the basilisk family requires a gaze shield; goblins require nothing")
	void spotChecks()
	{
		MonsterStats basilisk = data.searchMonsters("basilisk", 1).get(0);
		RequiredGear.Rule rule = RequiredGear.ruleFor(basilisk);
		assertNotNull(rule);
		assertEquals(GearSlot.SHIELD, rule.slot);
		assertTrue(rule.ids(data).contains(4156), "mirror shield accepted");
		assertNull(RequiredGear.ruleFor(data.searchMonsters("goblin", 1).get(0)));
		// The harpies' lantern must resolve to the LIT version only.
		MonsterStats harpie = data.searchMonsters("harpie bug swarm", 1).get(0);
		assertEquals(Set.of(7053), RequiredGear.ruleFor(harpie).ids(data));
	}
}
