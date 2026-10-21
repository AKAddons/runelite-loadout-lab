package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.StatBlock;
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
class IncomingHygieneTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static MonsterStats mob(String name, String version)
	{
		return data.getMonsters().stream()
			.filter(m -> m.getName().equals(name) && (version == null || m.getVersion().equals(version)))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name + " " + version
				+ "; has " + data.getMonsters().stream().filter(m -> m.getName().equals(name))
					.map(MonsterStats::getVersion).collect(java.util.stream.Collectors.toList())));
	}

	private static Loadout armour(int each)
	{
		GearItem body = new GearItem(1, "Test platebody", "", GearSlot.BODY, "", 0,
			false, true, true, true, 0, StatBlock.ZERO,
			new StatBlock(each, each, each, each, each, 0, 0, 0, 0), StatBlock.ZERO, null);
		return new Loadout(Map.of(GearSlot.BODY, body));
	}

	private static IncomingDpsCalculator.Result hit(MonsterStats mob)
	{
		return IncomingDpsCalculator.calculate(mob, armour(100), 90, 90);
	}

	@Test
	@DisplayName("the wiki's listed max hit beats the stat formula")
	void listedMaxHitWins()
	{
		assertEquals(8, hit(mob("Aberrant spectre", null)).threats.get(0).maxHit,
			"magic max from the infobox, not the formula's 11");
		assertEquals(37, hit(mob("Vardorvis", "QUEST")).threats.get(0).maxHit,
			"30-37 (Melee) is 37, not the str-0 formula's 1");
		// "22 (Magic)" with Crush + Magic: the label feeds the magic attack
		// only; the unlabelled crush keeps the formula.
		IncomingDpsCalculator.Result druid = hit(mob("Undead Druid", null));
		IncomingDpsCalculator.StyleThreat magic = druid.threats.stream()
			.filter(t -> t.style.equals("Magic")).findFirst().orElseThrow(AssertionError::new);
		assertEquals(22, magic.maxHit);
	}

	@Test
	@DisplayName("Typeless slash rolls slash accuracy and no prayer blocks it")
	void typelessIsUnblockable()
	{
		IncomingDpsCalculator.Result moon = hit(mob("Blue Moon", null));
		assertEquals("full", moon.coverage);
		assertTrue(moon.totalDps > 0, "the moons hit you through any prayer");
		assertNull(moon.protectPrayer);
		assertEquals(32, moon.threats.get(0).maxHit);
		assertEquals(moon.unprayedDps, moon.totalDps, 1e-12);
	}

	@Test
	@DisplayName("stray control characters and parenthesised types are cleaned")
	void messyStylesAreModeled()
	{
		IncomingDpsCalculator.Result araxxor = hit(mob("Araxxor", null));
		assertEquals("full", araxxor.coverage, "\\x7f\\x7f Magic is magic");
		assertEquals(3, araxxor.threats.size());
		assertEquals("full", hit(mob("Dusk", "First form")).coverage, "Slash \\x7f\\x7f is slash");
		assertEquals("full", hit(mob("Yama", "Normal")).coverage, "Melee (Slash) is slash");
	}

	@Test
	@DisplayName("an unknown attack speed is unmodeled, never one tick")
	void unknownSpeedIsUnmodeled()
	{
		IncomingDpsCalculator.Result leviathan = hit(mob("The Leviathan", ""));
		assertEquals("none", leviathan.coverage);
		assertEquals(0.0, leviathan.unprayedDps, 0.0);
	}

	@Test
	@DisplayName("an unmodeled style takes no share - the real attack keeps the cadence")
	void noDilution()
	{
		IncomingDpsCalculator.Result dragon = hit(mob("Blue dragon", "1"));
		assertEquals("partial", dragon.coverage);
		assertTrue(dragon.unmodeled.contains("Dragonfire"), dragon.unmodeled.toString());
		IncomingDpsCalculator.StyleThreat slash = dragon.threats.stream()
			.filter(t -> t.modeled).findFirst().orElseThrow(AssertionError::new);
		assertEquals(1.0, slash.share, 0.0);
	}

	@Test
	@DisplayName("a monster that does not attack is fully modeled at zero")
	void nonAttackerIsZero()
	{
		IncomingDpsCalculator.Result flower = hit(mob("Flower", null));
		assertEquals("full", flower.coverage);
		assertEquals(0.0, flower.unprayedDps, 0.0);
	}

	@Test
	@DisplayName("curated bosses report curated coverage")
	void curatedCoverage()
	{
		assertEquals("curated", hit(data.searchMonsters("general graardor", 1).get(0)).coverage);
	}
}
