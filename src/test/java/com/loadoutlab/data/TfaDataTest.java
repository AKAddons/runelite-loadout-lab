package com.loadoutlab.data;

import com.loadoutlab.engine.DemonbaneVulnerability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Fractured Archive's data after the post-launch fixes (ISS-01M5JBYYKPE7192Z24NTR432M8,
 * Andrew 2026-10-22: "gearscore just did a big update on TFA can we make
 * sure our data is as accurate as theirs"). Values from the live wiki and
 * Jagex's launch-day news; ids from the gameval constants.
 */
class TfaDataTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Monster byId(int id) {
		return data.getMonsters().stream().filter(m -> m.getId() == id).findFirst()
			.orElseThrow(() -> new AssertionError("no monster row " + id));
	}

	// The cdn gives both Archive Guardian versions the wiki's one id (16659).
	private static Monster byVersion(String name, String version) {
		return data.getMonsters().stream().filter(m -> name.equals(m.getName()) && version.equals(m.getVersion())).findFirst()
			.orElseThrow(() -> new AssertionError("no monster row " + name + " " + version));
	}

	@Test
	@DisplayName("the wiki's fixes since 20 Oct: Nex crush 35, Bork crush 10 and flat armour 11 (the wiki's 8-14 base), Moia weak to air, Xarth's bonuses")
	void statFixes() {
		assertEquals(35, byId(11278).getDefensive().get("crush"), "Nex (Jagex 2026-10-20)");
		Monster bork = byId(16640);
		assertEquals(10, bork.getDefensive().get("crush"));
		assertEquals(11, bork.getDefensive().getFlatArmour());
		Monster moia = byId(16667);
		assertEquals("air", moia.getWeaknessElement());
		assertEquals(80, moia.getWeaknessSeverity());
		Monster xarth = byId(16684);
		assertEquals(150, xarth.getOffence().getAttackBonus());
		assertEquals(225, xarth.getOffence().getStrengthBonus());
		assertEquals(1601, byId(16677).getCombatLevel(), "Ocellus phase 3");
	}

	@Test
	@DisplayName("the Archive Guardian has a real grounded row beside the flying one (the wiki's one id, 16659)")
	void groundedGuardian() {
		Monster grounded = byVersion("Archive Guardian", "Grounded");
		assertEquals("Archive Guardian", grounded.getName());
		assertEquals("Grounded", grounded.getVersion());
		assertEquals(90, grounded.getDefensive().get("slash"));
		assertEquals(50, grounded.getDefensive().get("light"));
		assertEquals(115, grounded.getDefensive().get("standard"));
		assertEquals("fire", grounded.getWeaknessElement());
		assertEquals(40, grounded.getWeaknessSeverity());
		Monster flying = byVersion("Archive Guardian", "Flying");
		assertEquals(16659, flying.getId());
		assertEquals(80, flying.getWeaknessSeverity());
	}

	@Test
	@DisplayName("drains stop at 60% of base Defence on the six capped bosses (Jagex hotfix); The Blade drains to 0")
	void defenceFloors() {
		int[][] floors = {{16659, 156}, {16652, 78}, {16640, 180}, {16667, 117}, {16684, 102}, {16673, 129}, {16677, 150}};
		for (int[] f : floors) {
			assertEquals(f[1], DefenceFloors.floorFor(byId(f[0])), "floor for " + f[0]);
		}
		assertEquals(156, DefenceFloors.floorFor(byVersion("Archive Guardian", "Grounded")));
		assertEquals(0, DefenceFloors.floorFor(byId(16675)), "Ocellus phase 2 (The Blade) has no cap");
		assertEquals(0, DefenceFloors.floorFor(byId(16665)), "Dreadnoth has no cap");
	}

	@Test
	@DisplayName("demonbane: Xarth and Ocellus take 80% of it, Dreadnoth 25%")
	void demonbane() {
		assertEquals(80, DemonbaneVulnerability.percentFor(byId(16684)));
		assertEquals(80, DemonbaneVulnerability.percentFor(byId(16673)), "Ocellus phase 1");
		assertEquals(80, DemonbaneVulnerability.percentFor(byId(16677)));
		assertEquals(25, DemonbaneVulnerability.percentFor(byId(16665)));
	}

	@Test
	@DisplayName("the Dagon'hai trio and the adds have rows: Elder, Monks, jelly, binder, void rift and augmenter, Xarth's corpses")
	void newRows() {
		// Same-stat spawns fold into one row (the corpus rule, as for the
		// Tormented Demons): the Elder is 16643, the two Monks one row - the
		// cdn's order decides which id it keeps.
		assertEquals(640, byId(16643).getHitpoints(), "Dagon'hai Elder");
		assertEquals(640, data.searchMonsters("dagon'hai monk", 1).get(0).getHitpoints(), "Dagon'hai Monk");
		for (int id : new int[]{16666, 16670, 16678, 16680, 16687, 16688}) {
			assertTrue(byId(id).getHitpoints() > 0, "row " + id);
		}
		assertEquals("Doused", byId(16688).getVersion());
	}
}
