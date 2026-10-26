package com.loadoutlab.render;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The style strip wraps (ISS-01M5TS5CBHSDS00XQSQYJ19XW8, Andrew 2026-10-25: "i
 * don't see a hallowfeell dps option only magic, venator, chins"): four 64 px
 * tabs are wider than the side panel, and a plain FlowLayout lays the last one
 * out past the edge.
 */
class StyleStripWrapTest {
	private static final int PANEL = 225;

	private static JPanel strip(int tabs) {
		JPanel strip = ResultCards.styleStrip();
		for (int i = 0; i < tabs; i++) {
			JPanel cell = new JPanel();
			cell.setPreferredSize(new Dimension(64, 26));
			strip.add(cell);
		}
		strip.setSize(PANEL, 200);
		strip.doLayout();
		return strip;
	}

	@Test
	@DisplayName("four tabs at the side panel's width all sit inside the strip")
	void fourTabsFit() {
		for (Component cell : strip(4).getComponents()) {
			assertTrue(cell.getX() + cell.getWidth() <= PANEL, "a tab at x=" + cell.getX() + " runs past " + PANEL);
		}
	}

	@Test
	@DisplayName("three tabs still share one row")
	void threeTabsOneRow() {
		Component[] cells = strip(3).getComponents();
		for (Component cell : cells) { assertEquals(cells[0].getY(), cell.getY()); }
	}
}
