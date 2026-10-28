package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.LootContainers;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An ash dropper with no book of its own suggests Arceuus for Demonic Offering
 * (ISS-01M5TQ977R0MXBMSQ3VBHS29GB, Andrew 2026-10-25: "for something liek
 * abyssal demons that drop abyssal ashes and when there is no spellbook
 * requirements, it would be good to default to arceuss").
 */
class DemonicOfferingBookTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink {
		Map<String, Object> published;

		@Override
		public void publishPage(Map page) {
			published = page;
		}
	}

	private static Object offering(String query, boolean castable) {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state, (m, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setArceuusReady(magic -> castable && magic == 84);
		MonsterStats mob = data.searchMonsters(query, 1).get(0);
		engine.execute("select", Map.of("id", mob.getId()));
		engine.onResults(mob, Map.of());
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		return ((Map<?, ?>) ((List<?>) entry.get("mobs")).get(0)).get("demonicOffering");
	}

	@Test
	@DisplayName("abyssal demons drop demonic ashes, black dragons do not")
	void ashDroppers() {
		assertTrue(LootContainers.drops(data.searchMonsters("abyssal demon", 1).get(0), "ashes"));
		assertFalse(LootContainers.drops(data.searchMonsters("black dragon", 1).get(0), "ashes"));
	}

	@Test
	@DisplayName("castable: an abyssal demon's card offers Demonic Offering, a black dragon's does not; not castable, neither")
	void cardFlag() {
		assertEquals(Boolean.TRUE, offering("abyssal demon", true));
		assertEquals(Boolean.FALSE, offering("black dragon", true));
		assertEquals(Boolean.FALSE, offering("abyssal demon", false));
	}
}
