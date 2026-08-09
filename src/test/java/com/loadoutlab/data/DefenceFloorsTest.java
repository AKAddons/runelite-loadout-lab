package com.loadoutlab.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-boss defence floors (competitor audit 2026-08-08): the id table
 * is copied verbatim from the official calculator's constants, so
 * these pins are against LOADED corpus rows carrying the same ids.
 */
class DefenceFloorsTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static MonsterStats byId(int id)
	{
		return data.getMonsters().stream()
			.filter(m -> m.getId() == id)
			.findFirst().orElseThrow();
	}

	@Test
	@DisplayName("Nex floors at 250 - ten drainable levels off a 260 base")
	void nexFloor()
	{
		MonsterStats nex = byId(11278);
		assertEquals(250, DefenceFloors.floorFor(nex));
		assertEquals(260, nex.getDefence());
	}

	@Test
	@DisplayName("Verzik and Vardorvis cannot be drained at all")
	void baseFloors()
	{
		// The corpus's version collapse may keep any phase row - every
		// phase id is in the floor table, so name search suffices.
		MonsterStats verzik = data.searchMonsters("verzik", 1).get(0);
		assertEquals(verzik.getDefence(), DefenceFloors.floorFor(verzik));
		MonsterStats vardorvis = data.searchMonsters("vardorvis", 1).get(0);
		assertEquals(vardorvis.getDefence(), DefenceFloors.floorFor(vardorvis));
	}

	@Test
	@DisplayName("unfloored monsters drain freely")
	void unfloored()
	{
		assertEquals(0, DefenceFloors.floorFor(
			data.searchMonsters("general graardor", 1).get(0)));
		assertEquals(0, DefenceFloors.floorFor(null));
	}
}
