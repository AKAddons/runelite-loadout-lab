package com.loadoutlab.data;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Quest;

/**
 * The ORACLE for the name_rules.json extraction: the pre-extraction bodies of
 * QuestUnlocks.forItem, DataService.spellLevel and
 * DataService.knownSlayerMonster, copied verbatim out of git at
 * 0c93ff915e370b3414f03451d589bb6625e5be8a.
 *
 * NameRulesGoldenTest replays these against the real gear/spell/monster corpus
 * and asserts the resource-driven implementations agree exactly. Do not
 * "improve" this file - its whole value is being the frozen old behaviour.
 */
final class LegacyNameRules
{
	private LegacyNameRules()
	{
	}

	static Set<String> forItem(int id, String name, String version)
	{
		String item = ((name == null ? "" : name) + " " + (version == null ? "" : version)).toLowerCase(Locale.ROOT);
		Set<String> quests = new LinkedHashSet<>();

		requireIf(quests, item.contains("ava's attractor") || item.contains("ava's accumulator"), Quest.ANIMAL_MAGNETISM);
		requireIf(quests, item.contains("ava's assembler"), Quest.DRAGON_SLAYER_II);
		requireIf(quests, item.contains("dragon scimitar"), Quest.MONKEY_MADNESS_I);
		requireIf(quests, item.contains("heavy ballista") || item.contains("light ballista"), Quest.MONKEY_MADNESS_II);
		requireIf(quests, item.contains("dragon halberd"), Quest.REGICIDE);
		requireIf(quests, item.contains("dragon battleaxe") || item.contains("dragon mace"), Quest.HEROES_QUEST);
		requireIf(quests, item.contains("rune platebody") || item.contains("d'hide body"), Quest.DRAGON_SLAYER_I);
		requireIf(quests, item.contains("helm of neitiznot"), Quest.THE_FREMENNIK_ISLES);
		requireIf(quests, item.contains("neitiznot faceguard"), Quest.THE_FREMENNIK_EXILES);
		requireIf(quests, item.contains("berserker helm") || item.contains("archer helm") || item.contains("farseer helm") || item.contains("warrior helm"), Quest.THE_FREMENNIK_TRIALS);
		requireIf(quests, item.contains("barrows gloves"), Quest.RECIPE_FOR_DISASTER__CULINAROMANCER);
		requireIf(quests, item.contains("dragon gloves"), Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI);
		requireIf(quests, item.contains("rune gloves"), Quest.RECIPE_FOR_DISASTER__SIR_AMIK_VARZE);
		requireIf(quests, item.contains("adamant gloves"), Quest.RECIPE_FOR_DISASTER__SKRACH_UGLOGWEE);
		requireIf(quests, item.contains("mithril gloves"), Quest.RECIPE_FOR_DISASTER__LUMBRIDGE_GUIDE);
		requireIf(quests, item.contains("ancient staff"), Quest.DESERT_TREASURE_I);
		requireIf(quests, item.contains("keris partisan"), Quest.BENEATH_CURSED_SANDS);
		requireIf(quests, item.contains("lunar"), Quest.LUNAR_DIPLOMACY);
		requireIf(quests, item.contains("crystal bow") || item.contains("crystal shield"), Quest.ROVING_ELVES);
		requireIf(quests, item.contains("bow of faerdhinen") || item.contains("crystal helm") || item.contains("crystal body") || item.contains("crystal legs"), Quest.SONG_OF_THE_ELVES);
		requireIf(quests, item.contains("mythical cape"), Quest.DRAGON_SLAYER_II);
		requireIf(quests, item.contains("legends' cape"), Quest.LEGENDS_QUEST);
		requireIf(quests, item.contains("salve amulet"), Quest.HAUNTED_MINE);
		requireIf(quests, item.contains("barrelchest anchor"), Quest.THE_GREAT_BRAIN_ROBBERY);
		requireIf(quests, item.contains("darklight") || item.contains("arclight"), Quest.SHADOW_OF_THE_STORM);
		requireIf(quests, item.contains("silverlight"), Quest.DEMON_SLAYER);
		requireIf(quests, item.contains("wolfbane"), Quest.PRIEST_IN_PERIL);
		requireIf(quests, item.contains("ivandis flail"), Quest.IN_AID_OF_THE_MYREQUE);
		requireIf(quests, item.contains("blisterwood flail"), Quest.SINS_OF_THE_FATHER);
		requireIf(quests, item.contains("bearhead"), Quest.MOUNTAIN_DAUGHTER);
		requireIf(quests, item.contains("dwarven helmet"), Quest.BETWEEN_A_ROCK);
		requireIf(quests, item.contains("initiate"), Quest.RECRUITMENT_DRIVE);
		requireIf(quests, item.contains("proselyte"), Quest.THE_SLUG_MENACE);
		requireIf(quests, item.contains("dorgeshuun") || item.contains("bone crossbow"), Quest.THE_LOST_TRIBE);
		requireIf(quests, item.contains("gadderhammer"), Quest.IN_AID_OF_THE_MYREQUE);

		return quests;
	}

