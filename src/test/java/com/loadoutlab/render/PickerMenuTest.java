package com.loadoutlab.render;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The prayer and boost pickers behind the header's assume icons: the same
 * game-tab grid (field ask 2026-08-20) - Detect / None check items, then a
 * cell per option with the current pick lit, each click sending its pick for
 * the card's style. Popups are not in the rendered tree, so this reads the
 * menus directly.
 */
class PickerMenuTest
{
	private static Map<String, Object> page(String prayerPick, String boostPick)
	{
		Map<String, Object> params = new LinkedHashMap<>();
		Map<String, Object> prayerPicks = new LinkedHashMap<>();
		Map<String, Object> boostPicks = new LinkedHashMap<>();
		if (prayerPick != null)
		{
			prayerPicks.put("melee", prayerPick);
		}
		if (boostPick != null)
		{
			boostPicks.put("melee", boostPick);
		}
		params.put("prayerPicks", prayerPicks);
		params.put("boostPicks", boostPicks);
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("params", params);
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("prayers", Map.of("melee", List.of("Piety", "Chivalry")));
		options.put("prayerSprites", Map.of("Piety", 1337, "Chivalry", 0));
		options.put("prayerImages", Map.of());
		options.put("boosts", Map.of("melee", List.of(
			Map.of("label", "Super combat", "key", "SUPER_COMBAT"),
			Map.of("label", "Divine super combat", "key", "DIVINE_SUPER_COMBAT"))));
		options.put("boostItems", Map.of("Super combat", 12695, "Divine super combat", 0));
		Map<String, Object> page = new LinkedHashMap<>();
		page.put("entries", List.of(entry));
		page.put("assumeOptions", options);
		return page;
	}

	private static ResultCards cards(List<String> sent, Map<String, Object> page)
	{
		ResultCards cards = new ResultCards(
			mock(ItemManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			mock(SpriteManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			(name, args) -> sent.add(name + ":" + args.get("style") + ":" + args.get("value")),
			mock(ItemPicker.class));
		cards.render(page);
		return cards;
	}

	private static List<JCheckBoxMenuItem> checks(JPopupMenu menu)
	{
		List<JCheckBoxMenuItem> out = new ArrayList<>();
		for (Component c : menu.getComponents())
		{
			if (c instanceof JCheckBoxMenuItem)
			{
				out.add((JCheckBoxMenuItem) c);
			}
		}
		return out;
	}

	private static List<JLabel> cells(JPopupMenu menu)
	{
		List<JLabel> out = new ArrayList<>();
		for (Component c : menu.getComponents())
		{
			if (c instanceof JPanel)
			{
				for (Component cell : ((JPanel) c).getComponents())
				{
					out.add((JLabel) cell);
				}
			}
		}
		return out;
	}

	private static void click(Component cell)
	{
		for (MouseListener l : cell.getMouseListeners())
		{
			l.mouseClicked(new MouseEvent(cell, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false,
				MouseEvent.BUTTON1));
		}
	}

	@Test
	@DisplayName("the prayer book: Detect / None, then a cell per prayer, the pick lit and tagged assumed")
	void prayerBook()
	{
		List<String> sent = new ArrayList<>();
		JPopupMenu menu = cards(sent, page("Chivalry", null)).prayerMenu("melee");
		List<JCheckBoxMenuItem> checks = checks(menu);
		assertEquals(List.of("Detect best", "None (prayerless)"),
			List.of(checks.get(0).getText(), checks.get(1).getText()));
		assertFalse(checks.get(0).isSelected(), "a named pick is not Detect");
		assertFalse(checks.get(1).isSelected());
		List<JLabel> cells = cells(menu);
		assertEquals(2, cells.size());
		assertEquals("Piety", cells.get(0).getToolTipText());
		assertEquals("Chivalry (assumed)", cells.get(1).getToolTipText(), "the current pick says so");
		assertNotNull(cells.get(0).getIcon(), "a prayer with a sprite shows it");
		assertEquals("C", cells.get(1).getText(), "no sprite, no image: the initial");

		click(cells.get(0));
		assertEquals(List.of("set-prayer-pick:melee:Piety"), sent);
		sent.clear();
		checks.get(1).doClick();
		assertEquals(List.of("set-prayer-pick:melee:NONE"), sent);
		sent.clear();
		checks.get(0).doClick();
		assertEquals(List.of("set-prayer-pick:melee:null"), sent, "Detect clears the pick");
	}

	@Test
	@DisplayName("the boost rack: the same shape with potion cells keyed by the option key")
	void boostRack()
	{
		List<String> sent = new ArrayList<>();
		JPopupMenu menu = cards(sent, page(null, "DIVINE_SUPER_COMBAT")).boostMenu("melee");
		List<JCheckBoxMenuItem> checks = checks(menu);
		assertEquals(List.of("Detect best in bank", "None (unboosted)"),
			List.of(checks.get(0).getText(), checks.get(1).getText()));
		List<JLabel> cells = cells(menu);
		assertEquals(List.of("Super combat", "Divine super combat (assumed)"),
			List.of(cells.get(0).getToolTipText(), cells.get(1).getToolTipText()));
		assertNotNull(cells.get(0).getIcon(), "a potion with an item shows its image");
		assertEquals("D", cells.get(1).getText(), "no item: the initial");
		click(cells.get(0));
		assertEquals(List.of("set-boost-pick:melee:SUPER_COMBAT"), sent, "the KEY goes out, not the label");
	}

	@Test
	@DisplayName("no pick yet: Detect is checked; no options for the style: the check items alone")
	void detectAndEmpty()
	{
		List<String> sent = new ArrayList<>();
		ResultCards cards = cards(sent, page(null, "NONE"));
		assertTrue(checks(cards.prayerMenu("melee")).get(0).isSelected(), "Detect = no pick");
		JPopupMenu boosts = cards.boostMenu("melee");
		assertTrue(checks(boosts).get(1).isSelected(), "NONE lights None");
		JPopupMenu ranged = cards.prayerMenu("ranged");
		assertEquals(2, ranged.getComponentCount(), "no prayers listed for ranged: no separator, no grid");
		assertTrue(cells(ranged).isEmpty());
	}
}
