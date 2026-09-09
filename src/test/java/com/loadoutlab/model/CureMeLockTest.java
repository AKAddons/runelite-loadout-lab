package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, 2026-09-08: "cure me should lock the spell book into lunar and
 * shouldn't be an option if relying on other spellbooks. it should
 * override the thrall setting." */
class CureMeLockTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	/** Store ops whose lensed mob chose Cure Me, with a pin the test can flip. */
	private static final class CureMeOps extends TestStoreOps
	{
		String pinned = "";

		@Override
		public Map<String, String> supplyOverrides(int profileId)
		{
			return Map.of("antivenom", "CURE_ME");
		}

		@Override
		public String pinnedSpell(int monsterId)
		{
			return pinned;
		}

		@Override
		public void setPinnedSpell(int monsterId, String spellName)
		{
			pinned = spellName == null ? "" : spellName;
		}
	}

	@Test
	@DisplayName("Cure Me on a venomous mob computes with the Lunar lock and no Death Charge; a pinned spell or a non-venomous mob leaves both alone")
	void cureMeLocksLunar()
	{
		AtomicReference<String> lock = new AtomicReference<>();
		AtomicInteger charge = new AtomicInteger(-1);
		AtomicInteger computes = new AtomicInteger();
		CommandEngine engine = new CommandEngine(data, new PageState(),
			(mob, f2p, onTask, wild, book, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
				lock.set(book);
				charge.set(dc);
				computes.incrementAndGet();
			}, new CompanionLink());
		// "zulrah" resolves to the roster group, so capture that path too.
		engine.setRosterCompute((mobs, f2p, onTask, wild, book, tradeables, risk, antifire, dc, spec,
			boosts, prayers, budget, swaps, onDone) ->
		{
			lock.set(book);
			charge.set(dc);
			computes.incrementAndGet();
		});
		CureMeOps ops = new CureMeOps();
		engine.setStoreOps(ops);
		engine.setSupplyDefaults(() -> Map.of("antivenom", "DETECT_BEST"));
		assertTrue(engine.execute("set-param", Map.of("param", "deathCharge", "value", 1)));

		assertTrue(engine.execute("select", Map.of("query", "zulrah")));
		assertEquals("lunar", lock.get(), "Cure Me camps Lunar");
		assertEquals(0, charge.get(), "Death Charge is Arceuus - overridden");

		assertTrue(engine.execute("set-pinned-spell", Map.of("name", "Fire Bolt")));
		assertEquals("", lock.get(), "a pinned standard spell relies on another book: Cure Me steps aside");
		assertEquals(1, charge.get());

		// Picking an anti-venom choice recomputes (field 2026-09-08: the card's
		// book did not change until something else recomputed).
		int before = computes.get();
		assertTrue(engine.execute("set-supply-override", Map.of("category", "antivenom", "choice", "CURE_ME")));
		assertEquals(before + 1, computes.get(), "anti-venom choices recompute");
		assertTrue(engine.execute("set-supply-override", Map.of("category", "food", "choice", "NONE")));
		assertEquals(before + 1, computes.get(), "other supplies only republish");

		ops.pinned = "";
		assertTrue(engine.execute("select", Map.of("query", "goblin")));
		assertEquals("", lock.get(), "no venom, no Cure Me");
		assertEquals(1, charge.get());
	}
}
