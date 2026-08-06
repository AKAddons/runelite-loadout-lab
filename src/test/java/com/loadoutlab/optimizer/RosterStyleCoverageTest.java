package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

/**
 * Field report 2026-08-05: the Abyssal Sire roster (Respiratory system +
 * Phase 1 + Phase 3) rendered "-" for the ranged and magic style buttons
 * while every member answers all three styles when searched alone. A dash
 * means the style's StyleResult is absent for the lensed mob, which the
 * roster path only produces when a whole style came back empty - so this
 * pins the invariant: any style a mob can answer alone, it must also
 * answer inside a roster.
 */
public class RosterStyleCoverageTest
{
	/** A style-complete midgame kit: one real weapon per style plus
	 * enough armour that every optimize has genuine candidates. */
	private static Map<Integer, Integer> styleCompleteKit()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12926, 1);  // toxic blowpipe (charged)
		owned.put(11907, 1);  // trident of the seas
		owned.put(11832, 1);  // bandos chestplate
		owned.put(11834, 1);  // bandos tassets
		owned.put(10828, 1);  // helm of neitiznot
		owned.put(6585, 1);   // amulet of fury
		owned.put(11840, 1);  // dragon boots
		owned.put(2503, 1);   // black d'hide body
		owned.put(2497, 1);   // black d'hide chaps
		owned.put(4091, 1);   // mystic robe top
		owned.put(4093, 1);   // mystic robe bottom
		return owned;
	}

	@Test
	public void everyStyleASingleAnswersSurvivesTheRoster() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats vents = data.searchMonsters("respiratory system", 1).get(0);
		MonsterStats phase1 = sire(data, "Phase 1");
		MonsterStats phase3 = sire(data, "Phase 3 (stage 2)");
		List<MonsterStats> mobs = new ArrayList<>(List.of(vents, phase1, phase3));

		OptimizerService service = new OptimizerService(data);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
		// Mirrors the client call RosterOptimizerTest uses, at the
		// screenshot's Inventory: 3.
		ServiceCalls.bestPerStyleAcross(service,
			mobs, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED,
			new OwnedItems(styleCompleteKit(), true), 1, false, false, "",
			Collections.emptyMap(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0, 3,
			Collections.emptyMap(), Collections.emptyMap(), null, Collections.emptySet(),
			roster ->
			{
				out.set(roster);
				done.countDown();
			});
		Assert.assertTrue("roster compute timed out", done.await(120, TimeUnit.SECONDS));
		OptimizerService.RosterResult roster = out.get();
		Assert.assertNotNull("roster returned null (superseded?)", roster);

		for (int j = 0; j < mobs.size(); j++)
		{
			Map<CombatStyle, OptimizerService.StyleResult> byStyle = roster.perMob.get(j);
			for (CombatStyle style : CombatStyle.concreteValues())
			{
				OptimizerService.StyleResult sr = byStyle.get(style);
				Assert.assertNotNull(
					mobs.get(j).label() + " lost its " + style + " answer inside the roster"
						+ " (fine alone, dash in the roster - the field bug)",
					sr);
				if (style != CombatStyle.MELEE)
				{
					Assert.assertFalse(
						mobs.get(j).label() + " " + style + " owned list is empty in the roster",
						sr.owned == null || sr.owned.isEmpty());
				}
			}
		}
	}

	private static MonsterStats sire(LoadoutData data, String version)
	{
		return data.searchMonsters("abyssal sire", 6).stream()
			.filter(m -> version.equals(m.getVersion()))
			.findFirst().orElseThrow(() -> new AssertionError("missing Sire " + version));
	}
}
