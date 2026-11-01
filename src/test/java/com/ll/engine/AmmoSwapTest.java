package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-11-01 (Andrew: "it's not coming up with a result for
 * the fractured archive"): the client log showed every roster compute for
 * the Archive Guardian dying in the group ladder on a swap whose ammo
 * slot was empty. slotSwaps for the AMMO slot finished each trial with
 * the WORN ammo, so the candidate was overwritten - by nothing when the
 * member wore none. The contract: a swap for a slot wears the candidate
 * in that slot.
 */
class AmmoSwapTest {
	private static Tables data;
	private static Monster spectre;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		spectre = data.searchMonsters("aberrant spectre", 1).get(0);
	}

	private static Gear item(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getLname().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	private static Dps rangedKit(boolean withAmmo) {
		EnumMap<Slot, Gear> g = new EnumMap<>(Slot.class);
		for (String name : new String[]{"twisted bow", "masori mask (f)", "masori body (f)", "masori chaps (f)",
			"necklace of anguish", "ava's assembler", "zaryte vambraces", "pegasian boots", "venator ring"}) {
			Gear it = item(name);
			g.put(it.getSlot(), it);
		}
		if (withAmmo) g.put(Slot.AMMO, item("dragon arrow"));
		return new Calc().calculate(request(), new Kit(g));
	}

	private static Req request() {
		return new Req(spectre, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED, Prayers.ALL), null, 0, CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 3);
	}

	@Test
	@DisplayName("an ammo swap wears the candidate arrow, not the one the set already wore")
	void ammoSwapsWearTheCandidate() {
		Dps shown = rangedKit(true);
		int worn = shown.getLoadout().get(Slot.AMMO).getId();
		List<Dps> swaps = new LoadoutOptimizer().slotSwaps(data, request(), shown, Slot.AMMO);
		assertFalse(swaps.isEmpty(), "arrows other than the dragon arrow are swaps");
		for (Dps swap : swaps) {
			Gear ammo = swap.getLoadout().get(Slot.AMMO);
			assertNotNull(ammo, "a swap for the ammo slot wears its ammo");
			assertNotEquals(worn, ammo.getId(), "the worn arrow is no swap of itself");
		}
		Set<Integer> ids = swaps.stream().map(s -> s.getLoadout().get(Slot.AMMO).getId()).collect(Collectors.toSet());
		assertEquals(swaps.size(), ids.size(), "each swap is a different arrow");
		assertTrue(ids.contains(item("amethyst arrow").getId()), "the amethyst arrow is among them");
	}

	@Test
	@DisplayName("a set wearing no ammo still gets ammo swaps, each wearing its arrow")
	void emptyAmmoSlotStillSwaps() {
		Dps shown = rangedKit(false);
		assertNull(shown.getLoadout().get(Slot.AMMO), "the set wears no ammo");
		List<Dps> swaps = new LoadoutOptimizer().slotSwaps(data, request(), shown, Slot.AMMO);
		assertFalse(swaps.isEmpty(), "the arrows are swaps for an empty quiver");
		for (Dps swap : swaps) assertNotNull(swap.getLoadout().get(Slot.AMMO), "no swap leaves the slot empty");
	}
}
