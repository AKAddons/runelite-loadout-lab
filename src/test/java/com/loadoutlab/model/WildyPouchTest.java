package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-08-31 (Andrew, with the wiki passage): the divine rune
 * pouch is LOST on a pvp death in the Wilderness unless trouver-locked, so
 * the wilderness never recommends an unlocked pouch - a locked one (divine
 * 27509, then regular 24416) or none at all. Land trips are unchanged.
 */
class WildyPouchTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink
	{
		Map<String, Object> published;

		@Override
		public void publishPage(Map<String, Object> page)
		{
			published = page;
		}
	}

	private Object pouchFor(Set<Integer> bank, boolean wildy)
	{
		// Callisto is wilderness-EXCLUSIVE (select forces the param), so the
		// land case uses Graardor - a Callisto trip cannot be a land trip.
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(bank::contains);
		engine.setMagicLevel(99);
		MonsterStats mob = data.searchMonsters(wildy ? "callisto" : "general graardor", 1).get(0);
		engine.execute("select", Map.of("id", mob.getId()));
		state.setParam("thralls", true); // a casting trip, so the kit renders
		if (wildy)
		{
			state.setParam("inWilderness", true);
		}
		engine.onResults(mob, Map.of());
		try
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception ex)
		{
			throw new AssertionError(ex);
		}
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		Map<?, ?> mobNode = (Map<?, ?>) ((List<?>) entry.get("mobs")).get(0);
		return mobNode.get("castingPouch");
	}

	@Test
	@DisplayName("the wilderness never recommends an unlocked pouch - locked or nothing")
	void wildyPouchIsLockedOrNothing()
	{
		// Unlocked divine owned: recommended on land, NEVER in the wildy.
		assertEquals(27281, pouchFor(Set.of(27281), false));
		assertNull(pouchFor(Set.of(27281), true),
			"an unlocked divine pouch is LOST on a pvp death - never recommended");
		// A trouver-locked pouch is the wildy answer when owned.
		assertEquals(27509, pouchFor(Set.of(27281, 27509), true));
		assertEquals(24416, pouchFor(Set.of(24416), true));
	}
}
