package com.loadoutlab.ui;

import com.loadoutlab.command.Command;
import com.loadoutlab.command.CommandHistory;
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

/**
 * Monster selections join the SAME back/forward stack as edits (field
 * request 2026-07-16): search Zulrah, search Vorkath, hit back - you are
 * on Zulrah again. The first pick of a session is not recorded, so back
 * never lands on an empty panel.
 */
public class SelectionHistoryTest
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

	/** A HistoryControl over a real CommandHistory - the plugin's wiring. */
	private static LoadoutLabPanel.HistoryControl control(CommandHistory history)
	{
		return new LoadoutLabPanel.HistoryControl()
		{
			@Override
			public boolean undo()
			{
				return history.undo();
			}

			@Override
			public boolean redo()
			{
				return history.redo();
			}

			@Override
			public boolean canUndo()
			{
				return history.canUndo();
			}

			@Override
			public boolean canRedo()
			{
				return history.canRedo();
			}

			@Override
			public String undoLabel()
			{
				return history.peekUndoDescription();
			}

			@Override
			public String redoLabel()
			{
				return history.peekRedoDescription();
			}

			@Override
			public boolean execute(Command command)
			{
				return history.execute(command);
			}
		};
	}

	@Test
	public void backReturnsToThePreviousSearchAndForwardComesBack()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		Assert.assertEquals("Zulrah", computed.get().getName());
		Assert.assertTrue(panel.selectExternal("vorkath", null));
		Assert.assertEquals("Vorkath", computed.get().getName());

		Assert.assertTrue(history.undo());
		Assert.assertEquals("back lands on the previous search",
			"Zulrah", computed.get().getName());

		Assert.assertTrue(history.redo());
		Assert.assertEquals("forward returns to the newer search",
			"Vorkath", computed.get().getName());
	}

	@Test
	public void theFirstPickIsNotRecordedSoBackNeverBlanksThePanel()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		Assert.assertFalse("nothing to go back to after the first pick", history.canUndo());
	}

	@Test
	public void repickingTheSameMonsterAddsNoHistoryStep()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		Assert.assertTrue(panel.selectExternal("zulrah", null));
		Assert.assertFalse(history.canUndo());
	}
}
