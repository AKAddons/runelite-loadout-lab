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
 * The Ascension crossbows (the Fractured Archive, 2026-10-20; wiki): a
 * two-handed 3-tick crossbow that fires only ascension bolts (plain,
 * diamond, onyx and their enchanted forms), which fit no other weapon.
 */
public class AscensionCrossbowsTest {
	private static final int CROSSBOWS = 34681;
	private static final int[] ASCENSION_BOLTS = {34684, 34690, 34696, 34702, 34708};

	@Test
	public void onlyAscensionBoltsFitAndTheyFitNothingElse() {
		Tables data = new DataService().load();
		Gear crossbows = data.getGear(CROSSBOWS);
		Assert.assertNotNull(crossbows);
		for (int id : ASCENSION_BOLTS) {
			Assert.assertTrue("bolts " + id, RangedAmmo.compatible(data.getGear(id), crossbows));
		}
		Assert.assertFalse("no ammo", RangedAmmo.compatible(null, crossbows));
		Gear runiteBolts = data.getGear(9144);
		Gear runeCrossbow = data.getGear(9185);
		Assert.assertNotNull(runiteBolts);
		Assert.assertNotNull(runeCrossbow);
		Assert.assertFalse("runite bolts", RangedAmmo.compatible(runiteBolts, crossbows));
		Assert.assertFalse("a rune crossbow", RangedAmmo.compatible(data.getGear(34684), runeCrossbow));
	}

	@Test
	public void rapidFiresAtTwoTicks() {
		Tables data = new DataService().load();
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Req request = new Req(goblin, Style.RANGED, Levels.MAXED,
			PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, Owned.EMPTY,
			RequirementProfile.MAXED, 10);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, data.getGear(CROSSBOWS));
		gear.put(Slot.AMMO, data.getGear(34702));
		Dps result = new Calc().calculate(request, new Kit(gear));
		Assert.assertEquals(2, result.getAttackSpeed());
		Assert.assertTrue(result.getAttackType(), result.getAttackType().contains("rapid"));
	}
}
