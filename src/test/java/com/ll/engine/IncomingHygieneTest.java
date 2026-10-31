package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.Stats;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layer 1 of the defensive rebuild (F-01M5FY64VERY7X0PG7S1A5J373): the
 * stat-sheet model reads the wiki's listed max hits, understands the
 * corpus's messy style strings, refuses to guess an unknown attack
 * speed, never dilutes a real attack with an unmodeled one, and says how
 * much of the monster it actually covers.
 */
class IncomingHygieneTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Monster mob(String name, String version) {
		return data.getMonsters().stream()
			.filter(m -> m.getName().equals(name) && (version == null || m.getVersion().equals(version)))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name + " " + version
				+ "; has " + data.getMonsters().stream().filter(m -> m.getName().equals(name))
					.map(Monster::getVersion).collect(java.util.stream.Collectors.toList())));
	}

	private static Kit armour(int each) {
		Gear body = new Gear(1, "Test platebody", "", Slot.BODY, "", 0,
			false, true, true, true, 0, Stats.ZERO,
			new Stats(each, each, each, each, each, 0, 0, 0, 0), Stats.ZERO, null);
		return new Kit(Map.of(Slot.BODY, body));
	}

	private static Incoming.Result hit(Monster mob) {
		return Incoming.calculate(mob, armour(100), 90, 90, 0);
	}

	@Test
	@DisplayName("the wiki's listed max hit beats the stat formula")
	void listedMaxHitWins() {
		assertEquals(8, hit(mob("Aberrant spectre", null)).threats.get(0).maxHit,
			"magic max from the infobox, not the formula's 11");
		assertEquals(37, hit(mob("Vardorvis", "QUEST")).threats.get(0).maxHit,
			"30-37 (Melee) is 37, not the str-0 formula's 1");
		// "22 (Magic)" with Crush + Magic: the label feeds the magic attack
		// only; the unlabelled crush keeps the formula.
		Incoming.Result druid = hit(mob("Undead Druid", null));
		Incoming.StyleThreat magic = druid.threats.stream()
			.filter(t -> t.style.equals("Magic")).findFirst().orElseThrow(AssertionError::new);
		assertEquals(22, magic.maxHit);
	}

	@Test
	@DisplayName("Typeless slash rolls slash accuracy and no prayer blocks it")
	void typelessIsUnblockable() {
		Incoming.Result moon = hit(mob("Blue Moon", null));
		assertEquals("full", moon.coverage);
		assertTrue(moon.tot > 0, "the moons hit you through any prayer");
		assertNull(moon.protectPrayer);
		assertEquals(32, moon.threats.get(0).maxHit);
		assertEquals(moon.unprayedDps, moon.tot, 1e-12);
	}

	@Test
	@DisplayName("stray control characters and parenthesised types are cleaned")
	void messyStylesAreModeled() {
		Incoming.Result araxxor = hit(mob("Araxxor", null));
		assertEquals("full", araxxor.coverage, "\\x7f\\x7f Magic is magic");
		assertEquals(3, araxxor.threats.size());
		assertEquals("full", hit(mob("Dusk", "First form")).coverage, "Slash \\x7f\\x7f is slash");
		assertEquals("full", hit(mob("Yama", "Normal")).coverage, "Melee (Slash) is slash");
	}

	@Test
	@DisplayName("an unknown attack speed is unmodeled, never one tick")
	void unknownSpeedIsUnmodeled() {
		// Skotizo: Stab + Magic on the sheet, speed "Random" (0). The Leviathan
		// used to sit here until its curated entry priced it (ISS-01M5PBSZBBBAS9Y46X04MVMHZB).
		Incoming.Result skotizo = hit(mob("Skotizo", ""));
		assertEquals("none", skotizo.coverage);
		assertEquals(0.0, skotizo.unprayedDps, 0.0);
	}

	@Test
	@DisplayName("an unmodeled style takes no share - the real attack keeps the cadence")
	void noDilution() {
		Incoming.Result dragon = hit(mob("Blue dragon", "1"));
		assertEquals("partial", dragon.coverage);
		assertTrue(dragon.unmodeled.contains("Dragonfire"), dragon.unmodeled.toString());
		Incoming.StyleThreat slash = dragon.threats.stream()
			.filter(t -> t.modeled).findFirst().orElseThrow(AssertionError::new);
		assertEquals(1.0, slash.share, 0.0);
	}

	@Test
	@DisplayName("a monster that does not attack is fully modeled at zero")
	void nonAttackerIsZero() {
		Incoming.Result flower = hit(mob("Flower", null));
		assertEquals("full", flower.coverage);
		assertEquals(0.0, flower.unprayedDps, 0.0);
	}

	@Test
	@DisplayName("curated bosses report curated coverage")
	void curatedCoverage() {
		assertEquals("curated", hit(data.searchMonsters("general graardor", 1).get(0)).coverage);
	}
}
