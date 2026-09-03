package com.loadoutlab.render;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Andrew, 2026-09-02: "too many different output numbers" on a sea
 * card - the row said 10.34, the trip total 17.98, the tab 18.38. One
 * ledger whose parts sum to the tab, with the row's number named. */
class TripLedgerTest
{
	private static Map<String, Object> cannon(String tier, String who, double dps, String blocked)
	{
		Map<String, Object> c = new java.util.LinkedHashMap<>();
		c.put("tier", tier);
		c.put("firedBy", who);
		c.put("dps", dps);
		if (blocked != null)
		{
			c.put("blocked", blocked);
		}
		return c;
	}

	private static Map<String, Object> ship(String station, Map<String, Object>... cannons)
	{
		return Map.of("station", station, "cannons", List.of(cannons));
	}

	private static final Map<String, Object> SIDE = Map.of("dps", 9.94, "spec", Map.of("dpsAdded", 0.40));

	@Test
	@DisplayName("set, spec and cannons sum to the tab number; the gear line is the row's number")
	void sumsToTheTab()
	{
		String html = ResultCards.tripLedger(
			ship("helm", cannon("rune", "crew", 4.02, null), cannon("rune", "crew", 4.02, null)), SIDE);
		assertTrue(html.contains("18.38"), html);
		assertTrue(html.contains("10.34"), html);
		assertFalse(html.contains("17.98"), html);
		assertTrue(html.contains("rune, crew"), html);
	}

	@Test
	@DisplayName("a manned trip counts cannons only and says why the gear is not attacking")
	void manned()
	{
		String html = ResultCards.tripLedger(
			ship("cannon", cannon("rune", "player", 5.10, null), cannon("rune", "crew", 4.02, null)), SIDE);
		assertTrue(html.contains("manning"), html);
		assertTrue(html.contains("9.12"), html);
		assertFalse(html.contains("9.94"), html);
	}

	@Test
	@DisplayName("a blocked cannon shows its reason and adds nothing")
	void blocked()
	{
		String html = ResultCards.tripLedger(
			ship("helm", cannon("rune", "crew", 0, "Crew needs Privateering 40")), SIDE);
		assertTrue(html.contains("Crew needs Privateering 40"), html);
		assertTrue(html.contains("10.34"), html);
	}

	@Test
	@DisplayName("the collapsed ledger is the total line alone, and the total says total")
	void collapsedIsTheTotalOnly()
	{
		Map<String, Object> ship = ship("helm", cannon("rune", "crew", 4.02, null), cannon("rune", "crew", 4.02, null));
		String full = ResultCards.tripLedger(ship, SIDE, true);
		String collapsed = ResultCards.tripLedger(ship, SIDE, false);
		assertTrue(full.contains("= total") && !full.contains("= trip"), full);
		assertTrue(collapsed.contains("18.38"), collapsed);
		assertFalse(collapsed.contains("cannon 1"), collapsed);
		assertFalse(collapsed.contains("9.94"), collapsed);
		assertEquals(1, collapsed.split("<tr>").length - 1, "one row");
	}
}
