package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import net.runelite.api.Skill;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-08-27: "it's saying my bis on obor is a rune scimitar but
 * my attack is only 10 so i can't use that."
 *
 * <p>The ceiling gated on RequirementProfile.MAXED, so it named gear the player
 * could not wear and quoted a DPS that can never occur - reaching the 40 Attack
 * a rune scimitar needs changes the number it was quoting. It now uses the same
 * requirement profile as the Yours side, leaving OWNERSHIP as the only
 * difference between the two, which is the gap the comparison exists to show.
 *
 * <p>The goldens cannot catch this: FixtureBank is a maxed account, where the
 * two requirement profiles agree.
 */
class LowLevelCeilingTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	/** A fresh account: 10 Attack, nothing trained, every quest unstarted. */
	private static RequirementProfile freshAccount()
	{
		Map<Skill, Integer> levels = new HashMap<>();
		for (Skill s : Skill.values())
		{
			levels.put(s, 1);
		}
		levels.put(Skill.ATTACK, 10);
		levels.put(Skill.HITPOINTS, 10);
		return new RequirementProfile(levels, Collections.emptySet());
	}

	@Test
	@DisplayName("the BiS ceiling never names gear the player cannot equip")
	void ceilingRespectsThePlayersRequirements() throws Exception
	{
		MonsterStats obor = data.searchMonsters("obor", 1).get(0);
		RequirementProfile fresh = freshAccount();
		PlayerLevels levels = new PlayerLevels(10, 1, 1, 10, 1, 1, 1);

		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out =
				new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, obor,
				levels, levels, PrayerUnlocks.ALL,
				fresh, new OwnedItems(new HashMap<>(), true), 0,
				false, false, "", new EnumMap<>(CombatStyle.class), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false,
				Collections.emptySet(), 0,
				Collections.emptyMap(), null, 0, Collections.emptySet(),
				r ->
				{
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS), "the Obor query timed out");

			OptimizerService.StyleResult melee = out.get().get(CombatStyle.MELEE);
			assertNotNull(melee, "no melee result for Obor");
			assertNotNull(melee.overallBest, "no game-best set for Obor");

			for (GearSlot slot : GearSlot.values())
			{
				GearItem worn = melee.overallBest.getLoadout().get(slot);
				if (worn == null)
				{
					continue;
				}
				assertTrue(fresh.canEquip(worn.getRequirements()),
					"the ceiling put on " + worn.getName()
						+ ", which a 10 Attack account cannot equip");
			}
		}
		finally
		{
			service.shutdown();
		}
	}
}
