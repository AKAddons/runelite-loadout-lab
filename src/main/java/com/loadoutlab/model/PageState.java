package com.loadoutlab.model;

import com.google.gson.*;
import com.loadoutlab.engine.*;
import com.loadoutlab.data.*;
import java.util.*;
import com.loadoutlab.data.NavalCombat.Cannon;

// Core-owned page state for the Companion seam (ADR-0008: Core owns
// ALL state, including view parameters, so every renderer - the bare
// UI, the Companion, undo history - sees the same world). v1 holds
// one active entry: the selected mob and the full compute parameter
// set. The old Swing panel still keeps its own ResultEntry during the
// transition; the two reconcile when the panel leaves for the
// Companion.
// <p>The params live in ONE map seeded from {@link #DEFAULTS} (the slim,
// 2026-10-23: 35 fields and a 40-arm switch said the same thing three
// times over). Booleans, ints and strings are set by the type of their
// default; the arms of {@link #setParam} cover the few with rules of
// their own.
public class PageState {
    MonsterStats mob;
    List<MonsterStats> rosterMobs;
    String rosterName;

    // Every page param with its default, in the params node's order, from
    // page_params.json (hub token cap) - the int clamps, the view params and
    // the keys a select seeds ride the same file. What each one means, where
    // it is not obvious:
    // <ul>
    // <li>f2pWorld - true while the client is on a NON-members world. The
    // chip renders from this, not from f2pOnly - otherwise unticking the
    // filter hides the control that unticked it and there is no way back.
    // <li>pvpWorld - PvP mode: 0 off, 1 PvP world, 2 High Risk (Ragnarrok,
    // 2026-10-20: "3 iteming" on PvP worlds). World state like f2pWorld,
    // so never in the selection snapshot; pvpWorldHere = the client is on
    // one.
    // <li>kourendElite - the elite Kourend & Kebos diary is done (varbit
    // 7928) - Karuulm needs no boots (ISS-01M5JJ0Z4M988DR0M0ZGJHD139).
    // <li>optimizeMode - MAX_DPS / BALANCED / TANKY, every player's since
    // 2026-10-22 (ISS-01M5JBYYWHQNTM0CGNYWQBNYX6; no developer-mode gate).
    // <li>gwdAncient - the GWD Ancient chip: Zaros joins the protection
    // route - the Nex bank (ISS-01M5G9KPDQWBR014Z10F79B29T).
    // <li>nerAvailable - Not Enough Runes is enabled - the ladder's "how
    // to get it" link.
    // <li>ladderSlot - the slot whose ladder strip is open on the Yours
    // card ("" = none); ladderPage - the strip's page (0 = the strip, n =
    // the nth five further steps up), reset when another slot opens.
    // <li>reqOpen - the "+ Requirement" editor is open; a new search
    // closes it (Andrew 2026-10-27).
    // <li>cannonCount, cannon1/2Material, cannonAmmo, shipKeel,
    // cannon1/2Operator - ship combat (REQ-SC-2/3/3b). Post-compute
    // additive like thralls - none of these enter the optimizer request,
    // so none ride computeArgs. They persist across lens changes; the
    // ENGINE only reads them for a naval mob (REQ-SC-7's veto, same shape
    // as the F2P lock). "detect" ammo resolves to the best BANKED ball
    // every carried cannon can fire (the boost idiom, Andrew 2026-08-31);
    // a tier name pins it. An operator is "you", or "crew1".."crew4" for
    // a crewmate of that Privateering (REQ-SC-9). At most one cannon is
    // yours - picking "you" on one flips the other back to crew. Picking
    // a material auto-RAISES a crew operator to the minimum Privateering
    // that can man it (Andrew: "automatically assume you are going to
    // pick a crew member at minimum that can man the cannon") and never
    // lowers an explicit pick.
    // <li>raidSelection - true while the selection is a RAID group (ToB
    // included - the boost heuristic missed it): raids are never a
    // slayer-task context. Set by the selects, never by a command.
    // <li>riskCapped - whether a cap is actually in force; never inferred
    // from the gp value, which collides with the engine's no-cap sentinel.
    // <li>spellbookSwap - bringing Spellbook Swap + Vengeance on the trip.
    // Supplies only - the DPS Vengeance returns is deliberately not
    // modelled.
    // <li>antifireMode - 0 = shield required, 1 = regular antifire, 2 =
    // super antifire - the classic tri-state; Detect resolves it at
    // selection time.
    // <li>sunfireRunes - assumed for a fire spell (seeded from ownership
    // at each selection; the card plate overrides).
    // <li>multiView, multiStack - the Multi card (Andrew 2026-10-19): the
    // switch computes the multi tools; the stack is a view scale on their
    // per-target numbers.
    // <li>viewingBis, selectedTab, thralls, lensIndex - view state,
    // core-owned like everything else (ADR-0008), but a change here
    // republishes without recomputing (see CommandEngine).
    // </ul>
    static final Map DEFAULTS = new LinkedHashMap<>();
    // Set by the engine (the selects, the risk cap), never by a command.
    static final Set<String> DERIVED = Set.of("raidSelection", "riskCapped", "myLevels");
    static final String[] SIM_KEYS = {"simAttack", "simStrength", "simDefence", "simRanged", "simMagic", "simPrayer", "simHitpoints"};

