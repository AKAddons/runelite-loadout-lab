package com.loadoutlab.data;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A teleport keeps the cached result (ISS-01M5V7W28574DBWHTR5ATE37Q8, Andrew 2026-10-25: "when i
 * teleport it should stay the same and should be able to fully rely on the
 * cache"): a jewellery charge is a new item id, and the cache key must not
 * see it.
 */
class ChargeFreeFingerprintTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static int print(int... idQty) {
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(4151 + 1000000, 0);
		for (int i = 0; i < idQty.length; i += 2) bank.put(idQty[i], idQty[i + 1]);
		return data.chargeFreeFingerprint(bank);
	}

	@Test
	@DisplayName("a teleport's charge does not change the key: games necklace (8) -> (7), glory (4) -> (3), dueling (8) -> (2)")
	void chargesFold() {
		assertEquals(print(3853, 1, 554, 100), print(3855, 1, 554, 100), "games necklace (8) vs (7)");
		assertEquals(print(1712, 1), print(1710, 1), "glory (4) vs (3)");
		assertEquals(print(2552, 1), print(2564, 1), "ring of dueling (8) vs (2)");
	}

	@Test
	@DisplayName("real differences still change it: charged vs uncharged trident, an added whip; a rune count does not")
	void realChangesCount() {
		assertNotEquals(print(11907, 1), print(11908, 1), "trident charged vs uncharged");
		assertNotEquals(print(3853, 1), print(3853, 1, 4151, 1), "a whip joins the bank");
		assertEquals(print(554, 100), print(554, 99), "fewer fire runes, same presence");
	}
}
