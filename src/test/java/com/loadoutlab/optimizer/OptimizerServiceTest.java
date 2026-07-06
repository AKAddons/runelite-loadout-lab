package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.RequirementProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

public class OptimizerServiceTest
{
	@Test
	public void ownedSpecWeaponSurfacesOnTheStyleResult() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip - the sustained-DPS weapon
		owned.put(1215, 1);   // dragon dagger - the spec weapon
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(monster, PlayerLevels.MAXED, RequirementProfile.MAXED,
				new OwnedItems(owned, true), 1, false, results ->
				{
					out.set(results);
					done.countDown();
				});
			Assert.assertTrue(done.await(60, TimeUnit.SECONDS));

			OptimizerService.StyleResult melee = out.get().get(CombatStyle.MELEE);
			Assert.assertNotNull(melee);
			Assert.assertFalse(melee.owned.isEmpty());
			// The whip wins sustained DPS; the dagger is still surfaced as
			// the spec weapon with a positive expected special-attack hit.
			Assert.assertNotNull(melee.spec);
			Assert.assertEquals("Dragon dagger", melee.spec.getDisplayName());
			Assert.assertEquals(1215, melee.specWeapon.getId());
			Assert.assertTrue(melee.specExpectedDamage > 0);
			// The game-best section carries its own spec - the strongest
			// special attack that exists, regardless of ownership.
			Assert.assertNotNull(melee.gameSpec);
			Assert.assertNotNull(melee.gameSpecWeapon);
			Assert.assertTrue(melee.gameSpecExpectedDamage >= melee.specExpectedDamage);
		}
		finally
		{
			service.shutdown();
		}
	}
}
