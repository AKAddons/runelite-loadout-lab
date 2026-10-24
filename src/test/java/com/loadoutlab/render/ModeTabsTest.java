package com.loadoutlab.render;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The dev-mode defensive modes as one 3-way control - look B, picked 2026-10-21. */
class ModeTabsTest {
	private static JLabel word(JPanel tabs, String mode) {
		for (Component c : tabs.getComponents()) {
			if (mode.equals(c.getName())) {
				return (JLabel) c;
			}
		}
		throw new AssertionError("no " + mode + " word");
	}

	private static void layoutDeep(java.awt.Container container) {
		container.doLayout();
		for (Component child : container.getComponents()) {
			if (child instanceof java.awt.Container) {
				layoutDeep((java.awt.Container) child);
			}
		}
	}

	@Test
	@DisplayName("each tab says what it optimises and what the card and the swaps show; Balanced and Tanky are beta")
	void tooltips() {
		// Andrew 2026-10-22: "better descriptions to what the differences are
		// and what you'll see in regular/ladder results" - "Tab tooltips".
		JPanel tabs = RenderSurface.modeTabs("MAX_DPS", (name, args) -> { });
		String max = word(tabs, "MAX_DPS").getToolTipText();
		String balanced = word(tabs, "BALANCED").getToolTipText();
		String tanky = word(tabs, "TANKY").getToolTipText();
		for (String tip : new String[]{max, balanced, tanky}) {
			assertFalse(tip.contains("Dev prototype"), tip);
			assertTrue(tip.contains("card") && tip.contains("swaps"), "card + swaps explained: " + tip);
		}
		assertTrue(max.contains("most damage"), max);
		assertTrue(balanced.contains("beta") && balanced.contains("damage taken"), balanced);
		assertTrue(tanky.contains("beta") && tanky.contains("least damage taken"), tanky);
	}

	@Test
	@DisplayName("one rounded outline, three words, only the active one in the accent; a click sends its mode")
	void tabs() throws Exception {
		List<Map<String, Object>> sent = new ArrayList<>();
		JPanel tabs = RenderSurface.modeTabs("BALANCED", (name, args) -> sent.add(args));
		assertTrue(tabs.getBorder() instanceof RoundedBorder, "the chips' rounded outline");
		JLabel max = word(tabs, "MAX_DPS");
		JLabel balanced = word(tabs, "BALANCED");
		JLabel tanky = word(tabs, "TANKY");
		assertEquals("Max DPS", max.getText());
		assertEquals("Balanced", balanced.getText());
		assertEquals("Tanky", tanky.getText());
		assertEquals(ResultCards.ACCENT, balanced.getForeground());
		assertNotEquals(ResultCards.ACCENT, max.getForeground());
		assertNotEquals(ResultCards.ACCENT, tanky.getForeground());

		for (java.awt.event.MouseListener l : tanky.getMouseListeners()) {
			l.mouseClicked(new java.awt.event.MouseEvent(tanky, java.awt.event.MouseEvent.MOUSE_CLICKED,
				0, 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1));
			l.mouseReleased(new java.awt.event.MouseEvent(tanky, java.awt.event.MouseEvent.MOUSE_RELEASED,
				0, 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1));
		}
		assertFalse(sent.isEmpty(), "a click sends a command");
		assertEquals("TANKY", sent.get(0).get("value"));
		assertEquals(ResultCards.ACCENT, tanky.getForeground(), "the click repaints at once");
		assertNotEquals(ResultCards.ACCENT, balanced.getForeground());

		// A look for the human half: the centered mode row above a chip row,
		// as the surface stacks them.
		JPanel modeRow = RenderSurface.modeRowPanel();
		modeRow.add(RenderSurface.modeTabs("BALANCED", (name, args) -> { }));
		modeRow.setVisible(true);
		JPanel chips = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
		chips.setBackground(net.runelite.client.ui.ColorScheme.DARKER_GRAY_COLOR);
		chips.add(RenderSurface.pill("Task", false, "", () -> { }));
		chips.add(RenderSurface.pill("Thralls", true, "", () -> { }));
		chips.add(RenderSurface.pill("Spec", true, "", () -> { }));
		JPanel holder = new JPanel();
		holder.setLayout(new javax.swing.BoxLayout(holder, javax.swing.BoxLayout.Y_AXIS));
		holder.setBackground(net.runelite.client.ui.ColorScheme.DARKER_GRAY_COLOR);
		holder.add(modeRow);
		holder.add(chips);
		int height = holder.getPreferredSize().height;
		holder.setSize(240, height);
		layoutDeep(holder);
		BufferedImage image = new BufferedImage(480, height * 2, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		g.scale(2, 2);
		holder.paint(g);
		g.dispose();
		new java.io.File("build/render-shots").mkdirs();
		javax.imageio.ImageIO.write(image, "png", new java.io.File("build/render-shots/mode-tabs.png"));
	}
}
