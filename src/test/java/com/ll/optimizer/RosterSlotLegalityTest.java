package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Dps;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * No answer may wear a shield under a two-handed weapon - anywhere.
 * Field report 2026-08-15 (twice): the Moons of Peril roster's Eclipse
 * Moon BiS wore Scythe of vitur WITH an Avernic defender. The
 * birthplace was the kit pass's slot unification (withSlot carries no
 * 2h guard), with a second hole in the dps-neutral utility swap; this
 * test walks EVERY set the roster answer publishes.
 */
public class RosterSlotLegalityTest {
	@Test
	public void moonsRosterNeverWearsAShieldUnderATwoHander() throws Exception {
		Tables data = new DataService().load();
		List<Monster> moons = List.of(
			data.searchMonsters("blood moon", 1).get(0),
			data.searchMonsters("blue moon", 1).get(0),
			data.searchMonsters("eclipse moon", 1).get(0));
		Service service = new Service(data);
		Service.RosterResult roster = ServiceCalls.runRoster(
			service, moons, Collections.emptyMap(), 3, Collections.emptyMap());
		Assert.assertNotNull(roster);
		int checked = 0;
		for (int i = 0; i < roster.mobs.size(); i++) {
			Map<Style, Service.Pick> styles = roster.pm.get(i);
			for (Map.Entry<Style, Service.Pick> e : styles.entrySet()) {
				Service.Pick result = e.getValue();
				if (result.overallBest != null) {
					assertLegal(roster.mobs.get(i), e.getKey(), "BiS", result.overallBest);
					checked++;
				}
				if (result.owned != null && !result.owned.isEmpty()) {
					assertLegal(roster.mobs.get(i), e.getKey(), "Yours", result.owned.get(0));
					checked++;
				}
			}
		}
		Assert.assertTrue("the roster answered something", checked > 0);
	}

	private static void assertLegal(Monster mob, Style style,
		String side, Dps result) {
		Gear weapon = result.getLoadout().getWeapon();
		Gear shield = result.getLoadout().get(Slot.SHIELD);
		if (weapon != null && weapon.isTwoh()) {
			Assert.assertNull(mob.label() + " " + style + " " + side + " wears "
					+ weapon.label() + " (two-handed) WITH " + (shield == null ? "" : shield.label()),
				shield);
		}
	}
}
