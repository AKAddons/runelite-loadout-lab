package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.StatBlock;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class QuestRewardItemsTest
{
	private static GearItem gear(String name)
	{
		return new GearItem(1, name, "", GearSlot.HANDS, "", 0, false, true,
			false, true, null, StatBlock.ZERO, StatBlock.ZERO, StatBlock.ZERO, null);
	}

	@Test
	public void knownEntriesResolveToTheirSourceQuest()
	{
		Assert.assertEquals("Recipe for Disaster", QuestRewardItems.questFor(gear("Barrows gloves")));
		Assert.assertEquals("The Blood Moon Rises", QuestRewardItems.questFor(gear("Sunspear")));
		Assert.assertEquals("Underground Pass", QuestRewardItems.questFor(gear("Iban's staff (u)")));
		Assert.assertTrue(QuestRewardItems.isQuestReward(gear("Ava's assembler")));
	}

	@Test
	public void nameMatchingIgnoresCase()
	{
		Assert.assertEquals("Recipe for Disaster", QuestRewardItems.questFor(gear("BARROWS GLOVES")));
		Assert.assertTrue(QuestRewardItems.isQuestReward(gear("blisterwood FLAIL")));
	}

	@Test
	public void nonQuestItemsAreNotQuestRewards()
	{
		Assert.assertNull(QuestRewardItems.questFor(gear("Twisted bow")));
		Assert.assertFalse(QuestRewardItems.isQuestReward(gear("Fire cape")));
		Assert.assertNull(QuestRewardItems.questFor(null));
		Assert.assertFalse(QuestRewardItems.isQuestReward(null));
	}

	@Test
	public void everyCuratedEntryResolvesToARealCorpusItemName()
	{
		Set<String> corpus = new HashSet<>();
		for (GearItem item : new DataService().load().getGearItems())
		{
			corpus.add(item.getName().toLowerCase(Locale.ROOT));
		}
		for (String name : QuestRewardItems.itemNames())
		{
			Assert.assertTrue("quest_rewards.json entry '" + name
				+ "' does not match any gear corpus item name", corpus.contains(name));
		}
	}
}
