package com.loadoutlab.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The compute animation follows the selection: sea, a raid, Zulrah, or
 * the land flask (Andrew 2026-09-02: "definitely want to hit raids first"). */
class MoodKeyTest
{
	private static Map<String, Object> page(String raid, String mobName, boolean naval)
	{
		Map<String, Object> mob = new LinkedHashMap<>();
		mob.put("name", mobName);
		mob.put("naval", naval);
		mob.put("raid", raid);
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("lensIndex", 0);
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("params", params);
		entry.put("mobs", List.of(mob));
		Map<String, Object> page = new LinkedHashMap<>();
		page.put("entries", List.of(entry));
		return page;
	}

	@Test
	@DisplayName("the animation pool follows the selection")
	void poolFollowsTheSelection()
	{
		assertEquals("sea", RenderSurface.moodKey(page(null, "Hammerhead shark", true)));
		assertEquals("toa", RenderSurface.moodKey(page("toa", "Ba-Ba", false)));
		assertEquals("tob", RenderSurface.moodKey(page("tob", "Verzik Vitur", false)));
		assertEquals("cox", RenderSurface.moodKey(page("cox", "Tekton", false)));
		assertEquals("zulrah", RenderSurface.moodKey(page(null, "Zulrah", false)));
		assertNull(RenderSurface.moodKey(page(null, "General Graardor", false)), "land keeps the flask");
		assertNull(RenderSurface.moodKey(page(null, "Vorkath", false)));
	}

	@Test
	@DisplayName("the ten bosses route to their own pools by name, Echo and companion forms included")
	void bossesRouteByName()
	{
		String[][] routes = {
			{"Cerberus", "cerberus"}, {"Cerberus (Echo)", "cerberus"}, {"Brutus", "brutus"},
			{"Mad Angel", "madangel"}, {"Dusk", "guardians"}, {"Dawn", "guardians"}, {"Kraken", "kraken"},
			{"Thermonuclear smoke devil", "thermy"}, {"Vet'ion", "vetion"}, {"Calvar'ion", "vetion"},
			{"King Black Dragon", "kbd"}, {"Kalphite Queen", "kq"}, {"Kalphite Queen (Echo)", "kq"},
			{"Phantom Muspah", "muspah"}, {"Zulrah", "zulrah"}};
		for (String[] route : routes)
		{
			assertEquals(route[1], RenderSurface.moodKey(page(null, route[0], false)), route[0]);
		}
		assertNull(RenderSurface.moodKey(page(null, "Big Brutus", false)), "a prefix, not a substring");
		assertNull(RenderSurface.moodKey(page(null, "Pygmy kraken", false)), "the sea krakens keep their own lens");
	}

	@Test
	@DisplayName("Demonic Brutus wears the demonic reskin of the Brutus mood")
	void demonicBrutusReskin()
	{
		assertEquals("dbrutus", RenderSurface.moodKey(page(null, "Demonic Brutus", false)));
		assertEquals("brutus", RenderSurface.moodKey(page(null, "Brutus", false)));
	}

	@Test
	@DisplayName("the fifteen group pools route from every member of their group")
	void groupsRoute()
	{
		String[][] routes = {
			{"TzTok-Jad", "jad"}, {"Tz-Kih", "jad"}, {"TzKal-Zuk", "zuk"}, {"Jal-Nib", "zuk"},
			{"Dagannoth Rex", "dks"}, {"Dharok the Wretched", "barrows"}, {"Verac the Defiled", "barrows"},
			{"Blood Moon", "moons"}, {"Tormented Demon", "tormented"}, {"Demonic gorilla", "gorilla"},
			{"Abyssal Sire", "sire"}, {"Respiratory system", "sire"}, {"Sol Heredit", "sol"}, {"Manticore", "sol"},
			{"Crystalline Hunllef", "hunllef"}, {"Corrupted Hunllef", "hunllef"}, {"The Hueycoatl", "huey"},
			{"Nex", "nex"}, {"Cruor", "nex"}, {"Branda the Fire Queen", "titans"}, {"Eldric the Ice King", "titans"},
			{"Maggot King", "maggot"}, {"Yama", "yama"}, {"Judge of Yama", "yama"}};
		for (String[] route : routes)
		{
			assertEquals(route[1], RenderSurface.moodKey(page(null, route[0], false)), route[0]);
		}
	}
}
