package com.loadoutlab.optimizer;

import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Risk-capped wilderness searches never assume a heart (field spec
 * 2026-07-18): both hearts are tradeable and worth far more than any
 * sane risk cap - the magic assumption falls back to the potion.
 */
class BoostSelectorTest
{
	@Test
	@DisplayName("a risk-capped wildy search downgrades the heart to a magic potion")
	void noHeartsUnderARiskCap()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(27641, 1); // saturated heart
		OwnedItems bag = new OwnedItems(owned, true);
		assertEquals(BoostProfile.SATURATED_HEART,
			BoostSelector.bestFor(CombatStyle.MAGIC, bag, false, false));
		assertEquals(BoostProfile.MAGIC,
			BoostSelector.bestFor(CombatStyle.MAGIC, bag, false, true));
	}
}
