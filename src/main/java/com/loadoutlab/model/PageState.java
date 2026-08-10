package com.loadoutlab.model;

import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Core-owned page state for the Companion seam (ADR-0008: Core owns
 * ALL state, including view parameters, so every renderer - the bare
 * UI, the Companion, undo history - sees the same world). v1 holds
 * one active entry: the selected mob and the full compute parameter
 * set. The old Swing panel still keeps its own ResultEntry during the
 * transition; the two reconcile when the panel leaves for the
 * Companion.
 */
public class PageState
{
	private MonsterStats mob;

	private boolean onTask;
	private boolean inWilderness;
	private boolean f2pOnly;
	private String spellbookLock = "";
	private int maxTradeables = -1;
	private int riskBudgetGp = OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
	private boolean antifirePotion;
	private int deathCharge;
	private boolean specWeapon;
	private int upgradeBudgetGp;
	private int maxSwaps = 1;
	private boolean raidBoost;
	// View state - core-owned like everything else (ADR-0008), but a
	// change here republishes without recomputing (see CommandEngine).
	private boolean viewingBis;
	private String selectedTab = "";
	private final Map<CombatStyle, String> boostPicks = new LinkedHashMap<>();
	private final Map<CombatStyle, String> prayerPicks = new LinkedHashMap<>();

	/** Parameters that change what is SHOWN, not what is computed. */
	public static boolean isViewParam(String param)
	{
		return "viewingBis".equals(param) || "selectedTab".equals(param);
	}

	public synchronized void select(MonsterStats mob)
	{
		this.mob = mob;
	}

	public synchronized MonsterStats mob()
	{
		return mob;
	}

	/** Apply one named parameter; returns false for an unknown name so
	 * the engine can refuse loudly instead of silently dropping it. */
	public synchronized boolean setParam(String param, Object value)
	{
		switch (param == null ? "" : param)
		{
			case "onTask":
				onTask = Boolean.TRUE.equals(value);
				return true;
			case "inWilderness":
				inWilderness = Boolean.TRUE.equals(value);
				return true;
			case "f2pOnly":
				f2pOnly = Boolean.TRUE.equals(value);
				return true;
			case "spellbookLock":
				spellbookLock = value instanceof String ? (String) value : "";
				return true;
			case "riskBudgetGp":
				riskBudgetGp = asInt(value, OptimizationRequest.DEFAULT_RISK_BUDGET_GP);
				return true;
			case "antifirePotion":
				antifirePotion = Boolean.TRUE.equals(value);
				return true;
			case "deathCharge":
				deathCharge = asInt(value, 0);
				return true;
			case "specWeapon":
				specWeapon = Boolean.TRUE.equals(value);
				return true;
			case "upgradeBudgetGp":
				upgradeBudgetGp = asInt(value, 0);
				return true;
			case "maxSwaps":
				maxSwaps = asInt(value, 1);
				return true;
			case "raidBoost":
				raidBoost = Boolean.TRUE.equals(value);
				return true;
			case "viewingBis":
				viewingBis = Boolean.TRUE.equals(value);
				return true;
			case "selectedTab":
				selectedTab = value instanceof String ? (String) value : "";
				return true;
			default:
				return false;
		}
	}

	private static int asInt(Object value, int fallback)
	{
		return value instanceof Number ? ((Number) value).intValue() : fallback;
	}

	/** The params node of the model page - what a renderer needs to
	 * draw every chip in its current position. */
	public synchronized Map<String, Object> paramsNode()
	{
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("onTask", onTask);
		node.put("inWilderness", inWilderness);
		node.put("f2pOnly", f2pOnly);
		node.put("spellbookLock", spellbookLock);
		node.put("riskBudgetGp", riskBudgetGp);
		node.put("antifirePotion", antifirePotion);
		node.put("deathCharge", deathCharge);
		node.put("specWeapon", specWeapon);
		node.put("upgradeBudgetGp", upgradeBudgetGp);
		node.put("maxSwaps", maxSwaps);
		node.put("raidBoost", raidBoost);
		node.put("viewingBis", viewingBis);
		node.put("selectedTab", selectedTab);
		return node;
	}

	/** The compute arguments in ComputeHook order (see CommandEngine). */
	public synchronized Object[] computeArgs()
	{
		return new Object[]{
			f2pOnly, onTask, inWilderness, spellbookLock, maxTradeables,
			riskBudgetGp, antifirePotion, deathCharge, specWeapon,
			new LinkedHashMap<>(boostPicks), new LinkedHashMap<>(prayerPicks),
			upgradeBudgetGp, maxSwaps, raidBoost,
		};
	}
}
