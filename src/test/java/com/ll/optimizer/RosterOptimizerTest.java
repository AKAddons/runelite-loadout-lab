package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Dps;
import com.ll.engine.Kit;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

/**
 * The roster optimizer (M-2 multi-mob core): one shared set per style,
 * chosen for the best AVERAGE dps across the mobs, with per-mob display
 * numbers. A single-mob roster must reduce to the exact bestPerStyle path.
 */
public class RosterOptimizerTest {
	private static Map<Integer, Integer> ownedMelee() {
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(1215, 1);   // dragon dagger (spec)
		owned.put(11840, 1);  // dragon boots
		owned.put(6737, 1);   // berserker ring
		// Premium runes: the rune-ownership gate (0.4.1) would otherwise
		// drop the blood/death spells and shift the arithmetic this test
		// pins - rune poverty has its own net (SpellRuneWiringTest).
		owned.put(565, 1000);   // blood
		owned.put(560, 1000);   // death
		owned.put(566, 1000);   // soul
		owned.put(21880, 1000); // wrath
		return owned;
	}

	private static RosterResultView run(Service service, List<Monster> mobs,
		Map<Integer, Integer> owned) throws Exception {
		return run(service, mobs, owned, 1);
	}

	private static RosterResultView run(Service service, List<Monster> mobs,
		Map<Integer, Integer> owned, int maxSwaps) throws Exception {
		return run(service, mobs, owned, maxSwaps, Collections.emptyMap());
	}

	private static RosterResultView run(Service service, List<Monster> mobs,
		Map<Integer, Integer> owned, int maxSwaps,
		Map<Integer, Map<Style, java.util.Set<Integer>>> excludedByMob) throws Exception {
		return new RosterResultView(
			ServiceCalls.runRoster(service, mobs, owned, maxSwaps, excludedByMob));
	}

	/** The worn item ids of a set, sorted - a set-identity fingerprint. */
	private static List<Integer> setIds(Kit loadout) {
		List<Integer> ids = new ArrayList<>();
		for (Slot slot : Slot.values()) {
			if (loadout.get(slot) != null) {
				ids.add(loadout.get(slot).getId());
			}
		}
		Collections.sort(ids);
		return ids;
	}

