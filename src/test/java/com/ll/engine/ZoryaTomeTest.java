package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * Zorya's tome (the Fractured Archive, 2026-10-20; wiki): a two-handed
 * powered staff, 3-tick, base max hit 18 (flat, like the crystal staff),
 * 15% magic damage of its own.
 */
public class ZoryaTomeTest {
	@Test
	public void threeTicksFromABaseOfEighteen() {
		Tables data = new DataService().load();
		Gear tome = data.getGear(34725);
		Assert.assertNotNull(tome);
		Assert.assertTrue(tome.isPoweredStaff());
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Req request = new Req(goblin, Style.MAGIC, Levels.MAXED,
			PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, Owned.EMPTY,
			RequirementProfile.MAXED, 10);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, tome);
		Dps result = new Calc().calculate(request, new Kit(gear));
		Assert.assertEquals(3, result.getAttackSpeed());
		// floor(18 * 1.15): the base with the tome's own 15%.
		Assert.assertEquals(20, result.getMaxHit());
	}
}
