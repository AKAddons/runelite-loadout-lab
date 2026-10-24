package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.optimizer.OptimizerService.OptimizeMode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The defensive modes are a developer-mode prototype (ISS-01M5G5CZC9SK46499FACRSJ8ZT). */
class OptimizeModeParamTest
{
	private static CommandEngine engine(PageState state)
	{
		return new CommandEngine(new DataService().load(), state,
			(mob, p, onDone) ->
			{
			},
			new CompanionLink());
	}

	@Test
	@DisplayName("the mode param defaults to Max DPS and only takes the three modes")
	void paramValues()
	{
		PageState state = new PageState();
		assertEquals("MAX_DPS", state.paramsNode().get("optimizeMode"));
		assertTrue(state.setParam("optimizeMode", "TANKY"));
		assertEquals("TANKY", state.paramsNode().get("optimizeMode"));
		state.setParam("optimizeMode", "NONSENSE");
		assertEquals("MAX_DPS", state.paramsNode().get("optimizeMode"));
	}

	@Test
	@DisplayName("every player's optimizer gets the chosen mode - released 2026-10-22, no developer-mode gate")
	void releasedToEveryone()
	{
		// Andrew 2026-10-22: "Release to everyone" (ISS-01M5JBYYWHQNTM0CGNYWQBNYX6).
		PageState state = new PageState();
		CommandEngine engine = engine(state);
		engine.execute("set-param", Map.of("param", "optimizeMode", "value", "BALANCED"));
		assertEquals(OptimizeMode.BALANCED, engine.optimizeMode());
		assertEquals("BALANCED", state.computeParams().optimizeMode, "the compute carries it too");
		state.setParam("optimizeMode", "TANKY");
		assertEquals("TANKY", state.computeParams().optimizeMode);
	}
}
