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

	@Test
	@DisplayName("the BiS ceiling assumes divine potions for melee and ranged")
	void ceilingPrefersDivine()
	{
		assertEquals(BoostProfile.DIVINE_SUPER_COMBAT,
			BoostSelector.ceilingFor(CombatStyle.MELEE, false));
		assertEquals(BoostProfile.DIVINE_RANGING,
			BoostSelector.ceilingFor(CombatStyle.RANGED, false));
		assertEquals(BoostProfile.SATURATED_HEART,
			BoostSelector.ceilingFor(CombatStyle.MAGIC, false),
			"the heart out-boosts the flat divine magic potion");
	}

	@Test
	@DisplayName("owned divine potions are preferred over the base assumption")
	void ownedDivinePreferred()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(23691, 1); // divine super combat (2) - any dose counts
		owned.put(23733, 1); // divine ranging (4)
		owned.put(23754, 1); // divine magic (1)
		OwnedItems bag = new OwnedItems(owned, true);
		assertEquals(BoostProfile.DIVINE_SUPER_COMBAT,
			BoostSelector.bestFor(CombatStyle.MELEE, bag, false, false));
		assertEquals(BoostProfile.DIVINE_RANGING,
			BoostSelector.bestFor(CombatStyle.RANGED, bag, false, false));
		assertEquals(BoostProfile.DIVINE_MAGIC,
			BoostSelector.bestFor(CombatStyle.MAGIC, bag, false, false));
	}

	@Test
	@DisplayName("without divine potions the base tradeables stay assumed")
	void baselineWithoutDivine()
	{
		OwnedItems empty = new OwnedItems(new HashMap<>(), true);
		assertEquals(BoostProfile.SUPER_COMBAT,
			BoostSelector.bestFor(CombatStyle.MELEE, empty, false, false));
		assertEquals(BoostProfile.RANGING,
			BoostSelector.bestFor(CombatStyle.RANGED, empty, false, false));
		assertEquals(BoostProfile.MAGIC,
			BoostSelector.bestFor(CombatStyle.MAGIC, empty, false, false));
	}

	@Test
	@DisplayName("an owned heart still beats an owned divine magic potion")
	void heartBeatsDivineMagic()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(27641, 1); // saturated heart
		owned.put(23745, 1); // divine magic (4)
		OwnedItems bag = new OwnedItems(owned, true);
		assertEquals(BoostProfile.SATURATED_HEART,
			BoostSelector.bestFor(CombatStyle.MAGIC, bag, false, false));
		assertEquals(BoostProfile.DIVINE_MAGIC,
			BoostSelector.bestFor(CombatStyle.MAGIC, bag, false, true),
			"under a risk cap the heart falls to the divine potion when owned");
	}
}
