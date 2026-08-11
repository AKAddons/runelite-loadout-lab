package com.loadoutlab.model;

import java.util.Map;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Core's half of the Companion seam (docs/COMPANION_CONTRACT.md):
 * announces itself, publishes the render-model after every compute,
 * and replays the latest page when a UI says hello - so either plugin
 * can start first. The UI never replies to core-hello (the hello
 * already republishes; a reply would ping-pong).
 */
public class CompanionLink
{
	public static final String NAMESPACE = "loadoutlab";
	public static final String UI_HELLO = "ui-hello";
	/** Companion registers its renderer: {"renderer": Function<Map,JComponent>}
	 * - a JDK type passed BY REFERENCE (same JVM); Core mounts what it
	 * returns inside Core's own panel shell (the one-surface ruling). */
	public static final String SURFACE_REGISTER = "surface-register";
	public static final String SURFACE_CLEAR = "surface-clear";
	static final String CORE_HELLO = "core-hello";
	static final String MODEL = "model";
	static final String STATUS = "status";

	private final EventBus eventBus;
	private final String coreVersion;
	private volatile Map<String, Object> lastPage;
	/** In-plugin page listener (the internal renderer) - notified on
	 * every publish alongside the PluginMessage broadcast. */
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

	/** Compute-in-flight signal: the contract's status message plus the
	 * in-plugin listener (drives the renderer's waiting state). */
	public void publishStatus(boolean computing)
	{
		if (eventBus != null)
		{
			eventBus.post(new PluginMessage(NAMESPACE, STATUS,
				Map.of("v", RenderModel.VERSION, "computing", computing)));
		}
		java.util.function.Consumer<Boolean> listener = statusListener;
		if (listener != null)
		{
			listener.accept(computing);
		}
	}

	public CompanionLink(EventBus eventBus, String coreVersion)
	{
		this.eventBus = eventBus;
		this.coreVersion = coreVersion;
	}

	/** Announce and replay - on startUp and on every ui-hello. */
	public void hello()
	{
		eventBus.post(new PluginMessage(NAMESPACE, CORE_HELLO,
			Map.of("v", RenderModel.VERSION, "coreVersion", coreVersion)));
		Map<String, Object> page = lastPage;
		if (page != null)
		{
			post(page);
		}
	}

	/** Publish an assembled page (the CommandEngine builds them). */
	public void publishPage(Map<String, Object> page)
	{
		post(page);
	}

	/** The latest published page - Core's own hosted view renders it. */
	public Map<String, Object> lastPage()
	{
		return lastPage;
	}

	private void post(Map<String, Object> page)
	{
		lastPage = page;
		if (eventBus != null)
		{
			eventBus.post(new PluginMessage(NAMESPACE, MODEL,
				Map.of("v", RenderModel.VERSION, "page", page)));
		}
		Runnable listener = pageListener;
		if (listener != null)
		{
			listener.run();
		}
	}
}
