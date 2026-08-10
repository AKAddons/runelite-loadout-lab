package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.optimizer.OptimizerService;
import com.loadoutlab.optimizer.ServiceCalls;
import com.loadoutlab.profile.FixtureBank;
import com.loadoutlab.profile.PlayerProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Model-snapshot golden (ADR-0008): the serialized render-model for a
 * scenario matrix, canonical-JSON per line. Byte-identical output is
 * the contract the Companion renders against - this locks the seam the
 * way golden/rosterGolden lock the engine, and the captured lines
 * double as the Companion repo's renderer fixtures.
 *
 *   ./gradlew -q modelGolden > model-golden.txt
 */
public final class ModelGolden
{
	/** Single mobs chosen for card-shape coverage: tri-style + spec
	 * (Graardor), recoil/venom + magic-led (Zulrah), antifire +
	 * required-gear (Vorkath), incoming-override boss (Muspah). */
	private static final String[] MOBS = {
		"general graardor", "zulrah", "vorkath", "phantom muspah",
	};

	private static final String ROSTER_GROUP = "Dagannoth Kings";
	private static final int ROSTER_BENCH = 3;

	private ModelGolden()
	{
	}

	public static void main(String[] args) throws Exception
	{
		LoadoutData data = new DataService().load();
		PlayerProfile profile = FixtureBank.profile(data);
		for (String name : MOBS)
		{
			MonsterStats mob = data.searchMonsters(name, 1).get(0);
			System.out.println("##### " + mob.label());
			System.out.println(Json.write(RenderModel.page(List.of(single(data, profile, mob)))));
		}
		MonsterGroups.MonsterGroup group = null;
		for (MonsterGroups.MonsterGroup candidate : MonsterGroups.load(data))
		{
			if (candidate.getName().equalsIgnoreCase(ROSTER_GROUP))
			{
				group = candidate;
			}
		}
		if (group == null)
		{
			throw new IllegalStateException("group gone: " + ROSTER_GROUP);
		}
		System.out.println("##### " + group.label() + " | bench=" + ROSTER_BENCH);
		System.out.println(Json.write(RenderModel.page(
			List.of(roster(data, profile, group.getMobs(), ROSTER_BENCH)))));
	}

	private static Map<String, Object> single(LoadoutData data, PlayerProfile profile,
		MonsterStats mob) throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, mob,
				profile.realLevels, profile.boostedLevels, profile.prayerUnlocks,
				profile.requirements, profile.ownedItems(), profile.owned.hashCode(),
				false, false, "", new EnumMap<>(CombatStyle.class), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false,
				Collections.emptySet(), 0,
				Collections.emptyMap(), null, 0, Collections.emptySet(),
				r ->
				{
					out.set(r);
					done.countDown();
				});
			if (!done.await(300, TimeUnit.SECONDS))
			{
				throw new IllegalStateException("query timed out: " + mob.label());
			}
			return RenderModel.entry(List.of(mob), List.of(out.get()));
		}
		finally
		{
			service.shutdown();
		}
	}

	private static Map<String, Object> roster(LoadoutData data, PlayerProfile profile,
		List<MonsterStats> mobs, int bench) throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
			ServiceCalls.bestPerStyleAcross(service, mobs,
				profile.realLevels, profile.boostedLevels, profile.prayerUnlocks,
				profile.requirements, profile.ownedItems(), profile.owned.hashCode(),
				false, false, "", Collections.emptyMap(), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false,
				Collections.emptySet(), 0, bench,
				Collections.emptyMap(), null, Collections.emptySet(),
				roster ->
				{
					out.set(roster);
					done.countDown();
				});
			if (!done.await(300, TimeUnit.SECONDS))
			{
				throw new IllegalStateException("roster query timed out");
			}
			return RenderModel.entry(out.get().mobs, out.get().perMob);
		}
		finally
		{
			service.shutdown();
		}
	}
}
