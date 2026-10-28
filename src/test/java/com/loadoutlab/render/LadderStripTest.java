package com.loadoutlab.render;

import com.loadoutlab.model.PageState;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The ladder as a strip under the gear grid (ISS-01M5GHXXEZKX8S68M5X9DHY6ME,
 * Andrew 2026-10-21: "Go with concept A"): owned steps down, the worn item,
 * steps up - bars rising left to right, unowned steps dashed and linked to
 * Not Enough Runes.
 */
class LadderStripTest {
	private static Map<String, Object> rung(int id, String name, boolean owned, double gain, String delta) {
		Map<String, Object> rung = new LinkedHashMap<>();
		rung.put("id", id);
		rung.put("name", name);
		rung.put("owned", owned);
		rung.put("price", owned ? 0 : 1_400_000);
		rung.put("gain", gain);
		rung.put("delta", delta);
		return rung;
	}

	private static Map<String, Object> ladder() {
		Map<String, Object> ladder = new LinkedHashMap<>();
		ladder.put("base", "4.20 DPS");
		ladder.put("up", List.of(rung(12006, "Abyssal tentacle", false, 0.21, "+0.21 DPS"),
			rung(22324, "Ghrazi rapier", true, 0.55, "+0.55 DPS")));
		ladder.put("down", List.of(rung(4587, "Dragon scimitar", true, -0.80, "-0.80 DPS"),
			rung(1333, "Rune scimitar", true, -1.10, "-1.10 DPS")));
		return ladder;
	}

	private static Map<String, Object> worn() {
		Map<String, Object> worn = new LinkedHashMap<>();
		worn.put("id", 4151);
		worn.put("name", "Abyssal whip");
		worn.put("base", "4.20 DPS");
		return worn;
	}

