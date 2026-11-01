package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wilderness-weapon +50% used to fire by monster NAME, so Catacombs
 * hellhounds and Slayer-tower staples got Ursine-chainmace picks with
 * buffed numbers (2026-07 player audit A3.1). The buff now keys on the
 * request's in-Wilderness flag: wilderness-EXCLUSIVE monsters (revs, the
 * boss ring) default it on; shared-name monsters default off and take
 * the panel toggle.
 */
class WildernessToggleTest {
	private static Tables data;
	private static Monster hellhound;
	private static Monster revDemon;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		hellhound = data.searchMonsters("hellhound", 1).get(0);
		revDemon = data.searchMonsters("revenant demon", 1).get(0);
	}

	private static Req request(Monster monster) {
		return new Req(monster, Style.MELEE,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED),
			null, 0, CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static Kit ursine() {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals("ursine chainmace")
				&& "charged".equals(item.getVer())) {
				EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
				gear.put(Slot.WEAPON, item);
				return new Kit(gear);
			}
		}
		throw new AssertionError("corpus is missing a charged ursine chainmace");
	}

	@Test
	@DisplayName("a hellhound is not the Wilderness by default - the toggle opts in")
	void sharedNameMonstersNeedTheToggle() {
		Req atHellhound = request(hellhound);
		assertFalse(atHellhound.isInWilderness(), "hellhounds also live in the Catacombs");
		assertFalse(Calc.revWeaponBuff(atHellhound, ursine(), "ursine chainmace"));

		Req inWildy = atHellhound.withInWilderness(true);
		assertTrue(Calc.revWeaponBuff(inWildy, ursine(), "ursine chainmace"),
			"the same fight WITH the toggle gets the +50%");
	}

	@Test
	@DisplayName("wilderness-exclusive monsters default the flag on")
	void exclusiveMonstersDefaultOn() {
		assertTrue(request(revDemon).isInWilderness());
		assertTrue(Calc.revWeaponBuff(request(revDemon), ursine(), "ursine chainmace"));
	}

	@Test
	@DisplayName("the optimizer no longer hands Catacombs hellhound tasks an Ursine chainmace")
	void hellhoundMeleeIsNotUrsine() {
		List<Dps> results = new LoadoutOptimizer().optimize(data, request(hellhound));
		assertFalse(results.isEmpty());
		assertNotEquals("ursine chainmace",
			results.get(0).getLoadout().getWeapon().getLname());
	}
}
