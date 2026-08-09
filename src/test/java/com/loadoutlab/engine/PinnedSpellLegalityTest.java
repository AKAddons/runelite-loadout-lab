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
import org.junit.Assert;
import org.junit.Test;

/**
 * A pinned spell must only ride weapons that can actually cast it
 * (field report 2026-08-09: Blood Barrage pinned on Thermy, but the
 * recommended weapon was a Trident - a powered staff that can only
 * fire its own built-in). The auto-spell path already enforces
 * autocast legality per weapon; the fixed-spell path skipped it.
 */
public class PinnedSpellLegalityTest
{
	private static LoadoutData data;

	private static LoadoutData data()
	{
		if (data == null)
		{
			data = new DataService().load();
		}
		return data;
	}

	private static SpellStats spell(String name)
	{
		return data().getSpells().stream()
			.filter(s -> s.getName().equalsIgnoreCase(name))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static void own(Map<Integer, Integer> owned, String nameLower)
	{
		GearItem item = data().getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + nameLower));
		owned.put(item.getId(), 1);
	}

	private static OptimizationRequest ownedMagicRequest(MonsterStats mob, Map<Integer, Integer> owned)
	{
		return new OptimizationRequest(
			mob, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1);
	}

	@Test
	public void aPinnedBarrageNeverRidesAPoweredStaff()
	{
		MonsterStats thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		own(owned, "trident of the seas");
		own(owned, "ancient staff");

		List<DpsResult> results = new LoadoutOptimizer().optimize(data(),
			ownedMagicRequest(thermy, owned).withSpell(spell("Blood Barrage")));
		Assert.assertFalse("an ancient staff is owned - the pin must field it", results.isEmpty());
		String weapon = results.get(0).getLoadout().get(GearSlot.WEAPON).labelLower();
		Assert.assertFalse("a powered staff cannot cast a pinned spell: " + weapon,
			weapon.contains("trident"));
		Assert.assertTrue("the ancient-capable staff carries the pin: " + weapon,
			weapon.contains("ancient staff"));
	}

	@Test
	public void noAutocastCapableWeaponMeansNoResultNotAnIllegalPair()
	{
		MonsterStats thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		own(owned, "trident of the seas");
		// Harmonised autocasts the standard book only - it must not
		// carry an ancient pin either.
		own(owned, "harmonised nightmare staff");

		List<DpsResult> results = new LoadoutOptimizer().optimize(data(),
			ownedMagicRequest(thermy, owned).withSpell(spell("Blood Barrage")));
		Assert.assertTrue("no owned weapon can autocast the pin - honest empty beats an illegal pair",
			results.isEmpty());
	}

	@Test
	public void aStandardPinStillPricesNormally()
	{
		MonsterStats thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		own(owned, "kodai wand");

		List<DpsResult> results = new LoadoutOptimizer().optimize(data(),
			ownedMagicRequest(thermy, owned).withSpell(spell("Fire Surge")));
		Assert.assertFalse(results.isEmpty());
		Assert.assertTrue(results.get(0).getMaxHit() > 0);
	}
}
