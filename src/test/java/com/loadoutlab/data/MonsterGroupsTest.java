package com.loadoutlab.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The curated groups must resolve COMPLETELY against loaded corpus rows -
 * a silent member drop at runtime is a curation bug this test catches
 * (the loader itself stays lenient so a bad row never takes the panel
 * down). Members with ambiguous names must name a version.
 */
class MonsterGroupsTest
{
	private static LoadoutData data;
	private static List<MonsterGroups.MonsterGroup> groups;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		groups = MonsterGroups.load(data);
	}

	@Test
	@DisplayName("every curated member resolves to exactly one loaded row")
	void everyMemberResolves() throws Exception
	{
		try (InputStreamReader reader = new InputStreamReader(
			MonsterGroupsTest.class.getResourceAsStream("/com/loadoutlab/data/monster_groups.json"),
			StandardCharsets.UTF_8))
		{
			JsonArray rows = new JsonParser().parse(reader).getAsJsonArray();
			int curated = 0;
			for (JsonElement element : rows)
			{
				JsonObject row = element.getAsJsonObject();
				String group = row.get("name").getAsString();
				for (JsonElement m : row.getAsJsonArray("members"))
				{
					JsonObject member = m.getAsJsonObject();
					String name = member.get("name").getAsString();
					String version = member.has("version") ? member.get("version").getAsString() : null;
					assertNotNull(MonsterGroups.resolve(data, name, version),
						group + " member did not resolve (missing or ambiguous): "
							+ name + (version == null ? "" : " [" + version + "]"));
					curated++;
				}
			}
			// The loader kept every resolved member (nothing silently dropped).
			int loaded = groups.stream().mapToInt(g -> g.getMobs().size()).sum();
			assertEquals(curated, loaded, "loader dropped members");
		}
	}

	@Test
	@DisplayName("the flagship groups load with their full rosters")
	void flagshipRosters()
	{
		assertEquals(10, groups.size());
		assertEquals(7, byName("Fight Caves").getMobs().size());
		assertEquals(9, byName("Inferno").getMobs().size());
		assertEquals(3, byName("Zulrah (all forms)").getMobs().size());
		assertEquals(8, byName("Theatre of Blood").getMobs().size());
		assertEquals(6, byName("Tombs of Amascut").getMobs().size());
		assertEquals(15, byName("Chambers of Xeric").getMobs().size());
		// The Jad in the Fight Caves roster is the real one, not the
		// Colosseum's TzTok-Jad-Rek; the Tok-Xil is the fight-caves row,
		// not the Construction target dummy.
		assertTrue(byName("Fight Caves").getMobs().stream()
			.anyMatch(m -> m.getName().equals("TzTok-Jad")));
		assertTrue(byName("Fight Caves").getMobs().stream()
			.noneMatch(m -> m.getName().contains("Construction")));
	}

	@Test
	@DisplayName("group search finds by fragment, in curated order")
	void searchFindsGroups()
	{
		assertEquals("Fight Caves",
			MonsterGroups.search(groups, "fight", 5).get(0).getName());
		assertEquals("Inferno",
			MonsterGroups.search(groups, "infer", 5).get(0).getName());
		assertTrue(MonsterGroups.search(groups, "x", 5).isEmpty(),
			"single-char queries return nothing");
		// Player vocabulary reaches the groups via aliases.
		assertEquals("Fight Caves",
			MonsterGroups.search(groups, "jad", 5).get(0).getName());
		assertEquals("Inferno",
			MonsterGroups.search(groups, "zuk", 5).get(0).getName());
		assertEquals("Dagannoth Kings",
			MonsterGroups.search(groups, "dks", 5).get(0).getName());
	}

	@Test
	@DisplayName("tormented demon phases are synthetic style-immune variants of one sheet")
	void tormentedDemonPhases()
	{
		MonsterGroups.MonsterGroup tds = byName("Tormented Demons");
		assertEquals(3, tds.getMobs().size());
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (MonsterStats phase : tds.getMobs())
		{
			assertTrue(ids.add(phase.getId()), "synthetic ids must be distinct");
			assertEquals("Tormented Demon", phase.getName(),
				"the NAME survives - name-keyed TD rules keep applying");
			assertEquals(600, phase.getHitpoints());
			assertTrue(phase.hasAttribute("demon"));
		}
		// Each phase is immune to exactly its shielded style.
		com.loadoutlab.engine.CombatStyle[] styles = {
			com.loadoutlab.engine.CombatStyle.MELEE,
			com.loadoutlab.engine.CombatStyle.RANGED,
			com.loadoutlab.engine.CombatStyle.MAGIC};
		for (int i = 0; i < 3; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				assertEquals(i == j, com.loadoutlab.engine.MonsterMechanics
						.styleImmune(tds.getMobs().get(i), styles[j]),
					tds.getMobs().get(i).getVersion() + " vs " + styles[j]);
			}
		}
		// Search reaches it by player vocabulary.
		assertEquals("Tormented Demons",
			MonsterGroups.search(groups, "td", 5).get(0).getName());
		// A synthetic phase's profile id maps back to its real monster,
		// so pins/exclusions/notes set on the plain mob follow it into
		// the group (transitive exclusions).
		for (MonsterStats phase : tds.getMobs())
		{
			assertTrue(phase.getId() >= MonsterStats.SYNTHETIC_ID_BASE);
			assertTrue(phase.profileId() < MonsterStats.SYNTHETIC_ID_BASE);
			assertEquals(MonsterGroups.resolve(data, "Tormented Demon", null).getId(),
				phase.profileId());
		}
	}

	@Test
	@DisplayName("the raid groups resolve and answer to player vocabulary")
	void raidGroups()
	{
		assertEquals("Theatre of Blood",
			MonsterGroups.search(groups, "tob", 5).get(0).getName());
		assertEquals("Tombs of Amascut",
			MonsterGroups.search(groups, "toa", 5).get(0).getName());
		assertEquals("Chambers of Xeric",
			MonsterGroups.search(groups, "cox", 5).get(0).getName());
		// Verzik's three phases ride the ToB roster.
		assertEquals(3, byName("Theatre of Blood").getMobs().stream()
			.filter(m -> m.getName().equals("Verzik Vitur")).count());
		// Olm is head plus both claws.
		assertEquals(3, byName("Chambers of Xeric").getMobs().stream()
			.filter(m -> m.getName().equals("Great Olm")).count());
	}

	private static MonsterGroups.MonsterGroup byName(String name)
	{
		return groups.stream().filter(g -> g.getName().equals(name))
			.findFirst().orElseThrow(() -> new AssertionError("missing group " + name));
	}
}
