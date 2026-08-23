package com.loadoutlab.render;

import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Band-2 prototype (F-01M0RD6XPMMXF04J0NEKE8QTTB): can the result panel be
 * painted with NO RuneLite client, so layout regressions stop needing a manual
 * in-game pass? The seam is that ResultCards takes ItemManager and
 * SpriteManager by constructor, and its input is the plain map that
 * modelGolden already serializes.
 *
 * <p>This proves the mechanism only. It validates LAYOUT, not iconography -
 * a mocked ItemManager returns no sprites - and it cannot see tooltips, which
 * are assembled on hover and never enter the painted panel.
 */
class HeadlessRenderTest
{
	/** The golden page model, captured from modelGolden at v0.4.0. */
	private static Map<String, Object> fixture() throws Exception
	{
		try (java.io.InputStream in = HeadlessRenderTest.class
			.getResourceAsStream("page-fixture.json"))
		{
			assertNotNull(in, "page-fixture.json missing");
			String json = new String(in.readAllBytes(),
				java.nio.charset.StandardCharsets.UTF_8);
			return new com.google.gson.Gson().fromJson(json, Map.class);
		}
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

	@Test
	@DisplayName("the result panel paints headlessly, with no game client")
	void paintsWithoutAClient() throws Exception
	{
		JPanel panel = cards().render(fixture());
		assertNotNull(panel, "render returned no panel");

		panel.setSize(240, 900);
		panel.doLayout();
		layoutDeep(panel);

		BufferedImage image = new BufferedImage(240, 900, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		panel.paint(g);
		g.dispose();

		assertTrue(nonBlankPixels(image) > 500,
			"panel painted blank - the seam does not work headlessly");

		// The review half of the proposal: an image a human can scan,
		// instead of an in-client pass.
		java.io.File out = new java.io.File("build/render-shots/page.png");
		out.getParentFile().mkdirs();
		javax.imageio.ImageIO.write(image, "png", out);
	}

	@Test
	@DisplayName("the same model paints identically twice - safe to diff")
	void paintingIsReproducible() throws Exception
	{
		Map<String, Object> page = fixture();
		assertArrayEquals(paint(page), paint(page),
			"same model painted two different images; cannot be used as a gate");
	}

	private static byte[] paint(Map<String, Object> page)
	{
		JPanel panel = cards().render(page);
		panel.setSize(240, 900);
		panel.doLayout();
		layoutDeep(panel);
		BufferedImage image = new BufferedImage(240, 900, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		panel.paint(g);
		g.dispose();
		int[] pixels = image.getRGB(0, 0, 240, 900, null, 0, 240);
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(pixels.length * 4);
		buffer.asIntBuffer().put(pixels);
		return sha256(buffer.array());
	}

	/** Swing only lays out realized containers; walk it ourselves. */
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

	private static int nonBlankPixels(BufferedImage image)
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

	private static byte[] sha256(byte[] data)
	{
		try
		{
			return java.security.MessageDigest.getInstance("SHA-256").digest(data);
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
}
