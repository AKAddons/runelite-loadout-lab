package com.loadoutlab.data;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Quest;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Golden test for the name_rules.json extraction.
 *
 * Replays LegacyNameRules - the verbatim pre-extraction implementations - over
 * the entire real corpus and asserts the resource-driven versions in
 * DataService agree on every single row. This, plus the byte-identical golden
 * DPS run, is the proof that the extraction changed no behaviour.
 */
public class NameRulesGoldenTest {
	private static LoadoutData data;
	private static Method questsFor;
	private static Method spellLevel;
	private static Method knownSlayerMonster;

	@BeforeClass
	public static void loadCorpus() throws Exception {
		data = new DataService().load();
		questsFor = privateStatic("questsFor", String.class, String.class);
		spellLevel = privateStatic("spellLevel", String.class);
		knownSlayerMonster = privateStatic("knownSlayerMonster", String.class);
	}

	private static Method privateStatic(String name, Class<?>... params) throws Exception {
		Method method = DataService.class.getDeclaredMethod(name, params);
		method.setAccessible(true);
		return method;
	}

	/** The legacy rules, less the deliberate corrections made since the
	 * extraction (LegacyNameRules itself stays frozen): the blisterwood
	 * flail wields at 50 Attack with no quest - it is made partway through
	 * Sins of the Father and the quest's own Vanstrom fight needs it (wiki;
	 * ISS-01M5RGMT70PG86MWHPF28DBJ3V). From the quest-boss audit
	 * (ISS-01M5RRTHQ3F1FCCJA30HQKMJ5R, each on the item's wiki page): only the
	 * green and gilded d'hide bodies need Dragon Slayer I; the dwarven helmet
	 * needs Grim Tales, not Between a Rock...; the light ballista and the
	 * Dorgeshuun crossbow need no quest (the Dorgeshuun-crest heraldry keeps
	 * The Lost Tribe). */
	private static List<String> expectedQuests(Gear item) {
		List<String> expected = new ArrayList<>(
			LegacyNameRules.forItem(item.getId(), item.getName(), item.getVersion()));
		String key = (item.getName() + " " + item.getVersion()).toLowerCase(java.util.Locale.ROOT);
		if (key.contains("blisterwood flail")) {
			expected.remove(Quest.SINS_OF_THE_FATHER.name());
		}
		if (key.contains("d'hide body") && !key.contains("green d'hide body") && !key.contains("gilded d'hide body")) {
			expected.remove(Quest.DRAGON_SLAYER_I.name());
		}
		if (key.contains("dwarven helmet")) {
			expected.replaceAll(q -> q.equals(Quest.BETWEEN_A_ROCK.name()) ? Quest.GRIM_TALES.name() : q);
		}
		if (key.contains("light ballista") && !key.contains("heavy ballista")) {
			expected.remove(Quest.MONKEY_MADNESS_II.name());
		}
		if ((key.contains("dorgeshuun") && !key.contains("(dorgeshuun)")) || key.contains("bone crossbow")) {
			expected.remove(Quest.THE_LOST_TRIBE.name());
		}
		return expected;
	}

