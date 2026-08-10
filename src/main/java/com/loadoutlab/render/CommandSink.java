package com.loadoutlab.render;

import java.util.Map;

/** Routes a contract command {name, args} to the CommandEngine -
 * directly in-plugin today; over PluginMessage if the renderer ever
 * lifts out into the parked Companion plugin (ADR-0008 Path C). */
public interface CommandSink
{
	void send(String name, Map<String, Object> args);
}
