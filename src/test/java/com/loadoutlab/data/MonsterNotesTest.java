package com.loadoutlab.data;

import org.junit.Assert;
import org.junit.Test;

public class MonsterNotesTest
{
	@Test
	public void gargoyleTypesCarryFinishingNotesAndOthersDoNot()
	{
		LoadoutData data = new DataService().load();
		Assert.assertNotNull(MonsterNotes.noteFor(data.searchMonsters("dusk", 1).get(0)));
		Assert.assertNotNull(MonsterNotes.noteFor(data.searchMonsters("gargoyle", 1).get(0)));
		Assert.assertNotNull(MonsterNotes.noteFor(data.searchMonsters("rockslug", 1).get(0)));
		Assert.assertNull(MonsterNotes.noteFor(data.searchMonsters("goblin", 1).get(0)));
		Assert.assertNotNull(MonsterNotes.noteFor(data.searchMonsters("zulrah", 1).get(0)));
	}
}
