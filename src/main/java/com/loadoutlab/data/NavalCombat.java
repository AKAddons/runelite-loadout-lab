package com.loadoutlab.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ship-combat data (see naval_combat.json): the seven cannon tiers, the
 * eight cannonball tiers, and the curated list of ship-eligible monsters.
 * Wiki-verified 2026-08-31; the resource's $comment records the revisions
 * that matter (dragon ball +174 -> +270 on 2026-03-18, and the stale
 * crewmate formula flag).
 *
 * <p>The corpus carries no naval attribute, so eligibility is this list -
 * every name resolves against a corpus row (NavalCombatTest proves it).
 */
public final class NavalCombat
{
	/** One cannon tier. Cannons are FACILITIES, not items; itemId is the
	 * skill-guide item that carries the illustration (32199..32205). */
	public static final class Cannon
	{
		public final String tier;
		public final int itemId;
		public final int sailing;
		public final int ranged;
		public final int heavyAccuracy;
		public final int strength;
		public final int privateering;

		Cannon(String tier, int itemId, int sailing, int ranged,
			int heavyAccuracy, int strength, int privateering)
		{
			this.tier = tier;
			this.itemId = itemId;
			this.sailing = sailing;
			this.ranged = ranged;
			this.heavyAccuracy = heavyAccuracy;
			this.strength = strength;
			this.privateering = privateering;
		}
	}

	/** One cannonball tier. Steel is the classic Cannonball (item 2). */
	public static final class Ball
	{
		public final String tier;
		public final int itemId;
		public final int accuracy;
		public final int strength;

		Ball(String tier, int itemId, int accuracy, int strength)
		{
			this.tier = tier;
			this.itemId = itemId;
			this.accuracy = accuracy;
			this.strength = strength;
		}
	}

	private static final Map<String, Cannon> CANNONS = new LinkedHashMap<>();
	private static final Map<String, Ball> BALLS = new LinkedHashMap<>();
	private static final List<String> BALL_ORDER = new ArrayList<>();
	private static final Set<String> NAVAL = new java.util.HashSet<>();
	private static final List<String> KEELS = new ArrayList<>();
	private static final Map<String, int[]> KEEL_MAX_HITS = new LinkedHashMap<>();
	private static final boolean CREW_FORMULA_STALE;
	public static final int ATTACK_TICKS;

	static
	{
		JsonObject root = JsonResources.object("/com/loadoutlab/data/naval_combat.json");
		ATTACK_TICKS = root.get("attackTicks").getAsInt();
		CREW_FORMULA_STALE = root.get("crewFormulaStale").getAsBoolean();
		for (JsonElement e : root.getAsJsonArray("cannons"))
		{
			JsonObject c = e.getAsJsonObject();
			CANNONS.put(c.get("tier").getAsString(), new Cannon(
				c.get("tier").getAsString(), c.get("itemId").getAsInt(),
				c.get("sailing").getAsInt(), c.get("ranged").getAsInt(),
				c.get("heavyAccuracy").getAsInt(), c.get("strength").getAsInt(),
				c.get("privateering").getAsInt()));
		}
		for (JsonElement e : root.getAsJsonArray("cannonballs"))
		{
			JsonObject b = e.getAsJsonObject();
			BALLS.put(b.get("tier").getAsString(), new Ball(
				b.get("tier").getAsString(), b.get("itemId").getAsInt(),
				b.get("accuracy").getAsInt(), b.get("strength").getAsInt()));
		}
		JsonArray order = root.getAsJsonArray("ballTierOrder");
		for (JsonElement e : order)
		{
			BALL_ORDER.add(e.getAsString());
		}
		for (JsonElement e : root.getAsJsonArray("navalMonsters"))
		{
			NAVAL.add(e.getAsString().toLowerCase(Locale.ROOT));
		}
		for (JsonElement e : root.getAsJsonArray("keels"))
		{
			KEELS.add(e.getAsString());
		}
		for (Map.Entry<String, JsonElement> row
			: root.getAsJsonObject("keelMaxHits").entrySet())
		{
			JsonArray hits = row.getValue().getAsJsonArray();
			int[] out = new int[hits.size()];
			for (int i = 0; i < out.length; i++)
			{
				out[i] = hits.get(i).getAsInt();
			}
			KEEL_MAX_HITS.put(row.getKey().toLowerCase(Locale.ROOT), out);
		}
	}

