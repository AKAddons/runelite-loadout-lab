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
		Assert.assertTrue(any.getSpellName().contains("Demonbane")
			|| any.getSpellName().isEmpty());

		// Locked to standard: no arceuus spell may appear.
		DpsResult standard = new LoadoutOptimizer().optimize(data(),
			base.withSpellbookLock("standard")).get(0);
		if (!standard.getSpellName().isEmpty())
		{
			Assert.assertEquals("standard", bookOf(standard.getSpellName()));
		}

		// Locked to ancient: the pick is an ancient spell or a powered staff.
		DpsResult ancient = new LoadoutOptimizer().optimize(data(),
			base.withSpellbookLock("ancient")).get(0);
		if (!ancient.getSpellName().isEmpty())
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
		Assert.assertFalse(results.get(0).getSpellName().isEmpty());
		Assert.assertTrue(results.get(0).getMaxHit() > 0);
	}

	private static String bookOf(String spellName)
	{
		return data().getSpells().stream()
			.filter(s -> s.getName().equals(spellName))
			.findFirst().orElseThrow(AssertionError::new).getSpellbook();
	}
}
