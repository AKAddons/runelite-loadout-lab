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
 * N, Discord 2026-09-26 (glacial temotli): "is flat armor taken into
 * account? ... an additional 4 damage every hit seems pretty big boost
 * to dps". Wiki (Multi-hit weapons, 2026-09-27): the twin weapons and
 * Torag's hammers roll TWO INDEPENDENT hits on a halved max; the dark
 * bow fires two full arrows; charged Tonalztics throw twice at 75%.
 * Flat armour applies to each hitsplat.
 */
public class TwoHitWeaponsTest {
	private static Tables data;

	private static Tables data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	private static Gear byName(String lname, String version) {
		return data().getGearItems().stream()
			.filter(g -> g.getLname().equals(lname) && g.isStandardGear()
				&& (version == null || version.equalsIgnoreCase(g.getVersion())))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + lname));
	}

	private static Dps swing(String monster, Style style, Gear weapon, String ammo) {
		Monster mob = data().searchMonsters(monster, 1).get(0);
		Req request = TestRequests.of(mob, style,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, weapon);
		if (ammo != null) {
			gear.put(Slot.AMMO, byName(ammo, null));
		}
		Dps result = new Calc().calculate(request, new Kit(gear));
		Assert.assertNotNull(result);
		return result;
	}

	private static void assertTwoIndependentHits(String weapon, String version) {
		Monster moon = data().searchMonsters("blue moon", 1).get(0);
		int armour = moon.getDefensive().getFlatArmour();
		Assert.assertTrue("blue moon carries negative flat armour", armour < 0);
		Dps result = swing("blue moon", Style.MELEE, byName(weapon, version), null);
		int rawMax = result.getMaxHit() + armour;
		int firstRaw = rawMax / 2;
		int secondRaw = rawMax - firstRaw;
		double acc = result.getAccuracy();
		double expected = RollMath.expectedHitWithFlatArmour(acc, 0, firstRaw, armour)
			+ RollMath.expectedHitWithFlatArmour(acc, 0, secondRaw, armour);
		Assert.assertEquals(weapon + ": two independent hitsplats, each with the flat bonus",
			expected, result.getEh(), 1e-9);
	}

	@Test
	public void theTwinWeaponsRollTwoIndependentHits() {
		assertTwoIndependentHits("glacial temotli", null);
		assertTwoIndependentHits("sulphur blades", null);
		assertTwoIndependentHits("earthbound tecpatl", null);
		assertTwoIndependentHits("torag's hammers", "Undamaged");
	}

	@Test
	public void theDarkBowFiresTwoFullArrows() {
		Dps bow = swing("goblin", Style.RANGED, byName("dark bow", "Regular"), "dragon arrow");
		double one = RollMath.expectedHitWithFlatArmour(bow.getAccuracy(), 0, bow.getMaxHit(), 0);
		Assert.assertEquals("two arrows, each a full roll", 2 * one, bow.getEh(), 1e-9);
	}

	@Test
	public void chargedTonalzticsThrowTwiceAtThreeQuarters() {
		Dps charged = swing("goblin", Style.RANGED, byName("tonalztics of ralos", "Charged"), null);
		Dps uncharged = swing("goblin", Style.RANGED, byName("tonalztics of ralos", "Uncharged"), null);
		Assert.assertEquals("both versions roll 75% of the max", uncharged.getMaxHit(), charged.getMaxHit());
		double one = RollMath.expectedHitWithFlatArmour(charged.getAccuracy(), 0, charged.getMaxHit(), 0);
		Assert.assertEquals("charged throws twice", 2 * one, charged.getEh(), 1e-9);
		Assert.assertEquals("uncharged throws once", one, uncharged.getEh(), 1e-9);
	}
}
