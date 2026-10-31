package com.ll.profile;

import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.Map;

/**
 * Profile fixtures only the test suite and the headless runner build.
 * Kept out of main source so they do not count against the Plugin Hub
 * bot's 200k token budget; construction is the production version
 * verbatim.
 */
public final class PlayerProfileTestSupport {
	private PlayerProfileTestSupport() {
	}

	/** A maxed account that owns nothing - game-best queries only. */
	public static PlayerProfile maxed() {
		return new PlayerProfile(Levels.MAXED, Levels.MAXED,
			Prayers.ALL, RequirementProfile.MAXED, Map.of(), true);
	}
}
