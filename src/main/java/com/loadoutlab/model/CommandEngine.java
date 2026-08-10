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

	public CommandEngine(LoadoutData data, PageState state, Compute compute, CompanionLink link)
	{
		this.data = data;
		this.state = state;
		this.compute = compute;
		this.link = link;
	}

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
				state.select(matches.get(0));
				recompute();
				return true;
			}
			case "set-param":
			{
				Object param = args == null ? null : args.get("param");
				if (!(param instanceof String)
					|| !state.setParam((String) param, args.get("value")))
				{
					return false;
				}
				recompute();
				return true;
			}
			case "recompute":
				recompute();
				return true;
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

	/** Every single-mob compute lands here (engine-driven or old-panel
	 * driven) - assemble the page, attach the params the state holds,
	 * and publish. */
	public void onResults(MonsterStats mob, Map<CombatStyle, OptimizerService.StyleResult> results)
	{
		Map<String, Object> entry = RenderModel.entry(List.of(mob), List.of(results));
		entry.put("params", state.paramsNode());
		link.publishPage(RenderModel.page(List.of(entry)));
	}

	/** Roster computes: published with the shared params node. */
	public void onRosterResults(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob)
	{
		Map<String, Object> entry = RenderModel.entry(mobs, perMob);
		entry.put("params", state.paramsNode());
		link.publishPage(RenderModel.page(List.of(entry)));
	}
}
