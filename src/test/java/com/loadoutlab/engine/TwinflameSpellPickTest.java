package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The twinflame staff's 40% second hit applies to Bolt/Blast/Wave, not
 * Strike/Surge - so spell DOMINANCE depends on the weapon. The elemental
 * prune used to keep only the top max-hit spell per class, which dropped
 * every doubled tier before evaluation: a pinned twinflame was told to
 * autocast Wind Surge while Wind Wave computed ~18% more dps (field
 * report 2026-08-06, reproduced at Guthan).
 */
class TwinflameSpellPickTest
{
	private static LoadoutData data;
	private static MonsterStats guthan;
	private static GearItem staff;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		guthan = data.searchMonsters("guthan", 1).get(0);
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().contains("twinflame") && g.isStandardGear())
			{
				staff = g;
			}
		}
		assertNotNull(staff, "test premise: the twinflame staff exists");
	}

	@Test
	@DisplayName("a pinned twinflame autocasts a doubled tier, not the pruned-to Surge")
	void pinnedTwinflamePicksADoubledTier()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(staff.getId(), 1);
		Map<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
		pins.put(GearSlot.WEAPON, staff.getId());
		OptimizationRequest request = TestRequests.of(guthan,
			CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), 1)
			.withPinnedItems(pins);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, request);
		assertFalse(out.isEmpty());
		String spell = out.get(0).getSpellName();
		assertNotNull(spell);
		assertTrue(spell.endsWith("Wave") || spell.endsWith("Blast") || spell.endsWith("Bolt"),
			"the doubled tier must win under a twinflame, picked: " + spell);

		// And the pick genuinely out-damages the surge it used to recommend.
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, staff);
		SpellStats surge = data.getSpells().stream()
			.filter(s -> "Wind Surge".equals(s.getName())).findFirst().orElseThrow();
		DpsResult surged = new DpsCalculator().calculate(request.withSpell(surge),
			new Loadout(gear));
		assertTrue(out.get(0).getDps() > surged.getDps(),
			"picked " + spell + " at " + out.get(0).getDps()
				+ " must beat Wind Surge at " + surged.getDps());
	}

	@Test
	@DisplayName("without a twinflame the surge tier still wins - no over-correction")
	void plainStaffStillPicksSurge()
	{
		OptimizationRequest request = TestRequests.of(guthan,
			CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, request);
		assertFalse(out.isEmpty());
		String spell = out.get(0).getSpellName();
		// Powered staves carry their own builtin; only assert when the pick
		// is a plain elemental autocast.
		if (spell != null && DpsCalculator.isPlainElemental(
			data.getSpells().stream().filter(s -> spell.equals(s.getName()))
				.findFirst().orElse(null)))
		{
			assertFalse(spell.endsWith("Wave") || spell.endsWith("Blast"),
				"with no twinflame the doubled tiers have no edge, picked: " + spell);
		}
	}
}
