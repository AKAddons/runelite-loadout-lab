package com.loadoutlab;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

public class LoadoutLabPluginTest
{
	/**
	 * Hub plugins loaded alongside when their jar (and its dependency
	 * jars) sit in dev-plugins/ (gitignored): lets cross-plugin
	 * integrations (PluginMessage) be exercised end-to-end locally, e.g.
	 * against a patched Dude Where's My Stuff before its upstream PR
	 * ships. Remember: a hub-installed copy of the same plugin conflicts
	 * with the sideloaded one - disable the hub copy while testing.
	 */
	private static final String[] COMPANION_PLUGINS = {
		"dev.thource.runelite.dudewheresmystuff.DudeWheresMyStuffPlugin",
	};

	public static void main(String[] args) throws Exception
	{
		List<Class<? extends Plugin>> plugins = new ArrayList<>();
		plugins.add(LoadoutLabPlugin.class);
		for (String className : COMPANION_PLUGINS)
		{
			try
			{
				@SuppressWarnings("unchecked")
				Class<? extends Plugin> plugin = (Class<? extends Plugin>) Class.forName(className);
				plugins.add(plugin);
				System.out.println("dev client: loading companion plugin " + className);
			}
			catch (ClassNotFoundException e)
			{
				// jar not in dev-plugins/ - run without it
			}
		}
		@SuppressWarnings("unchecked")
		Class<? extends Plugin>[] arr = plugins.toArray(new Class[0]);
		ExternalPluginManager.loadBuiltin(arr);
		RuneLite.main(args);
	}
}
