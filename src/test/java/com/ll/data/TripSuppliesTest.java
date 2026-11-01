package com.ll.data;

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
public class TripSuppliesTest {
	@Test
	public void everyCategoryLoadsWithBestFirstOrdering() {
		// Spot-check the verified tier heads (wiki 2026-07-20): moonlight
		// antelope 26 hp leads food, karambwan leads fast food, super
		// restore leads prayer, extended anti-venom+ leads anti-venom.
		Assert.assertEquals("MOONLIGHT_ANTELOPE", Trip.options(Trip.FOOD).get(0).key);
		Assert.assertEquals("KARAMBWAN", Trip.options(Trip.FAST_FOOD).get(0).key);
		Assert.assertEquals("SUPER_RESTORE", Trip.options(Trip.PRAYER_RESTORE).get(0).key);
		Assert.assertEquals("SURGE_POTION", Trip.options(Trip.SURGE).get(0).key);
		Assert.assertEquals("MAX_CAPE", Trip.options(Trip.SPELLBOOK_CAPE).get(0).key);
		Assert.assertEquals("EXTENDED_ANTIVENOM", Trip.options(Trip.ANTIVENOM).get(0).key);
	}

	@Test
	public void detectBestPicksTheBestOwnedTierAcrossAnyDose() {
		// Owning only a 1-dose prayer potion (id 143) must still detect
		// PRAYER_POTION - the filter and detection cover every dose.
		Set<Integer> owned = new HashSet<>();
		owned.add(143);
		Trip.Option pick = Trip.detectBest(Trip.PRAYER_RESTORE, owned::contains);
		Assert.assertNotNull(pick);
		Assert.assertEquals("PRAYER_POTION", pick.key);

		// Adding a super restore (3-dose, 3026) upgrades the pick.
		owned.add(3026);
		Assert.assertEquals("SUPER_RESTORE",
			Trip.detectBest(Trip.PRAYER_RESTORE, owned::contains).key);
	}

	/** Andrew 2026-09-01: "wildy food / supplies should prefer blighted
	 * options". Blighted variants are wilderness-only (wiki), so a trip
	 * there takes a banked one over any tradeable tier, and a land trip
	 * never picks one (ids objtypes-verified 2026-09-02). */
	@Test
	public void blightedSuppliesWinInTheWildernessAndNeverOnLand() {
		Set<Integer> bank = new HashSet<>(List.of(29143, 24592, 3024, 24598, 3144, 24595));
		Assert.assertEquals("BLIGHTED_ANGLERFISH",
			Trip.detectBest(Trip.options(Trip.FOOD), bank::contains, true).key);
		Assert.assertEquals("BLIGHTED_SUPER_RESTORE",
			Trip.detectBest(Trip.options(Trip.PRAYER_RESTORE), bank::contains, true).key);
		Assert.assertEquals("BLIGHTED_KARAMBWAN",
			Trip.detectBest(Trip.options(Trip.FAST_FOOD), bank::contains, true).key);
		// Land: the best tradeable tier, blighted ignored even when owned.
		Assert.assertEquals("MOONLIGHT_ANTELOPE",
			Trip.detectBest(Trip.options(Trip.FOOD), bank::contains, false).key);
		Set<Integer> onlyBlighted = Set.of(24592);
		Assert.assertNull("blighted cannot be eaten outside the wilderness",
			Trip.detectBest(Trip.options(Trip.FOOD), onlyBlighted::contains, false));
		// Wilderness without a blighted stack falls through to the tiers.
		Set<Integer> sharks = Set.of(385);
		Assert.assertEquals("SHARK",
			Trip.detectBest(Trip.options(Trip.FOOD), sharks::contains, true).key);
		// The two-arg detect is the land rule.
		Assert.assertNull(Trip.detectBest(Trip.FOOD, onlyBlighted::contains));
	}

	@Test
	public void detectBestReturnsNullWhenNothingIsOwned() {
		Assert.assertNull(Trip.detectBest(Trip.FOOD, id -> false));
	}

	@Test
	public void prayerRegenerationIsNeverAutoDetected() {
		// Its over-time mechanism must not win a detect - explicitly
		// selectable only (detect:false in the resource).
		Set<Integer> owned = new HashSet<>();
		owned.add(30125); // Prayer regeneration potion(4)
		Assert.assertNull(Trip.detectBest(Trip.PRAYER_RESTORE, owned::contains));
		Assert.assertNotNull(Trip.option(Trip.PRAYER_RESTORE, "PRAYER_REGENERATION"));
	}