	@Test
	public void corpusIsBigEnoughToBeMeaningful() {
		// Guards against a silently empty corpus making every assertion vacuous.
		assertTrue("gear corpus suspiciously small: " + data.getGearItems().size(),
			data.getGearItems().size() > 4000);
		assertTrue("spell corpus suspiciously small", data.getSpells().size() > 20);
		assertTrue("monster corpus suspiciously small", data.getMonsters().size() > 500);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void questUnlocksMatchTheOldImplementationForEveryGearItem() throws Exception {
		List<String> mismatches = new ArrayList<>();
		int withQuests = 0;
		for (Gear item : data.getGearItems()) {
			List<String> expected = expectedQuests(item);
			List<String> actual = new ArrayList<>(
				(java.util.Set<String>) questsFor.invoke(null, item.getName(), item.getVersion()));
			if (!expected.equals(actual)) {
				mismatches.add(item.getId() + " " + item.getName() + " [" + item.getVersion()
					+ "] expected " + expected + " but got " + actual);
			}
			if (!expected.isEmpty()) {
				withQuests++;
			}
		}
		assertTrue("no gear item resolved any quest - the table is not being exercised",
			withQuests > 0);
		assertEquals("quest unlock mismatches: " + mismatches, 0, mismatches.size());
	}

	/** The end-to-end path: the GearRequirements actually attached to items. */
	@Test
	public void attachedGearRequirementsMatchTheOldImplementation() {
		List<String> mismatches = new ArrayList<>();
		for (Gear item : data.getGearItems()) {
			List<String> expected = expectedQuests(item);
			List<String> actual = new ArrayList<>(item.getRequirements().getQuests());
			if (!expected.equals(actual)) {
				mismatches.add(item.getId() + " " + item.getName()
					+ " expected " + expected + " but got " + actual);
			}
		}
		assertEquals("attached requirement mismatches: " + mismatches, 0, mismatches.size());
	}

	@Test
	public void spellLevelsMatchTheOldSwitchForEverySpell() throws Exception {
		for (SpellStats spell : data.getSpells()) {
			assertEquals(spell.getName(),
				LegacyNameRules.spellLevel(spell.getName()),
				((Integer) spellLevel.invoke(null, spell.getName())).intValue());
		}
		// Unknown and null names must still fall back to 1.
		assertEquals(1, ((Integer) spellLevel.invoke(null, "Not A Spell")).intValue());
		assertEquals(1, ((Integer) spellLevel.invoke(null, (Object) null)).intValue());
	}

	@Test
	public void slayerMonsterDetectionMatchesTheOldOrChain() throws Exception {
		List<String> mismatches = new ArrayList<>();
		int matched = 0;
		for (Monster monster : data.getMonsters()) {
			boolean expected = LegacyNameRules.knownSlayerMonster(monster.getName());
			boolean actual = (Boolean) knownSlayerMonster.invoke(null, monster.getName());
			if (expected != actual) {
				mismatches.add(monster.getName() + " expected " + expected);
			}
			if (expected) {
				matched++;
			}
		}
		assertTrue("no monster matched the slayer table - it is not being exercised", matched > 0);
		assertEquals("slayer detection mismatches: " + mismatches, 0, mismatches.size());
		assertEquals(false, knownSlayerMonster.invoke(null, (Object) null));
	}

	/**
	 * BLOCKER guard: every quest name frozen into name_rules.json must still
	 * resolve through Quest.valueOf. RuneLite is not pinned
	 * (runeLiteVersion = 'latest.release'), so an upstream enum rename would
	 * otherwise silently degrade an item to "no quest requirement".
	 */
	@Test
	public void everyQuestNameStillResolvesAgainstTheRuneLiteEnum() {
		List<String> unknown = new ArrayList<>();
		List<String> names = questNames();
		for (String name : names) {
			try {
				Quest.valueOf(name);
			} catch (IllegalArgumentException ex) {
				unknown.add(name);
			}
		}
		assertEquals("name_rules.json has 34 quest rules (35 at the extraction; the blisterwood flail gate went, ISS-01M5RGMT70PG86MWHPF28DBJ3V)", 34, names.size());
		assertTrue("quest names no longer in net.runelite.api.Quest: " + unknown,
			unknown.isEmpty());
	}

	private static List<String> questNames() {
		com.google.gson.JsonObject root =
			JsonResources.objectOrThrow("/com/loadoutlab/data/name_rules.json");
		List<String> names = new ArrayList<>();
		for (com.google.gson.JsonElement rule : root.getAsJsonArray("questRules")) {
			names.add(rule.getAsJsonObject().get("quest").getAsString());
		}
		return names;
	}

	/** The resource must carry the full tables, not a truncated file. */
	@Test
	public void resourceTableSizesAreTheVerifiedCounts() {
		com.google.gson.JsonObject root =
			JsonResources.objectOrThrow("/com/loadoutlab/data/name_rules.json");
		assertEquals("spell rows", 48, root.getAsJsonObject("spellLevels").size());
		assertEquals("slayer contains terms", 29, root.getAsJsonArray("slayerContains").size());
		assertEquals("slayer startsWith terms", 1, root.getAsJsonArray("slayerStartsWith").size());
		assertEquals("quest rules", 34, root.getAsJsonArray("questRules").size());
	}
}
