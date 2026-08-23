package com.loadoutlab.render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Band-2 coverage (F-01M0RD6XPMMXF04J0NEKE8QTTB): paint every result panel
 * with NO RuneLite client, so layout regressions stop needing a manual in-game
 * pass. The seam is that ResultCards takes ItemManager and SpriteManager by
 * constructor, and its input is the same map modelGolden serializes.
 *
 * <p><b>The gate is the component TREE, not the pixels.</b> A pixel hash is
 * machine-dependent - font rasterisation and metrics differ per JDK and per OS -
 * so it would either fail everywhere but here, or never fail at all. The tree
 * (nesting, order, component kinds, label and button text) is stable across
 * machines and is what actually breaks: a row vanishing, text changing, a
 * section rendering in the wrong order.
 *
 * <p>Pixels are still emitted, for the human half: per-variant PNGs plus a
 * contact sheet under build/render-shots/, one image to scan instead of one
 * in-client session per scenario.
 *
 * <p>NOT covered, deliberately: item iconography (a stubbed ItemManager yields
 * no sprites), tooltips (assembled on hover, never in the panel), and sub-pixel
 * drift (the tree cannot see a 3px move).
 */
class HeadlessRenderTest
{
	private static final String BASELINE = "structure-baseline.txt";
	private static final java.io.File SHOTS = new java.io.File("build/render-shots");

	/**
	 * The views worth rendering. {@code gated} says whether the variant also
	 * enters the structure baseline.
	 *
	 * <p>"narrow" is painted but NOT gated, because the component tree is
	 * width-invariant: at 200px it is byte-identical to 240px (verified when
	 * the baseline was first generated). Including it would triple-report
	 * every real regression while adding no independent signal. Its value is
	 * visual - truncation and overflow are pixel phenomena the tree cannot
	 * see. If width-responsive logic is ever added, flip it to gated.
	 */
	private static final Variant[] VARIANTS = {
		new Variant("yours", 240, false, true),
		new Variant("bis", 240, true, true),
		new Variant("narrow", 200, false, false),
	};

	private static final class Variant
	{
		final String name;
		final int width;
		final boolean bis;
		final boolean gated;

		Variant(String name, int width, boolean bis, boolean gated)
		{
			this.name = name;
			this.width = width;
			this.bis = bis;
			this.gated = gated;
		}
	}

	/** The modelGolden scenarios: "##### name" lines then a page json. */
	private static Map<String, Map<String, Object>> scenarios() throws Exception
	{
		Map<String, Map<String, Object>> out = new LinkedHashMap<>();
		try (java.io.InputStream in = HeadlessRenderTest.class.getResourceAsStream("pages.txt"))
		{
			assertNotNull(in, "pages.txt fixture missing");
			List<String> lines = new java.io.BufferedReader(new java.io.InputStreamReader(
				in, java.nio.charset.StandardCharsets.UTF_8)).lines()
				.collect(java.util.stream.Collectors.toList());
			String name = null;
			for (String line : lines)
			{
				if (line.startsWith("#####"))
				{
					name = line.substring(5).trim();
				}
				else if (!line.isBlank() && name != null)
				{
					out.put(name, new com.google.gson.Gson().fromJson(line, Map.class));
					name = null;
				}
			}
		}
		assertFalse(out.isEmpty(), "no scenarios parsed from the fixture");
		return out;
	}

