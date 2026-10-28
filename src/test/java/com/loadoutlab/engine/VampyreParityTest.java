package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vampyre maths at parity with the official calculator's engine
 * (ISS-01M5RM2A7FFR9XWKATPX877TDD; osrs-dps-calc 89c3e25b PlayerVsNPCCalc
 * vampyre accuracy, damage, tier-2 transforms and immunity; PR #671). Each
 * vampyre is compared with its own twin minus the vampyre attribute, so the
 * expected numbers come from the same set's plain max hit and roll.
 */
class VampyreParityTest {
	private static final int SENTINEL = 9756;
	/** Any loaded juvinate: the loader folds combat-identical versions. */
	private static final int JUVINATE = -2;
	private static final int FERAL = 3237;
	private static final int EFARITAY = 21140;
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static MonsterStats monster(int id) {
		return data.getMonsters().stream()
			.filter(m -> id == JUVINATE ? m.getNameLower().equals("vampyre juvinate") && m.hasAttribute("vampyre2")
				: m.getId() == id)
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing monster " + id));
	}

	/** The same monster without its vampyre tier: the plain baseline. */
	private static MonsterStats plain(int id) {
		MonsterStats m = monster(id);
		return m.withoutAttributes(-m.getId(), m.getVersion(), List.of("vampyre1", "vampyre2", "vampyre3"));
	}

	private static GearItem named(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	private static Loadout wearing(Object... items) {
		Map<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		for (Object item : items) {
			GearItem g = item instanceof Integer ? data.getGear((Integer) item) : named((String) item);
			gear.put(g.getSlot(), g);
		}
		return new Loadout(gear);
	}

	private static DpsResult calc(MonsterStats monster, CombatStyle style, PlayerLevels levels, PrayerBonuses prayers,
		SpellStats spell, Loadout loadout) {
		OptimizationRequest request = new OptimizationRequest(monster, style, levels, prayers, spell, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		return new DpsCalculator().calculate(request, loadout);
	}

	private static DpsResult melee(MonsterStats monster, Loadout loadout) {
		return calc(monster, CombatStyle.MELEE, PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null, loadout);
	}

	/** Expected damage per attack of one uniform hit 0..max at this
	 * accuracy, each roll mapped through the transform. */
	private static double expected(double accuracy, int max, IntUnaryOperator perHit) {
		double sum = 0;
		for (int d = 0; d <= max; d++) {
			sum += perHit.applyAsInt(d);
		}
		return accuracy * sum / (max + 1);
	}

	@Test
	@DisplayName("the reference's own sentinel case: blisterwood stakes max 26, and 28 with Efaritay's aid")
	void referenceStakeCase() {
		PlayerLevels levels = new PlayerLevels(99, 99, 99, 112, 99, 99, 99);
		PrayerBonuses deadeye = PrayerBonuses.forPick(CombatStyle.RANGED, "Deadeye", PrayerBonuses.bestAvailable(levels));
		Object[] set = {11664, 22109, 19547, 33716, 13072, 13073, 8842, 19933};
		DpsResult without = calc(monster(SENTINEL), CombatStyle.RANGED, levels, deadeye, null, wearing(set));
		Object[] withRing = java.util.Arrays.copyOf(set, set.length + 1);
		withRing[set.length] = EFARITAY;
		DpsResult with = calc(monster(SENTINEL), CombatStyle.RANGED, levels, deadeye, null, wearing(withRing));
		assertNotNull(without, "the stake hurts a tier-3 vampyre");
		assertEquals(26, without.getMaxHit());
		assertEquals(28, with.getMaxHit());
	}

	@Test
	@DisplayName("the hallowed flail vs a sentinel: roll x1.25, then x23/20 with Efaritay's aid; max x1.25, the ring's x1.1 first")
	void hallowedFlailVsSentinel() {
		DpsResult base = melee(plain(SENTINEL), wearing("hallowed flail"));
		DpsResult bane = melee(monster(SENTINEL), wearing("hallowed flail"));
		DpsResult ring = melee(monster(SENTINEL), wearing("hallowed flail", EFARITAY));
		int max = base.getMaxHit();
		long roll = base.getAttackRoll();
		assertEquals(max * 125 / 100, bane.getMaxHit());
		assertEquals(roll * 125 / 100, bane.getAttackRoll());
		assertEquals((max * 11 / 10) * 125 / 100, ring.getMaxHit());
		assertEquals((roll * 125 / 100) * 23 / 20, ring.getAttackRoll());
	}

	@Test
	@DisplayName("the blisterwood flail and sickle get +5% accuracy and their own damage; the Ivandis flail damage only")
	void flailAndSickleBonuses() {
		for (Object[] row : new Object[][]{{"blisterwood flail", 105, 125}, {"blisterwood sickle", 105, 115},
			{"ivandis flail", 100, 120}}) {
			String name = (String) row[0];
			DpsResult base = melee(plain(SENTINEL), wearing(name));
			DpsResult bane = melee(monster(SENTINEL), wearing(name));
			assertEquals(base.getAttackRoll() * (int) row[1] / 100, bane.getAttackRoll(), name + " roll");
			assertEquals(base.getMaxHit() * (int) row[2] / 100, bane.getMaxHit(), name + " max");
		}
	}

	@Test
	@DisplayName("a juvinate: a whip is immune, Efaritay's aid halves every hit (the raised accurate 0 too)")
	void tierTwoRegularWeapon() {
		assertNull(melee(monster(JUVINATE), wearing("abyssal whip")), "regular weapons cannot hurt tier 2");
		DpsResult base = melee(plain(JUVINATE), wearing("abyssal whip"));
		DpsResult ring = melee(monster(JUVINATE), wearing("abyssal whip", EFARITAY));
		assertNotNull(ring);
		assertEquals(base.getMaxHit() / 2, ring.getMaxHit());
		double perAttack = expected(ring.getAccuracy(), base.getMaxHit(), d -> Math.max(1, d) / 2);
		assertEquals(perAttack / (ring.getAttackSpeed() * 0.6), ring.getDps(), 1e-9);
	}

	@Test
	@DisplayName("a juvinate: silver weapons cap at 10 (halved with the ring), the Ivandis flail x1.2 then capped, the sunspear x1.5 uncapped")
	void tierTwoSilverAndBane() {
		int silverlight = melee(plain(JUVINATE), wearing("silverlight")).getMaxHit();
		assertEquals(Math.min(silverlight, 10), melee(monster(JUVINATE), wearing("silverlight")).getMaxHit());
		assertEquals(silverlight / 2, melee(monster(JUVINATE), wearing("silverlight", EFARITAY)).getMaxHit());
		int ivandis = melee(plain(JUVINATE), wearing("ivandis flail")).getMaxHit();
		assertEquals(Math.min(ivandis * 6 / 5, 10), melee(monster(JUVINATE), wearing("ivandis flail")).getMaxHit());
		int sunspear = melee(plain(JUVINATE), wearing("sunspear")).getMaxHit();
		assertEquals(sunspear * 150 / 100, melee(monster(JUVINATE), wearing("sunspear")).getMaxHit());
		assertEquals(Math.min(melee(plain(JUVINATE), wearing("blisterwood sickle (e)")).getMaxHit(), 10),
			melee(monster(JUVINATE), wearing("blisterwood sickle (e)")).getMaxHit(), "the (e) sickle is tier-2 silver");
		assertNull(melee(monster(SENTINEL), wearing("blisterwood sickle (e)")), "and no tier-3 weapon");
	}

	@Test
	@DisplayName("a juvinate: silver bolts let a crossbow through, capped at 10; other bolts are immune; magic needs the ring")
	void tierTwoRangedAndMagic() {
		PrayerBonuses best = PrayerBonuses.bestAvailable(PlayerLevels.MAXED);
		int bolts = calc(plain(JUVINATE), CombatStyle.RANGED, PlayerLevels.MAXED, best, null,
			wearing("rune crossbow", 9145)).getMaxHit();
		assertEquals(Math.min(bolts, 10), calc(monster(JUVINATE), CombatStyle.RANGED, PlayerLevels.MAXED, best, null,
			wearing("rune crossbow", 9145)).getMaxHit());
		assertNull(calc(monster(JUVINATE), CombatStyle.RANGED, PlayerLevels.MAXED, best, null,
			wearing("rune crossbow", "adamant bolts")));
		SpellStats bolt = data.getSpells().stream().filter(s -> s.getName().equals("Fire Bolt")).findFirst().get();
		assertNull(calc(monster(JUVINATE), CombatStyle.MAGIC, PlayerLevels.MAXED, best, bolt, wearing("staff of fire")));
		int spell = calc(plain(JUVINATE), CombatStyle.MAGIC, PlayerLevels.MAXED, best, bolt, wearing("staff of fire")).getMaxHit();
		assertEquals(spell / 2, calc(monster(JUVINATE), CombatStyle.MAGIC, PlayerLevels.MAXED, best, bolt,
			wearing("staff of fire", EFARITAY)).getMaxHit());
	}

	@Test
	@DisplayName("a tier-1 vampyre: silverlight x1.1 (x1.1 again and +15% accuracy with the ring); a whip unchanged, ring or not")
	void tierOne() {
		DpsResult base = melee(plain(FERAL), wearing("silverlight"));
		assertEquals(base.getMaxHit() * 11 / 10, melee(monster(FERAL), wearing("silverlight")).getMaxHit());
		DpsResult ring = melee(monster(FERAL), wearing("silverlight", EFARITAY));
		assertEquals((base.getMaxHit() * 11 / 10) * 11 / 10, ring.getMaxHit());
		assertEquals(base.getAttackRoll() * 23 / 20, ring.getAttackRoll());
		int whip = melee(plain(FERAL), wearing("abyssal whip")).getMaxHit();
		assertEquals(whip, melee(monster(FERAL), wearing("abyssal whip")).getMaxHit());
		assertEquals(whip, melee(monster(FERAL), wearing("abyssal whip", EFARITAY)).getMaxHit());
	}

	@Test
	@DisplayName("the optimizer pairs an owned whip with an owned Efaritay's aid vs a juvinate, and finds nothing without the ring")
	void optimizerFindsTheRing() {
		OwnedItems both = new OwnedItems(Map.of(4151, 1, EFARITAY, 1), true);
		OptimizationRequest request = new OptimizationRequest(monster(JUVINATE), CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.OWNED_ONLY, true, false, both,
			RequirementProfile.MAXED, 5);
		List<DpsResult> sets = new LoadoutOptimizer().optimize(data, request);
		assertFalse(sets.isEmpty(), "the ring makes the whip work");
		GearItem ring = sets.get(0).getLoadout().get(GearSlot.RING);
		assertNotNull(ring, "the set wears a ring");
		assertEquals("Efaritay's aid", ring.getName());
		assertEquals("Abyssal whip", sets.get(0).getLoadout().getWeapon().getName());
		OptimizationRequest whipOnly = new OptimizationRequest(monster(JUVINATE), CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(Map.of(4151, 1), true), RequirementProfile.MAXED, 5);
		assertTrue(new LoadoutOptimizer().optimize(data, whipOnly).isEmpty(), "and nothing works without it");
	}

	@Test
	@DisplayName("Efaritay's aid stays in the ring pool vs a vampyre")
	void ringPoolKeepsEfaritay() {
		OptimizationRequest request = new OptimizationRequest(monster(SENTINEL), CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		assertTrue(new LoadoutOptimizer().preparePools(data, request).slotCandidates.get(GearSlot.RING).stream()
			.anyMatch(g -> g != null && g.getId() == EFARITAY));
	}
}
