package com.loadoutlab.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * multi_mobs.json (Andrew 2026-10-19, the multi-combat card design):
 * the corpus monsters fought in multi-combat areas, curated from the
 * wiki location tables, the crowdsourced multicombat varbit tiles and
 * the areas' own pages (docs/data/multi-mobs.md). Every entry must name
 * exactly one corpus monster, or the card could never find it.
 */
class MultiMobsTest
{
	private static LoadoutData data;
	private static JsonArray mobs;

	@BeforeAll
	static void load() throws Exception
	{
		data = new DataService().load();
		try (InputStream in = MultiMobsTest.class.getResourceAsStream("/com/loadoutlab/data/multi_mobs.json"))
		{
			assertNotNull(in, "multi_mobs.json is on the classpath");
			JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			assertEquals(1, root.get("version").getAsInt());
			mobs = root.getAsJsonArray("mobs");
		}
	}

	private static String version(MonsterStats m)
	{
		return m.getVersion() == null ? "" : m.getVersion();
	}

	@Test
	@DisplayName("crowds only: the Catacombs nechryaels are listed, a boss standing alone in a multi area is not (Andrew 2026-10-19)")
	void crowdsNotBosses()
	{
		java.util.Set<String> names = new HashSet<>();
		for (JsonElement e : mobs)
		{
			names.add(e.getAsJsonObject().get("name").getAsString());
		}
		assertTrue(names.contains("Greater Nechryael"));
		assertTrue(names.contains("Dust devil"));
		assertFalse(names.contains("General Graardor"), "one spawn in the Bandos room");
		assertFalse(names.contains("Kalphite Queen"));
		assertFalse(names.contains("Dagannoth Rex"));
	}

	@Test
	@DisplayName("every entry resolves to exactly one corpus monster by name and version, and carries a location")
	void entriesResolve()
	{
		assertTrue(mobs.size() > 50, "the list is not a stub: " + mobs.size());
		Set<String> seen = new HashSet<>();
		for (JsonElement e : mobs)
		{
			JsonObject mob = e.getAsJsonObject();
			String name = mob.get("name").getAsString();
			String version = mob.get("version").getAsString();
			assertTrue(seen.add(name + "#" + version), "repeated entry: " + name + " [" + version + "]");
			List<MonsterStats> hits = data.searchMonsters(name, 100);
			long exact = hits.stream()
				.filter(m -> m.getName().equals(name) && version(m).equals(version)).count();
			assertEquals(1, exact, name + " [" + version + "] must name one corpus monster, found " + exact);
			JsonArray locations = mob.getAsJsonArray("locations");
			assertTrue(locations.size() > 0, name + " has no location");
			for (JsonElement l : locations)
			{
				assertFalse(l.getAsJsonObject().get("location").getAsString().isBlank());
				assertFalse(l.getAsJsonObject().get("source").getAsString().isBlank());
			}
		}
	}
}
