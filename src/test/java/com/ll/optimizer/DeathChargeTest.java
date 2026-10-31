package com.ll.optimizer;

import com.ll.data.Monster;
import com.ll.engine.CandidateMode;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.PrayerBonuses;
import com.ll.engine.RequirementProfile;
import org.junit.Assert;
import org.junit.Test;

/**
 * Death Charge (field direction 2026-07-21): 15% spec energy per killing
 * blow, one refund per 60s cast window - an extra energy stream for the
 * spec value model, plumbed panel -> request -> sda.
 */
public class DeathChargeTest {
	@Test
	public void deathChargeAddsOneRefundPerCastWindow() {
		// SUSTAINED pricing (field direction 2026-08-08): the budget is
		// regen over the kill - no opening bar; a 50-200 mob grind is
		// regen-bound and the bar amortises to nothing.
		// Long fight (>= 60s): one full 15-energy refund rides the kill.
		Assert.assertEquals(120.0 / 3.0,
			Service.specEnergyOverKill(120, false, 0, false), 1e-9);
		Assert.assertEquals(120.0 / 3.0 + 15.0,
			Service.specEnergyOverKill(120, false, 1, false), 1e-9);

		// Short fight (30s): kills come faster than the 60s cast, so the
		// per-kill refund scales to the window share (7.5 energy).
		Assert.assertEquals(10.0 + 7.5,
			Service.specEnergyOverKill(30, false, 1, false), 1e-9);

		// Lightbearer doubles regen independently of the refund - at
		// sustained pace this is the whole point: double regen is double
		// spec throughput, every kill, not one extra squeeze.
		Assert.assertEquals(120.0 * 2.0 / 3.0 + 15.0,
			Service.specEnergyOverKill(120, true, 1, false), 1e-9);
	}

	@Test
	public void yamasRiteHalvesTheEffectiveWindow() {
		// Upgraded (level 2): two refunds per cast = a 30s window. A 30s
		// kill now earns the FULL 15 (vs 7.5 base); a long kill is
		// unchanged (only one kill fits the minute either way).
		Assert.assertEquals(10.0 + 15.0,
			Service.specEnergyOverKill(30, false, 2, false), 1e-9);
		Assert.assertEquals(5.0 + 7.5,
			Service.specEnergyOverKill(15, false, 2, false), 1e-9);
		Assert.assertEquals(120.0 / 3.0 + 15.0,
			Service.specEnergyOverKill(120, false, 2, false), 1e-9);
	}

	@Test
	public void theRequestCarriesTheFlagThroughItsWither() {
		Monster dummy = new Monster(1, "Test", "", 100, 100, 1, 1, null, null);
		Req base = new Req(dummy, Style.MELEE,
			Levels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		Assert.assertEquals(0, base.getDeathCharge());
		Req on = base.withDeathCharge(2);
		Assert.assertEquals(2, on.getDeathCharge());
		Assert.assertEquals("withers never mutate the source", 0, base.getDeathCharge());
		// Any later wither must PRESERVE the level (the clone-based copy).
		Assert.assertEquals(2, on.withAntifirePotion(true).getDeathCharge());
	}
}
