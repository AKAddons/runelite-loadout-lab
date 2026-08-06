package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterNotes;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Sire's vents take "magic, ranged or a halberd" only, and a melee
 * demonbane hit destroys one outright (wiki-verified 2026-08-05). Standard
 * melee was being recommended - a whip line vs a target it cannot damage.
 */
class RespiratorySystemTest
{
	private static LoadoutData data;
	private static MonsterStats vents;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		vents = data.searchMonsters("respiratory system", 1).get(0);
		assertEquals(5914, vents.getId(), "test premise: the corpus vent row");
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

	@Test
	@DisplayName("standard melee can never work on a vent; halberds and demonbane can")
	void meleeGating()
	{
		assertFalse(MonsterMechanics.weaponCanEverWork(vents, CombatStyle.MELEE,
			byName("abyssal whip")), "a whip cannot damage the vents");
		assertFalse(MonsterMechanics.weaponCanEverWork(vents, CombatStyle.MELEE,
			byName("osmumten's fang")), "a stab sword cannot damage the vents");
		assertTrue(MonsterMechanics.weaponCanEverWork(vents, CombatStyle.MELEE,
			byName("crystal halberd")), "halberds are the melee exception");
		assertTrue(MonsterMechanics.weaponCanEverWork(vents, CombatStyle.MELEE,
			byName("arclight")), "melee demonbane one-shots a vent");
		assertTrue(MonsterMechanics.weaponCanEverWork(vents, CombatStyle.RANGED,
			byName("toxic blowpipe")), "ranged is unaffected by the melee gate");
	}

	@Test
	@DisplayName("the melee pick vs a vent is a halberd or demonbane, never a whip line")
	void optimizerRespectsTheGate()
	{
		OptimizationRequest request = new OptimizationRequest(vents,
			CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, request);
		assertFalse(out.isEmpty());
		GearItem weapon = out.get(0).getLoadout().getWeapon();
		String name = weapon.getNameLower();
		assertTrue("Polearm".equals(weapon.getCategory())
				|| name.contains("arclight") || name.contains("emberlight")
				|| name.contains("darklight") || name.contains("silverlight"),
			"melee pick must be a halberd or demonbane, was: " + weapon.label());
	}

	@Test
	@DisplayName("the math agrees with the pool: a whip does ZERO dps to a vent")
	void calculateAgreesWithTheGate()
	{
		// The candidate pool filters standard melee out, but re-show paths
		// (the roster kit pass, shared-set evaluation) call calculate()
		// directly - without the damageFactor rule a whip line rendered a
		// phantom 6.97 dps against the vents (found 2026-08-05 while
		// chasing the roster dashes).
		OptimizationRequest request = new OptimizationRequest(vents,
			CombatStyle.MELEE, PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		java.util.EnumMap<com.loadoutlab.data.GearSlot, GearItem> gear =
			new java.util.EnumMap<>(com.loadoutlab.data.GearSlot.class);
		gear.put(com.loadoutlab.data.GearSlot.WEAPON, byName("abyssal whip"));
		DpsResult whip = new DpsCalculator().calculate(request, new Loadout(gear));
		assertTrue(whip == null || whip.getDps() == 0.0,
			"a whip must deal zero to the vents, got "
				+ (whip == null ? "null" : whip.getDps()));

		gear.put(com.loadoutlab.data.GearSlot.WEAPON, byName("crystal halberd"));
		DpsResult halberd = new DpsCalculator().calculate(request, new Loadout(gear));
		assertTrue(halberd != null && halberd.getDps() > 0,
			"a halberd must still land");
	}

	@Test
	@DisplayName("the vents and Sire phase 1 carry their mechanics notes")
	void notesPresent()
	{
		assertNotNull(MonsterNotes.noteFor(vents));
		MonsterStats phase1 = data.searchMonsters("abyssal sire", 4).stream()
			.filter(m -> m.getVersion() != null && m.getVersion().startsWith("Phase 1"))
			.findFirst().orElseThrow();
		String note = MonsterNotes.noteFor(phase1);
		assertNotNull(note);
		assertTrue(note.contains("Shadow"), "phase 1 note names the shadow-spell disorient");
	}
}
