package com.loadoutlab.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loadoutlab.data.MonsterStats;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

	private static final Set<String> STYLES = new HashSet<>(Arrays.asList(
		"melee", "stab", "slash", "crush", "ranged", "magic", "typeless"));

	/** One curated attack in the boss's rotation. */
	public static final class Attack
	{
		private final String style;
		private final int maxHit;
		private final double share;
		private final boolean prayable;
		/** Fraction of damage that gets THROUGH the matching protection
		 * prayer: 0 = fully blocked, 0.5 = half pierces (Callisto melee,
		 * Corp magic), 1 = prayer does nothing. maxHit is always the TRUE
		 * unprayed value. */
		private final double prayerFactor;
		private final int speedTicks;

		Attack(String style, int maxHit, double share, boolean prayable, double prayerFactor, int speedTicks)
		{
			this.style = style;
			this.maxHit = maxHit;
			this.share = share;
			this.prayable = prayable;
			this.prayerFactor = prayerFactor;
			this.speedTicks = speedTicks;
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
		try (InputStream stream = BossIncomingOverrides.class.getResourceAsStream(RESOURCE);
			InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.entrySet())
			{
				String name = entry.getKey().toLowerCase(Locale.ROOT);
				OVERRIDES.put(name, parse(name, entry.getValue().getAsJsonObject()));
			}
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("Could not load " + RESOURCE, ex);
		}
	}

	private static BossOverride parse(String name, JsonObject json)
	{
		List<Attack> attacks = new ArrayList<>();
		double shareSum = 0;
		for (JsonElement element : json.getAsJsonArray("attacks"))
		{
			JsonObject a = element.getAsJsonObject();
			String style = a.get("style").getAsString().toLowerCase(Locale.ROOT);
			int maxHit = a.get("maxHit").getAsInt();
			double share = a.get("share").getAsDouble();
			boolean prayable = !a.has("prayable") || a.get("prayable").getAsBoolean();
			// Legacy semantics preserved: prayable = full block (0 through),
			// unprayable = full pierce (1 through), unless a factor is given.
			double prayerFactor = a.has("prayerFactor") ? a.get("prayerFactor").getAsDouble()
				: (prayable ? 0.0 : 1.0);
			if (prayerFactor < 0 || prayerFactor > 1)
			{
				throw new IllegalStateException(name + ": prayerFactor out of range");
			}
			int speedTicks = a.has("speedTicks") ? a.get("speedTicks").getAsInt() : 0;
			if (!STYLES.contains(style))
			{
				throw new IllegalStateException(name + ": unknown style " + style);
			}
			if ("typeless".equals(style) && prayable)
			{
				throw new IllegalStateException(name + ": typeless attacks must be prayable=false");
			}
			if (maxHit <= 0 || share <= 0 || share > 1.0 || speedTicks < 0)
			{
				throw new IllegalStateException(name + ": bad attack values");
			}
			shareSum += share;
			attacks.add(new Attack(style, maxHit, share, prayable, prayerFactor, speedTicks));
		}
		if (attacks.isEmpty() || shareSum > 1.0 + 1e-9)
		{
			throw new IllegalStateException(name + ": shares must be non-empty and sum to <= 1.0");
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
		return OVERRIDES.get(monster.getName().toLowerCase(Locale.ROOT));
	}

	/** All curated boss names (lowercase) - test and tooling surface. */
	public static Set<String> names()
	{
		return Collections.unmodifiableSet(OVERRIDES.keySet());
	}
}
