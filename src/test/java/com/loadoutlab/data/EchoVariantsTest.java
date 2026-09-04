package com.loadoutlab.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Andrew, 2026-09-03: "should not be able to search 'echo' version in
 * main mode" - the Leagues echo bosses are seasonal and never enter the
 * corpus. */
class EchoVariantsTest
{
	@Test
	@DisplayName("no Echo boss enters the corpus, so none can be searched")
	void noEchoBosses()
	{
		LoadoutData data = new DataService().load();
		for (MonsterStats m : data.getMonsters())
		{
			assertFalse(m.getName().contains("(Echo)"), m.getName());
		}
		assertFalse(data.searchMonsters("cerberus", 10).isEmpty(), "the main-game Cerberus still answers");
		for (MonsterStats m : data.searchMonsters("echo", 20))
		{
			assertFalse(m.getName().contains("(Echo)"), m.getName());
		}
	}
}
