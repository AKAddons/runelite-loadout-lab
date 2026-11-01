package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Game-update audit 2026-10-06 (The Blood Moon Rises, 30 June 2026).
 * Wiki, Arrows: seeking arrows "have +20 ranged accuracy more than their
 * normal counterpart and a minimum hit of 3" - a landed 0, 1 or 2 becomes
 * 3. The official calc clamps every accurate hitsplat to 3. */
class SeekingArrowsTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Dps shoot(String bow, String ammo) {
		Monster mob = data.searchMonsters("goblin", 1).get(0);
		Req request = TestRequests.of(mob, Style.RANGED, Levels.MAXED,
			PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		for (String name : new String[]{bow, ammo}) {
			Gear item = data.getGearItems().stream()
				.filter(g -> g.getLname().equals(name) && g.isStandardGear())
				.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + name));
			gear.put(item.getSlot(), item);
		}
		return new Calc().calculate(request, new Kit(gear));
	}

	@Test
	@DisplayName("a landed seeking arrow deals at least 3: the 0, 1 and 2 rolls lift to 3")
	void minimumHitOfThree() {
		Dps seeking = shoot("magic shortbow", "seeking rune arrow");
		Dps plain = shoot("magic shortbow", "rune arrow");
		assertEquals(plain.getMaxHit(), seeking.getMaxHit(), "same max hit");
		assertTrue(seeking.getAccuracy() > plain.getAccuracy(), "+20 ranged accuracy");
		double acc = seeking.getAccuracy();
		int max = seeking.getMaxHit();
		// The plain expectation already counts a landed 0 as 1, so the
		// three low rolls lift by 2, 2 and 1 (official vector: 0.0%).
		double expected = RollMath.expectedHitWithFlatArmour(acc, 0, max, 0) + acc * 5.0 / (max + 1);
		assertEquals(expected, seeking.getEh(), 1e-9, "three rolls lifted to 3");
	}

	@Test
	@DisplayName("seeking arrows in the ammo slot of a blowpipe count for nothing")
	void onlyWhenTheWeaponFiresThem() {
		Dps pipe = shoot("toxic blowpipe", "seeking rune arrow");
		double acc = pipe.getAccuracy();
		assertEquals(RollMath.expectedHitWithFlatArmour(acc, 0, pipe.getMaxHit(), 0),
			pipe.getEh(), 1e-9);
	}
}
