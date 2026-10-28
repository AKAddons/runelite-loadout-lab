package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.RequiredGear;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slayer items, required by default and shown even unowned
 * (ISS-01M5JJ0Z4M988DR0M0ZGJHD139, Andrew 2026-10-22: "for slayer monsters it should suggest
 * the slayer item needed", "for anything in the karuulm slayer dungeon we
 * should check the status of the elite kebos/kourend diary and make boot
 * suggestions based on that", "we should pin/require those by default and
 * show them as necessary even when the player doesn't own those items").
 */
class SlayerRequirementsTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static MonsterStats mob(String name) {
		return data.searchMonsters(name, 1).get(0);
	}

	private static int id(String nameLower) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + nameLower)).getId();
	}

	private static OptimizerService.StyleResult melee(MonsterStats monster, boolean kourendElite, String... owned)
		throws Exception {
		Map<Integer, Integer> bank = new HashMap<>();
		for (String name : owned) {
			bank.put(id(name), 1);
		}
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ComputeParams p = new ComputeParams();
			p.specWeapon = false;
			p.kourendElite = kourendElite;
			service.bestPerStyle(monster, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(data.canonicalizeOwned(bank), true), 1, p,
				Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			return out.get().get(CombatStyle.MELEE);
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("a required item you do not own is still worn on Yours - the cave horror's witchwood icon")
	void unownedRequiredIsWorn() throws Exception {
		OptimizerService.StyleResult melee = melee(mob("cave horror"), false,
			"abyssal whip", "amulet of torture", "fighter torso", "dragon defender");
		assertEquals("Witchwood icon", melee.owned.get(0).getLoadout().get(GearSlot.NECK).getName(),
			"needed even unowned - not the torture");
	}

	@Test
	@DisplayName("Karuulm needs boots of stone (or brimstone, granite) until the elite Kourend & Kebos diary")
	void karuulmBoots() throws Exception {
		MonsterStats drake = mob("drake");
		assertTrue(RequiredGear.requiredIds(drake, data, Set.of()).containsKey(GearSlot.FEET));
		assertFalse(RequiredGear.requiredIds(drake, data, Set.of(RequiredGear.KOUREND_ELITE)).containsKey(GearSlot.FEET),
			"the elite diary waives them");
		OptimizerService.StyleResult without = melee(drake, false, "abyssal whip", "dragon boots", "fighter torso");
		assertEquals("Boots of stone", without.owned.get(0).getLoadout().get(GearSlot.FEET).getName(),
			"unowned, the first acceptable pair is worn");
		OptimizerService.StyleResult with = melee(drake, true, "abyssal whip", "dragon boots", "fighter torso");
		assertEquals("Dragon boots", with.owned.get(0).getLoadout().get(GearSlot.FEET).getName(),
			"the diary frees the slot");
	}

	@Test
	@DisplayName("the finishing items ride as trip supplies, owned or not: hammer, fungicide, salt, ice cooler")
	void finishingItems() {
		Map<String, Integer> expect = Map.of("gargoyle", 21742, "zygomite", 7421, "rockslug", 4161,
			"desert lizard", 6696);
		for (Map.Entry<String, Integer> e : expect.entrySet()) {
			List<Map> picks = RequiredGear.carriedPicks(mob(e.getKey()), item -> false);
			assertEquals(1, picks.size(), e.getKey());
			assertEquals(e.getValue(), picks.get(0).get("id"), e.getKey() + ": the best when you own none");
			assertTrue(String.valueOf(picks.get(0).get("why")).contains("not owned"), e.getKey());
		}
		List<Map> owned = RequiredGear.carriedPicks(mob("gargoyle"), item -> item == 4162);
		assertEquals(4162, owned.get(0).get("id"), "the rock hammer you own beats the granite hammer you don't");
		assertFalse(String.valueOf(owned.get(0).get("why")).contains("not owned"));
		assertTrue(RequiredGear.carriedPicks(mob("abyssal demon"), item -> false).isEmpty());
	}

	@Test
	@DisplayName("the existing worn rules still hold: killerwatts insulated boots, harpies the lit lantern")
	void existingRules() {
		Set<String> killerwatt = RequiredGear.requiredIds(mob("killerwatt"), data, Set.of()).get(GearSlot.FEET)
			.stream().map(i -> data.getGear(i).getName()).collect(Collectors.toSet());
		assertTrue(killerwatt.contains("Insulated boots"), killerwatt.toString());
		assertTrue(RequiredGear.requiredIds(mob("harpie bug swarm"), data, Set.of()).containsKey(GearSlot.SHIELD));
	}
}