	private NavalCombat()
	{
	}

	public static Cannon cannon(String tier)
	{
		return CANNONS.get(tier);
	}

	public static Ball ball(String tier)
	{
		return BALLS.get(tier);
	}

	public static List<Cannon> cannons()
	{
		return List.copyOf(CANNONS.values());
	}

	public static List<Ball> balls()
	{
		return List.copyOf(BALLS.values());
	}

	/** True when a cannon of {@code cannonTier} may fire {@code ballTier}:
	 * at or below its own tier in the firing order (granite rides between
	 * mithril and adamant, so mithril cannons cannot fire it). */
	public static boolean canFire(String cannonTier, String ballTier)
	{
		int c = BALL_ORDER.indexOf(cannonTier);
		int b = BALL_ORDER.indexOf(ballTier);
		return c >= 0 && b >= 0 && b <= c;
	}

	/** The best ball a set of cannons can SHARE (REQ-SC-2: one ammo tier for
	 * all): the highest tier every cannon can fire, or null for no cannons. */
	public static String bestSharedBall(List<String> cannonTiers)
	{
		if (cannonTiers == null || cannonTiers.isEmpty())
		{
			return null;
		}
		int min = Integer.MAX_VALUE;
		for (String tier : cannonTiers)
		{
			int i = BALL_ORDER.indexOf(tier);
			if (i < 0)
			{
				return null;
			}
			min = Math.min(min, i);
		}
		return BALL_ORDER.get(min);
	}

	/** Ship-eligible per the wiki's Ship combat roster (curated - the corpus
	 * has no naval attribute). Case-insensitive on the corpus name. */
	public static boolean isNaval(String monsterName)
	{
		return monsterName != null && NAVAL.contains(monsterName.toLowerCase(Locale.ROOT));
	}

	public static Set<String> navalNames()
	{
		return Collections.unmodifiableSet(NAVAL);
	}

	public static List<String> keels()
	{
		return Collections.unmodifiableList(KEELS);
	}

	/** Pre-0.5.0 builds named the keel columns after the hull woods; a
	 * character's persisted ship.shipKeel may still say so. Same column,
	 * right name - nobody's saved keel resets. */
	private static final List<String> LEGACY_WOOD_KEELS = List.of(
		"regular", "oak", "teak", "mahogany", "camphor", "ironwood", "rosewood", "dragon");

	public static String normalizeKeel(String keel)
	{
		int i = keel == null ? -1 : LEGACY_WOOD_KEELS.indexOf(keel.toLowerCase(Locale.ROOT));
		return i >= 0 && i < KEELS.size() ? KEELS.get(i) : keel;
	}

	/** The monster's effective max hit against this keel (armour already
	 * applied, from the wiki's Boat combat table), or -1 for an unknown
	 * monster/keel. A 0 means this keel cannot be hit by it at all. */
	public static int keelMaxHit(String monsterName, String keelTier)
	{
		int[] row = monsterName == null ? null
			: KEEL_MAX_HITS.get(monsterName.toLowerCase(Locale.ROOT));
		int i = KEELS.indexOf(keelTier);
		return row == null || i < 0 || i >= row.length ? -1 : row[i];
	}

		/** Crew-fired numbers are estimates while the wiki flags the crewmate
	 * formula out-of-date; the UI cites this on crew tooltips. */
	public static boolean crewFormulaStale()
	{
		return CREW_FORMULA_STALE;
	}
}
