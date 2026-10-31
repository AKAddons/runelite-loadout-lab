package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * Zorya's tome special (wiki, 2026-10-20): a cast at +150% accuracy and
 * +60% damage; if it lands, the next three casts carry the same buff and
 * fire every two ticks. The energy cost is unpublished: 50% assumed (the
 * Gatekeeper's Cane proposal's cost; the blog changed the buff, not it).
 */
public class ZoryaSpecTest {
	@Test
	public void aLandedSpecBuysThreeBuffedCastsAndTheirTime() {
		LoadoutData data = new DataService().load();
		Gear tome = data.getGear(34725);
		SpecialAttack spec = SpecialAttack.match(tome, Style.MAGIC);
		Assert.assertNotNull(spec);
		Assert.assertEquals(SpecialAttack.Kind.BUFFED_FOLLOWUPS, spec.getKind());
		Assert.assertEquals(50, spec.getEnergyCost());
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(goblin, Style.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY,
			RequirementProfile.MAXED, 10);
		EnumMap<GearSlot, Gear> worn = new EnumMap<>(GearSlot.class);
		worn.put(GearSlot.WEAPON, tome);
		DpsResult base = new DpsCalculator().calculate(request, new Loadout(worn));
		double h = RollMath.normalAccuracy((long) (base.getAttackRoll() * 2.5), base.getDefenceRoll());
		double hit = h * (int) (base.getMaxHit() * 1.6) / 2.0;
		Assert.assertEquals(hit * (1 + 3 * h), spec.expectedDamage(base, goblin, PlayerLevels.MAXED), 1e-9);
		Assert.assertEquals(6 * h, spec.extraTicks(base), 1e-9);
		Assert.assertEquals(0, SpecialAttack.match(data.getGear(11802), Style.MELEE).extraTicks(base), 1e-9);
	}
}