	/** Field report 2026-10-31 (an ironman, Discord): "please give us moonlight
	 * moth mixes as an option". Wiki: 22 prayer points a dose, two doses, no
	 * Herblore - the prayer potion for irons, weaker per dose than one. */
	@Test
	public void moonlightMothMixIsAPrayerRestoreBelowThePrayerPotion() {
		Trip.Option mix = Trip.option(Trip.PRAYER_RESTORE, "MOONLIGHT_MOTH_MIX");
		Assert.assertNotNull("the mix is a prayer restore option", mix);
		Assert.assertEquals("Moonlight moth mix", mix.name);
		Assert.assertArrayEquals("two-dose leads the cell, one-dose rides the filter", new int[]{29195, 29213}, mix.ids);
		Assert.assertTrue("a real restore, so Detect best may pick it", mix.detect);
		List<String> keys = new java.util.ArrayList<>();
		for (Trip.Option o : Trip.options(Trip.PRAYER_RESTORE)) keys.add(o.key);
		Assert.assertTrue("after the prayer potion", keys.indexOf("PRAYER_POTION") < keys.indexOf("MOONLIGHT_MOTH_MIX"));
		Assert.assertTrue("before prayer regeneration", keys.indexOf("MOONLIGHT_MOTH_MIX") < keys.indexOf("PRAYER_REGENERATION"));
		// An iron with only mixes banked detects them; a prayer potion in the bank still wins.
		Assert.assertEquals("MOONLIGHT_MOTH_MIX", Trip.detectBest(Trip.PRAYER_RESTORE, id -> id == 29213).key);
		Assert.assertEquals("PRAYER_POTION", Trip.detectBest(Trip.PRAYER_RESTORE, id -> id == 29213 || id == 143).key);
	}

	@Test
	public void spellbookCapeDetectPrefersTheMaxCape() {
		Set<Integer> owned = new HashSet<>();
		owned.add(9763); // Magic cape(t)
		Assert.assertEquals("MAGIC_CAPE",
			Trip.detectBest(Trip.SPELLBOOK_CAPE, owned::contains).key);
		owned.add(13280); // Max cape
		Assert.assertEquals("MAX_CAPE",
			Trip.detectBest(Trip.SPELLBOOK_CAPE, owned::contains).key);
	}

	@Test
	public void onlyTheSpellbookCapesAreUtilityPlacement() {
		// Utility = fight-relevant gear that is neither worn nor carried:
		// the bank layout's third strip. Consumables stay inventory-placed.
		for (Trip.Option o : Trip.options(Trip.SPELLBOOK_CAPE)) {
			Assert.assertTrue(o.key + " should be utility", o.utility);
		}
		for (String category : new String[]{Trip.FOOD, Trip.FAST_FOOD,
			Trip.PRAYER_RESTORE, Trip.SURGE, Trip.ANTIVENOM}) {
			for (Trip.Option o : Trip.options(category)) {
				Assert.assertFalse(o.key + " should not be utility", o.utility);
			}
		}
	}

	@Test
	public void theCannonIsAnExplicitFiveCellKit() {
		// miko (Discord 2026-10-17) wanted the cannon in every setup; AKAddons:
		// "an easy option to include it in inventory" - no dps modelled, so
		// Detect best never picks it; chosen, it is one cell per part.
		List<Trip.Option> cannon = Trip.options(Trip.CANNON);
		Assert.assertEquals(1, cannon.size());
		Assert.assertEquals("DWARF_MULTICANNON", cannon.get(0).key);
		Assert.assertArrayEquals(new int[]{6, 8, 10, 12, 2}, cannon.get(0).ids);
		Assert.assertEquals(5, cannon.get(0).names.length);
		Assert.assertFalse(cannon.get(0).detect);
		Assert.assertNull(Trip.detectBest(Trip.CANNON, id -> true));
		// Each cell carries its variants too (Andrew 2026-10-18: the ornamented
		// parts and granite cannonballs must light up in the filter).
		Assert.assertEquals(5, cannon.get(0).cells.length);
		Assert.assertArrayEquals(new int[]{6, 26520}, cannon.get(0).cells[0]);
		Assert.assertArrayEquals(new int[]{2, 21728}, cannon.get(0).cells[4]);
		Assert.assertEquals("Cannon", Trip.label(Trip.CANNON));
		Assert.assertEquals("Food", Trip.label(Trip.FOOD));
		Assert.assertNull("repair kits never show in the chip menu",
			Trip.label(Trip.SHIP_REPAIR_KIT));
	}

	@Test
	public void unknownAndModeKeysResolveToNoOption() {
		Assert.assertNull(Trip.option(Trip.FOOD, "DETECT_BEST"));
		Assert.assertNull(Trip.option(Trip.FOOD, "NONE"));
		Assert.assertNull(Trip.option(Trip.FOOD, "NO_SUCH"));
	}

