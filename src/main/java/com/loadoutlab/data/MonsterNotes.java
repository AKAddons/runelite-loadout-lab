package com.loadoutlab.data;

import java.util.Locale;

/**
 * Curated per-monster mechanics the stat data cannot express - finishing
 * items, immunities - shown as a note under the selected monster so a
 * mathematically-correct suggestion doesn't read as a wrong one (e.g. the
 * tentacle out-DPSes the granite hammer vs Dusk by ~25%; the hammer's
 * value is the auto-smash). Wiki-verified 2026-07-05.
 */
public final class MonsterNotes
{
	private MonsterNotes()
	{
	}

	/** A short mechanics note for this monster, or null. */
	public static String noteFor(MonsterStats monster)
	{
		if (monster == null)
		{
			return null;
		}
		String name = monster.getName().toLowerCase(Locale.ROOT);
		switch (name)
		{
			case "dusk":
				return "Gargoyle: bring a rock hammer to finish it, or use the"
					+ " granite hammer (auto-smashes). Mostly immune to Magic.";
			case "gargoyle":
			case "marble gargoyle":
				return "Bring a rock hammer to finish it, or use the granite"
					+ " hammer (auto-smashes).";
			case "rockslug":
			case "giant rockslug":
				return "Bring a bag of salt to finish it.";
			case "lizard":
			case "small lizard":
			case "desert lizard":
				return "Bring an ice cooler to finish it.";
			case "zygomite":
			case "ancient zygomite":
				return "Bring fungicide spray to finish it.";
			default:
				return null;
		}
	}
}
