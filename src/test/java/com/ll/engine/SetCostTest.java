package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The "cost to own" line prices the gap between your bank and the set on
 * screen. It leans entirely on the search accumulating budgetCost per slot
 * - the item's price when you do not own it, zero when you do - so these
 * pin that behaviour down rather than the formatting.
 */
class SetCostTest {
	private static Tables data;
	private static Monster target;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		target = data.searchMonsters("abyssal demon", 1).get(0);
	}

	private static Dps best(Owned owned, CandidateMode mode) {
		Req request = TestRequests.of(target,
			Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			mode, true, false, owned, 1);
		List<Dps> out = new LoadoutOptimizer().optimize(data, request);
		assertFalse(out.isEmpty(), "optimizer returned nothing");
		return out.get(0);
	}

	@Test
	@DisplayName("a game-best set owned by nobody prices its whole self")
	void gameBestPricesTheGap() {
		Dps r = best(Owned.EMPTY, CandidateMode.ALL_STANDARD);
		assertTrue(r.getPurchaseCost() > 0,
			"the BiS set must carry the gp cost of the pieces you lack - "
				+ "the 'cost to own' line renders from exactly this");
	}

	@Test
	@DisplayName("owning the pieces drives the cost to zero")
	void owningThemCostsNothing() {
		// Own precisely what the game-best search picked, then re-run: every
		// slot now returns budgetCost 0, so the line is suppressed.
		Dps bis = best(Owned.EMPTY, CandidateMode.ALL_STANDARD);
		Map<Integer, Integer> owned = new HashMap<>();
		bis.getLoadout().getGear().values().stream()
			.filter(java.util.Objects::nonNull)
			.forEach(g -> owned.put(g.getId(), 1));

		Dps again = best(new Owned(owned, true), CandidateMode.ALL_STANDARD);
		assertEquals(0, again.getPurchaseCost(),
			"a set you already own must price at zero, so the line stays off");
	}
}
