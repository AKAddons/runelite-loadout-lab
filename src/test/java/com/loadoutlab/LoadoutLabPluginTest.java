package com.loadoutlab;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LoadoutLabPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LoadoutLabPlugin.class);
		RuneLite.main(args);
	}
}
