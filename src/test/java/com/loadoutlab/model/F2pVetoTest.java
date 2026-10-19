package com.loadoutlab.model;

import com.loadoutlab.engine.ComputeParams;
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

		ComputeParams unlocked = state.computeParams();
		assertTrue(unlocked.onSlayerTask, "onTask should pass through unlocked");
		assertEquals(2, unlocked.deathCharge, "deathCharge should pass through unlocked");

		state.setParam("f2pOnly", true);
		ComputeParams locked = state.computeParams();
		assertFalse(locked.onSlayerTask, "the lock did not veto onTask");
		assertEquals(0, locked.deathCharge, "the lock did not veto deathCharge");
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
		ComputeParams restored = state.computeParams();
		assertTrue(restored.onSlayerTask, "onTask did not come back");
		assertEquals(2, restored.deathCharge, "deathCharge did not come back");
	}
}
