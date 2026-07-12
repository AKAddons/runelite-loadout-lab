package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.LoadoutOptimizer;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.RequirementProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The spec-weapon pick prefers the higher poison tier on damage ties. */
class SpecPoisonTest
{
	@Test
	@DisplayName("the suggested spec dagger is the p++ when every tier is owned")
	void specPicksThePlusPlusDagger()
	{
		LoadoutData data = new DataService().load();
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		// All four dagger tiers plus a whip as the main-hand.
		OwnedItems owned = new OwnedItems(
			Map.of(4151, 1, 1215, 1, 1231, 1, 5680, 1, 5698, 1), true);
		OptimizationRequest request = new OptimizationRequest(
			goblin, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false, owned, RequirementProfile.MAXED, 1);
		List<DpsResult> base = new LoadoutOptimizer().optimize(data, request);
		assertFalse(base.isEmpty());

		OptimizerService service = new OptimizerService(data);
		try
		{
			OptimizerService.SpecPick pick = service.bestSpec(
				data, request, base, CombatStyle.MELEE, goblin, PlayerLevels.MAXED, owned);
			assertNotNull(pick, "a dagger spec should be available");
			assertEquals("dragon dagger", pick.weapon.getNameLower());
			assertEquals(3, pick.weapon.poisonTier(),
				"expected the p++ tier, got version: " + pick.weapon.getVersionLower());
		}
		finally
		{
			service.shutdown();
		}
	}
}
