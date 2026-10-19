package com.loadoutlab.engine;

import java.util.Map;

/**
 * The page's compute settings, built fresh per compute by the page state
 * and carried through the plugin to the optimizer as one object (the
 * fourteen scalars used to ride every layer by hand). The engine edits
 * its copy before handing it on: the fire gate, the fight-book lock, the
 * Arceuus veto.
 */
public final class ComputeParams
{
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
}
