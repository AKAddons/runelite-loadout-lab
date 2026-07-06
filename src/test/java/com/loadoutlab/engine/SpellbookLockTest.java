package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class SpellbookLockTest
{
	@Test
	public void lockRestrictsAutoSpellToTheChosenBook()
	{
		LoadoutData data = new DataService().load();
		MonsterStats td = data.searchMonsters("tormented demon", 1).get(0);
		OptimizationRequest base = new OptimizationRequest(
			td, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);

		// Unlocked vs demons: the arceuus demonbane wins.
		DpsResult any = new LoadoutOptimizer().optimize(data, base).get(0);
		Assert.assertTrue(any.getSpellName().contains("Demonbane")
			|| any.getSpellName().isEmpty()); // powered staff also legal

		// Locked to standard: no arceuus spell may appear.
		DpsResult standard = new LoadoutOptimizer().optimize(data,
			base.withSpellbookLock("standard")).get(0);
		if (!standard.getSpellName().isEmpty())
		{
			String book = data.getSpells().stream()
				.filter(s -> s.getName().equals(standard.getSpellName()))
				.findFirst().orElseThrow(AssertionError::new).getSpellbook();
			Assert.assertEquals("standard", book);
		}

		// Locked to ancient: the pick is an ancient spell or a powered staff.
		DpsResult ancient = new LoadoutOptimizer().optimize(data,
			base.withSpellbookLock("ancient")).get(0);
		if (!ancient.getSpellName().isEmpty())
		{
			String book = data.getSpells().stream()
				.filter(s -> s.getName().equals(ancient.getSpellName()))
				.findFirst().orElseThrow(AssertionError::new).getSpellbook();
			Assert.assertEquals("ancient", book);
		}
	}
}
