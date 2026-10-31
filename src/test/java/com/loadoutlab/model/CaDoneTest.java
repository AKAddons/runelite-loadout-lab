package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The page's combat achievement tasks carry "done" from the plugin's
 * completion predicate (ISS-01M672BWDBZ9DJ7Y0YTA39RRG8).
 */
class CaDoneTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink {
		Map published;

		@Override
		public void publishPage(Map page) {
			published = page;
		}
	}

	@Test
	@DisplayName("Prayer Smasher (161) is marked done on the Kalphite Queen's page when the predicate says so, the others not")
	void doneFromThePredicate() throws Exception {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, link);
		engine.setCaDone(id -> id == 161);
		Monster kq = data.searchMonsters("kalphite queen", 1).get(0);
		engine.execute("select", Map.of("id", kq.getId()));
		engine.onResults(kq, Map.of());
		SwingUtilities.invokeAndWait(() -> {
		});
		List<Map> tasks = (List<Map>) link.published.get("caTasks");
		assertFalse(tasks.isEmpty(), "the Kalphite Queen has tasks");
		boolean seen = false;
		for (Map t : tasks) {
			boolean done = Boolean.TRUE.equals(t.get("done"));
			if (((Number) t.get("id")).intValue() == 161) {
				seen = true;
				assertTrue(done, "Prayer Smasher done");
			} else assertFalse(done, t.get("name") + " not done");
		}
		assertTrue(seen, "Prayer Smasher listed");
	}
}
