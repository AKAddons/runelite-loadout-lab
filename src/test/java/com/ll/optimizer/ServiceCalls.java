package com.ll.optimizer;

import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.ComputeParams;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Test/headless convenience bridges for {@link Service}. These
 * were back-compat overloads in main source; the Plugin Hub token cap
 * moved them here (tests are not counted by the hub bot). Each fills the
 * newest signature's defaults exactly as the old overloads did.
 */
public final class ServiceCalls {
	private ServiceCalls() {
	}

	// A roster's route gods, as the engine seeds them on a pick.
	static java.util.List<String> routeOf(java.util.List<Monster> mobs) {
		java.util.List<String> gods = new java.util.ArrayList<>();
		for (Monster m : mobs) for (String god : com.ll.engine.GwdProtection.godsFor(m)) if (!gods.contains(god)) gods.add(god);
		return gods;
	}

	private static ComputeParams params(java.util.List<String> gods, boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		int maxTr, int riskBudgetGp, boolean antifirePotion, boolean inWilderness,
		int upgradeBudgetGp, int maxSwaps) {
		ComputeParams p = new ComputeParams();
		// The route's gods as a selection seeds them (ISS-01M67DQ9ATHSQSJA7QGDKAQGPT).
		p.gods = gods;
		p.f2pOnly = f2pOnly;
		p.onSlayerTask = onSlayerTask;
		p.spellbookLock = spellbookLock;
		p.maxTr = maxTr;
		p.riskBudgetGp = riskBudgetGp;
		p.antifirePotion = antifirePotion;
		p.inWilderness = inWilderness;
		p.upgradeBudgetGp = upgradeBudgetGp;
		p.maxSwaps = maxSwaps;
		return p;
	}

	/** No pinned items. */
	public static void bestPerStyle(
		Service service,
		Monster monster,
		Levels realLevels,
		Levels boostedLevels,
		Prayers prayerUnlocks,
		RequirementProfile requirements,
		Owned owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Set<Integer> excludedItems,
		int maxTr,
		int riskBudgetGp,
		boolean antifirePotion,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		Consumer<Map<Style, Service.Pick>> callback) {
		bestPerStyle(service, monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock,
			excludedItems, maxTr, riskBudgetGp, antifirePotion, dreamItems,
			upgradeBudgetGp,
			Collections.<Style, Map<com.ll.data.Slot, Integer>>emptyMap(),
			null, callback);
	}

