package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Save to Inventory Setups (ISS-01M5V8P2430MQ61C5QKHHRQR6Z, Andrew 2026-10-25: "a
 * button that creates a new inventory setup under a "Loadout Lab" section"; his
 * picks: worn set plus the trip inventory, named "Mob - Style", a repeat save
 * gets " (2)").
 */
class SaveSetupTest {
	private static Map<String, Object> payload(Map<String, Integer> worn, List<int[]> stacks) {
		return SetupExport.payload("Zulrah - Magic", worn, stacks, new ArrayList<>(), new LinkedHashMap<>(), id -> id == 560);
	}

	@Test
	@DisplayName("the worn set lands in EquipmentInventorySlot order, -1 where nothing is worn")
	void equipmentOrder() {
		Map<String, Integer> gear = new HashMap<>();
		gear.put("head", 10);
		gear.put("weapon", 30);
		gear.put("legs", 70);
		gear.put("ring", 120);
		gear.put("ammo", 130);
		gear.put("shield", 0);
		assertEquals(List.of(10, -1, -1, 30, -1, -1, -1, 70, -1, -1, -1, -1, 120, 130),
			payload(gear, new ArrayList<>()).get("equipmentItemIds"));
	}

	@Test
	@DisplayName("the trip fills 28 slots: a stackable once at its quantity, others a slot each, each id once; section Loadout Lab")
	void inventorySlots() {
		List<int[]> stacks = new ArrayList<>();
		stacks.add(new int[]{1215, 1});
		stacks.add(new int[]{0, 1});
		stacks.add(new int[]{560, 4});
		stacks.add(new int[]{385, 3});
		stacks.add(new int[]{560, 9});
		Map<String, Object> p = payload(new HashMap<>(), stacks);
		List<?> ids = (List<?>) p.get("inventoryItemIds");
		List<?> qty = (List<?>) p.get("inventoryQuantities");
		assertEquals(28, ids.size());
		assertEquals(List.of(1215, 560, 385, 385, 385, -1), ids.subList(0, 6));
		assertEquals(List.of(1, 4, 1, 1, 1, 0), qty.subList(0, 6));
		assertEquals("Loadout Lab", p.get("section"));
		assertEquals("Zulrah - Magic", p.get("setup"));
	}

	@Test
	@DisplayName("utility items ride as additional filtered items, never an inventory slot (ISS-01M5VBB3E4DM65MHHYWB3A8GSP)")
	void additionalItems() {
		List<int[]> stacks = new ArrayList<>();
		stacks.add(new int[]{385, 1});
		Map<String, Object> p = SetupExport.payload("Zulrah - Magic", new HashMap<>(), stacks, List.of(9763, 9790),
			new LinkedHashMap<>(), id -> false);
		assertEquals(List.of(9763, 9790), p.get("additionalItemIds"));
		assertEquals(List.of(385, -1), ((List<?>) p.get("inventoryItemIds")).subList(0, 2));
	}

	private static final class CaptureLink extends CompanionLink {
		Map<String, Object> published;

		@Override
		public void publishPage(Map page) {
			published = page;
		}
	}

	@Test
	@DisplayName("a trip's teleport cape node is marked utility, its boss teleport node is not (\"stuff like the magic cape\")")
	void supplyNodesMarkUtility() throws Exception {
		LoadoutData data = new DataService().load();
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state, (m, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> true);
		engine.setSupplyDefaults(() -> Map.of("teleportCape", "DETECT_BEST", "bossTeleport", "DETECT_BEST"));
		engine.setUnlocks(v -> true);
		MonsterStats mob = data.searchMonsters("zulrah", 1).get(0);
		engine.execute("select", Map.of("id", mob.getId()));
		engine.onResults(mob, Map.of());
		javax.swing.SwingUtilities.invokeAndWait(() -> {
		});
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		Map<String, Object> utility = new HashMap<>();
		for (Object node : (List<?>) entry.get("supplies")) {
			Map<?, ?> n = (Map<?, ?>) node;
			utility.put(String.valueOf(n.get("category")), n.get("utility"));
		}
		assertEquals(Boolean.TRUE, utility.get("teleportCape"), utility.toString());
		assertEquals(Boolean.FALSE, utility.get("bossTeleport"), utility.toString());
	}

	@Test
	@DisplayName("the rune pouch's runes ride in its slots, in order (ISS-01M5VC3V5DCEEDGVNMC37VHJ2K)")
	void runePouch() {
		Map<Integer, Integer> pouch = new LinkedHashMap<>();
		pouch.put(560, 2);
		pouch.put(565, 4);
		List<int[]> stacks = new ArrayList<>();
		stacks.add(new int[]{27281, 1});
		Map<String, Object> p = SetupExport.payload("Zulrah - Magic", new HashMap<>(), stacks, new ArrayList<>(), pouch, id -> false);
		assertEquals(List.of(560, 565), p.get("runePouchItemIds"));
		assertEquals(List.of(2, 4), p.get("runePouchQuantities"));
		assertEquals(List.of(27281, -1), ((List<?>) p.get("inventoryItemIds")).subList(0, 2));
	}

	@Test
	@DisplayName("a trip with no utility casts still names the owned rune pouch, so an autocast set's runes have one")
	void pouchWithoutUtilityCasts() throws Exception {
		LoadoutData data = new DataService().load();
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state, (m, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> id == 27281);
		MonsterStats mob = data.searchMonsters("zulrah", 1).get(0);
		engine.execute("select", Map.of("id", mob.getId()));
		engine.onResults(mob, Map.of());
		javax.swing.SwingUtilities.invokeAndWait(() -> {
		});
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		Map<?, ?> node = (Map<?, ?>) ((List<?>) entry.get("mobs")).get(0);
		assertEquals(List.of(), node.get("utilityRunes"), "no utility casts at Zulrah");
		assertEquals(27281, node.get("castingPouch"));
	}

	@Test
	@DisplayName("more than 28 items stop at 28")
	void inventoryCaps() {
		List<int[]> stacks = new ArrayList<>();
		stacks.add(new int[]{385, 40});
		assertEquals(28, ((List<?>) payload(new HashMap<>(), stacks).get("inventoryItemIds")).stream()
			.filter(id -> Integer.valueOf(385).equals(id)).count());
	}

	@Test
	@DisplayName("with Inventory Setups ready the save command hands the card's payload over; not ready, nothing is sent")
	void commandGate() {
		LoadoutData data = new DataService().load();
		List<Map> sent = new ArrayList<>();
		for (boolean ready : new boolean[]{true, false}) {
			PageState state = new PageState();
			CommandEngine engine = new CommandEngine(data, state, (m, p, onDone) -> {
			}, new CompanionLink());
			engine.setSetupLink(() -> ready, sent::add);
			MonsterStats mob = data.searchMonsters("zulrah", 1).get(0);
			engine.execute("select", Map.of("id", mob.getId()));
			Map<String, Object> args = new HashMap<>();
			args.put("setup", "Zulrah - Magic");
			assertEquals(ready, engine.execute("save-setup", args));
			assertEquals(ready, Boolean.TRUE.equals(state.paramsNode().get("setupsAvailable")));
		}
		assertEquals(1, sent.size());
		assertEquals("Zulrah - Magic", sent.get(0).get("setup"));
	}
}