    // Your live levels for the page ("a/s/d/r/m/p/h"): what the stat sim's
    // boxes and the report show where nothing is simmed.
    public synchronized void setMyLevels(String levels) { params.put("myLevels", levels == null ? "" : levels); }
    // Int params with a range: {min, max}.
    static final Map<String, int[]> CLAMPS = new HashMap<>();
    // The params a select seeds (antifire, tab, view side, on-task,
    // wilderness, bench, and the config seeds) - what undoing it restores:
    // undoing a Callisto used to leave its wilderness and risk chips
    // sitting on the restored mob (pre-release adversarial pass
    // 2026-08-22).
    static final List<String> SNAPSHOT_KEYS = new ArrayList<>();
    static final Set<String> VIEW_PARAMS = new HashSet<>();

    static {
        JsonObject root = JsonResources.objectOrThrow("page_params.json");
        for (var e : root.getAsJsonObject("defaults").entrySet()) {
            JsonPrimitive v = e.getValue().getAsJsonPrimitive();
            DEFAULTS.put(e.getKey(), v.isBoolean() ? v.getAsBoolean() : v.isNumber() ? (Object) v.getAsInt() : v.getAsString());
        }
        // The one default the engine owns (its uncapped sentinel), never the file.
        DEFAULTS.replace("riskBudgetGp", OptimizationRequest.DEFAULT_RISK_BUDGET_GP);
        for (var e : root.getAsJsonObject("clamps").entrySet()) {
            CLAMPS.put(e.getKey(), JsonResources.ints(e.getValue().getAsJsonArray()));
        }
        JsonResources.strings(root, "snapshotKeys", SNAPSHOT_KEYS);
        JsonResources.strings(root, "viewParams", VIEW_PARAMS);
    }

    final Map params = new LinkedHashMap<>(DEFAULTS);
    final Map<CombatStyle, String> boostPicks = new LinkedHashMap<>();
    final Map<CombatStyle, String> prayerPicks = new LinkedHashMap<>();

    // Parameters that change what is SHOWN, not what is computed.
    public static boolean isViewParam(String param) { return VIEW_PARAMS.contains(param); }

    public synchronized void select(MonsterStats mob) {
        params.put("raidSelection", false);
        this.mob = mob;
        rosterMobs = null;
        rosterName = null;
    }

    public synchronized void selectRoster(List<MonsterStats> mobs, String name, boolean raid) {
        params.put("raidSelection", raid);
        mob = null;
        rosterMobs = mobs;
        rosterName = name;
    }

    public synchronized MonsterStats mob() { return mob; }

    public synchronized List<MonsterStats> rosterMobs() { return rosterMobs; }

    // Selection snapshot for undo: restore() reinstates it exactly -
    // the mob or roster, and the params a select seeds (SNAPSHOT_KEYS).
    public synchronized Object[] selectionSnapshot() {
        Map seeded = new LinkedHashMap<>();
        for (String key : SNAPSHOT_KEYS) seeded.put(key, params.get(key));
        return new Object[]{mob, rosterMobs, rosterName, seeded};
    }

    public synchronized void restoreSelection(Object[] snapshot) {
        mob = (MonsterStats) snapshot[0];
        rosterMobs = (List<MonsterStats>) snapshot[1];
        rosterName = (String) snapshot[2];
        params.putAll((Map) snapshot[3]);
    }

    public synchronized boolean hasSelection() { return mob != null || rosterMobs != null; }

