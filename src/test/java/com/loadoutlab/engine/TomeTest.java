package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * rakaykhan1512 (Discord 2026-10-18): "it wasn't including the tome of
 * water in my bank when using water surge on drakes as my best mage dps
 * instead it suggested elidinis ward though its clearly lower dps".
 * Wiki (Tome of Water): a charged tome raises a standard-spellbook
 * spell of its element by 10% accuracy and 10% damage against NPCs,
 * multiplicative with the magic damage pool. Empty tomes, off-element
 * spells and Ancients get nothing.
 */
class TomeTest {
	private static LoadoutData data;
	private static MonsterStats drake;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		drake = data.searchMonsters("drake", 1).get(0);
		assertEquals("Drake", drake.getName());
	}

	private static SpellStats spell(String name) {
		return data.getSpells().stream().filter(s -> s.getName().equalsIgnoreCase(name))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static void own(Map<Integer, Integer> owned, String nameLower, String versionLower) {
		GearItem item = data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower)
				&& (versionLower == null ? g.isStandardGear() : g.getVersionLower().equals(versionLower)))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + nameLower + " " + versionLower));
		owned.put(item.getId(), 1);
	}

	/** The reporter's shape: a full magic set, so accuracy is near its
	 * cap and the shield decides damage; wrath runes so Water Surge is
	 * castable (the auto-pick skips a spell whose premium runes are not
	 * banked). */
	private static final String[] MAGIC_SET = {"ancestral hat", "ancestral robe top",
		"ancestral robe bottom", "occult necklace", "tormented bracelet", "eternal boots"};
	private static final int WRATH_RUNE = 21880;

	private static DpsResult best(SpellStats pinned, String... ownedNames) {
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(WRATH_RUNE, 1000);
		for (String name : MAGIC_SET) {
			own(owned, name, null);
		}
		for (String name : ownedNames) {
			String[] parts = name.split("#");
			own(owned, parts[0], parts.length > 1 ? parts[1] : null);
		}
		OptimizationRequest request = new OptimizationRequest(
			drake, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data,
			pinned == null ? request : request.withSpell(pinned));
		assertFalse(results.isEmpty(), "a kodai set must exist");
		return results.get(0);
	}

	@Test
	@DisplayName("a charged tome of water lifts Water Surge above the fortified ward at a drake")
	void chargedTomeBeatsTheWard() {
		DpsResult tome = best(spell("Water Surge"), "kodai wand", "tome of water#charged");
		DpsResult ward = best(spell("Water Surge"), "kodai wand", "elidinis' ward (f)");
		assertEquals("tome of water", tome.getLoadout().get(GearSlot.SHIELD).getNameLower());
		assertTrue(tome.getMaxHit() > ward.getMaxHit(),
			"tome max hit " + tome.getMaxHit() + " vs ward " + ward.getMaxHit());
		assertTrue(tome.getDps() > ward.getDps(),
			"tome dps " + tome.getDps() + " vs ward " + ward.getDps());
	}

	@Test
	@DisplayName("an empty tome, an off-element tome and an Ancients spell get nothing")
	void onlyAChargedMatchingTomeCounts() {
		DpsResult bare = best(spell("Water Surge"), "kodai wand");
		assertEquals(bare.getMaxHit(), best(spell("Water Surge"), "kodai wand", "tome of water#empty").getMaxHit());
		assertEquals(bare.getMaxHit(), best(spell("Water Surge"), "kodai wand", "tome of fire#charged").getMaxHit());
		DpsResult barrage = best(spell("Ice Barrage"), "ancient staff");
		assertEquals(barrage.getMaxHit(),
			best(spell("Ice Barrage"), "ancient staff", "tome of water#charged").getMaxHit());
	}

	@Test
	@DisplayName("the fire and earth tomes boost damage only; the water tome alone adds accuracy (wiki)")
	void onlyTheWaterTomeAddsAccuracy() {
		// The empty tome carries the same +8 magic attack, so it isolates
		// the charge's own effect.
		DpsResult empty = best(spell("Fire Surge"), "kodai wand", "tome of fire#empty");
		DpsResult fire = best(spell("Fire Surge"), "kodai wand", "tome of fire#charged");
		assertEquals("tome of fire", fire.getLoadout().get(GearSlot.SHIELD).getNameLower());
		assertTrue(fire.getMaxHit() > empty.getMaxHit(), "the fire tome still lifts the max hit");
		assertEquals(empty.getAccuracy(), fire.getAccuracy(), 1e-9, "no accuracy term on the fire tome");
		DpsResult emptyWater = best(spell("Water Surge"), "kodai wand", "tome of water#empty");
		DpsResult water = best(spell("Water Surge"), "kodai wand", "tome of water#charged");
		assertTrue(water.getAccuracy() > emptyWater.getAccuracy(), "the water tome adds accuracy");
	}

	@Test
	@DisplayName("with both owned, the drake's magic card wears the charged tome, not the ward")
	void optimizerPicksTheTome() {
		DpsResult best = best(null, "kodai wand", "tome of water#charged", "elidinis' ward (f)");
		assertEquals("tome of water", best.getLoadout().get(GearSlot.SHIELD).getNameLower());
		assertEquals("Water Surge", best.getSpellName());
	}
}
