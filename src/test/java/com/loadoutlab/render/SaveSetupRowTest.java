package com.loadoutlab.render;

import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The Save setup button sits on its own row (ISS-01M5VH8H5MBRK0BFA6B8E68Z7T, Andrew 2026-10-26: "the
 * save set button is too wide for that row and should be on its own line").
 */
class SaveSetupRowTest {
	@SuppressWarnings("unchecked")
	private static Map<String, Object> firstPage() throws Exception {
		try (java.io.InputStream in = SaveSetupRowTest.class.getResourceAsStream("pages.txt")) {
			assertNotNull(in, "pages.txt fixture missing");
			for (String line : new java.io.BufferedReader(new java.io.InputStreamReader(in,
				java.nio.charset.StandardCharsets.UTF_8)).lines().collect(java.util.stream.Collectors.toList())) {
				if (!line.isBlank() && !line.startsWith("#####")) return new com.google.gson.Gson().fromJson(line, Map.class);
			}
		}
		throw new AssertionError("no page in the fixture");
	}

	private static AbstractButton find(Container root, String text) {
		for (Component c : root.getComponents()) {
			if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText())) return (AbstractButton) c;
			if (c instanceof Container) {
				AbstractButton hit = find((Container) c, text);
				if (hit != null) return hit;
			}
		}
		return null;
	}

	@Test
	@DisplayName("Save setup is on its own row, not beside Show in bank")
	@SuppressWarnings("unchecked")
	void ownRow() throws Exception {
		Map<String, Object> page = firstPage();
		for (Object entry : (List<Object>) page.get("entries")) {
			Map<String, Object> e = (Map<String, Object>) entry;
			Map<String, Object> params = e.get("params") instanceof Map
				? new LinkedHashMap<>((Map<String, Object>) e.get("params")) : new LinkedHashMap<>();
			params.put("setupsAvailable", true);
			e.put("params", params);
		}
		ResultCards cards = new ResultCards(
			mock(ItemManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			mock(SpriteManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			mock(CommandSink.class), mock(ItemPicker.class));
		JPanel card = cards.render(page);
		AbstractButton save = find(card, "Save setup");
		AbstractButton bank = find(card, "Show in bank");
		assertNotNull(save, "the Save setup button renders");
		assertNotNull(bank, "the Show in bank button renders");
		assertNotSame(bank.getParent(), save.getParent(), "Save setup shares the bank row");
	}
}
