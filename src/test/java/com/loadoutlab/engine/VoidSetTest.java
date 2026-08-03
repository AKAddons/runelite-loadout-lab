package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
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
class VoidSetTest
{
	private static LoadoutData data;
	private static MonsterStats ankou;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		ankou = data.searchMonsters("ankou", 1).get(0);
	}

	private static GearItem byId(int id)
	{
		GearItem item = data.getGear(id);
		assertNotNull(item, "corpus is missing item " + id);
		return item;
	}

	private static OptimizationRequest request(CombatStyle style)
	{
		return new OptimizationRequest(ankou, style, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("every void piece is seeded into its style's candidate pool")
	void voidPiecesScoreIntoThePool()
	{
		// The shared pieces enter every style's pool; each helm only its own.
		int[] shared = {8839, 8840, 8842, 13072, 13073}; // top, robe, gloves, elite top, elite robe
		for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
		{
			for (int id : shared)
			{
				assertTrue(LoadoutOptimizer.candidateScoreForTest(request(style), byId(id)) > 0,
					"piece " + id + " must be seeded for " + style);
			}
		}
		assertTrue(LoadoutOptimizer.candidateScoreForTest(request(CombatStyle.RANGED), byId(11664)) > 0,
			"ranger helm seeds the ranged pool");
		assertTrue(LoadoutOptimizer.candidateScoreForTest(request(CombatStyle.MAGIC), byId(11663)) > 0,
			"mage helm seeds the magic pool");
		assertTrue(LoadoutOptimizer.candidateScoreForTest(request(CombatStyle.MELEE), byId(11665)) > 0,
			"melee helm seeds the melee pool");
		// The off-style helm stays unseeded (zero score - pruned).
		assertEquals(0.0, LoadoutOptimizer.candidateScoreForTest(request(CombatStyle.MELEE), byId(11664)),
			1e-9, "ranger helm has no business in the melee pool");
	}

	@Test
	@DisplayName("elite magic void grants +2.5% magic damage on top of the 45% accuracy")
	void eliteMagicVoidDamage()
	{
		DpsResult regular = calcMagic(8839, 8840); // regular top + robe
		DpsResult elite = calcMagic(13072, 13073); // elite top + robe
		assertTrue(regular.getMaxHit() > 0);
		assertEquals((int) Math.floor(regular.getMaxHit() * 1.025), elite.getMaxHit(),
			"elite = floor(regular x 1.025)");
	}

	private DpsResult calcMagic(int topId, int robeId)
	{
		com.loadoutlab.data.SpellStats surge = data.getSpells().stream()
			.filter(sp -> sp.getName().equalsIgnoreCase("Fire Surge"))
			.findFirst().orElseThrow(() -> new AssertionError("no Fire Surge"));
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName("kodai wand"));
		gear.put(GearSlot.HEAD, byId(11663));
		gear.put(GearSlot.BODY, byId(topId));
		gear.put(GearSlot.LEGS, byId(robeId));
		gear.put(GearSlot.HANDS, byId(8842));
		return new DpsCalculator().calculate(
			request(CombatStyle.MAGIC).withSpell(surge), new Loadout(gear));
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
}
