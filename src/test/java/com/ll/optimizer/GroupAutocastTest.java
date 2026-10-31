package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A group scores its shared magic set with the best autocast
 * (ISS-01M5K9KFJH7EZ0GMAKNGZ4K2JG, Andrew 2026-10-22: "i'm in a weird loop
 * for barrows: it is showing trident as my weapon, however it is showing
 * the smoke battlestaff is a 0.49 dps upgrade in the ladder"). The weapon
 * itself is whatever the honest single-mob numbers pick for Ahrim - the smoke
 * staves lost the Twinflame's phantom second hit in
 * ISS-01M5PHE6WD81W8542RW71EXSHC, so "smoke" is no longer a given; the point
 * is that the group's kit wears Ahrim's own winner, autocast scored.
 */
class GroupAutocastTest {
	@Test
	@DisplayName("Barrows brothers as a group wear Ahrim's own best magic weapon - the kit and the single-mob answer agree")
	void groupWearsAhrimsOwnWinner() throws Exception {
		Tables data = new DataService().load();
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"virtus mask", "imbued zamorak cape", "occult necklace", "rada's blessing 4",
			"trident of the seas", "ahrim's robetop", "ghommal's hilt 4", "ahrim's robeskirt", "tormented bracelet",
			"aranea boots", "seers ring (i)", "smoke battlestaff", "mystic smoke staff"}) {
			int id = data.getGearItems().stream()
				.filter(g -> g.getLname().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
				.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name)).getId();
			owned.put(id, 1);
		}
		for (int rune : new int[]{21880, 560, 565, 566, 554, 556, 555, 557}) {
			owned.put(rune, 10000);
		}
		List<Monster> brothers = List.of(data.searchMonsters("ahrim the blighted", 1).get(0),
			data.searchMonsters("dharok the wretched", 1).get(0));
		Service service = new Service(data);
		try {
			Service.RosterResult roster = ServiceCalls.runRoster(service, brothers, owned, 0, Map.of());
			Service.Pick magic = roster.pm.get(0).get(Style.MAGIC);
			String weapon = magic.owned.get(0).getLoadout().getWeapon().getName();

			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> alone = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, brothers.get(0), Levels.MAXED, Levels.MAXED,
				Prayers.ALL, RequirementProfile.MAXED, new Owned(owned, true), owned.hashCode() + 7, false, false, "",
				Set.of(), -1, Req.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
				results -> {
					alone.set(results);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS), "single-mob compute timed out");
			String aloneWeapon = alone.get().get(Style.MAGIC).owned.get(0).getLoadout().getWeapon().getName();
			assertEquals(aloneWeapon, weapon, "the group wears Ahrim's own winner");
			if (!weapon.toLowerCase().contains("trident")) {
				assertNotNull(magic.owned.get(0).getSpellName(), "with its autocast");
			}
		} finally {
			service.shutdown();
		}
	}
}
