package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, 2026-10-15: "abyssal sire needs to be in the ancient spellbook
 * however it is recommending magic with dark demonbane which is arceuss".
 * The fight book already drove the plate and the Arceuus gate; now it is
 * the autocast lock too, unless the player locked a book or pinned a spell. */
class FightBookLockTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static final class PinOps extends TestStoreOps
	{
		String pinned = "";

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
	@DisplayName("the Sire computes with the Ancients lock; an explicit lock or a pinned spell wins; a goblin has no lock")
	void fightBookLocksTheSpell()
	{
		AtomicReference<String> lock = new AtomicReference<>();
		CommandEngine engine = new CommandEngine(data, new PageState(),
			(mob, f2p, onTask, wild, book, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) -> lock.set(book), new CompanionLink());
		// "abyssal sire" resolves to the fight's roster group, so capture that path too.
		engine.setRosterCompute((mobs, f2p, onTask, wild, book, tradeables, risk, antifire, dc, spec,
			boosts, prayers, budget, swaps, onDone) -> lock.set(book));
		PinOps ops = new PinOps();
		engine.setStoreOps(ops);

		assertTrue(engine.execute("select", Map.of("query", "abyssal sire")));
		assertEquals("ancient", lock.get(), "the Sire's fight book is the autocast lock");

		assertTrue(engine.execute("set-param", Map.of("param", "spellbookLock", "value", "standard")));
		assertEquals("standard", lock.get(), "the player's own lock wins");
		assertTrue(engine.execute("set-param", Map.of("param", "spellbookLock", "value", "")));
		assertEquals("ancient", lock.get(), "back to the fight book when the lock is cleared");

		assertTrue(engine.execute("set-pinned-spell", Map.of("name", "Dark Demonbane")));
		assertEquals("", lock.get(), "a pinned spell is the player's choice: no fight-book lock");
		ops.pinned = "";

		assertTrue(engine.execute("select", Map.of("query", "goblin")));
		assertEquals("", lock.get(), "no fight book, no lock");
	}
}
