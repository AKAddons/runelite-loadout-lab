package com.loadoutlab.model;

import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.List;
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
	static final String CORE_HELLO = "core-hello";
	static final String MODEL = "model";

	private final EventBus eventBus;
	private final String coreVersion;
	private volatile Map<String, Object> lastPage;

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

	public void publish(MonsterStats mob, Map<CombatStyle, OptimizerService.StyleResult> results)
	{
		post(RenderModel.page(List.of(RenderModel.entry(List.of(mob), List.of(results)))));
	}

	public void publishRoster(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob)
	{
		post(RenderModel.page(List.of(RenderModel.entry(mobs, perMob))));
	}

	private void post(Map<String, Object> page)
	{
		lastPage = page;
		eventBus.post(new PluginMessage(NAMESPACE, MODEL,
			Map.of("v", RenderModel.VERSION, "page", page)));
	}
}
