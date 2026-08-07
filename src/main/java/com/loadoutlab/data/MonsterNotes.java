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
			case "kalphite queen":
				return "Each form prays a style away - crawling blocks magic"
					+ " and ranged, airborne blocks melee. Verac's set"
					+ " pierces the prayer (25% guaranteed hits, +1 damage);"
					+ " otherwise switch styles per form.";
			case "salarin the twisted":
				return "Only Strike spells damage him - a flat 9-12 set by"
					+ " your highest strike unlocked. Gear and damage"
					+ " bonuses do nothing; a Ring of recoil and"
					+ " dynamite(p) also work.";
			case "jal-nib":
				return "Three nibblers spawn every wave - one Ice Barrage"
					+ " cast clears the trio (a 152 xp drop means all three"
					+ " died). Bring the Ancient spellbook; Blood Barrage"
					+ " heals off packs once the wave is under control.";
			case "jal-ak":
				return "Blood or Ice Barrage is effective on the blob and"
					+ " its spawns.";
			case "respiratory system":
				return "Standard melee cannot damage the vents - halberds,"
					+ " ranged, or magic only. A demonbane hit (Scorching"
					+ " bow, Arclight...) destroys one instantly, and every"
					+ " hit lands for at least half your max. The four vents"
					+ " are spread out: ranged one-shots them all from one"
					+ " spot, so the melee numbers include the walk.";
			case "abyssal sire":
				if (monster.getVersion() != null
					&& monster.getVersion().startsWith("Phase 1"))
				{
					return "Disorient the Sire with a Shadow spell (Ancient"
						+ " spellbook - bring Ancients or Spellbook Swap),"
						+ " then kill the respiratory systems.";
				}
				return null;
			case "zulrah":
				return "Bring a recoil effect for the snakelings - Ring of"
					+ " recoil, Ring of suffering (r), or Echo boots. Hits"
					+ " above 50 are rerolled to 45-50.";
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
