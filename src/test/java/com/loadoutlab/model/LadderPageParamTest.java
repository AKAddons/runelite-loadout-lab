package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strip's "more" control sends set-param ladderPage; the param must be
 * declared or the state refuses it and the click does nothing (Andrew
 * 2026-10-24: "more steps up is not working for me" -
 * ISS-01M5QJMTBRNAWX5WT7NRJD8JWC).
 */
class LadderPageParamTest {
	private static final class Link extends CompanionLink {
		private Map<String, Object> last;

		@Override
		public void publishPage(Map<String, Object> page) {
			last = page;
		}

		@Override
		public Map<String, Object> lastPage() {
			return last;
		}
	}

	@Test
	@DisplayName("set-param ladderPage is accepted as a view param (no recompute) and opening another slot resets it to 0")
	void ladderPageIsADeclaredViewParam() {
		LoadoutData data = new DataService().load();
		AtomicInteger computes = new AtomicInteger();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> computes.incrementAndGet(), new Link());
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		int before = computes.get();

		assertTrue(engine.execute("set-param", Map.of("param", "ladderPage", "value", 2)), "the page turns");
		assertEquals(2, ((Number) state.paramsNode().get("ladderPage")).intValue());
		assertEquals(before, computes.get(), "a view param never recomputes");

		assertTrue(engine.execute("set-param", Map.of("param", "ladderSlot", "value", "weapon")));
		assertEquals(0, ((Number) state.paramsNode().get("ladderPage")).intValue(), "a fresh slot opens on its first page");
		assertEquals(before, computes.get());
	}

	@Test
	@DisplayName("a new search closes the strip and resets its page (Andrew 2026-10-24: \"whenver a new search is done the ladder view should be reset\")")
	void aNewSearchClosesTheStrip() {
		LoadoutData data = new DataService().load();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, new Link());
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		assertTrue(engine.execute("set-param", Map.of("param", "ladderSlot", "value", "weapon")));
		assertTrue(engine.execute("set-param", Map.of("param", "ladderPage", "value", 2)));

		assertTrue(engine.execute("select", Map.of("query", "zulrah")));
		assertEquals("", state.paramsNode().get("ladderSlot"), "no strip until a slot is clicked");
		assertEquals(0, ((Number) state.paramsNode().get("ladderPage")).intValue(), "page one");

		assertTrue(engine.execute("set-param", Map.of("param", "ladderSlot", "value", "weapon")));
		assertTrue(engine.execute("set-param", Map.of("param", "ladderPage", "value", 1)));
		assertTrue(engine.execute("select", Map.of("query", "tombs of amascut")), "a group search resets it too");
		assertEquals("", state.paramsNode().get("ladderSlot"));
		assertEquals(0, ((Number) state.paramsNode().get("ladderPage")).intValue());
	}
}
