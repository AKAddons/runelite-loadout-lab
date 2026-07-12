package com.loadoutlab.data;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StashUnitsTest
{
	@Test
	@DisplayName("the vendored STASH table loads with every unit and positive item ids")
	void tableLoads()
	{
		StashUnits units = StashUnits.load();
		assertTrue(units.size() >= 100, "expected 100+ STASH units, got " + units.size());

		int[] warriors = units.itemsFor("Warriors' Guild bank");
		assertNotNull(warriors);
		for (int id : warriors)
		{
			assertTrue(id > 0);
		}
		assertNull(units.itemsFor("not a stash unit"));
	}

	@Test
	@DisplayName("the two Warriors' Guild bank units stay distinct across tiers")
	void warriorsGuildTiersAreDistinct()
	{
		StashUnits units = StashUnits.load();
		assertNotNull(units.itemsFor("Warriors' Guild bank"));
		assertNotNull(units.itemsFor("Warriors' Guild bank (master)"));
		assertNotEquals(
			units.itemsFor("Warriors' Guild bank").length,
			units.itemsFor("Warriors' Guild bank (master)").length,
			"the hard and master units hold different item sets");
	}

	@Test
	@DisplayName("a unit is filled only when its text cell is followed by two sprites")
	void chartStateMachine()
	{
		List<StashUnits.Cell> cells = List.of(
			new StashUnits.Cell(StashUnits.TEXT_CELL, "Filled unit"),
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""),
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""),
			new StashUnits.Cell(StashUnits.TEXT_CELL, "Built only"),
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""),
			new StashUnits.Cell(StashUnits.TEXT_CELL, "Unbuilt"),
			new StashUnits.Cell(StashUnits.TEXT_CELL, "Trailing filled"),
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""),
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""));

		assertEquals(List.of("Filled unit", "Trailing filled"),
			StashUnits.filledNames(cells, false));
	}

	@Test
	@DisplayName("a leading sprite with no unit yet is ignored")
	void leadingSpriteIsIgnored()
	{
		List<StashUnits.Cell> cells = List.of(
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""),
			new StashUnits.Cell(StashUnits.TEXT_CELL, "Unit"),
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""));
		assertTrue(StashUnits.filledNames(cells, false).isEmpty());
	}

	@Test
	@DisplayName("the master tier renames the Warriors' Guild bank chart text")
	void masterTierRename()
	{
		List<StashUnits.Cell> cells = List.of(
			new StashUnits.Cell(StashUnits.TEXT_CELL, "Warriors' Guild bank"),
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""),
			new StashUnits.Cell(StashUnits.SPRITE_CELL, ""));

		assertEquals(List.of("Warriors' Guild bank (master)"),
			StashUnits.filledNames(cells, true));
		assertEquals(List.of("Warriors' Guild bank"),
			StashUnits.filledNames(cells, false));
	}
}
