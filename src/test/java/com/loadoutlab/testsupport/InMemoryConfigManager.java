package com.loadoutlab.testsupport;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.mockito.Mockito;

/**
 * A ConfigManager test double whose get/set/unsetConfiguration methods are
 * backed by an in-memory map - the same fake pattern proven on goal-planner:
 * real stores run against this instead of parallel fake store classes.
 */
public final class InMemoryConfigManager
{
	private InMemoryConfigManager()
	{
	}

	public static ConfigManager create()
	{
		ConfigManager mock = Mockito.mock(ConfigManager.class);
		Map<String, String> store = new HashMap<>();

		Mockito.when(mock.getConfiguration(Mockito.anyString(), Mockito.anyString()))
			.thenAnswer(inv -> store.get(inv.getArgument(0) + "." + inv.getArgument(1)));
		// Stub the STRING overload specifically: javac resolves
		// setConfiguration(group, key, someString) to (String,String,String),
		// and a stub on the (String,String,Object) overload would not match it.
		Mockito.doAnswer(inv ->
		{
			store.put(inv.getArgument(0) + "." + inv.getArgument(1), inv.getArgument(2));
			return null;
		}).when(mock).setConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
		Mockito.doAnswer(inv ->
		{
			store.remove(inv.getArgument(0) + "." + inv.getArgument(1));
			return null;
		}).when(mock).unsetConfiguration(Mockito.anyString(), Mockito.anyString());

		return mock;
	}
}
