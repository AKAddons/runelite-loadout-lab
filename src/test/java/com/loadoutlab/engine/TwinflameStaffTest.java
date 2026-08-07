package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Twinflame staff (wiki, verified 2026-07-18): cast speed 6, +10%
 * accuracy and damage on standard-book spells, and elemental
 * Bolt/Blast/Wave spells fire a second hit at 40% - Strike and Surge
 * do not double. Field question that found the gap: Barrows brothers
 * are air-weak (severity 50), and the un-modeled staff lost to a
 * powered staff it should beat.
 */
class TwinflameStaffTest
{
	private static LoadoutData data;
	private static MonsterStats dharok;
	private static final DpsCalculator calc = new DpsCalculator();

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		dharok = data.searchMonsters("dharok", 1).get(0);
	}

	private static Loadout wielding(int weaponId)
	{
		GearItem weapon = data.getGear(weaponId);
		assertNotNull(weapon, "corpus is missing item " + weaponId);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, weapon);
		return new Loadout(gear);
	}

	private static SpellStats spell(String name)
	{
		return data.getSpells().stream().filter(s -> s.getName().equalsIgnoreCase(name))
			.findFirst().orElseThrow(() -> new AssertionError("no spell " + name));
	}

	private static OptimizationRequest request()
	{
		return new OptimizationRequest(dharok, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("the twinflame casts at 6 ticks and doubles Wave but not Surge")
	void doublesWaveNotSurge()
	{
		DpsResult wave = calc.calculate(request().withSpell(spell("Wind Wave")), wielding(30634));
		DpsResult surge = calc.calculate(request().withSpell(spell("Wind Surge")), wielding(30634));
		assertNotNull(wave);
		assertNotNull(surge);
		assertEquals(6, wave.getAttackSpeed(), "wiki: attack and cast speed of 6");
		// Surge's higher base max (23 vs 19) loses to the Wave's 40%
		// second hit - the doubling excludes Surge by name.
		assertTrue(wave.getDps() > surge.getDps(),
			"Wind Wave " + wave.getDps() + " must beat Wind Surge " + surge.getDps());
	}

	@Test
	@DisplayName("vs an air-weak brother the twinflame beats a powered staff")
	void beatsPoweredStaffVsBarrows()
	{
		DpsResult twinflame = calc.calculate(request().withSpell(spell("Wind Wave")), wielding(30634));
		DpsResult trident = calc.calculate(request(), wielding(12899));
		assertNotNull(twinflame);
		assertNotNull(trident);
		assertTrue(twinflame.getDps() > trident.getDps(),
			"twinflame " + twinflame.getDps() + " must beat swamp trident " + trident.getDps()
				+ " vs the air-weak Dharok (field report 2026-07-18)");
	}
}
