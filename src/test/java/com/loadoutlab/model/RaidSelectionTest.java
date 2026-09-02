package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field reports 2026-08-31: raids opened at Inv 3 instead of 8 ("it must
 * not be counting theater of blood as a raid" - ToB has no supplied boost,
 * so the boost-coverage heuristic missed it), and the slayer-task chip lit
 * for raid selections (Skeletal Mystic's corpus slayer flag). The GROUP
 * DATA now owns both facts: its inventory seeds the slider, its raid flag
 * suppresses the task context.
 */
class RaidSelectionTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink
	{
		Map<String, Object> published;

		@Override
		public void publishPage(Map<String, Object> page)
		{
			published = page;
		}
	}

	private static Map<String, Object> selectGroup(String group,
		PageState state, CaptureLink link)
	{
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.execute("select", Map.of("query", group));
		try
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception ex)
		{
			throw new AssertionError(ex);
		}
		return state.paramsNode();
	}

	@Test
	@DisplayName("every raid opens at its declared inventory - ToB included")
	void raidsOpenAtEight()
	{
		for (String raidName : new String[]{"Theatre of Blood (Entry)",
			"Theatre of Blood (Hard)", "Chambers of Xeric", "Tombs of Amascut"})
		{
			PageState state = new PageState();
			Map<String, Object> params = selectGroup(raidName, state, new CaptureLink());
			assertEquals(8, params.get("maxSwaps"), raidName + " must open at 8");
			assertEquals(Boolean.TRUE, params.get("raidSelection"), raidName);
		}
	}

	@Test
	@DisplayName("non-raid groups keep the 3-swap default and no raid flag")
	void groupsKeepThree()
	{
		PageState state = new PageState();
		Map<String, Object> params = selectGroup("Dagannoth Kings", state, new CaptureLink());
		assertEquals(3, params.get("maxSwaps"));
		assertEquals(Boolean.FALSE, params.get("raidSelection"));
	}

	@Test
	@DisplayName("the entry carries the raid flag the chip row suppresses on")
	void entryCarriesTheFlag()
	{
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.execute("select", Map.of("query", "Chambers of Xeric"));
		List<com.loadoutlab.data.MonsterStats> mobs = state.rosterMobs();
		assertNotNull(mobs, "the group select must set a roster");
		List<Map<com.loadoutlab.engine.CombatStyle, com.loadoutlab.optimizer.OptimizerService.StyleResult>> empty =
			new java.util.ArrayList<>();
		for (int i = 0; i < mobs.size(); i++)
		{
			empty.add(Map.of());
		}
		engine.onRosterResults(mobs, empty);
		try
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception ex)
		{
			throw new AssertionError(ex);
		}
		assertNotNull(link.published);
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		assertEquals(Boolean.TRUE, entry.get("raidSelection"));
	}

	@Test
	@DisplayName("a raid selection sheds stale wilderness and task context")
	void staleContextResets()
	{
		// The field setup: a wildy session left Wilderness + On task + a
		// risk cap behind, then a ToA select inherited all three and the
		// risk optimizer picked cheap-to-lose junk over real gear.
		PageState state = new PageState();
		state.setParam("inWilderness", true);
		state.setParam("onTask", true);
		state.setParam("riskBudgetGp", 75000);
		Map<String, Object> params = selectGroup("Tombs of Amascut", state, new CaptureLink());
		assertEquals(Boolean.FALSE, params.get("inWilderness"),
			"no ToA member is wilderness-capable - the param must reset");
		assertEquals(Boolean.FALSE, params.get("onTask"),
			"raids are never a slayer-task context");
	}

	@Test
	@DisplayName("arrow-nav opens each mob on its own recommended style (issue #15)")
	void lensClearsTheStickyTab()
	{
		PageState state = new PageState();
		state.setParam("selectedTab", "magic");
		state.setParam("lensIndex", 1);
		assertEquals("", state.paramsNode().get("selectedTab"),
			"a lens change must drop the previous mob's explicit tab");
	}
}
