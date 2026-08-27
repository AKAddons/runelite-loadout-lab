package com.loadoutlab.collection;

import net.runelite.client.config.ConfigManager;

/**
 * Per-character config keys, plus the one-time adoption of the pre-0.4.1
 * unscoped entry.
 *
 * <p>Field report 2026-08-26: on an install upgraded from 0.4.0 the alt still
 * showed the main's exclude list. The first cut fell back to the unscoped key
 * whenever the scoped one was empty, which is true for EVERY new character -
 * so every character adopted the main's list. Adoption has to happen once and
 * then stop, which means MOVING the entry into the first character that loads
 * it rather than copying it.
 */
final class ScopedKeys
{
	private ScopedKeys()
	{
	}

	/** A scope with no account hash behaves as the pre-0.4.1 bucket, so edits
	 * made before login are adopted by the first character instead of landing
	 * in a key nothing reads. */
	static String key(String scope, String legacyKey)
	{
		return scope == null || scope.isEmpty() ? legacyKey : scope + "." + legacyKey;
	}

	/** Stored JSON for this scope, adopting the legacy entry once. */
	static String read(ConfigManager configManager, String group, String scope, String legacyKey)
	{
		return adopt(configManager, group, key(scope, legacyKey), legacyKey);
	}

	/** As {@link #read}, for a store whose legacy key is not simply the
	 * scoped key without its prefix. */
	static String adopt(ConfigManager configManager, String group, String key, String legacyKey)
	{
		String json = configManager.getConfiguration(group, key);
		if (json != null && !json.isEmpty())
		{
			return json;
		}
		if (key.equals(legacyKey))
		{
			return null;
		}
		String legacy = configManager.getConfiguration(group, legacyKey);
		if (legacy == null || legacy.isEmpty())
		{
			return null;
		}
		// A move, not a copy: the second character must find nothing here.
		configManager.setConfiguration(group, key, legacy);
		configManager.unsetConfiguration(group, legacyKey);
		return legacy;
	}
}
