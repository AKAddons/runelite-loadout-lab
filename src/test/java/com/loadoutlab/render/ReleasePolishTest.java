package com.loadoutlab.render;

import java.awt.Dimension;
import javax.swing.JComboBox;
import javax.swing.plaf.basic.ComboPopup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pre-release polish (ISS-01M60VXAXCQF8AVDBDHK8VYPYD, Andrew 2026-10-28:
 * "click for it to plan it, with any task picked can just be elimited
 * complely", "the stat dropdown for the requirements which has the icons +
 * text gets cut off on the text").
 */
class ReleasePolishTest {
	@Test
	@DisplayName("a combat achievement chip says click to plan for it - no 'with any other task picked'")
	void caChipTip() {
		assertEquals("Click to plan for it.", Ui.text("ca.pick"));
		assertEquals("Click to drop the task.", Ui.text("ca.drop"));
	}

	@Test
	@DisplayName("the requirement stat list opens as wide as its longest name; the closed box stays narrow")
	void statListWidth() {
		JComboBox<String> box = Ui.wideCombo(RenderSurface.REQ_NAMES);
		box.setPrototypeDisplayValue("Stab");
		Dimension closed = box.getPreferredSize();
		box.setSize(closed);
		box.doLayout();
		int list = ((ComboPopup) box.getUI().getAccessibleChild(box, 0)).getList().getPreferredSize().width;
		assertTrue(list > closed.width, "the names are wider than the closed box: " + list + " vs " + closed.width);
		assertTrue(box.getSize().width >= list, "the popup is sized to the list: " + box.getSize().width + " vs " + list);
		assertEquals(closed.width, box.getWidth(), "the closed box keeps its own width");
	}
}
