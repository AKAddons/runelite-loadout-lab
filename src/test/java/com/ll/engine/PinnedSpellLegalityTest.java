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
import org.junit.Assert;
import org.junit.Test;

/**
 * A pinned spell must only ride weapons that can actually cast it
 * (field report 2026-08-09: Blood Barrage pinned on Thermy, but the
 * recommended weapon was a Trident - a powered staff that can only
 * fire its own built-in). The auto-spell path already enforces
 * autocast legality per weapon; the fixed-spell path skipped it.
 */
public class PinnedSpellLegalityTest {
	private static Tables data;

	private static Tables data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	private static Spell spell(String name) {
		return data().getSpells().stream()
			.filter(s -> s.getName().equalsIgnoreCase(name))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static void own(Map<Integer, Integer> owned, String lname) {
		Gear item = data().getGearItems().stream()
			.filter(g -> g.getLname().equals(lname) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + lname));
		owned.put(item.getId(), 1);
	}

	private static Req ownedMagicRequest(Monster mob, Map<Integer, Integer> owned) {
		return new Req(
			mob, Style.MAGIC, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 1);
	}

	@Test
	public void aPinnedBarrageNeverRidesAPoweredStaff() {
		Monster thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		own(owned, "trident of the seas");
		own(owned, "ancient staff");

		List<Dps> results = new LoadoutOptimizer().optimize(data(),
			ownedMagicRequest(thermy, owned).withSpell(spell("Blood Barrage")));
		Assert.assertFalse("an ancient staff is owned - the pin must field it", results.isEmpty());
		String weapon = results.get(0).getLoadout().get(Slot.WEAPON).labelLower();
		Assert.assertFalse("a powered staff cannot cast a pinned spell: " + weapon,
			weapon.contains("trident"));
		Assert.assertTrue("the ancient-capable staff carries the pin: " + weapon,
			weapon.contains("ancient staff"));
	}

	@Test
	public void noAutocastCapableWeaponMeansNoResultNotAnIllegalPair() {
		Monster thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		own(owned, "trident of the seas");
		// Harmonised autocasts the standard book only - it must not
		// carry an ancient pin either.
		own(owned, "harmonised nightmare staff");

		List<Dps> results = new LoadoutOptimizer().optimize(data(),
			ownedMagicRequest(thermy, owned).withSpell(spell("Blood Barrage")));
		Assert.assertTrue("no owned weapon can autocast the pin - honest empty beats an illegal pair",
			results.isEmpty());
	}

	@Test
	public void aStandardPinStillPricesNormally() {
		Monster thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		own(owned, "kodai wand");

		List<Dps> results = new LoadoutOptimizer().optimize(data(),
			ownedMagicRequest(thermy, owned).withSpell(spell("Fire Surge")));
		Assert.assertFalse(results.isEmpty());
		Assert.assertTrue(results.get(0).getMaxHit() > 0);
	}
}
