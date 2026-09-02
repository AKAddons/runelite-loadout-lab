package com.loadoutlab.model;

import com.loadoutlab.engine.OptimizationRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One source of truth for "is a risk cap in force". Field report
 * 2026-08-22 (Artio): the chip lit up on riskBudgetGp > 0 while the
 * compute asked riskBudgetGp != DEFAULT - and DEFAULT is itself 75k,
 * the exact value the config seeds, so the UI promised a cap the
 * optimizer ignored and the report called it "none".
 */
class RiskCapTruthTest
{
	/** computeArgs order: [f2p, onTask, inWilderness, lock, tradeables, ...] */
	private static final int TRADEABLES = 4;

	private static PageState wilderness()
	{
		PageState state = new PageState();
		state.setParam("inWilderness", true);
		return state;
	}

	@Test
	@DisplayName("a cap equal to the engine's sentinel still binds")
	void theSentinelValueIsStillARealCap()
	{
		PageState state = wilderness();
		state.setParam("riskBudgetGp", OptimizationRequest.DEFAULT_RISK_BUDGET_GP);
		assertEquals(Boolean.TRUE, state.paramsNode().get("riskCapped"),
			"75k is a cap the player chose, not the absence of one");
		assertEquals(3, state.computeArgs()[TRADEABLES],
			"a bound cap limits carried tradeables to 3");
	}

	@Test
	@DisplayName("no cap by default, and clearing it releases the constraint")
	void clearedCapReleasesTheConstraint()
	{
		PageState fresh = wilderness();
		assertEquals(Boolean.FALSE, fresh.paramsNode().get("riskCapped"));
		assertEquals(-1, fresh.computeArgs()[TRADEABLES], "unconstrained by default");

		PageState state = wilderness();
		state.setParam("riskBudgetGp", 5_000_000);
		assertEquals(Boolean.TRUE, state.paramsNode().get("riskCapped"));
		state.setParam("riskBudgetGp", null);
		assertEquals(Boolean.FALSE, state.paramsNode().get("riskCapped"),
			"an emptied field means uncapped");
		assertEquals(-1, state.computeArgs()[TRADEABLES]);
	}

	@Test
	@DisplayName("a cap can be typed even when it equals the sentinel")
	void typingTheSentinelValueTakesEffect()
	{
		// Found by the adversarial pass 2026-08-22: set-param's no-op
		// guard compared only the gp number, and 75k reads identically
		// capped or not - so typing 75k was silently swallowed.
		PageState state = wilderness();
		CommandEngine engine = new CommandEngine(
			new com.loadoutlab.data.DataService().load(), state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			new CompanionLink());
		assertEquals(Boolean.FALSE, state.paramsNode().get("riskCapped"));

		assertTrue(engine.execute("set-param", Map.of(
			"param", "riskBudgetGp", "value", OptimizationRequest.DEFAULT_RISK_BUDGET_GP)));
		assertEquals(Boolean.TRUE, state.paramsNode().get("riskCapped"),
			"typing 75k must bind a real cap");
		assertEquals(3, state.computeArgs()[TRADEABLES]);
	}

	@Test
	@DisplayName("Protect Item keeps a fourth slot under a bound cap")
	void protectItemKeepsAFourthSlot()
	{
		PageState state = wilderness();
		state.setParam("riskBudgetGp", 1_000_000);
		state.setParam("protectItem", true);
		assertEquals(4, state.computeArgs()[TRADEABLES]);
	}

	@Test
	@DisplayName("a cap outside the wilderness never constrains")
	void tameContentIgnoresTheCap()
	{
		PageState state = new PageState();
		state.setParam("riskBudgetGp", 1_000_000);
		assertEquals(-1, state.computeArgs()[TRADEABLES]);
	}
}
