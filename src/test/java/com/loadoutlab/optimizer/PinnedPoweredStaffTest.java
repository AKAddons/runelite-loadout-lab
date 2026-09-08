package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Discord (Apathy, 2026-09-06): "pin another item -> warped sceptre
 * doesn't pin it over my items ... I have to exclude every other staff".
 * A weapon pin forces the slot; the set is optimised around it. */
class PinnedPoweredStaffTest
{
	private static LoadoutData data;
	private static MonsterStats zulrah;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		zulrah = data.searchMonsters("Zulrah", 1).get(0);
	}

	@Test
	@DisplayName("a pinned warped sceptre holds the weapon slot at Zulrah with the spell on auto")
	void autoSpell() throws Exception
	{
		OptimizerService.StyleResult magic = magic(null);
		assertEquals(28585, magic.owned.get(0).getLoadout().getWeapon().getId());
	}

	@Test
	@DisplayName("a pinned warped sceptre still holds the slot when a spell is pinned too - the staff's built-in spell wins")
	void pinnedSpell() throws Exception
	{
		SpellStats fireBolt = data.getSpells().stream().filter(s -> s.getName().equals("Fire Bolt")).findFirst().orElseThrow();
		OptimizerService.StyleResult magic = magic(fireBolt);
		assertNotNull(magic);
		assertTrue(!magic.owned.isEmpty(), "the card must still have an answer");
		assertEquals(28585, magic.owned.get(0).getLoadout().getWeapon().getId());
	}

	private static OptimizerService.StyleResult magic(SpellStats pinnedSpell) throws Exception
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(11905, 1); // trident of the seas
		owned.put(28585, 1); // warped sceptre
		owned.put(1387, 1); // staff of fire
		owned.put(12002, 1); // occult necklace
		Map<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
		pins.put(GearSlot.WEAPON, 28585);
		Map<CombatStyle, Map<GearSlot, Integer>> byStyle = new EnumMap<>(CombatStyle.class);
		byStyle.put(CombatStyle.MAGIC, pins);
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, zulrah, PlayerLevels.MAXED, PlayerLevels.MAXED,
				PrayerUnlocks.ALL, RequirementProfile.MAXED, new OwnedItems(owned, true), 1, false, false, "",
				Collections.emptySet(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
				byStyle, pinnedSpell, results ->
				{
					out.set(results);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS));
			return out.get().get(CombatStyle.MAGIC);
		}
		finally
		{
			service.shutdown();
		}
	}
}
