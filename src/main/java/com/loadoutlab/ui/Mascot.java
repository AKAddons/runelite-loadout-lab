package com.loadoutlab.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Base for the mascot loading animations. Holds the one 30fps timer that
 * runs ONLY while the component is showing (started in addNotify, stopped
 * in removeNotify - so a loader that scrolls off or is replaced by results
 * costs nothing), a fixed sidebar-friendly canvas shared by every mood so
 * the loading area does not resize as they swap, and a single
 * render(g2, t, w, h) hook each mood fills in.
 *
 * Elapsed seconds since the animation (re)started is passed to render. A
 * mood may keep per-run state in fields (the workout's slosh spring) and
 * reset it in onStart; a stateless mood ignores that and is a pure
 * function of t, which lets the preview harness reproduce exact frames.
 */
abstract class Mascot extends JComponent
{
	/** One canvas for every mood - wide enough for the chef's pot and the
	 * striker's globe; narrower moods just centre in it. */
	static final Dimension SIZE = new Dimension(16 * MascotArt.SCALE + 96, 16 * MascotArt.SCALE + 32);

	private final Timer timer = new Timer(33, e -> repaint());
	private long startedAt;

	Mascot()
	{
		setPreferredSize(SIZE);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, SIZE.height));
		setAlignmentX(LEFT_ALIGNMENT);
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		startedAt = System.currentTimeMillis();
		onStart();
		timer.start();
	}

	@Override
	public void removeNotify()
	{
		timer.stop();
		super.removeNotify();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if (!MascotArt.available())
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		render(g2, (System.currentTimeMillis() - startedAt) / 1000.0, getWidth(), getHeight());
		g2.dispose();
	}

	/** Reset per-run state when the animation (re)starts. Default no-op. */
	protected void onStart()
	{
	}

	/** Draw one frame at elapsed time t (seconds) on a w x h canvas. */
	protected abstract void render(Graphics2D g2, double t, int w, int h);
}
