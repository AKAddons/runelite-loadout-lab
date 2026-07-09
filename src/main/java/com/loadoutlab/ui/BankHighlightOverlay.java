package com.loadoutlab.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Set;
import java.util.function.Supplier;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * "Show in bank": outlines the active set's items in the bank (and the
 * bank-side inventory) so gearing up is a visual scan, not a search.
 * The id set is pre-expanded with variants (whip (or) glows when the
 * set says whip); null/empty means off.
 */
public class BankHighlightOverlay extends WidgetItemOverlay
{
	private static final Color GLOW = new Color(140, 200, 140);
	private static final Color FILL = new Color(140, 200, 140, 40);

	private final Supplier<Set<Integer>> highlighted;

	public BankHighlightOverlay(Supplier<Set<Integer>> highlighted)
	{
		this.highlighted = highlighted;
		showOnBank();
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> ids = highlighted.get();
		if (ids == null || !ids.contains(itemId))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(FILL);
		graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
		graphics.setColor(GLOW);
		graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
	}
}
