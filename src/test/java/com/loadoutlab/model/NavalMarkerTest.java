package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REQ-SC-1/6: the model is where naval identity rides - search options carry
 * a naval flag for ship-eligible monsters, and every mob node says whether
 * its fight happens from a boat, exactly as wilderness/taskOnly already do.
 * The Swing indicator renders FROM these flags, so this is the seam to pin.
 */
class NavalMarkerTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static MonsterStats byName(String name)
	{
		return data.searchMonsters(name, 1).get(0);
	}

	@Test
	@DisplayName("mob nodes carry the naval flag - sharks yes, Graardor no")
	void mobNodesCarryTheFlag()
	{
		Map<String, Object> shark = RenderModel.mob(byName("hammerhead shark"));
		assertEquals(Boolean.TRUE, shark.get("naval"),
			"a ship-combat monster's node must say so");
		Map<String, Object> graardor = RenderModel.mob(byName("general graardor"));
		assertEquals(Boolean.FALSE, graardor.get("naval"),
			"a land monster must carry naval=false, not a missing key");
	}

	@Test
	@DisplayName("search options flag naval rows and leave land rows unflagged")
	void searchOptionsFlagNavalRows()
	{
		CommandEngine engine = new CommandEngine(data, new PageState(),
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			new CompanionLink());

		boolean navalSeen = false;
		for (Map<String, Object> option : engine.searchOptions("hammerhead"))
		{
			if (option.get("id") != null)
			{
				navalSeen |= Boolean.TRUE.equals(option.get("naval"));
			}
		}
		assertTrue(navalSeen, "the hammerhead search row lost its naval flag");

		for (Map<String, Object> option : engine.searchOptions("graardor"))
		{
			assertNull(option.get("naval"),
				"land rows must not carry the key at all: " + option.get("label"));
		}
	}
}
