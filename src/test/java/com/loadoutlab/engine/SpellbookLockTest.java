package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class SpellbookLockTest
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

	@Test
	public void lockRestrictsAutoSpellToTheChosenBook()
	{
		MonsterStats td = data().searchMonsters("tormented demon", 1).get(0);
		OptimizationRequest base = new OptimizationRequest(
			td, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);

		// Unlocked vs demons: arceuus demonbane or a powered staff wins.
		DpsResult any = new LoadoutOptimizer().optimize(data(), base).get(0);
		Assert.assertTrue(any.getSpellName() == null
			|| any.getSpellName().contains("Demonbane"));

		// Locked to standard: no arceuus spell may appear.
		DpsResult standard = new LoadoutOptimizer().optimize(data(),
			base.withSpellbookLock("standard")).get(0);
		if (standard.getSpellName() != null)
		{
			Assert.assertEquals("standard", bookOf(standard.getSpellName()));
		}

		// Locked to ancient: the pick is an ancient spell or a powered staff.
		DpsResult ancient = new LoadoutOptimizer().optimize(data(),
			base.withSpellbookLock("ancient")).get(0);
		if (ancient.getSpellName() != null)
		{
			Assert.assertEquals("ancient", bookOf(ancient.getSpellName()));
		}
	}

	@Test
	public void purgingStaffAutocastsAncientsAndNoSpellMeansNoResult()
	{
		MonsterStats graardor = data().searchMonsters("general graardor", 1).get(0);
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		data().getGearItems().stream().filter(g -> g.getName().equalsIgnoreCase("Purging staff"))
			.findFirst().ifPresent(g -> owned.put(g.getId(), 1));
		OptimizationRequest ancientLock = new OptimizationRequest(
			graardor, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1)
			.withSpellbookLock("ancient");
		List<DpsResult> results = new LoadoutOptimizer().optimize(data(), ancientLock);
		// The purging staff CAN autocast ancients - a real barrage result,
		// never a spell-less max-0 garbage row (the field 0.04-dps report).
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotNull(results.get(0).getSpellName());
		Assert.assertTrue(results.get(0).getMaxHit() > 0);
	}

	private static String bookOf(String spellName)
	{
		return data().getSpells().stream()
			.filter(s -> s.getName().equals(spellName))
			.findFirst().orElseThrow(AssertionError::new).getSpellbook();
	}

	@Test
	public void eyeOfAyakEvaluatesAsAPoweredStaffInTheOptimizer()
	{
		// The optimizer once had its own powered-staff list missing the
		// eye of ayak, sending it down the autocast path. A powered staff
		// result carries no spell name.
		com.loadoutlab.data.LoadoutData data = new com.loadoutlab.data.DataService().load();
		com.loadoutlab.data.MonsterStats zulrah = data.searchMonsters("zulrah", 1).get(0);
		java.util.Map<Integer, Integer> owned = java.util.Map.of(31113, 1); // eye of ayak (charged)
		OptimizationRequest req = new OptimizationRequest(zulrah, CombatStyle.MAGIC,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new com.loadoutlab.engine.OwnedItems(data.canonicalizeOwned(owned), true), 1);
		java.util.List<DpsResult> out = new LoadoutOptimizer().optimize(data, req);
		org.junit.Assert.assertFalse(out.isEmpty());
		org.junit.Assert.assertEquals("Eye of ayak", out.get(0).getLoadout().getWeapon().getName());
		org.junit.Assert.assertNull("powered staff results carry no spell name",
			out.get(0).getSpellName());
		org.junit.Assert.assertTrue(out.get(0).getDps() > 0);
	}
}
