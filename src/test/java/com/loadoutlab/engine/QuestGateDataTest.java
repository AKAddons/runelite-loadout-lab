package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearRequirements;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.data.RequiredGear;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quest-gate data fixes from the quest-boss audit (ISS-01M5RRTHQ3F1FCCJA30HQKMJ5R,
 * Andrew 2026-10-24: "Start with the zero-token data fixes (d'hide bodies,
 * dwarven helmet, light ballista, Dorgeshuun weapons, ice gloves for Flambeed
 * and Fareed)"). Every rule was checked against the item's OSRS Wiki page.
 */
class QuestGateDataTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	/** The row by name: the base item first, else a cosmetic variant (the
	 * trimmed bodies and crested shields are variants of their base). */
	private static Gear item(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name))
			.min(java.util.Comparator.comparing(g -> data.isVariant(g.getId())))
			.orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	private static RequirementProfile quests(String... done) {
		return new RequirementProfile(RequirementProfile.MAXED.getLevels(), Set.of(done));
	}

	@Test
	@DisplayName("without Dragon Slayer I the blue, red, black and Zamorak d'hide bodies are wearable; the green and gilded ones are not")
	void dhideBodies() {
		for (String name : List.of("blue d'hide body", "red d'hide body", "black d'hide body", "black d'hide body (t)",
			"zamorak d'hide body")) {
			assertTrue(quests().canEquip(item(name).getRequirements()), name + " needs no quest");
		}
		for (String name : List.of("green d'hide body", "green d'hide body (g)", "gilded d'hide body")) {
			assertFalse(quests().canEquip(item(name).getRequirements()), name + " still needs Dragon Slayer I");
			assertTrue(quests("DRAGON_SLAYER_I").canEquip(item(name).getRequirements()), name + " with the quest");
		}
	}

	@Test
	@DisplayName("the dwarven helmet needs Grim Tales, not Between a Rock...")
	void dwarvenHelmet() {
		GearRequirements helmet = item("dwarven helmet").getRequirements();
		assertEquals(Set.of("GRIM_TALES"), helmet.getQuests());
		assertFalse(quests("BETWEEN_A_ROCK").canEquip(helmet));
		assertTrue(quests("GRIM_TALES").canEquip(helmet));
	}

	@Test
	@DisplayName("the light ballista needs no quest; the heavy ballista still needs Monkey Madness II")
	void ballistas() {
		assertTrue(item("light ballista").getRequirements().getQuests().isEmpty());
		assertEquals(Set.of("MONKEY_MADNESS_II"), item("heavy ballista").getRequirements().getQuests());
	}

	@Test
	@DisplayName("the Dorgeshuun crossbow needs no quest; the Dorgeshuun-crest rune kiteshield still needs The Lost Tribe")
	void dorgeshuun() {
		assertTrue(item("dorgeshuun crossbow").getRequirements().getQuests().isEmpty());
		assertEquals(Set.of("THE_LOST_TRIBE"), item("rune kiteshield (dorgeshuun)").getRequirements().getQuests());
	}

	@Test
	@DisplayName("against Flambeed and Fareed the hands slot is ice gloves or smiths gloves (i), and the best melee set wears one")
	void iceGloves() {
		Set<Integer> gloves = Set.of(item("ice gloves").getId(), item("smiths gloves (i)").getId());
		for (String boss : List.of("flambeed", "fareed")) {
			Monster mob = data.searchMonsters(boss, 1).get(0);
			RequiredGear.Rule rule = RequiredGear.rulesFor(mob).stream().filter(r -> r.slot == GearSlot.HANDS)
				.findFirst().orElseThrow(() -> new AssertionError(boss + " has no hands rule"));
			assertEquals(gloves, rule.ids(data), boss + " names both gloves");
			OptimizationRequest request = new OptimizationRequest(mob, Style.MELEE, PlayerLevels.MAXED,
				PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.ALL_STANDARD, true, false,
				OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
			List<DpsResult> best = new LoadoutOptimizer().optimize(data, request);
			assertFalse(best.isEmpty(), boss + " has a melee set");
			Gear hands = best.get(0).getLoadout().get(GearSlot.HANDS);
			assertTrue(hands != null && gloves.contains(hands.getId()),
				boss + "'s melee set wears " + (hands == null ? "nothing" : hands.getName()));
		}
	}
}
