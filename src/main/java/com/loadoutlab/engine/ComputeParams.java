package com.loadoutlab.engine;

import java.util.Map;

/**
 * The page's compute settings, built fresh per compute by the page state
 * and carried through the plugin to the optimizer as one object (the
 * fourteen scalars used to ride every layer by hand). The engine edits
 * its copy before handing it on: the fire gate, the fight-book lock, the
 * Arceuus veto.
 */
public final class ComputeParams {
	public boolean f2pOnly;
	public boolean onSlayerTask;
	public boolean inWilderness;
	public String spellbookLock = "";
	public int maxTradeables = -1;
	public int riskBudgetGp = OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
	public boolean antifirePotion;
	public boolean sunfireRunes;
	public int deathCharge;
	public boolean specWeapon = true;
	public Map<CombatStyle, String> boostPicks = Map.of();
	public Map<CombatStyle, String> prayerPicks = Map.of();
	public int upgradeBudgetGp;
	public int maxSwaps = 1;
	/** The Multi card is showing: the multi tools are computed too. */
	public boolean multiView;
	/** Targets stacked on the Multi card: 3 or 9. */
	public int multiStack = 3;
	/** The defensive mode - MAX_DPS / BALANCED / TANKY; only ever not
	 * MAX_DPS in developer mode (ISS-01M5G5CZC9SK46499FACRSJ8ZT). */
	public String optimizeMode = "MAX_DPS";
	/** The GWD Ancient chip: Zaros joins the protection route
	 * (ISS-01M5G9KPDQWBR014Z10F79B29T). */
	public boolean gwdAncient;
	/** The elite Kourend & Kebos diary is done: Karuulm needs no boots
	 * (ISS-01M5JJ0Z4M988DR0M0ZGJHD139). */
	public boolean kourendElite;
}
