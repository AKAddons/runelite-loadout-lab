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
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import com.ll.optimizer.Service.OptimizeMode;
import com.ll.optimizer.Service.Pick;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
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

import static com.ll.optimizer.Service.OptimizeMode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Balanced and Tanky on a group search (ISS-01M64GNB852CBYVVWE29A9HJK4,
 * Andrew 2026-10-29: "i'm not sure why moons of peril can't have the 3 tab
 * options in search?"): each member's sweep pick feeds the trip kit, then
 * each member wears its mode-best combination of what the trip carries. The
 * Moons of Peril on the mid melee bank of the probe for KendyyOP's report.
 */
class GroupModesTest {
	static final Set<String> BANK = Set.of("dragon scimitar", "abyssal whip", "abyssal tentacle", "dual macuahuitl",
		"dragon defender", "helm of neitiznot", "blood moon helm", "blood moon chestplate", "blood moon tassets",
		"fighter torso", "bandos chestplate", "bandos tassets", "obsidian platelegs", "dragon platelegs",
		"amulet of fury", "amulet of torture", "fire cape", "barrows gloves", "dragon boots", "berserker ring (i)",
		"holy blessing",
		// A tank shield with no strength: kill time never wears it, Tanky can.
		"dragonfire shield");
	static final Levels LV = new Levels(80, 80, 80, 75, 75, 77, 85);

	private static Tables data;
	private static Owned owned;
	private static List<Monster> moons;
	private static final Map<OptimizeMode, Service.RosterResult> runs = new EnumMap<>(OptimizeMode.class);

	@BeforeAll
	static void searchTheMoons() throws Exception {
		data = new DataService().load();
		moons = List.of(data.searchMonsters("blood moon", 1).get(0), data.searchMonsters("blue moon", 1).get(0),
			data.monsters.stream().filter(m -> m.name.equals("Eclipse Moon") && "Regular".equals(m.version))
				.findFirst().get());
		Map<Integer, Integer> bank = new HashMap<>();
		for (Slot slot : Slot.values()) {
			for (Gear g : data.getGearItems(slot)) {
				if (BANK.contains(g.lname)) bank.put(g.id, 1);
			}
		}
		owned = new Owned(bank, true);
		// One service for the three searches, as in game: a tab flip reuses pass A.
		Service service = new Service(data);
		try {
			for (OptimizeMode mode : OptimizeMode.values()) {
				ComputeParams p = new ComputeParams();
				p.optimizeMode = mode.name();
				CountDownLatch done = new CountDownLatch(1);
				AtomicReference<Service.RosterResult> out = new AtomicReference<>();
				service.bestPerStyleAcross(moons, LV, LV, Prayers.ALL, RequirementProfile.MAXED, owned, 1, p,
					Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(),
					Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
					Collections.<Integer>emptySet(), result -> {
						out.set(result);
						done.countDown();
					});
				assertTrue(done.await(600, TimeUnit.SECONDS), mode + " finished");
				runs.put(mode, out.get());
			}
		} finally {
			service.shutdown();
		}
	}

	private static Pick card(OptimizeMode mode, int mob, Style style) {
		return runs.get(mode).pm.get(mob).get(style);
	}

	private static double dps(Pick card) {
		return card.owned.get(0).getDps();
	}

	private static double dtps(Pick card) {
		return card.incoming.tot;
	}

	private static Set<Integer> ids(Pick card) {
		return card.owned.get(0).getLoadout().ids();
	}

	// Since kill time (ISS-01M6415DTE84ZZ45C4YD3MZRBJ) Max DPS already wears
	// the tank set on Blood Moon (her heal makes it the faster kill), so the
	// modes' cuts are asserted across the three moons: never worse, and a real
	// trade on at least one (Blue Moon's macuahuitl gives way to a defender).

	@Test
	@DisplayName("Tanky: no moon's melee card takes more damage than on Max DPS, at least one takes less, and its trade says what it cost")
	void tankyCutsAMoonsDamage() {
		int cut = 0;
		for (int j = 0; j < moons.size(); j++) {
			Pick max = card(MAX_DPS, j, Style.MELEE);
			Pick tanky = card(TANKY, j, Style.MELEE);
			String where = moons.get(j).name + " DTPS " + dtps(tanky) + " vs Max DPS " + dtps(max);
			assertTrue(dtps(tanky) <= dtps(max) + 1e-9, where);
			assertNotNull(tanky.modeTrade, where);
			assertEquals(TANKY, tanky.modeTrade.mode);
			if (dtps(tanky) < dtps(max) - 0.01) {
				cut++;
				assertNull(tanky.modeTrade.reason, where + ": a safer set, not Max DPS kept");
				assertTrue(tanky.modeTrade.dmgCutPct > 0, where + ": the card shows the damage cut");
			}
		}
		assertTrue(cut >= 1, "a real trade on some moon");
	}

	@Test
	@DisplayName("Balanced: no moon's melee card takes more damage per DPS than on Max DPS; a card that keeps the Max DPS set says why")
	void balancedNeverWorsePerDps() {
		// On this bank the dragonfire shield's -32% DPS buys ~1% damage, so
		// Balanced rightly keeps every Max DPS set (Tanky takes the shield).
		for (int j = 0; j < moons.size(); j++) {
			Pick max = card(MAX_DPS, j, Style.MELEE);
			Pick balanced = card(BALANCED, j, Style.MELEE);
			double rMax = dtps(max) / dps(max);
			double rBalanced = dtps(balanced) / dps(balanced);
			assertTrue(rBalanced <= rMax + 1e-9, moons.get(j).name + " DTPS/DPS " + rBalanced + " vs Max DPS " + rMax);
			assertNotNull(balanced.modeTrade);
			if (ids(balanced).equals(ids(max))) {
				assertNotNull(balanced.modeTrade.reason, moons.get(j).name + ": the same set says why");
			} else {
				assertNull(balanced.modeTrade.reason, moons.get(j).name + ": a safer set, not Max DPS kept");
			}
		}
	}

	@Test
	@DisplayName("under a mode every Yours card carries its trade against the group's Max DPS card; Max DPS carries none")
	void tradesReadAgainstTheMaxDpsCard() {
		int cards = 0;
		for (int j = 0; j < moons.size(); j++) {
			for (Style style : Style.values()) {
				Pick max = card(MAX_DPS, j, style);
				if (max == null || max.owned.isEmpty()) continue;
				assertNull(max.modeTrade, "Max DPS makes no trade");
				for (OptimizeMode mode : List.of(BALANCED, TANKY)) {
					Pick c = card(mode, j, style);
					String where = moons.get(j).name + " " + style + " " + mode;
					assertNotNull(c, where);
					assertNotNull(c.modeTrade, where);
					cards++;
					if (c.modeTrade.reason != null) {
						assertEquals(ids(max), ids(c), where + " kept Max DPS (" + c.modeTrade.reason + ")");
					} else {
						assertEquals((int) Math.round((1 - dps(c) / dps(max)) * 100), c.modeTrade.dpsLossPct, where);
						assertEquals((int) Math.round((1 - dtps(c) / dtps(max)) * 100), c.modeTrade.dmgCutPct, where);
					}
				}
			}
		}
		assertTrue(cards >= 6, "the three moons, melee at least, both modes: " + cards);
	}

	/** A Tanky search's context and a moon's melee request on the probe bank. */
	private static Service.ComputeContext tanky(Service service) {
		ComputeParams p = new ComputeParams();
		p.optimizeMode = TANKY.name();
		return service.buildContext(LV, LV, Prayers.ALL, RequirementProfile.MAXED, owned, 1, p,
			Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.<Integer>emptySet());
	}

	private static Req melee(Service service, Service.ComputeContext ctx, Monster moon) {
		return service.ownedRequestFor(ctx, moon, Style.MELEE,
			Service.stylePlan(ctx, Style.MELEE, null).levels, null);
	}

	private static Gear item(String name) {
		for (Slot slot : Slot.values()) {
			for (Gear g : data.getGearItems(slot)) {
				if (g.lname.equals(name)) return g;
			}
		}
		throw new AssertionError("no " + name);
	}

	private static Kit set(List<String> names) {
		Map<Slot, Gear> gear = new EnumMap<>(Slot.class);
		for (String name : names) {
			Gear g = item(name);
			gear.put(g.slot, g);
		}
		return new Kit(gear);
	}

	private static Dps wearing(Service service, Req req, List<String> names) {
		return service.calcR(new Calc(), req, set(names));
	}

	private static double dtps(Service.ComputeContext ctx, Monster moon, Dps r) {
		return Service.inc(moon, r.getLoadout(), ctx, Style.MELEE).tot;
	}

	private static final List<String> ARMOUR = List.of("blood moon helm", "fire cape", "amulet of torture",
		"holy blessing", "blood moon chestplate", "blood moon tassets", "barrows gloves", "dragon boots",
		"berserker ring (i)");

	private static List<String> with(String... more) {
		List<String> names = new ArrayList<>(ARMOUR);
		names.addAll(List.of(more));
		return names;
	}

	@Test
	@DisplayName("after a re-wear the bench holds what was worn or carried and is not worn now, the carried spec weapon first")
	void rebenchKeepsTheTrip() {
		Kit was = set(List.of("abyssal tentacle", "blood moon chestplate"));
		Kit now = set(List.of("abyssal tentacle", "bandos chestplate"));
		assertEquals(List.of(item("dragon scimitar"), item("blood moon chestplate")),
			Service.rebench(List.of(item("dragon scimitar"), item("bandos chestplate")), was, now));
	}

	@Test
	@DisplayName("the re-wear keeps the shown set when nothing is carried, and wears only what it is given")
	void reWearWearsOnlyWhatItIsGiven() {
		Service service = new Service(data);
		try {
			Service.ComputeContext ctx = tanky(service);
			Monster moon = moons.get(0);
			Req req = melee(service, ctx, moon);
			Calc calc = new Calc();
			Dps shown = wearing(service, req, with("abyssal tentacle"));
			assertSame(shown, service.modeWear(calc, ctx, req, moon, shown, List.of(), null));
			List<Gear> carried = List.of(item("bandos chestplate"), item("bandos tassets"), item("dragon defender"),
				item("amulet of fury"));
			Dps worn = service.modeWear(calc, ctx, req, moon, shown, carried, null);
			Set<Integer> given = new HashSet<>(shown.getLoadout().ids());
			carried.forEach(g -> given.add(g.id));
			assertTrue(given.containsAll(worn.getLoadout().ids()), "nothing it was not given");
			assertTrue(dtps(ctx, moon, worn) < dtps(ctx, moon, shown) - 0.01,
				"Tanky cut the damage taken: " + dtps(ctx, moon, worn) + " vs " + dtps(ctx, moon, shown));
			assertTrue(worn.getLoadout().ids().contains(item("dragon defender").id), "the defender is worn");
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("the re-wear reaches the member's sweep pick when the shown set and the carry cover it, never a piece it was not given")
	void reWearTakesTheCoveredPick() {
		Service service = new Service(data);
		try {
			Service.ComputeContext ctx = tanky(service);
			Monster moon = moons.get(1);
			Req req = melee(service, ctx, moon);
			Calc calc = new Calc();
			Dps shown = wearing(service, req, with("dual macuahuitl"));
			Dps pick = wearing(service, req, with("abyssal tentacle", "dragon defender"));
			assertTrue(dtps(ctx, moon, pick) < dtps(ctx, moon, shown) - 0.01, "the pick is the safer set");
			Dps covered = service.modeWear(calc, ctx, req, moon, shown,
				List.of(item("abyssal tentacle"), item("dragon defender")), pick);
			assertEquals(pick.getLoadout().ids(), covered.getLoadout().ids());
			Dps uncovered = service.modeWear(calc, ctx, req, moon, shown, List.of(item("abyssal tentacle")), pick);
			assertFalse(uncovered.getLoadout().ids().contains(item("dragon defender").id), "the defender was not carried");
		} finally {
			service.shutdown();
		}
	}
}
