package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Flat max-hit bonuses on elemental spells (wiki, verified 2026-09-08 against
 * the official calculator's PlayerVsNPCCalc): chaos gauntlets +3 on any bolt
 * spell; the elemental amulet +2 on any elemental spell; an amulet of air /
 * water / earth / fire +2 on its own element. All added BEFORE the magic
 * damage percentage. Field report: Mike, Discord, 2026-09-07.
 */
class BoltSpellModifiersTest
{
	private static LoadoutData data;
	private static MonsterStats goblin;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		goblin = data.searchMonsters("Goblin", 1).iterator().next();
	}

	private static GearItem byName(String nameLower)
	{
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().equals(nameLower))
			{
				return g;
			}
		}
		throw new AssertionError("missing from the gear data: " + nameLower);
	}

	private static SpellStats spell(String name)
	{
		return data.getSpells().stream().filter(s -> s.getName().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("missing spell " + name));
	}

	/** Max hit for a spell on a staff of fire, plus any named extras. */
	@Test
	@DisplayName("Salarin honours the elemental amulet alone: strikes stay a flat 12 for every other item, 14 with it")
	void salarin()
	{
		MonsterStats salarin = data.searchMonsters("Salarin", 1).iterator().next();
		assertEquals(12, maxHitVs(salarin, "Fire Strike"));
		assertEquals(12, maxHitVs(salarin, "Fire Strike", "occult necklace"), "spell damage items do nothing");
		assertEquals(14, maxHitVs(salarin, "Fire Strike", "elemental amulet"), "the sole exception (wiki)");
		assertEquals(12, maxHitVs(salarin, "Fire Strike", "amulet of fire"), "the wiki names the elemental amulet only");
		assertEquals(14, maxHitVs(salarin, "Wind Strike", "elemental amulet"), "every strike is treated as the top bolt");
	}

	private static int maxHit(String spellName, String... extras)
	{
		return maxHitVs(goblin, spellName, extras);
	}

	private static int maxHitVs(MonsterStats monster, String spellName, String... extras)
	{
		OptimizationRequest request = TestRequests.of(monster, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, spell(spellName), 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		EnumMap<GearSlot, GearItem> worn = new EnumMap<>(GearSlot.class);
		worn.put(GearSlot.WEAPON, byName("staff of fire"));
		for (String extra : extras)
		{
			GearItem item = byName(extra);
			worn.put(item.getSlot(), item);
		}
		return new DpsCalculator().calculate(request, new Loadout(worn)).getMaxHit();
	}

	@Test
	@DisplayName("chaos gauntlets add 3 to a bolt spell and nothing to a strike")
	void chaosGauntlets()
	{
		assertEquals(12, maxHit("Fire Bolt"), "Fire Bolt at 99 Magic");
		assertEquals(15, maxHit("Fire Bolt", "chaos gauntlets"));
		assertEquals(8, maxHit("Fire Strike"));
		assertEquals(8, maxHit("Fire Strike", "chaos gauntlets"));
	}

	@Test
	@DisplayName("the elemental amulet adds 2 to every elemental spell and stacks with the gauntlets")
	void elementalAmulet()
	{
		assertEquals(14, maxHit("Fire Bolt", "elemental amulet"));
		assertEquals(17, maxHit("Fire Bolt", "chaos gauntlets", "elemental amulet"));
		assertEquals(10, maxHit("Fire Strike", "elemental amulet"));
		assertEquals(26, maxHit("Wind Surge", "elemental amulet"));
	}

	@Test
	@DisplayName("an element amulet only lifts its own element")
	void elementAmulets()
	{
		assertEquals(14, maxHit("Fire Bolt", "amulet of fire"));
		assertEquals(12, maxHit("Fire Bolt", "amulet of air"));
		assertEquals(14, maxHit("Wind Bolt", "amulet of air"));
		// June 2025 rebalance: every Blast hits 16 at 99 Magic; the amulet adds 2.
		assertEquals(18, maxHit("Water Blast", "amulet of water"));
		assertEquals(18, maxHit("Earth Blast", "amulet of earth"));
		assertEquals(16, maxHit("Earth Blast", "amulet of water"));
	}
}