	@Test
	public void singleMobRosterReducesToBestPerStyle() throws Exception {
		Tables data = new DataService().load();
		Monster ankou = data.searchMonsters("ankou", 1).get(0);
		Map<Integer, Integer> owned = ownedMelee();
		Service service = new Service(data);
		try {
			// via the roster entry point (1 mob)
			RosterResultView roster = run(service, Collections.singletonList(ankou), owned);
			Assert.assertEquals(1, roster.result.pm.size());
			double rosterDps = roster.result.pm.get(0)
				.get(Style.MELEE).owned.get(0).getDps();

			// via the direct single-mob path
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			com.ll.optimizer.ServiceCalls.bestPerStyle(service, ankou, Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(owned, true), 1, false, false, "",
				Collections.emptyMap(), -1, Req.DEFAULT_RISK_BUDGET_GP, false, false,
				Collections.emptySet(), 0, 				Collections.emptyMap(), null, Collections.emptySet(),
				map -> {
					out.set(map);
					done.countDown();
				});
			Assert.assertTrue(done.await(90, TimeUnit.SECONDS));
			double directDps = out.get().get(Style.MELEE).owned.get(0).getDps();
			Assert.assertEquals("1-mob roster must equal bestPerStyle", directDps, rosterDps, 1e-9);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void twoMobRosterSharesOneSetButFlipsTheNumbers() throws Exception {
		Tables data = new DataService().load();
		// Two monsters with clearly different defence so the same set does
		// visibly different dps - the lens invariant (same set, numbers flip).
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Monster ankou = data.searchMonsters("ankou", 1).get(0);
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, Arrays.asList(goblin, ankou), ownedMelee());
			Assert.assertEquals(2, roster.result.pm.size());

			Service.Pick m0 = roster.result.pm.get(0).get(Style.MELEE);
			Service.Pick m1 = roster.result.pm.get(1).get(Style.MELEE);
			Assert.assertNotNull(m0);
			Assert.assertNotNull(m1);
			Assert.assertFalse(m0.owned.isEmpty());
			Assert.assertFalse(m1.owned.isEmpty());

			// ONE shared set: the worn items are identical across both mobs.
			Assert.assertEquals("the roster shares a single owned set",
				setIds(m0.owned.get(0).getLoadout()), setIds(m1.owned.get(0).getLoadout()));

			// The numbers flip: the same set does different dps per mob.
			double dps0 = m0.owned.get(0).getDps();
			double dps1 = m1.owned.get(0).getDps();
			Assert.assertTrue(dps0 > 0 && dps1 > 0);
			Assert.assertTrue("same set, different mobs -> different dps",
				Math.abs(dps0 - dps1) > 1e-6);

			// (The old "one shared BiS set" assertion is retired: the BiS
			// side runs the same kit pass now, so per-mob answers may
			// legitimately differ within the inventory budget.)
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void zulrahFormsShareOneSetWithPerFormDps() throws Exception {
		// Field bug report 2026-07-17: all three forms showed the SAME dps.
		// The corpus rows disagree hard (ranged def 300 / 50 / 0), so the
		// shared ranged set MUST produce three different numbers.
		Tables data = new DataService().load();
		List<Monster> forms = new ArrayList<>();
		for (Monster hit : data.searchMonsters("zulrah", 10)) {
			if ("Zulrah".equals(hit.getName())) {
				forms.add(hit);
			}
		}
		Assert.assertEquals("corpus premise: three Zulrah forms", 3, forms.size());
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(12926, 1);  // toxic blowpipe
		owned.put(11840, 1);  // dragon boots
		owned.put(2503, 1);   // black d'hide body
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, forms, owned);
			Assert.assertEquals(3, roster.result.pm.size());
			List<Double> dps = new ArrayList<>();
			List<List<Integer>> sets = new ArrayList<>();
			for (Map<Style, Service.Pick> pm : roster.result.pm) {
				Service.Pick ranged = pm.get(Style.RANGED);
				Assert.assertNotNull(ranged);
				Assert.assertFalse(ranged.owned.isEmpty());
				dps.add(ranged.owned.get(0).getDps());
				sets.add(setIds(ranged.owned.get(0).getLoadout()));
			}
			// ONE set...
			Assert.assertEquals(sets.get(0), sets.get(1));
			Assert.assertEquals(sets.get(0), sets.get(2));
			// ...three different numbers.
			Assert.assertTrue("form dps must differ: " + dps,
				Math.abs(dps.get(0) - dps.get(1)) > 1e-6
					|| Math.abs(dps.get(1) - dps.get(2)) > 1e-6);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void bigHpMobDominatesTheSharedSetChoice() throws Exception {
		// HP-weighted objective (field decision 2026-07-17): Vorkath (~750hp,
		// undead - salve territory) vs a goblin (5hp) - the shared melee
		// neck must be the big mob's preference, the goblin along for the
		// ride. (A mob the set cannot damage contributes zero everywhere,
		// so an unhittable giant drops out of the choice naturally.)
		Tables data = new DataService().load();
		Monster vorkath = data.searchMonsters("vorkath", 1).get(0);
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Assert.assertTrue("premise: vorkath dwarfs the goblin",
			vorkath.getHitpoints() >= 10 * goblin.getHitpoints());
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12018, 1);  // salve amulet(ei)
		owned.put(19553, 1);  // amulet of torture
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, Arrays.asList(vorkath, goblin), owned);
			Service.Pick melee = roster.result.pm.get(0).get(Style.MELEE);
			Assert.assertNotNull(melee);
			Assert.assertFalse(melee.owned.isEmpty());
			Gear neck = melee.owned.get(0).getLoadout().get(Slot.NECK);
			Assert.assertNotNull(neck);
			Assert.assertEquals("the 750hp undead's salve wins the shared neck",
				"salve amulet(ei)", neck.getLname());
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void rosterSharesOneSpecWeaponAcrossMobs() throws Exception {
		// The spec weapon is part of the carried set (field decision
		// 2026-07-17): the roster brings exactly ONE, chosen for the most
		// DPS added across the WHOLE trip (value-over-replacement, HP-
		// weighted) AMONG options that pay for their carried seat. Under
		// the fishing drain math the warhammer outbids the dagger trip-wide
		// (two opening specs at Graardor are worth the fishing), but its
		// damage-priced seat value cannot cover the swap slot it displaces
		// - the seat check falls through to the dagger, the strongest
		// KEEPABLE option (a burst spec on the low-defence fire giant), and
		// the trip carries a spec rather than none (regression 2026-08-08:
		// argmax-then-veto used to drop the spec slot entirely).
		Tables data = new DataService().load();
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		Monster fireGiant = data.searchMonsters("fire giant", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // whip
		owned.put(1215, 1);   // dragon dagger
		owned.put(13576, 1);  // dragon warhammer
		// Premium runes: the rune-ownership gate (0.4.1) would otherwise
		// strip the magic rows and shift the HP-weighted seat arithmetic
		// this test pins - rune poverty has its own net.
		owned.put(565, 1000);   // blood
		owned.put(560, 1000);   // death
		owned.put(566, 1000);   // soul
		owned.put(21880, 1000); // wrath
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, Arrays.asList(graardor, fireGiant), owned);
			Service.Pick m0 = roster.result.pm.get(0).get(Style.MELEE);
			Service.Pick m1 = roster.result.pm.get(1).get(Style.MELEE);
			Assert.assertNotNull(m0.specWeapon);
			Assert.assertNotNull(m1.specWeapon);
			Assert.assertEquals("one spec weapon for the whole trip",
				m0.specWeapon.getId(), m1.specWeapon.getId());
			Assert.assertEquals("the trip-wide best spec wins the shared slot",
				1215, m0.specWeapon.getId());
			// The numbers still flip per mob.
			Assert.assertTrue(m0.specExpectedDamage > 0);
			Assert.assertTrue(m1.specExpectedDamage > 0);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void zulrahRosterWearsRecoilGear() throws Exception {
		// Field report 2026-08-09: the Zulrah roster recommended sets with
		// no recoil gear - ensureRequiredUtility (the mandatory-recoil
		// swap) was a single-mob post-pass the roster path never ran, the
		// same disease as the quiver relocation. Every non-empty shown set
		// on both sides must satisfy the recoil requirement.
		Tables data = new DataService().load();
		List<Monster> forms = new ArrayList<>(data.searchMonsters("zulrah", 5));
		Assert.assertTrue("the Zulrah forms must load", forms.size() >= 3);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(12926, 1);  // toxic blowpipe - Zulrah rejects melee
		owned.put(2550, 1);   // ring of recoil
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, forms, owned, 3);
			boolean sawAnySet = false;
			for (int j = 0; j < forms.size(); j++) {
				for (Style s : Style.concreteValues()) {
					Service.Pick sr = roster.result.pm.get(j).get(s);
					if (sr == null) {
						continue;
					}
					if (sr.owned != null && !sr.owned.isEmpty()) {
						sawAnySet = true;
						Assert.assertTrue("owned set vs " + forms.get(j).getVersion()
								+ " " + s + " must carry recoil",
							com.ll.engine.RequiredUtility.hasRecoil(
								sr.owned.get(0).getLoadout()));
					}
					if (sr.overallBest != null) {
						Assert.assertTrue("BiS set vs " + forms.get(j).getVersion()
								+ " " + s + " must carry recoil",
							com.ll.engine.RequiredUtility.hasRecoil(
								sr.overallBest.getLoadout()));
					}
				}
			}
			Assert.assertTrue(sawAnySet);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void bisRosterKeepsASpecWhenTheKitContestIsClose() throws Exception {
		// Field report 2026-08-09 (Phantom Muspah, Spec chip on, no BiS
		// spec shown): the magic primary outbid a spec-kept ranged primary
		// by 0.3% of kit total because the primary contest scored the kept
		// spec in the damage-blind seat currency (elder maul = 58 there,
		// 1,100+ in honest drain-aware value) - so the whole BiS trip lost
		// its spec. The contest now scores what the card SHOWS: kit total
		// plus the kept spec's dpsAdded value.
		Tables data = new DataService().load();
		List<Monster> mobs = new ArrayList<>(data.searchMonsters("phantom muspah", 10));
		Assert.assertTrue("the Muspah phases must load", mobs.size() >= 3);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // whip - the owned side is irrelevant here
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, mobs, owned, 3);
			boolean anyGameSpec = false;
			for (int j = 0; j < mobs.size(); j++) {
				for (Style s : Style.concreteValues()) {
					Service.Pick sr = roster.result.pm.get(j).get(s);
					anyGameSpec |= sr != null && sr.gameSpecWeapon != null;
				}
			}
			Assert.assertTrue("the BiS trip must carry its spec somewhere",
				anyGameSpec);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void rosterArbitratesTheLightbearerOnTheSharedRing() throws Exception {
		// Field report 2026-08-08 (Nex group): the shared ring falls out of
		// raw-stat set construction, which the stat-less Lightbearer can
		// never win - but its doubled regen scales the trip's spec value.
		// Against a stat-less alternative (ring of dueling) the Lightbearer
		// must take the shared slot whenever any spec value is in play.
		Tables data = new DataService().load();
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		Monster fireGiant = data.searchMonsters("fire giant", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // whip
		owned.put(13576, 1);  // dragon warhammer
		for (Gear g : data.getGearItems()) {
			if ((g.getLname().equals("ring of dueling")
				|| g.getLname().equals("lightbearer")) && g.isStandardGear()) {
				owned.put(g.getId(), 1);
			}
		}
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, Arrays.asList(graardor, fireGiant), owned);
			Service.Pick m0 = roster.result.pm.get(0).get(Style.MELEE);
			Assert.assertNotNull(m0);
			Gear ring = m0.owned.get(0).getLoadout().get(com.ll.data.Slot.RING);
			Assert.assertNotNull("a ring must be worn", ring);
			Assert.assertTrue("doubled regen with no stat cost must take the shared slot, was: "
				+ ring.label(), ring.getLname().contains("lightbearer"));
			Assert.assertNotNull("the trip still carries its spec", m0.specWeapon);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void aStatRingKeepsTheSharedSlotOnTrashRosters() throws Exception {
		// Two goblins die before any sustained spec fires - the Berserker
		// ring's strength beats regen nobody spends.
		Tables data = new DataService().load();
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // whip
		owned.put(13576, 1);  // dragon warhammer
		for (Gear g : data.getGearItems()) {
			if ((g.getLname().equals("berserker ring (i)")
				|| g.getLname().equals("lightbearer")) && g.isStandardGear()) {
				owned.put(g.getId(), 1);
			}
		}
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, Arrays.asList(goblin, goblin), owned);
			Service.Pick m0 = roster.result.pm.get(0).get(Style.MELEE);
			Assert.assertNotNull(m0);
			Gear ring = m0.owned.get(0).getLoadout().get(com.ll.data.Slot.RING);
			Assert.assertNotNull(ring);
			Assert.assertTrue("no spec value, no swap - the stat ring stands, was: "
				+ ring.label(), ring.getLname().contains("berserker"));
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void tormentedDemonPhasesHonorTheShieldInTheRoster() throws Exception {
		// The TD group's synthetic phases (M-3): with MELEE-ONLY gear (no
		// cross-style kit possible) the melee-immune phase must return NO
		// melee set, while the two hittable phases share one.
		Tables data = new DataService().load();
		com.ll.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip (also bypasses the TD reduction)
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, tds.getMobs(), owned);
			Assert.assertEquals(3, roster.result.pm.size());
			// Phase order in the curation: melee, ranged, magic immune.
			Service.Pick meleeVsMeleeImmune =
				roster.result.pm.get(0).get(Style.MELEE);
			Assert.assertTrue("no melee set vs the melee-shielded phase",
				meleeVsMeleeImmune == null || meleeVsMeleeImmune.owned.isEmpty());
			Service.Pick m1 = roster.result.pm.get(1).get(Style.MELEE);
			Service.Pick m2 = roster.result.pm.get(2).get(Style.MELEE);
			Assert.assertFalse("hittable phases carry the shared melee set", m1.owned.isEmpty());
			Assert.assertFalse(m2.owned.isEmpty());
			Assert.assertEquals("one shared set across the hittable phases",
				setIds(m1.owned.get(0).getLoadout()), setIds(m2.owned.get(0).getLoadout()));
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void crossStyleKitAnswersEveryShieldPhase() throws Exception {
		// THE CROSS-STYLE KIT (field decision 2026-07-17): owning weapons
		// of several styles, the bench carries another style's weapon, so
		// ONE kit answers every TD shield phase - each phase's shown result
		// attacks with a style the phase is NOT immune to, and exactly one
		// tab (the kit's primary style) has no immune hole.
		Tables data = new DataService().load();
		com.ll.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12926, 1);  // toxic blowpipe
		owned.put(11907, 1);  // trident of the seas
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, tds.getMobs(), owned);
			Assert.assertEquals(3, roster.result.pm.size());
			// Every phase has a best-across-styles answer that respects its
			// shield (phase order matches the immunity order).
			Style[] immuneTo = {Style.MELEE, Style.RANGED, Style.MAGIC};
			for (int j = 0; j < 3; j++) {
				Dps best = null;
				for (Style style : Style.values()) {
					Service.Pick r = roster.result.pm.get(j).get(style);
					Dps shown = r == null || r.owned.isEmpty() ? null : r.owned.get(0);
					if (shown != null && (best == null || shown.getDps() > best.getDps())) {
						best = shown;
					}
				}
				Assert.assertNotNull("phase " + j + " has a kit answer", best);
				Assert.assertTrue(best.getDps() > 0);
				Assert.assertNotEquals("phase " + j + " must not attack into its shield",
					immuneTo[j], styleOfAttack(best.getAttackType()));
			}
			// Each tab stays honest about its own immunity even in the kit.
			Service.Pick meleeVsMeleeImmune =
				roster.result.pm.get(0).get(Style.MELEE);
			Assert.assertTrue("no melee roll into the melee shield",
				meleeVsMeleeImmune == null || meleeVsMeleeImmune.owned.isEmpty());
			Service.Pick rangedVsRangedImmune =
				roster.result.pm.get(1).get(Style.RANGED);
			Assert.assertTrue("no ranged roll into the ranged shield",
				rangedVsRangedImmune == null || rangedVsRangedImmune.owned.isEmpty());
			// The backpack view never duplicates: vs the melee-immune phase
			// the ranged answer WEARS the blowpipe and benches the whip.
			Service.Pick rangedKit = roster.result.pm.get(0).get(Style.RANGED);
			Assert.assertNotNull(rangedKit);
			Assert.assertFalse(rangedKit.owned.isEmpty());
			Assert.assertEquals("the carried blowpipe answers the melee shield",
				12926, rangedKit.owned.get(0).getLoadout().getWeapon().getId());
			Assert.assertTrue("the displaced whip rides the bench",
				rangedKit.bench.stream().anyMatch(i -> i.getId() == 4151));
			Assert.assertTrue("the worn blowpipe never duplicates into the bench",
				rangedKit.bench.stream().noneMatch(i -> i.getId() == 12926));
			// ...and vs the ranged-immune phase the melee answer flips it.
			Service.Pick meleeKit = roster.result.pm.get(1).get(Style.MELEE);
			Assert.assertNotNull(meleeKit);
			Assert.assertFalse(meleeKit.owned.isEmpty());
			Assert.assertEquals(4151, meleeKit.owned.get(0).getLoadout().getWeapon().getId());
			Assert.assertTrue("the benched blowpipe waits in the backpack",
				meleeKit.bench.stream().anyMatch(i -> i.getId() == 12926));
			Assert.assertTrue(meleeKit.bench.stream().noneMatch(i -> i.getId() == 4151));
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void bigBenchConvergesToEachMobsOwnBest() throws Exception {
		// FIELD INSIGHT (2026-07-17): a bigger bench is an EASIER problem -
		// at the limit every mob simply wears its own best owned set. With
		// Bench 20 the melee-immune TD phase must get the FULL ranged best
		// (d'hide and all, not just a blowpipe in melee armor): the exact
		// dps of optimizing that phase alone.
		Tables data = new DataService().load();
		com.ll.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12926, 1);  // toxic blowpipe
		owned.put(2503, 1);   // black d'hide body - the ranged best differs
		owned.put(2497, 1);   // black d'hide chaps      from the melee base
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, tds.getMobs(), owned, 20);
			RosterResultView solo = run(service,
				Collections.singletonList(tds.getMobs().get(0)), owned, 20);
			double soloBest = solo.result.pm.get(0)
				.get(Style.RANGED).owned.get(0).getDps();
			double rosterBest = 0;
			for (Style style : Style.values()) {
				Service.Pick r = roster.result.pm.get(0).get(style);
				if (r != null && !r.owned.isEmpty()) {
					rosterBest = Math.max(rosterBest, r.owned.get(0).getDps());
				}
			}
			// Slot unification may trade up to 0.5% per-mob dps for fewer
			// carried items (field decision 2026-07-18) - the convergence
			// guarantee is now 99.5% of the solo answer.
			Assert.assertTrue("Bench 20 must reach at least 99.5% of the solo answer:"
					+ " solo=" + soloBest + " roster=" + rosterBest,
				rosterBest >= soloBest * 0.995 - 1e-9);
			// The weapon swap must be visible in the inventory view: vs the
			// ranged-immune phase (worn melee) the carried blowpipe waits
			// in the backpack (field bug: it vanished into a clipped row).
			Service.Pick meleeVsRangedImmune =
				roster.result.pm.get(1).get(Style.MELEE);
			Assert.assertNotNull(meleeVsRangedImmune);
			Assert.assertTrue("the carried cross weapon rides the inventory",
				meleeVsRangedImmune.bench.stream().anyMatch(i -> i.getId() == 12926));
			// NO JUNK (field report): the inventory is the trip PLAN - every
			// item is worn by some mob's best answer (or is the spec swap).
			java.util.Set<Integer> planned = new java.util.HashSet<>();
			for (int j = 0; j < 3; j++) {
				Dps best = null;
				for (Style style : Style.values()) {
					Service.Pick r = roster.result.pm.get(j).get(style);
					if (r != null && !r.owned.isEmpty()
						&& (best == null || r.owned.get(0).getDps() > best.getDps())) {
						best = r.owned.get(0);
					}
				}
				if (best != null) {
					for (Gear item : best.getLoadout().getGear().values()) {
						if (item != null) {
							planned.add(item.getId());
						}
					}
				}
			}
			for (int j = 0; j < 3; j++) {
				for (Style style : Style.values()) {
					Service.Pick r = roster.result.pm.get(j).get(style);
					if (r == null) {
						continue;
					}
					for (Gear item : r.bench) {
						Assert.assertTrue("inventory item outside the plan: " + item.label(),
							planned.contains(item.getId())
								|| (r.specWeapon != null && item.getId() == r.specWeapon.getId()));
					}
				}
			}
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void quiverNeverCostsASwapSlot() throws Exception {
		// FIELD FIX (2026-07-17): the ammo slot does nothing for melee, so
		// the melee answers WEAR the ranged answer's bolts instead of the
		// kit carrying an "ammo swap" - no inventory item is ever an
		// ammo-slot item in a one-ranged-set roster.
		Tables data = new DataService().load();
		com.ll.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(9185, 1);   // rune crossbow
		owned.put(9144, 1);   // runite bolts
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, tds.getMobs(), owned, 4);
			// The melee answer vs the ranged-immune phase wears the bolts.
			Service.Pick melee =
				roster.result.pm.get(1).get(Style.MELEE);
			Assert.assertNotNull(melee);
			Assert.assertFalse(melee.owned.isEmpty());
			Gear wornAmmo = melee.owned.get(0).getLoadout().get(Slot.AMMO);
			Assert.assertNotNull("the melee set wears the ranged ammo", wornAmmo);
			Assert.assertEquals(9144, wornAmmo.getId());
			// ...and no inventory list carries an ammo-slot item.
			for (int j = 0; j < 3; j++) {
				for (Style style : Style.values()) {
					Service.Pick r = roster.result.pm.get(j).get(style);
					if (r == null) {
						continue;
					}
					Assert.assertTrue("ammo never rides the inventory: " + r.bench,
						r.bench.stream().noneMatch(i -> i.getSlot() == Slot.AMMO));
				}
			}
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void specCompetesWithSwapsForTheLastSlot() throws Exception {
		// FIELD FIX (2026-07-17): a spec weapon must not lock the budget
		// when a cross-style weapon would answer an immune phase. Strong
		// melee bank + scorching bow + dragon dagger at Inventory 1: the
		// bow (a whole phase's dps) outbids the dagger's spec - the spec
		// is DROPPED, the bow rides the inventory.
		Tables data = new DataService().load();
		com.ll.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(29589, 1);  // emberlight (demonbane melee)
		owned.put(19553, 1);  // amulet of torture
		owned.put(13239, 1);  // primordial boots
		owned.put(22981, 1);  // ferocious gloves
		owned.put(11832, 1);  // bandos chestplate
		owned.put(11834, 1);  // bandos tassets
		owned.put(21295, 1);  // infernal cape
		owned.put(29591, 1);  // scorching bow (demonbane ranged, 2h)
		owned.put(21326, 1);  // amethyst arrow
		owned.put(1215, 1);   // dragon dagger (the spec that must lose)
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, tds.getMobs(), owned, 1);
			// The melee-immune phase gets the bow's answer.
			Dps phase0 = null;
			for (Style style : Style.values()) {
				Service.Pick r = roster.result.pm.get(0).get(style);
				if (r != null && !r.owned.isEmpty()
					&& (phase0 == null || r.owned.get(0).getDps() > phase0.getDps())) {
					phase0 = r.owned.get(0);
				}
			}
			Assert.assertNotNull("the melee-immune phase has an answer", phase0);
			Assert.assertEquals("the carried bow answers the melee shield",
				29591, phase0.getLoadout().getWeapon().getId());
			// The spec lost its seat, and the dagger stays home.
			Service.Pick melee = roster.result.pm.get(1).get(Style.MELEE);
			Assert.assertNotNull(melee);
			Assert.assertFalse(melee.owned.isEmpty());
			Assert.assertNull("the spec is dropped for the bow", melee.specWeapon);
			Assert.assertTrue("the bow rides the inventory",
				melee.bench.stream().anyMatch(i -> i.getId() == 29591));
			Assert.assertTrue("the dagger stays home",
				melee.bench.stream().noneMatch(i -> i.getId() == 1215));
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void bisSideRunsTheSameKit() throws Exception {
		// FIELD REPORT (2026-07-17): the BiS view lagged the Yours view -
		// no swaps, no inventory, immune holes. The BiS side now runs the
		// SAME kit pass under the same budget: every phase has a best
		// answer that respects its shield, and the BiS inventory shows.
		Tables data = new DataService().load();
		com.ll.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // owned gear is irrelevant to the BiS side
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, tds.getMobs(), owned, 1);
			Style[] immuneTo = {Style.MELEE, Style.RANGED, Style.MAGIC};
			boolean anyInventory = false;
			for (int j = 0; j < 3; j++) {
				Dps best = null;
				for (Style style : Style.values()) {
					Service.Pick r = roster.result.pm.get(j).get(style);
					if (r == null) {
						continue;
					}
					anyInventory |= !r.gameBench.isEmpty();
					if (r.overallBest != null
						&& (best == null || r.overallBest.getDps() > best.getDps())) {
						best = r.overallBest;
					}
				}
				Assert.assertNotNull("BiS phase " + j + " has a kit answer", best);
				Assert.assertTrue(best.getDps() > 0);
				Assert.assertNotEquals("BiS phase " + j + " must not attack into its shield",
					immuneTo[j], styleOfAttack(best.getAttackType()));
			}
			Assert.assertTrue("the BiS kit shows an inventory", anyInventory);
		} finally {
			service.shutdown();
		}
	}

	@Test
	public void exclusionsScopeToTheirMob() throws Exception {
		// FIELD FIX (2026-07-17): excluding an item for ONE mob keeps it
		// usable for the rest of the roster - the excluding mob's answer
		// just never wears it, even when the shared set carries it.
		Tables data = new DataService().load();
		Monster vorkath = data.searchMonsters("vorkath", 1).get(0);
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12018, 1);  // salve amulet(ei)
		Map<Integer, Map<Style, java.util.Set<Integer>>> excludedByMob =
			Collections.singletonMap(goblin.profileId(), Collections.singletonMap(
				Style.MELEE, Collections.singleton(4151)));
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service,
				Arrays.asList(vorkath, goblin), owned, 1, excludedByMob);
			Service.Pick vsVorkath = roster.result.pm.get(0).get(Style.MELEE);
			Assert.assertNotNull(vsVorkath);
			Assert.assertFalse("the roster still uses the whip", vsVorkath.owned.isEmpty());
			Assert.assertEquals(4151, vsVorkath.owned.get(0).getLoadout().getWeapon().getId());
			Service.Pick vsGoblin = roster.result.pm.get(1).get(Style.MELEE);
			boolean goblinWearsWhip = vsGoblin != null && !vsGoblin.owned.isEmpty()
				&& vsGoblin.owned.get(0).getLoadout().getWeapon() != null
				&& vsGoblin.owned.get(0).getLoadout().getWeapon().getId() == 4151;
			Assert.assertFalse("the excluding mob never wears its excluded item", goblinWearsWhip);
		} finally {
			service.shutdown();
		}
	}

	private static com.ll.data.MonsterGroups.MonsterGroup tdGroup(Tables data) {
		return com.ll.data.MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("Tormented Demons"))
			.findFirst().orElseThrow(() -> new AssertionError("no TD group"));
	}

	/** The style a result's attack-type string rolls under. */
	private static Style styleOfAttack(String attackType) {
		if (attackType != null && attackType.startsWith("ranged")) {
			return Style.RANGED;
		}
		if (attackType != null && attackType.startsWith("magic")) {
			return Style.MAGIC;
		}
		return Style.MELEE;
	}

	@Test
	public void benchCarriesThePerMobSwap() throws Exception {
		// The bench (default 1, no spec weapon owned -> a free swap slot):
		// Vorkath (750hp, undead) anchors the shared neck to the salve;
		// the goblin's better neck (torture) should ride the BENCH and be
		// WORN only against the goblin - per-mob combinations over one
		// carried kit.
		Tables data = new DataService().load();
		Monster vorkath = data.searchMonsters("vorkath", 1).get(0);
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12018, 1);  // salve amulet(ei)
		owned.put(19553, 1);  // amulet of torture
		Service service = new Service(data);
		try {
			RosterResultView roster = run(service, Arrays.asList(vorkath, goblin), owned);
			Service.Pick vsVorkath = roster.result.pm.get(0).get(Style.MELEE);
			Service.Pick vsGoblin = roster.result.pm.get(1).get(Style.MELEE);
			Gear vorkathNeck = vsVorkath.owned.get(0).getLoadout().get(Slot.NECK);
			Gear goblinNeck = vsGoblin.owned.get(0).getLoadout().get(Slot.NECK);
			Assert.assertEquals("the 750hp undead anchors the salve",
				"salve amulet(ei)", vorkathNeck.getLname());
			Assert.assertEquals("the goblin wears the benched torture",
				"amulet of torture", goblinNeck.getLname());
			// The bench is a per-mob BACKPACK: it holds what is NOT worn -
			// the torture waits vs Vorkath, the displaced salve vs the
			// goblin, and a worn item never duplicates into it.
			Assert.assertTrue("the torture waits on the bench vs Vorkath",
				vsVorkath.bench.stream().anyMatch(i -> i.getId() == 19553));
			Assert.assertTrue(vsVorkath.bench.stream().noneMatch(i -> i.getId() == 12018));
			Assert.assertTrue("the displaced salve rides the bench vs the goblin",
				vsGoblin.bench.stream().anyMatch(i -> i.getId() == 12018));
			Assert.assertTrue(vsGoblin.bench.stream().noneMatch(i -> i.getId() == 19553));
		} finally {
			service.shutdown();
		}
	}

	/** Thin holder so the helper can return the typed roster result. */
	private static final class RosterResultView {
		final Service.RosterResult result;

		RosterResultView(Service.RosterResult result) {
			this.result = result;
		}
	}
}
