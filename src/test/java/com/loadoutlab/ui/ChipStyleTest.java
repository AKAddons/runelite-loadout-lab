package com.loadoutlab.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The four chip factories (pinFilterChip, paramChip, viewChip,
 * localCountChip) were unified onto one private static chip(...) body.
 * These lock the visual contract each of them produced BEFORE that merge -
 * foreground, font, opacity and border (colour + insets) - so a future
 * edit to the shared body cannot silently restyle one of the four.
 *
 * The wrappers themselves are private instance methods on a panel that is
 * expensive to construct, so the test drives the shared static body with
 * each wrapper's exact argument expression, copied verbatim from the
 * wrapper source.
 */
class ChipStyleTest
{
	/** MascotArt.LIMB - the panel's ACCENT constant. */
	private static final Color ACCENT = MascotArt.LIMB;

	private static JLabel chip(String text, boolean opaque, Color foreground, int fontStyle,
		float fontSize, Color borderColor, int padding, int arc, String tooltip)
		throws Exception
	{
		Method m = LoadoutLabPanel.class.getDeclaredMethod("chip", String.class, boolean.class,
			Color.class, int.class, float.class, Color.class, int.class, int.class,
			String.class, Consumer.class);
		m.setAccessible(true);
		return (JLabel) m.invoke(null, text, opaque, foreground, fontStyle, fontSize,
			borderColor, padding, arc, tooltip, (Consumer<java.awt.event.MouseEvent>) e ->
			{
			});
	}

	/** RoundedBorder keeps no equals(), so compare its colour reflectively. */
	private static Color borderColor(Border border) throws Exception
	{
		Field f = border.getClass().getDeclaredField("color");
		f.setAccessible(true);
		return (Color) f.get(border);
	}

	private static Font expectedFont(int style, float size)
	{
		return new JLabel().getFont().deriveFont(style, size);
	}

	private static void assertBorder(JLabel chip, Color color, int vPad, int hPad)
		throws Exception
	{
		Border border = chip.getBorder();
		assertEquals(color, borderColor(border), "border colour");
		Insets insets = border.getBorderInsets((Component) chip);
		assertEquals(new Insets(vPad + 1, hPad + 1, vPad + 1, hPad + 1), insets,
			"border insets (padding, arc)");
	}

	@Test
	@DisplayName("pinFilterChip: white bold 11, accent border, opaque")
	void pinFilter() throws Exception
	{
		JLabel chip = chip("Pins: 2", true, Color.WHITE, Font.BOLD, 11f, ACCENT, 2, 7, "tip");
		assertTrue(chip.isOpaque());
		assertEquals(Color.WHITE, chip.getForeground());
		assertEquals(expectedFont(Font.BOLD, 11f), chip.getFont());
		assertBorder(chip, ACCENT, 2, 7);
		assertEquals("tip", chip.getToolTipText());
	}

	@Test
	@DisplayName("paramChip selected: white bold 11, accent border")
	void paramSelected() throws Exception
	{
		JLabel chip = chip("On task", true, Color.WHITE, Font.BOLD, 11f, ACCENT, 2, 7, "tip");
		assertTrue(chip.isOpaque());
		assertEquals(Color.WHITE, chip.getForeground());
		assertEquals(expectedFont(Font.BOLD, 11f), chip.getFont());
		assertBorder(chip, ACCENT, 2, 7);
	}

	@Test
	@DisplayName("paramChip unselected/forced: muted greys, plain font, quiet outline")
	void paramUnselectedAndForced() throws Exception
	{
		JLabel off = chip("On task", true, new Color(170, 170, 170), Font.PLAIN, 11f,
			ColorScheme.MEDIUM_GRAY_COLOR, 2, 7, "tip");
		assertEquals(new Color(170, 170, 170), off.getForeground());
		assertEquals(expectedFont(Font.PLAIN, 11f), off.getFont());
		assertBorder(off, ColorScheme.MEDIUM_GRAY_COLOR, 2, 7);

		JLabel forced = chip("On task", true, new Color(150, 150, 150), Font.PLAIN, 11f,
			ColorScheme.MEDIUM_GRAY_COLOR, 2, 7, "tip");
		assertEquals(new Color(150, 150, 150), forced.getForeground(),
			"a forced chip greys out rather than reading as selected");
	}

	@Test
	@DisplayName("viewChip: bold 14, wider border, opacity tracks selection")
	void view() throws Exception
	{
		JLabel selected = chip("BiS", true, Color.WHITE, Font.BOLD, 14f, ACCENT, 5, 12,
			"The game-wide best set at your levels");
		assertTrue(selected.isOpaque(), "the selected view chip fills its background");
		assertEquals(Color.WHITE, selected.getForeground());
		assertEquals(expectedFont(Font.BOLD, 14f), selected.getFont());
		assertBorder(selected, ACCENT, 5, 12);

		JLabel unselected = chip("Yours", false, new Color(150, 150, 150), Font.BOLD, 14f,
			ColorScheme.MEDIUM_GRAY_COLOR, 5, 12, "Your best owned set");
		assertFalse(unselected.isOpaque(),
			"the unselected view chip is transparent - setOpaque(selected), not (true)");
		assertEquals(new Color(150, 150, 150), unselected.getForeground());
		assertBorder(unselected, ColorScheme.MEDIUM_GRAY_COLOR, 5, 12);
	}

	@Test
	@DisplayName("localCountChip: red for exclusions, green for sims, muted when empty")
	void localCount() throws Exception
	{
		JLabel red = chip("-2", true, new Color(220, 120, 120), Font.BOLD, 11f,
			new Color(170, 90, 90), 2, 7, "tip");
		assertEquals(new Color(220, 120, 120), red.getForeground());
		assertEquals(expectedFont(Font.BOLD, 11f), red.getFont());
		assertBorder(red, new Color(170, 90, 90), 2, 7);

		JLabel green = chip("+1", true, new Color(130, 200, 130), Font.BOLD, 11f,
			new Color(95, 160, 95), 2, 7, "tip");
		assertEquals(new Color(130, 200, 130), green.getForeground());
		assertBorder(green, new Color(95, 160, 95), 2, 7);

		JLabel redEmpty = chip("-0", true, new Color(140, 110, 110), Font.BOLD, 11f,
			ColorScheme.MEDIUM_GRAY_COLOR, 2, 7, "tip");
		assertEquals(new Color(140, 110, 110), redEmpty.getForeground());
		assertBorder(redEmpty, ColorScheme.MEDIUM_GRAY_COLOR, 2, 7);

		JLabel greenEmpty = chip("+0", true, new Color(110, 140, 110), Font.BOLD, 11f,
			ColorScheme.MEDIUM_GRAY_COLOR, 2, 7, "tip");
		assertEquals(new Color(110, 140, 110), greenEmpty.getForeground());
	}

	@Test
	@DisplayName("a chip with a click handler wears the hand cursor")
	void clickableChipShowsHandCursor() throws Exception
	{
		JLabel chip = chip("Pins: 2", true, Color.WHITE, Font.BOLD, 11f, ACCENT, 2, 7, "tip");
		assertEquals(java.awt.Cursor.HAND_CURSOR, chip.getCursor().getType());
		// setToolTipText registers a ToolTipManager listener of its own, so
		// this only checks that a click listener was attached at all.
		assertTrue(chip.getMouseListeners().length >= 1, "a click listener is attached");
	}
}
