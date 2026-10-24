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
	/** Thralls are on (members, not F2P): with Death Charge, the reason a
	 * group defaults to Arceuus (ISS-01M5PB4R0MP149NXWNFSEEHVP8). */
	public boolean thralls;

	/** A field-for-field copy: the roster's second pass edits the lock
	 * and Death Charge without touching the page's own params. */
	public ComputeParams copy() {
		ComputeParams c = new ComputeParams();
		c.f2pOnly = f2pOnly;
		c.onSlayerTask = onSlayerTask;
		c.inWilderness = inWilderness;
		c.spellbookLock = spellbookLock;
		c.maxTradeables = maxTradeables;
		c.riskBudgetGp = riskBudgetGp;
		c.antifirePotion = antifirePotion;
		c.sunfireRunes = sunfireRunes;
		c.deathCharge = deathCharge;
		c.specWeapon = specWeapon;
		c.boostPicks = boostPicks;
		c.prayerPicks = prayerPicks;
		c.upgradeBudgetGp = upgradeBudgetGp;
		c.maxSwaps = maxSwaps;
		c.multiView = multiView;
		c.multiStack = multiStack;
		c.optimizeMode = optimizeMode;
		c.gwdAncient = gwdAncient;
		c.kourendElite = kourendElite;
		c.thralls = thralls;
		return c;
	}
}
