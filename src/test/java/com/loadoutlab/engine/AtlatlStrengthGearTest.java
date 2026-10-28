package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eclipse atlatl's damage comes from Strength and worn MELEE strength,
 * so a ranged search that holds one must let strength gear into its slot
 * pools (ISS-01M5PTESXPE03VZP9JB8X93KRF, Kendyy on Discord 2026-10-23: "lab
 * is suggesting rune knives for demonic gorillas. eclipe set + atlatl has
 * better dps calc" ... "atlatl gets its damage from melee strength but does
 * range damage so maybe something to do with that, the calc might be trying
 * to match it with range boosting gear").
 */
class AtlatlStrengthGearTest {
	private static final int ECLIPSE_ATLATL = 29000;
	private static final int ATLATL_DART = 28991;
	private static LoadoutData data;
	private static MonsterStats gorilla;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		gorilla = data.searchMonsters("demonic gorilla", 1).get(0);
	}

	private static GearItem item(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	private static GearItem item(String name, String version) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && version.equalsIgnoreCase(g.getVersion()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name + " " + version));
	}

	/** Kendyy's shape of bank: the atlatl and its set, knives, crossbows,
	 * darts, and both ranged and melee-strength jewellery and armour. */
	private static OptimizationRequest request() {
		String[] names = {"eclipse atlatl", "atlatl dart", "eclipse moon helm", "eclipse moon chestplate", "eclipse moon tassets",
			"rune knife", "hunters' sunlight crossbow", "sunlight antler bolts", "steel dart", "amulet of torture",
			"necklace of anguish", "ferocious gloves", "barrows gloves", "ultor ring", "archers ring (i)", "infernal cape",
			"ava's assembler", "primordial boots", "pegasian boots", "armadyl chestplate", "armadyl chainskirt",
			"armadyl helmet", "bandos chestplate", "bandos tassets", "neitiznot faceguard", "berserker ring (i)",
			"fire cape", "rada's blessing 4", "dragon dart", "magic shortbow (i)", "rune arrow", "adamant dart",
			"rune crossbow", "ruby dragon bolts (e)", "diamond dragon bolts (e)", "dragon knife", "karil's crossbow",
			"bolt rack", "dragon crossbow", "dragon hunter crossbow", "dragon arrow", "amethyst arrow", "toxic blowpipe",
			"black d'hide body", "black d'hide chaps", "black d'hide vambraces", "snakeskin boots", "slayer helmet (i)",
			"torva full helm", "torva platebody", "torva platelegs", "amulet of fury", "ring of suffering (i)"};
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : names) {
			owned.put(item(name).getId(), 1);
		}
		return new OptimizationRequest(gorilla, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 3);
	}

	private static boolean holds(List<GearItem> pool, String name) {
		return pool.stream().anyMatch(g -> g != null && g.getNameLower().equals(name));
	}

	@Test
	@DisplayName("with an eclipse atlatl in the bank the ranged slot pools admit the strength pieces: torture, ferocious gloves, ultor ring, infernal cape, primordial boots")
	void strengthGearReachesTheRangedPools() {
		LoadoutOptimizer.CandidatePools pools = new LoadoutOptimizer().preparePools(data, request());
		assertTrue(holds(pools.slotCandidates.get(GearSlot.NECK), "amulet of torture"), "torture");
		assertTrue(holds(pools.slotCandidates.get(GearSlot.HANDS), "ferocious gloves"), "ferocious gloves");
		assertTrue(holds(pools.slotCandidates.get(GearSlot.RING), "ultor ring"), "ultor ring");
		assertTrue(holds(pools.slotCandidates.get(GearSlot.CAPE), "infernal cape"), "infernal cape");
		assertTrue(holds(pools.slotCandidates.get(GearSlot.FEET), "primordial boots"), "primordial boots");
		assertTrue(holds(pools.slotCandidates.get(GearSlot.NECK), "necklace of anguish"), "the ranged pieces stay too");
	}

	@Test
	@DisplayName("the atlatl wins the demonic gorilla with its darts and strength gear - at least the dps of the eclipse set with torture, ferocious gloves, ultor, infernal cape and primordials")
	void atlatlWinsWithStrengthGear() {
		OptimizationRequest request = request();
		EnumMap<GearSlot, GearItem> g = new EnumMap<>(GearSlot.class);
		g.put(GearSlot.WEAPON, item("eclipse atlatl"));
		g.put(GearSlot.AMMO, item("atlatl dart"));
		g.put(GearSlot.HEAD, item("eclipse moon helm", "New"));
		g.put(GearSlot.BODY, item("eclipse moon chestplate", "New"));
		g.put(GearSlot.LEGS, item("eclipse moon tassets", "New"));
		g.put(GearSlot.NECK, item("amulet of torture"));
		g.put(GearSlot.HANDS, item("ferocious gloves"));
		g.put(GearSlot.RING, item("ultor ring"));
		g.put(GearSlot.CAPE, item("infernal cape"));
		g.put(GearSlot.FEET, item("primordial boots"));
		DpsResult handBuilt = new DpsCalculator().calculate(request, new Loadout(g));

		List<DpsResult> best = new LoadoutOptimizer().optimize(data, request);
		assertFalse(best.isEmpty());
		DpsResult winner = best.get(0);
		assertEquals(ECLIPSE_ATLATL, winner.getLoadout().getWeapon().getId(),
			"the atlatl, not " + winner.getLoadout().getWeapon().getName() + " at " + winner.getDps());
		assertEquals(ATLATL_DART, winner.getLoadout().get(GearSlot.AMMO).getId(), "its darts in the ammo slot");
		assertTrue(winner.getDps() >= handBuilt.getDps() - 1e-6,
			"the search reaches the strength set: " + winner.getDps() + " vs hand-built " + handBuilt.getDps());
	}

	@Test
	@DisplayName("atlatl darts are the atlatl's ammo; a blessing or an empty slot is not")
	void dartsAreTheAmmo() {
		GearItem atlatl = item("eclipse atlatl");
		assertTrue(RangedAmmo.compatible(item("atlatl dart"), atlatl));
		assertFalse(RangedAmmo.compatible(item("rada's blessing 4"), atlatl));
		assertFalse(RangedAmmo.compatible(null, atlatl));
	}
}
