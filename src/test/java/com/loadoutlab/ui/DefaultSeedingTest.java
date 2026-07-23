package com.loadoutlab.ui;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * The Controls config seeds every NEW result's assumptions (options
 * evolution, field spec 2026-07-21): detect-vs-none gates for thralls and
 * Death Charge, the spec chip, named prayer/boost picks, and autocast-off.
 * The per-card chips stay live for overriding.
 */
public class DefaultSeedingTest
{
	private static LoadoutData data;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
	}

	private static LoadoutLabPanel panel(AtomicReference<MonsterStats> computed)
	{
		return new LoadoutLabPanel(data,
			Mockito.mock(ItemManager.class, Mockito.RETURNS_DEEP_STUBS),
			Mockito.mock(SpriteManager.class),
			(monster, f2p, slayer, wildy, book, maxTradeables, riskBudget, antifire, dcharge, specw, boostPicks, prayerPicks, budget, maxSwaps, raidBoost, onDone) -> computed.set(monster),
			itemId -> false,
			java.util.Collections::emptySet,
			new LoadoutLabPanel.ProtectOnlyToggle()
			{
				@Override
				public boolean toggle(int itemId)
				{
					return false;
				}

				@Override
				public boolean isProtectOnly(int itemId)
				{
					return false;
				}
			},
			itemId -> false,
			java.util.Collections::emptySet,
			itemId -> false,
			java.util.Collections::emptySet,
			new LoadoutLabPanel.LocationHint()
			{
				@Override
				public String hint(int itemId)
				{
					return "";
				}

				@Override
				public String primary(int itemId)
				{
					return "";
				}
			},
			new LoadoutLabPanel.MobProfile()
			{
				@Override
				public java.util.Map<com.loadoutlab.data.GearSlot, Integer> pins(
					int monsterId, com.loadoutlab.engine.CombatStyle style)
				{
					return java.util.Map.of();
				}

				@Override
				public java.util.Map<String, java.util.Map<com.loadoutlab.data.GearSlot, Integer>> allPins(int monsterId)
				{
					return java.util.Map.of();
				}

				@Override
				public void pin(int monsterId, String scope,
					com.loadoutlab.data.GearSlot slot, int itemId)
				{
				}

				@Override
				public void unpin(int monsterId, String scope, com.loadoutlab.data.GearSlot slot)
				{
				}

				@Override
				public String note(int monsterId)
				{
					return "";
				}

				@Override
				public void setNote(int monsterId, String note)
				{
				}

				@Override
				public java.util.Set<Integer> filterItems(
					int monsterId, com.loadoutlab.engine.CombatStyle style)
				{
					return java.util.Set.of();
				}

				@Override
				public java.util.Map<String, java.util.Map<Integer, String>> allFilterItems(int monsterId)
				{
					return java.util.Map.of();
				}

				@Override
				public void addFilterItem(int monsterId, String scope, int itemId, String name)
				{
				}

				@Override
				public void removeFilterItem(int monsterId, String scope, int itemId)
				{
				}

				@Override
				public String pinnedSpell(int monsterId)
				{
					return "";
				}

				@Override
				public void setPinnedSpell(int monsterId, String spellName)
				{
				}
			},
			(prompt, onPicked) -> { },
			itemId -> true,
			ids -> { },
			(ids, layout) -> { });
	}


	@Test
	public void noneGatesStartTheAssumptionsOff()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		LoadoutLabPanel panel = panel(computed);
		LoadoutLabPanel.DisplayOptions options = new LoadoutLabPanel.DisplayOptions();
		options.detectThralls = false;
		options.detectDeathCharge = false;
		options.defaultSpecWeapon = false;
		options.autocastNone = true;
		panel.setDisplayOptions(options);

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		LoadoutLabPanel.ResultEntry entry = panel.activeForTest();
		Assert.assertFalse("thralls seeded off", entry.thralls);
		Assert.assertFalse("death charge seeded off", entry.deathCharge);
		Assert.assertFalse("spec weapon seeded off", entry.specWeapon);
		Assert.assertEquals("autocast off = the No autocast spellbook index",
			4, entry.spellbookIndex);
	}

	@Test
	public void namedPicksLandOnTheirStyles()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		LoadoutLabPanel panel = panel(computed);
		LoadoutLabPanel.DisplayOptions options = new LoadoutLabPanel.DisplayOptions();
		options.defaultPrayerPicks.put(CombatStyle.MELEE, "Piety");
		options.defaultPrayerPicks.put(CombatStyle.RANGED, "Eagle Eye");
		options.defaultBoostPicks.put(CombatStyle.MAGIC, "OVERLOAD");
		options.defaultBoostPicks.put(CombatStyle.RANGED, "OVERLOAD");
		panel.setDisplayOptions(options);

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		LoadoutLabPanel.ResultEntry entry = panel.activeForTest();
		Assert.assertEquals("each style seeds independently",
			"Piety", entry.prayerPicks.get(CombatStyle.MELEE));
		Assert.assertEquals("Eagle Eye", entry.prayerPicks.get(CombatStyle.RANGED));
		Assert.assertNull("unset styles stay detect",
			entry.prayerPicks.get(CombatStyle.MAGIC));
		Assert.assertEquals("OVERLOAD", entry.boostPicks.get(CombatStyle.MAGIC));
		Assert.assertEquals("OVERLOAD", entry.boostPicks.get(CombatStyle.RANGED));
		Assert.assertNull(entry.boostPicks.get(CombatStyle.MELEE));
	}

	@Test
	public void noneSeedsPrayerlessAndUnboostedEverywhere()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		LoadoutLabPanel panel = panel(computed);
		LoadoutLabPanel.DisplayOptions options = new LoadoutLabPanel.DisplayOptions();
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			options.defaultPrayerPicks.put(style, "NONE");
			options.defaultBoostPicks.put(style, "NONE");
		}
		panel.setDisplayOptions(options);

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		LoadoutLabPanel.ResultEntry entry = panel.activeForTest();
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			Assert.assertEquals("NONE", entry.prayerPicks.get(style));
			Assert.assertEquals("NONE", entry.boostPicks.get(style));
		}
	}

	@Test
	public void detectDefaultsLeaveThePickersEmpty()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		LoadoutLabPanel panel = panel(computed);
		panel.setDisplayOptions(new LoadoutLabPanel.DisplayOptions());

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		LoadoutLabPanel.ResultEntry entry = panel.activeForTest();
		Assert.assertTrue("detect = no seeded picks", entry.prayerPicks.isEmpty());
		Assert.assertTrue(entry.boostPicks.isEmpty());
		Assert.assertTrue("spec chip defaults on", entry.specWeapon);
		Assert.assertEquals("autocast stays on Any spellbook", 0, entry.spellbookIndex);
	}
}
