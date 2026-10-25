package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
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
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static RequirementProfile seventies() {
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values()) { levels.put(skill, 70); }
		return new RequirementProfile(levels, RequirementProfile.MAXED.getCompletedQuests());
	}

	@Test
	@DisplayName("the waiver lets a 70s player wear the fire max cape and nothing else above them")
	void waiverCoversOnlyTheSimmedCapes() {
		GearItem cape = data.getGear(FIRE_MAX_CAPE);
		GearItem fang = data.getGearItems().stream().filter(g -> g.getNameLower().equals("osmumten's fang")).findFirst().get();
		assertFalse(seventies().canWear(cape), "no waiver: 99s required");
		RequirementProfile simmed = seventies().waiving(Set.of(FIRE_MAX_CAPE));
		assertTrue(simmed.canWear(cape));
		assertFalse(simmed.canWear(fang), "the fang still needs 82 Attack");
	}

	@Test
	@DisplayName("a 70s player owning the fire max cape under the sim's waiver wears it in the best melee set")
	void optimizerWearsTheSimmedCape() {
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		PlayerLevels levels = new PlayerLevels(70, 70, 70, 70, 70, 70, 70);
		OptimizationRequest request = new OptimizationRequest(graardor, CombatStyle.MELEE, levels,
			PrayerBonuses.bestAvailable(levels), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(Map.of(FIRE_MAX_CAPE, 1, 4151, 1), true), seventies().waiving(Set.of(FIRE_MAX_CAPE)), 1);
		List<DpsResult> best = new LoadoutOptimizer().optimize(data, request);
		GearItem cape = best.get(0).getLoadout().get(GearSlot.CAPE);
		assertEquals(FIRE_MAX_CAPE, cape == null ? -1 : cape.getId());
	}
}
