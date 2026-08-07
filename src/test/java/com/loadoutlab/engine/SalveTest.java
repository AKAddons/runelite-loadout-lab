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
 * The salve amulet family's tiers, wiki-verified (2026-07 player audit
 * A2.1/A2.2): melee/ranged - base and (i) at 16.67%, (e) and (ei) at 20%;
 * magic - (i) 15% accuracy AND damage, (ei) 20% accuracy AND damage. The
 * original engine matched "salve amulet (e)" (with a space), so the
 * spaceless "Salve amulet(ei)" fell into the 16.67% bucket, and the magic
 * DAMAGE path had no salve branch at all.
 */
class SalveTest
{
	private static LoadoutData data;
	private static MonsterStats ankou;   // undead, magic-attackable

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		ankou = data.searchMonsters("ankou", 1).get(0);
		assertTrue(ankou.hasAttribute("undead"), "test premise: ankou is undead");
	}

	private static GearItem byName(String nameLower)
	{
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals(nameLower))
			{
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower);
	}

	private static OptimizationRequest request(CombatStyle style)
	{
		return new OptimizationRequest(ankou, style, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static DpsResult calc(CombatStyle style, GearItem weapon, GearItem neck)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, weapon);
		if (neck != null)
		{
			gear.put(neck.getSlot(), neck);
		}
		return new DpsCalculator().calculate(request(style), new Loadout(gear));
	}

	@Test
	@DisplayName("melee: salve(ei) sits in the 20% tier with salve (e), above (i) and base")
	void meleeTiers()
	{
		GearItem whip = byName("abyssal whip");
		double ei = calc(CombatStyle.MELEE, whip, byName("salve amulet(ei)")).getDps();
		double e = calc(CombatStyle.MELEE, whip, byName("salve amulet (e)")).getDps();
		double i = calc(CombatStyle.MELEE, whip, byName("salve amulet(i)")).getDps();
		double base = calc(CombatStyle.MELEE, whip, byName("salve amulet")).getDps();

		assertEquals(e, ei, 1e-9, "(ei) must match (e)'s 20% melee tier");
		assertEquals(base, i, 1e-9, "(i) shares the base 16.67% melee tier");
		assertTrue(ei > i, "20% tier must beat 16.67% tier");
	}

	@Test
	@DisplayName("magic: salve(ei) raises the MAX HIT 20% and (i) 15% against undead")
	void magicDamageTiers()
	{
		GearItem sang = byName("sanguinesti staff");
		int bare = calc(CombatStyle.MAGIC, sang, null).getMaxHit();
		int ei = calc(CombatStyle.MAGIC, sang, byName("salve amulet(ei)")).getMaxHit();
		int i = calc(CombatStyle.MAGIC, sang, byName("salve amulet(i)")).getMaxHit();

		assertEquals(multiply(bare, 6, 5), ei, "(ei) magic damage is +20%");
		assertEquals(multiply(bare, 23, 20), i, "(i) magic damage is +15%");
	}

	@Test
	@DisplayName("salves do nothing against the living")
	void noEffectOnNonUndead()
	{
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		assertFalse(goblin.hasAttribute("undead"));
		OptimizationRequest req = new OptimizationRequest(goblin, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		GearItem whip = byName("abyssal whip");

		EnumMap<GearSlot, GearItem> bare = new EnumMap<>(GearSlot.class);
		bare.put(GearSlot.WEAPON, whip);
		EnumMap<GearSlot, GearItem> salved = new EnumMap<>(bare);
		GearItem ei = byName("salve amulet(ei)");
		salved.put(ei.getSlot(), ei);

		assertEquals(new DpsCalculator().calculate(req, new Loadout(bare)).getDps(),
			new DpsCalculator().calculate(req, new Loadout(salved)).getDps(), 1e-9);
	}

	private static int multiply(int value, int numerator, int denominator)
	{
		return value * numerator / denominator;
	}
}
