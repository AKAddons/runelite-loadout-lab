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
	private java.util.List<MonsterStats> rosterMobs;
	private String rosterName;

	private boolean onTask;
	private boolean inWilderness;
	private boolean f2pOnly;
	private String spellbookLock = "";
	private int maxTradeables = -1;
	private int riskBudgetGp = OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
	/** Whether a cap is actually in force - never inferred from the gp
	 * value, which collides with the engine's no-cap sentinel. */
	private boolean riskCapped;
	/** 0 = shield required, 1 = regular antifire, 2 = super antifire -
	 * the classic tri-state; Detect resolves it at selection time. */
	private int antifireMode;
	private int deathCharge;
	private boolean specWeapon;
	private int upgradeBudgetGp;
	private int maxSwaps = 1;
	private boolean raidBoost;
	private int toaInvocation = 300;
	private boolean protectItem;
	// View state - core-owned like everything else (ADR-0008), but a
	// change here republishes without recomputing (see CommandEngine).
	private boolean viewingBis;
	private String selectedTab = "";
	private boolean thralls;
	private int lensIndex;
	private final Map<CombatStyle, String> boostPicks = new LinkedHashMap<>();
	private final Map<CombatStyle, String> prayerPicks = new LinkedHashMap<>();

	/** Parameters that change what is SHOWN, not what is computed. */
	public static boolean isViewParam(String param)
	{
		return "viewingBis".equals(param) || "selectedTab".equals(param)
			|| "thralls".equals(param) || "lensIndex".equals(param);
	}

	public synchronized void select(MonsterStats mob)
	{
		this.mob = mob;
		this.rosterMobs = null;
		this.rosterName = null;
	}

	public synchronized void selectRoster(java.util.List<MonsterStats> mobs, String name)
	{
		this.mob = null;
		this.rosterMobs = mobs;
		this.rosterName = name;
	}

	public synchronized MonsterStats mob()
	{
		return mob;
	}

	public synchronized java.util.List<MonsterStats> rosterMobs()
	{
		return rosterMobs;
	}

	/** Selection snapshot for undo: restore() reinstates it exactly. */
	public synchronized Object[] selectionSnapshot()
	{
		return new Object[]{mob, rosterMobs, rosterName};
	}

	@SuppressWarnings("unchecked")
	public synchronized void restoreSelection(Object[] snapshot)
	{
		this.mob = (MonsterStats) snapshot[0];
		this.rosterMobs = (java.util.List<MonsterStats>) snapshot[1];
		this.rosterName = (String) snapshot[2];
	}

	public synchronized boolean hasSelection()
	{
		return mob != null || rosterMobs != null;
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
				// ONE source of truth for "is there a cap" (field report
				// 2026-08-22): the chip lit up on riskBudgetGp > 0 while
				// the compute asked riskBudgetGp != DEFAULT - and the
				// DEFAULT *is* 75k, the same value the config seeds, so
				// the UI promised a cap the optimizer ignored.
				if (value == null)
				{
					riskBudgetGp = OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
					riskCapped = false;
				}
				else
				{
					int cap = asInt(value, OptimizationRequest.DEFAULT_RISK_BUDGET_GP);
					riskBudgetGp = cap;
					riskCapped = cap > 0;
				}
				return true;
			case "antifireMode":
				antifireMode = Math.max(0, Math.min(2, asInt(value, 0)));
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
			case "toaInvocation":
				toaInvocation = asInt(value, 300);
				return true;
			case "protectItem":
				protectItem = Boolean.TRUE.equals(value);
				return true;
			case "viewingBis":
				viewingBis = Boolean.TRUE.equals(value);
				return true;
			case "thralls":
				thralls = Boolean.TRUE.equals(value);
				return true;
			case "lensIndex":
				lensIndex = asInt(value, 0);
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
		node.put("riskCapped", riskCapped);
		node.put("antifireMode", antifireMode);
		node.put("deathCharge", deathCharge);
		node.put("specWeapon", specWeapon);
		node.put("upgradeBudgetGp", upgradeBudgetGp);
		node.put("maxSwaps", maxSwaps);
		node.put("raidBoost", raidBoost);
		node.put("toaInvocation", toaInvocation);
		node.put("protectItem", protectItem);
		node.put("viewingBis", viewingBis);
		node.put("thralls", thralls);
		node.put("lensIndex", lensIndex);
		node.put("selectedTab", selectedTab);
		Map<String, Object> prayers = new LinkedHashMap<>();
		for (Map.Entry<CombatStyle, String> pick : prayerPicks.entrySet())
		{
			prayers.put(pick.getKey().name().toLowerCase(), pick.getValue());
		}
		node.put("prayerPicks", prayers);
		Map<String, Object> boosts = new LinkedHashMap<>();
		for (Map.Entry<CombatStyle, String> pick : boostPicks.entrySet())
		{
			boosts.put(pick.getKey().name().toLowerCase(), pick.getValue());
		}
		node.put("boostPicks", boosts);
		return node;
	}

	public synchronized int toaInvocation()
	{
		return toaInvocation;
	}

	/** Prayer/boost pick per style: null value = Detect (remove),
	 * "NONE" = explicitly none, else the option key. */
	public synchronized void setPick(boolean prayer, CombatStyle style, String value)
	{
		Map<CombatStyle, String> picks = prayer ? prayerPicks : boostPicks;
		if (value == null)
		{
			picks.remove(style);
		}
		else
		{
			picks.put(style, value);
		}
	}

	/** The compute arguments in ComputeHook order (see CommandEngine). */
	public synchronized Object[] computeArgs()
	{
		// Kept-slots mirror the classic riskCap() gate: a risk cap only
		// binds in the wilderness, and Protect Item keeps a 4th slot.
		int tradeables = inWilderness && riskCapped
			? (protectItem ? 4 : 3) : maxTradeables;
		return new Object[]{
			f2pOnly, onTask, inWilderness, spellbookLock, tradeables,
			riskBudgetGp, antifireMode == 2, deathCharge, specWeapon,
			new LinkedHashMap<>(boostPicks), new LinkedHashMap<>(prayerPicks),
			upgradeBudgetGp, maxSwaps, raidBoost,
		};
	}
}