	/** One exclusion set, all styles; Wilderness only when the monster
	 * exists nowhere else (matches the request default). */
	public static void bestPerStyle(
		Service service,
		Monster monster,
		Levels realLevels,
		Levels boostedLevels,
		Prayers prayerUnlocks,
		RequirementProfile requirements,
		Owned owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Set<Integer> excludedItems,
		int maxTr,
		int riskBudgetGp,
		boolean antifirePotion,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		Map<Style, Map<com.ll.data.Slot, Integer>> pinnedByStyle,
		com.ll.data.Spell pinnedSpell,
		Consumer<Map<Style, Service.Pick>> callback) {
		Set<Integer> uniform = excludedItems == null
			? Collections.emptySet() : excludedItems;
		Map<Style, Set<Integer>> byStyle = new EnumMap<>(Style.class);
		for (Style style : Style.concreteValues()) {
			byStyle.put(style, uniform);
		}
		service.bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, params(com.ll.engine.GwdProtection.godsFor(monster), f2pOnly, onSlayerTask, spellbookLock, maxTr,
				riskBudgetGp, antifirePotion, com.ll.data.WildernessMonsters.isExclusive(monster),
				upgradeBudgetGp, 1),
			byStyle, dreamItems, pinnedByStyle, pinnedSpell, 0,
			Collections.<Integer>emptySet(), callback);
	}

	/** Per-style exclusions, no bench (maxSwaps 1). */
	public static void bestPerStyle(
		Service service,
		Monster monster,
		Levels realLevels,
		Levels boostedLevels,
		Prayers prayerUnlocks,
		RequirementProfile requirements,
		Owned owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Map<Style, Set<Integer>> excludedByStyle,
		int maxTr,
		int riskBudgetGp,
		boolean antifirePotion,
		boolean inWilderness,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		Map<Style, Map<com.ll.data.Slot, Integer>> pinnedByStyle,
		com.ll.data.Spell pinnedSpell,
		Set<Integer> protectOnlyItems,
		Consumer<Map<Style, Service.Pick>> callback) {
		service.bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, params(com.ll.engine.GwdProtection.godsFor(monster), f2pOnly, onSlayerTask, spellbookLock, maxTr,
				riskBudgetGp, antifirePotion, inWilderness, upgradeBudgetGp, 1),
			excludedByStyle, dreamItems, pinnedByStyle, pinnedSpell, 0, protectOnlyItems, callback);
	}

	/** Per-style exclusions with a pinned SPEC weapon (no bench). */
	public static void bestPerStyle(
		Service service,
		Monster monster,
		Levels realLevels,
		Levels boostedLevels,
		Prayers prayerUnlocks,
		RequirementProfile requirements,
		Owned owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Map<Style, Set<Integer>> excludedByStyle,
		int maxTr,
		int riskBudgetGp,
		boolean antifirePotion,
		boolean inWilderness,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		Map<Style, Map<com.ll.data.Slot, Integer>> pinnedByStyle,
		com.ll.data.Spell pinnedSpell,
		int pinnedSpec,
		Set<Integer> protectOnlyItems,
		Consumer<Map<Style, Service.Pick>> callback) {
		service.bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, params(com.ll.engine.GwdProtection.godsFor(monster), f2pOnly, onSlayerTask, spellbookLock, maxTr,
				riskBudgetGp, antifirePotion, inWilderness, upgradeBudgetGp, 1),
			excludedByStyle, dreamItems, pinnedByStyle, pinnedSpell, pinnedSpec, protectOnlyItems, callback);
	}

	/** maxSwaps bench, raid boost assumed. */
	public static void bestPerStyle(
		Service service,
		Monster monster,
		Levels realLevels,
		Levels boostedLevels,
		Prayers prayerUnlocks,
		RequirementProfile requirements,
		Owned owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Map<Style, Set<Integer>> excludedByStyle,
		int maxTr,
		int riskBudgetGp,
		boolean antifirePotion,
		boolean inWilderness,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		int maxSwaps,
		Map<Style, Map<com.ll.data.Slot, Integer>> pinnedByStyle,
		com.ll.data.Spell pinnedSpell,
		Set<Integer> protectOnlyItems,
		Consumer<Map<Style, Service.Pick>> callback) {
		service.bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, params(com.ll.engine.GwdProtection.godsFor(monster), f2pOnly, onSlayerTask, spellbookLock, maxTr,
				riskBudgetGp, antifirePotion, inWilderness, upgradeBudgetGp, maxSwaps),
			excludedByStyle, dreamItems, pinnedByStyle, pinnedSpell, 0,
			protectOnlyItems, callback);
	}

	/** No bench, no per-mob exclusions. */
	public static void bestPerStyleAcross(
		Service service,
		List<Monster> mobs,
		Levels realLevels, Levels boostedLevels, Prayers prayerUnlocks,
		RequirementProfile requirements, Owned owned, int collectionFingerprint,
		boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		Map<Style, Set<Integer>> excludedByStyle, int maxTr, int riskBudgetGp,
		boolean antifirePotion, boolean inWilderness, Set<Integer> dreamItems, int upgradeBudgetGp,
		Map<Style, Map<com.ll.data.Slot, Integer>> pinnedByStyle,
		com.ll.data.Spell pinnedSpell, Set<Integer> protectOnlyItems,
		Consumer<Service.RosterResult> callback) {
		bestPerStyleAcross(service, mobs, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock, excludedByStyle,
			maxTr, riskBudgetGp, antifirePotion, inWilderness, dreamItems, upgradeBudgetGp,
			1, pinnedByStyle, pinnedSpell, protectOnlyItems, callback);
	}

	/** maxSwaps bench, no per-mob exclusions. */
	public static void bestPerStyleAcross(
		Service service,
		List<Monster> mobs,
		Levels realLevels, Levels boostedLevels, Prayers prayerUnlocks,
		RequirementProfile requirements, Owned owned, int collectionFingerprint,
		boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		Map<Style, Set<Integer>> excludedByStyle, int maxTr, int riskBudgetGp,
		boolean antifirePotion, boolean inWilderness, Set<Integer> dreamItems, int upgradeBudgetGp,
		int maxSwaps,
		Map<Style, Map<com.ll.data.Slot, Integer>> pinnedByStyle,
		com.ll.data.Spell pinnedSpell, Set<Integer> protectOnlyItems,
		Consumer<Service.RosterResult> callback) {
		bestPerStyleAcross(service, mobs, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock, excludedByStyle,
			maxTr, riskBudgetGp, antifirePotion, inWilderness, dreamItems, upgradeBudgetGp,
			maxSwaps, Collections.emptyMap(), pinnedByStyle, pinnedSpell,
			protectOnlyItems, callback);
	}

	/** The synchronous roster harness the roster tests share: the client's
	 * bestPerStyleAcross call at MAXED defaults, latched to completion.
	 * Returns the raw result (null when the compute was superseded);
	 * throws on timeout. Previously each roster test hand-rolled this
	 * latch + AtomicReference block. */
	public static Service.RosterResult runRoster(
		Service service, List<Monster> mobs,
		Map<Integer, Integer> owned, int maxSwaps,
		Map<Integer, Map<Style, Set<Integer>>> excludedByMob)
		throws InterruptedException {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Service.RosterResult> out = new AtomicReference<>();
		bestPerStyleAcross(service,
			mobs, Levels.MAXED, Levels.MAXED, Prayers.ALL, RequirementProfile.MAXED,
			new Owned(owned, true), 1, false, false, "",
			Collections.emptyMap(), -1, Req.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0, maxSwaps,
			excludedByMob, Collections.emptyMap(), null, Collections.emptySet(),
			roster -> {
				out.set(roster);
				done.countDown();
			});
		if (!done.await(120, TimeUnit.SECONDS)) {
			throw new AssertionError("roster compute timed out");
		}
		return out.get();
	}

	/** The per-member harness (ISS-01M4Y2VJ8DWBBKFR1HP9NR45NG): pins, the
	 * pinned spell and the pinned spec keyed by Monster.profileId(),
	 * the way excludedByMob travels. Same latch as runRoster. */
	public static Service.RosterResult runRosterPinned(
		Service service, List<Monster> mobs,
		Map<Integer, Integer> owned, int maxSwaps,
		Map<Integer, Map<Style, Map<com.ll.data.Slot, Integer>>> pinsByMob,
		Map<Integer, com.ll.data.Spell> spellOf,
		Map<Integer, Integer> specOf)
		throws InterruptedException {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Service.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(mobs, Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, new Owned(owned, true), 1,
			params(routeOf(mobs), false, false, "", -1, Req.DEFAULT_RISK_BUDGET_GP, false, false, 0, maxSwaps),
			Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(),
			pinsByMob, spellOf, specOf, Collections.emptySet(),
			roster -> {
				out.set(roster);
				done.countDown();
			});
		if (!done.await(120, TimeUnit.SECONDS)) {
			throw new AssertionError("roster compute timed out");
		}
		return out.get();
	}

	/** Per-mob exclusions; per-mob sims empty, raid boost assumed. */
	public static void bestPerStyleAcross(
		Service service,
		List<Monster> mobs,
		Levels realLevels, Levels boostedLevels, Prayers prayerUnlocks,
		RequirementProfile requirements, Owned owned, int collectionFingerprint,
		boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		Map<Style, Set<Integer>> excludedByStyle, int maxTr, int riskBudgetGp,
		boolean antifirePotion, boolean inWilderness, Set<Integer> dreamItems, int upgradeBudgetGp,
		int maxSwaps,
		Map<Integer, Map<Style, Set<Integer>>> excludedByMob,
		Map<Style, Map<com.ll.data.Slot, Integer>> pinnedByStyle,
		com.ll.data.Spell pinnedSpell, Set<Integer> protectOnlyItems,
		Consumer<Service.RosterResult> callback) {
		// A roster-wide pin reaches every member (today's test semantics).
		Map<Integer, Map<Style, Map<com.ll.data.Slot, Integer>>> pinsByMob = new HashMap<>();
		Map<Integer, com.ll.data.Spell> spellOf = new HashMap<>();
		for (Monster mob : mobs) {
			pinsByMob.put(mob.profileId(), pinnedByStyle);
			spellOf.put(mob.profileId(), pinnedSpell);
		}
		service.bestPerStyleAcross(mobs, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint,
			params(routeOf(mobs), f2pOnly, onSlayerTask, spellbookLock, maxTr, riskBudgetGp, antifirePotion, inWilderness, upgradeBudgetGp, maxSwaps),
			excludedByStyle, dreamItems, excludedByMob, Collections.emptyMap(),
			pinsByMob, spellOf, Collections.emptyMap(), protectOnlyItems, callback);
	}
}