	private static void requireIf(Set<String> quests, boolean condition, Quest quest)
	{
		if (condition)
		{
			quests.add(quest.name());
		}
	}

	static int spellLevel(String name)
	{
		switch (name == null ? "" : name)
		{
			case "Wind Strike":
				return 1;
			case "Water Strike":
				return 5;
			case "Earth Strike":
				return 9;
			case "Fire Strike":
				return 13;
			case "Wind Bolt":
				return 17;
			case "Water Bolt":
				return 23;
			case "Earth Bolt":
				return 29;
			case "Fire Bolt":
				return 35;
			case "Crumble Undead":
				return 39;
			case "Wind Blast":
				return 41;
			case "Water Blast":
				return 47;
			case "Iban Blast":
				return 50;
			case "Magic Dart":
				return 50;
			case "Earth Blast":
				return 53;
			case "Fire Blast":
				return 59;
			case "Saradomin Strike":
			case "Claws of Guthix":
			case "Flames of Zamorak":
				return 60;
			case "Wind Wave":
				return 62;
			case "Water Wave":
				return 65;
			case "Earth Wave":
				return 70;
			case "Fire Wave":
				return 75;
			case "Wind Surge":
				return 81;
			case "Water Surge":
				return 85;
			case "Earth Surge":
				return 90;
			case "Fire Surge":
				return 95;
			case "Smoke Rush":
				return 50;
			case "Shadow Rush":
				return 52;
			case "Blood Rush":
				return 56;
			case "Ice Rush":
				return 58;
			case "Smoke Burst":
				return 62;
			case "Shadow Burst":
				return 64;
			case "Blood Burst":
				return 68;
			case "Ice Burst":
				return 70;
			case "Smoke Blitz":
				return 74;
			case "Shadow Blitz":
				return 76;
			case "Blood Blitz":
				return 80;
			case "Ice Blitz":
				return 82;
			case "Smoke Barrage":
				return 86;
			case "Shadow Barrage":
				return 88;
			case "Blood Barrage":
				return 92;
			case "Ice Barrage":
				return 94;
			case "Ghostly Grasp":
				return 35;
			case "Skeletal Grasp":
				return 56;
			case "Undead Grasp":
				return 79;
			case "Inferior Demonbane":
				return 44;
			case "Superior Demonbane":
				return 62;
			case "Dark Demonbane":
				return 82;
			default:
				return 1;
		}
	}

	static boolean knownSlayerMonster(String name)
	{
		String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
		return normalized.contains("aberrant spectre")
			|| normalized.contains("abyssal demon")
			|| normalized.contains("banshee")
			|| normalized.contains("basilisk")
			|| normalized.contains("bloodveld")
			|| normalized.contains("cave crawler")
			|| normalized.contains("cave horror")
			|| normalized.contains("crawling hand")
			|| normalized.contains("dust devil")
			|| normalized.contains("gargoyle")
			|| normalized.contains("kurask")
			|| normalized.contains("nechryael")
			|| normalized.contains("rockslug")
			|| normalized.contains("skeletal wyvern")
			|| normalized.contains("smoke devil")
			|| normalized.contains("turoth")
			|| normalized.contains("wyrm")
			|| normalized.contains("drake")
			|| normalized.contains("hydra")
			|| normalized.contains("tzkal-zuk")
			|| normalized.contains("tztok-jad")
			|| normalized.contains("jaltok-jad")
			|| normalized.contains("tzhaar")
			|| normalized.contains("tok-xil")
			|| normalized.contains("yt-mejkot")
			|| normalized.contains("yt-hurkot")
			|| normalized.contains("ket-zek")
			|| normalized.contains("tz-kih")
			|| normalized.contains("tz-kek")
			|| normalized.startsWith("jal-");
	}
}
