package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, 2026-09-09 (Discord: gold rune gloves at the crab): "the quest
 * gating shouldn't stop barrows gloves from appearing in the bis sheet".
 * The ceiling honours the player's LEVELS (2026-08-27 ruling) but never
 * their quest log. */
class GameBestIgnoresQuestsTest
{
	@Test
	@DisplayName("game-best hands at 70/70/70 with no quests done are Barrows gloves, not a quest-free fallback")
	void barrowsGlovesInTheCeiling() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats goblin = data.searchMonsters("Goblin", 1).get(0);
		EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			levels.put(skill, 99);
		}
		levels.put(Skill.ATTACK, 70);
		levels.put(Skill.STRENGTH, 70);
		levels.put(Skill.DEFENCE, 70);
		RequirementProfile noQuests = new RequirementProfile(levels, Collections.emptySet());
		PlayerLevels player = new PlayerLevels(70, 70, 70, 99, 99, 99, 99);
		Map<Integer, Integer> owned = Map.of(4151, 1); // abyssal whip
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, goblin, player, player, PrayerUnlocks.ALL, noQuests,
				new OwnedItems(owned, true), 1, false, false, "", Collections.emptySet(), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0, results ->
				{
					out.set(results);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS));
			assertEquals(7462, out.get().get(CombatStyle.MELEE).overallBest.getLoadout().get(GearSlot.HANDS).getId(),
				"Barrows gloves: RFD is not a wall on the ceiling; Ferocious stays out on levels");
		}
		finally
		{
			service.shutdown();
		}
	}
}
