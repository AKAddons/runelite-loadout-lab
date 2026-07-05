// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Quest;

final class QuestUnlocks
{
	private QuestUnlocks()
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
}
