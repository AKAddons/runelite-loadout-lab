package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Arceuus offerings in place of the sanctifier and the bonecrusher (ISS-01M5VJAHRWNHFRPPNK3J6B63SZ,
 * Andrew 2026-10-26: "being able to replace ash sanctifier with demonic offering
 * and bonecrusher with sinister offering"; his pick: a wrench setting per pair).
 */
class OfferingChoiceTest {
	private static final int ASH_SANCTIFIER = 25781;
	private static final int BONECRUSHER = 13116;
	private static final int SOUL = 566;
	private static final int WRATH = 21880;
	private static final int BLOOD = 565;
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

	/** The mob node's utility rows and its sinister flag, under the given wrench choices. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> mob(String query, Map<String, String> choices, boolean castable) {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state, (m, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> id == ASH_SANCTIFIER || id == BONECRUSHER || id == SOUL || id == WRATH || id == BLOOD);
		engine.setSupplyDefaults(() -> new HashMap<>(choices));
		engine.setArceuusReady(magic -> castable && magic >= 84);
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
		return (Map<String, Object>) ((List<?>) entry.get("mobs")).get(0);
	}

	@SuppressWarnings("unchecked")
	private static Set<Object> ids(Map<String, Object> mob) {
		return ((List<Map>) mob.get("utilityRunes")).stream().map(r -> ((Number) r.get("id")).intValue())
			.collect(Collectors.toSet());
	}

	@Test
	@DisplayName("by default the abyssal demon carries the ash sanctifier")
	void defaultKeepsTheItem() {
		assertTrue(ids(mob("abyssal demon", Map.of(), true)).contains(ASH_SANCTIFIER));
	}

	@Test
	@DisplayName("Demonic Offering picked and castable: no sanctifier, its soul and wrath runes ride")
	void demonicOffering() {
		Set<Object> ids = ids(mob("abyssal demon", Map.of("ashes", "DEMONIC_OFFERING"), true));
		assertFalse(ids.contains(ASH_SANCTIFIER), ids.toString());
		assertTrue(ids.contains(SOUL) && ids.contains(WRATH), ids.toString());
	}

	@Test
	@DisplayName("Sinister Offering picked and castable at a black dragon: no bonecrusher, blood and wrath ride, the plate goes Arceuus")
	void sinisterOffering() {
		Map<String, Object> mob = mob("black dragon", Map.of("bones", "SINISTER_OFFERING"), true);
		Set<Object> ids = ids(mob);
		assertFalse(ids.contains(BONECRUSHER), ids.toString());
		assertTrue(ids.contains(BLOOD) && ids.contains(WRATH), ids.toString());
		assertEquals(Boolean.TRUE, mob.get("sinisterOffering"));
	}

	@Test
	@DisplayName("picked but not castable: the bonecrusher stays, no offering runes; None carries neither")
	void notCastable() {
		Set<Object> ids = ids(mob("black dragon", Map.of("bones", "SINISTER_OFFERING"), false));
		assertTrue(ids.contains(BONECRUSHER), ids.toString());
		assertFalse(ids.contains(BLOOD), ids.toString());
		assertFalse(ids(mob("black dragon", Map.of("bones", "NONE"), true)).contains(BONECRUSHER));
	}
}
