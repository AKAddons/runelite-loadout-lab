package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.Collections;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * God Wars Dungeon protection (ISS-01M5G9KPDQWBR014Z10F79B29T): followers
 * leave you alone while you WEAR an item of their god; each general's walk
 * needs the wiki route table's gods (corner + encampment), worn when it
 * costs no DPS, otherwise carried - Andrew 2026-10-21.
 */
class GwdProtectionTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Monster mob(String name) {
		return data.searchMonsters(name, 1).get(0);
	}

	private static int id(String nameLower) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + nameLower)).getId();
	}

	private static OwnedItems owned(String... names) {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : names) {
			owned.put(id(name), 1);
		}
		return new OwnedItems(data.canonicalizeOwned(owned), true);
	}

	@Test
	@DisplayName("each general's route needs the wiki table's gods; bodyguards share their general's")
	void routeGods() {
		assertEquals(List.of("armadyl", "bandos", "zamorak"), GwdProtection.godsFor(mob("general graardor")));
		assertEquals(List.of("armadyl", "bandos", "saradomin", "zamorak"), GwdProtection.godsFor(mob("kree'arra")));
		assertEquals(List.of("armadyl", "saradomin", "zamorak"), GwdProtection.godsFor(mob("commander zilyana")));
		assertEquals(List.of("bandos", "saradomin", "zamorak"), GwdProtection.godsFor(mob("k'ril tsutsaroth")));
		assertEquals(GwdProtection.godsFor(mob("general graardor")),
			GwdProtection.godsFor(mob("sergeant strongstack")));
		assertTrue(GwdProtection.godsFor(mob("vorkath")).isEmpty(), "nowhere near GWD");
	}

	@Test
	@DisplayName("an item's gods come from the wiki table; the Staff of light counts twice")
	void itemGods() {
		Gear light = data.getGear(id("staff of light"));
		assertEquals(Set.of("saradomin", "zamorak"), GwdProtection.godsOf(light));
		assertEquals(Set.of("bandos"), GwdProtection.godsOf(data.getGear(id("war blessing"))));
		assertTrue(GwdProtection.godsOf(data.getGear(id("abyssal whip"))).isEmpty());
	}

	@Test
	@DisplayName("a god item is worn only when it costs no DPS; the rest are carried")
	void wornWhenFreeElseCarried() {
		Monster graardor = mob("general graardor");
		OwnedItems owned = owned("abyssal whip", "dragon defender", "fighter torso", "dragon platelegs",
			"barrows gloves", "dragon boots", "amulet of fury", "berserker ring", "fire cape", "helm of neitiznot",
			"war blessing", "unholy blessing", "honourable blessing", "zamorak stole", "armadyl stole");
		OptimizationRequest request = TestRequests.of(graardor, Style.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL), null, 0,
			CandidateMode.OWNED_ONLY, true, false, owned, 1);
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		DpsResult plain = optimizer.fillDpsNeutralSlots(data, request, optimizer.optimize(data, request).get(0));
		List<String> gods = GwdProtection.godsFor(graardor);
		DpsResult worn = optimizer.wearGodItems(data, request, plain, gods);
		assertEquals(plain.getDps(), worn.getDps(), 1e-9, "protection never costs DPS");
		Set<String> covered = GwdProtection.coveredGods(worn.getLoadout());
		assertFalse(covered.isEmpty(), "a blessing in the free ammo slot covers one god");
		List<Gear> carried = optimizer.carriedGodItems(data, request, worn.getLoadout(), gods);
		Set<String> all = new HashSet<>(covered);
		for (Gear item : carried) {
			Set<String> adds = GwdProtection.godsOf(item);
			assertFalse(adds.isEmpty(), item.getName() + " protects nothing");
			all.addAll(adds);
		}
		assertTrue(all.containsAll(gods), "every route god covered: " + all);
		assertTrue(carried.size() < gods.size() + 1 && carried.size() <= gods.size() - covered.size() + 1);
		for (Gear item : carried) {
			assertTrue(owned.owns(item.getId()), "owned protection is preferred: " + item.getName());
		}
	}

	@Test
	@DisplayName("the card's set covers every route god between what it wears and what it carries")
	void serviceCoversTheRoute() throws Exception {
		Monster kree = mob("kree'arra");
		OwnedItems owned = owned("magic shortbow", "rune arrow", "black d'hide body", "black d'hide chaps",
			"zamorak stole", "saradomin stole", "bandos stole", "armadyl stole", "honourable blessing");
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ComputeParams p = new ComputeParams();
			p.maxSwaps = 6;
			p.gods = List.of("armadyl", "bandos", "saradomin", "zamorak", "zaros");
			service.bestPerStyle(kree, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, owned, 1, p, Collections.emptyMap(), Collections.emptySet(),
				Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			OptimizerService.StyleResult ranged = out.get().get(Style.RANGED);
			Set<String> all = new HashSet<>(GwdProtection.coveredGods(ranged.owned.get(0).getLoadout()));
			for (Gear item : ranged.protection) {
				all.addAll(GwdProtection.godsOf(item));
				assertTrue(ranged.bench.contains(item), "carried protection rides the Inventory");
			}
			assertTrue(all.containsAll(p.gods), "all four + Zaros: " + all);
		} finally {
			service.shutdown();
		}
	}
}
