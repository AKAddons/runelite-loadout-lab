package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.ComputeParams;
import com.ll.engine.Dps;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
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
	private static Tables data;
	private static Monster mob;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		mob = data.searchMonsters("abyssal demon", 1).get(0);
	}

	private static Owned whip() {
		return new Owned(Map.of(4151, 1), true);
	}

	private static Map<Style, Service.Pick> search(Service service, String mode) throws Exception {
		ComputeParams p = new ComputeParams();
		p.specWeapon = false;
		p.optimizeMode = mode;
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
		service.bestPerStyle(mob, Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, whip(), 1, p, Collections.emptyMap(), Collections.emptySet(),
			Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), result -> {
				out.set(result);
				done.countDown();
			});
		assertTrue(done.await(300, TimeUnit.SECONDS));
		return out.get();
	}

	private static Service.ComputeContext context(Service service) {
		ComputeParams p = new ComputeParams();
		p.specWeapon = false;
		return service.buildContext(Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, whip(), 1, p, Collections.emptyMap(), Collections.emptySet(),
			Map.of(), Map.of(), Map.of(), Collections.emptySet());
	}

	private static Set<String> keys(Service service) {
		synchronized (service.optimizeCache) {
			return new HashSet<>(service.optimizeCache.keySet());
		}
	}

	@Test
	@DisplayName("a single search leaves its six optimizes cached, and a mode flip adds none: the mode is not in their key")
	void modeFlipReusesTheOptimizes() throws Exception {
		Service service = new Service(data);
		try {
			Map<Style, Service.Pick> max = search(service, "MAX_DPS");
			Set<String> before = keys(service);
			assertEquals(6, before.size(), "owned and BiS for each of the three styles");
			Map<Style, Service.Pick> tanky = search(service, "TANKY");
			assertEquals(before, keys(service), "the Tanky search computed no new optimize");
			for (Style style : max.keySet()) {
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
		Service service = new Service(data);
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
		Service service = new Service(data);
		try {
			Service.ComputeContext ctx = context(service);
			long stale = service.requestSeq.get();
			long ticket = service.requestSeq.incrementAndGet();
			assertNull(service.passA(List.of(mob), ctx, stale), "a stale ticket abandons");
			assertTrue(keys(service).isEmpty(), "and computes nothing");
			Map<Style, Service.StyleWork> works = service.passA(List.of(mob), ctx, ticket);
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
		Service service = new Service(data);
		try {
			Service.ComputeContext ctx = context(service);
			ctx.ticket = service.requestSeq.incrementAndGet();
			Service.StylePlan plan = Service.stylePlan(ctx, Style.MELEE, null);
			Req req = service.ownedRequestFor(ctx, mob, Style.MELEE, plan.levels, null);
			List<Dps> first = service.sideBest(false, ctx, req, null);
			assertFalse(first.isEmpty());
			assertEquals(1, keys(service).size(), "a style card's side is cached");
			assertSame(first.get(0), service.sideBest(false, ctx, req, null).get(0), "the second ask is a hit");
			assertFalse(service.sideBest(false, ctx, req, new Service.ToolPick()).isEmpty());
			assertEquals(1, keys(service).size(), "a multi tool's side is not cached");
			service.clearCache();
			ctx.ticket = -1;
			List<Dps> direct = service.sideBest(false, ctx, req, null);
			assertEquals(first.get(0).dps, direct.get(0).dps, 1e-9, "a superseded miss falls back to a direct optimize");
			assertTrue(keys(service).isEmpty(), "and caches nothing");
		} finally {
			service.shutdown();
		}
	}
}
