package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.Spell;
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
	private static Tables data;
	private static Monster drake;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		drake = data.searchMonsters("drake", 1).get(0);
		assertEquals("Drake", drake.getName());
	}

	private static Spell spell(String name) {
		return data.getSpells().stream().filter(s -> s.getName().equalsIgnoreCase(name))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static void own(Map<Integer, Integer> owned, String lname, String ver) {
		Gear item = data.getGearItems().stream()
			.filter(g -> g.getLname().equals(lname)
				&& (ver == null ? g.isStandardGear() : g.getVer().equals(ver)))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + lname + " " + ver));
		owned.put(item.getId(), 1);
	}

	/** The reporter's shape: a full magic set, so accuracy is near its
	 * cap and the shield decides damage; wrath runes so Water Surge is
	 * castable (the auto-pick skips a spell whose premium runes are not
	 * banked). */
	private static final String[] MAGIC_SET = {"ancestral hat", "ancestral robe top",
		"ancestral robe bottom", "occult necklace", "tormented bracelet", "eternal boots"};
	private static final int WRATH_RUNE = 21880;

	private static Dps best(Spell pinned, String... ownedNames) {
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(WRATH_RUNE, 1000);
		for (String name : MAGIC_SET) {
			own(owned, name, null);
		}
		for (String name : ownedNames) {
			String[] parts = name.split("#");
			own(owned, parts[0], parts.length > 1 ? parts[1] : null);
		}
		Req request = new Req(
			drake, Style.MAGIC, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 1);
		List<Dps> results = new LoadoutOptimizer().optimize(data,
			pinned == null ? request : request.withSpell(pinned));
		assertFalse(results.isEmpty(), "a kodai set must exist");
		return results.get(0);
	}

	@Test
	@DisplayName("a charged tome of water lifts Water Surge above the fortified ward at a drake")
	void chargedTomeBeatsTheWard() {
		Dps tome = best(spell("Water Surge"), "kodai wand", "tome of water#charged");
		Dps ward = best(spell("Water Surge"), "kodai wand", "elidinis' ward (f)");
		assertEquals("tome of water", tome.getLoadout().get(Slot.SHIELD).getLname());
		assertTrue(tome.getMaxHit() > ward.getMaxHit(),
			"tome max hit " + tome.getMaxHit() + " vs ward " + ward.getMaxHit());
		assertTrue(tome.getDps() > ward.getDps(),
			"tome dps " + tome.getDps() + " vs ward " + ward.getDps());
	}

	@Test
	@DisplayName("an empty tome, an off-element tome and an Ancients spell get nothing")
	void onlyAChargedMatchingTomeCounts() {
		Dps bare = best(spell("Water Surge"), "kodai wand");
		assertEquals(bare.getMaxHit(), best(spell("Water Surge"), "kodai wand", "tome of water#empty").getMaxHit());
		assertEquals(bare.getMaxHit(), best(spell("Water Surge"), "kodai wand", "tome of fire#charged").getMaxHit());
		Dps barrage = best(spell("Ice Barrage"), "ancient staff");
		assertEquals(barrage.getMaxHit(),
			best(spell("Ice Barrage"), "ancient staff", "tome of water#charged").getMaxHit());
	}

	@Test
	@DisplayName("the fire and earth tomes boost damage only; the water tome alone adds accuracy (wiki)")
	void onlyTheWaterTomeAddsAccuracy() {
		// The empty tome carries the same +8 magic attack, so it isolates
		// the charge's own effect.
		Dps empty = best(spell("Fire Surge"), "kodai wand", "tome of fire#empty");
		Dps fire = best(spell("Fire Surge"), "kodai wand", "tome of fire#charged");
		assertEquals("tome of fire", fire.getLoadout().get(Slot.SHIELD).getLname());
		assertTrue(fire.getMaxHit() > empty.getMaxHit(), "the fire tome still lifts the max hit");
		assertEquals(empty.getAccuracy(), fire.getAccuracy(), 1e-9, "no accuracy term on the fire tome");
		Dps emptyWater = best(spell("Water Surge"), "kodai wand", "tome of water#empty");
		Dps water = best(spell("Water Surge"), "kodai wand", "tome of water#charged");
		assertTrue(water.getAccuracy() > emptyWater.getAccuracy(), "the water tome adds accuracy");
	}

	@Test
	@DisplayName("with both owned, the drake's magic card wears the charged tome, not the ward")
	void optimizerPicksTheTome() {
		Dps best = best(null, "kodai wand", "tome of water#charged", "elidinis' ward (f)");
		assertEquals("tome of water", best.getLoadout().get(Slot.SHIELD).getLname());
		assertEquals("Water Surge", best.getSpellName());
	}
}
