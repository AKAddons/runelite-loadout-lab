package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;

import static com.loadoutlab.data.JsonResources.*;

// Ship-combat data (see naval_combat.json): the seven cannon tiers, the
// eight cannonball tiers, and the curated list of ship-eligible monsters.
// Wiki-verified 2026-08-31; the resource's $comment records the revisions
// that matter (dragon ball +174 -> +270 on 2026-03-18, and the stale
// crewmate formula flag).
// <p>The corpus carries no naval attribute, so eligibility is this list -
// every name resolves against a corpus row (NavalCombatTest proves it).
public class NavalCombat {
// One cannon tier. Cannons are FACILITIES, not items; itemId is the
// skill-guide item that carries the illustration (32199..32205).
@lombok.AllArgsConstructor
public static class Cannon {
    public final String tier;
    public final int itemId;
    public final int sailing;
    public final int ranged;
    public final int heavyAccuracy;
    public final int strength;
    public final int privateering;
}

// One cannonball tier. Steel is the classic Cannonball (item 2).
@lombok.AllArgsConstructor
public static class Ball {
    public final String tier;
    public final int itemId;
    public final int accuracy;
    public final int strength;
}

static final Map<String, Cannon> CANNONS = new LinkedHashMap<>();
static final Map<String, Ball> BALLS = new LinkedHashMap<>();
static final List<String> BALL_ORDER = new ArrayList<>();
public static final Set<String> NAVAL = new HashSet<>();
static final List<String> KEELS = new ArrayList<>();
// Pre-0.5.0 builds named the keel columns after the hull woods; a
// character's persisted ship.shipKeel may still say so. Same column,
// right name - nobody's saved keel resets.
static final List<String> LEGACY_WOOD_KEELS = new ArrayList<>();
static final Map<String, int[]> KEEL_MAX_HITS = new LinkedHashMap<>();
static final boolean CREW_FORMULA_STALE;
public static final int ATTACK_TICKS;

// naval_combat.json as Gson reads it; the cannon and ball rows are the
// classes themselves (their field names are the file's keys).
static class Root {
    int attackTicks;
    boolean crewFormulaStale;
    List<Cannon> cannons = List.of();
    List<Ball> cannonballs = List.of();
    List<String> ballTierOrder = List.of(), navalMonsters = List.of(), keels = List.of(), legacyWoodKeels = List.of();
    Map<String, int[]> keelMaxHits = Map.of();
}

static {
    Root root = read("naval_combat.json", Root.class);
    ATTACK_TICKS = root.attackTicks;
    CREW_FORMULA_STALE = root.crewFormulaStale;
    for (Cannon c : root.cannons) CANNONS.put(c.tier, c);
    for (Ball b : root.cannonballs) BALLS.put(b.tier, b);
    BALL_ORDER.addAll(root.ballTierOrder);
    for (String m : root.navalMonsters) NAVAL.add(Names.lower(m));
    KEELS.addAll(root.keels);
    LEGACY_WOOD_KEELS.addAll(root.legacyWoodKeels);
    root.keelMaxHits.forEach((keel, hits) -> KEEL_MAX_HITS.put(Names.lower(keel), hits));
}


public static Cannon cannon(String tier) { return CANNONS.get(tier); }

public static Ball ball(String tier) { return BALLS.get(tier); }

public static List<Cannon> cannons() { return List.copyOf(CANNONS.values()); }

public static List<Ball> balls() { return List.copyOf(BALLS.values()); }

// True when a cannon of {@code cannonTier} may fire {@code ballTier}:
// at or below its own tier in the firing order (granite rides between
// mithril and adamant, so mithril cannons cannot fire it).
public static boolean canFire(String cannonTier, String ballTier) {
    int c = BALL_ORDER.indexOf(cannonTier);
    int b = BALL_ORDER.indexOf(ballTier);
    return c >= 0 && b >= 0 && b <= c;
}

// The best ball a set of cannons can SHARE (REQ-SC-2: one ammo tier for
// all): the highest tier every cannon can fire, or null for no cannons.
public static String bestSharedBall(List<String> cannonTiers) {
    if (cannonTiers == null || cannonTiers.isEmpty()) return null;
    int min = Integer.MAX_VALUE;
    for (String tier : cannonTiers) {
        int i = BALL_ORDER.indexOf(tier);
        if (i < 0) return null;
        min = Math.min(min, i);
    }
    return BALL_ORDER.get(min);
}

// Ship-eligible per the wiki's Ship combat roster (curated - the corpus
// has no naval attribute). Case-insensitive on the corpus name.
public static boolean isNaval(String monsterName) {
    return monsterName != null && NAVAL.contains(Names.lower(monsterName));
}

public static List<String> keels() { return Collections.unmodifiableList(KEELS); }

public static String normalizeKeel(String keel) {
    int i = keel == null ? -1 : LEGACY_WOOD_KEELS.indexOf(Names.lower(keel));
    return i >= 0 && i < KEELS.size() ? KEELS.get(i) : keel;
}

// The monster's effective max hit against this keel (armour already
// applied, from the wiki's Boat combat table), or -1 for an unknown
// monster/keel. A 0 means this keel cannot be hit by it at all.
public static int keelMaxHit(String monsterName, String keelTier) {
    int[] row = monsterName == null ? null : KEEL_MAX_HITS.get(Names.lower(monsterName));
    int i = KEELS.indexOf(keelTier);
    return row == null || i < 0 || i >= row.length ? -1 : row[i];
}

// Crew-fired numbers are estimates while the wiki flags the crewmate
// formula out-of-date; the UI cites this on crew tooltips.
public static boolean crewFormulaStale() { return CREW_FORMULA_STALE; }
}
