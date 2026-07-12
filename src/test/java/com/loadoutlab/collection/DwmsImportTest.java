package com.loadoutlab.collection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DwmsImportTest
{
	@Test
	@DisplayName("a timestamped DWMS item list parses to id -> quantity")
	void parsesTimestampedItemLists()
	{
		Map<Integer, Integer> into = new HashMap<>();
		DwmsImport.parseValue("1720549342765;536x3,995x100", into);
		assertEquals(3, into.get(536));
		assertEquals(100, into.get(995));
	}

	@Test
	@DisplayName("automatic storages save without a timestamp and still parse")
	void parsesListsWithoutTimestamp()
	{
		Map<Integer, Integer> into = new HashMap<>();
		DwmsImport.parseValue("4151x1,11832x2", into);
		assertEquals(1, into.get(4151));
		assertEquals(2, into.get(11832));
	}

	@Test
	@DisplayName("deathbank-style trailing fields after the item list are ignored")
	void trailingFieldsAreIgnored()
	{
		Map<Integer, Integer> into = new HashMap<>();
		DwmsImport.parseValue("1720549342765;4151x1;false;MEDIUM", into);
		assertEquals(Map.of(4151, 1), into);
	}

	@Test
	@DisplayName("static quantity lists, junk, and non-positive entries are dropped, never guessed")
	void defensiveParsingDropsEverythingElse()
	{
		Map<Integer, Integer> into = new HashMap<>();
		DwmsImport.parseValue(null, into);
		DwmsImport.parseValue("", into);
		DwmsImport.parseValue("1720549342765;5,3,0", into); // static store: quantities only
		DwmsImport.parseValue("5", into);
		DwmsImport.parseValue("borked", into);
		DwmsImport.parseValue("12x", into);
		DwmsImport.parseValue("x3", into);
		DwmsImport.parseValue("-1x5,0x5,5x0,5x-2", into);
		DwmsImport.parseValue("999999999999999x1,1x999999999999999", into); // overflow
		assertTrue(into.isEmpty());
	}

	@Test
	@DisplayName("the same item across two storages merges by summing quantities")
	void duplicateItemsAcrossStoragesSum()
	{
		Map<Integer, Integer> into = new HashMap<>();
		DwmsImport.parseValue("1;4151x1", into);
		DwmsImport.parseValue("2;4151x1", into);
		assertEquals(2, into.get(4151));
	}

	@Test
	@DisplayName("reload aggregates the gear prefixes for the current RSProfile")
	void reloadAggregatesGearPrefixes()
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.abc");
		when(configManager.getRSProfileConfigurationKeys(eq("dudewheresmystuff"),
			eq("rsprofile.abc"), anyString())).thenReturn(List.of());
		when(configManager.getRSProfileConfigurationKeys("dudewheresmystuff",
			"rsprofile.abc", "stash.")).thenReturn(List.of("stash.28223"));
		when(configManager.getRSProfileConfigurationKeys("dudewheresmystuff",
			"rsprofile.abc", "poh.")).thenReturn(List.of("poh.armourCase"));
		when(configManager.getConfiguration("dudewheresmystuff", "rsprofile.abc",
			"stash.28223")).thenReturn("1720549342765;4151x1");
		when(configManager.getConfiguration("dudewheresmystuff", "rsprofile.abc",
			"poh.armourCase")).thenReturn("1720549342765;11832x1,11834x1");

		DwmsImport dwms = new DwmsImport(configManager);
		assertEquals(0, dwms.count(), "empty before the first reload");
		dwms.reload();

		assertEquals(3, dwms.count());
		assertEquals(1, dwms.snapshot().get(4151));
		assertEquals(1, dwms.snapshot().get(11832));

		Map<Integer, Integer> owned = new HashMap<>(Map.of(4151, 2));
		dwms.mergeInto(owned);
		assertEquals(3, owned.get(4151), "DWMS quantities add onto the ledger view");
	}

	@Test
	@DisplayName("logged out (no RSProfile) reloads to an empty snapshot")
	void loggedOutReloadsEmpty()
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		when(configManager.getRSProfileKey()).thenReturn(null);

		DwmsImport dwms = new DwmsImport(configManager);
		dwms.reload();
		assertTrue(dwms.snapshot().isEmpty());
		assertEquals(0, dwms.count());
	}
}
