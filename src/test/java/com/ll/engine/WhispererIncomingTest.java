package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Whisperer's damage taken under standard play + leak
 * (ISS-01M5PBSZBBBAS9Y46X04MVMHZB, Andrew 2026-10-24: "i'd love the standard
 * play + leak"): the coloured volleys are prayed on cue and leak a tenth, the
 * melee binds land through prayer, tentacles / Screech / Soul Siphon stay
 * dodged, and the card's max hit is one hit (42), never the pair (84).
 */
class WhispererIncomingTest {
	@Test
	@DisplayName("the Whisperer prays on cue: the leaked projectiles and the melee binds are the damage, the specials stay dodged, the max hit reads 42")
	void standardPlayPlusLeak() {
		Tables data = new DataService().load();
		// The loader drops the "Post-quest" label: the 900-hp row is the bare fight.
		Monster whisperer = data.searchMonsters("the whisperer", 10).stream()
			.filter(m -> m.getName().equals("The Whisperer") && m.getHitpoints() == 900).findFirst()
			.orElseThrow(() -> new AssertionError("no 900-hp Whisperer in the corpus"));
		Incoming.Result result = Incoming.calculate(whisperer, new Kit(Map.of()), 99, 99, 0);

		assertEquals(Incoming.CURATED, result.coverage);
		assertNotNull(result.protectPrayer);
		assertTrue(result.protectPrayer.endsWith("on cue"), result.protectPrayer);
		assertTrue(result.tot > 0 && result.tot < result.unprayedDps,
			result.tot + " prayed vs " + result.unprayedDps + " unprayed");

		boolean volley = false, bind = false, tentacles = false;
		double counted = 0;
		for (Incoming.StyleThreat threat : result.threats) {
			if (threat.style.startsWith("ranged volley")) {
				volley = true;
				assertTrue(threat.style.contains("on cue"), threat.style);
				assertEquals(0.1, threat.prayerFactor, 1e-9, "a tenth of the volley lands");
				assertEquals(40, threat.maxHit);
			}
			if (threat.style.startsWith("melee (x2)")) {
				bind = true;
				assertEquals(42, threat.maxHit, "one hit of the pair, not 84");
				assertTrue(threat.prayerFactor > 0.1 && threat.prayerFactor < 1, "the second hit pierces: " + threat.prayerFactor);
			}
			if (threat.style.startsWith("shadow tentacles")) {
				tentacles = true;
				assertTrue(threat.style.contains("avoidable"), threat.style);
			}
			if (threat.modeled && !threat.style.contains("avoidable")) {
				counted += threat.dps * threat.share * threat.prayerFactor;
			}
		}
		assertTrue(volley && bind && tentacles, "volley " + volley + " bind " + bind + " tentacles " + tentacles);
		assertEquals(result.tot, counted, 1e-6, "the total is exactly the counted rows");
	}
}
