package com.loadoutlab.profile;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * A deterministic mid-to-high level fixture account for benchmarks and
 * golden-output captures: 40+ bank items resolved BY NAME against the
 * loaded dataset (never hardcoded ids, so a data refresh cannot silently
 * shift the fixture to different items), mid-90s combat levels, piety and
 * rigour unlocked but not augury/deadeye.
 */
public final class FixtureBank
{
	private static final String[] BANK_NAMES = {
		// Melee
		"Abyssal whip",
		"Dragon scimitar",
		"Dragon dagger",
		"Dragon defender",
		"Amulet of fury",
		"Amulet of glory",
		"Fire cape",
		"Barrows gloves",
		"Dragon boots",
		"Berserker ring",
		"Warrior ring",
		"Helm of neitiznot",
		"Fighter torso",
		"Dragon platelegs",
		"Bandos chestplate",
		"Bandos tassets",
		"Granite maul",
		// Ranged
		"Magic shortbow",
		"Rune crossbow",
		"Toxic blowpipe",
		"Rune arrow",
		"Adamant bolts",
		"Black d'hide body",
		"Black d'hide chaps",
		"Black d'hide vambraces",
		"Snakeskin boots",
		"Ava's accumulator",
		"Archers ring",
		// Magic
		"Mystic hat",
		"Mystic robe top",
		"Mystic robe bottom",
		"Mystic boots",
		"Mystic gloves",
		"Occult necklace",
		"Ancient staff",
		"Slayer's staff",
		"Trident of the seas",
		"Mage's book",
		"Seers ring",
		// Utility / defense
		"Ring of recoil",
		"Anti-dragon shield",
		"Toktz-ket-xil",
		"Rune kiteshield",
		"Rune platebody",
		"Rune platelegs",
	};

	private FixtureBank()
	{
	}

	public static PlayerProfile profile(LoadoutData data)
	{
		PlayerLevels levels = new PlayerLevels(90, 92, 85, 94, 89, 77, 90);
		PrayerUnlocks unlocks = new PrayerUnlocks(true, true, false, false, false);
		Map<Skill, Integer> reqLevels = new LinkedHashMap<>();
		reqLevels.put(Skill.ATTACK, 90);
		reqLevels.put(Skill.STRENGTH, 92);
		reqLevels.put(Skill.DEFENCE, 85);
		reqLevels.put(Skill.RANGED, 94);
		reqLevels.put(Skill.MAGIC, 89);
		reqLevels.put(Skill.PRAYER, 77);
		reqLevels.put(Skill.HITPOINTS, 90);
		reqLevels.put(Skill.SLAYER, 75);
		reqLevels.put(Skill.AGILITY, 70);
		reqLevels.put(Skill.CRAFTING, 70);
		reqLevels.put(Skill.FLETCHING, 70);
		reqLevels.put(Skill.HERBLORE, 70);
		reqLevels.put(Skill.CONSTRUCTION, 70);
		reqLevels.put(Skill.FARMING, 70);
		reqLevels.put(Skill.HUNTER, 70);
		reqLevels.put(Skill.THIEVING, 70);
		reqLevels.put(Skill.MINING, 70);
		reqLevels.put(Skill.SMITHING, 70);
		reqLevels.put(Skill.FISHING, 70);
		reqLevels.put(Skill.COOKING, 70);
		reqLevels.put(Skill.FIREMAKING, 70);
		reqLevels.put(Skill.WOODCUTTING, 70);
		reqLevels.put(Skill.RUNECRAFT, 60);
		RequirementProfile requirements = new RequirementProfile(
			reqLevels, RequirementProfile.MAXED.getCompletedQuests());
		return new PlayerProfile(levels, levels, unlocks, requirements, bank(data), true);
	}

	/** Item ids for the fixture bank, resolved by name; fails loudly on a miss. */
	public static Map<Integer, Integer> bank(LoadoutData data)
	{
		Map<Integer, Integer> owned = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (String name : BANK_NAMES)
		{
			GearItem item = firstByName(data, name);
			if (item == null)
			{
				missing.add(name);
			}
			else
			{
				owned.put(item.getId(), 1);
			}
		}
		if (!missing.isEmpty())
		{
			throw new IllegalStateException("Fixture bank items not in dataset: " + missing);
		}
		return owned;
	}

	private static GearItem firstByName(LoadoutData data, String name)
	{
		GearItem fallback = null;
		for (GearItem item : data.getGearItems())
		{
			if (!item.getName().equalsIgnoreCase(name) || !item.isStandardGear()
				|| data.isVariant(item.getId()))
			{
				continue;
			}
			if (item.getVersion().isEmpty())
			{
				return item;
			}
			if (fallback == null)
			{
				fallback = item;
			}
		}
		return fallback;
	}
}
