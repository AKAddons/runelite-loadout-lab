package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.Style;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.DpsCalculator;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.Loadout;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.model.RenderModel;
import com.loadoutlab.optimizer.OptimizerService.StyleResult;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sets rank by kill time where the boss heals (ISS-01M6415DTE84ZZ45C4YD3MZRBJ,
 * Andrew 2026-10-29: "ultimately what we should be aiming for is to make the
 * time to kill as low as possible and not necessarily output the most dps"):
 * net DPS (dps minus the boss's heal off the set) is what the search ranks,
 * alone and on a group's kit; a boss with no heal rule ranks as before.
 */
class KillTimeRankingTest {
	private static LoadoutData data;
	private static OptimizerService service;
	private static OwnedItems owned;
	private static List<Monster> moons;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		service = new OptimizerService(data);
		Map<Integer, Integer> bank = new HashMap<>();
		for (GearSlot slot : GearSlot.values()) {
			for (Gear g : data.getGearItems(slot)) {
				if (GroupModesTest.BANK.contains(g.nameLower)) bank.put(g.id, 1);
			}
		}
		owned = new OwnedItems(bank, true);
		moons = List.of(data.searchMonsters("blood moon", 1).get(0), data.searchMonsters("blue moon", 1).get(0),
			data.monsters.stream().filter(m -> m.name.equals("Eclipse Moon") && "Regular".equals(m.version))
				.findFirst().get());
	}

	@AfterAll
	static void stop() {
		service.shutdown();
	}

	private static Map<Style, StyleResult> single(Monster mob) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, StyleResult>> out = new AtomicReference<>();
		service.bestPerStyle(mob, GroupModesTest.LV, GroupModesTest.LV, PrayerUnlocks.ALL, RequirementProfile.MAXED, owned, 1,
			new ComputeParams(), Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
			Collections.<Integer>emptySet(), result -> {
				out.set(result);
				done.countDown();
			});
		assertTrue(done.await(600, TimeUnit.SECONDS));
		return out.get();
	}

	private static OptimizerService.RosterResult group() throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(moons, GroupModesTest.LV, GroupModesTest.LV, PrayerUnlocks.ALL, RequirementProfile.MAXED,
			owned, 1, new ComputeParams(), Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.<Integer>emptySet(), result -> {
				out.set(result);
				done.countDown();
			});
		assertTrue(done.await(600, TimeUnit.SECONDS));
		return out.get();
	}

	private static Gear item(String name) {
		for (GearSlot slot : GearSlot.values()) {
			for (Gear g : data.getGearItems(slot)) {
				if (g.nameLower.equals(name)) return g;
			}
		}
		throw new AssertionError("no " + name);
	}

	private static Loadout set(String... names) {
		Map<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
		for (String name : names) gear.put(item(name).slot, item(name));
		return new Loadout(gear);
	}

	private static OptimizationRequest melee(Monster moon) {
		OptimizerService.ComputeContext ctx = service.buildContext(GroupModesTest.LV, GroupModesTest.LV, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, owned, 1, new ComputeParams(), Collections.emptyMap(), Collections.emptySet(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.<Integer>emptySet());
		return service.ownedRequestFor(ctx, moon, Style.MELEE, OptimizerService.stylePlan(ctx, Style.MELEE, null).levels, null);
	}

	@Test
	@DisplayName("alone, Blood Moon's Max DPS card counts her heal: its net DPS is below its DPS, its kill time reads the net, and no set in the bank kills faster")
	void singleBloodMoonRanksByNet() throws Exception {
		StyleResult melee = single(moons.get(0)).get(Style.MELEE);
		DpsResult shown = melee.owned.get(0);
		assertTrue(melee.incoming.healDps > 0, "she heals");
		assertEquals(shown.dps - melee.incoming.healDps, shown.net, 1e-9);
		DpsCalculator calc = new DpsCalculator();
		OptimizationRequest req = melee(moons.get(0));
		Loadout bloodMoon = set("blood moon helm", "fire cape", "amulet of torture", "holy blessing", "abyssal tentacle",
			"blood moon chestplate", "blood moon tassets", "barrows gloves", "dragon boots", "berserker ring (i)");
		DpsResult bm = service.calcRespecting(calc, req, bloodMoon);
		assertTrue(shown.net >= bm.net - 1e-9, "the shown set kills at least as fast as blood moon armour: " + shown.net + " vs " + bm.net);
		assertTrue(bm.net < bm.dps, "blood moon armour feeds her heal");
	}

	@Test
	@DisplayName("a boss with no heal rule ranks as before: net DPS is DPS on every card, and the incoming heal is zero")
	void graardorUnchanged() throws Exception {
		Map<Style, StyleResult> cards = single(data.searchMonsters("general graardor", 1).get(0));
		int seen = 0;
		for (StyleResult r : cards.values()) {
			if (r == null || r.owned.isEmpty()) continue;
			assertEquals(r.owned.get(0).dps, r.owned.get(0).net, 0);
			assertEquals(0, r.incoming.healDps, 0);
			seen++;
		}
		assertTrue(seen >= 1, "melee at least on the melee bank");
	}

	@Test
	@DisplayName("the Moons' Max DPS kit wears the faster-kill set on Blood Moon, each card carries its kill time, and the entry carries the trip's")
	void moonsKitRanksByTripTime() throws Exception {
		OptimizerService.RosterResult roster = group();
		StyleResult blood = roster.perMob.get(0).get(Style.MELEE);
		DpsResult shown = blood.owned.get(0);
		Set<Integer> worn = shown.getLoadout().ids();
		assertFalse(worn.contains(item("blood moon chestplate").id), "the heal-feeding chestplate is not worn on her");
		DpsCalculator calc = new DpsCalculator();
		DpsResult bm = service.calcRespecting(calc, melee(moons.get(0)), set("blood moon helm", "fire cape",
			"amulet of torture", "holy blessing", "abyssal tentacle", "blood moon chestplate", "blood moon tassets",
			"barrows gloves", "dragon boots", "berserker ring (i)"));
		assertTrue(shown.net > bm.net, "faster than blood moon armour on her: " + shown.net + " vs " + bm.net);
		Map entry = RenderModel.entry(moons, roster.perMob);
		Map first = (Map) ((List) entry.get("mobs")).get(0);
		Map card = (Map) ((Map) ((Map) first.get("styles")).get("melee")).get("yours");
		Map kill = (Map) card.get("killTime");
		assertNotNull(kill, "the card's kill time");
		double p50 = ((Number) kill.get("p50")).doubleValue();
		assertTrue(p50 > 0 && !Double.isInfinite(p50), "P50 " + p50);
		assertTrue(((Number) kill.get("healDps")).doubleValue() > 0);
		Map trip = (Map) entry.get("tripKillTime");
		assertNotNull(trip, "the trip's kill time");
		double tp = ((Number) trip.get("p50")).doubleValue();
		assertTrue(tp > p50, "the trip outlasts one member: " + tp + " vs " + p50);
		assertEquals(3, ((List) trip.get("members")).size());
	}
}
