package com.loadoutlab.engine;

import java.util.*;

// The page's compute settings, built fresh per compute by the page state
// and carried through the plugin to the optimizer as one object (the
// fourteen scalars used to ride every layer by hand). The engine edits
// its copy before handing it on: the fire gate, the fight-book lock, the
// Arceuus veto.
public class ComputeParams implements Cloneable {
    public boolean f2pOnly;
    public boolean onSlayerTask;
    public boolean inWilderness;
    public String spellbookLock = "";
    public int maxTradeables = -1;
    public int riskBudgetGp = OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
    public boolean antifirePotion;
    // antifireMode as is: 0 none, 1 antifire, 2 super antifire.
    public int antifireTier;
    // The combat achievement task's rules plus the player's requirements
    // (ISS-01M5XC7XYMMEN3Q4QC6CGZFVPK); null = none.
    public SetRules setRules;
    public boolean sunfireRunes;
    public int deathCharge;
    public boolean specWeapon = true;
    public Map<CombatStyle, String> boostPicks = Map.of();
    public Map<CombatStyle, String> prayerPicks = Map.of();
    public int upgradeBudgetGp;
    public int maxSwaps = 1;
    // The Multi card is showing: the multi tools are computed too.
    public boolean multiView;
    // Targets stacked on the Multi card: 3 or 9.
    public int multiStack = 3;
    // The defensive mode - MAX_DPS / BALANCED / TANKY; only ever not
    // MAX_DPS in developer mode (ISS-01M5G5CZC9SK46499FACRSJ8ZT).
    public String optimizeMode = "MAX_DPS";
    // The god chips: the trip's protection, worn when free, else carried
    // (ISS-01M67DQ9ATHSQSJA7QGDKAQGPT; the GWD route seeds them).
    public List<String> gods = List.of();
    // The elite Kourend & Kebos diary is done: Karuulm needs no boots
    // (ISS-01M5JJ0Z4M988DR0M0ZGJHD139).
    public boolean kourendElite;
    // Thralls are on (members, not F2P): with Death Charge, the reason a
    // group defaults to Arceuus (ISS-01M5PB4R0MP149NXWNFSEEHVP8).
    public boolean thralls;
    // A raid selection: its group runs Arceuus whenever thralls or Death
    // Charge are on (ISS-01M5TMC6SK50VPV8713B33SPPM).
    public boolean raid;
    // The stat sim (ISS-01M5QY13K6YHTYDW1CCYD30CSY): seven levels in
    // attack/strength/defence/ranged/magic/prayer/hitpoints order, 0 = your
    // own; null when nothing is simmed. simMaxCape sims the max cape.
    public int[] simLevels;
    public boolean simMaxCape;

    // A field-for-field copy: the roster's second pass edits the lock
    // and Death Charge without touching the page's own params.
    @lombok.SneakyThrows
    public ComputeParams copy() { return (ComputeParams) clone(); }
}