	/** Deep stubs because the render path dereferences the image
	 * ItemManager hands back without a null guard - safe in the live
	 * client, which always yields one, but a plain mock returns null
	 * and NPEs at ResultCards.mobCard. */
	private static ResultCards cards()
	{
		return new ResultCards(
			mock(ItemManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			mock(SpriteManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
			mock(CommandSink.class), mock(ItemPicker.class));
	}

	/** The BiS side is a page parameter, not a different model. */
	@SuppressWarnings("unchecked")
	private static void selectBis(Map<String, Object> page)
	{
		for (Object entry : (List<Object>) page.get("entries"))
		{
			((Map<String, Object>) entry).put("params",
				new LinkedHashMap<>(Map.of("viewingBis", true, "lensIndex", 0.0)));
		}
	}

	/** Compose exactly as the live surface does (RenderSurface:1028 then
	 * :1040): the roster list above, the lens-selected card below. */
	private static JPanel compose(Map<String, Object> page, int width)
	{
		ResultCards cards = cards();
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setBackground(net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR);
		JPanel roster = cards.renderRoster(page);
		if (roster != null)
		{
			panel.add(roster);
		}
		JPanel card = cards.render(page);
		if (card != null && card.getComponentCount() > 0)
		{
			panel.add(card);
		}
		assertTrue(panel.getComponentCount() > 0, "nothing rendered for this page");
		int height = Math.max(120, Math.min(panel.getPreferredSize().height, 3000));
		panel.setSize(width, height);
		layoutDeep(panel);
		return panel;
	}

	/** Every (scenario, variant) rendered once: structure text, keyed. */
	private static Map<String, String> renderAll(List<BufferedImage> shots, List<String> labels)
		throws Exception
	{
		Map<String, String> structures = new LinkedHashMap<>();
		for (Variant variant : VARIANTS)
		{
			for (Map.Entry<String, Map<String, Object>> scenario : scenarios().entrySet())
			{
				Map<String, Object> page = scenario.getValue();
				if (variant.bis)
				{
					selectBis(page);
				}
				JPanel panel = compose(page, variant.width);
				String key = scenario.getKey() + " [" + variant.name + "]";
				if (variant.gated)
				{
					structures.put(key, structure(panel));
				}

				if (shots != null)
				{
					BufferedImage image = new BufferedImage(variant.width,
						panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
					java.awt.Graphics2D g = image.createGraphics();
					panel.paint(g);
					g.dispose();
					assertTrue(nonBlank(image) > 500, key + " painted blank");
					shots.add(image);
					labels.add(key);
					SHOTS.mkdirs();
					javax.imageio.ImageIO.write(image, "png", new java.io.File(SHOTS,
						String.format("%02d-%s.png", shots.size(),
							key.replaceAll("[^A-Za-z0-9]+", "-")
								.replaceAll("(^-|-$)", "").toLowerCase())));
				}
			}
		}
		return structures;
	}

	@Test
	@DisplayName("the rendered component tree matches the committed baseline")
	void treeMatchesBaseline() throws Exception
	{
		String actual = render(renderAll(null, null));
		String expected;
		try (java.io.InputStream in = HeadlessRenderTest.class.getResourceAsStream(BASELINE))
		{
			expected = in == null ? null : new String(in.readAllBytes(),
				java.nio.charset.StandardCharsets.UTF_8);
		}
		if (expected == null)
		{
			SHOTS.mkdirs();
			java.nio.file.Files.write(new java.io.File(SHOTS, BASELINE).toPath(),
				actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			fail("no baseline committed; one was generated at build/render-shots/"
				+ BASELINE + " - review it, then copy it into src/test/resources");
		}
		if (!expected.equals(actual))
		{
			SHOTS.mkdirs();
			java.nio.file.Files.write(new java.io.File(SHOTS, "structure-actual.txt").toPath(),
				actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			fail("render tree changed: " + firstDifference(expected, actual)
				+ "\n  actual written to build/render-shots/structure-actual.txt;"
				+ " check the PNGs, then re-baseline if the change was intended");
		}
	}

	@Test
	@DisplayName("every scenario and view paints, and lands in a contact sheet")
	void everyViewPaints() throws Exception
	{
		List<BufferedImage> shots = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		renderAll(shots, labels);
		assertEquals(VARIANTS.length * scenarios().size(), shots.size());
		contactSheet(shots, labels);
	}

	/** Which scenario moved, so the failure names the panel to look at. */
	private static String firstDifference(String expected, String actual)
	{
		String[] want = expected.split("\n");
		String[] got = actual.split("\n");
		String section = "?";
		for (int i = 0; i < Math.max(want.length, got.length); i++)
		{
			String w = i < want.length ? want[i] : "<end>";
			String g = i < got.length ? got[i] : "<end>";
			if (w.startsWith("== "))
			{
				section = w.substring(3);
			}
			if (!w.equals(g))
			{
				return "first change in [" + section + "] line " + (i + 1)
					+ "\n  expected: " + w.trim() + "\n  actual:   " + g.trim();
			}
		}
		return "(no line differs; whitespace only)";
	}

	private static String render(Map<String, String> structures)
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, String> e : structures.entrySet())
		{
			out.append("== ").append(e.getKey()).append('\n').append(e.getValue());
		}
		return out.toString();
	}

	/**
	 * The component tree as stable text: nesting, kind and any text. NO
	 * bounds - a JLabel's width comes from font metrics, which differ per
	 * machine, and baking that in would make this fail everywhere but the
	 * machine that wrote it.
	 */
	private static String structure(java.awt.Component component)
	{
		StringBuilder out = new StringBuilder();
		walk(component, 0, out);
		return out.toString();
	}

	private static void walk(java.awt.Component component, int depth, StringBuilder out)
	{
		for (int i = 0; i < depth; i++)
		{
			out.append("  ");
		}
		out.append(component.getClass().getSimpleName());
		String text = null;
		if (component instanceof javax.swing.JLabel)
		{
			text = ((javax.swing.JLabel) component).getText();
		}
		else if (component instanceof javax.swing.AbstractButton)
		{
			text = ((javax.swing.AbstractButton) component).getText();
		}
		if (text != null && !text.isEmpty())
		{
			out.append(" \"").append(text.replace('\n', ' ')).append('"');
		}
		// Tooltips ARE in the tree: setToolTipText runs at build time, not
		// on hover. Snapshotting them is what gives REQ-007's surface - the
		// tooltip kit - a test that can actually go red.
		if (component instanceof javax.swing.JComponent)
		{
			String hover = ((javax.swing.JComponent) component).getToolTipText();
			if (hover != null && !hover.isEmpty())
			{
				out.append(" tip=").append(hover.replace('\n', ' '));
			}
		}
		if (!component.isVisible())
		{
			out.append(" (hidden)");
		}
		out.append('\n');
		if (component instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) component).getComponents())
			{
				walk(child, depth + 1, out);
			}
		}
	}

