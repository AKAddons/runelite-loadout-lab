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
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.optimizer.OptimizerService.OptimizeMode;
import com.loadoutlab.optimizer.OptimizerService.StyleResult;
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

import static com.loadoutlab.optimizer.OptimizerService.OptimizeMode.*;
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
	static final PlayerLevels LV = new PlayerLevels(80, 80, 80, 75, 75, 77, 85);

	private static LoadoutData data;
	private static OwnedItems owned;
	private static List<Monster> moons;
	private static final Map<OptimizeMode, OptimizerService.RosterResult> runs = new EnumMap<>(OptimizeMode.class);

	@BeforeAll
	static void searchTheMoons() throws Exception {
		data = new DataService().load();
		moons = List.of(data.searchMonsters("blood moon", 1).get(0), data.searchMonsters("blue moon", 1).get(0),
			data.monsters.stream().filter(m -> m.name.equals("Eclipse Moon") && "Regular".equals(m.version))
				.findFirst().get());
		Map<Integer, Integer> bank = new HashMap<>();
		for (GearSlot slot : GearSlot.values()) {
			for (Gear g : data.getGearItems(slot)) {
				if (BANK.contains(g.nameLower)) bank.put(g.id, 1);
			}
		}
		owned = new OwnedItems(bank, true);
		// One service for the three searches, as in game: a tab flip reuses pass A.
		OptimizerService service = new OptimizerService(data);
		try {
			for (OptimizeMode mode : OptimizeMode.values()) {
				ComputeParams p = new ComputeParams();
				p.optimizeMode = mode.name();
				CountDownLatch done = new CountDownLatch(1);
				AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
				service.bestPerStyleAcross(moons, LV, LV, PrayerUnlocks.ALL, RequirementProfile.MAXED, owned, 1, p,
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

	private static StyleResult card(OptimizeMode mode, int mob, Style style) {
		return runs.get(mode).perMob.get(mob).get(style);
	}

	private static double dps(StyleResult card) {
		return card.owned.get(0).getDps();
	}

	private static double dtps(StyleResult card) {
		return card.incoming.totalDps;
	}

	private static Set<Integer> ids(StyleResult card) {
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
			StyleResult max = card(MAX_DPS, j, Style.MELEE);
			StyleResult tanky = card(TANKY, j, Style.MELEE);
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
			StyleResult max = card(MAX_DPS, j, Style.MELEE);
			StyleResult balanced = card(BALANCED, j, Style.MELEE);
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
				StyleResult max = card(MAX_DPS, j, style);
				if (max == null || max.owned.isEmpty()) continue;
				assertNull(max.modeTrade, "Max DPS makes no trade");
				for (OptimizeMode mode : List.of(BALANCED, TANKY)) {
					StyleResult c = card(mode, j, style);
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
	private static OptimizerService.ComputeContext tanky(OptimizerService service) {
		ComputeParams p = new ComputeParams();
		p.optimizeMode = TANKY.name();
		return service.buildContext(LV, LV, PrayerUnlocks.ALL, RequirementProfile.MAXED, owned, 1, p,
			Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.<Integer>emptySet());
	}

	private static OptimizationRequest melee(OptimizerService service, OptimizerService.ComputeContext ctx, Monster moon) {
		return service.ownedRequestFor(ctx, moon, Style.MELEE,
			OptimizerService.stylePlan(ctx, Style.MELEE, null).levels, null);
	}

	private static Gear item(String name) {
		for (GearSlot slot : GearSlot.values()) {
			for (Gear g : data.getGearItems(slot)) {
				if (g.nameLower.equals(name)) return g;
			}
		}
		throw new AssertionError("no " + name);
	}

	private static Loadout set(List<String> names) {
		Map<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
		for (String name : names) {
			Gear g = item(name);
			gear.put(g.slot, g);
		}
		return new Loadout(gear);
	}

	private static DpsResult wearing(OptimizerService service, OptimizationRequest req, List<String> names) {
		return service.calcRespecting(new DpsCalculator(), req, set(names));
	}

	private static double dtps(OptimizerService.ComputeContext ctx, Monster moon, DpsResult r) {
		return OptimizerService.incomingFor(moon, r.getLoadout(), ctx, Style.MELEE).totalDps;
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
		Loadout was = set(List.of("abyssal tentacle", "blood moon chestplate"));
		Loadout now = set(List.of("abyssal tentacle", "bandos chestplate"));
		assertEquals(List.of(item("dragon scimitar"), item("blood moon chestplate")),
			OptimizerService.rebench(List.of(item("dragon scimitar"), item("bandos chestplate")), was, now));
	}

	@Test
	@DisplayName("the re-wear keeps the shown set when nothing is carried, and wears only what it is given")
	void reWearWearsOnlyWhatItIsGiven() {
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.ComputeContext ctx = tanky(service);
			Monster moon = moons.get(0);
			OptimizationRequest req = melee(service, ctx, moon);
			DpsCalculator calc = new DpsCalculator();
			DpsResult shown = wearing(service, req, with("abyssal tentacle"));
			assertSame(shown, service.modeWear(calc, ctx, req, moon, shown, List.of(), null));
			List<Gear> carried = List.of(item("bandos chestplate"), item("bandos tassets"), item("dragon defender"),
				item("amulet of fury"));
			DpsResult worn = service.modeWear(calc, ctx, req, moon, shown, carried, null);
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
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.ComputeContext ctx = tanky(service);
			Monster moon = moons.get(1);
			OptimizationRequest req = melee(service, ctx, moon);
			DpsCalculator calc = new DpsCalculator();
			DpsResult shown = wearing(service, req, with("dual macuahuitl"));
			DpsResult pick = wearing(service, req, with("abyssal tentacle", "dragon defender"));
			assertTrue(dtps(ctx, moon, pick) < dtps(ctx, moon, shown) - 0.01, "the pick is the safer set");
			DpsResult covered = service.modeWear(calc, ctx, req, moon, shown,
				List.of(item("abyssal tentacle"), item("dragon defender")), pick);
			assertEquals(pick.getLoadout().ids(), covered.getLoadout().ids());
			DpsResult uncovered = service.modeWear(calc, ctx, req, moon, shown, List.of(item("abyssal tentacle")), pick);
			assertFalse(uncovered.getLoadout().ids().contains(item("dragon defender").id), "the defender was not carried");
		} finally {
			service.shutdown();
		}
	}
}
