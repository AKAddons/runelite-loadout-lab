package com.loadoutlab;

import java.lang.reflect.Method;
import java.util.List;
import net.runelite.client.config.ConfigItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, 2026-09-18: "does the reset button in the panel actually reset
 * the user back to a neutral/just installed state?" The reset now unsets
 * every setting too; this pins the key list to the config interface. */
class ResetSettingsTest
{
	@Test
	@DisplayName("the reset's setting list is every @ConfigItem key except the reset tick itself")
	void settingKeysCoverTheConfig()
	{
		List<String> keys = LoadoutLabConfig.settingKeys();
		int items = 0;
		for (Method m : LoadoutLabConfig.class.getDeclaredMethods())
		{
			if (m.getAnnotation(ConfigItem.class) != null)
			{
				items++;
			}
		}
		assertEquals(items - 1, keys.size(), "one key per setting, minus the tick");
		assertFalse(keys.contains("resetCustomizations"), "the tick is set false separately");
		assertTrue(keys.contains("defaultThralls"), "defaults go back");
		assertTrue(keys.contains("showDegradableChip"), "controls go back");
		assertTrue(keys.contains("useDwmsData"), "unsectioned settings go back");
	}
}
