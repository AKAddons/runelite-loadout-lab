package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.Loadout;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
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
public class RosterOptimizerTest
{
	private static Map<Integer, Integer> ownedMelee()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(1215, 1);   // dragon dagger (spec)
		owned.put(11840, 1);  // dragon boots
		owned.put(6737, 1);   // berserker ring
		return owned;
	}

	private static RosterResultView run(OptimizerService service, List<MonsterStats> mobs,
		Map<Integer, Integer> owned) throws Exception
	{
		return run(service, mobs, owned, 1);
	}

	private static RosterResultView run(OptimizerService service, List<MonsterStats> mobs,
		Map<Integer, Integer> owned, int maxSwaps) throws Exception
	{
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(
			mobs, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED,
			new OwnedItems(owned, true), 1, false, false, "",
			Collections.emptyMap(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0, OptimizerService.OptimizeMode.MAX_DPS,
			maxSwaps, Collections.emptyMap(), null, Collections.emptySet(),
			roster ->
			{
				out.set(roster);
				done.countDown();
			});
		Assert.assertTrue("roster compute timed out", done.await(90, TimeUnit.SECONDS));
		return new RosterResultView(out.get());
	}

	/** The worn item ids of a set, sorted - a set-identity fingerprint. */
	private static List<Integer> setIds(Loadout loadout)
	{
		List<Integer> ids = new ArrayList<>();
		for (GearSlot slot : GearSlot.values())
		{
			if (loadout.get(slot) != null)
			{
				ids.add(loadout.get(slot).getId());
			}
		}
		Collections.sort(ids);
		return ids;
	}

	@Test
	public void singleMobRosterReducesToBestPerStyle() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats ankou = data.searchMonsters("ankou", 1).get(0);
		Map<Integer, Integer> owned = ownedMelee();
		OptimizerService service = new OptimizerService(data);
		try
		{
			// via the roster entry point (1 mob)
			RosterResultView roster = run(service, Collections.singletonList(ankou), owned);
			Assert.assertEquals(1, roster.result.perMob.size());
			double rosterDps = roster.result.perMob.get(0)
				.get(CombatStyle.MELEE).owned.get(0).getDps();

			// via the direct single-mob path
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(ankou, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 1, false, false, "",
				Collections.emptyMap(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false,
				Collections.emptySet(), 0, OptimizerService.OptimizeMode.MAX_DPS,
				Collections.emptyMap(), null, Collections.emptySet(),
				map ->
				{
					out.set(map);
					done.countDown();
				});
			Assert.assertTrue(done.await(90, TimeUnit.SECONDS));
			double directDps = out.get().get(CombatStyle.MELEE).owned.get(0).getDps();
			Assert.assertEquals("1-mob roster must equal bestPerStyle", directDps, rosterDps, 1e-9);
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void twoMobRosterSharesOneSetButFlipsTheNumbers() throws Exception
	{
		LoadoutData data = new DataService().load();
		// Two monsters with clearly different defence so the same set does
		// visibly different dps - the lens invariant (same set, numbers flip).
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		MonsterStats ankou = data.searchMonsters("ankou", 1).get(0);
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, Arrays.asList(goblin, ankou), ownedMelee());
			Assert.assertEquals(2, roster.result.perMob.size());

			OptimizerService.StyleResult m0 = roster.result.perMob.get(0).get(CombatStyle.MELEE);
			OptimizerService.StyleResult m1 = roster.result.perMob.get(1).get(CombatStyle.MELEE);
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

			// The shared game (BiS) set is also single across the roster.
			if (m0.overallBest != null && m1.overallBest != null)
			{
				Assert.assertEquals("the roster shares a single BiS set",
					setIds(m0.overallBest.getLoadout()), setIds(m1.overallBest.getLoadout()));
			}
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void zulrahFormsShareOneSetWithPerFormDps() throws Exception
	{
		// Field bug report 2026-07-17: all three forms showed the SAME dps.
		// The corpus rows disagree hard (ranged def 300 / 50 / 0), so the
		// shared ranged set MUST produce three different numbers.
		LoadoutData data = new DataService().load();
		List<MonsterStats> forms = new ArrayList<>();
		for (MonsterStats hit : data.searchMonsters("zulrah", 10))
		{
			if ("Zulrah".equals(hit.getName()))
			{
				forms.add(hit);
			}
		}
		Assert.assertEquals("corpus premise: three Zulrah forms", 3, forms.size());
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(12926, 1);  // toxic blowpipe
		owned.put(11840, 1);  // dragon boots
		owned.put(2503, 1);   // black d'hide body
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, forms, owned);
			Assert.assertEquals(3, roster.result.perMob.size());
			List<Double> dps = new ArrayList<>();
			List<List<Integer>> sets = new ArrayList<>();
			for (Map<CombatStyle, OptimizerService.StyleResult> perMob : roster.result.perMob)
			{
				OptimizerService.StyleResult ranged = perMob.get(CombatStyle.RANGED);
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
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void bigHpMobDominatesTheSharedSetChoice() throws Exception
	{
		// HP-weighted objective (field decision 2026-07-17): Vorkath (~750hp,
		// undead - salve territory) vs a goblin (5hp) - the shared melee
		// neck must be the big mob's preference, the goblin along for the
		// ride. (A mob the set cannot damage contributes zero everywhere,
		// so an unhittable giant drops out of the choice naturally.)
		LoadoutData data = new DataService().load();
		MonsterStats vorkath = data.searchMonsters("vorkath", 1).get(0);
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		Assert.assertTrue("premise: vorkath dwarfs the goblin",
			vorkath.getHitpoints() >= 10 * goblin.getHitpoints());
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12018, 1);  // salve amulet(ei)
		owned.put(19553, 1);  // amulet of torture
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, Arrays.asList(vorkath, goblin), owned);
			OptimizerService.StyleResult melee = roster.result.perMob.get(0).get(CombatStyle.MELEE);
			Assert.assertNotNull(melee);
			Assert.assertFalse(melee.owned.isEmpty());
			GearItem neck = melee.owned.get(0).getLoadout().get(GearSlot.NECK);
			Assert.assertNotNull(neck);
			Assert.assertEquals("the 750hp undead's salve wins the shared neck",
				"salve amulet(ei)", neck.getNameLower());
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void rosterSharesOneSpecWeaponAcrossMobs() throws Exception
	{
		// Solo, Graardor picks the warhammer (drain pays off over 255hp)
		// and a goblin picks the dagger (raw burst) - proven by
		// OptimizerServiceTest. With ZERO swaps the spec weapon is part of
		// the carried set (field decision 2026-07-17): the roster brings
		// ONE, HP-weighted like the worn set - Graardor's warhammer wins.
		LoadoutData data = new DataService().load();
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // whip
		owned.put(1215, 1);   // dragon dagger
		owned.put(13576, 1);  // dragon warhammer
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, Arrays.asList(graardor, goblin), owned);
			OptimizerService.StyleResult m0 = roster.result.perMob.get(0).get(CombatStyle.MELEE);
			OptimizerService.StyleResult m1 = roster.result.perMob.get(1).get(CombatStyle.MELEE);
			Assert.assertNotNull(m0.specWeapon);
			Assert.assertNotNull(m1.specWeapon);
			Assert.assertEquals("one spec weapon for the whole trip",
				m0.specWeapon.getId(), m1.specWeapon.getId());
			Assert.assertEquals("the 255hp boss's warhammer wins the slot",
				13576, m0.specWeapon.getId());
			// The numbers still flip per mob.
			Assert.assertTrue(m0.specExpectedDamage > 0);
			Assert.assertTrue(m1.specExpectedDamage > 0);
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void tormentedDemonPhasesHonorTheShieldInTheRoster() throws Exception
	{
		// The TD group's synthetic phases (M-3): with MELEE-ONLY gear (no
		// cross-style kit possible) the melee-immune phase must return NO
		// melee set, while the two hittable phases share one.
		LoadoutData data = new DataService().load();
		com.loadoutlab.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip (also bypasses the TD reduction)
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, tds.getMobs(), owned);
			Assert.assertEquals(3, roster.result.perMob.size());
			// Phase order in the curation: melee, ranged, magic immune.
			OptimizerService.StyleResult meleeVsMeleeImmune =
				roster.result.perMob.get(0).get(CombatStyle.MELEE);
			Assert.assertTrue("no melee set vs the melee-shielded phase",
				meleeVsMeleeImmune == null || meleeVsMeleeImmune.owned.isEmpty());
			OptimizerService.StyleResult m1 = roster.result.perMob.get(1).get(CombatStyle.MELEE);
			OptimizerService.StyleResult m2 = roster.result.perMob.get(2).get(CombatStyle.MELEE);
			Assert.assertFalse("hittable phases carry the shared melee set", m1.owned.isEmpty());
			Assert.assertFalse(m2.owned.isEmpty());
			Assert.assertEquals("one shared set across the hittable phases",
				setIds(m1.owned.get(0).getLoadout()), setIds(m2.owned.get(0).getLoadout()));
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void crossStyleKitAnswersEveryShieldPhase() throws Exception
	{
		// THE CROSS-STYLE KIT (field decision 2026-07-17): owning weapons
		// of several styles, the bench carries another style's weapon, so
		// ONE kit answers every TD shield phase - each phase's shown result
		// attacks with a style the phase is NOT immune to, and exactly one
		// tab (the kit's primary style) has no immune hole.
		LoadoutData data = new DataService().load();
		com.loadoutlab.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12926, 1);  // toxic blowpipe
		owned.put(11907, 1);  // trident of the seas
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, tds.getMobs(), owned);
			Assert.assertEquals(3, roster.result.perMob.size());
			// Every phase has a best-across-styles answer that respects its
			// shield (phase order matches the immunity order).
			CombatStyle[] immuneTo = {CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC};
			for (int j = 0; j < 3; j++)
			{
				DpsResult best = null;
				for (CombatStyle style : CombatStyle.values())
				{
					OptimizerService.StyleResult r = roster.result.perMob.get(j).get(style);
					DpsResult shown = r == null || r.owned.isEmpty() ? null : r.owned.get(0);
					if (shown != null && (best == null || shown.getDps() > best.getDps()))
					{
						best = shown;
					}
				}
				Assert.assertNotNull("phase " + j + " has a kit answer", best);
				Assert.assertTrue(best.getDps() > 0);
				Assert.assertNotEquals("phase " + j + " must not attack into its shield",
					immuneTo[j], styleOfAttack(best.getAttackType()));
			}
			// Each tab stays honest about its own immunity even in the kit.
			OptimizerService.StyleResult meleeVsMeleeImmune =
				roster.result.perMob.get(0).get(CombatStyle.MELEE);
			Assert.assertTrue("no melee roll into the melee shield",
				meleeVsMeleeImmune == null || meleeVsMeleeImmune.owned.isEmpty());
			OptimizerService.StyleResult rangedVsRangedImmune =
				roster.result.perMob.get(1).get(CombatStyle.RANGED);
			Assert.assertTrue("no ranged roll into the ranged shield",
				rangedVsRangedImmune == null || rangedVsRangedImmune.owned.isEmpty());
			// The backpack view never duplicates: vs the melee-immune phase
			// the ranged answer WEARS the blowpipe and benches the whip.
			OptimizerService.StyleResult rangedKit = roster.result.perMob.get(0).get(CombatStyle.RANGED);
			Assert.assertNotNull(rangedKit);
			Assert.assertFalse(rangedKit.owned.isEmpty());
			Assert.assertEquals("the carried blowpipe answers the melee shield",
				12926, rangedKit.owned.get(0).getLoadout().getWeapon().getId());
			Assert.assertTrue("the displaced whip rides the bench",
				rangedKit.bench.stream().anyMatch(i -> i.getId() == 4151));
			Assert.assertTrue("the worn blowpipe never duplicates into the bench",
				rangedKit.bench.stream().noneMatch(i -> i.getId() == 12926));
			// ...and vs the ranged-immune phase the melee answer flips it.
			OptimizerService.StyleResult meleeKit = roster.result.perMob.get(1).get(CombatStyle.MELEE);
			Assert.assertNotNull(meleeKit);
			Assert.assertFalse(meleeKit.owned.isEmpty());
			Assert.assertEquals(4151, meleeKit.owned.get(0).getLoadout().getWeapon().getId());
			Assert.assertTrue("the benched blowpipe waits in the backpack",
				meleeKit.bench.stream().anyMatch(i -> i.getId() == 12926));
			Assert.assertTrue(meleeKit.bench.stream().noneMatch(i -> i.getId() == 4151));
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void bigBenchConvergesToEachMobsOwnBest() throws Exception
	{
		// FIELD INSIGHT (2026-07-17): a bigger bench is an EASIER problem -
		// at the limit every mob simply wears its own best owned set. With
		// Bench 20 the melee-immune TD phase must get the FULL ranged best
		// (d'hide and all, not just a blowpipe in melee armor): the exact
		// dps of optimizing that phase alone.
		LoadoutData data = new DataService().load();
		com.loadoutlab.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12926, 1);  // toxic blowpipe
		owned.put(2503, 1);   // black d'hide body - the ranged best differs
		owned.put(2497, 1);   // black d'hide chaps      from the melee base
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, tds.getMobs(), owned, 20);
			RosterResultView solo = run(service,
				Collections.singletonList(tds.getMobs().get(0)), owned, 20);
			double soloBest = solo.result.perMob.get(0)
				.get(CombatStyle.RANGED).owned.get(0).getDps();
			double rosterBest = 0;
			for (CombatStyle style : CombatStyle.values())
			{
				OptimizerService.StyleResult r = roster.result.perMob.get(0).get(style);
				if (r != null && !r.owned.isEmpty())
				{
					rosterBest = Math.max(rosterBest, r.owned.get(0).getDps());
				}
			}
			Assert.assertEquals("Bench 20 must equal optimizing the phase alone",
				soloBest, rosterBest, 1e-6);
			// The weapon swap must be visible in the inventory view: vs the
			// ranged-immune phase (worn melee) the carried blowpipe waits
			// in the backpack (field bug: it vanished into a clipped row).
			OptimizerService.StyleResult meleeVsRangedImmune =
				roster.result.perMob.get(1).get(CombatStyle.MELEE);
			Assert.assertNotNull(meleeVsRangedImmune);
			Assert.assertTrue("the carried cross weapon rides the inventory",
				meleeVsRangedImmune.bench.stream().anyMatch(i -> i.getId() == 12926));
			// NO JUNK (field report): the inventory is the trip PLAN - every
			// item is worn by some mob's best answer (or is the spec swap).
			java.util.Set<Integer> planned = new java.util.HashSet<>();
			for (int j = 0; j < 3; j++)
			{
				DpsResult best = null;
				for (CombatStyle style : CombatStyle.values())
				{
					OptimizerService.StyleResult r = roster.result.perMob.get(j).get(style);
					if (r != null && !r.owned.isEmpty()
						&& (best == null || r.owned.get(0).getDps() > best.getDps()))
					{
						best = r.owned.get(0);
					}
				}
				if (best != null)
				{
					for (GearItem item : best.getLoadout().getGear().values())
					{
						if (item != null)
						{
							planned.add(item.getId());
						}
					}
				}
			}
			for (int j = 0; j < 3; j++)
			{
				for (CombatStyle style : CombatStyle.values())
				{
					OptimizerService.StyleResult r = roster.result.perMob.get(j).get(style);
					if (r == null)
					{
						continue;
					}
					for (GearItem item : r.bench)
					{
						Assert.assertTrue("inventory item outside the plan: " + item.label(),
							planned.contains(item.getId())
								|| (r.specWeapon != null && item.getId() == r.specWeapon.getId()));
					}
				}
			}
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void quiverNeverCostsASwapSlot() throws Exception
	{
		// FIELD FIX (2026-07-17): the ammo slot does nothing for melee, so
		// the melee answers WEAR the ranged answer's bolts instead of the
		// kit carrying an "ammo swap" - no inventory item is ever an
		// ammo-slot item in a one-ranged-set roster.
		LoadoutData data = new DataService().load();
		com.loadoutlab.data.MonsterGroups.MonsterGroup tds = tdGroup(data);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(9185, 1);   // rune crossbow
		owned.put(9144, 1);   // runite bolts
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, tds.getMobs(), owned, 4);
			// The melee answer vs the ranged-immune phase wears the bolts.
			OptimizerService.StyleResult melee =
				roster.result.perMob.get(1).get(CombatStyle.MELEE);
			Assert.assertNotNull(melee);
			Assert.assertFalse(melee.owned.isEmpty());
			GearItem wornAmmo = melee.owned.get(0).getLoadout().get(GearSlot.AMMO);
			Assert.assertNotNull("the melee set wears the ranged ammo", wornAmmo);
			Assert.assertEquals(9144, wornAmmo.getId());
			// ...and no inventory list carries an ammo-slot item.
			for (int j = 0; j < 3; j++)
			{
				for (CombatStyle style : CombatStyle.values())
				{
					OptimizerService.StyleResult r = roster.result.perMob.get(j).get(style);
					if (r == null)
					{
						continue;
					}
					Assert.assertTrue("ammo never rides the inventory: " + r.bench,
						r.bench.stream().noneMatch(i -> i.getSlot() == GearSlot.AMMO));
				}
			}
		}
		finally
		{
			service.shutdown();
		}
	}

	private static com.loadoutlab.data.MonsterGroups.MonsterGroup tdGroup(LoadoutData data)
	{
		return com.loadoutlab.data.MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("Tormented Demons"))
			.findFirst().orElseThrow(() -> new AssertionError("no TD group"));
	}

	/** The style a result's attack-type string rolls under. */
	private static CombatStyle styleOfAttack(String attackType)
	{
		if (attackType != null && attackType.startsWith("ranged"))
		{
			return CombatStyle.RANGED;
		}
		if (attackType != null && attackType.startsWith("magic"))
		{
			return CombatStyle.MAGIC;
		}
		return CombatStyle.MELEE;
	}

	@Test
	public void benchCarriesThePerMobSwap() throws Exception
	{
		// The bench (default 1, no spec weapon owned -> a free swap slot):
		// Vorkath (750hp, undead) anchors the shared neck to the salve;
		// the goblin's better neck (torture) should ride the BENCH and be
		// WORN only against the goblin - per-mob combinations over one
		// carried kit.
		LoadoutData data = new DataService().load();
		MonsterStats vorkath = data.searchMonsters("vorkath", 1).get(0);
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(12018, 1);  // salve amulet(ei)
		owned.put(19553, 1);  // amulet of torture
		OptimizerService service = new OptimizerService(data);
		try
		{
			RosterResultView roster = run(service, Arrays.asList(vorkath, goblin), owned);
			OptimizerService.StyleResult vsVorkath = roster.result.perMob.get(0).get(CombatStyle.MELEE);
			OptimizerService.StyleResult vsGoblin = roster.result.perMob.get(1).get(CombatStyle.MELEE);
			GearItem vorkathNeck = vsVorkath.owned.get(0).getLoadout().get(GearSlot.NECK);
			GearItem goblinNeck = vsGoblin.owned.get(0).getLoadout().get(GearSlot.NECK);
			Assert.assertEquals("the 750hp undead anchors the salve",
				"salve amulet(ei)", vorkathNeck.getNameLower());
			Assert.assertEquals("the goblin wears the benched torture",
				"amulet of torture", goblinNeck.getNameLower());
			// The bench is a per-mob BACKPACK: it holds what is NOT worn -
			// the torture waits vs Vorkath, the displaced salve vs the
			// goblin, and a worn item never duplicates into it.
			Assert.assertTrue("the torture waits on the bench vs Vorkath",
				vsVorkath.bench.stream().anyMatch(i -> i.getId() == 19553));
			Assert.assertTrue(vsVorkath.bench.stream().noneMatch(i -> i.getId() == 12018));
			Assert.assertTrue("the displaced salve rides the bench vs the goblin",
				vsGoblin.bench.stream().anyMatch(i -> i.getId() == 12018));
			Assert.assertTrue(vsGoblin.bench.stream().noneMatch(i -> i.getId() == 19553));
		}
		finally
		{
			service.shutdown();
		}
	}

	/** Thin holder so the helper can return the typed roster result. */
	private static final class RosterResultView
	{
		final OptimizerService.RosterResult result;

		RosterResultView(OptimizerService.RosterResult result)
		{
			this.result = result;
		}
	}
}
