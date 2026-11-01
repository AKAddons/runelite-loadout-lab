package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.CandidateMode;
import com.ll.engine.Style;
import com.ll.engine.Dps;
import com.ll.engine.LoadoutOptimizer;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.PrayerBonuses;
import com.ll.engine.RequirementProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The spec-weapon pick prefers the higher poison tier on damage ties. */
class SpecPoisonTest {
	@Test
	@DisplayName("the suggested spec dagger is the p++ when every tier is owned")
	void specPicksThePlusPlusDagger() {
		Tables data = new DataService().load();
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		// All four dagger tiers plus a whip as the main-hand.
		Owned owned = new Owned(
			Map.of(4151, 1, 1215, 1, 1231, 1, 5680, 1, 5698, 1), true);
		Req request = new Req(
			goblin, Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false, owned, RequirementProfile.MAXED, 1);
		List<Dps> base = new LoadoutOptimizer().optimize(data, request);
		assertFalse(base.isEmpty());

		Service service = new Service(data);
		try {
			Service.Spec pick = service.bestSpec(data, request, base.get(0), owned, null, false);
			assertNotNull(pick, "a dagger spec should be available");
			assertEquals("dragon dagger", pick.weapon.getLname());
			assertEquals(3, pick.weapon.poisonTier(),
				"expected the p++ tier, got version: " + pick.weapon.getVer());
		} finally {
			service.shutdown();
		}
	}
}
