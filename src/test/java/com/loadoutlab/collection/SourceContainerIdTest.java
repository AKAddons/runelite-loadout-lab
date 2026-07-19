package com.loadoutlab.collection;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.gameval.InventoryID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Oracle for the source -> container-id table that moved onto the enum.
 *
 * <p>ORACLE is a literal transcription of the switch statement that used to
 * live in LoadoutLabPlugin.containerFor(...), so a typo in the enum table
 * shows up here rather than as silently unscanned storage.
 */
class SourceContainerIdTest
{
	private static Map<CollectionLedger.Source, Integer> oracle()
	{
		Map<CollectionLedger.Source, Integer> m = new EnumMap<>(CollectionLedger.Source.class);
		m.put(CollectionLedger.Source.EQUIPMENT, net.runelite.api.InventoryID.EQUIPMENT.getId());
		m.put(CollectionLedger.Source.INVENTORY, net.runelite.api.InventoryID.INVENTORY.getId());
		m.put(CollectionLedger.Source.BANK, net.runelite.api.InventoryID.BANK.getId());
		m.put(CollectionLedger.Source.LOOTING_BAG, InventoryID.LOOTING_BAG);
		m.put(CollectionLedger.Source.POH_COSTUMES, InventoryID.POH_COSTUMES);
		// STASH is chart-driven, never container-scanned.
		m.put(CollectionLedger.Source.STASH, -1);
		m.put(CollectionLedger.Source.CARGO_HOLD_1, InventoryID.SAILING_BOAT_1_CARGOHOLD);
		m.put(CollectionLedger.Source.CARGO_HOLD_2, InventoryID.SAILING_BOAT_2_CARGOHOLD);
		m.put(CollectionLedger.Source.CARGO_HOLD_3, InventoryID.SAILING_BOAT_3_CARGOHOLD);
		m.put(CollectionLedger.Source.CARGO_HOLD_4, InventoryID.SAILING_BOAT_4_CARGOHOLD);
		m.put(CollectionLedger.Source.CARGO_HOLD_5, InventoryID.SAILING_BOAT_5_CARGOHOLD);
		return m;
	}

	@Test
	@DisplayName("every source carries the container id the old switch returned")
	void containerIdMatchesTheOldSwitch()
	{
		Map<CollectionLedger.Source, Integer> oracle = oracle();
		assertEquals(11, CollectionLedger.Source.values().length,
			"the oracle covers exactly the sources that exist");
		assertEquals(11, oracle.size());
		for (CollectionLedger.Source s : CollectionLedger.Source.values())
		{
			assertEquals(oracle.get(s).intValue(), s.containerId(), s.name());
		}
	}

	@Test
	@DisplayName("the reverse lookup returns the storage sources the plugin used to switch on")
	void forContainerResolvesStorageSources()
	{
		assertEquals(CollectionLedger.Source.LOOTING_BAG,
			CollectionLedger.Source.forContainer(InventoryID.LOOTING_BAG));
		assertEquals(CollectionLedger.Source.POH_COSTUMES,
			CollectionLedger.Source.forContainer(InventoryID.POH_COSTUMES));
		assertEquals(CollectionLedger.Source.CARGO_HOLD_1,
			CollectionLedger.Source.forContainer(InventoryID.SAILING_BOAT_1_CARGOHOLD));
		assertEquals(CollectionLedger.Source.CARGO_HOLD_5,
			CollectionLedger.Source.forContainer(InventoryID.SAILING_BOAT_5_CARGOHOLD));
	}

	@Test
	@DisplayName("the reverse lookup rejects the STASH sentinel and unknown containers")
	void forContainerRejectsSentinelAndUnknown()
	{
		assertNull(CollectionLedger.Source.forContainer(-1));
		assertNull(CollectionLedger.Source.forContainer(Integer.MIN_VALUE));
		assertNull(CollectionLedger.Source.forContainer(Integer.MAX_VALUE));
	}

	@Test
	@DisplayName("no two sources claim the same container id")
	void containerIdsAreUnique()
	{
		Map<Integer, CollectionLedger.Source> seen = new java.util.HashMap<>();
		for (CollectionLedger.Source s : CollectionLedger.Source.values())
		{
			if (s.containerId() < 0)
			{
				continue;
			}
			CollectionLedger.Source clash = seen.put(s.containerId(), s);
			assertNull(clash, "container id collision: " + s + " vs " + clash);
		}
	}
}
