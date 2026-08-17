package com.loadoutlab.data;

import org.junit.Assert;
import org.junit.Test;

/** A bare partial name surfaces the fight the player means - the
 * normal version, never Awakened (field report 2026-08-16). */
public class SearchVersionRankTest
{
	@Test
	public void barePartialNamePrefersTheNormalVersion()
	{
		LoadoutData data = new DataService().load();
		MonsterStats first = data.searchMonsters("whisperer", 5).get(0);
		Assert.assertEquals("The Whisperer", first.getName());
		Assert.assertNotEquals("Awakened", first.getVersion());
		MonsterStats vardorvis = data.searchMonsters("vardorvis", 5).get(0);
		Assert.assertNotEquals("Awakened", vardorvis.getVersion());
	}
}
