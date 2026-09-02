package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

/**
 * The last-known character snapshot - real levels, finished quests and the
 * prayer unlocks - persisted per character. Before 0.5.0 only the bank
 * persisted, so a logged-out compute priced a MAXED stranger wearing your
 * bank (field report 2026-09-01: Piety suggested to a 45-prayer account
 * before login). Now it prices the character seen last; MAXED remains the
 * fallback only for a character never seen.
 */
public class ProfileStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "profile";
	/** Unscoped: the character seen last - the scope to load while logged out. */
	static final String LAST_KEY = "lastScope";

	private static final class Stored
	{
		Map<Skill, Integer> levels;
		Set<String> quests;
		/** {@link PrayerUnlocks#key()} bits. */
		String prayers;
	}

	private final ConfigManager configManager;
	private final Gson gson;
	private String worldScope;
	private Stored stored;

	public ProfileStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** The scope of the character seen last; null on a fresh install. */
	public String lastScope()
	{
		return configManager.getConfiguration(CONFIG_GROUP, LAST_KEY);
	}

	public synchronized void loadScope(String scope)
	{
		worldScope = scope;
		configManager.setConfiguration(CONFIG_GROUP, LAST_KEY, scope);
		stored = null;
		String json = ScopedKeys.read(configManager, CONFIG_GROUP, scope, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			stored = gson.fromJson(json, Stored.class);
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: this character counts as never seen.
		}
	}

	/** Re-read from config - the active RuneLite profile may have changed. */
	public synchronized void reload()
	{
		if (worldScope != null)
		{
			loadScope(worldScope);
		}
	}

	public synchronized void save(Map<Skill, Integer> real, Set<String> quests, PrayerUnlocks unlocks)
	{
		Stored next = new Stored();
		next.levels = real;
		next.quests = quests;
		next.prayers = unlocks.key();
		stored = next;
		configManager.setConfiguration(CONFIG_GROUP, ScopedKeys.key(worldScope, KEY), gson.toJson(next));
	}

	/** Null for a character never snapshotted - the callers fall back to MAXED. */
	public synchronized RequirementProfile profile()
	{
		return stored == null ? null : new RequirementProfile(stored.levels, stored.quests);
	}

	public synchronized PlayerLevels levels()
	{
		return stored == null ? null : PlayerLevels.from(stored.levels);
	}

	public synchronized PrayerUnlocks unlocks()
	{
		String p = stored == null ? null : stored.prayers;
		if (p == null || p.length() < 5)
		{
			return null;
		}
		return new PrayerUnlocks(p.charAt(0) == '1', p.charAt(1) == '1', p.charAt(2) == '1',
			p.charAt(3) == '1', p.charAt(4) == '1');
	}
}
