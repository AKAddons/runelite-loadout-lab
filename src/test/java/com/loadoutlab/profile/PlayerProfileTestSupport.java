package com.loadoutlab.profile;

import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Map;

/**
 * Profile fixtures only the test suite and the headless runner build.
 * Kept out of main source so they do not count against the Plugin Hub
 * bot's 200k token budget; construction is the production version
 * verbatim.
 */
public final class PlayerProfileTestSupport
{
	private PlayerProfileTestSupport()
	{
	}

	/** A maxed account that owns nothing - game-best queries only. */
	public static PlayerProfile maxed()
	{
		return new PlayerProfile(PlayerLevels.MAXED, PlayerLevels.MAXED,
			PrayerUnlocks.ALL, RequirementProfile.MAXED, Map.of(), true);
	}
}
