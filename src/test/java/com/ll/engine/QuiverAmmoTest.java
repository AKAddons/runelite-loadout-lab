package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
class QuiverAmmoTest {
	private static Tables data;
	private static Monster target;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		target = data.searchMonsters("general graardor", 1).get(0);
	}

	private static Gear byName(String lname) {
		for (Gear g : data.getGearItems()) {
			if (g.getLname().equals(lname) && g.isStandardGear()) {
				return g;
			}
		}
		throw new AssertionError("corpus is missing: " + lname);
	}

	private static Req req() {
		return TestRequests.of(target, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
	}

	private static Kit worn(String weapon, String ammo, String cape) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byName(weapon));
		if (ammo != null) {
			gear.put(Slot.AMMO, byName(ammo));
		}
		if (cape != null) {
			gear.put(Slot.CAPE, byName(cape));
		}
		return new Kit(gear);
	}

	@Test
	@DisplayName("the fill pass moves a bow's arrows into the quiver and frees the slot")
	void bowArrowsRelocate() {
		Req request = req();
		Kit start = worn("magic shortbow (i)", "amethyst arrow", "blessed dizana's quiver");
		Dps base = new Calc().calculate(request, start);
		assertNotNull(base);

		Dps filled = new LoadoutOptimizer().fillDpsNeutralSlots(data, request, base);
		Gear carried = filled.getLoadout().getQuiverAmmo();
		assertNotNull(carried, "the arrows must ride the quiver");
		assertEquals("Amethyst arrow", carried.getName());
		Gear slot = filled.getLoadout().get(Slot.AMMO);
		assertTrue(slot == null || slot.getBonuses().getRangedStrength() == 0,
			"the freed slot holds a passive item (blessing), never firing ammo: "
				+ (slot == null ? "empty" : slot.label()));
		assertTrue(filled.getDps() >= base.getDps() - 1e-9,
			"the relocation is dps-neutral or better");
		assertTrue(filled.getMaxHit() >= base.getMaxHit(),
			"the arrows' ranged strength still counts from the quiver");
	}

	@Test
	@DisplayName("the roster relocation helper frees the ammo slot on its own")
	void rosterRelocationHelper() {
		// The roster path calls relocateQuiverAmmo (not the full sweep) on
		// each shown set (field report 2026-08-09: a roster BiS kept the
		// arrow in the ammo slot instead of Rada's). It relocates AND fills
		// the freed slot, exactly like the sweep's quiver branch, with no
		// other slot touched.
		Req request = req();
		Kit start = worn("magic shortbow (i)", "amethyst arrow", "blessed dizana's quiver");
		Dps base = new Calc().calculate(request, start);
		assertNotNull(base);

		Dps moved = new LoadoutOptimizer().relocateQuiverAmmo(data, request, base);
		assertEquals("Amethyst arrow", moved.getLoadout().getQuiverAmmo().getName(),
			"the arrows must ride the quiver");
		Gear slot = moved.getLoadout().get(Slot.AMMO);
		assertTrue(slot == null || slot.getBonuses().getRangedStrength() == 0,
			"the freed slot holds a passive, never firing ammo: "
				+ (slot == null ? "empty" : slot.label()));
		assertTrue(moved.getDps() >= base.getDps() - 1e-9, "relocation is dps-neutral");
		assertTrue(moved.getMaxHit() >= base.getMaxHit(), "the quiver still fires the arrows");
	}

	@Test
	@DisplayName("the roster helper no-ops on a set without a relocatable quiver")
	void rosterRelocationNoOp() {
		Req request = req();
		Kit plain = worn("twisted bow", "dragon arrow", "ava's assembler");
		Dps base = new Calc().calculate(request, plain);
		Dps moved = new LoadoutOptimizer().relocateQuiverAmmo(data, request, base);
		assertSame(base, moved, "no quiver, no change - same object back");
		assertNull(new LoadoutOptimizer().relocateQuiverAmmo(data, request, null));
	}

	@Test
	@DisplayName("a re-show prices the relocated set identically")
	void reShowParity() {
		Req request = req();
		Kit start = worn("magic shortbow (i)", "amethyst arrow", "blessed dizana's quiver");
		Dps base = new Calc().calculate(request, start);

		EnumMap<Slot, Gear> gear = new EnumMap<>(start.getGear());
		Gear arrows = gear.remove(Slot.AMMO);
		Kit relocated = Kit.adopting(gear).withQuiverAmmo(arrows);
		Dps reShown = new Calc().calculate(request, relocated);
		assertNotNull(reShown);
		assertEquals(base.getDps(), reShown.getDps(), 1e-9,
			"quiver-carried arrows must price exactly like slot arrows");
		assertEquals(base.getMaxHit(), reShown.getMaxHit());
	}

	@Test
	@DisplayName("accuracy-bearing arrows keep their bonus from the quiver")
	void accuracyParity() {
		// Field report 2026-08-09: Seeking dragon arrows carry +20 ranged
		// ACCURACY; pricing only the strength made the relocation
		// dps-negative and the ammo slot never freed for a blessing. The
		// quiver's extra slot is worn equipment - full stats count.
		Req request = req();
		Kit start = worn("twisted bow", "seeking dragon arrow", "blessed dizana's quiver");
		Dps base = new Calc().calculate(request, start);
		assertNotNull(base);

		EnumMap<Slot, Gear> gear = new EnumMap<>(start.getGear());
		Gear arrows = gear.remove(Slot.AMMO);
		Kit relocated = Kit.adopting(gear).withQuiverAmmo(arrows);
		Dps reShown = new Calc().calculate(request, relocated);
		assertNotNull(reShown);
		assertEquals(base.getDps(), reShown.getDps(), 1e-9,
			"the +20 accuracy must survive the relocation");
		assertEquals(base.getAccuracy(), reShown.getAccuracy(), 1e-9);
	}

	@Test
	@DisplayName("crossbow bolts relocate; a ballista's javelins and a plain cape do not")
	void scoping() {
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
