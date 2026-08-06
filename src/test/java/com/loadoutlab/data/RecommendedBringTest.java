package com.loadoutlab.data;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The curated bring-chips are name-keyed like the notes, so they are
 * pinned against LOADED rows - a corpus rename must fail here, not
 * silently orphan a recommendation.
 */
class RecommendedBringTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static Map<Integer, String> chipsFor(String search)
	{
		return RecommendedBring.chipsFor(data.searchMonsters(search, 1).get(0));
	}

	@Test
	@DisplayName("the nibblers chip the barrage runes and name the spellbook")
	void nibblerRunes()
	{
		Map<Integer, String> chips = chipsFor("jal-nib");
		assertTrue(chips.containsKey(555), "water rune for Ice Barrage");
		assertTrue(chips.containsKey(565), "blood rune for Blood Barrage");
		assertTrue(chips.values().stream().allMatch(t -> t.contains("Ancient")),
			"the spellbook is the stipulation - every tooltip names it");
		assertTrue(RecommendedBring.isRune(555), "runes pull the pouch along");
	}

	@Test
	@DisplayName("Sire phase 1 chips the shadow runes; phase 3 does not")
	void sireShadowRunes()
	{
		MonsterStats p1 = data.searchMonsters("abyssal sire", 6).stream()
			.filter(m -> m.getVersion() != null && m.getVersion().startsWith("Phase 1"))
			.findFirst().orElseThrow();
		assertTrue(RecommendedBring.chipsFor(p1).containsKey(566),
			"soul rune for Shadow Barrage");
		MonsterStats p3 = data.searchMonsters("abyssal sire", 6).stream()
			.filter(m -> m.getVersion() != null && m.getVersion().startsWith("Phase 3"))
			.findFirst().orElseThrow();
		assertTrue(RecommendedBring.chipsFor(p3).isEmpty(),
			"the disorient is a phase 1 mechanic");
	}

	@Test
	@DisplayName("every King chips antipoison for the spinolyps")
	void kingsChipAntipoison()
	{
		for (String king : new String[]{"dagannoth rex", "dagannoth prime", "dagannoth supreme"})
		{
			Map<Integer, String> chips = chipsFor(king);
			assertTrue(chips.containsKey(5952), king + " must chip antipoison");
			assertFalse(RecommendedBring.isRune(5952),
				"antipoison must not drag the rune pouch in");
		}
	}

	@Test
	@DisplayName("monsters without a recommendation chip nothing")
	void unknownIsEmpty()
	{
		assertTrue(chipsFor("goblin").isEmpty());
	}
}
