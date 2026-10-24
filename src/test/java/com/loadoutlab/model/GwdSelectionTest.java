package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** GWD selections bring room for their protection and an Ancient chip (ISS-01M5G9KPDQWBR014Z10F79B29T). */
class GwdSelectionTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink {
		Map<String, Object> published;

		@Override
		public void publishPage(Map<String, Object> page) {
			published = page;
		}
	}

	private static Object[] select(String name) {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, p, onDone) -> {
			},
			link);
		MonsterStats mob = data.searchMonsters(name, 1).get(0);
		assertTrue(engine.execute("select", Map.of("id", mob.getId())));
		return new Object[]{state, engine, link, mob};
	}

	@Test
	@DisplayName("god nicknames find their general (Andrew 2026-10-21: zam/zammy, arma/armadyl, sara)")
	void godNicknames() {
		for (String q : new String[]{"zam", "zammy", "Zamorak"}) {
			assertEquals("K'ril Tsutsaroth", data.searchMonsters(q, 5).get(0).getName(), q);
		}
		for (String q : new String[]{"arma", "Armadyl"}) {
			assertEquals("Kree'arra", data.searchMonsters(q, 5).get(0).getName(), q);
		}
		for (String q : new String[]{"sara", "Saradomin"}) {
			assertEquals("Commander Zilyana", data.searchMonsters(q, 5).get(0).getName(), q);
		}
		assertEquals("General Graardor", data.searchMonsters("bandos", 5).get(0).getName());
	}

	@Test
	@DisplayName("a GWD general defaults Inventory to the spec + every route god + the Ancient")
	void inventoryDefault() {
		assertEquals(5, ((PageState) select("general graardor")[0]).paramsNode().get("maxSwaps"));
		assertEquals(6, ((PageState) select("kree'arra")[0]).paramsNode().get("maxSwaps"));
		assertEquals(5, ((PageState) select("sergeant strongstack")[0]).paramsNode().get("maxSwaps"));
		assertEquals(1, ((PageState) select("vorkath")[0]).paramsNode().get("maxSwaps"), "other singles keep 1");
	}

	@Test
	@DisplayName("the Ancient chip param is off by default and reaches the compute")
	void ancientParam() {
		Object[] s = select("general graardor");
		PageState state = (PageState) s[0];
		CommandEngine engine = (CommandEngine) s[1];
		assertEquals(Boolean.FALSE, state.paramsNode().get("gwdAncient"));
		assertFalse(engine.gwdAncient());
		engine.execute("set-param", Map.of("param", "gwdAncient", "value", true));
		assertTrue(engine.gwdAncient());
	}

	@Test
	@DisplayName("the page marks GWD monsters so the card can show the Ancient chip")
	void pageFlag() {
		Object[] s = select("general graardor");
		CommandEngine engine = (CommandEngine) s[1];
		engine.onResults((MonsterStats) s[3], Map.of());
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) ((CaptureLink) s[2]).published.get("entries")).get(0);
		Map<?, ?> mob = (Map<?, ?>) ((List<?>) entry.get("mobs")).get(0);
		assertEquals(Boolean.TRUE, mob.get("gwd"));
	}
}
