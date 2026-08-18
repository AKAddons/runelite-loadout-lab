package com.loadoutlab.model;

import java.util.Map;

/**
 * The in-process page store between the CommandEngine and the
 * renderer: holds the latest published page and notifies the
 * listeners the plugin wires at startup. (The 2026-08 merge-back
 * retired the PluginMessage seam this class once broadcast over -
 * the renderer lives in this plugin now.)
 */
public class CompanionLink
{
	private volatile Map<String, Object> lastPage;
	private volatile Runnable pageListener;
	private volatile java.util.function.Consumer<Boolean> statusListener;

	public void setPageListener(Runnable pageListener)
	{
		this.pageListener = pageListener;
	}

	public void setStatusListener(java.util.function.Consumer<Boolean> statusListener)
	{
		this.statusListener = statusListener;
	}

	/** Compute-in-flight signal - drives the renderer's waiting state. */
	public void publishStatus(boolean computing)
	{
		java.util.function.Consumer<Boolean> listener = statusListener;
		if (listener != null)
		{
			listener.accept(computing);
		}
	}

	/** Publish an assembled page (the CommandEngine builds them). */
	public void publishPage(Map<String, Object> page)
	{
		// Identical pages do not republish (perf, 2026-08-15: post-boss
		// silent refreshes usually land the SAME answer - the deep
		// equals is pennies next to the render it prevents).
		if (page != null && page.equals(lastPage))
		{
			return;
		}
		lastPage = page;
		Runnable listener = pageListener;
		if (listener != null)
		{
			listener.run();
		}
	}

	/** The latest published page - the hosted view renders it. */
	public Map<String, Object> lastPage()
	{
		return lastPage;
	}
}
