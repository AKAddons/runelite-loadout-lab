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
		// The blighted (wilderness-only) variants sit ahead of each head.
		Assert.assertEquals("MOONLIGHT_ANTELOPE", firstLandOption(TripSupplies.FOOD).key);
		Assert.assertEquals("KARAMBWAN", firstLandOption(TripSupplies.FAST_FOOD).key);
		Assert.assertEquals("SUPER_RESTORE", firstLandOption(TripSupplies.PRAYER_RESTORE).key);
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
		TripSupplies.Option pick = TripSupplies.detectBest(TripSupplies.PRAYER_RESTORE, owned::contains, false);
		Assert.assertNotNull(pick);
		Assert.assertEquals("PRAYER_POTION", pick.key);

		// Adding a super restore (3-dose, 3026) upgrades the pick.
		owned.add(3026);
		Assert.assertEquals("SUPER_RESTORE",
			TripSupplies.detectBest(TripSupplies.PRAYER_RESTORE, owned::contains, false).key);
	}

	@Test
	public void detectBestReturnsNullWhenNothingIsOwned()
	{
		Assert.assertNull(TripSupplies.detectBest(TripSupplies.FOOD, id -> false, false));
		Assert.assertNull(TripSupplies.detectBest(TripSupplies.FOOD, id -> false, true));
	}

	/** Andrew, 2026-09-01: "wildy food / supplies should prefer blighted
	 * options" - usable only in the Wilderness, cheap to lose there. Ids
	 * from the cache (gameval ItemID, 2026-09-02): blighted anglerfish
	 * 24592, manta 24589, karambwan 24595, super restore 24598-24605. */
	@Test
	public void wildernessTripPrefersBankedBlightedSupplies()
	{
		Set<Integer> owned = new HashSet<>();
		owned.add(13441); // anglerfish
		owned.add(24592); // blighted anglerfish
		owned.add(3144);  // karambwan
		owned.add(24595); // blighted karambwan
		owned.add(3024);  // super restore(4)
		owned.add(24605); // blighted super restore(1)
		Assert.assertEquals("BLIGHTED_ANGLERFISH",
			TripSupplies.detectBest(TripSupplies.FOOD, owned::contains, true).key);
		Assert.assertEquals("BLIGHTED_KARAMBWAN",
			TripSupplies.detectBest(TripSupplies.FAST_FOOD, owned::contains, true).key);
		Assert.assertEquals("BLIGHTED_SUPER_RESTORE",
			TripSupplies.detectBest(TripSupplies.PRAYER_RESTORE, owned::contains, true).key);
		// Without a banked blighted variant the wilderness falls back to
		// the tradeable tier.
		owned.remove(24592);
		Assert.assertEquals("ANGLERFISH",
			TripSupplies.detectBest(TripSupplies.FOOD, owned::contains, true).key);
	}

	@Test
	public void blightedSuppliesAreNeverPickedOffTheWilderness()
	{
		Set<Integer> owned = new HashSet<>();
		owned.add(24592); // blighted anglerfish only
		owned.add(24598); // blighted super restore(4) only
		Assert.assertNull(TripSupplies.detectBest(TripSupplies.FOOD, owned::contains, false));
		Assert.assertNull(TripSupplies.detectBest(TripSupplies.PRAYER_RESTORE, owned::contains, false));
		// The explicit per-category default still resolves (the user's call).
		Assert.assertNotNull(TripSupplies.option(TripSupplies.FOOD, "BLIGHTED_ANGLERFISH"));
		for (String category : new String[]{TripSupplies.SURGE, TripSupplies.SPELLBOOK_CAPE,
			TripSupplies.ANTIVENOM})
		{
			for (TripSupplies.Option o : TripSupplies.options(category))
			{
				Assert.assertFalse(o.key + " has no blighted variant", o.wildyOnly);
			}
		}
	}

	private static TripSupplies.Option firstLandOption(String category)
	{
		for (TripSupplies.Option o : TripSupplies.options(category))
		{
			if (!o.wildyOnly)
			{
				return o;
			}
		}
		return null;
	}

	@Test
	public void prayerRegenerationIsNeverAutoDetected()
	{
		// Its over-time mechanism must not win a detect - explicitly
		// selectable only (detect:false in the resource).
		Set<Integer> owned = new HashSet<>();
		owned.add(30125); // Prayer regeneration potion(4)
		Assert.assertNull(TripSupplies.detectBest(TripSupplies.PRAYER_RESTORE, owned::contains, false));
		Assert.assertNotNull(TripSupplies.option(TripSupplies.PRAYER_RESTORE, "PRAYER_REGENERATION"));
	}

	@Test
	public void spellbookCapeDetectPrefersTheMaxCape()
	{
		Set<Integer> owned = new HashSet<>();
		owned.add(9763); // Magic cape(t)
		Assert.assertEquals("MAGIC_CAPE",
			TripSupplies.detectBest(TripSupplies.SPELLBOOK_CAPE, owned::contains, false).key);
		owned.add(13280); // Max cape
		Assert.assertEquals("MAX_CAPE",
			TripSupplies.detectBest(TripSupplies.SPELLBOOK_CAPE, owned::contains, false).key);
	}

	@Test
	public void onlyTheSpellbookCapesAreUtilityPlacement()
	{
		// Utility = fight-relevant gear that is neither worn nor carried:
		// the bank layout's third strip. Consumables stay inventory-placed.
		for (TripSupplies.Option o : TripSupplies.options(TripSupplies.SPELLBOOK_CAPE))
		{
			Assert.assertTrue(o.key + " should be utility", o.utility);
		}
		for (String category : new String[]{TripSupplies.FOOD, TripSupplies.FAST_FOOD,
			TripSupplies.PRAYER_RESTORE, TripSupplies.SURGE, TripSupplies.ANTIVENOM})
		{
			for (TripSupplies.Option o : TripSupplies.options(category))
			{
				Assert.assertFalse(o.key + " should not be utility", o.utility);
			}
		}
	}

	@Test
	public void spellKitsCarryTheVerifiedArceuusCosts()
	{
		// Wiki-verified 2026-07-21: greater resurrect 10 fire 5 blood
		// 1 cosmic; Death Charge 1 death 1 blood 1 soul; Mark of Darkness
		// 1 cosmic 1 soul; pouch detect order divine > base.
		org.junit.Assert.assertArrayEquals(new int[]{554, 565, 564},
			TripSupplies.spellKit("thrallGreaterRunes"));
		org.junit.Assert.assertArrayEquals(new int[]{560, 565, 566},
			TripSupplies.spellKit("deathChargeRunes"));
		org.junit.Assert.assertArrayEquals(new int[]{564, 566},
			TripSupplies.spellKit("markOfDarknessRunes"));
		org.junit.Assert.assertEquals(27281, TripSupplies.spellKit("runePouch")[0]);
		org.junit.Assert.assertArrayEquals(new int[]{9075, 564, 563},
			TripSupplies.spellKit("spellbookSwapRunes"));
		org.junit.Assert.assertArrayEquals(new int[]{9075, 557, 560},
			TripSupplies.spellKit("vengeanceRunes"));
		org.junit.Assert.assertEquals(0, TripSupplies.spellKit("noSuchKit").length);
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
