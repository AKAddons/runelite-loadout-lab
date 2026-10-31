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
 * Field report (Andrew, 2026-10-26): "the plugin is completely disregarding
 * keris partisan on kephri". Two keris effects the official calc models
 * (PlayerVsNPCCalc.ts) were missing: every keris has a 1/51 chance of a
 * triple hit against kalphites, and inside the Tombs the keris partisan of
 * the sun rolls accuracy x5/4 while the target is below 25% hitpoints.
 */
class KerisProcTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Monster mob(String name, String version) {
		return data.getMonsters().stream()
			.filter(m -> m.getName().equalsIgnoreCase(name) && (version.isEmpty() || m.getVersion().equalsIgnoreCase(version)))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static Dps wielding(String weapon, Monster mob) {
		Gear item = data.getGearItems().stream()
			.filter(g -> g.getLname().equals(weapon) && g.isStandardGear())
			.findFirst().orElseThrow(AssertionError::new);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, item);
		Req request = new Req(mob, Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED, new Prayers(true, true, true, true, true)),
			null, 0, CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		Dps result = new Calc().calculate(request, new Kit(gear));
		assertNotNull(result);
		return result;
	}

	private static double plain(Dps r) {
		return RollMath.eh(r.getAccuracy(), 0, r.getMaxHit());
	}

	@Test
	@DisplayName("a keris triples one hit in 51 against a kalphite - the expected hit is 53/51 of a plain one")
	void kerisTriplesAgainstKalphites() {
		Dps kephri = wielding("keris partisan", mob("Kephri", "Shielded"));
		assertEquals(plain(kephri) * 53 / 51, kephri.getEh(), 1e-9);
		Dps queen = wielding("keris partisan", mob("Kalphite Queen", ""));
		assertEquals(plain(queen) * 53 / 51, queen.getEh(), 1e-9);
	}

	@Test
	@DisplayName("no triple hit against a mob that is not a kalphite")
	void noTripleOffKalphites() {
		Dps goblin = wielding("keris partisan", mob("Goblin", ""));
		assertEquals(plain(goblin), goblin.getEh(), 1e-9);
	}

	@Test
	@DisplayName("the sun keris inside the Tombs: the last quarter of the hitpoints at x5/4 accuracy, and the triple hit")
	void sunKerisInsideToa() {
		Dps r = wielding("keris partisan of the sun", mob("Kephri", "Shielded"));
		double boosted = RollMath.eh(RollMath.normalAccuracy(r.getAr() * 5 / 4, r.getDr()),
			0, r.getMaxHit());
		double timed = 1 / (0.75 / plain(r) + 0.25 / boosted);
		assertTrue(boosted > plain(r), "the boost raises the hit chance");
		assertEquals(timed * 53 / 51, r.getEh(), 1e-9);
	}

	@Test
	@DisplayName("the sun keris outside the Tombs has no accuracy boost")
	void sunKerisOutsideToa() {
		Dps goblin = wielding("keris partisan of the sun", mob("Goblin", ""));
		assertEquals(plain(goblin), goblin.getEh(), 1e-9);
	}
}
