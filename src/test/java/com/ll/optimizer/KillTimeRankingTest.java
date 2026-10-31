package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.ComputeParams;
import com.ll.engine.Calc;
import com.ll.engine.Dps;
import com.ll.engine.Kit;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import com.ll.model.RenderModel;
import com.ll.optimizer.Service.Pick;
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
	private static Tables data;
	private static Service service;
	private static Owned owned;
	private static List<Monster> moons;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		service = new Service(data);
		Map<Integer, Integer> bank = new HashMap<>();
		for (Slot slot : Slot.values()) {
			for (Gear g : data.getGearItems(slot)) {
				if (GroupModesTest.BANK.contains(g.lname)) bank.put(g.id, 1);
			}
		}
		owned = new Owned(bank, true);
		moons = List.of(data.searchMonsters("blood moon", 1).get(0), data.searchMonsters("blue moon", 1).get(0),
			data.monsters.stream().filter(m -> m.name.equals("Eclipse Moon") && "Regular".equals(m.version))
				.findFirst().get());
	}

	@AfterAll
	static void stop() {
		service.shutdown();
	}

	private static Map<Style, Pick> single(Monster mob) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Pick>> out = new AtomicReference<>();
		service.bestPerStyle(mob, GroupModesTest.LV, GroupModesTest.LV, Prayers.ALL, RequirementProfile.MAXED, owned, 1,
			new ComputeParams(), Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
			Collections.<Integer>emptySet(), result -> {
				out.set(result);
				done.countDown();
			});
		assertTrue(done.await(600, TimeUnit.SECONDS));
		return out.get();
	}

	private static Service.RosterResult group() throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Service.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(moons, GroupModesTest.LV, GroupModesTest.LV, Prayers.ALL, RequirementProfile.MAXED,
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
		for (Slot slot : Slot.values()) {
			for (Gear g : data.getGearItems(slot)) {
				if (g.lname.equals(name)) return g;
			}
		}
		throw new AssertionError("no " + name);
	}

	private static Kit set(String... names) {
		Map<Slot, Gear> gear = new EnumMap<>(Slot.class);
		for (String name : names) gear.put(item(name).slot, item(name));
		return new Kit(gear);
	}

	private static Req melee(Monster moon) {
		Service.ComputeContext ctx = service.buildContext(GroupModesTest.LV, GroupModesTest.LV, Prayers.ALL,
			RequirementProfile.MAXED, owned, 1, new ComputeParams(), Collections.emptyMap(), Collections.emptySet(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.<Integer>emptySet());
		return service.ownedRequestFor(ctx, moon, Style.MELEE, Service.stylePlan(ctx, Style.MELEE, null).levels, null);
	}

	@Test
	@DisplayName("alone, Blood Moon's Max DPS card counts her heal: its net DPS is below its DPS, its kill time reads the net, and no set in the bank kills faster")
	void singleBloodMoonRanksByNet() throws Exception {
		Pick melee = single(moons.get(0)).get(Style.MELEE);
		Dps shown = melee.owned.get(0);
		assertTrue(melee.incoming.healDps > 0, "she heals");
		assertEquals(shown.dps - melee.incoming.healDps, shown.net, 1e-9);
		Calc calc = new Calc();
		Req req = melee(moons.get(0));
		Kit bloodMoon = set("blood moon helm", "fire cape", "amulet of torture", "holy blessing", "abyssal tentacle",
			"blood moon chestplate", "blood moon tassets", "barrows gloves", "dragon boots", "berserker ring (i)");
		Dps bm = service.calcR(calc, req, bloodMoon);
		assertTrue(shown.net >= bm.net - 1e-9, "the shown set kills at least as fast as blood moon armour: " + shown.net + " vs " + bm.net);
		assertTrue(bm.net < bm.dps, "blood moon armour feeds her heal");
	}

	@Test
	@DisplayName("a boss with no heal rule ranks as before: net DPS is DPS on every card, and the incoming heal is zero")
	void graardorUnchanged() throws Exception {
		Map<Style, Pick> cards = single(data.searchMonsters("general graardor", 1).get(0));
		int seen = 0;
		for (Pick r : cards.values()) {
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
		Service.RosterResult roster = group();
		Pick blood = roster.pm.get(0).get(Style.MELEE);
		Dps shown = blood.owned.get(0);
		Set<Integer> worn = shown.getLoadout().ids();
		assertFalse(worn.contains(item("blood moon chestplate").id), "the heal-feeding chestplate is not worn on her");
		Calc calc = new Calc();
		Dps bm = service.calcR(calc, melee(moons.get(0)), set("blood moon helm", "fire cape",
			"amulet of torture", "holy blessing", "abyssal tentacle", "blood moon chestplate", "blood moon tassets",
			"barrows gloves", "dragon boots", "berserker ring (i)"));
		assertTrue(shown.net > bm.net, "faster than blood moon armour on her: " + shown.net + " vs " + bm.net);
		Map entry = RenderModel.entry(moons, roster.pm);
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
