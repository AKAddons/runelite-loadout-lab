// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import org.junit.Assert;
import org.junit.Test;

public class DataServiceTest
{
	@Test
	public void loadsMergedGearAndMonsterDataset()
	{
		LoadoutData data = new DataService().load();
		Assert.assertTrue(data.getGearItems().size() > 5000);
		// 1,941 distinct stat blocks after collapsing combat-identical spawns
		// (2,851 raw rows - a third of the wiki list is duplicate spawns).
		Assert.assertTrue(data.getMonsters().size() > 1800);
		Assert.assertTrue(data.getSpells().size() > 20);
		Assert.assertFalse(data.searchMonsters("zulrah", 10).isEmpty());
		// Punctuation-insensitive: "kril" -> K'ril, "kreearra" -> Kree'arra.
		Assert.assertEquals("K'ril Tsutsaroth", data.searchMonsters("kril", 1).get(0).getName());
		Assert.assertEquals("Kree'arra", data.searchMonsters("kreearra", 1).get(0).getName());
	}

	@Test
	public void monsterOffensiveSheetIsParsed()
	{
		LoadoutData data = new DataService().load();
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		MonsterOffence off = graardor.getOffence();
		Assert.assertEquals(280, off.getAttackLevel());
		Assert.assertEquals(350, off.getStrengthLevel());
		Assert.assertEquals(120, off.getAttackBonus());
		Assert.assertEquals(43, off.getStrengthBonus());
		Assert.assertEquals(6, off.getSpeedTicks());
		Assert.assertTrue(off.getStyles().contains("Crush"));
		Assert.assertTrue(off.getStyles().contains("Ranged"));
	}

	@Test
	public void impossiblePvpVariantsAreNotStandardGear()
	{
		LoadoutData data = new DataService().load();
		GearItem vesta = data.getGear(22616);
		Assert.assertNotNull(vesta);
		Assert.assertFalse(vesta.isStandardGear());
	}

	@Test
	public void releasedYamaGearIsStandardNotFrozenOffAsPreRelease()
	{
		// Field report: oathplate never appeared. best-dps froze these ids
		// non-standard while unreleased and the refresh preserved the stale
		// False, so they were filtered out of every candidate pool. Now
		// live, tradeable, ordinary equip - the refresh override forces them
		// standard (see scripts/refresh_data.py STANDARD_OVERRIDES).
		LoadoutData data = new DataService().load();
		for (int id : new int[]{30750, 30753, 30756, 31106})
		{
			GearItem item = data.getGear(id);
			Assert.assertNotNull("missing gear id " + id, item);
			Assert.assertTrue(item.getName() + " must be standard gear",
				item.isStandardGear());
		}
	}

	@Test
	public void snapshotContainsPostMay2026Content()
	{
		LoadoutData data = new DataService().load();
		// Blood Moon Rises wave (June 2026): the Maggot King and the
		// Necklace of rupture, which outclasses the Necklace of anguish.
		Assert.assertFalse(data.searchMonsters("maggot king", 5).isEmpty());
		GearItem rupture = data.getGear(33639);
		Assert.assertNotNull(rupture);
		Assert.assertTrue(rupture.getBonuses().getRangedStrength()
			> data.getGear(19547).getBonuses().getRangedStrength());
	}

	@Test
	public void leaguesRewardsAreExcludedFromTheCorpus()
	{
		LoadoutData data = new DataService().load();
		// Main-game 'Echo boots' (Colosseum recoil boots) must NOT be
		// caught by the leagues echo filter.
		Assert.assertNotNull(data.getGear(28945));
		for (GearItem item : data.getGearItems())
		{
			String name = item.getName().toLowerCase();
			Assert.assertFalse("leagues-only gear must not be suggestible: " + item.getName(),
				name.startsWith("echo venator") || name.startsWith("echo virtus")
					|| name.startsWith("echo ahrim") || name.contains("trailblazer"));
		}
		// Spot-check the stat-relevant offender: Echo venator bow (charged).
		Assert.assertNull(data.getGear(30434));
	}

