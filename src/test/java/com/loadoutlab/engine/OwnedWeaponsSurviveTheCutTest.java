package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Your own weapons never fall off the weapon pool's top-N stat cut
 * (ISS-01M5PHE6WD81W8542RW71EXSHC): the smoke battlestaff's worth is a rule
 * (+10% on standard spells), not a stat, so a bank of many staves pushed it out
 * of the kit search while the swaps strip - which prices every eligible item -
 * still rated it, and the two disagreed in front of Andrew ("i OWN A SMOKE
 * BATTLESTAFF").
 */
class OwnedWeaponsSurviveTheCutTest {
	/** Sixteen owned staves. The four stat-twins (toxic staff of the dead, staff of
	 * balance, eldritch and volatile nightmare staff) fold into their identical
	 * siblings by design - dedupe keeps one of each interchangeable stat block -
	 * so the pool must hold every DISTINCT staff, the rule-valued smoke staves first. */
	private static final String[] STAVES = {"smoke battlestaff", "mystic smoke staff", "purging staff", "kodai wand",
		"master wand", "ancient staff", "ahrim's staff", "toxic staff of the dead", "staff of light", "staff of balance",
		"nightmare staff", "eldritch nightmare staff", "volatile nightmare staff", "3rd age wand", "teacher wand",
		"toktz-mej-tal"};
	private static final Set<String> STAT_TWINS = Set.of("toxic staff of the dead", "staff of balance",
		"eldritch nightmare staff", "volatile nightmare staff");

	@Test
	@DisplayName("with sixteen staves in the bank every distinct owned magic weapon is in the pool - both smoke staves included")
	void ownedWeaponsStayInThePool() {
		LoadoutData data = new DataService().load();
		MonsterStats giant = data.searchMonsters("fire giant", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		List<GearItem> staves = new ArrayList<>();
		for (String name : STAVES) {
			GearItem staff = data.getGearItems(GearSlot.WEAPON).stream()
				.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
				.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
			owned.put(staff.getId(), 1);
			staves.add(staff);
		}
		for (int rune : new int[]{554, 555, 556, 557, 560, 565, 566, 21880}) {
			owned.put(rune, 10000);
		}
		OptimizationRequest request = new OptimizationRequest(giant, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 3);

		Set<Integer> pool = new LoadoutOptimizer().preparePools(data, request).weapons.stream()
			.map(GearItem::getId).collect(Collectors.toSet());
		List<String> missing = staves.stream()
			.filter(s -> !STAT_TWINS.contains(s.getNameLower()) && !pool.contains(s.getId()))
			.map(GearItem::getName).collect(Collectors.toList());
		assertTrue(missing.isEmpty(), "owned weapons cut from the pool: " + missing);
	}
}
