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
 * Field report (Andrew, 2026-10-26): "the plugin is completely disregarding
 * keris partisan on kephri". Two keris effects the official calc models
 * (PlayerVsNPCCalc.ts) were missing: every keris has a 1/51 chance of a
 * triple hit against kalphites, and inside the Tombs the keris partisan of
 * the sun rolls accuracy x5/4 while the target is below 25% hitpoints.
 */
class KerisProcTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Monster mob(String name, String version) {
		return data.getMonsters().stream()
			.filter(m -> m.getName().equalsIgnoreCase(name) && (version.isEmpty() || m.getVersion().equalsIgnoreCase(version)))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static DpsResult wielding(String weapon, Monster mob) {
		Gear item = data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(weapon) && g.isStandardGear())
			.findFirst().orElseThrow(AssertionError::new);
		EnumMap<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, item);
		OptimizationRequest request = new OptimizationRequest(mob, Style.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, new PrayerUnlocks(true, true, true, true, true)),
			null, 0, CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		assertNotNull(result);
		return result;
	}

	private static double plain(DpsResult r) {
		return RollMath.expectedHit(r.getAccuracy(), 0, r.getMaxHit());
	}

	@Test
	@DisplayName("a keris triples one hit in 51 against a kalphite - the expected hit is 53/51 of a plain one")
	void kerisTriplesAgainstKalphites() {
		DpsResult kephri = wielding("keris partisan", mob("Kephri", "Shielded"));
		assertEquals(plain(kephri) * 53 / 51, kephri.getExpectedHit(), 1e-9);
		DpsResult queen = wielding("keris partisan", mob("Kalphite Queen", ""));
		assertEquals(plain(queen) * 53 / 51, queen.getExpectedHit(), 1e-9);
	}

	@Test
	@DisplayName("no triple hit against a mob that is not a kalphite")
	void noTripleOffKalphites() {
		DpsResult goblin = wielding("keris partisan", mob("Goblin", ""));
		assertEquals(plain(goblin), goblin.getExpectedHit(), 1e-9);
	}

	@Test
	@DisplayName("the sun keris inside the Tombs: the last quarter of the hitpoints at x5/4 accuracy, and the triple hit")
	void sunKerisInsideToa() {
		DpsResult r = wielding("keris partisan of the sun", mob("Kephri", "Shielded"));
		double boosted = RollMath.expectedHit(RollMath.normalAccuracy(r.getAttackRoll() * 5 / 4, r.getDefenceRoll()),
			0, r.getMaxHit());
		double timed = 1 / (0.75 / plain(r) + 0.25 / boosted);
		assertTrue(boosted > plain(r), "the boost raises the hit chance");
		assertEquals(timed * 53 / 51, r.getExpectedHit(), 1e-9);
	}

	@Test
	@DisplayName("the sun keris outside the Tombs has no accuracy boost")
	void sunKerisOutsideToa() {
		DpsResult goblin = wielding("keris partisan of the sun", mob("Goblin", ""));
		assertEquals(plain(goblin), goblin.getExpectedHit(), 1e-9);
	}
}