	@Test
	public void effectOnlySpellsAreExcludedFromTheCorpus()
	{
		LoadoutData data = new DataService().load();
		for (SpellStats spell : data.getSpells())
		{
			Assert.assertNotEquals("unselectable effect spells must not be castable",
				"Flames of Cerberus", spell.getName());
			Assert.assertNotEquals("unselectable effect spells must not be castable",
				"King's Ice Barrage", spell.getName());
		}
	}

	@Test
	public void combatIdenticalMonsterSpawnsCollapseToOneEntry()
	{
		LoadoutData data = new DataService().load();
		// Four Tormented Demon spawns share one stat block -> one unlabeled entry.
		java.util.List<MonsterStats> tds = data.searchMonsters("tormented demon", 10);
		Assert.assertEquals(1, tds.size());
		Assert.assertEquals("", tds.get(0).getVersion());
		// Dusk's two forms have different stats -> both stay, labeled.
		java.util.List<MonsterStats> dusks = data.searchMonsters("dusk", 10).stream()
			.filter(m -> m.getName().equals("Dusk")).collect(java.util.stream.Collectors.toList());
		Assert.assertEquals(2, dusks.size());
		Assert.assertFalse(dusks.get(0).getVersion().isEmpty());
	}

	@Test
	public void minigameOnlyAmmoIsExcludedFromTheCorpus()
	{
		LoadoutData data = new DataService().load();
		// Barbarian Assault arrows: ranged str 125, usable only inside BA.
		Assert.assertNull(data.getGear(22227)); // Bullet arrow
		Assert.assertNull(data.getGear(22228)); // Field arrow
		Assert.assertNull(data.getGear(22229)); // Blunt arrow
		Assert.assertNull(data.getGear(22230)); // Barbed arrow
	}

	@Test
	public void ornamentVariantsCanonicalizeToTheBaseItem()
	{
		LoadoutData data = new DataService().load();
		// Abyssal whip (or) = 12773, Volcanic abyssal whip = 12774 -> whip 4151.
		Assert.assertTrue(data.isVariant(12773));
		Assert.assertTrue(data.isVariant(12774));
		Assert.assertFalse(data.isVariant(4151));

		java.util.Map<Integer, Integer> owned = data.canonicalizeOwned(java.util.Map.of(12773, 1));
		Assert.assertEquals(1, owned.get(4151).intValue());
		Assert.assertEquals(1, owned.get(12773).intValue());

		// Degraded charge-states count too: Blood moon tassets (Used)
		// 29045 credits the base (New) 29025 the optimizer suggests.
		java.util.Map<Integer, Integer> degraded = data.canonicalizeOwned(java.util.Map.of(29045, 1));
		Assert.assertEquals(1, degraded.get(29025).intValue());

		// Corrupted/cosmetic crystal: bofa (c) and a saeldor hue variant
		// credit the Charged base - including through the (c) chain.
		Assert.assertEquals(1, data.canonicalizeOwned(java.util.Map.of(25867, 1)).get(25865).intValue());
		Assert.assertEquals(1, data.canonicalizeOwned(java.util.Map.of(25882, 1)).get(23995).intValue());
	}

	@Test
	public void equivalentIdsSpanTheWholeVariantFamily()
	{
		LoadoutData data = new DataService().load();
		// Base whip expands to its ornament variants and back.
		java.util.Set<Integer> family = data.equivalentIds(4151);
		Assert.assertTrue(family.contains(4151));
		Assert.assertTrue(family.contains(12773));
		Assert.assertTrue(data.equivalentIds(12773).contains(4151));
		// A variant-free item is just itself.
		Assert.assertEquals(java.util.Set.of(20997), data.equivalentIds(20997));
	}

	@Test
	public void loadsEquipmentRequirements()
	{
		LoadoutData data = new DataService().load();
		GearItem whip = data.getGear(4151);
		GearItem bandosChestplate = data.getGear(11832);
		Assert.assertNotNull(whip);
		Assert.assertNotNull(bandosChestplate);
		Assert.assertEquals(70, whip.getRequirements().getSkills().get("attack").intValue());
		Assert.assertEquals(65, bandosChestplate.getRequirements().getSkills().get("defence").intValue());
	}
}
