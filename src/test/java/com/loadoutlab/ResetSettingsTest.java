package com.loadoutlab;

import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Andrew, 2026-09-18: "does the reset button in the panel actually reset
 * the user back to a neutral/just installed state?" The reset unsets every
 * setting. Hub review of 0.5.2 (Psychemaster, 2026-09-29): "Reflection is
 * not allowed" - the key list now comes from RuneLite's own config
 * descriptor, never from walking the interface ourselves. */
class ResetSettingsTest
{
	@Test
	@DisplayName("the reset unsets every setting the config descriptor lists, in our group")
	void unsetsEverySetting()
	{
		ConfigManager manager = mock(ConfigManager.class);
		LoadoutLabConfig config = new LoadoutLabConfig()
		{
		};
		when(manager.getConfigDescriptor(config)).thenCallRealMethod();

		LoadoutLabConfig.unsetAll(manager, config);

		verify(manager).unsetConfiguration("loadoutlab", "defaultThralls");
		verify(manager).unsetConfiguration("loadoutlab", "showDegradableChip");
		verify(manager).unsetConfiguration("loadoutlab", "useDwmsData");
		verify(manager).unsetConfiguration("loadoutlab", "resetCustomizations");
		verify(manager, atLeast(10)).unsetConfiguration(eq("loadoutlab"), anyString());
	}
}
