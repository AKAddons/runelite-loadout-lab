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
			(monster, f2p, slayer, wildy, book, maxTradeables, riskBudget, antifire, budget, mode, maxSwaps, onDone) -> computed.set(monster),
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
	public void parameterChangesAndSelectionsInterleaveInOneStream()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));          // unrecorded first pick
		panel.optimizeModeForTest().setSelectedIndex(1);                  // step: Optimize: Balanced
		Assert.assertTrue(panel.selectExternal("vorkath", null));         // step: vs Vorkath

		Assert.assertTrue(history.undo());
		Assert.assertEquals("first back re-lands the previous search",
			"Zulrah", computed.get().getName());
		Assert.assertEquals("the optimize step is untouched so far",
			1, panel.optimizeModeForTest().getSelectedIndex());

		Assert.assertTrue(history.undo());
		Assert.assertEquals("second back reverts the optimize change",
			0, panel.optimizeModeForTest().getSelectedIndex());
		Assert.assertFalse(history.canUndo());

		Assert.assertTrue(history.redo());
		Assert.assertEquals(1, panel.optimizeModeForTest().getSelectedIndex());
		Assert.assertTrue(history.redo());
		Assert.assertEquals("Vorkath", computed.get().getName());
	}

	@Test
	public void parameterStepsReplayInTheMobContextTheyWereTakenOn()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("general graardor", null));
		panel.optimizeModeForTest().setSelectedIndex(2);               // step: Tanky @ Graardor
		panel.clearSelectionForTest();                                 // step: Clear - Graardor
		computed.set(null);

		Assert.assertEquals("the step names its mob",
			"Clear - General Graardor", history.peekUndoDescription());
		Assert.assertTrue(history.undo());                             // back over the clear
		Assert.assertEquals("General Graardor", computed.get().getName());

		Assert.assertTrue(history.undo());                             // back over Tanky
		Assert.assertEquals("the toggle reverts WITH its mob on screen, not in a vacuum",
			0, panel.optimizeModeForTest().getSelectedIndex());
		Assert.assertEquals("General Graardor", computed.get().getName());
	}

	@Test
	public void selectingAfterAClearIsARecordedStepBackToTheClearedState()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));       // first pick: unrecorded
		panel.clearSelectionForTest();                                 // step
		Assert.assertTrue(panel.selectExternal("vorkath", null));      // step (post-clear pick)

		Assert.assertTrue(history.undo());
		Assert.assertNull("back from a post-clear pick returns to the cleared state",
			panel.selectedMonsterForTest());
		Assert.assertTrue(history.undo());
		Assert.assertEquals("Zulrah", panel.selectedMonsterForTest().getName());
	}

	@Test
	public void addToViewGrowsThePageAndBackShrinksIt()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		Assert.assertEquals(1, panel.pageSizeForTest());

		panel.addToView(data.searchMonsters("general graardor", 1).get(0));
		Assert.assertEquals("the page holds both results", 2, panel.pageSizeForTest());
		Assert.assertEquals("the newcomer is active",
			"General Graardor", panel.selectedMonsterForTest().getName());
		Assert.assertEquals("only the newcomer computed",
			"General Graardor", computed.get().getName());

		Assert.assertTrue(history.undo());
		Assert.assertEquals("back removes the added result", 1, panel.pageSizeForTest());
		Assert.assertEquals("Zulrah", panel.selectedMonsterForTest().getName());

		Assert.assertTrue(history.redo());
		Assert.assertEquals(2, panel.pageSizeForTest());
		Assert.assertEquals("General Graardor", panel.selectedMonsterForTest().getName());
	}

	@Test
	public void closingAResultIsAStepAndBackRestoresIt()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		panel.addToView(data.searchMonsters("general graardor", 1).get(0));
		Assert.assertEquals(2, panel.pageSizeForTest());

		panel.closeActiveResultForTest();
		Assert.assertEquals("closing the active result shrinks the page", 1, panel.pageSizeForTest());
		Assert.assertEquals("the remaining result's mob takes over",
			"Zulrah", panel.selectedMonsterForTest().getName());
		Assert.assertEquals("Close - General Graardor", history.peekUndoDescription());

		Assert.assertTrue(history.undo());
		Assert.assertEquals("back re-adds the closed result", 2, panel.pageSizeForTest());
		Assert.assertEquals("General Graardor", panel.selectedMonsterForTest().getName());
	}

	@Test
	public void closingTheLastResultClearsThePanel()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		panel.closeActiveResultForTest();
		Assert.assertEquals(0, panel.pageSizeForTest());
		Assert.assertNull(panel.selectedMonsterForTest());

		Assert.assertTrue(history.undo());
		Assert.assertEquals("back restores the lone result", 1, panel.pageSizeForTest());
		Assert.assertEquals("Zulrah", panel.selectedMonsterForTest().getName());
	}

	@Test
	public void eachResultCarriesItsOwnParameterZone()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		CommandHistory history = new CommandHistory();
		LoadoutLabPanel panel = panel(computed);
		panel.setHistoryControl(control(history));

		Assert.assertTrue(panel.selectExternal("zulrah", null));
		panel.optimizeModeForTest().setSelectedIndex(2);               // Zulrah: Tanky
		Assert.assertEquals(2, panel.optimizeModeForTest().getSelectedIndex());

		panel.addToView(data.searchMonsters("general graardor", 1).get(0));
		Assert.assertEquals("the new result starts at its own defaults",
			0, panel.optimizeModeForTest().getSelectedIndex());

		panel.closeActiveResultForTest();
		Assert.assertEquals("Zulrah's zone survives untouched - the controls repaint from it",
			2, panel.optimizeModeForTest().getSelectedIndex());
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
