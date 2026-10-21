package com.loadoutlab.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The card's damage-taken line says how much of the monster it covers. */
class DtpsLabelTest
{
	private static Map<String, Object> incoming(String coverage, double dps, List<String> unmodeled)
	{
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("coverage", coverage);
		node.put("dps", dps);
		node.put("unprayedDps", dps * 2);
		node.put("protectPrayer", "Protect from Melee");
		node.put("unmodeled", unmodeled);
		return node;
	}

	@Test
	@DisplayName("full and curated coverage read as a plain estimate")
	void fullIsPlain()
	{
		assertEquals("~3.0", ResultCards.dtpsLine(incoming("full", 3.0, List.of()))[0]);
		assertEquals("~3.0", ResultCards.dtpsLine(incoming("curated", 3.0, List.of()))[0]);
	}

	@Test
	@DisplayName("partial coverage is starred and the tooltip names what is not counted")
	void partialIsFlagged()
	{
		String[] line = ResultCards.dtpsLine(incoming("partial", 3.0, List.of("Dragonfire")));
		assertEquals("~3.0*", line[0]);
		assertTrue(line[1].contains("Not counted: Dragonfire"), line[1]);
	}

	@Test
	@DisplayName("no coverage reads as unknown, never as safe")
	void noneIsUnknown()
	{
		assertEquals("DTPS: ?", ResultCards.dtpsLine(incoming("none", 0, List.of("All")))[0]);
	}
}
