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
 * <p>Writes one PNG per scenario plus a contact sheet under
 * build/render-shots/ - one image to scan instead of one in-client session per
 * scenario - and prints a per-scenario hash so a change announces WHICH panel
 * moved.
 *
 * <p>Deliberately NOT a pixel-equality gate: font rasterisation varies between
 * machines, so the hashes are a change detector on one machine, not a
 * portable assertion. What IS asserted is that every panel paints something and
 * that painting is reproducible within a run.
 *
 * <p>Scope: layout and structure only. A stubbed ItemManager yields no item
 * icons, and tooltips are assembled on hover so they never enter the panel.
 */
class HeadlessRenderTest
{
	private static final int WIDTH = 240;
	private static final java.io.File SHOTS = new java.io.File("build/render-shots");

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

	private static BufferedImage paint(Map<String, Object> page)
	{
		ResultCards cards = cards();
		// Compose exactly as the live surface does (RenderSurface:1028
		// then :1040): the roster list above, the lens-selected card
		// below. Painting only one of the two misrepresents a roster
		// page, which is the case this harness most needs to get right.
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
		panel.setSize(WIDTH, height);
		layoutDeep(panel);

		BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		panel.paint(g);
		g.dispose();
		return image;
	}

	@Test
	@DisplayName("every golden scenario paints headlessly, with no game client")
	void everyScenarioPaints() throws Exception
	{
		SHOTS.mkdirs();
		Map<String, Map<String, Object>> scenarios = scenarios();
		List<BufferedImage> sheet = new ArrayList<>();
		int index = 0;
		for (Map.Entry<String, Map<String, Object>> scenario : scenarios.entrySet())
		{
			BufferedImage image = paint(scenario.getValue());
			assertTrue(nonBlank(image) > 500,
				scenario.getKey() + " painted blank");

			String slug = scenario.getKey().replaceAll("[^A-Za-z0-9]+", "-")
				.replaceAll("(^-|-$)", "").toLowerCase();
			javax.imageio.ImageIO.write(image, "png",
				new java.io.File(SHOTS, String.format("%02d-%s.png", ++index, slug)));
			sheet.add(image);
			System.out.printf("  %-40s %5dx%-5d %s%n", scenario.getKey(),
				image.getWidth(), image.getHeight(), hash(image).substring(0, 16));
		}
		contactSheet(sheet, scenarios.keySet());
	}

	@Test
	@DisplayName("painting is reproducible, so a diff means a real change")
	void paintingIsReproducible() throws Exception
	{
		for (Map.Entry<String, Map<String, Object>> scenario : scenarios().entrySet())
		{
			assertEquals(hash(paint(scenario.getValue())), hash(paint(scenario.getValue())),
				scenario.getKey() + " painted two different images");
		}
	}

	/** One image to scan instead of one in-client session per scenario. */
	private static void contactSheet(List<BufferedImage> shots, Iterable<String> names)
		throws Exception
	{
		int gap = 8;
		int tall = 0;
		for (BufferedImage shot : shots)
		{
			tall = Math.max(tall, shot.getHeight());
		}
		BufferedImage sheet = new BufferedImage(
			shots.size() * (WIDTH + gap) + gap, tall + 24, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		g.setColor(new java.awt.Color(30, 30, 30));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setColor(java.awt.Color.LIGHT_GRAY);
		int x = gap;
		java.util.Iterator<String> label = names.iterator();
		for (BufferedImage shot : shots)
		{
			g.drawImage(shot, x, 20, null);
			g.drawString(label.hasNext() ? label.next() : "", x, 14);
			x += WIDTH + gap;
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

	private static String hash(BufferedImage image)
	{
		int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(),
			null, 0, image.getWidth());
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(pixels.length * 4);
		buffer.asIntBuffer().put(pixels);
		try
		{
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
				.digest(buffer.array());
			StringBuilder out = new StringBuilder();
			for (byte b : digest)
			{
				out.append(String.format("%02x", b));
			}
			return out.toString();
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
}
