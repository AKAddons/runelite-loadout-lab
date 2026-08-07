package com.loadoutlab.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JLabel;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Proves the SizedIcon base class refactor is pixel-for-pixel invisible.
 *
 * For each converted icon we build the current implementation (a private
 * nested class of LoadoutLabPanel, reached by reflection) and the verbatim
 * pre-refactor copy in LegacyIcons, then compare reported dimensions and every
 * painted pixel across a spread of sizes.
 */
public class SizedIconPixelTest
{
	/** Icon simple names that were moved onto the SizedIcon base. */
	private static final List<String> CONVERTED = Arrays.asList(
		"NoPrayerIcon",
		"CloseIcon",
		"DotsIcon",
		"ReloadIcon",
		"CrosshairIcon",
		"HitsplatIcon",
		"InfoIcon",
		"PlusStarIcon");

	/** Sizes spanning the range these icons are constructed with in the panel. */
	private static final int[] SIZES = {8, 9, 10, 11, 12, 13, 14, 16, 18, 20};

	private static Icon current(String name, int size) throws Exception
	{
		Class<?> cls = Class.forName("com.loadoutlab.ui.LoadoutLabPanel$" + name);
		Constructor<?> ctor = cls.getDeclaredConstructor(int.class);
		ctor.setAccessible(true);
		return (Icon) ctor.newInstance(size);
	}

	private static Icon legacy(String name, int size) throws Exception
	{
		Class<?> cls = Class.forName("com.loadoutlab.ui.LegacyIcons$Legacy" + name);
		Constructor<?> ctor = cls.getDeclaredConstructor(int.class);
		ctor.setAccessible(true);
		return (Icon) ctor.newInstance(size);
	}

	/**
	 * Renders an icon into an opaque ARGB image with a margin, so any stray
	 * drawing outside the declared bounds would also show up in the diff.
	 */
	private static int[] render(Icon icon)
	{
		int w = icon.getIconWidth() + 8;
		int h = icon.getIconHeight() + 8;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		JLabel host = new JLabel();
		host.setForeground(new Color(200, 170, 60));
		host.setEnabled(true);
		icon.paintIcon(host, g, 4, 4);
		g.dispose();
		return img.getRGB(0, 0, w, h, null, 0, w);
	}

	@Test
	public void reportsTheSameDimensionsAsBeforeTheRefactor() throws Exception
	{
		for (String name : CONVERTED)
		{
			for (int size : SIZES)
			{
				Icon now = current(name, size);
				Icon before = legacy(name, size);
				assertEquals(name + " width @" + size,
					before.getIconWidth(), now.getIconWidth());
				assertEquals(name + " height @" + size,
					before.getIconHeight(), now.getIconHeight());
				assertEquals(name + " width is size @" + size, size, now.getIconWidth());
				assertEquals(name + " height is size @" + size, size, now.getIconHeight());
			}
		}
	}

	@Test
	public void paintsIdenticalPixelsToThePreRefactorImplementation() throws Exception
	{
		for (String name : CONVERTED)
		{
			for (int size : SIZES)
			{
				int[] before = render(legacy(name, size));
				// Guard against a vacuous pass: an icon that painted nothing
				// would compare equal to anything else that painted nothing.
				assertTrue(name + " painted something @" + size,
					Arrays.stream(before).anyMatch(p -> (p >>> 24) != 0));
				assertArrayEquals(name + " pixels @" + size,
					before, render(current(name, size)));
			}
		}
	}

	/**
	 * Negative control: the comparison above is only meaningful if render()
	 * can actually distinguish two different icons. Two distinct shapes at the
	 * same size must not compare equal.
	 */
	@Test
	public void renderDistinguishesDifferentIcons() throws Exception
	{
		assertFalse("NoPrayerIcon and PlusStarIcon should not render alike",
			Arrays.equals(render(current("NoPrayerIcon", 14)),
				render(current("PlusStarIcon", 14))));
	}

	/** The base class must not be leaking antialiasing onto its subclasses. */
	@Test
	public void baseClassSetsNoRenderingHints() throws Exception
	{
		Class<?> base = Class.forName("com.loadoutlab.ui.LoadoutLabPanel$SizedIcon");
		assertEquals("SizedIcon declares no paintIcon", 0,
			Arrays.stream(base.getDeclaredMethods())
				.filter(m -> "paintIcon".equals(m.getName()))
				.count());
	}
}
