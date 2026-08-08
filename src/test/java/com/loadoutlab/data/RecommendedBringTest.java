package com.loadoutlab.data;

import java.util.ArrayList;
import java.util.List;
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
	@DisplayName("Vorkath chips the Crumble Undead runes for the spawn")
	void vorkathCrumbleRunes()
	{
		Map<Integer, String> chips = chipsFor("vorkath");
		assertTrue(chips.containsKey(556), "air rune (x2)");
		assertTrue(chips.containsKey(557), "earth rune (x2)");
		assertTrue(chips.containsKey(562), "chaos rune (x1)");
		assertTrue(chips.containsKey(4170), "slayer's staff for the left-click cast");
		assertTrue(chips.values().stream().allMatch(t -> t.contains("Crumble Undead")),
			"the spell is the point of every chip");
		assertTrue(RecommendedBring.isRune(557), "runes pull the pouch along");
		assertFalse(RecommendedBring.isRune(4170),
			"the staff must not drag the rune pouch in");
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
	@DisplayName("spell recommendations stipulate their book; antipoison does not")
	void spellbookImplication()
	{
		assertTrue(RecommendedBring.stipulatesSpellbook(
			data.searchMonsters("jal-nib", 1).get(0)),
			"the Inferno trip lives on Ancients - Arceuus folds stand down");
		assertTrue(RecommendedBring.stipulatesSpellbook(
			data.searchMonsters("vorkath", 1).get(0)),
			"Crumble Undead locks the trip to the standard book - Arceuus"
				+ " folds stand down (field report 2026-08-08)");
		assertFalse(RecommendedBring.stipulatesSpellbook(
			data.searchMonsters("dagannoth rex", 1).get(0)),
			"antipoison implies no spellbook at all");
	}

	@Test
	@DisplayName("monsters without a recommendation chip nothing")
	void unknownIsEmpty()
	{
		assertTrue(chipsFor("goblin").isEmpty());
	}

	@Test
	@DisplayName("stipulatesSpellbook stays in lockstep with the chip tooltips")
	void spellbookSwitchAgreesWithChips()
	{
		// stipulatesSpellbook answers by name switch instead of scanning
		// its own tooltips (the panel asks per mob per rebuild) - this pins
		// the switch against the tooltip-derived truth for every curated
		// case, so a new spell chip cannot silently miss the switch. Any
		// tooltip naming a spellbook IS a stipulation (Ancient barrages,
		// standard Crumble Undead alike).
		List<MonsterStats> cases = new ArrayList<>();
		for (String name : new String[]{"jal-nib", "jal-ak", "vorkath",
			"dagannoth rex", "dagannoth prime", "dagannoth supreme", "goblin"})
		{
			cases.add(data.searchMonsters(name, 1).get(0));
		}
		cases.addAll(data.searchMonsters("abyssal sire", 6));
		for (MonsterStats monster : cases)
		{
			boolean fromChips = RecommendedBring.chipsFor(monster).values().stream()
				.anyMatch(t -> t.toLowerCase().contains("spellbook"));
			assertEquals(fromChips, RecommendedBring.stipulatesSpellbook(monster),
				monster.label() + ": the switch must agree with the tooltips it replaced");
		}
	}
}
