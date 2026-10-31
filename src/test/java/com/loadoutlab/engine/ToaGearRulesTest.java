package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kendyy, Discord 2026-09-29: "Aquanites slayer task giving keris as bis
 * ... calc isn't adding up to the same on wiki DPS calc ... only seems to
 * happen with black gem attached". Micah: "black keris gets recommended a
 * lot when it has terrible stats outside of TOA". Wiki: the keris partisan
 * of Amascut is +108 stab / +67 strength INSIDE the Tombs and the base
 * partisan's +58 / +45 outside; Tumeken's shadow multiplies by four inside
 * and three outside. */
class ToaGearRulesTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static DpsResult swing(String monster, Style style, String... gearNames) {
		Monster mob = data.searchMonsters(monster, 1).get(0);
		OptimizationRequest request = TestRequests.of(mob, style, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		EnumMap<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
		for (String name : gearNames) {
			Gear item = data.getGearItems().stream()
				.filter(g -> g.getNameLower().equals(name) && g.isStandardGear())
				.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + name));
			gear.put(item.getSlot(), item);
		}
		return new DpsCalculator().calculate(request, new Loadout(gear));
	}

	@Test
	@DisplayName("outside the Tombs the keris partisan of Amascut is a plain keris partisan")
	void amascutPartisanOutside() {
		DpsResult amascut = swing("aquanite", Style.MELEE, "keris partisan of amascut");
		DpsResult plain = swing("aquanite", Style.MELEE, "keris partisan");
		assertEquals(plain.getMaxHit(), amascut.getMaxHit(), "strength +45, not +67");
		assertEquals(plain.getAccuracy(), amascut.getAccuracy(), 1e-9, "stab +58, not +108");
	}

	@Test
	@DisplayName("inside the Tombs it keeps its +108 stab and +67 strength")
	void amascutPartisanInside() {
		DpsResult amascut = swing("akkha", Style.MELEE, "keris partisan of amascut");
		DpsResult plain = swing("akkha", Style.MELEE, "keris partisan");
		assertTrue(amascut.getMaxHit() > plain.getMaxHit());
		assertTrue(amascut.getAccuracy() > plain.getAccuracy());
	}

	@Test
	@DisplayName("Tumeken's shadow multiplies by four inside the Tombs, three outside")
	void shadowInside() {
		DpsResult inside = swing("akkha", Style.MAGIC, "tumeken's shadow", "occult necklace");
		DpsResult outside = swing("goblin", Style.MAGIC, "tumeken's shadow", "occult necklace");
		assertTrue(inside.getAttackRoll() > outside.getAttackRoll(), "the magic attack bonus is quadrupled");
		assertTrue(inside.getMaxHit() > outside.getMaxHit(), "the occult's 5% is 20% inside, 15% outside");
	}
}
