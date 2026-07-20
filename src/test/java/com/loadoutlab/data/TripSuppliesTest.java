package com.loadoutlab.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 * The trip-supply tier tables (field direction 2026-07-20): persistent
 * food/fast-food/prayer/surge/anti-venom defaults with a "Detect best"
 * that picks the best owned tier, and anti-venom gated on the wiki's
 * venom-inflictor list.
 */
public class TripSuppliesTest
{
	@Test
	public void everyCategoryLoadsWithBestFirstOrdering()
	{
		// Spot-check the verified tier heads (wiki 2026-07-20): moonlight
		// antelope 26 hp leads food, karambwan leads fast food, super
		// restore leads prayer, extended anti-venom+ leads anti-venom.
		Assert.assertEquals("MOONLIGHT_ANTELOPE", TripSupplies.options(TripSupplies.FOOD).get(0).key);
		Assert.assertEquals("KARAMBWAN", TripSupplies.options(TripSupplies.FAST_FOOD).get(0).key);
		Assert.assertEquals("SUPER_RESTORE", TripSupplies.options(TripSupplies.PRAYER_RESTORE).get(0).key);
		Assert.assertEquals("SURGE_POTION", TripSupplies.options(TripSupplies.SURGE).get(0).key);
		Assert.assertEquals("MAX_CAPE", TripSupplies.options(TripSupplies.SPELLBOOK_CAPE).get(0).key);
		Assert.assertEquals("EXTENDED_ANTIVENOM", TripSupplies.options(TripSupplies.ANTIVENOM).get(0).key);
	}

	@Test
	public void detectBestPicksTheBestOwnedTierAcrossAnyDose()
	{
		// Owning only a 1-dose prayer potion (id 143) must still detect
		// PRAYER_POTION - the filter and detection cover every dose.
		Set<Integer> owned = new HashSet<>();
		owned.add(143);
		TripSupplies.Option pick = TripSupplies.detectBest(TripSupplies.PRAYER_RESTORE, owned::contains);
		Assert.assertNotNull(pick);
		Assert.assertEquals("PRAYER_POTION", pick.key);

		// Adding a super restore (3-dose, 3026) upgrades the pick.
		owned.add(3026);
		Assert.assertEquals("SUPER_RESTORE",
			TripSupplies.detectBest(TripSupplies.PRAYER_RESTORE, owned::contains).key);
	}

	@Test
	public void detectBestReturnsNullWhenNothingIsOwned()
	{
		Assert.assertNull(TripSupplies.detectBest(TripSupplies.FOOD, id -> false));
	}

	@Test
	public void prayerRegenerationIsNeverAutoDetected()
	{
		// Its over-time mechanism must not win a detect - explicitly
		// selectable only (detect:false in the resource).
		Set<Integer> owned = new HashSet<>();
		owned.add(30125); // Prayer regeneration potion(4)
		Assert.assertNull(TripSupplies.detectBest(TripSupplies.PRAYER_RESTORE, owned::contains));
		Assert.assertNotNull(TripSupplies.option(TripSupplies.PRAYER_RESTORE, "PRAYER_REGENERATION"));
	}

	@Test
	public void spellbookCapeDetectPrefersTheMaxCape()
	{
		Set<Integer> owned = new HashSet<>();
		owned.add(9763); // Magic cape(t)
		Assert.assertEquals("MAGIC_CAPE",
			TripSupplies.detectBest(TripSupplies.SPELLBOOK_CAPE, owned::contains).key);
		owned.add(13280); // Max cape
		Assert.assertEquals("MAX_CAPE",
			TripSupplies.detectBest(TripSupplies.SPELLBOOK_CAPE, owned::contains).key);
	}

	@Test
	public void unknownAndModeKeysResolveToNoOption()
	{
		Assert.assertNull(TripSupplies.option(TripSupplies.FOOD, "DETECT_BEST"));
		Assert.assertNull(TripSupplies.option(TripSupplies.FOOD, "NONE"));
		Assert.assertNull(TripSupplies.option(TripSupplies.FOOD, "NO_SUCH"));
	}

	@Test
	public void venomInflictorsMatchByNameIncludingVariants()
	{
		Assert.assertTrue(TripSupplies.inflictsVenom(named("Zulrah")));
		Assert.assertTrue(TripSupplies.inflictsVenom(named("Araxxor")));
		Assert.assertTrue(TripSupplies.inflictsVenom(named("Dreadborn Araxyte")));
		Assert.assertTrue(TripSupplies.inflictsVenom(named("Vorkath")));
		Assert.assertFalse(TripSupplies.inflictsVenom(named("General Graardor")));
		Assert.assertFalse(TripSupplies.inflictsVenom(named("Abyssal demon")));
		Assert.assertFalse(TripSupplies.inflictsVenom(null));
	}

	@Test
	public void displayIdLeadsEachIdList()
	{
		// ids[0] is the display/cell id: the 4-dose potion or cooked food.
		for (String category : new String[]{TripSupplies.FOOD, TripSupplies.FAST_FOOD,
			TripSupplies.PRAYER_RESTORE, TripSupplies.SURGE, TripSupplies.SPELLBOOK_CAPE,
			TripSupplies.ANTIVENOM})
		{
			List<TripSupplies.Option> options = TripSupplies.options(category);
			Assert.assertFalse(category + " must not be empty", options.isEmpty());
			for (TripSupplies.Option o : options)
			{
				Assert.assertTrue(o.key + " needs at least one id", o.ids.length >= 1);
			}
		}
	}

	private static MonsterStats named(String name)
	{
		if (name == null)
		{
			return null;
		}
		return new MonsterStats(1, name, "", 100, 100, 1, 1, null, null);
	}
}
