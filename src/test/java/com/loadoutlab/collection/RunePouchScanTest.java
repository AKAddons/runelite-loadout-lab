package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.data.SpellRunes;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Discord (Mumblerit, 2026-10-10): "when I withdraw all of a rune type
 * (e.g. blood runes) from the bank and put them into my rune pouch, the
 * loadout simulation stops seeing those runes and drops recommended
 * spells that need them (like Fire Wave with a staff)". */
class RunePouchScanTest {
	private static final int BLOOD_RUNE = 565;
	private static final int DEATH_RUNE = 560;

	/** A stand-in for the client's RUNEPOUCH_RUNE enum: type 1 = death, 2 = blood. */
	private static int typeToItem(int type) {
		return type == 1 ? DEATH_RUNE : type == 2 ? BLOOD_RUNE : 0;
	}

	@Test
	@DisplayName("filled pouch slots map to item ids with their quantities")
	void filledSlotsCount() {
		Map<Integer, Integer> items = RunePouchScan.items(
			new int[]{2, 1, 0, 0}, new int[]{1200, 300, 0, 0}, RunePouchScanTest::typeToItem);
		assertEquals(Map.of(BLOOD_RUNE, 1200, DEATH_RUNE, 300), items);
	}

	@Test
	@DisplayName("an empty slot (type 0) or a zero quantity contributes nothing")
	void emptySlotsAddNothing() {
		assertTrue(RunePouchScan.items(new int[]{0, 2}, new int[]{500, 0}, RunePouchScanTest::typeToItem).isEmpty());
	}

	@Test
	@DisplayName("blood runes only in the pouch still let Fire Wave through the premium-rune gate")
	void pouchRunesReachTheSpellGate() {
		CollectionLedger ledger = new CollectionLedger(InMemoryConfigManager.create(), new Gson());
		ledger.loadScope("std");
		ledger.update(CollectionLedger.Source.BANK, Map.of(1387, 1));
		OwnedItems bankOnly = new OwnedItems(ledger.owned(), true);
		assertFalse(SpellRunes.premiumRunesOwned("Fire Wave", bankOnly), "no blood runes anywhere yet");

		ledger.update(CollectionLedger.Source.RUNE_POUCH, RunePouchScan.items(
			new int[]{2}, new int[]{1200}, RunePouchScanTest::typeToItem));
		OwnedItems withPouch = new OwnedItems(ledger.owned(), true);
		assertEquals(1200, ledger.owned().get(BLOOD_RUNE));
		assertTrue(SpellRunes.premiumRunesOwned("Fire Wave", withPouch));
	}
}
