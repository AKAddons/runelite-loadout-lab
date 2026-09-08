package com.loadoutlab.data;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DegradableTest
{
	@Test
	@DisplayName("gear that wears down or burns charges in combat degrades; charge-once shields, corrupted crystal and the plain whip do not")
	void definition()
	{
		for (String yes : new String[]{"dharok's helm", "echo ahrim's hood", "blood moon tassets",
			"bow of faerdhinen", "blade of saeldor", "crystal body", "arclight", "abyssal tentacle",
			"toxic blowpipe", "tumeken's shadow", "serpentine helm", "amulet of blood fury", "tome of fire"})
		{
			assertTrue(Degradable.matches(yes), yes);
		}
		for (String no : new String[]{"bow of faerdhinen (c)", "blade of saeldor (c)", "emberlight",
			"abyssal whip", "dragonfire shield", "dragonfire ward", "ancient wyvern shield", "amulet of glory",
			"graceful hood", "dragon platelegs", "crystal bow (historical)", "staff of the dead", "elder maul"})
		{
			assertFalse(Degradable.matches(no), no);
		}
	}

	@Test
	@DisplayName("the id set comes from the gear corpus: every Dharok's helm state is in, dragon platelegs and the dragonfire shield are not")
	void ids()
	{
		Set<Integer> ids = Degradable.ids(new DataService().load());
		assertTrue(ids.contains(4716), "Dharok's helm (undamaged)");
		assertTrue(ids.contains(4880), "Dharok's helm 100");
		assertTrue(ids.contains(12006), "abyssal tentacle");
		assertFalse(ids.contains(4087), "dragon platelegs");
		assertFalse(ids.contains(11283), "dragonfire shield");
		assertFalse(ids.contains(25867), "bow of faerdhinen (c)");
	}
}
