package com.loadoutlab.ui;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import net.runelite.client.ui.PluginPanel;

/**
 * The one-surface ruling (ADR-0008): Core owns the single sidebar
 * panel; when the Companion has registered a renderer, its content
 * mounts HERE - same icon, same slot, prettier inside. EDT only.
 */
public class CompanionHost extends PluginPanel
{
	private JComponent content;

	public CompanionHost()
	{
		setLayout(new BorderLayout());
	}

	public void mount(JComponent component)
	{
		if (content == component)
		{
			return;
		}
		removeAll();
		content = component;
		if (component != null)
		{
			add(component, BorderLayout.NORTH);
		}
		revalidate();
		repaint();
	}
}
