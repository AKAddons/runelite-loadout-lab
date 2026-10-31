package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.Spell;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The eclipse atlatl's melee-side damage scaling and the Eclipse /
 * Frostweaver set effects, wiki-verified 2026-07-17. The atlatl base
 * model mirrors the official calc (strength level + worn melee strength
 * bonuses, ranged accuracy untouched); the two set effects go BEYOND the
 * official calc, which flags both sets unsupported - the wiki quantifies
 * them, so we model the expectation and say so in the chips.
 */
class MoonWeaponsTest {
	private static Tables data;
	private static Monster ankou;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		ankou = data.searchMonsters("ankou", 1).get(0);
	}

	private static Gear byNameVersion(String lname, String version) {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals(lname)
				&& (version == null || version.equalsIgnoreCase(item.getVersion()))) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + lname + " " + version);
	}

	private static Spell spell(String name) {
		return data.getSpells().stream()
			.filter(s -> s.getName().equalsIgnoreCase(name))
			.findFirst().orElseThrow(() -> new AssertionError("no spell " + name));
	}

	private static Req request(Style style, Monster monster) {
		return new Req(monster, style, Levels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static EnumMap<Slot, Gear> atlatlGear(boolean fullSet) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byNameVersion("eclipse atlatl", null));
		gear.put(Slot.AMMO, byNameVersion("atlatl dart", null));
		if (fullSet) {
			gear.put(Slot.HEAD, byNameVersion("eclipse moon helm", "New"));
			gear.put(Slot.BODY, byNameVersion("eclipse moon chestplate", "New"));
			gear.put(Slot.LEGS, byNameVersion("eclipse moon tassets", "New"));
		}
		return gear;
	}

	@Test
	@DisplayName("the atlatl's max hit comes from Strength level and worn MELEE strength bonuses")
	void atlatlScalesWithStrength() {
		Kit loadout = new Kit(atlatlGear(false));
		Dps result = new Calc().calculate(request(Style.RANGED, ankou), loadout);
		// Rapid (stance 0), no prayer: effective 99+8, strength term is the
		// worn melee str total (the atlatl's own +40) - NOT ranged strength,
		// which is 0 on both the weapon and the darts.
		int effective = RollMath.effectiveLevel(Levels.MAXED.getStrength(), 1.0, 0);
		assertEquals(RollMath.maxHitFromEffective(effective, loadout.getBonuses().getStrength()),
			result.getMaxHit());
		// The old ranged-strength model derived a near-zero max hit.
		assertTrue(result.getMaxHit() >= 10);
	}

	@Test
	@DisplayName("Eclipse: the full set adds ~2 burn damage per landed hit against a burnable monster")
	void eclipseBurnExpectation() {
		Dps set = new Calc().calculate(
			request(Style.RANGED, ankou), new Kit(atlatlGear(true)));
		// The burn rides expected damage on top of the plain roll: recover
		// the bonus from the result's own accuracy and max hit.
		double base = RollMath.normalExpectedHit(set.getAccuracy(), set.getMaxHit());
		assertEquals(set.getAccuracy() * 2.0, set.getEh() - base, 1e-9);
		assertTrue(set.getCountedBonuses().stream()
			.anyMatch(line -> line.toLowerCase().contains("eclipse set")));
	}

	@Test
	@DisplayName("Eclipse: burn-immune monsters take no burn and show no chip")
	void eclipseBurnRespectsImmunity() {
		Monster immune = data.getMonsters().stream()
			.filter(m -> m.hasAttribute("burn_immune"))
			.findFirst().orElseThrow(() -> new AssertionError("corpus premise: some monster is burn-immune"));
		Dps set = new Calc().calculate(
			request(Style.RANGED, immune), new Kit(atlatlGear(true)));
		double base = RollMath.normalExpectedHit(set.getAccuracy(), set.getMaxHit());
		assertEquals(0.0, set.getEh() - base, 1e-9);
		assertTrue(set.getCountedBonuses().stream()
			.noneMatch(line -> line.toLowerCase().contains("eclipse set")));
	}

	private Dps castWithBlueMoon(Spell spell) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byNameVersion("blue moon spear", null));
		gear.put(Slot.HEAD, byNameVersion("blue moon helm", "New"));
		gear.put(Slot.BODY, byNameVersion("blue moon chestplate", "New"));
		gear.put(Slot.LEGS, byNameVersion("blue moon tassets", "New"));
		return new Calc().calculate(
			request(Style.MAGIC, ankou).withSpell(spell), new Kit(gear));
	}

	private static double bonus(Dps result) {
		return result.getEh()
			- RollMath.normalExpectedHit(result.getAccuracy(), result.getMaxHit());
	}

	@Test
	@DisplayName("Frostweaver: grasps proc at 50% and ice spells at 20% - the bonus ratio is exactly 2.5")
	void frostweaverChancesBydSpellClass() {
		Dps grasp = castWithBlueMoon(spell("Undead Grasp"));
		Dps ice = castWithBlueMoon(spell("Ice Barrage"));
		assertTrue(bonus(grasp) > 0, "grasp cast should carry a spear-hit expectation");
		assertTrue(bonus(ice) > 0, "ice cast should carry a spear-hit expectation");
		// Same monster, same spear - the melee expectation cancels, leaving
		// the 0.5 / 0.2 chance ratio.
		assertEquals(2.5, bonus(grasp) / bonus(ice), 1e-9);
		assertTrue(grasp.getCountedBonuses().stream()
			.anyMatch(line -> line.toLowerCase().contains("frostweaver")));
	}

	@Test
	@DisplayName("Frostweaver: non-binding spells get nothing")
	void frostweaverIgnoresPlainSpells() {
		Dps surge = castWithBlueMoon(spell("Fire Surge"));
		assertEquals(0.0, bonus(surge), 1e-9);
		assertTrue(surge.getCountedBonuses().stream()
			.noneMatch(line -> line.toLowerCase().contains("frostweaver")));
	}
}
