package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Charged versions only (ISS-01M5QQV2TGQ5X730T0JJ75S1RD, Andrew 2026-10-24:
 * "let's only consider charged versions of weapons, don't think about eye
 * of ayak uncharged or obligator uncharged"): an uncharged, inactive,
 * broken or locked weapon is a variant of its charged twin - never a pool
 * candidate, a swap or a spec pick - and owning it counts as owning the
 * charged one.
 */
class UnchargedStepTest {
	private static final int OBLIGATOR_CHARGED = 34715;
	private static final int OBLIGATOR_UNCHARGED = 34717;
	private static final int EYE_OF_AYAK_CHARGED = 31113;
	private static final int EYE_OF_AYAK_UNCHARGED = 31115;
	private static LoadoutData data;
	private static Monster spectre;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		spectre = data.searchMonsters("aberrant spectre", 1).get(0);
	}

	private static Gear item(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	@Test
	@DisplayName("an uncharged weapon is a variant of its charged twin, and owning it is owning the charged one")
	void unchargedIsTheChargedTwinsVariant() {
		assertTrue(data.isVariant(OBLIGATOR_UNCHARGED), "Obligator (Uncharged) stands behind the charged one");
		assertTrue(data.isVariant(EYE_OF_AYAK_UNCHARGED), "Eye of Ayak (Uncharged) too");
		assertFalse(data.isVariant(OBLIGATOR_CHARGED));
		assertFalse(data.isVariant(EYE_OF_AYAK_CHARGED));
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(OBLIGATOR_UNCHARGED, 1);
		OwnedItems owned = new OwnedItems(data.canonicalizeOwned(bank), true);
		assertTrue(owned.owns(OBLIGATOR_CHARGED), "the uncharged one in the bank is the Obligator you charge before the trip");
	}

	@Test
	@DisplayName("the Aberrant spectre's weapon swaps hold the charged Obligator and never the uncharged one")
	void swapsAreChargedOnly() {
		Map<Integer, Integer> owned = new HashMap<>();
		EnumMap<GearSlot, Gear> g = new EnumMap<>(GearSlot.class);
		for (String name : new String[]{"neitiznot faceguard", "fire cape", "amulet of torture", "rada's blessing 4",
			"noxious halberd", "bandos chestplate", "ahrim's robeskirt", "ferocious gloves", "aranea boots", "berserker ring (i)"}) {
			Gear it = item(name);
			owned.put(it.getId(), 1);
			g.put(it.getSlot(), it);
		}
		OptimizationRequest request = new OptimizationRequest(spectre, Style.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 3);
		DpsResult shown = new DpsCalculator().calculate(request, new Loadout(g));
		List<Integer> swaps = new LoadoutOptimizer().slotSwaps(data, request, shown, GearSlot.WEAPON).stream()
			.map(r -> r.getLoadout().getWeapon().getId()).collect(Collectors.toList());
		assertTrue(swaps.contains(OBLIGATOR_CHARGED), "the charged Obligator is a swap");
		assertFalse(swaps.contains(OBLIGATOR_UNCHARGED), "the uncharged one is never a step");
		for (DpsResult swap : new LoadoutOptimizer().slotSwaps(data, request, shown, GearSlot.WEAPON)) {
			assertFalse(swap.getLoadout().getWeapon().getVersionLower().contains("uncharged"),
				"no uncharged weapon anywhere on the strip: " + swap.getLoadout().getWeapon().getName());
		}
	}
}
