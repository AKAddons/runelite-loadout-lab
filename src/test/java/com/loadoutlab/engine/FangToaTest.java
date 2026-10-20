package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TRIPLEKILLTRENT (YouTube 2026-10-18): "its telling me salad blade is
 * better than my fang at toa". The harness showed why: inside the Tombs
 * the fang's hit chance ran 17-24% under the official calc while the
 * sulphur blades matched exactly. Wiki (Osmumten's fang): outside ToA the
 * fang rerolls a failed accuracy check against a reduced defence roll;
 * inside the Tombs it simply rolls accuracy twice, so the hit chance is
 * 1 - (1 - p)^2. The constants below are the official calculator's own
 * numbers for these exact vectors (fang-baba-i300, fang-warden3-i300:
 * maxed levels, Piety, invocation 300, the fang alone).
 */
class FangToaTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static DpsResult fangAt(String monster, String version, int invocation)
	{
		MonsterStats mob = version.isEmpty() ? data.searchMonsters(monster, 1).get(0)
			: data.getMonsters().stream()
				.filter(m -> m.getName().equalsIgnoreCase(monster) && m.getVersion().equalsIgnoreCase(version))
				.findFirst().orElseThrow(AssertionError::new);
		mob = MonsterMechanics.atToaInvocation(mob, invocation);
		GearItem fang = data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals("osmumten's fang") && g.isStandardGear())
			.findFirst().orElseThrow(AssertionError::new);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, fang);
		OptimizationRequest request = new OptimizationRequest(mob, CombatStyle.MELEE, PlayerLevels.MAXED,
			// The official vectors were taken under Piety (2026-10-18); ALL
			// assumes Zeal since the Fractured Archive (2026-10-20).
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, new PrayerUnlocks(true, true, true, true, true)),
			null, 0, CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		assertNotNull(result);
		return result;
	}

	@Test
	@DisplayName("inside the Tombs the fang rolls accuracy twice - the official calc's hit chances")
	void fangRollsTwiceInsideToa()
	{
		assertEquals(0.624, fangAt("Ba-Ba", "", 300).getAccuracy(), 0.006, "Ba-Ba at invocation 300");
		assertEquals(0.441, fangAt("Elidinis' Warden", "Enraged", 300).getAccuracy(), 0.006,
			"the Enraged Warden at invocation 300");
	}

	@Test
	@DisplayName("outside the Tombs the fang keeps its reroll formula - vector fang-goblin stays exact")
	void fangRerollOutsideToa()
	{
		DpsResult goblin = fangAt("Goblin", "", 0);
		assertEquals(RollMath.fangAccuracy(goblin.getAttackRoll(), goblin.getDefenceRoll()),
			goblin.getAccuracy(), 1e-9);
	}
}
