package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterNotes;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
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

	/** A loadout wielding just this weapon. */
	private static Loadout wielding(String nameLower)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName(nameLower));
		return new Loadout(gear);
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
		OptimizationRequest request = TestRequests.of(vents,
			CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, request);
		assertFalse(out.isEmpty());
		GearItem weapon = out.get(0).getLoadout().getWeapon();
		assertTrue("Polearm".equals(weapon.getCategory()) || weapon.isMeleeDemonbane(),
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
		DpsResult whip = new DpsCalculator().calculate(req(vents), wielding("abyssal whip"));
		assertTrue(whip == null || whip.getDps() == 0.0,
			"a whip must deal zero to the vents, got "
				+ (whip == null ? "null" : whip.getDps()));

		DpsResult halberd = new DpsCalculator().calculate(req(vents), wielding("crystal halberd"));
		assertTrue(halberd != null && halberd.getDps() > 0,
			"a halberd must still land");
	}

	@Test
	@DisplayName("a demonbane hit is modeled as the one-shot it is, in every style")
	void demonbaneOneShotMath()
	{
		DpsResult arclight = new DpsCalculator().calculate(req(vents), wielding("arclight"));
		assertNotNull(arclight);
		assertEquals(vents.getHitpoints(), arclight.getMaxHit(),
			"a demonbane max hit vs a vent is the vent's whole hp bar");
		assertEquals(arclight.getAccuracy() * vents.getHitpoints(),
			arclight.getExpectedHit(), 1e-6);
	}

	@Test
	@DisplayName("the bow beats melee demonbane at the vents: no walking between them")
	void rangedDemonbaneOutranksMeleeDemonbane()
	{
		DpsResult bow = new DpsCalculator().calculate(
			TestRequests.of(vents, CombatStyle.RANGED, PlayerLevels.MAXED,
				PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, true, false,
				OwnedItems.EMPTY, 1), wielding("scorching bow"));

		DpsResult ember = new DpsCalculator().calculate(req(vents), wielding("emberlight"));

		assertNotNull(bow);
		assertNotNull(ember);
		assertTrue(bow.getDps() > ember.getDps() * 1.5,
			"one spot vs four walks: the bow must lead decisively, got bow="
				+ bow.getDps() + " vs melee=" + ember.getDps());

		// The melee interval carries exactly the published walk penalty.
		int reach = MonsterMechanics.meleeReachPenaltyTicks(vents);
		assertTrue(reach > 0, "the vents must publish a melee reach penalty");
		assertEquals(
			ember.getAccuracy() * vents.getHitpoints()
				/ ((ember.getAttackSpeed() + reach) * 0.6),
			ember.getDps(), 1e-6);
	}

	@Test
	@DisplayName("the ranged pick vs a vent is the Scorching bow, not a blowpipe")
	void scorchingBowBeatsTheBlowpipe()
	{
		OptimizationRequest request = TestRequests.of(vents,
			CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, request);
		assertFalse(out.isEmpty());
		assertTrue(out.get(0).getLoadout().getWeapon().getNameLower().startsWith("scorching bow"),
			"the one-shot must out-rank ordinary ranged dps, was: "
				+ out.get(0).getLoadout().getWeapon().label());
	}

	@Test
	@DisplayName("every other landed hit deals at least half its max (1.5x expectation)")
	void minHitScalesTheExpectation()
	{
		// Compare mean damage PER LANDED HIT (expected/accuracy) against the
		// same weapon at a plain target: gear and levels identical, so the
		// only difference is the vents' min-hit rule. Asserting against a
		// reconstructed acc*max*0.75 was ~0.2% off - the base roll model has
		// its own subtleties; the 1.5x scaling is the contract.
		Loadout halberd = wielding("crystal halberd");

		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		DpsResult atVents = new DpsCalculator().calculate(req(vents), halberd);
		DpsResult atGoblin = new DpsCalculator().calculate(req(goblin), halberd);
		assertNotNull(atVents);
		assertNotNull(atGoblin);
		double meanVents = atVents.getExpectedHit() / atVents.getAccuracy();
		double meanPlain = atGoblin.getExpectedHit() / atGoblin.getAccuracy();
		assertEquals(1.5, meanVents / meanPlain, 1e-9,
			"the vents' min-hit must scale the per-hit mean by exactly 1.5");
	}

	private static OptimizationRequest req(MonsterStats monster)
	{
		return TestRequests.of(monster,
			CombatStyle.MELEE, PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
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
