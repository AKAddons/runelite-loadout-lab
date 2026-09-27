package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.WildernessMonsters;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, field 2026-09-27 (Frost Nagua): "i'm seeing my occult necklace
 * count as bis but when i sim a amulet of fire the dps goes up". The gold
 * border compared slots with the game-best set, which casts from a Shadow;
 * on a Fire Bolt card the fire amulet beats the occult. The border now
 * stands only where nothing in the game beats the item IN YOUR SET. */
class BisBorderTest
{
	private static final int OCCULT = 12002;
	private static final int PURGING_STAFF = 29594;
	private static final int SHADOW = 27275;

	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static OptimizerService.StyleResult magicAtFrostNagua(Map<Integer, Integer> owned) throws Exception
	{
		MonsterStats mob = data.searchMonsters("frost nagua", 1).get(0);
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(mob, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 1, false, false, "",
				Collections.emptyMap(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, 0, true,
				Collections.emptyMap(), Collections.emptyMap(), WildernessMonsters.isExclusive(mob),
				Collections.emptySet(), 0, 1, Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result ->
				{
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS), "compute finished");
			return out.get().get(CombatStyle.MAGIC);
		}
		finally
		{
			service.shutdown();
		}
	}

	@SuppressWarnings("unchecked")
	private static boolean neckBordered(OptimizerService.StyleResult result, Map<Integer, Integer> owned)
	{
		Map<String, Object> card = RenderModel.card(result, false, -1, owned::containsKey,
			Collections.emptySet());
		Map<String, Object> gear = (Map<String, Object>) card.get("gear");
		Map<String, Object> neck = (Map<String, Object>) gear.get("neck");
		return Boolean.TRUE.equals(neck.get("bisMatch"));
	}

	@Test
	@DisplayName("an occult on a Fire Bolt card loses the border: a fire amulet beats it in that set")
	void boltCard() throws Exception
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(PURGING_STAFF, 1);
		owned.put(OCCULT, 1);
		OptimizerService.StyleResult result = magicAtFrostNagua(owned);
		assertEquals(OCCULT, result.owned.get(0).getLoadout().get(GearSlot.NECK).getId());
		assertEquals(OCCULT, result.overallBest.getLoadout().get(GearSlot.NECK).getId(),
			"game best (a Shadow) wears the occult");
		assertTrue(result.beaten.contains(GearSlot.NECK), "the neck is beaten in the Fire Bolt set");
		assertFalse(neckBordered(result, owned), "no gold border on the occult");
	}

	@Test
	@DisplayName("the same occult under a Shadow keeps the border")
	void shadowCard() throws Exception
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(SHADOW, 1);
		owned.put(OCCULT, 1);
		OptimizerService.StyleResult result = magicAtFrostNagua(owned);
		assertEquals(SHADOW, result.owned.get(0).getLoadout().getWeapon().getId());
		assertFalse(result.beaten.contains(GearSlot.NECK));
		assertTrue(neckBordered(result, owned), "nothing beats the occult here");
	}
}
