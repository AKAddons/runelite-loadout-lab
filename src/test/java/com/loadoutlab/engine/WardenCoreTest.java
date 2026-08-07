package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterNotes;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Wardens' ejected core (ToA P2, wiki-verified 2026-08-06): "Players
 * will always deal their max hit when using melee against the Warden's
 * core" - no accuracy roll, no damage roll. The certainty is the whole
 * reason the phase is a spec dump (field report 2026-08-06: "ToA is
 * missing the crucial heart phase with the DDS spec").
 */
class WardenCoreTest
{
	private static LoadoutData data;
	private static MonsterStats core;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		core = data.searchMonsters("tumeken's warden", 8).stream()
			.filter(m -> m.getVersion().startsWith("Core-ejected"))
			.findFirst().orElseThrow();
		assertTrue(MonsterMechanics.isWardenCore(core), "test premise: the core row");
		assertEquals(1, core.getSize(), "the ejected core is a 1x1 target - the"
			+ " Warden-frame size 5 handed the chally sweep a phantom second hit"
			+ " (field report 2026-08-06)");
	}

	private static GearItem byName(String nameLower)
	{
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals(nameLower))
			{
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower);
	}

	private static Loadout wielding(String nameLower)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName(nameLower));
		return new Loadout(gear);
	}

	private static OptimizationRequest req(MonsterStats monster)
	{
		return TestRequests.of(monster,
			CombatStyle.MELEE, PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
	}

	@Test
	@DisplayName("melee at the core always deals its max: accuracy 1, expectation = max")
	void meleeAlwaysMaxes()
	{
		DpsResult whip = new DpsCalculator().calculate(req(core), wielding("abyssal whip"));
		assertNotNull(whip);
		assertEquals(1.0, whip.getAccuracy(), 1e-9, "no accuracy roll at the core");
		assertEquals(whip.getMaxHit(), whip.getExpectedHit(), 1e-9,
			"no damage roll either - every hit is the max");
		assertEquals(whip.getMaxHit() / (whip.getAttackSpeed() * 0.6),
			whip.getDps(), 1e-9);
		assertTrue(whip.getCountedBonuses().stream().anyMatch(b -> b.contains("core")),
			"the certainty must be a COUNTED assumption, visible in the assurance line");
	}

	@Test
	@DisplayName("the DDS spec prices as both boosted hits at max - the classic dump")
	void ddsSpecIsTwoMaxHits()
	{
		DpsResult base = new DpsCalculator().calculate(req(core), wielding("dragon dagger"));
		assertNotNull(base);
		SpecialAttack spec = SpecialAttack.match(byName("dragon dagger"));
		assertNotNull(spec, "the corpus must carry the DDS spec");
		double expected = spec.expectedDamage(base, core, PlayerLevels.MAXED);
		assertEquals(2.0 * (int) (base.getMaxHit() * 1.15), expected, 1e-9,
			"two independent hits, both at their boosted max, both landing");
	}

	@Test
	@DisplayName("the always-max rule stays at the core - a goblin still rolls normally")
	void ruleIsScopedToTheCore()
	{
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		DpsResult base = new DpsCalculator().calculate(req(goblin), wielding("dragon dagger"));
		assertNotNull(base);
		SpecialAttack spec = SpecialAttack.match(byName("dragon dagger"));
		double expected = spec.expectedDamage(base, goblin, PlayerLevels.MAXED);
		assertTrue(expected < 2.0 * (int) (base.getMaxHit() * 1.15),
			"away from the core the rolls are real again");
	}

	@Test
	@DisplayName("the core row carries its spec-dump mechanics note")
	void notePresent()
	{
		String note = MonsterNotes.noteFor(core);
		assertNotNull(note);
		assertTrue(note.contains("max hit"), "the note names the always-max rule");
		MonsterStats damaged = data.searchMonsters("tumeken's warden", 8).stream()
			.filter(m -> m.getVersion().startsWith("Damaged"))
			.findFirst().orElseThrow();
		assertNull(MonsterNotes.noteFor(damaged),
			"the note belongs to the core phase alone");
	}
}
