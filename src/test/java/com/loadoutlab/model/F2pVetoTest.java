package com.loadoutlab.model;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field ask 2026-08-27: D charge, thralls and the slayer-task chip have no
 * meaning under the F2P lock. The lock VETOES them in the compute args
 * without clearing their params - so undo history stays coherent and
 * unticking F2P brings the old setup straight back.
 *
 * <p>computeArgs() is the single funnel into the optimizer, so the veto is
 * asserted there: position 1 = onTask, position 7 = deathCharge.
 */
class F2pVetoTest
{
	private static PageState configured()
	{
		PageState state = new PageState();
		state.setParam("onTask", true);
		state.setParam("deathCharge", 2);
		state.setParam("thralls", true);
		return state;
	}

	@Test
	@DisplayName("the F2P lock silences task and Death Charge in the compute args")
	void lockVetoesComputeArgs()
	{
		PageState state = configured();

		Object[] unlocked = state.computeArgs();
		assertEquals(Boolean.TRUE, unlocked[1], "onTask should pass through unlocked");
		assertEquals(2, unlocked[7], "deathCharge should pass through unlocked");

		state.setParam("f2pOnly", true);
		Object[] locked = state.computeArgs();
		assertEquals(Boolean.FALSE, locked[1], "the lock did not veto onTask");
		assertEquals(0, locked[7], "the lock did not veto deathCharge");
	}

	@Test
	@DisplayName("the veto never clears the params - unticking restores the setup")
	void untickRestoresEverything()
	{
		PageState state = configured();
		state.setParam("f2pOnly", true);

		Map<String, Object> params = state.paramsNode();
		assertEquals(Boolean.TRUE, params.get("onTask"),
			"the lock CLEARED onTask instead of vetoing it");
		assertEquals(2, params.get("deathCharge"),
			"the lock CLEARED deathCharge instead of vetoing it");
		assertEquals(Boolean.TRUE, params.get("thralls"),
			"the lock CLEARED thralls instead of vetoing it");

		state.setParam("f2pOnly", false);
		Object[] restored = state.computeArgs();
		assertEquals(Boolean.TRUE, restored[1], "onTask did not come back");
		assertEquals(2, restored[7], "deathCharge did not come back");
	}
}
