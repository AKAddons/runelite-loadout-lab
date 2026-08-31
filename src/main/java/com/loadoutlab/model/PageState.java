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
	/** True while the client is on a NON-members world. The chip renders from
	 * this, not from f2pOnly - otherwise unticking the filter hides the control
	 * that unticked it and there is no way back. */
	private boolean f2pWorld;
	/** Ship combat (REQ-SC-2/3/3b). Post-compute additive like thralls -
	 * none of these enter the optimizer request, so none ride computeArgs.
	 * They persist across lens changes; the ENGINE only reads them for a
	 * naval mob (REQ-SC-7's veto, same shape as the F2P lock). */
	private int cannonCount;
	private String cannon1Material = "bronze";
	private String cannon2Material = "bronze";
	private String cannonAmmo = "bronze";
	private String playerStation = "gear";
	private int crewPrivateering = 1;
	private String spellbookLock = "";
	private int maxTradeables = -1;
	private int riskBudgetGp = OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
	/** Whether a cap is actually in force - never inferred from the gp
	 * value, which collides with the engine's no-cap sentinel. */
	private boolean riskCapped;
	/** Bringing Spellbook Swap + Vengeance on the trip. Supplies only -
	 * the DPS Vengeance returns is deliberately not modelled. */
	private boolean spellbookSwap;
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
			|| "thralls".equals(param) || "lensIndex".equals(param)
			|| "spellbookSwap".equals(param);
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

	/** Selection snapshot for undo: restore() reinstates it exactly.
	 * Carries the PARAMS a select seeds too (antifire, tab, view side,
	 * on-task, wilderness, bench, and the config seeds) - undoing a
	 * Callisto used to leave its wilderness and risk chips sitting on
	 * the restored mob (pre-release adversarial pass 2026-08-22). */
	public synchronized Object[] selectionSnapshot()
	{
		return new Object[]{mob, rosterMobs, rosterName,
			new Object[]{antifireMode, selectedTab, viewingBis, onTask, inWilderness,
				maxSwaps, riskBudgetGp, riskCapped, protectItem, upgradeBudgetGp}};
	}

	@SuppressWarnings("unchecked")
	public synchronized void restoreSelection(Object[] snapshot)
	{
		this.mob = (MonsterStats) snapshot[0];
		this.rosterMobs = (java.util.List<MonsterStats>) snapshot[1];
		this.rosterName = (String) snapshot[2];
		if (snapshot.length > 3 && snapshot[3] instanceof Object[])
		{
			Object[] params = (Object[]) snapshot[3];
			this.antifireMode = (Integer) params[0];
			this.selectedTab = (String) params[1];
			this.viewingBis = (Boolean) params[2];
			this.onTask = (Boolean) params[3];
			this.inWilderness = (Boolean) params[4];
			this.maxSwaps = (Integer) params[5];
			this.riskBudgetGp = (Integer) params[6];
			this.riskCapped = (Boolean) params[7];
			this.protectItem = (Boolean) params[8];
			this.upgradeBudgetGp = (Integer) params[9];
		}
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
			case "cannonCount":
				cannonCount = Math.max(0, Math.min(2, asInt(value, 0)));
				return true;
			case "cannon1Material":
				cannon1Material = String.valueOf(value);
				return true;
			case "cannon2Material":
				cannon2Material = String.valueOf(value);
				return true;
			case "cannonAmmo":
				cannonAmmo = String.valueOf(value);
				return true;
			case "playerStation":
				playerStation = "cannon".equals(value) ? "cannon" : "gear";
				return true;
			case "crewPrivateering":
				crewPrivateering = Math.max(1, Math.min(4, asInt(value, 1)));
				return true;
			case "f2pWorld":
				f2pWorld = Boolean.TRUE.equals(value);
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
			case "spellbookSwap":
				spellbookSwap = Boolean.TRUE.equals(value);
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
		node.put("f2pWorld", f2pWorld);
		node.put("cannonCount", cannonCount);
		node.put("cannon1Material", cannon1Material);
		node.put("cannon2Material", cannon2Material);
		node.put("cannonAmmo", cannonAmmo);
		node.put("playerStation", playerStation);
		node.put("crewPrivateering", crewPrivateering);
		node.put("spellbookLock", spellbookLock);
		node.put("riskBudgetGp", riskBudgetGp);
		node.put("riskCapped", riskCapped);
		node.put("spellbookSwap", spellbookSwap);
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
		// The F2P lock vetoes members mechanics WITHOUT clearing them: the
		// params keep their values (undo history stays coherent, and
		// unticking F2P brings the old setup straight back) - they just
		// stop steering the math while the lock is on (field ask
		// 2026-08-27: D charge / thralls / task have no meaning on F2P).
		return new Object[]{
			f2pOnly, !f2pOnly && onTask, inWilderness, spellbookLock, tradeables,
			riskBudgetGp, antifireMode == 2, f2pOnly ? 0 : deathCharge, specWeapon,
			new LinkedHashMap<>(boostPicks), new LinkedHashMap<>(prayerPicks),
			upgradeBudgetGp, maxSwaps, raidBoost,
		};
	}
}
