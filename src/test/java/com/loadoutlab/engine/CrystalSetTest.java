package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * Crystal armour scaling for the crystal bow / bow of faerdhinen, verified
 * against the official wiki calculator engine (scripts/verify_official.py:
 * bofa-graardor, bofahelm/body/legs/set-graardor, cbowset-graardor and
 * bofasetslayer-graardor all at 0.0% delta):
 *
 *   accuracy roll x(20+n)/20, max hit x(40+n)/40
 *   where n = helm 1, legs 2, body 3 (full set: +30% accuracy, +15% damage)
 *
 * The multiplier applies to the BASE roll/max hit, before salve/slayer
 * (flooring order verified in-game by the wiki calc devs). Inactive pieces
 * give nothing, and only the two bows scale - not the blade or halberd.
 */
public class CrystalSetTest
{
	@Test
	public void eachCrystalPieceScalesTheBofaByItsExactWeight()
	{
		LoadoutData data = new DataService().load();
		OptimizationRequest request = request(data, false);
		DpsResult bare = calc(data, request, "Bow of faerdhinen", null, null);
		assertExactScaling(data, request, bare, 1, "Crystal helm");
		assertExactScaling(data, request, bare, 2, "Crystal legs");
		assertExactScaling(data, request, bare, 3, "Crystal body");
		assertExactScaling(data, request, bare, 6, "Crystal helm", "Crystal body", "Crystal legs");
	}

	@Test
	public void fullCrystalSetBoostsTheCrystalBowByThirtyAccFifteenDamage()
	{
		LoadoutData data = new DataService().load();
		OptimizationRequest request = request(data, false);
		DpsResult bare = calc(data, request, "Crystal bow", null, null);
		DpsResult withSet = calc(data, request, "Crystal bow", null,
			new String[]{"Crystal helm", "Crystal body", "Crystal legs"});
		Assert.assertEquals(expectedRoll(bare, withSet, 6), withSet.getAttackRoll());
		Assert.assertEquals(bare.getMaxHit() * 46 / 40, withSet.getMaxHit());
		Assert.assertTrue(withSet.getDps() > bare.getDps() * 1.15);
	}

	@Test
	public void inactiveCrystalPiecesGiveTheBofaNothing()
	{
		LoadoutData data = new DataService().load();
		OptimizationRequest request = request(data, false);
		DpsResult bare = calc(data, request, "Bow of faerdhinen", null, null);
		DpsResult withInactive = calc(data, request, "Bow of faerdhinen", "Inactive",
			new String[]{"Crystal helm", "Crystal body", "Crystal legs"});
		// Inactive pieces have zero bonuses AND must not count for scaling.
		Assert.assertEquals(bare.getAttackRoll(), withInactive.getAttackRoll());
		Assert.assertEquals(bare.getMaxHit(), withInactive.getMaxHit());
	}

	@Test
	public void crystalScalingFloorsBeforeTheSlayerHelmMultiplier()
	{
		// The wiki calc devs verified in-game that crystal applies to the
		// base max hit before the slayer helm multiplier; the reverse order
		// gives a max hit one higher at these stats.
		LoadoutData data = new DataService().load();
		OptimizationRequest request = request(data, true);
		DpsResult bare = calc(data, request, "Bow of faerdhinen", null, null);
		DpsResult slayerSet = calc(data, request, "Bow of faerdhinen", null,
			new String[]{"Slayer helmet (i)", "Crystal body", "Crystal legs"});
		int base = bare.getMaxHit();
		int crystalFirst = (base * 45 / 40) * 23 / 20;
		int slayerFirst = (base * 23 / 20) * 45 / 40;
		Assert.assertNotEquals("pick stats where the flooring order matters", crystalFirst, slayerFirst);
		Assert.assertEquals(crystalFirst, slayerSet.getMaxHit());
	}

	@Test
	public void crystalArmourDoesNotScaleTheBladeOfSaeldor()
	{
		LoadoutData data = new DataService().load();
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			graardor, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		DpsResult bare = calc(data, request, "Blade of saeldor", null, null);
		DpsResult withSet = calc(data, request, "Blade of saeldor", null,
			new String[]{"Crystal helm", "Crystal body", "Crystal legs"});
		Assert.assertEquals(bare.getMaxHit(), withSet.getMaxHit());
	}

	private static void assertExactScaling(LoadoutData data, OptimizationRequest request,
		DpsResult bare, int pieces, String... armour)
	{
		DpsResult result = calc(data, request, "Bow of faerdhinen", null, armour);
		Assert.assertEquals(String.join("+", armour) + " accuracy",
			expectedRoll(bare, result, pieces), result.getAttackRoll());
		// Crystal armour has no ranged strength, so the base max is shared.
		Assert.assertEquals(String.join("+", armour) + " ranged strength",
			bare.getLoadout().getBonuses().getRangedStrength(),
			result.getLoadout().getBonuses().getRangedStrength());
		Assert.assertEquals(String.join("+", armour) + " max hit",
			bare.getMaxHit() * (40 + pieces) / 40, result.getMaxHit());
	}

	/**
	 * Roll = effectiveLevel x (rangedAttack + 64), crystal-scaled before any
	 * other multiplier. Derive the effective level from the bare bow's roll
	 * (identity multiplier), then rebuild the scaled loadout's expectation.
	 */
	private static long expectedRoll(DpsResult bare, DpsResult scaled, int pieces)
	{
		long bareBonus = bare.getLoadout().getOffensive().getRanged() + 64;
		Assert.assertEquals("effective level derivation", 0, bare.getAttackRoll() % bareBonus);
		long effective = bare.getAttackRoll() / bareBonus;
		long scaledBonus = scaled.getLoadout().getOffensive().getRanged() + 64;
		return effective * scaledBonus * (20 + pieces) / 20;
	}

	private static OptimizationRequest request(LoadoutData data, boolean onTask)
	{
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		return new OptimizationRequest(
			graardor, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, onTask,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static DpsResult calc(LoadoutData data, OptimizationRequest request,
		String weapon, String armourVersion, String[] armour)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName(data, weapon, null));
		if (armour != null)
		{
			for (String name : armour)
			{
				GearItem item = byName(data, name, armourVersion);
				gear.put(item.getSlot(), item);
			}
		}
		return new DpsCalculator().calculate(request, new Loadout(gear));
	}

	private static GearItem byName(LoadoutData data, String name, String version)
	{
		return data.getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name))
			.filter(g -> version == null ? g.isStandardGear() : version.equalsIgnoreCase(g.getVersion()))
			.findFirst().orElseThrow(() -> new AssertionError("missing " + name));
	}
}
