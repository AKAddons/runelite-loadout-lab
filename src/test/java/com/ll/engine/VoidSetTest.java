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
 * Void's set value lives entirely in the DPS model - the pieces carry
 * ~zero raw stats, so without pool seeding the zero-score prune dropped
 * the helm/gloves and a stocked bank's top-N cut dropped the top/robe:
 * the set could never assemble for a well-geared account (field question
 * 2026-07-17). These tests pin the seeding and the set math, including
 * the previously missing elite magic void damage.
 */
class VoidSetTest {
	private static Tables data;
	private static Monster ankou;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		ankou = data.searchMonsters("ankou", 1).get(0);
	}

	private static Gear byId(int id) {
		Gear item = data.getGear(id);
		assertNotNull(item, "corpus is missing item " + id);
		return item;
	}

	private static Req request(Style style) {
		return new Req(ankou, style, Levels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("every void piece is seeded into its style's candidate pool")
	void voidPiecesScoreIntoThePool() {
		// The shared pieces enter every style's pool; each helm only its own.
		int[] shared = {8839, 8840, 8842, 13072, 13073}; // top, robe, gloves, elite top, elite robe
		for (Style style : new Style[]{Style.MELEE, Style.RANGED, Style.MAGIC}) {
			for (int id : shared) {
				assertTrue(LoadoutOptimizer.candidateScore(request(style), byId(id), false) > 0,
					"piece " + id + " must be seeded for " + style);
			}
		}
		assertTrue(LoadoutOptimizer.candidateScore(request(Style.RANGED), byId(11664), false) > 0,
			"ranger helm seeds the ranged pool");
		assertTrue(LoadoutOptimizer.candidateScore(request(Style.MAGIC), byId(11663), false) > 0,
			"mage helm seeds the magic pool");
		assertTrue(LoadoutOptimizer.candidateScore(request(Style.MELEE), byId(11665), false) > 0,
			"melee helm seeds the melee pool");
		// The off-style helm stays unseeded (zero score - pruned).
		assertEquals(0.0, LoadoutOptimizer.candidateScore(request(Style.MELEE), byId(11664), false),
			1e-9, "ranger helm has no business in the melee pool");
	}

	@Test
	@DisplayName("elite magic void grants +2.5% magic damage on top of the 45% accuracy")
	void eliteMagicVoidDamage() {
		Dps regular = calcMagic(8839, 8840); // regular top + robe
		Dps elite = calcMagic(13072, 13073); // elite top + robe
		assertTrue(regular.getMaxHit() > 0);
		assertEquals((int) Math.floor(regular.getMaxHit() * 1.025), elite.getMaxHit(),
			"elite = floor(regular x 1.025)");
	}

	private Dps calcMagic(int topId, int robeId) {
		com.ll.data.Spell surge = data.getSpells().stream()
			.filter(sp -> sp.getName().equalsIgnoreCase("Fire Surge"))
			.findFirst().orElseThrow(() -> new AssertionError("no Fire Surge"));
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byName("kodai wand"));
		gear.put(Slot.HEAD, byId(11663));
		gear.put(Slot.BODY, byId(topId));
		gear.put(Slot.LEGS, byId(robeId));
		gear.put(Slot.HANDS, byId(8842));
		return new Calc().calculate(
			request(Style.MAGIC).withSpell(surge), new Kit(gear));
	}

	private static Gear byName(String lname) {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals(lname)) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + lname);
	}
}
