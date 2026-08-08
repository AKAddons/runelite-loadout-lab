package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dizana's quiver carries the arrows, freeing the ammo slot for a
 * blessing - the blowpipe treatment (field report 2026-08-07). Wiki
 * rule: with a non-ammunition item in the ammo slot, the quiver's
 * stored ammunition fires at full ranged strength.
 */
class QuiverAmmoTest
{
	private static LoadoutData data;
	private static MonsterStats target;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		target = data.searchMonsters("general graardor", 1).get(0);
	}

	private static GearItem byName(String nameLower)
	{
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().equals(nameLower) && g.isStandardGear())
			{
				return g;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower);
	}

	private static OptimizationRequest req()
	{
		return TestRequests.of(target, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
	}

	private static Loadout worn(String weapon, String ammo, String cape)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName(weapon));
		if (ammo != null)
		{
			gear.put(GearSlot.AMMO, byName(ammo));
		}
		if (cape != null)
		{
			gear.put(GearSlot.CAPE, byName(cape));
		}
		return new Loadout(gear);
	}

	@Test
	@DisplayName("the fill pass moves a bow's arrows into the quiver and frees the slot")
	void bowArrowsRelocate()
	{
		OptimizationRequest request = req();
		Loadout start = worn("magic shortbow (i)", "amethyst arrow", "blessed dizana's quiver");
		DpsResult base = new DpsCalculator().calculate(request, start);
		assertNotNull(base);

		DpsResult filled = new LoadoutOptimizer().fillDpsNeutralSlots(data, request, base);
		GearItem carried = filled.getLoadout().getQuiverAmmo();
		assertNotNull(carried, "the arrows must ride the quiver");
		assertEquals("Amethyst arrow", carried.getName());
		GearItem slot = filled.getLoadout().get(GearSlot.AMMO);
		assertTrue(slot == null || slot.getBonuses().getRangedStrength() == 0,
			"the freed slot holds a passive item (blessing), never firing ammo: "
				+ (slot == null ? "empty" : slot.label()));
		assertTrue(filled.getDps() >= base.getDps() - 1e-9,
			"the relocation is dps-neutral or better");
		assertTrue(filled.getMaxHit() >= base.getMaxHit(),
			"the arrows' ranged strength still counts from the quiver");
	}

	@Test
	@DisplayName("a re-show prices the relocated set identically")
	void reShowParity()
	{
		OptimizationRequest request = req();
		Loadout start = worn("magic shortbow (i)", "amethyst arrow", "blessed dizana's quiver");
		DpsResult base = new DpsCalculator().calculate(request, start);

		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(start.getGear());
		GearItem arrows = gear.remove(GearSlot.AMMO);
		Loadout relocated = Loadout.adopting(gear).withQuiverAmmo(arrows);
		DpsResult reShown = new DpsCalculator().calculate(request, relocated);
		assertNotNull(reShown);
		assertEquals(base.getDps(), reShown.getDps(), 1e-9,
			"quiver-carried arrows must price exactly like slot arrows");
		assertEquals(base.getMaxHit(), reShown.getMaxHit());
	}

	@Test
	@DisplayName("crossbow bolts relocate; a ballista's javelins and a plain cape do not")
	void scoping()
	{
		assertTrue(QuiverAmmo.relocatable(
			worn("dragon crossbow", "ruby dragon bolts (e)", "blessed dizana's quiver")));
		assertFalse(QuiverAmmo.relocatable(
			worn("heavy ballista", "dragon javelin", "blessed dizana's quiver")),
			"the quiver holds arrows and bolts, not javelins");
		assertFalse(QuiverAmmo.relocatable(
			worn("magic shortbow (i)", "amethyst arrow", "ava's assembler")),
			"no quiver, no relocation");
		assertFalse(QuiverAmmo.relocatable(
			worn("toxic blowpipe", null, "blessed dizana's quiver")),
			"a blowpipe's darts are its own business");
	}
}
