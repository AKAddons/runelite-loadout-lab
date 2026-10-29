package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Speed pass 2 (ISS-01M62X98P4G3FKE6PVH1CDCY0Y, Andrew 2026-10-28: "you have the
 * freedom to execute as many perf experiments and improvements as you want"): a
 * single-mob search runs its six optimizes as the roster's pass A, through the
 * optimize cache, so a mode flip reuses them and an account switch clears them.
 */
class SharedOptimizeCacheTest {
	private static LoadoutData data;
	private static MonsterStats mob;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		mob = data.searchMonsters("abyssal demon", 1).get(0);
	}

	private static OwnedItems whip() {
		return new OwnedItems(Map.of(4151, 1), true);
	}

	private static Map<CombatStyle, OptimizerService.StyleResult> search(OptimizerService service, String mode) throws Exception {
		ComputeParams p = new ComputeParams();
		p.specWeapon = false;
		p.optimizeMode = mode;
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
		service.bestPerStyle(mob, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, whip(), 1, p, Collections.emptyMap(), Collections.emptySet(),
			Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), result -> {
				out.set(result);
				done.countDown();
			});
		assertTrue(done.await(300, TimeUnit.SECONDS));
		return out.get();
	}

	private static OptimizerService.ComputeContext context(OptimizerService service) {
		ComputeParams p = new ComputeParams();
		p.specWeapon = false;
		return service.buildContext(PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, whip(), 1, p, Collections.emptyMap(), Collections.emptySet(),
			Map.of(), Map.of(), Map.of(), Collections.emptySet());
	}

	private static Set<String> keys(OptimizerService service) {
		synchronized (service.optimizeCache) {
			return new HashSet<>(service.optimizeCache.keySet());
		}
	}

	@Test
	@DisplayName("a single search leaves its six optimizes cached, and a mode flip adds none: the mode is not in their key")
	void modeFlipReusesTheOptimizes() throws Exception {
		OptimizerService service = new OptimizerService(data);
		try {
			Map<CombatStyle, OptimizerService.StyleResult> max = search(service, "MAX_DPS");
			Set<String> before = keys(service);
			assertEquals(6, before.size(), "owned and BiS for each of the three styles");
			Map<CombatStyle, OptimizerService.StyleResult> tanky = search(service, "TANKY");
			assertEquals(before, keys(service), "the Tanky search computed no new optimize");
			for (CombatStyle style : max.keySet()) {
				assertEquals(max.get(style).overallBest.dps, tanky.get(style).overallBest.dps, 1e-9,
					style + ": the ceiling does not depend on the mode");
			}
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("an account switch clears the optimize cache with the card cache: its key holds no quest or diary state")
	void clearCacheEmptiesTheOptimizeCache() {
		OptimizerService service = new OptimizerService(data);
		try {
			synchronized (service.optimizeCache) {
				service.optimizeCache.put("stale", new ArrayList<>());
			}
			service.clearCache();
			assertTrue(keys(service).isEmpty());
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("pass A for one mob caches its six optimizes, and a superseded ticket abandons it with null")
	void passAForOneMob() {
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.ComputeContext ctx = context(service);
			long stale = service.requestSeq.get();
			long ticket = service.requestSeq.incrementAndGet();
			assertNull(service.passA(List.of(mob), ctx, stale), "a stale ticket abandons");
			assertTrue(keys(service).isEmpty(), "and computes nothing");
			Map<CombatStyle, OptimizerService.StyleWork> works = service.passA(List.of(mob), ctx, ticket);
			assertNotNull(works);
			assertEquals(3, works.size(), "one plan per style");
			assertEquals(6, keys(service).size(), "owned and BiS per style, cached");
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("a style card's side is a cache hit the second time, a multi tool's skips the cache, a superseded miss still answers")
	void sideBestRoutes() {
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.ComputeContext ctx = context(service);
			ctx.ticket = service.requestSeq.incrementAndGet();
			OptimizerService.StylePlan plan = OptimizerService.stylePlan(ctx, CombatStyle.MELEE, null);
			OptimizationRequest req = service.ownedRequestFor(ctx, mob, CombatStyle.MELEE, plan.levels, null);
			List<DpsResult> first = service.sideBest(false, ctx, req, null);
			assertFalse(first.isEmpty());
			assertEquals(1, keys(service).size(), "a style card's side is cached");
			assertSame(first.get(0), service.sideBest(false, ctx, req, null).get(0), "the second ask is a hit");
			assertFalse(service.sideBest(false, ctx, req, new OptimizerService.ToolPick()).isEmpty());
			assertEquals(1, keys(service).size(), "a multi tool's side is not cached");
			service.clearCache();
			ctx.ticket = -1;
			List<DpsResult> direct = service.sideBest(false, ctx, req, null);
			assertEquals(first.get(0).dps, direct.get(0).dps, 1e-9, "a superseded miss falls back to a direct optimize");
			assertTrue(keys(service).isEmpty(), "and caches nothing");
		} finally {
			service.shutdown();
		}
	}
}
