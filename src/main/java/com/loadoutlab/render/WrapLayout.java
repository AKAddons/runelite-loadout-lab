package com.loadoutlab.render;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/** The classic chip-row layout (ported verbatim): FlowLayout that
 * actually wraps, so no control silently clips off the row's end. */
final class WrapLayout extends FlowLayout
{
	WrapLayout(int align, int hgap, int vgap)
	{
		super(align, hgap, vgap);
	}

	@Override
	public Dimension preferredLayoutSize(Container target)
	{
		synchronized (target.getTreeLock())
		{
			// A fresh card has no width yet - falling back to unlimited
			// meant one endless row, clipped after a few chips (field
			// bug: parameters "disappearing" mid-compute). Assume the
			// plugin panel's width until the real one exists.
			int targetWidth = target.getWidth() > 0 ? target.getWidth()
				: (target.getParent() != null && target.getParent().getWidth() > 0
					? target.getParent().getWidth() : 220);
			Insets insets = target.getInsets();
			int maxWidth = targetWidth - insets.left - insets.right - getHgap() * 2;
			int x = 0;
			int rowHeight = 0;
			Dimension dim = new Dimension(0, insets.top + getVgap());
			for (int i = 0; i < target.getComponentCount(); i++)
			{
				Component c = target.getComponent(i);
				if (!c.isVisible())
				{
					continue;
				}
				Dimension d = c.getPreferredSize();
				if (x == 0 || x + getHgap() + d.width <= maxWidth)
				{
					x += (x > 0 ? getHgap() : 0) + d.width;
					rowHeight = Math.max(rowHeight, d.height);
				}
				else
				{
					dim.width = Math.max(dim.width, x);
					dim.height += rowHeight + getVgap();
					x = d.width;
					rowHeight = d.height;
				}
			}
			dim.width = Math.max(dim.width, x) + insets.left + insets.right + getHgap() * 2;
			dim.height += rowHeight + getVgap() + insets.bottom;
			return dim;
		}
	}
}
