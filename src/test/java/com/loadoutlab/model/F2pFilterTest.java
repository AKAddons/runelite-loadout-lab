package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-08-25: "the f2p lock seems to be working but i used to be
 * able to turn that off and see what the p2p result was on my account."
 *
 * <p>The merge-back dropped the F2P chip, so the filter could be switched on by
 * the client but never off by the player. The fix splits the two facts apart:
 * {@code f2pWorld} is where the client is (chip visibility), {@code f2pOnly} is
 * what the player asked for (the filter itself). Collapsing them back into one
 * flag is what made the control hide the moment it was used.
 */
class F2pFilterTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static CommandEngine engine(PageState state)
	{
		return new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			new CompanionLink());
	}

	private static boolean flag(PageState state, String param)
	{
		return Boolean.TRUE.equals(state.paramsNode().get(param));
	}

	@Test
	@DisplayName("unticking the F2P filter leaves the chip's own visibility flag alone")
	void untickingKeepsTheChipOnScreen()
	{
		PageState state = new PageState();
		CommandEngine engine = engine(state);

		// Logging in to a non-members world: both flags go up.
		engine.execute("set-param", Map.of("param", "f2pWorld", "value", true));
		engine.execute("set-param", Map.of("param", "f2pOnly", "value", true));
		assertTrue(flag(state, "f2pWorld"));
		assertTrue(flag(state, "f2pOnly"));

		// The player unticks it to preview members gear. The chip renders from
		// f2pWorld, so it must survive - otherwise there is no way back.
		engine.execute("set-param", Map.of("param", "f2pOnly", "value", false));
		assertFalse(flag(state, "f2pOnly"), "the filter did not come off");
		assertTrue(flag(state, "f2pWorld"),
			"unticking the filter hid the chip that unticks it");

		// ...and it can be turned back on.
		engine.execute("set-param", Map.of("param", "f2pOnly", "value", true));
		assertTrue(flag(state, "f2pOnly"), "the filter could not be restored");
	}

	@Test
	@DisplayName("a members world reports neither flag")
	void membersWorldHidesTheChip()
	{
		PageState state = new PageState();
		assertFalse(flag(state, "f2pWorld"), "the chip would show on a members world");
		assertFalse(flag(state, "f2pOnly"));
	}
}
