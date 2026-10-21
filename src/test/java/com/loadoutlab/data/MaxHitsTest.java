package com.loadoutlab.data;

import com.loadoutlab.data.MaxHits.Family;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The corpus max_hit is wiki infobox text, one or more labelled values
 * (F-01M5FY64VERY7X0PG7S1A5J373: never loaded until now). Every shape
 * below is a real corpus string.
 */
class MaxHitsTest
{
	private static int of(String raw, Family family)
	{
		return MaxHits.parse(raw).of(family);
	}

	@Test
	@DisplayName("a labelled value lands on its family and nowhere else")
	void labelledValue()
	{
		assertEquals(13, of("13 (Ranged)", Family.RANGED));
		assertEquals(-1, of("13 (Ranged)", Family.MELEE));
		assertEquals(55, of("55 (Crush)", Family.MELEE));
		assertEquals(21, of("21 (Melee)", Family.MELEE));
		assertEquals(20, of("20 (Flies)", Family.RANGED));
	}

	@Test
	@DisplayName("a plain number or a 'normal' label is the general max")
	void generalValue()
	{
		assertEquals(8, of("8", Family.GENERAL));
		assertEquals(-1, of("8", Family.MAGIC));
		assertEquals(34, of("34 (normal)", Family.GENERAL));
		assertEquals(10, of("10?", Family.GENERAL));
		assertEquals(46, of("46 (auto-attacks)", Family.GENERAL));
	}

	@Test
	@DisplayName("ranges and alternatives take the larger value")
	void rangesTakeTheMax()
	{
		assertEquals(37, of("30-37 (Melee)\u007f\u007f", Family.MELEE));
		assertEquals(60, of("50/60 (Melee)", Family.MELEE));
	}

	@Test
	@DisplayName("multi-hit attacks count their total")
	void multiHitTotals()
	{
		assertEquals(14, of("7x2 (Magic)", Family.MAGIC));
		assertEquals(60, of("30 (x2) (Melee)", Family.MELEE));
		assertEquals(6, of("2 (x3)", Family.GENERAL));
		assertEquals(30, of("26 (melee)<br/> 15x2 (ranged)<br/> 65 (special attack)", Family.RANGED));
		assertEquals(32, of("32 total 4+8+20", Family.GENERAL));
	}

	@Test
	@DisplayName("several entries split on breaks, list bullets and plain runs")
	void severalEntries()
	{
		String araxxor = "38 (melee) <br/> 21 (magic) <br/> 34 (ranged)";
		assertEquals(38, of(araxxor, Family.MELEE));
		assertEquals(21, of(araxxor, Family.MAGIC));
		assertEquals(34, of(araxxor, Family.RANGED));
		assertEquals(15, of("15 (melee) 33 (special attack)", Family.MELEE));
		String list = "<div class=\"plainlist \" >\n*16 (Stab)\n*50 (Dragonfire)\n</div>";
		assertEquals(16, of(list, Family.MELEE));
		assertEquals(22, of("22 (standard)<br/>34 (Icicle Crash)", Family.GENERAL));
	}

	@Test
	@DisplayName("a label naming two families feeds both")
	void twoFamilyLabel()
	{
		assertEquals(46, of("46 (Magic / Ranged)", Family.MAGIC));
		assertEquals(46, of("46 (Magic / Ranged)", Family.RANGED));
	}

	@Test
	@DisplayName("specials, dragonfire and non-attackers give no regular max")
	void ignoredValues()
	{
		assertEquals(-1, of("99 (charge)", Family.GENERAL));
		assertEquals(50, of("50 <br/>80 (charge)", Family.GENERAL));
		assertEquals(-1, of("50 (Dragonfire)", Family.MELEE));
		assertTrue(MaxHits.parse("0 (Does not attack)").isEmpty());
		assertTrue(MaxHits.parse("N/A").isEmpty());
		assertTrue(MaxHits.parse("Varies").isEmpty());
		assertTrue(MaxHits.parse(null).isEmpty());
	}
}
