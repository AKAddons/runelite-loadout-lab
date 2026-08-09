package com.loadoutlab.optimizer;

import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Test/headless convenience bridges for {@link OptimizerService}. These
 * were back-compat overloads in main source; the Plugin Hub token cap
 * moved them here (tests are not counted by the hub bot). Each fills the
 * newest signature's defaults exactly as the old overloads did.
 */
public final class ServiceCalls
{
	private ServiceCalls()
	{
	}

	/** No pinned items. */
	public static void bestPerStyle(
		OptimizerService service,
		MonsterStats monster,
		PlayerLevels realLevels,
		PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Set<Integer> excludedItems,
		int maxTradeables,
		int riskBudgetGp,
		boolean antifirePotion,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		Consumer<Map<CombatStyle, OptimizerService.StyleResult>> callback)
	{
		bestPerStyle(service, monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock,
			excludedItems, maxTradeables, riskBudgetGp, antifirePotion, dreamItems,
			upgradeBudgetGp,
			Collections.<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>>emptyMap(),
			null, callback);
	}

	/** One exclusion set, all styles; Wilderness only when the monster
	 * exists nowhere else (matches the request default). */
	public static void bestPerStyle(
		OptimizerService service,
		MonsterStats monster,
		PlayerLevels realLevels,
		PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Set<Integer> excludedItems,
		int maxTradeables,
		int riskBudgetGp,
		boolean antifirePotion,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell,
		Consumer<Map<CombatStyle, OptimizerService.StyleResult>> callback)
	{
		Set<Integer> uniform = excludedItems == null
			? Collections.emptySet() : excludedItems;
		Map<CombatStyle, Set<Integer>> byStyle = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			byStyle.put(style, uniform);
		}
		service.bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock,
			byStyle, maxTradeables, riskBudgetGp, antifirePotion, 0, true,
			Collections.emptyMap(), Collections.emptyMap(),
			com.loadoutlab.data.WildernessMonsters.isExclusive(monster),
			dreamItems, upgradeBudgetGp, 1, true, pinnedByStyle, pinnedSpell, 0,
			Collections.<Integer>emptySet(), callback);
	}

	/** Per-style exclusions, no bench (maxSwaps 1). */
	public static void bestPerStyle(
		OptimizerService service,
		MonsterStats monster,
		PlayerLevels realLevels,
		PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle,
		int maxTradeables,
		int riskBudgetGp,
		boolean antifirePotion,
		boolean inWilderness,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell,
		Set<Integer> protectOnlyItems,
		Consumer<Map<CombatStyle, OptimizerService.StyleResult>> callback)
	{
		service.bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock, excludedByStyle,
			maxTradeables, riskBudgetGp, antifirePotion, 0, true, Collections.emptyMap(), Collections.emptyMap(), inWilderness, dreamItems,
			upgradeBudgetGp, 1, true, pinnedByStyle, pinnedSpell, 0, protectOnlyItems, callback);
	}

	/** Per-style exclusions with a pinned SPEC weapon (no bench). */
	public static void bestPerStyle(
		OptimizerService service,
		MonsterStats monster,
		PlayerLevels realLevels,
		PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle,
		int maxTradeables,
		int riskBudgetGp,
		boolean antifirePotion,
		boolean inWilderness,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell,
		int pinnedSpec,
		Set<Integer> protectOnlyItems,
		Consumer<Map<CombatStyle, OptimizerService.StyleResult>> callback)
	{
		service.bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock, excludedByStyle,
			maxTradeables, riskBudgetGp, antifirePotion, 0, true, Collections.emptyMap(), Collections.emptyMap(), inWilderness, dreamItems,
			upgradeBudgetGp, 1, true, pinnedByStyle, pinnedSpell, pinnedSpec, protectOnlyItems, callback);
	}

	/** maxSwaps bench, raid boost assumed. */
	public static void bestPerStyle(
		OptimizerService service,
		MonsterStats monster,
		PlayerLevels realLevels,
		PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle,
		int maxTradeables,
		int riskBudgetGp,
		boolean antifirePotion,
		boolean inWilderness,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		int maxSwaps,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell,
		Set<Integer> protectOnlyItems,
		Consumer<Map<CombatStyle, OptimizerService.StyleResult>> callback)
	{
		service.bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock, excludedByStyle,
			maxTradeables, riskBudgetGp, antifirePotion, 0, true, Collections.emptyMap(), Collections.emptyMap(), inWilderness, dreamItems,
			upgradeBudgetGp, maxSwaps, true, pinnedByStyle, pinnedSpell, 0,
			protectOnlyItems, callback);
	}

	/** No bench, no per-mob exclusions. */
	public static void bestPerStyleAcross(
		OptimizerService service,
		List<MonsterStats> mobs,
		PlayerLevels realLevels, PlayerLevels boostedLevels, PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
		boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle, int maxTradeables, int riskBudgetGp,
		boolean antifirePotion, boolean inWilderness, Set<Integer> dreamItems, int upgradeBudgetGp,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell, Set<Integer> protectOnlyItems,
		Consumer<OptimizerService.RosterResult> callback)
	{
		bestPerStyleAcross(service, mobs, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock, excludedByStyle,
			maxTradeables, riskBudgetGp, antifirePotion, inWilderness, dreamItems, upgradeBudgetGp,
			1, pinnedByStyle, pinnedSpell, protectOnlyItems, callback);
	}

	/** maxSwaps bench, no per-mob exclusions. */
	public static void bestPerStyleAcross(
		OptimizerService service,
		List<MonsterStats> mobs,
		PlayerLevels realLevels, PlayerLevels boostedLevels, PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
		boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle, int maxTradeables, int riskBudgetGp,
		boolean antifirePotion, boolean inWilderness, Set<Integer> dreamItems, int upgradeBudgetGp,
		int maxSwaps,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell, Set<Integer> protectOnlyItems,
		Consumer<OptimizerService.RosterResult> callback)
	{
		bestPerStyleAcross(service, mobs, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock, excludedByStyle,
			maxTradeables, riskBudgetGp, antifirePotion, inWilderness, dreamItems, upgradeBudgetGp,
			maxSwaps, Collections.emptyMap(), pinnedByStyle, pinnedSpell,
			protectOnlyItems, callback);
	}

	/** The synchronous roster harness the roster tests share: the client's
	 * bestPerStyleAcross call at MAXED defaults, latched to completion.
	 * Returns the raw result (null when the compute was superseded);
	 * throws on timeout. Previously each roster test hand-rolled this
	 * latch + AtomicReference block. */
	public static OptimizerService.RosterResult runRoster(
		OptimizerService service, List<MonsterStats> mobs,
		Map<Integer, Integer> owned, int maxSwaps,
		Map<Integer, Map<CombatStyle, Set<Integer>>> excludedByMob)
		throws InterruptedException
	{
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
		bestPerStyleAcross(service,
			mobs, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED,
			new OwnedItems(owned, true), 1, false, false, "",
			Collections.emptyMap(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0, maxSwaps,
			excludedByMob, Collections.emptyMap(), null, Collections.emptySet(),
			roster ->
			{
				out.set(roster);
				done.countDown();
			});
		if (!done.await(120, TimeUnit.SECONDS))
		{
			throw new AssertionError("roster compute timed out");
		}
		return out.get();
	}

	/** Per-mob exclusions; per-mob sims empty, raid boost assumed. */
	public static void bestPerStyleAcross(
		OptimizerService service,
		List<MonsterStats> mobs,
		PlayerLevels realLevels, PlayerLevels boostedLevels, PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
		boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle, int maxTradeables, int riskBudgetGp,
		boolean antifirePotion, boolean inWilderness, Set<Integer> dreamItems, int upgradeBudgetGp,
		int maxSwaps,
		Map<Integer, Map<CombatStyle, Set<Integer>>> excludedByMob,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell, Set<Integer> protectOnlyItems,
		Consumer<OptimizerService.RosterResult> callback)
	{
		service.bestPerStyleAcross(mobs, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock,
			excludedByStyle, maxTradeables, riskBudgetGp, antifirePotion, 0, true, Collections.emptyMap(), Collections.emptyMap(), inWilderness,
			dreamItems, upgradeBudgetGp, maxSwaps, excludedByMob,
			Collections.emptyMap(), true,
			pinnedByStyle, pinnedSpell, 0, protectOnlyItems, callback);
	}
}
