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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew, 2026-10-26: "superantifire and no shield reuqires protection from
 * magic to prevent the full damage of magical attacks". The wiki's Vorkath
 * dragonfire table (failed roll): unprotected 80, shield 20, PfM 30, antifire
 * 70, super antifire 60, shield + antifire 10, shield + super 0, PfM +
 * antifire 20, PfM + super 10.
 */
class VorkathDragonfireTest {
	private static LoadoutData data;
	private static Monster vorkath;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		vorkath = data.getMonsters().stream().filter(m -> m.getName().equals("Vorkath"))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static Loadout wearing(String shield) {
		EnumMap<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
		if (shield != null) {
			gear.put(GearSlot.SHIELD, data.getGearItems().stream()
				.filter(g -> g.getNameLower().equals(shield) && g.isStandardGear())
				.findFirst().orElseThrow(AssertionError::new));
		}
		return new Loadout(gear);
	}

	private static IncomingDpsCalculator.StyleThreat dragonfire(IncomingDpsCalculator.Result result) {
		return result.threats.stream().filter(t -> t.style.toLowerCase().contains("dragonfire"))
			.findFirst().orElseThrow(() -> new AssertionError("no dragonfire row"));
	}

	@Test
	@DisplayName("super antifire without a shield prays Magic: the dragonfire's 60 comes down to 10")
	void superAntifireNoShieldPraysMagic() {
		IncomingDpsCalculator.Result r = IncomingDpsCalculator.calculate(vorkath, wearing(null), 99, 99, 2);
		assertEquals("Protect from Magic", r.protectPrayer);
		IncomingDpsCalculator.StyleThreat fire = dragonfire(r);
		assertEquals(60, fire.maxHit);
		assertTrue(fire.blocked);
		assertEquals(10.0 / 60, fire.prayerFactor, 1e-9);
	}

	@Test
	@DisplayName("super antifire with an anti-dragon shield: no dragonfire damage, Protect from Missiles")
	void superAntifireWithShield() {
		IncomingDpsCalculator.Result r = IncomingDpsCalculator.calculate(vorkath, wearing("anti-dragon shield"), 99, 99, 2);
		assertEquals(0, dragonfire(r).maxHit);
		assertEquals("Protect from Missiles", r.protectPrayer);
	}

	@Test
	@DisplayName("antifire with a shield: 10 under either prayer, Protect from Missiles")
	void antifireWithShield() {
		IncomingDpsCalculator.Result r = IncomingDpsCalculator.calculate(vorkath, wearing("anti-dragon shield"), 99, 99, 1);
		IncomingDpsCalculator.StyleThreat fire = dragonfire(r);
		assertEquals(10, fire.maxHit);
		assertFalse(fire.blocked);
		assertEquals("Protect from Missiles", r.protectPrayer);
	}

	@Test
	@DisplayName("a shield and no potion: the dragonfire hits up to 20")
	void shieldOnly() {
		assertEquals(20, dragonfire(IncomingDpsCalculator.calculate(vorkath, wearing("anti-dragon shield"), 99, 99, 0)).maxHit);
	}
}
