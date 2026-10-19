package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Muuffin (Discord 2026-10-19): "I got Rada's blessing 3 but the loadouts
 * prioritize the god blessings over it ... They give the exact same
 * stats, but Rada's has teleports". The two stat-tie (+1 prayer), and
 * with both owned the family collapse fell to the tradeable god
 * blessing. A Rada's blessing wins that tie.
 */
class RadasBlessingTest
{
	private static final int RADAS_3 = 22945;
	private static final int HOLY_BLESSING = 20220;
	private static LoadoutData data;
	private static MonsterStats goblin;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		goblin = data.searchMonsters("goblin", 1).get(0);
	}

	private static void own(Map<Integer, Integer> owned, String nameLower)
	{
		GearItem item = data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + nameLower));
		owned.put(item.getId(), 1);
	}

	private static GearItem ammoOf(int... blessings)
	{
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"rune scimitar", "rune full helm", "rune platebody", "rune platelegs"})
		{
			own(owned, name);
		}
		for (int id : blessings)
		{
			owned.put(id, 1);
		}
		OptimizationRequest request = new OptimizationRequest(
			goblin, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		assertFalse(results.isEmpty());
		return results.get(0).getLoadout().get(GearSlot.AMMO);
	}

	@Test
	@DisplayName("with Rada's blessing 3 and a holy blessing owned, the set carries Rada's")
	void radasWinsTheTie()
	{
		GearItem ammo = ammoOf(RADAS_3, HOLY_BLESSING);
		assertNotNull(ammo, "a blessing rides the ammo slot");
		assertEquals(RADAS_3, ammo.getId(), "got " + ammo.getName());
	}

	@Test
	@DisplayName("with only the holy blessing owned, it still rides")
	void holyAloneStillRides()
	{
		GearItem ammo = ammoOf(HOLY_BLESSING);
		assertNotNull(ammo);
		assertEquals(HOLY_BLESSING, ammo.getId(), "got " + ammo.getName());
	}
}
