package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dragonfire honesty (2026-07 player audit A3.2): when the shield-required
 * constraint CANNOT be satisfied (no protective shield in the pool), the
 * beam used to half-apply it - every two-handed weapon banned AND the
 * returned set still had an empty shield, with pure-damage items (Ultor,
 * Avernic) unrecoverable by the neutral fill. A maxed player's Vorkath
 * magic card showed a 2.71-dps Sanguinesti while their Shadow computed
 * ~7.5. Now the constraint is all-or-nothing: unsatisfiable means the
 * UNCONSTRAINED set comes back flagged antifire-assumed, so the panel can
 * say "needs a super antifire" instead of quietly recommending nonsense.
 */
class DragonfireHonestyTest
{
	private static LoadoutData data;
	private static MonsterStats vorkath;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		vorkath = data.searchMonsters("vorkath", 1).get(0);
		assertEquals("Post-quest", vorkath.getVersion(), "test premise: everyday Vorkath");
		assertTrue(DragonfireRules.breathesFire(vorkath));
	}

	private static GearItem byName(String nameLower, String versionLower)
	{
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals(nameLower)
				&& (versionLower == null || versionLower.equals(item.getVersionLower())))
			{
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower);
	}

	private static OptimizationRequest owning(CombatStyle style, GearItem... items)
	{
		Map<Integer, Integer> owned = new HashMap<>();
		for (GearItem item : items)
		{
			owned.put(item.getId(), 1);
		}
		return new OptimizationRequest(vorkath, style, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 3);
	}

	@Test
	@DisplayName("no protective shield owned: the 2h Shadow comes back, flagged antifire-assumed")
	void unsatisfiableConstraintFallsBackHonestly()
	{
		GearItem shadow = byName("tumeken's shadow", "charged");
		List<DpsResult> results = new LoadoutOptimizer().optimize(data, owning(CombatStyle.MAGIC, shadow));
		assertFalse(results.isEmpty(), "the shadow line must not die");
		assertEquals("tumeken's shadow", results.get(0).getLoadout().getWeapon().getNameLower());
		assertTrue(results.get(0).isAntifireAssumed(),
			"the result must confess it assumes a super antifire");
	}

	@Test
	@DisplayName("a protective shield owned: the constraint applies and nothing is assumed")
	void satisfiableConstraintStaysHonest()
	{
		GearItem sang = byName("sanguinesti staff", "charged");
		GearItem antiDragon = byName("anti-dragon shield", null);
		List<DpsResult> results = new LoadoutOptimizer().optimize(
			data, owning(CombatStyle.MAGIC, sang, antiDragon));
		assertFalse(results.isEmpty());
		DpsResult best = results.get(0);
		assertNotNull(best.getLoadout().get(com.loadoutlab.data.GearSlot.SHIELD),
			"protection must be worn");
		assertTrue(DragonfireRules.isProtectiveShield(
			best.getLoadout().get(com.loadoutlab.data.GearSlot.SHIELD)));
		assertFalse(best.isAntifireAssumed());
	}

	@Test
	@DisplayName("an explicit antifire potion is the user's own assumption - not flagged")
	void explicitPotionIsNotFlagged()
	{
		GearItem shadow = byName("tumeken's shadow", "charged");
		List<DpsResult> results = new LoadoutOptimizer().optimize(
			data, owning(CombatStyle.MAGIC, shadow).withAntifirePotion(true));
		assertFalse(results.isEmpty());
		assertEquals("tumeken's shadow", results.get(0).getLoadout().getWeapon().getNameLower());
		assertFalse(results.get(0).isAntifireAssumed());
	}
}
