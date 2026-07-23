package com.loadoutlab.optimizer;

import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.RequirementProfile;
import org.junit.Assert;
import org.junit.Test;

/**
 * Death Charge (field direction 2026-07-21): 15% spec energy per killing
 * blow, one refund per 60s cast window - an extra energy stream for the
 * spec value model, plumbed panel -> request -> specDpsAdded.
 */
public class DeathChargeTest
{
	@Test
	public void deathChargeAddsOneRefundPerCastWindow()
	{
		// Long fight (>= 60s): one full 15-energy refund rides the kill.
		Assert.assertEquals(100.0 + 120.0 / 3.0,
			OptimizerService.specEnergyOverKill(120, false, 0), 1e-9);
		Assert.assertEquals(100.0 + 120.0 / 3.0 + 15.0,
			OptimizerService.specEnergyOverKill(120, false, 1), 1e-9);

		// Short fight (30s): kills come faster than the 60s cast, so the
		// per-kill refund scales to the window share (7.5 energy).
		Assert.assertEquals(100.0 + 10.0 + 7.5,
			OptimizerService.specEnergyOverKill(30, false, 1), 1e-9);

		// Lightbearer doubles regen independently of the refund.
		Assert.assertEquals(100.0 + 120.0 * 2.0 / 3.0 + 15.0,
			OptimizerService.specEnergyOverKill(120, true, 1), 1e-9);
	}

	@Test
	public void yamasRiteHalvesTheEffectiveWindow()
	{
		// Upgraded (level 2): two refunds per cast = a 30s window. A 30s
		// kill now earns the FULL 15 (vs 7.5 base); a long kill is
		// unchanged (only one kill fits the minute either way).
		Assert.assertEquals(100.0 + 10.0 + 15.0,
			OptimizerService.specEnergyOverKill(30, false, 2), 1e-9);
		Assert.assertEquals(100.0 + 5.0 + 7.5,
			OptimizerService.specEnergyOverKill(15, false, 2), 1e-9);
		Assert.assertEquals(100.0 + 120.0 / 3.0 + 15.0,
			OptimizerService.specEnergyOverKill(120, false, 2), 1e-9);
	}

	@Test
	public void theRequestCarriesTheFlagThroughItsWither()
	{
		MonsterStats dummy = new MonsterStats(1, "Test", "", 100, 100, 1, 1, null, null);
		OptimizationRequest base = new OptimizationRequest(dummy, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		Assert.assertEquals(0, base.getDeathCharge());
		OptimizationRequest on = base.withDeathCharge(2);
		Assert.assertEquals(2, on.getDeathCharge());
		Assert.assertEquals("withers never mutate the source", 0, base.getDeathCharge());
		// Any later wither must PRESERVE the level (the clone-based copy).
		Assert.assertEquals(2, on.withAntifirePotion(true).getDeathCharge());
	}
}
