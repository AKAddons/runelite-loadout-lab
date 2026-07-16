package com.loadoutlab.ui;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/** The cross-plugin "search" link-in: selectExternal by name or npc id. */
public class LinkInTest
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
			(monster, f2p, slayer, wildy, book, maxTradeables, riskBudget, antifire, budget, mode, onDone) -> computed.set(monster),
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
			ids -> { });
	}

	@Test
	public void selectsByPunctuationInsensitiveNameAndTriggersACompute()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		Assert.assertTrue(panel(computed).selectExternal("kril tsutsaroth", null));
		Assert.assertNotNull(computed.get());
		Assert.assertEquals("K'ril Tsutsaroth", computed.get().getName());
	}

	@Test
	public void npcIdWinsOverTheNameWhenBothArePresent()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		MonsterStats zulrah = data.searchMonsters("zulrah", 1).get(0);
		Assert.assertTrue(panel(computed).selectExternal("callisto", zulrah.getId()));
		Assert.assertEquals("Zulrah", computed.get().getName());
	}

	@Test
	public void nicknamesAndActivityNamesResolveThroughAliases()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		Assert.assertTrue(panel(computed).selectExternal("Thermy", null));
		Assert.assertEquals("Thermonuclear smoke devil", computed.get().getName());
		Assert.assertTrue(panel(computed).selectExternal("Grotesque Guardians", null));
		Assert.assertEquals("Dusk", computed.get().getName());
		Assert.assertTrue(panel(computed).selectExternal("ToB (HM)", null));
		Assert.assertEquals("Verzik Vitur", computed.get().getName());
	}

	@Test
	public void qualifierSuffixesAreStrippedOnRetry()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		Assert.assertTrue(panel(computed).selectExternal("Doom of Mokhaiotl (L3)", null));
		Assert.assertEquals("Doom of Mokhaiotl", computed.get().getName());
		Assert.assertTrue(panel(computed).selectExternal("Duke (Awake)", null));
		Assert.assertEquals("Duke Sucellus", computed.get().getName());
	}

	@Test
	public void unknownMonsterReturnsFalseAndComputesNothing()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		Assert.assertFalse(panel(computed).selectExternal("definitely not a monster", null));
		Assert.assertNull(computed.get());
	}
}
