package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups.MonsterGroup;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A group's trip kills each monster once (Andrew 2026-10-30: "royal titans -
 * trip time is the total of all mobs but really it only clears the hp of the
 * titans once"; "the royal titans thing happens to anything with duplicated
 * mobs: toa, cox, tormented demons, demonic gorillas, tfa?, tob"; "Titans
 * only").
 */
class TripDedupeTest {
	private static List<MonsterGroup> groups;

	@BeforeAll
	static void load() {
		LoadoutData data = new DataService().load();
		groups = MonsterGroups.load(data);
	}

	private static MonsterGroup group(String name) {
		return groups.stream().filter(g -> g.getName().equalsIgnoreCase(name)).findFirst().orElseThrow(AssertionError::new);
	}

	@Test
	@DisplayName("the Royal Titans trip counts Branda and Eldric once each, their first version, and leaves the elementals out")
	void royalTitans() {
		List<MonsterStats> mobs = group("Royal Titans").getMobs();
		assertEquals(6, mobs.size());
		assertEquals(List.of(0, 1), RenderModel.tripMembers(mobs));
		assertTrue(mobs.get(4).hasAttribute("no_trip") && mobs.get(5).hasAttribute("no_trip"));
	}

	@Test
	@DisplayName("prayer variants and repeated raid bosses count once: Tormented Demons, Demonic gorillas, Zulrah's forms, the Tombs, the Chambers, the Theatre, the Archive")
	void duplicatesOnce() {
		for (String name : new String[]{"Tormented Demons", "Demonic gorillas", "Zulrah (all forms)", "Tombs of Amascut",
			"Chambers of Xeric", "Theatre of Blood (Entry)", "The Fractured Archive"}) {
			List<MonsterStats> mobs = group(name).getMobs();
			List<Integer> counted = RenderModel.tripMembers(mobs);
			long distinct = mobs.stream().map(m -> m.name).distinct().count();
			assertEquals(distinct, counted.size(), name);
			for (int i : counted) {
				int first = i;
				for (int j = 0; j < i; j++) if (mobs.get(j).name.equals(mobs.get(i).name)) first = j;
				assertEquals(i, first, name + ": the first version counts");
			}
		}
	}
}
