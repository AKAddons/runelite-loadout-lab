package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
public class CrystalSetTest {
	@Test
	public void eachCrystalPieceScalesTheBofaByItsExactWeight() {
		Tables data = new DataService().load();
		Req request = request(data, false);
		Dps bare = calc(data, request, "Bow of faerdhinen", null, null);
		assertExactScaling(data, request, bare, 1, "Crystal helm");
		assertExactScaling(data, request, bare, 2, "Crystal legs");
		assertExactScaling(data, request, bare, 3, "Crystal body");
		assertExactScaling(data, request, bare, 6, "Crystal helm", "Crystal body", "Crystal legs");
	}

	@Test
	public void fullCrystalSetBoostsTheCrystalBowByThirtyAccFifteenDamage() {
		Tables data = new DataService().load();
		Req request = request(data, false);
		Dps bare = calc(data, request, "Crystal bow", null, null);
		Dps withSet = calc(data, request, "Crystal bow", null,
			new String[]{"Crystal helm", "Crystal body", "Crystal legs"});
		Assert.assertEquals(expectedRoll(bare, withSet, 6), withSet.getAr());
		Assert.assertEquals(bare.getMaxHit() * 46 / 40, withSet.getMaxHit());
		Assert.assertTrue(withSet.getDps() > bare.getDps() * 1.15);
	}

	@Test
	public void inactiveCrystalPiecesGiveTheBofaNothing() {
		Tables data = new DataService().load();
		Req request = request(data, false);
		Dps bare = calc(data, request, "Bow of faerdhinen", null, null);
		Dps withInactive = calc(data, request, "Bow of faerdhinen", "Inactive",
			new String[]{"Crystal helm", "Crystal body", "Crystal legs"});
		// Inactive pieces have zero bonuses AND must not count for scaling.
		Assert.assertEquals(bare.getAr(), withInactive.getAr());
		Assert.assertEquals(bare.getMaxHit(), withInactive.getMaxHit());
	}

	@Test
	public void crystalScalingFloorsBeforeTheSlayerHelmMultiplier() {
		// The wiki calc devs verified in-game that crystal applies to the
		// base max hit before the slayer helm multiplier; the reverse order
		// gives a max hit one higher at these stats.
		Tables data = new DataService().load();
		Req request = request(data, true);
		Dps bare = calc(data, request, "Bow of faerdhinen", null, null);
		Dps slayerSet = calc(data, request, "Bow of faerdhinen", null,
			new String[]{"Slayer helmet (i)", "Crystal body", "Crystal legs"});
		int base = bare.getMaxHit();
		int crystalFirst = (base * 45 / 40) * 23 / 20;
		int slayerFirst = (base * 23 / 20) * 45 / 40;
		Assert.assertNotEquals("pick stats where the flooring order matters", crystalFirst, slayerFirst);
		Assert.assertEquals(crystalFirst, slayerSet.getMaxHit());
	}

	@Test
	public void crystalArmourDoesNotScaleTheBladeOfSaeldor() {
		Tables data = new DataService().load();
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		Req request = new Req(
			graardor, Style.MELEE, Levels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		Dps bare = calc(data, request, "Blade of saeldor", null, null);
		Dps withSet = calc(data, request, "Blade of saeldor", null,
			new String[]{"Crystal helm", "Crystal body", "Crystal legs"});
		Assert.assertEquals(bare.getMaxHit(), withSet.getMaxHit());
	}

	private static void assertExactScaling(Tables data, Req request,
		Dps bare, int pieces, String... armour) {
		Dps result = calc(data, request, "Bow of faerdhinen", null, armour);
		Assert.assertEquals(String.join("+", armour) + " accuracy",
			expectedRoll(bare, result, pieces), result.getAr());
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
	private static long expectedRoll(Dps bare, Dps scaled, int pieces) {
		long bareBonus = bare.getLoadout().getOffensive().getRanged() + 64;
		Assert.assertEquals("effective level derivation", 0, bare.getAr() % bareBonus);
		long effective = bare.getAr() / bareBonus;
		long scaledBonus = scaled.getLoadout().getOffensive().getRanged() + 64;
		return effective * scaledBonus * (20 + pieces) / 20;
	}

	private static Req request(Tables data, boolean onTask) {
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		return new Req(
			graardor, Style.RANGED, Levels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, onTask,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static Dps calc(Tables data, Req request,
		String weapon, String armourVersion, String[] armour) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byName(data, weapon, null));
		if (armour != null) {
			for (String name : armour) {
				Gear item = byName(data, name, armourVersion);
				gear.put(item.getSlot(), item);
			}
		}
		return new Calc().calculate(request, new Kit(gear));
	}

	private static Gear byName(Tables data, String name, String version) {
		return data.getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name))
			.filter(g -> version == null ? g.isStandardGear() : version.equalsIgnoreCase(g.getVersion()))
			.findFirst().orElseThrow(() -> new AssertionError("missing " + name));
	}
}