    // Apply one named parameter; returns false for an unknown name so
    // the engine can refuse loudly instead of silently dropping it.
    public synchronized boolean setParam(String param, Object value) {
        Object current = param == null ? null : params.get(param);
        if (current == null || DERIVED.contains(param)) return false;
        switch (param) {
            case "cannon1Material":
            case "cannon2Material": {
                // The material gates its crew: a pick raises the operator.
                String operator = "cannon" + param.charAt(6) + "Operator";
                params.put(param, String.valueOf(value));
                params.put(operator, raiseCrewToGate(str(operator), String.valueOf(value)));
                return true;
            }
            case "cannon1Operator":
            case "cannon2Operator": {
                // At most one cannon is yours: picking "you" on one demotes the other.
                int i = param.charAt(6) - '1';
                String mine = operator(value, str("cannon" + (i + 1) + "Material"));
                params.put(param, mine);
                String other = "cannon" + (2 - i) + "Operator";
                if ("you".equals(mine) && "you".equals(params.get(other))) params.put(other, raiseCrewToGate("crew1", str("cannon" + (2 - i) + "Material")));
                return true;
            }
            case "shipKeel":
                params.put(param, NavalCombat.normalizeKeel(String.valueOf(value)));
                return true;
            case "cannonAmmo":
                params.put(param, String.valueOf(value));
                return true;
            case "optimizeMode":
                params.put(param, "BALANCED".equals(value) || "TANKY".equals(value) ? value : "MAX_DPS");
                return true;
            case "riskBudgetGp": {
                // ONE source of truth for "is there a cap" (field report
                // 2026-08-22): the chip lit up on riskBudgetGp > 0 while
                // the compute asked riskBudgetGp != DEFAULT - and the
                // DEFAULT *is* 75k, the same value the config seeds, so
                // the UI promised a cap the optimizer ignored. A null
                // clears the cap (the gp falls back to the default).
                int cap = asInt(value, OptimizationRequest.DEFAULT_RISK_BUDGET_GP);
                params.put(param, cap);
                params.put("riskCapped", value != null && cap > 0);
                return true;
            }
            case "lensIndex":
                params.put(param, asInt(value, 0));
                // Switching mobs opens on THAT mob's recommended style (the
                // 2026-08-15 rule) - the row click already did this, but
                // arrow-nav kept the old tab, so a magic-only Warden left
                // "magic" stuck on the Obelisk as a tab-only view (community
                // report, issue #15). The row pick re-sets the tab AFTER the
                // lens, so explicit picks still land.
                params.put("selectedTab", "");
                return true;
            default:
                if (current instanceof Boolean) {
                    params.put(param, Boolean.TRUE.equals(value));
                } else if (current instanceof String) {
                    params.put(param, value instanceof String ? value : "");
                } else {
                    int[] clamp = CLAMPS.getOrDefault(param, new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
                    params.put(param, Math.max(clamp[0], Math.min(clamp[1], asInt(value, defaultInt(param)))));
                }
                return true;
        }
    }

    // An int param's default - what a non-numeric value falls back to.
    static int defaultInt(String param) { return (Integer) DEFAULTS.get(param); }

    // Normalize an operator pick; a crew pick is clamped 1-4 and then
    // raised to the material's gate.
    static String operator(Object value, String material) {
        String v = String.valueOf(value);
        return "you".equals(v) ? "you" : raiseCrewToGate("crew" + Math.max(1, Math.min(4, crewLevel(v))), material);
    }

    // Crew operators rise to the minimum Privateering the material demands;
    // "you" and already-sufficient crew pass through untouched.
    static String raiseCrewToGate(String operator, String material) {
        if ("you".equals(operator)) return operator;
        Cannon cannon = NavalCombat.cannon(material);
        int gate = cannon == null ? 1 : cannon.privateering;
        return "crew" + Math.max(crewLevel(operator), gate);
    }

    // The Privateering of a "crewN" operator; 1 for "you", a bare "crew"
    // or anything else.
    static int crewLevel(String operator) {
        if (operator != null && operator.startsWith("crew")) {
            try {
                return Integer.parseInt(operator.substring(4));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    static int asInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    boolean flag(String key) { return Boolean.TRUE.equals(params.get(key)); }

    int num(String key) { return (Integer) params.get(key); }

    String str(String key) { return (String) params.get(key); }

    // The params node of the model page - what a renderer needs to
    // draw every chip in its current position.
    public synchronized Map paramsNode() {
        Map node = new LinkedHashMap<>(params);
        node.put("prayerPicks", lowered(prayerPicks));
        node.put("boostPicks", lowered(boostPicks));
        return node;
    }

    // A pick map keyed by the style's lower-case name, for the page.
    static Map lowered(Map<CombatStyle, String> picks) {
        Map out = new LinkedHashMap<>();
        for (var pick : picks.entrySet()) out.put(pick.getKey().name().toLowerCase(), pick.getValue());
        return out;
    }

    public synchronized int toaInvocation() { return num("toaInvocation"); }

    // Prayer/boost pick per style: null value = Detect (remove),
    // "NONE" = explicitly none, else the option key.
    public synchronized void setPick(boolean prayer, CombatStyle style, String value) {
        Map<CombatStyle, String> picks = prayer ? prayerPicks : boostPicks;
        if (value == null) {
            picks.remove(style);
        } else picks.put(style, value);
    }

    // The picked combat achievement tasks ("161,72"), in pick order.
    public synchronized List<CombatAchievements.Task> caPicks() {
        List<CombatAchievements.Task> out = new ArrayList<>();
        for (String id : str("caPicks").split(",")) {
            CombatAchievements.Task t = id.isBlank() ? null : CombatAchievements.byId(Integer.parseInt(id.trim()));
            if (t != null) out.add(t);
        }
        return out;
    }

    // Every picked task's rules on the page's mobs, ANDed; null when none is picked.
    public synchronized SetRules taskRules() {
        SetRules all = null;
        List<MonsterStats> on = rosterMobs != null ? rosterMobs : mob != null ? List.of(mob) : List.of();
        for (CombatAchievements.Task t : caPicks()) all = all == null ? t.rulesFor(on) : all.and(t.rulesFor(on));
        return all;
    }

    // The F2P lock: the param, or a picked free-to-play task (ISS-01M60HTYZT9DTZKDSFAGRNNK53).
    public synchronized boolean f2pLocked() {
        SetRules tasks = taskRules();
        return flag("f2pOnly") || tasks != null && tasks.f2p;
    }

    // The compute settings, one object per compute (see CommandEngine).
    public synchronized ComputeParams computeParams() {
        // Kept-slots mirror the classic riskCap() gate: a risk cap only
        // binds in the wilderness or on a PvP world, and Protect Item
        // keeps a 4th slot - none on High Risk. The wilderness flag itself
        // stays out of it: a PvP world earns no wilderness weapon buff.
        int pvp = num("pvpWorld");
        int kept = PvpRisk.keptSlots(flag("inWilderness") ? Math.max(1, pvp) : pvp, flag("protectItem"));
        // The F2P lock vetoes members mechanics WITHOUT clearing them: the
        // params keep their values (undo history stays coherent, and
        // unticking F2P brings the old setup straight back) - they just
        // stop steering the math while the lock is on (field ask
        // 2026-08-27: D charge / thralls / task have no meaning on F2P).
        boolean f2p = f2pLocked();
        var p = new ComputeParams();
        p.f2pOnly = f2p;
        p.onSlayerTask = !f2p && flag("onTask");
        p.inWilderness = flag("inWilderness");
        p.spellbookLock = str("spellbookLock");
        // -1 = no tradeables limit (the engine's uncapped sentinel).
        p.maxTradeables = flag("riskCapped") && kept >= 0 ? kept : -1;
        p.riskBudgetGp = num("riskBudgetGp");
        p.antifirePotion = num("antifireMode") == 2;
        p.antifireTier = num("antifireMode");
        // A combat achievement task's rules plus the player's own requirements
        // (ISS-01M5XC7XYMMEN3Q4QC6CGZFVPK).
        SetRules taskRules = taskRules();
        List<SetRules.Rule> extra = SetRules.parse(str("requirements"));
        p.setRules = taskRules != null ? taskRules.plus(extra) : extra.isEmpty() ? null : SetRules.of(extra, Set.of(), null);
        p.sunfireRunes = flag("sunfireRunes");
        p.multiView = flag("multiView");
        p.multiStack = num("multiStack");
        p.deathCharge = f2p ? 0 : num("deathCharge");
        p.specWeapon = flag("specWeapon");
        p.boostPicks = new LinkedHashMap<>(boostPicks);
        p.prayerPicks = new LinkedHashMap<>(prayerPicks);
        // A task's prayer and spellbook terms ride the existing picks: no
        // prayer on any style, the one book as the lock.
        if (taskRules != null && taskRules.spellbook != null) p.spellbookLock = taskRules.spellbook;
        p.upgradeBudgetGp = num("upgradeBudgetGp");
        p.maxSwaps = num("maxSwaps");
        // Every player's chosen mode since 2026-10-22 (Andrew: "Release to
        // everyone"; ISS-01M5JBYYWHQNTM0CGNYWQBNYX6) - no developer-mode gate.
        p.optimizeMode = str("optimizeMode");
        p.kourendElite = flag("kourendElite");
        p.gwdAncient = flag("gwdAncient");
        int[] sims = new int[SIM_KEYS.length];
        boolean simmed = false;
        for (int i = 0; i < sims.length; i++) {
            sims[i] = num(SIM_KEYS[i]);
            simmed |= sims[i] > 0;
        }
        p.simLevels = simmed ? sims : null;
        p.simMaxCape = flag("simMaxCape");
        return p;
    }
}
