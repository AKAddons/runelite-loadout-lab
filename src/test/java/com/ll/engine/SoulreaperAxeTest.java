package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * Soul stacks (wiki, mirrored from the official calc): each stack adds
 * 6% of the boosted strength level, flat, AFTER the prayer factor -
 * "does not stack multiplicatively with prayers" - to a cap of +30% at
 * five stacks. Stacks build one per swing (even misses), persist
 * between kills, and only decay after 30s idle, so a sustained grind
 * sits at five stacks; the engine prices that steady state.
 * (Field report 2026-08-09: simmed Soulreaper axe lost to Noxious
 * halberd on Thermy because the axe was priced as a plain axe.)
 */
public class SoulreaperAxeTest {
	private static Tables data;

	private static Tables data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	private static Gear byName(String lname) {
		return data().getGearItems().stream()
			.filter(g -> g.getLname().equals(lname) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + lname));
	}

	@Test
	public void fiveSoulStacksBeatTheNoxiousHalberdOnThermy() {
		Monster thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(byName("soulreaper axe").getId(), 1);
		owned.put(byName("noxious halberd").getId(), 1);
		Req request = new Req(
			thermy, Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 1);
		List<Dps> results = new LoadoutOptimizer().optimize(data(), request);
		Assert.assertFalse(results.isEmpty());
		String weapon = results.get(0).getLoadout().get(Slot.WEAPON).labelLower();
		Assert.assertTrue("at 5 stacks the axe outhits the halberd: " + weapon,
			weapon.contains("soulreaper axe"));
	}

	@Test
	public void theStackBonusIsFlatPostPrayerOffTheBoostedLevel() {
		// The exact wiki-calc chain: effective strength gains
		// floor(level * 30/100) on top of the prayer-multiplied level -
		// never level * prayer * 1.30 (that would over-price the axe).
		Monster thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Req request = new Req(
			thermy, Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byName("soulreaper axe"));
		Dps result = new Calc().calculate(request, new Kit(gear));
		Assert.assertNotNull(result);

		int level = request.getLevels().getStrength();
		double prayer = request.getPrayers().getMeleeStrength();
		// The engine reports the winning stance; the axe's strength
		// bonus is 125.
		int stance = stanceBonus(result);
		int expectedEffective = RollMath.effectiveLevel(level, prayer, stance) + level * 30 / 100;
		Assert.assertEquals(RollMath.maxHitFromEffective(expectedEffective, 125),
			result.getMaxHit());
	}

	@Test
	public void otherAxesDoNotInheritTheStackBonus() {
		Monster thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Req request = new Req(
			thermy, Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byName("dragon axe"));
		Dps result = new Calc().calculate(request, new Kit(gear));
		Assert.assertNotNull(result);
		int level = request.getLevels().getStrength();
		double prayer = request.getPrayers().getMeleeStrength();
		int plainEffective = RollMath.effectiveLevel(level, prayer, stanceBonus(result));
		Assert.assertEquals(RollMath.maxHitFromEffective(plainEffective,
				byName("dragon axe").getBonuses().getStrength()),
			result.getMaxHit());
	}

	/** The strength-stance bonus of the variant the engine chose. */
	private static int stanceBonus(Dps result) {
		return result.getAttackType().contains("aggressive") ? 3
			: result.getAttackType().contains("controlled") ? 1 : 0;
	}
}
