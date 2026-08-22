package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * Wielding the weapon you spec with is normal - charging the death
 * for it twice is not. Field report 2026-08-22 (Artio magic BiS):
 * "Accursed sceptre (Charged), Accursed sceptre (Charged)" in one
 * lost list, and the beam's fast path double-counted it too.
 */
public class WornSpecRiskTest
{
	private static LoadoutData data;

	private static LoadoutData data()
	{
		if (data == null)
		{
			data = new DataService().load();
		}
		return data;
	}

	private static GearItem byName(String name)
	{
		return data().getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + name));
	}

	@Test
	public void aWornSpecWeaponIsChargedOnce()
	{
		GearItem weapon = byName("abyssal whip");
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, weapon);
		gear.put(GearSlot.BODY, byName("bandos chestplate"));
		gear.put(GearSlot.FEET, byName("primordial boots"));
		Loadout loadout = new Loadout(gear);

		PvpRisk.Assessment worn = PvpRisk.assess(loadout, weapon, 3);
		PvpRisk.Assessment none = PvpRisk.assess(loadout, null, 3);
		Assert.assertEquals("a worn spec weapon adds nothing to the death",
			none.riskGp, worn.riskGp);
		long appearances = java.util.stream.Stream
			.concat(worn.kept.stream(), worn.lost.stream())
			.filter(g -> g.getId() == weapon.getId()).count();
		Assert.assertEquals("the weapon is listed once", 1, appearances);

		// The beam's fast path must agree with the shown line, or the
		// filter prices deaths the card never displays.
		Assert.assertEquals(worn.riskGp, PvpRisk.riskGp(loadout, weapon, 3));
		Assert.assertEquals(none.riskGp, PvpRisk.riskGp(loadout, null, 3));
	}

	@Test
	public void aSeparatelyCarriedSpecStillCounts()
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName("abyssal whip"));
		gear.put(GearSlot.BODY, byName("bandos chestplate"));
		Loadout loadout = new Loadout(gear);
		GearItem carried = byName("dragon warhammer");

		long withCarried = PvpRisk.riskGp(loadout, carried, 1);
		long without = PvpRisk.riskGp(loadout, null, 1);
		Assert.assertTrue("a genuinely carried spec still costs you",
			withCarried > without);
	}
}
