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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The salve amulet family's tiers, wiki-verified (2026-07 player audit
 * A2.1/A2.2): melee/ranged - base and (i) at 16.67%, (e) and (ei) at 20%;
 * magic - (i) 15% accuracy AND damage, (ei) 20% accuracy AND damage. The
 * original engine matched "salve amulet (e)" (with a space), so the
 * spaceless "Salve amulet(ei)" fell into the 16.67% bucket, and the magic
 * DAMAGE path had no salve branch at all.
 */
class SalveTest {
	private static Tables data;
	private static Monster ankou;   // undead, magic-attackable

	@BeforeAll
	static void load() {
		data = new DataService().load();
		ankou = data.searchMonsters("ankou", 1).get(0);
		assertTrue(ankou.hasAttribute("undead"), "test premise: ankou is undead");
	}

	private static Gear byName(String lname) {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals(lname)) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + lname);
	}

	private static Req request(Style style) {
		return new Req(ankou, style, Levels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static Dps calc(Style style, Gear weapon, Gear neck) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, weapon);
		if (neck != null) {
			gear.put(neck.getSlot(), neck);
		}
		return new Calc().calculate(request(style), new Kit(gear));
	}

	@Test
	@DisplayName("melee: salve(ei) sits in the 20% tier with salve (e), above (i) and base")
	void meleeTiers() {
		Gear whip = byName("abyssal whip");
		double ei = calc(Style.MELEE, whip, byName("salve amulet(ei)")).getDps();
		double e = calc(Style.MELEE, whip, byName("salve amulet (e)")).getDps();
		double i = calc(Style.MELEE, whip, byName("salve amulet(i)")).getDps();
		double base = calc(Style.MELEE, whip, byName("salve amulet")).getDps();

		assertEquals(e, ei, 1e-9, "(ei) must match (e)'s 20% melee tier");
		assertEquals(base, i, 1e-9, "(i) shares the base 16.67% melee tier");
		assertTrue(ei > i, "20% tier must beat 16.67% tier");
	}

	@Test
	@DisplayName("magic: salve(ei) raises the MAX HIT 20% and (i) 15% against undead")
	void magicDamageTiers() {
		Gear sang = byName("sanguinesti staff");
		int bare = calc(Style.MAGIC, sang, null).getMaxHit();
		int ei = calc(Style.MAGIC, sang, byName("salve amulet(ei)")).getMaxHit();
		int i = calc(Style.MAGIC, sang, byName("salve amulet(i)")).getMaxHit();

		assertEquals(multiply(bare, 6, 5), ei, "(ei) magic damage is +20%");
		assertEquals(multiply(bare, 23, 20), i, "(i) magic damage is +15%");
	}

	@Test
	@DisplayName("salves do nothing against the living")
	void noEffectOnNonUndead() {
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		assertFalse(goblin.hasAttribute("undead"));
		Req req = new Req(goblin, Style.MELEE,
			Levels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		Gear whip = byName("abyssal whip");

		EnumMap<Slot, Gear> bare = new EnumMap<>(Slot.class);
		bare.put(Slot.WEAPON, whip);
		EnumMap<Slot, Gear> salved = new EnumMap<>(bare);
		Gear ei = byName("salve amulet(ei)");
		salved.put(ei.getSlot(), ei);

		assertEquals(new Calc().calculate(req, new Kit(bare)).getDps(),
			new Calc().calculate(req, new Kit(salved)).getDps(), 1e-9);
	}

	private static int multiply(int value, int numerator, int denominator) {
		return value * numerator / denominator;
	}
}
