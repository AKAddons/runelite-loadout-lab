package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The Lightbearer's stat line is empty, so it can never win the ring
 * slot on dps - its value is doubled spec regen. The arbitration prices
 * both ring candidates on the TOTAL (set dps + spec dps-added) and the
 * argmax wins (field report 2026-08-08: "it can be really impactful in
 * fights with big spec utility").
 */
public class LightbearerArbitrationTest {
	private static Tables data;

	@BeforeClass
	public static void load() {
		data = new DataService().load();
	}

	private static Gear byName(String lname) {
		for (Gear g : data.getGearItems()) {
			// The charged version: an uncharged one is its variant now
			// (ISS-01M5QQV2TGQ5X730T0JJ75S1RD) and no pin can wear it.
			if (g.getLname().equals(lname) && g.isStandardGear() && !data.isVariant(g.getId())) {
				return g;
			}
		}
		throw new AssertionError("corpus is missing: " + lname);
	}

	private static Service.Pick melee(Monster monster,
		Map<Integer, Integer> owned) throws Exception {
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, monster,
				Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(owned, true), 1,
				false, false, "", new java.util.EnumMap<>(Style.class), -1,
				com.ll.engine.Req.DEFAULT_RISK_BUDGET_GP,
				false, false, Collections.emptySet(), 0,
				Collections.emptyMap(), null, Collections.emptySet(),
				results -> {
					out.set(results);
					done.countDown();
				});
			Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
			return out.get().get(Style.MELEE);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void aPinnedSpecWeaponOverridesTheAutoPick() throws Exception {
		// Field request 2026-08-09: "i'm unable to pin to the spec slot."
		// A pinned melee spec forces the melee card to it, even when the
		// model would pick something else - and never starves the ranged
		// card of its own free spec (the pin serves only melee here).
		// The pin forces the spec on EVERY style card, not just its own
		// (field report 2026-08-09: a pinned RANGED Tonalztics ignored on
		// the melee card read as "the pin doesn't take") - specs compete
		// cross-style, so the ranged pin must ride the MELEE card too. This
		// is the user's exact scenario (a simmed Tonalztics of ralos).
		Monster rex = data.searchMonsters("dagannoth rex", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(byName("abyssal whip").getId(), 1);
		int tonalId = byName("tonalztics of ralos").getId();
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, rex,
				Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(owned, true), 1,
				false, false, "", new java.util.EnumMap<>(Style.class), -1,
				com.ll.engine.Req.DEFAULT_RISK_BUDGET_GP,
				false, false, Collections.singleton(tonalId), 0,
				Collections.emptyMap(), null, tonalId, Collections.emptySet(),
				results -> { out.set(results); done.countDown(); });
			Assert.assertTrue("timed out", done.await(120, TimeUnit.SECONDS));
			Service.Pick melee = out.get().get(Style.MELEE);
			Assert.assertNotNull("the melee card must carry the cross-style pin",
				melee.specWeapon);
			Assert.assertEquals("the pinned Tonalztics rides the melee spec slot too",
				tonalId, melee.specWeapon.getId());
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void aPinnedSpecShowsEvenWhenItAddsNothing() throws Exception {
		// Field report 2026-08-09: pinning the Tonalztics on a set it does
		// not help showed NOTHING for the spec ("it would be great if that
		// was more clear"). A pin is the player's explicit choice - it must
		// display with its honest number, even ~0.00, not vanish. A goblin
		// dies before any spec fires, so a pinned DDS adds ~0 here.
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (String n : new String[]{"abyssal whip", "dragon dagger"}) {
			owned.put(byName(n).getId(), 1);
		}
		int daggerId = byName("dragon dagger").getId();
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, goblin,
				Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(owned, true), 1,
				false, false, "", new java.util.EnumMap<>(Style.class), -1,
				com.ll.engine.Req.DEFAULT_RISK_BUDGET_GP,
				false, false, Collections.emptySet(), 0,
				Collections.emptyMap(), null, daggerId, Collections.emptySet(),
				results -> { out.set(results); done.countDown(); });
			Assert.assertTrue("timed out", done.await(120, TimeUnit.SECONDS));
			Service.Pick melee = out.get().get(Style.MELEE);
			Assert.assertNotNull("the pinned spec must still show", melee.specWeapon);
			Assert.assertEquals(daggerId, melee.specWeapon.getId());
			Assert.assertTrue("its honest ~0 value is displayed, not hidden",
				melee.sda >= 0);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void lightbearerWinsALongFightWhenTheRingCostsNoDps() throws Exception {
		// Ring of dueling and the Lightbearer are both stat-less: equal set
		// dps, so the doubled regen's extra claws are pure profit and the
		// Lightbearer MUST take the slot at a long fight.
		Monster nex = data.searchMonsters("nex", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (String n : new String[]{"abyssal whip", "dragon claws",
			"ring of dueling", "lightbearer"}) {
			owned.put(byName(n).getId(), 1);
		}
		Service.Pick melee = melee(nex, owned);
		Assert.assertNotNull(melee);
		Gear ring = melee.owned.get(0).getLoadout().get(com.ll.data.Slot.RING);
		Assert.assertNotNull("a ring must be worn", ring);
		Assert.assertTrue("doubled regen with no dps cost must take the slot, was: "
			+ ring.label(), ring.getLname().contains("lightbearer"));
		Assert.assertNotNull("the claws still ride as the spec", melee.specWeapon);
	}

	@Test
	public void aDpsRingKeepsTheSlotWhenSpecValueIsTiny() throws Exception {
		// A goblin dies before a second spec fires - the Berserker ring's
		// strength is worth more than regen nobody uses.
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (String n : new String[]{"abyssal whip", "dragon claws",
			"berserker ring (i)", "lightbearer"}) {
			owned.put(byName(n).getId(), 1);
		}
		Service.Pick melee = melee(goblin, owned);
		Assert.assertNotNull(melee);
		Gear ring = melee.owned.get(0).getLoadout().get(com.ll.data.Slot.RING);
		Assert.assertNotNull(ring);
		Assert.assertTrue("no spec value, no swap - the dps ring stands, was: "
			+ ring.label(), ring.getLname().contains("berserker"));
	}

	@Test
	public void flooredBossesYieldNoDrainValue() throws Exception {
		// The same high-defence dummy twice: once synthetic (unfloored),
		// once wearing Verzik's id - whose defence_floors.json floor is
		// its own base, so the drain block must contribute NOTHING and
		// the DWH prices at damage mode alone (competitor audit
		// 2026-08-08: without the clamp, drain fishing overvalued drain
		// specs at floored bosses).
		com.ll.data.MonsterDefences def =
			new com.ll.data.MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60);
		com.ll.data.Monster unfloored = new com.ll.data.Monster(
			-42, "Drain Dummy", "", 500, 800, 3, 350, 200, 0,
			def, null, java.util.Collections.emptyList(), false, "", 0);
		com.ll.data.Monster floored = new com.ll.data.Monster(
			8372, "Verzik Dummy", "", 500, 800, 3, 350, 200, 0,
			def, null, java.util.Collections.emptyList(), false, "", 0);

		Service service = new Service(data);
		try {
			double[] added = new double[2];
			com.ll.data.Monster[] dummies = {unfloored, floored};
			for (int i = 0; i < 2; i++) {
				com.ll.engine.Req request =
					com.ll.engine.TestRequests.of(dummies[i],
						Style.MELEE, Levels.MAXED,
						com.ll.engine.PrayerBonuses.bestAvailable(Levels.MAXED),
						null, 0, com.ll.engine.CandidateMode.ALL_STANDARD,
						true, false, Owned.EMPTY, 1);
				java.util.EnumMap<com.ll.data.Slot, Gear> worn =
					new java.util.EnumMap<>(com.ll.data.Slot.class);
				worn.put(com.ll.data.Slot.WEAPON, byName("abyssal whip"));
				com.ll.engine.Dps main = new com.ll.engine.Calc()
					.calculate(request, new com.ll.engine.Kit(worn));
				java.util.EnumMap<com.ll.data.Slot, Gear> specWorn =
					new java.util.EnumMap<>(com.ll.data.Slot.class);
				specWorn.put(com.ll.data.Slot.WEAPON, byName("dragon warhammer"));
				com.ll.engine.Dps specBase = new com.ll.engine.Calc()
					.calculate(request, new com.ll.engine.Kit(specWorn));
				com.ll.engine.SpecialAttack dwh =
					com.ll.engine.SpecialAttack.match(byName("dragon warhammer"));
				double expected = dwh.expectedDamage(specBase, dummies[i], Levels.MAXED);
				added[i] = service.sda(new com.ll.engine.Calc(),
					dwh, specBase, expected, request, main, false);
			}
			Assert.assertTrue("the floor must strip the drain value: unfloored "
				+ added[0] + " vs floored " + added[1], added[0] > added[1] + 1e-9);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void doubledRegenRaisesDrainValueWhenTheBudgetLimitsFishing() throws Exception {
		// A synthetic high-defence dummy: the DWH land chance is low, so
		// P(landed) = 1-(1-p)^attempts is budget-bound - exactly where the
		// Lightbearer's extra specs buy real drain probability. v1 priced
		// ONE attempt, making drain value blind to regen.
		com.ll.data.Monster dummy = new com.ll.data.Monster(
			-42, "Drain Dummy", "", 500, 800, 3, 350, 200, 0,
			new com.ll.data.MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60),
			null, java.util.Collections.emptyList(), false, "", 0);
		com.ll.engine.Req request =
			com.ll.engine.TestRequests.of(dummy,
				Style.MELEE, Levels.MAXED,
				com.ll.engine.PrayerBonuses.bestAvailable(Levels.MAXED),
				null, 0, com.ll.engine.CandidateMode.ALL_STANDARD,
				true, false, Owned.EMPTY, 1);

		java.util.EnumMap<com.ll.data.Slot, Gear> worn =
			new java.util.EnumMap<>(com.ll.data.Slot.class);
		worn.put(com.ll.data.Slot.WEAPON, byName("abyssal whip"));
		com.ll.engine.Dps main = new com.ll.engine.Calc()
			.calculate(request, new com.ll.engine.Kit(worn));
		Assert.assertNotNull(main);

		java.util.EnumMap<com.ll.data.Slot, Gear> specWorn =
			new java.util.EnumMap<>(com.ll.data.Slot.class);
		specWorn.put(com.ll.data.Slot.WEAPON, byName("dragon warhammer"));
		com.ll.engine.Dps specBase = new com.ll.engine.Calc()
			.calculate(request, new com.ll.engine.Kit(specWorn));
		Assert.assertNotNull(specBase);
		com.ll.engine.SpecialAttack dwh =
			com.ll.engine.SpecialAttack.match(byName("dragon warhammer"));
		Assert.assertNotNull(dwh);
		double expected = dwh.expectedDamage(specBase, dummy, Levels.MAXED);

		Service service = new Service(data);
		try {
			double without = service.sda(new com.ll.engine.Calc(),
				dwh, specBase, expected, request, main, false);
			double with = service.sda(new com.ll.engine.Calc(),
				dwh, specBase, expected, request, main, true);
			Assert.assertTrue("more attempts must buy drain probability: "
				+ without + " -> " + with, with > without + 1e-9);
		} finally {
			service.shutdown();
		}
	}
}
