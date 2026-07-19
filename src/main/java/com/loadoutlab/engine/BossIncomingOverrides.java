package com.loadoutlab.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Curated per-boss incoming-attack overrides - the D-2 layer on top of the
 * uniform v1 model in IncomingDpsCalculator. The stat sheet gets scripted
 * max hits wrong (Graardor's ranged slam is 15-35, the NPC formula derives
 * 58), rotations are not uniform, some attacks pierce protection prayers,
 * and Typeless/Dragonfire attacks are otherwise unmodeled. Each entry in
 * boss_incoming.json (keyed by lowercase display name) lists the boss's
 * real attacks: style, scripted max hit, rotation share, whether the
 * matching protection prayer fully blocks it, and an optional per-attack
 * speed. Numeric facts are wiki-sourced; see each entry's note.
 */
public final class BossIncomingOverrides
{
	private static final String RESOURCE = "/com/loadoutlab/data/boss_incoming.json";

	/** One curated attack in the boss's rotation. */
	public static final class Attack
	{
		private final String style;
		private final int maxHit;
		private final double share;
		private final boolean prayable;
		/** Dodgeable by design (Zulrah's magma slam): the standard play
		 * takes zero, so the attack is excluded from both dps totals and
		 * kept in the threat list for the tooltip - the same philosophy as
		 * assuming the protection prayer is up. */
		private final boolean avoidable;
		/** Fraction of damage that gets THROUGH the matching protection
		 * prayer: 0 = fully blocked, 0.5 = half pierces (Callisto melee,
		 * Corp magic), 1 = prayer does nothing. maxHit is always the TRUE
		 * unprayed value. */
		private final double prayerFactor;
		private final int speedTicks;

		Attack(String style, int maxHit, double share, boolean prayable, double prayerFactor, int speedTicks)
		{
			this(style, maxHit, share, prayable, prayerFactor, speedTicks, false);
		}

		Attack(String style, int maxHit, double share, boolean prayable, double prayerFactor, int speedTicks, boolean avoidable)
		{
			this.style = style;
			this.maxHit = maxHit;
			this.share = share;
			this.prayable = prayable;
			this.prayerFactor = prayerFactor;
			this.speedTicks = speedTicks;
			this.avoidable = avoidable;
		}

		public boolean isAvoidable()
		{
			return avoidable;
		}

		public double getPrayerFactor()
		{
			return prayerFactor;
		}

		public String getStyle()
		{
			return style;
		}

		public int getMaxHit()
		{
			return maxHit;
		}

		public double getShare()
		{
			return share;
		}

		public boolean isPrayable()
		{
			return prayable;
		}

		/** 0 means "use the stat sheet's attack speed". */
		public int getSpeedTicks()
		{
			return speedTicks;
		}
	}

	/** The full curated picture for one boss. */
	public static final class BossOverride
	{
		private final List<Attack> attacks;
		private final String note;

		BossOverride(List<Attack> attacks, String note)
		{
			this.attacks = Collections.unmodifiableList(attacks);
			this.note = note;
		}

		public List<Attack> getAttacks()
		{
			return attacks;
		}

		public String getNote()
		{
			return note;
		}
	}

	private static final Map<String, BossOverride> OVERRIDES = new HashMap<>();

	static
	{
		JsonObject root = com.loadoutlab.data.JsonResources.objectOrThrow(RESOURCE);
		for (Map.Entry<String, JsonElement> entry : root.entrySet())
		{
			OVERRIDES.put(entry.getKey().toLowerCase(Locale.ROOT),
				parse(entry.getValue().getAsJsonObject()));
		}
	}

	/**
	 * Shape-only read of one boss entry. The DATA validation that used to
	 * throw from here (style whitelist, typeless-must-not-be-prayable, sane
	 * maxHit/share/speed, shares summing to at most 1.0, non-empty attack
	 * list) now lives in BossIncomingOverridesTest's all-overrides sweep -
	 * test source is free of the hub token cap, and the sweep is coupled to a
	 * hardcoded boss list so a newly added boss cannot skip it. Only the
	 * "resource missing" throw stays here: that is a packaging failure, not
	 * bad data.
	 */
	private static BossOverride parse(JsonObject json)
	{
		List<Attack> attacks = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("attacks"))
		{
			JsonObject a = element.getAsJsonObject();
			boolean prayable = !a.has("prayable") || a.get("prayable").getAsBoolean();
			// Legacy semantics preserved: prayable = full block (0 through),
			// unprayable = full pierce (1 through), unless a factor is given.
			double prayerFactor = a.has("prayerFactor") ? a.get("prayerFactor").getAsDouble()
				: (prayable ? 0.0 : 1.0);
			attacks.add(new Attack(
				a.get("style").getAsString().toLowerCase(Locale.ROOT),
				a.get("maxHit").getAsInt(),
				a.get("share").getAsDouble(),
				prayable,
				prayerFactor,
				a.has("speedTicks") ? a.get("speedTicks").getAsInt() : 0,
				a.has("avoidable") && a.get("avoidable").getAsBoolean()));
		}
		return new BossOverride(attacks, json.has("note") ? json.get("note").getAsString() : "");
	}

	private BossIncomingOverrides()
	{
	}

	/** The curated override for this monster, or null to use the v1 model.
	 * Keyed by display name so every version/phase row shares one entry. */
	public static BossOverride overridesFor(MonsterStats monster)
	{
		if (monster == null)
		{
			return null;
		}
		// Version-keyed first ("zulrah|serpentine"): multi-form bosses fight
		// differently per form, and the roster lens shows one form at a
		// time - each must carry ITS OWN attacks and protect prayer (field
		// report 2026-07-17: every Zulrah form said pray mage). The bare
		// name stays as the whole-fight fallback.
		String version = monster.getVersion();
		if (version != null && !version.isEmpty())
		{
			BossOverride byForm = OVERRIDES.get(
				(monster.getNameLower() + "|" + version).toLowerCase(Locale.ROOT));
			if (byForm != null)
			{
				return byForm;
			}
		}
		return OVERRIDES.get(monster.getNameLower());
	}

	/** All curated boss names (lowercase) - test and tooling surface. */
	public static Set<String> names()
	{
		return Collections.unmodifiableSet(OVERRIDES.keySet());
	}
}
