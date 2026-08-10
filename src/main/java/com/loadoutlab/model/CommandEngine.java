package com.loadoutlab.model;

import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.List;
import java.util.Map;

/**
 * Executes contract commands (docs/COMPANION_CONTRACT.md) against the
 * core-owned {@link PageState} and publishes the resulting page. This
 * is the layer BOTH UIs share: the Companion sends `command` messages
 * here; the bare text UI will call {@link #execute} directly. During
 * the transition the old Swing panel still drives computes its own
 * way - its results flow through {@link #onResults} too, so the
 * Companion mirrors whichever surface the user drives.
 */
public class CommandEngine
{
	/** The plugin's compute path, in ComputeHook argument order. */
	public interface Compute
	{
		void compute(MonsterStats monster, boolean f2pOnly, boolean onSlayerTask,
			boolean inWilderness, String spellbookLock, int maxTradeables, int riskBudgetGp,
			boolean antifirePotion, int deathCharge, boolean specWeapon,
			Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks,
			int upgradeBudgetGp, int maxSwaps, boolean raidBoost, Runnable onDone);
	}

	private final LoadoutData data;
	private final PageState state;
	private final Compute compute;
	private final CompanionLink link;
	/** Engine-owned history for seam-driven actions. Separate from the
	 * old panel's stack during the transition (its commands re-sync
	 * Swing controls the engine does not know about); they merge when
	 * the panel leaves. */
	private final com.loadoutlab.command.CommandHistory history =
		new com.loadoutlab.command.CommandHistory();

	public CommandEngine(LoadoutData data, PageState state, Compute compute, CompanionLink link)
	{
		this.data = data;
		this.state = state;
		this.compute = compute;
		this.link = link;
	}

	private static final Map<String, String> PARAM_LABELS = Map.ofEntries(
		Map.entry("onTask", "On task"), Map.entry("inWilderness", "Wilderness"),
		Map.entry("f2pOnly", "F2P"), Map.entry("specWeapon", "Spec weapon"),
		Map.entry("antifirePotion", "Antifire"), Map.entry("raidBoost", "Raid boost"),
		Map.entry("deathCharge", "Death charge"), Map.entry("viewingBis", "View"),
		Map.entry("selectedTab", "Tab"), Map.entry("spellbookLock", "Spellbook"),
		Map.entry("riskBudgetGp", "Risk cap"), Map.entry("upgradeBudgetGp", "Upgrade budget"),
		Map.entry("maxSwaps", "Inventory"));

	/** Handle one contract command; returns false for an unknown or
	 * malformed command (refused loudly at the seam, never guessed). */
	@SuppressWarnings("unchecked")
	public boolean execute(String name, Map<String, Object> args)
	{
		switch (name == null ? "" : name)
		{
			case "select":
			{
				Object query = args == null ? null : args.get("query");
				if (!(query instanceof String) || ((String) query).isBlank())
				{
					return false;
				}
				List<MonsterStats> matches = data.searchMonsters((String) query, 1);
				if (matches.isEmpty())
				{
					return false;
				}
				MonsterStats prev = state.mob();
				MonsterStats next = matches.get(0);
				if (prev != null && prev.getId() == next.getId())
				{
					recompute();
					return true;
				}
				return history.execute(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						state.select(next);
						recompute();
						return true;
					}

					@Override
					public boolean revert()
					{
						state.select(prev);
						if (prev != null)
						{
							recompute();
						}
						else
						{
							clearResults();
						}
						return true;
					}

					@Override
					public String getDescription()
					{
						return "vs " + next.label();
					}
				});
			}
			case "set-param":
			{
				Object param = args == null ? null : args.get("param");
				if (!(param instanceof String))
				{
					return false;
				}
				String key = (String) param;
				Object next = args.get("value");
				Object prev = state.paramsNode().get(key);
				if (java.util.Objects.equals(prev, next))
				{
					return true;
				}
				String label = PARAM_LABELS.getOrDefault(key, key);
				return history.execute(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						return applyParam(key, next);
					}

					@Override
					public boolean revert()
					{
						return applyParam(key, prev);
					}

					@Override
					public String getDescription()
					{
						return Boolean.TRUE.equals(next) ? label + " on"
							: Boolean.FALSE.equals(next) ? label + " off"
							: label + " " + next;
					}
				});
			}
			case "recompute":
				recompute();
				return true;
			case "undo":
			case "redo":
			{
				// The command's own publish runs BEFORE CommandHistory moves
				// it between stacks - republish after, so the page's history
				// node (canUndo/canRedo/labels) reflects the settled stacks.
				boolean ok = "undo".equals(name) ? history.undo() : history.redo();
				if (ok)
				{
					republish();
				}
				return ok;
			}
			default:
				return false;
		}
	}

	private void recompute()
	{
		MonsterStats mob = state.mob();
		if (mob == null)
		{
			return;
		}
		Object[] a = state.computeArgs();
		compute.compute(mob, (Boolean) a[0], (Boolean) a[1], (Boolean) a[2],
			(String) a[3], (Integer) a[4], (Integer) a[5], (Boolean) a[6],
			(Integer) a[7], (Boolean) a[8], (Map<CombatStyle, String>) a[9],
			(Map<CombatStyle, String>) a[10], (Integer) a[11], (Integer) a[12],
			(Boolean) a[13], () ->
			{
			});
	}

	private volatile List<MonsterStats> lastMobs;
	private volatile List<Map<CombatStyle, OptimizerService.StyleResult>> lastPerMob;

	/** Every single-mob compute lands here (engine-driven or old-panel
	 * driven) - assemble the page, attach the params the state holds,
	 * and publish. */
	public void onResults(MonsterStats mob, Map<CombatStyle, OptimizerService.StyleResult> results)
	{
		onRosterResults(List.of(mob), List.of(results));
	}

	/** Roster computes: published with the shared params node. */
	public void onRosterResults(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob)
	{
		lastMobs = mobs;
		lastPerMob = perMob;
		republish();
	}

	/** Route one param change: view params republish, the rest compute.
	 * Shared by the live command and its undo/redo replays. */
	private boolean applyParam(String key, Object value)
	{
		if (!state.setParam(key, value))
		{
			return false;
		}
		if (PageState.isViewParam(key))
		{
			republish();
		}
		else
		{
			recompute();
		}
		return true;
	}

	/** Reverting the first select: no mob, no results - an empty page. */
	private void clearResults()
	{
		lastMobs = null;
		lastPerMob = null;
		link.publishPage(withHistory(RenderModel.page(List.of())));
	}

	private void republish()
	{
		List<MonsterStats> mobs = lastMobs;
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob = lastPerMob;
		if (mobs == null || perMob == null)
		{
			return;
		}
		Map<String, Object> entry = RenderModel.entry(mobs, perMob);
		entry.put("params", state.paramsNode());
		link.publishPage(withHistory(RenderModel.page(List.of(entry))));
	}

	private Map<String, Object> withHistory(Map<String, Object> page)
	{
		Map<String, Object> node = new java.util.LinkedHashMap<>();
		node.put("canUndo", history.canUndo());
		node.put("canRedo", history.canRedo());
		node.put("undoLabel", history.peekUndoDescription());
		node.put("redoLabel", history.peekRedoDescription());
		page.put("history", node);
		return page;
	}
}
