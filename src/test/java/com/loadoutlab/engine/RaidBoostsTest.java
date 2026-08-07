package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Raids supply their own boosts (field spec 2026-07-18): CoX brews
 * overload (+) inside, ToA hands out smelling salts - fights inside
 * never assume the bank's potions.
 */
class RaidBoostsTest
{
	@Test
	@DisplayName("CoX mobs assume overload (+), ToA mobs smelling salts, outside null")
	void suppliedBoosts()
	{
		LoadoutData data = new DataService().load();
		assertEquals(BoostProfile.OVERLOAD_PLUS,
			RaidBoosts.suppliedBoost(data.searchMonsters("tekton", 1).get(0)));
		assertEquals(BoostProfile.OVERLOAD_PLUS,
			RaidBoosts.suppliedBoost(data.searchMonsters("great olm", 1).get(0)));
		assertEquals(BoostProfile.SMELLING_SALTS,
			RaidBoosts.suppliedBoost(data.searchMonsters("zebak", 1).get(0)));
		assertNull(RaidBoosts.suppliedBoost(data.searchMonsters("vorkath", 1).get(0)));
	}
}
