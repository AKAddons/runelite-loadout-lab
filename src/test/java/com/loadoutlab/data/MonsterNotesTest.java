package com.loadoutlab.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The curated mechanics notes are name-keyed strings - a rename in the
 * corpus silently orphans one, so the load-bearing notes are pinned
 * against the LOADED rows, not just the switch.
 */
class MonsterNotesTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static String noteFor(String search)
	{
		MonsterStats mob = data.searchMonsters(search, 1).get(0);
		return MonsterNotes.noteFor(mob);
	}

	@Test
	@DisplayName("the Inferno's nibblers carry the barrage stipulation")
	void nibblersRecommendBarrages()
	{
		String note = noteFor("jal-nib");
		assertNotNull(note);
		assertTrue(note.contains("Ice Barrage"), "the trio-clear is the point");
		assertTrue(note.contains("Ancient"), "the spellbook is the stipulation");
	}

	@Test
	@DisplayName("the blob seconds it")
	void blobRecommendsBarrages()
	{
		String note = noteFor("jal-ak");
		assertNotNull(note);
		assertTrue(note.contains("Barrage"));
	}

	@Test
	@DisplayName("Vorkath carries the Crumble Undead stipulation")
	void vorkathRecommendsCrumbleUndead()
	{
		String note = noteFor("vorkath");
		assertNotNull(note);
		assertTrue(note.contains("Crumble Undead"), "the spawn one-shot is the point");
		assertTrue(note.contains("Zombified Spawn"), "names the mechanic it answers");
	}
}
