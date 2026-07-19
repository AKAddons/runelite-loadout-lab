package com.loadoutlab.profile;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Wall-clock benchmark for the full optimizer path (patterned on
 * HeadlessQuery): times OptimizerService.bestPerStyle end-to-end for a
 * representative matrix of monsters x optimize modes x risk flags, using
 * the FixtureBank account. Warm JVM: one full untimed warmup pass, then
 * PASSES timed passes; the median per cell is reported.
 *
 *   ./gradlew benchmark
 */
public final class QueryBenchmark
{
	private static final OptimizerService.OptimizeMode[] MODES = {
		OptimizerService.OptimizeMode.MAX_DPS, OptimizerService.OptimizeMode.BALANCED};
	private static final int[] RISKS = {-1, 3};
	private static final int PASSES = 3;

	private static String[] MONSTERS = {"general graardor", "zulrah", "callisto"};

	private QueryBenchmark()
	{
	}

	public static void main(String[] args) throws Exception
	{
		// Optional: --only <monster substring> narrows the matrix (profiling).
		for (int i = 0; i + 1 < args.length; i++)
		{
			if ("--only".equals(args[i]))
			{
				MONSTERS = new String[]{args[i + 1]};
			}
		}
		LoadoutData data = new DataService().load();
		PlayerProfile profile = FixtureBank.profile(data);
		OptimizerService service = new OptimizerService(data);
		int[] fingerprint = {1};
		try
		{
			// Warmup: one untimed full pass so the JIT sees every path.
			for (String monster : MONSTERS)
			{
				for (OptimizerService.OptimizeMode mode : MODES)
				{
					for (int risk : RISKS)
					{
						runOnce(service, data, profile, monster, mode, risk, fingerprint);
					}
				}
			}
			// The friction + pin cell: a revenant (salve territory) with the
			// salve owned AND pinned, risk-capped - the per-trial rebuild-veto
			// and pinned-risk-floor paths the standard matrix never touches.
			runPinnedSalve(service, data, profile, fingerprint);
			System.out.println("monster            mode      risk  median_ms  passes_ms");
			for (String monster : MONSTERS)
			{
				for (OptimizerService.OptimizeMode mode : MODES)
				{
					for (int risk : RISKS)
					{
						long[] times = new long[PASSES];
						for (int pass = 0; pass < PASSES; pass++)
						{
							times[pass] = runOnce(service, data, profile, monster, mode, risk, fingerprint);
						}
						long[] sorted = times.clone();
						Arrays.sort(sorted);
						System.out.println(String.format(Locale.ROOT,
							"%-18s %-9s %-5s %9d  %s",
							monster, mode, risk < 0 ? "off" : ("lr" + risk),
							sorted[PASSES / 2], Arrays.toString(times)));
					}
				}
			}
			long[] times = new long[PASSES];
			for (int pass = 0; pass < PASSES; pass++)
			{
				times[pass] = runPinnedSalve(service, data, profile, fingerprint);
			}
			long[] sorted = times.clone();
			Arrays.sort(sorted);
			System.out.println(String.format(Locale.ROOT,
				"%-18s %-9s %-5s %9d  %s",
				"rev demon+salvepin", OptimizerService.OptimizeMode.MAX_DPS, "lr3",
				sorted[PASSES / 2], Arrays.toString(times)));
		}
		finally
		{
			service.shutdown();
		}
	}

	/**
	 * The risk-constrained + pinned scenario: revenant demon at lr3 with a
	 * salve amulet(ei) added to the fixture bank and pinned in every style.
	 * The salve is friction gear, so every trial runs the rebuild veto, and
	 * the pin keeps the pinned-risk-floor path live.
	 */
	private static long runPinnedSalve(OptimizerService service, LoadoutData data,
		PlayerProfile profile, int[] fingerprint) throws Exception
	{
		com.loadoutlab.data.GearItem salve = null;
		for (com.loadoutlab.data.GearItem item : data.getGearItems())
		{
			if ("salve amulet(ei)".equalsIgnoreCase(item.getName())
				&& item.isStandardGear() && !data.isVariant(item.getId()))
			{
				salve = item;
				break;
			}
		}
		if (salve == null)
		{
			throw new IllegalStateException("salve amulet(ei) not in dataset");
		}
		java.util.Map<Integer, Integer> owned =
			new java.util.LinkedHashMap<>(profile.ownedItems().getQuantities());
		owned.put(salve.getId(), 1);
		java.util.Map<com.loadoutlab.engine.CombatStyle,
			java.util.Map<com.loadoutlab.data.GearSlot, Integer>> pins = new java.util.EnumMap<>(
			com.loadoutlab.engine.CombatStyle.class);
		for (com.loadoutlab.engine.CombatStyle style : com.loadoutlab.engine.CombatStyle.concreteValues())
		{
			pins.put(style, java.util.Map.of(com.loadoutlab.data.GearSlot.NECK, salve.getId()));
		}
		MonsterStats monster = data.searchMonsters("revenant demon", 1).get(0);
		CountDownLatch done = new CountDownLatch(1);
		long start = System.nanoTime();
		com.loadoutlab.optimizer.ServiceCalls.bestPerStyle(service, monster, profile.realLevels, profile.boostedLevels,
			profile.prayerUnlocks, profile.requirements,
			new com.loadoutlab.engine.OwnedItems(owned, true),
			fingerprint[0]++, false, false, "",
			Collections.emptySet(), 3,
			com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false,
			Collections.emptySet(), 0, OptimizerService.OptimizeMode.MAX_DPS,
			pins, null,
			(Map<CombatStyle, OptimizerService.StyleResult> results) -> done.countDown());
		if (!done.await(600, TimeUnit.SECONDS))
		{
			throw new IllegalStateException("benchmark cell timed out: pinned salve");
		}
		return (System.nanoTime() - start) / 1_000_000L;
	}

	private static long runOnce(OptimizerService service, LoadoutData data,
		PlayerProfile profile, String monsterName, OptimizerService.OptimizeMode mode,
		int lowRisk, int[] fingerprint) throws Exception
	{
		MonsterStats monster = data.searchMonsters(monsterName, 1).get(0);
		CountDownLatch done = new CountDownLatch(1);
		long start = System.nanoTime();
		// A fresh fingerprint per run defeats the result cache, so every
		// run measures a full computation.
		com.loadoutlab.optimizer.ServiceCalls.bestPerStyle(service, monster, profile.realLevels, profile.boostedLevels,
			profile.prayerUnlocks, profile.requirements, profile.ownedItems(),
			fingerprint[0]++, false, false, "",
			Collections.emptySet(), lowRisk,
			com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false,
			Collections.emptySet(), 0, mode,
			(Map<CombatStyle, OptimizerService.StyleResult> results) -> done.countDown());
		if (!done.await(600, TimeUnit.SECONDS))
		{
			throw new IllegalStateException("benchmark cell timed out: " + monsterName);
		}
		return (System.nanoTime() - start) / 1_000_000L;
	}
}