	/** One image to scan instead of one in-client session per view. */
	private static void contactSheet(List<BufferedImage> shots, List<String> names)
		throws Exception
	{
		int gap = 8;
		int tall = 0;
		int wide = gap;
		for (BufferedImage shot : shots)
		{
			tall = Math.max(tall, shot.getHeight());
			wide += shot.getWidth() + gap;
		}
		BufferedImage sheet = new BufferedImage(wide, tall + 24, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		g.setColor(new java.awt.Color(30, 30, 30));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setColor(java.awt.Color.LIGHT_GRAY);
		int x = gap;
		for (int i = 0; i < shots.size(); i++)
		{
			g.drawImage(shots.get(i), x, 20, null);
			g.drawString(names.get(i), x, 14);
			x += shots.get(i).getWidth() + gap;
		}
		g.dispose();
		javax.imageio.ImageIO.write(sheet, "png", new java.io.File(SHOTS, "contact-sheet.png"));
	}

	/** Swing only lays out REALIZED containers; walk it ourselves. */
	private static void layoutDeep(java.awt.Container container)
	{
		container.doLayout();
		for (java.awt.Component child : container.getComponents())
		{
			if (child instanceof java.awt.Container)
			{
				layoutDeep((java.awt.Container) child);
			}
		}
	}

	private static int nonBlank(BufferedImage image)
	{
		int count = 0;
		for (int x = 0; x < image.getWidth(); x++)
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				if ((image.getRGB(x, y) >>> 24) != 0)
				{
					count++;
				}
			}
		}
		return count;
	}
}
