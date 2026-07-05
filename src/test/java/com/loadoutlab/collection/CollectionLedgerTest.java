package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionLedgerTest
{
	private ConfigManager configManager;
	private CollectionLedger ledger;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		ledger = new CollectionLedger(configManager, new Gson());
		ledger.loadScope("std");
	}

	@Test
	@DisplayName("the merged view sums quantities across equipment, inventory, and bank")
	void mergeSumsAcrossSources()
	{
		ledger.update(CollectionLedger.Source.BANK, Map.of(4151, 1, 861, 2));
		ledger.update(CollectionLedger.Source.INVENTORY, Map.of(861, 1));
		ledger.update(CollectionLedger.Source.EQUIPMENT, Map.of(11832, 1));

		Map<Integer, Integer> owned = ledger.owned();
		assertEquals(1, owned.get(4151));
		assertEquals(3, owned.get(861));
		assertEquals(1, owned.get(11832));
	}

	@Test
	@DisplayName("the ledger survives a new session - bank from LAST visit still counts")
	void persistsAcrossInstances()
	{
		ledger.update(CollectionLedger.Source.BANK, Map.of(20997, 1));

		// A fresh instance on the same backing store stands in for the next
		// session: no bank visit yet, but ownership is already known.
		CollectionLedger next = new CollectionLedger(configManager, new Gson());
		next.loadScope("std");
		assertEquals(1, next.owned().get(20997));
		assertTrue(next.bankKnown());
	}

	@Test
	@DisplayName("a seasonal-world login never touches the standard ledger")
	void worldScopesAreIsolated()
	{
		ledger.update(CollectionLedger.Source.BANK, Map.of(4151, 1));

		ledger.loadScope("seasonal");
		assertTrue(ledger.owned().isEmpty(), "seasonal scope starts empty");
		ledger.update(CollectionLedger.Source.BANK, Map.of(995, 1_000_000));

		ledger.loadScope("std");
		assertEquals(1, ledger.owned().get(4151));
		assertNull(ledger.owned().get(995), "leagues gold must not leak into the main ledger");
	}

	@Test
	@DisplayName("an unchanged snapshot neither rewrites config nor changes the fingerprint")
	void unchangedSnapshotIsANoOp()
	{
		assertTrue(ledger.update(CollectionLedger.Source.INVENTORY, Map.of(4151, 1)));
		int fp = ledger.fingerprint();

		clearInvocations(configManager);
		assertFalse(ledger.update(CollectionLedger.Source.INVENTORY, Map.of(4151, 1)));
		assertEquals(fp, ledger.fingerprint());
		verify(configManager, never()).setConfiguration(anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("the fingerprint changes when ownership actually changes")
	void fingerprintTracksOwnership()
	{
		ledger.update(CollectionLedger.Source.BANK, Map.of(4151, 1));
		int before = ledger.fingerprint();
		ledger.update(CollectionLedger.Source.BANK, Map.of(4151, 1, 11802, 1));
		assertNotEquals(before, ledger.fingerprint());
	}

	@Test
	@DisplayName("a corrupt persisted entry starts that snapshot fresh instead of failing")
	void corruptEntryDegradesGracefully()
	{
		configManager.setConfiguration(CollectionLedger.CONFIG_GROUP, "std.collection.bank", "{not json!");
		CollectionLedger fresh = new CollectionLedger(configManager, new Gson());
		fresh.loadScope("std");
		assertTrue(fresh.owned().isEmpty());
		assertFalse(fresh.bankKnown());
	}
}
