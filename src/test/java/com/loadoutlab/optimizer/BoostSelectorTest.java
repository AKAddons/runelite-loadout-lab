package com.loadoutlab.optimizer;

import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class BoostSelectorTest
{
	@Test
	public void picksTheBestOwnedBoostPerStyle()
	{
		OwnedItems none = OwnedItems.EMPTY;
		Assert.assertEquals(BoostProfile.NONE, BoostSelector.bestFor(CombatStyle.MELEE, none));

		OwnedItems scAndHearts = new OwnedItems(Map.of(
			12697, 3,   // super combat (3)
			22464, 1,   // bastion (3)
			20724, 1,   // imbued heart
			27641, 1),  // saturated heart - outranks the imbued
			true);
		Assert.assertEquals(BoostProfile.SUPER_COMBAT, BoostSelector.bestFor(CombatStyle.MELEE, scAndHearts));
		Assert.assertEquals(BoostProfile.RANGING, BoostSelector.bestFor(CombatStyle.RANGED, scAndHearts));
		Assert.assertEquals(BoostProfile.SATURATED_HEART, BoostSelector.bestFor(CombatStyle.MAGIC, scAndHearts));

		// Boost application: 99 melee stats -> 118 with super combat.
		PlayerLevels boosted = PlayerLevels.MAXED.boosted(BoostProfile.SUPER_COMBAT, PlayerLevels.MAXED);
		Assert.assertEquals(118, boosted.getStrength());
		// Never below live: element-wise max keeps a higher live boost.
		PlayerLevels live = new PlayerLevels(120, 120, 99, 99, 99, 99, 99);
		Assert.assertEquals(120, boosted.max(live).getStrength());
	}
}
