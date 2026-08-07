package com.loadoutlab.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Curated per-monster "bring this" recommendations, chipped into the
 * inventory row. The mechanics NOTE tells the player why; these chips
 * SHOW the items (field report 2026-08-06: the Inferno's barrage
 * stipulation rendered as prose but nothing recommended the runes or
 * the spellbook). Rune chips name their spell AND spellbook in the
 * tooltip - counts are deliberately absent, like every supply chip.
 */
public final class RecommendedBring
{
	private static final int AIR_RUNE = 556;
	private static final int WATER_RUNE = 555;
	private static final int DEATH_RUNE = 560;
	private static final int BLOOD_RUNE = 565;
	private static final int SOUL_RUNE = 566;
	private static final Set<Integer> RUNES =
		Set.of(AIR_RUNE, WATER_RUNE, DEATH_RUNE, BLOOD_RUNE, SOUL_RUNE);
	/** Antidote++ (4) - the representative antipoison chip; the supplies
	 * system stays the configurable path for exact tiers. */
	private static final int ANTIDOTE_PP = 5952;

	private RecommendedBring()
	{
	}

	/** True when the chip is a casting rune - the caller adds the rune
	 * pouch alongside. */
	public static boolean isRune(int itemId)
	{
		return RUNES.contains(itemId);
	}

	/** True when this monster's recommendation puts the trip on the
	 * ANCIENT spellbook (the barrage stipulations) - which has no path to
	 * Arceuus summons, so the thrall/Death Charge folds must stand down
	 * (field report 2026-08-06: the Inferno recommended barrages and
	 * thralls at once). A direct switch, not a scan of chipsFor's
	 * tooltips: the panel asks this per mob per card rebuild, and
	 * RecommendedBringTest pins the two in agreement. */
	public static boolean recommendsAncients(MonsterStats monster)
	{
		if (monster == null)
		{
			return false;
		}
		switch (monster.getName().toLowerCase(Locale.ROOT))
		{
			case "jal-nib":
			case "jal-ak":
				return true;
			case "abyssal sire":
				return monster.versionStartsWith("Phase 1");
			default:
				return false;
		}
	}

	/** The curated chips for this monster: item id -> tooltip. Empty for
	 * monsters with no recommendation. */
	public static Map<Integer, String> chipsFor(MonsterStats monster)
	{
		LinkedHashMap<Integer, String> chips = new LinkedHashMap<>();
		if (monster == null)
		{
			return chips;
		}
		String name = monster.getName().toLowerCase(Locale.ROOT);
		switch (name)
		{
			case "jal-nib":
			case "jal-ak":
			{
				String why = "Barrage runes (Ancient spellbook) - Ice clears"
					+ " the nibbler trio, Blood heals off packs";
				chips.put(WATER_RUNE, why);
				chips.put(DEATH_RUNE, why);
				chips.put(BLOOD_RUNE, why);
				chips.put(SOUL_RUNE, why);
				break;
			}
			case "abyssal sire":
				if (monster.versionStartsWith("Phase 1"))
				{
					String why = "Shadow Barrage runes (Ancient spellbook)"
						+ " - disorients the Sire";
					chips.put(AIR_RUNE, why);
					chips.put(SOUL_RUNE, why);
					chips.put(DEATH_RUNE, why);
				}
				break;
			case "dagannoth rex":
			case "dagannoth prime":
			case "dagannoth supreme":
				chips.put(ANTIDOTE_PP, "Spinolyps in the room poison"
					+ " - bring antipoison");
				break;
			default:
				break;
		}
		return chips;
	}
}
