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
 * Kendyy (Discord 2026-10-19): "same goes for if you are using sunfire
 * runes or not". Wiki (Sunfire rune, read 2026-10-19): any successful
 * hit with an offensive fire spell deals a minimum of 10% of the max
 * hit. The official calc takes trunc(maxHit / 10) after the magic
 * damage pool and the elemental weakness and BEFORE the tome step, so
 * a charged tome of fire lifts the max hit without lifting the floor.
 */
class SunfireTest {
	private static Tables data;
	private static Monster araxxor;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		araxxor = data.searchMonsters("araxxor", 1).get(0);
		assertEquals("Araxxor", araxxor.getName());
		assertEquals("fire", araxxor.getWeaknessElement(), "the fixture needs a fire-weak target");
	}

	private static Spell spell(String name) {
		return data.getSpells().stream().filter(s -> s.getName().equalsIgnoreCase(name))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static Gear item(String lname, String ver) {
		return data.getGearItems().stream()
			.filter(g -> g.getLname().equals(lname)
				&& (ver == null ? g.isStandardGear() : g.getVer().equals(ver)))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + lname));
	}

	/** A magic-damage set, so the fixture's max hits sit where the tenth
	 * is worth testing: Fire Wave reads 37 pre-tome and 40 with the tome
	 * (floors 3 and 4) - a bare wand gave 39 and 42, the same floor. */
	private static final String[] MAGIC_SET = {"kodai wand", "occult necklace",
		"tormented bracelet", "ancestral hat", "ancestral robe top", "ancestral robe bottom"};

	private static Dps cast(Spell spell, boolean sunfire, boolean fireTome) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		for (String name : MAGIC_SET) {
			Gear piece = item(name, null);
			gear.put(piece.getSlot(), piece);
		}
		if (fireTome) {
			gear.put(Slot.SHIELD, item("tome of fire", "charged"));
		}
		Req request = new Req(
			araxxor, Style.MAGIC, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), spell, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1).withSunfireRunes(sunfire);
		Dps result = new Calc().calculate(request, new Kit(gear));
		assertNotNull(result);
		return result;
	}

	@Test
	@DisplayName("sunfire runes give Fire Surge a minimum hit of a tenth of its max, priced uniform over [min, max]")
	void sunfireRunesRaiseTheFloor() {
		Dps plain = cast(spell("Fire Surge"), false, false);
		Dps sunfire = cast(spell("Fire Surge"), true, false);
		assertEquals(plain.getMaxHit(), sunfire.getMaxHit(), "the max hit is untouched");
		assertEquals(plain.getAccuracy(), sunfire.getAccuracy(), 1e-12, "accuracy is untouched");
		int min = sunfire.getMaxHit() / 10;
		assertTrue(min >= 1, "the fixture's max hit must be at least 10 for a floor to exist");
		assertEquals(sunfire.getAccuracy() * (min + sunfire.getMaxHit()) / 2.0,
			sunfire.getEh(), 1e-9, "a landed hit is uniform over [min, max]");
		assertTrue(sunfire.getDps() > plain.getDps(), "the floor raises the dps");
	}

	@Test
	@DisplayName("the floor is a tenth of the pre-tome max: a charged tome of fire lifts the max, not the minimum")
	void floorIsTakenBeforeTheTome() {
		Dps bare = cast(spell("Fire Wave"), true, false);
		Dps tome = cast(spell("Fire Wave"), true, true);
		assertTrue(tome.getMaxHit() > bare.getMaxHit(), "the charged fire tome adds damage");
		int floor = bare.getMaxHit() / 10;
		assertNotEquals(tome.getMaxHit() / 10, floor,
			"fixture guard: the post-tome tenth must differ or the ordering is untested");
		assertEquals(tome.getAccuracy() * (floor + tome.getMaxHit()) / 2.0,
			tome.getEh(), 1e-9, "the minimum is still the pre-tome tenth");
	}

	@Test
	@DisplayName("the flag does nothing to a water spell")
	void waterSpellsIgnoreTheFlag() {
		Dps plain = cast(spell("Water Surge"), false, false);
		Dps flagged = cast(spell("Water Surge"), true, false);
		assertEquals(plain.getDps(), flagged.getDps(), 1e-12);
		assertEquals(plain.getEh(), flagged.getEh(), 1e-12);
	}
}