	private static ResultCards cards(List<String> sent) {
		return new ResultCards(
			mock(ItemManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			mock(SpriteManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			(name, args) -> sent.add(name + ":" + args.get("itemId")), mock(ItemPicker.class));
	}

	private static List<JComponent> named(Container root, String prefix) {
		List<JComponent> out = new ArrayList<>();
		for (Component c : root.getComponents()) {
			if (c instanceof JComponent && c.getName() != null && c.getName().startsWith(prefix)) {
				out.add((JComponent) c);
			}
			if (c instanceof Container) {
				out.addAll(named((Container) c, prefix));
			}
		}
		return out;
	}

	/** A ladder with steps past the strip's two up (ISS-01M5QD7PNMCMNRZD92RPRMHWRP). */
	private static Map<String, Object> deepLadder() {
		Map<String, Object> ladder = ladder();
		List<Map> more = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			more.add(rung(30000 + i, "Step " + (i + 3), i == 4, 1.0 + i, String.format("+%.2f DPS", 1.0 + i)));
		}
		ladder.put("upMore", more);
		return ladder;
	}

	private static ResultCards cardsParams(List<String> sent) {
		return new ResultCards(
			mock(ItemManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			mock(SpriteManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			(name, args) -> sent.add(name + ":" + args.get("param") + "=" + args.get("value")), mock(ItemPicker.class));
	}

	@Test
	@DisplayName("show more (Andrew 2026-10-24: \"an option to 'show more' on the ladder to see more alternatives\"): with further steps up the strip draws a more control that turns the page; without them, none")
	void moreControlTurnsThePage() {
		List<String> sent = new ArrayList<>();
		JPanel strip = cardsParams(sent).ladderStrip(deepLadder(), worn(), "weapon", true, null, 0);
		assertEquals(5, named(strip, "rung:").size(), "page zero is the strip as before");
		List<JComponent> more = named(strip, "ladder-more");
		assertEquals(1, more.size(), "a more control at the right");
		assertTrue(named(strip, "ladder-back").isEmpty(), "nothing to go back to on page zero");
		click(more.get(0), MouseEvent.BUTTON1);
		assertEquals(List.of("set-param:ladderPage=1"), sent, "the page is view state like the open slot");

		JPanel plain = cardsParams(new ArrayList<>()).ladderStrip(ladder(), worn(), "weapon", true, null, 0);
		assertTrue(named(plain, "ladder-more").isEmpty(), "no further steps: no control");
	}

	@Test
	@DisplayName("page one draws the next five steps up against the worn item, with back and more; the last page has no more")
	void pagesThroughTheSteps() {
		List<String> sent = new ArrayList<>();
		JPanel page1 = cardsParams(sent).ladderStrip(deepLadder(), worn(), "weapon", true, null, 1);
		List<String> ids = new ArrayList<>();
		for (JComponent cell : named(page1, "rung:")) {
			ids.add(cell.getName().substring("rung:".length()));
		}
		assertEquals(List.of("30000", "30001", "30002", "30003", "30004"), ids, "steps three to seven, no worn column");
		assertEquals(1, named(page1, "ladder-back").size());
		assertEquals(1, named(page1, "ladder-more").size(), "two steps remain");
		click(named(page1, "ladder-back").get(0), MouseEvent.BUTTON1);
		assertEquals(List.of("set-param:ladderPage=0"), sent);

		JPanel page2 = cardsParams(new ArrayList<>()).ladderStrip(deepLadder(), worn(), "weapon", true, null, 2);
		assertEquals(2, named(page2, "rung:").size(), "the last two steps");
		assertTrue(named(page2, "ladder-more").isEmpty(), "the last page");
		paint(page1);
	}

	@Test
	@DisplayName("down (farthest first), worn, up (closest first) - bars rising left to right")
	void orderAndBars() {
		JPanel strip = cards(new ArrayList<>()).ladderStrip(ladder(), worn(), "weapon", true);
		List<String> ids = new ArrayList<>();
		for (JComponent cell : named(strip, "rung:")) {
			ids.add(cell.getName().substring("rung:".length()));
		}
		assertEquals(List.of("1333", "4587", "4151", "12006", "22324"), ids);
		List<JComponent> bars = named(strip, "bar:");
		assertEquals(5, bars.size());
		for (int i = 1; i < bars.size(); i++) {
			assertTrue(bars.get(i).getPreferredSize().height > bars.get(i - 1).getPreferredSize().height,
				"bars rise left to right");
		}
		paint(strip);
	}

	/** A look for the human half (no item sprites headless - empty frames). */
	private static void paint(JPanel strip) {
		try {
			java.awt.Dimension size = strip.getPreferredSize();
			strip.setSize(240, size.height);
			layoutDeep(strip);
			java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(480, size.height * 2,
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = image.createGraphics();
			g.scale(2, 2);
			strip.paint(g);
			g.dispose();
			new java.io.File("build/render-shots").mkdirs();
			javax.imageio.ImageIO.write(image, "png", new java.io.File("build/render-shots/ladder-strip.png"));
		} catch (java.io.IOException ex) {
			throw new AssertionError(ex);
		}
	}

	private static void layoutDeep(Container container) {
		container.doLayout();
		for (Component child : container.getComponents()) {
			if (child instanceof Container) {
				layoutDeep((Container) child);
			}
		}
	}

	private static void click(JComponent cell, int button) {
		int mask = button == MouseEvent.BUTTON1 ? MouseEvent.BUTTON1_DOWN_MASK : MouseEvent.BUTTON3_DOWN_MASK;
		for (MouseListener l : cell.getMouseListeners()) {
			l.mouseClicked(new MouseEvent(cell, MouseEvent.MOUSE_CLICKED, 0, mask, 1, 1, 1, false, button));
		}
	}

	private static List<String> menu(JComponent cell) {
		List<String> items = new ArrayList<>();
		if (cell.getComponentPopupMenu() != null) {
			for (Component c : cell.getComponentPopupMenu().getComponents()) {
				if (c instanceof javax.swing.JMenuItem) {
					items.add(((javax.swing.JMenuItem) c).getText());
				}
			}
		}
		return items;
	}

	@Test
	@DisplayName("an unowned step is dashed; a left click sims it for this mob - no pin; your own steps only pin from the menu")
	void unownedSteps() {
		List<String> sent = new ArrayList<>();
		JPanel strip = cards(sent).ladderStrip(ladder(), worn(), "weapon", true);
		JLabel tentacle = (JLabel) named(strip, "rung:12006").get(0);
		JLabel rapier = (JLabel) named(strip, "rung:22324").get(0);
		assertTrue(tentacle.getBorder() instanceof javax.swing.border.StrokeBorder, "unowned = dashed");
		assertFalse(rapier.getBorder() instanceof javax.swing.border.StrokeBorder, "owned = solid");

		// Andrew 2026-10-22: "we are using "pin" for this mob instead of sim
		// for this mob" - a sim alone; the optimizer wears it because it is better.
		click(tentacle, MouseEvent.BUTTON1);
		assertEquals(List.of("sim-for-mob:12006"), sent, "left click = sim for this mob");
		sent.clear();
		click(rapier, MouseEvent.BUTTON1);
		assertEquals(List.of(), sent, "an owned step has nothing to sim");
		sent.clear();
		click(tentacle, MouseEvent.BUTTON3);
		assertEquals(List.of(), sent, "a right click only opens the menu");
		assertEquals(List.of("Sim for this mob", "Sim everywhere", "How to get it (Not Enough Runes)",
			"Hide for this mob", "Hide for all mobs"), menu(tentacle));
		assertEquals(List.of("Pin for this mob", "Hide for this mob", "Hide for all mobs"), menu(rapier),
			"owned: an explicit pin, and hiding");
		assertTrue(tentacle.getToolTipText().contains("click to sim it for this mob"), tentacle.getToolTipText());

		JPanel without = cards(new ArrayList<>()).ladderStrip(ladder(), worn(), "weapon", false);
		JLabel offline = (JLabel) named(without, "rung:12006").get(0);
		assertEquals(List.of("Sim for this mob", "Sim everywhere", "Hide for this mob", "Hide for all mobs"),
			menu(offline));
		assertTrue(offline.getToolTipText().contains("Install Not Enough Runes"));
	}

	@Test
	@DisplayName("the worn column is marked and does nothing on a click")
	void wornColumn() {
		List<String> sent = new ArrayList<>();
		JPanel strip = cards(sent).ladderStrip(ladder(), worn(), "weapon", true);
		JLabel whip = (JLabel) named(strip, "rung:4151").get(0);
		click(whip, MouseEvent.BUTTON1);
		assertEquals(List.of(), sent);
		assertEquals(List.of(), menu(whip));
		assertEquals(1, named(strip, "worn-mark").size(), "a 'you' marker under the worn column");
	}

	@Test
	@DisplayName("titled '<Slot> swaps' - no player-facing text says 'ladder'")
	void naming() {
		JPanel strip = cards(new ArrayList<>()).ladderStrip(ladder(), worn(), "weapon", true);
		List<String> texts = new ArrayList<>();
		collectText(strip, texts);
		assertTrue(texts.contains("Weapon swaps"), texts.toString());
		for (String text : texts) {
			assertFalse(text.toLowerCase().contains("ladder"), text);
		}
	}

	private static void collectText(Container root, List<String> out) {
		for (Component c : root.getComponents()) {
			if (c instanceof JLabel && ((JLabel) c).getText() != null) {
				out.add(((JLabel) c).getText());
			}
			if (c instanceof JComponent && ((JComponent) c).getToolTipText() != null) {
				out.add(((JComponent) c).getToolTipText());
			}
			if (c instanceof Container) {
				collectText((Container) c, out);
			}
		}
	}

	@Test
	@DisplayName("Balanced steps print both numbers; an equal-result step prints '='")
	void secondLineAndSame() {
		Map<String, Object> ladder = ladder();
		Map<String, Object> tentacle = new LinkedHashMap<>(rung(12006, "Abyssal tentacle", false, 0.21, "+0.21 DPS"));
		tentacle.put("delta2", "-0.20 taken/s");
		Map<String, Object> rapier = new LinkedHashMap<>(rung(22324, "Ghrazi rapier", true, 0.0, "+0.00 DPS"));
		rapier.put("same", true);
		ladder.put("up", List.of(tentacle, rapier));
		Map<String, Object> worn = worn();
		worn.put("base2", "2.00 taken/s");
		JPanel strip = cards(new ArrayList<>()).ladderStrip(ladder, worn, "weapon", true);
		assertEquals("+0.21", ((JLabel) named(strip, "value:12006").get(0)).getText());
		assertEquals("-0.20", ((JLabel) named(strip, "value2:12006").get(0)).getText());
		assertEquals("2.00", ((JLabel) named(strip, "value2:4151").get(0)).getText(), "the worn column too");
		assertEquals("=", ((JLabel) named(strip, "value:22324").get(0)).getText());
		assertTrue(((JLabel) named(strip, "rung:22324").get(0)).getToolTipText().contains("same result"),
			"the tooltip explains the equal step");
		assertTrue(((JLabel) named(strip, "rung:12006").get(0)).getToolTipText().contains("-0.20 taken/s"));
	}

	@Test
	@DisplayName("five columns fit the side panel's card (about 190 px inside RuneLite's 225 px panel)")
	void fitsThePanel() {
		JPanel strip = cards(new ArrayList<>()).ladderStrip(ladder(), worn(), "weapon", true);
		assertTrue(strip.getPreferredSize().width <= 180, "strip is " + strip.getPreferredSize().width + " px wide");
	}

	@Test
	@DisplayName("a group strip: titled (group), a click sims for the whole group, your own items pin nothing")
	void groupStrip() {
		List<String> sent = new ArrayList<>();
		Map<String, Object> ladder = ladder();
		ladder.put("group", true);
		JPanel strip = cards(sent).ladderStrip(ladder, worn(), "body", true);
		List<String> texts = new ArrayList<>();
		collectText(strip, texts);
		assertTrue(texts.contains("Body swaps (group)"), texts.toString());
		JLabel tentacle = (JLabel) named(strip, "rung:12006").get(0);
		JLabel rapier = (JLabel) named(strip, "rung:22324").get(0);
		click(tentacle, MouseEvent.BUTTON1);
		assertEquals(List.of("sim-for-group:12006"), sent);
		assertEquals("Sim for this group", menu(tentacle).get(0));
		assertEquals(List.of("Hide for all mobs"), menu(rapier), "no pin for one member, and no one-mob hide");
		assertFalse(menu(tentacle).contains("Hide for this mob"));
	}

	/** Fires the named menu item of a step. */
	private static void choose(JComponent cell, String item) {
		for (Component c : cell.getComponentPopupMenu().getComponents()) {
			if (c instanceof javax.swing.JMenuItem && item.equals(((javax.swing.JMenuItem) c).getText())) {
				for (java.awt.event.ActionListener l : ((javax.swing.JMenuItem) c).getActionListeners()) {
					l.actionPerformed(new java.awt.event.ActionEvent(c, java.awt.event.ActionEvent.ACTION_PERFORMED, item));
				}
				return;
			}
		}
		fail("no menu item " + item);
	}

	@Test
	@DisplayName("right-click hides a step you never want suggested: for this mob, or for all mobs (an exclusion)")
	void hideStep() {
		// Andrew 2026-10-22: "stop showing this as a swap option (this
		// mob/all mobs)" - a hard-to-get weapon kept being offered.
		List<String> sent = new ArrayList<>();
		JPanel strip = cards(sent).ladderStrip(ladder(), worn(), "weapon", true);
		JLabel tentacle = (JLabel) named(strip, "rung:12006").get(0);
		choose(tentacle, "Hide for this mob");
		choose(tentacle, "Hide for all mobs");
		assertEquals(List.of("exclude-for-mob:12006", "toggle-exclusion:12006"), sent);
	}

	@Test
	@DisplayName("the strip stays centred in any card width - clipped evenly if it ever overflows - and never widens the card")
	void centredInAnyWidth() {
		// Andrew 2026-10-22 (screenshot): "still getting off-center here on
		// the UX sometimes" - the strip sat right of centre, cut on the right.
		JPanel strip = cards(new ArrayList<>()).ladderStrip(ladder(), worn(), "weapon", true);
		JPanel row = ResultCards.centered(strip);
		int stripWidth = strip.getPreferredSize().width;
		assertTrue(stripWidth <= 168, "five columns fit a narrow card: " + stripWidth);
		for (int width : new int[]{300, 190, 120}) {
			row.setSize(width, 200);
			row.doLayout();
			Insets in = row.getInsets();
			int inner = width - in.left - in.right;
			assertEquals(in.left + (inner - stripWidth) / 2, strip.getX(), "centred at width " + width);
		}
		assertTrue(row.getPreferredSize().width <= 2, "the row never asks the card for the strip's width");
	}

	@Test
	@DisplayName("the strip centres on the visible part of a card wider than its viewport")
	void centredOnTheVisibleCard() {
		// Andrew 2026-10-22: "also still seeing the ladder view off-center" -
		// the card ran wider than the side panel, so centring in the card put
		// the strip right of what he could see.
		JPanel strip = cards(new ArrayList<>()).ladderStrip(ladder(), worn(), "weapon", true);
		JPanel row = ResultCards.centered(strip);
		javax.swing.JViewport viewport = new javax.swing.JViewport();
		viewport.setView(row);
		viewport.setSize(200, 200);
		viewport.setExtentSize(new java.awt.Dimension(200, 200));
		row.setSize(320, 200);
		row.doLayout();
		int stripWidth = strip.getPreferredSize().width;
		assertEquals((200 - stripWidth) / 2, strip.getX(), "centred in the 200 px you can see, not the 320 px card");
	}

	@Test
	@DisplayName("the open slot is view state, never an undoable action")
	void viewParam() {
		assertTrue(PageState.isViewParam("ladderSlot"));
		PageState state = new PageState();
		assertEquals("", state.paramsNode().get("ladderSlot"));
		state.setParam("ladderSlot", "weapon");
		assertEquals("weapon", state.paramsNode().get("ladderSlot"));
	}
}
