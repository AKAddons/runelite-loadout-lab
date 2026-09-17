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
			RequiredGear.Rule rule = ruleFor(mob);
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
		RequiredGear.Rule rule = ruleFor(basilisk);
		assertNotNull(rule);
		assertEquals(GearSlot.SHIELD, rule.slot);
		assertTrue(rule.ids(data).contains(4156), "mirror shield accepted");
		assertNull(ruleFor(data.searchMonsters("goblin", 1).get(0)));
		// The harpies' lantern must resolve to the LIT version only.
		MonsterStats harpie = data.searchMonsters("harpie bug swarm", 1).get(0);
		assertEquals(Set.of(7053), ruleFor(harpie).ids(data));
	}

	@Test
	@DisplayName("Lizardman shamans outside CoX require the full tier-5 Shayzien set; the slayer helmet may stand in for the helm; the CoX shaman needs nothing")
	void shamansRequireShayzien()
	{
		MonsterStats temple = data.searchMonsters("Lizardman shaman", 5).stream()
			.filter(m -> "Lizardman Temple".equals(m.getVersion())).findFirst().orElseThrow();
		List<RequiredGear.Rule> rules = RequiredGear.rulesFor(temple);
		assertEquals(5, rules.size(), "head, body, legs, hands, feet");
		Set<Integer> ids = new java.util.HashSet<>();
		for (RequiredGear.Rule rule : rules)
		{
			ids.addAll(rule.ids(data));
		}
		for (int id : new int[]{13379, 13381, 13380, 13377, 13378})
		{
			assertTrue(ids.contains(id), "Shayzien tier-5 piece " + id);
		}
		assertTrue(ids.contains(11864), "the slayer helmet stands in for the helm (hard Kourend & Kebos diary)");
		MonsterStats cox = data.searchMonsters("Lizardman shaman (Chambers of Xeric)", 1).get(0);
		assertTrue(RequiredGear.rulesFor(cox).isEmpty(), "in CoX the attack is dodged");
	}

	/** The first rule, or null - the pre-2026-09-17 single-rule view. */
	private static RequiredGear.Rule ruleFor(MonsterStats mob)
	{
		List<RequiredGear.Rule> rules = RequiredGear.rulesFor(mob);
		return rules.isEmpty() ? null : rules.get(0);
	}
}
