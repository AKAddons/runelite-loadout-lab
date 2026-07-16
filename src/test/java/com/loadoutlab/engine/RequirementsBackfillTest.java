package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wear-requirements backfill (2026-07 player audit A1.1): the curated
 * file's coverage stopped ~Feb 2022, so ~4 years of meta gear had no
 * requirement rows and was recommended to accounts that cannot wear it
 * (a fang at 40 Attack; a 500m budget buying Oathplate for a fresh
 * account). These lock the flagship rows - all wiki-cited, 2026-07-15.
 */
class RequirementsBackfillTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
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

	private static RequirementProfile freshAccount()
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.ATTACK, 40);
		levels.put(Skill.STRENGTH, 40);
		levels.put(Skill.DEFENCE, 30);
		levels.put(Skill.RANGED, 30);
		levels.put(Skill.MAGIC, 25);
		return new RequirementProfile(levels, java.util.Set.of());
	}

	@Test
	@DisplayName("post-2022 flagship gear now carries its wiki requirements")
	void flagshipRowsExist()
	{
		assertEquals(82, byName("osmumten's fang").getRequirements().getSkills().get("attack"));
		assertEquals(85, byName("tumeken's shadow").getRequirements().getSkills().get("magic"));
		assertEquals(80, byName("torva full helm").getRequirements().getSkills().get("defence"));
		assertEquals(80, byName("masori mask").getRequirements().getSkills().get("ranged"));
		assertEquals(75, byName("voidwaker").getRequirements().getSkills().get("attack"));
		assertEquals(78, byName("oathplate chest").getRequirements().getSkills().get("defence"));
		assertEquals(80, byName("zaryte crossbow").getRequirements().getSkills().get("ranged"));
		// Avernic treads gate on four skills at 80.
		Map<String, Integer> treads = byName("avernic treads").getRequirements().getSkills();
		assertEquals(80, treads.get("defence"));
		assertEquals(80, treads.get("strength"));
		assertEquals(80, treads.get("ranged"));
		assertEquals(80, treads.get("magic"));
	}

	@Test
	@DisplayName("stale rows corrected: bowfa is 80 Ranged + 70 Agility, saeldor 80 Attack")
	void staleRowsCorrected()
	{
		Map<String, Integer> bowfa = byName("bow of faerdhinen").getRequirements().getSkills();
		assertEquals(80, bowfa.get("ranged"));
		assertEquals(70, bowfa.get("agility"));
		assertEquals(80, byName("blade of saeldor").getRequirements().getSkills().get("attack"));
	}

	@Test
	@DisplayName("a 40-Attack fresh account can no longer equip the fang (the audit's headline repro)")
	void freshAccountCannotWearFang()
	{
		RequirementProfile fresh = freshAccount();
		assertFalse(fresh.canEquip(byName("osmumten's fang").getRequirements()));
		assertFalse(fresh.canEquip(byName("masori body").getRequirements()));
		assertFalse(fresh.canEquip(byName("tumeken's shadow").getRequirements()));
		assertFalse(fresh.canEquip(byName("oathplate helm").getRequirements()));
		// Sanity: gear in their bracket still fits.
		assertTrue(fresh.canEquip(byName("rune scimitar").getRequirements()));
		assertTrue(RequirementProfile.MAXED.canEquip(byName("osmumten's fang").getRequirements()));
	}
}
