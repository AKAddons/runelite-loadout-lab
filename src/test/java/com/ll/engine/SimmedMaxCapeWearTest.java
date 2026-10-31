package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A simmed max cape is wearable (ISS-01M5RYC2TB43AEGQ96TTNEKEJN, Andrew
 * 2026-10-25: "if i have the max cape simmed i am NOT seeing the max+fire cape
 * being suggested"): every max cape needs 99 in all 23 skills, and the stat sim
 * only replaces the seven combat levels.
 */
class SimmedMaxCapeWearTest {
	private static final int FIRE_MAX_CAPE = 13329;
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static RequirementProfile seventies() {
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values()) { levels.put(skill, 70); }
		return new RequirementProfile(levels, RequirementProfile.MAXED.getCq());
	}

	@Test
	@DisplayName("the waiver lets a 70s player wear the fire max cape and nothing else above them")
	void waiverCoversOnlyTheSimmedCapes() {
		Gear cape = data.getGear(FIRE_MAX_CAPE);
		Gear fang = data.getGearItems().stream().filter(g -> g.getLname().equals("osmumten's fang")).findFirst().get();
		assertFalse(seventies().canWear(cape), "no waiver: 99s required");
		RequirementProfile simmed = seventies().waiving(Set.of(FIRE_MAX_CAPE));
		assertTrue(simmed.canWear(cape));
		assertFalse(simmed.canWear(fang), "the fang still needs 82 Attack");
	}

	@Test
	@DisplayName("a 70s player owning the fire max cape under the sim's waiver wears it in the best melee set")
	void optimizerWearsTheSimmedCape() {
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		Levels levels = new Levels(70, 70, 70, 70, 70, 70, 70);
		Req request = new Req(graardor, Style.MELEE, levels,
			PrayerBonuses.bestAvailable(levels), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new Owned(Map.of(FIRE_MAX_CAPE, 1, 4151, 1), true), seventies().waiving(Set.of(FIRE_MAX_CAPE)), 1);
		List<Dps> best = new LoadoutOptimizer().optimize(data, request);
		Gear cape = best.get(0).getLoadout().get(Slot.CAPE);
		assertEquals(FIRE_MAX_CAPE, cape == null ? -1 : cape.getId());
	}
}