	@Test
	public void venomInflictorsMatchByNameIncludingVariants() {
		Assert.assertTrue(Trip.inflictsVenom(named("Zulrah")));
		Assert.assertTrue(Trip.inflictsVenom(named("Araxxor")));
		Assert.assertTrue(Trip.inflictsVenom(named("Dreadborn Araxyte")));
		Assert.assertTrue(Trip.inflictsVenom(named("Vorkath")));
		Assert.assertFalse(Trip.inflictsVenom(named("General Graardor")));
		Assert.assertFalse(Trip.inflictsVenom(named("Abyssal demon")));
		Assert.assertFalse(Trip.inflictsVenom(null));
	}

	@Test
	public void displayIdLeadsEachIdList() {
		// ids[0] is the display/cell id: the 4-dose potion or cooked food.
		for (String category : new String[]{Trip.FOOD, Trip.FAST_FOOD,
			Trip.PRAYER_RESTORE, Trip.SURGE, Trip.SPELLBOOK_CAPE,
			Trip.ANTIVENOM}) {
			List<Trip.Option> options = Trip.options(category);
			Assert.assertFalse(category + " must not be empty", options.isEmpty());
			for (Trip.Option o : options) {
				Assert.assertTrue(o.key + " needs at least one id", o.ids.length >= 1);
			}
		}
	}

	private static Monster named(String name) {
		if (name == null) {
			return null;
		}
		return new Monster(1, name, "", 100, 100, 1, 1, null, null);
	}

	@Test
	public void cureMeAndThePrayerBookAreChoicesNeverADetectBestPick() {
		Trip.Option cureMe = Trip.option(Trip.ANTIVENOM, "CURE_ME");
		Trip.Option book = Trip.option(Trip.ANTIVENOM, "PRAYER_BOOK");
		org.junit.jupiter.api.Assertions.assertNotNull(cureMe, "Cure Me option");
		org.junit.jupiter.api.Assertions.assertNotNull(book, "Prayer book option");
		org.junit.jupiter.api.Assertions.assertFalse(cureMe.detect);
		org.junit.jupiter.api.Assertions.assertFalse(book.detect);
		org.junit.jupiter.api.Assertions.assertEquals(9075, cureMe.ids[0], "astral rune leads the Cure Me kit");
		org.junit.jupiter.api.Assertions.assertEquals(10890, book.ids[0], "the prayer book itself leads its kit");
		// Kits name every item so the card shows each one (field 2026-09-08: the holy symbol was filter-only).
		org.junit.jupiter.api.Assertions.assertArrayEquals(new String[]{"Astral rune (Cure Me)", "Cosmic rune (Cure Me)", "Law rune (Cure Me)"}, cureMe.names);
		org.junit.jupiter.api.Assertions.assertArrayEquals(new String[]{"Prayer book", "Holy symbol"}, book.names);
		org.junit.jupiter.api.Assertions.assertEquals(0, Trip.option(Trip.ANTIVENOM, "ANTIVENOM_PLUS").names.length, "potions are one cell");
		// Owning every rune and the book still leaves Detect-best on the potions.
		Trip.Option detected = Trip.detectBest(Trip.ANTIVENOM,
			id -> id == 9075 || id == 564 || id == 563 || id == 10890 || id == 1718);
		org.junit.jupiter.api.Assertions.assertNull(detected);
	}

	@Test
	public void antipoisonCoversThePoisonersTheVenomListDoesNot() {
		Tables data = new DataService().load();
		Monster rex = data.searchMonsters("Dagannoth Rex", 1).get(0);
		Monster zulrah = data.searchMonsters("Zulrah", 1).get(0);
		Assert.assertTrue(Trip.inflictsPoison(rex));
		Assert.assertFalse(Trip.inflictsVenom(rex));
		Assert.assertFalse("venom is its own category", Trip.inflictsPoison(zulrah));
		Assert.assertEquals("ANTIDOTE_PP", Trip.options(Trip.ANTIPOISON).get(0).key);
		Assert.assertEquals("ANTIPOISON",
			Trip.detectBest(Trip.ANTIPOISON, id -> id == 175 || id == 9075).key);
		Assert.assertNotNull(Trip.option(Trip.ANTIPOISON, "CURE_ME"));
		Assert.assertTrue(java.util.Arrays.asList(Trip.CATEGORY_KEYS).contains(Trip.ANTIPOISON));
	}

	/** Kendyy (Discord 2026-10-19): antipoison rode the Sarachnis trip; her
	 * wiki infobox says poisonous = No. The name had been on the list. */
	@Test
	public void sarachnisDoesNotPoison() {
		Monster sarachnis = new DataService().load().searchMonsters("sarachnis", 1).get(0);
		Assert.assertEquals("Sarachnis", sarachnis.getName());
		Assert.assertFalse("wiki infobox: poisonous = No", Trip.inflictsPoison(sarachnis));
		Assert.assertFalse(Trip.inflictsVenom(sarachnis));
	}
}
