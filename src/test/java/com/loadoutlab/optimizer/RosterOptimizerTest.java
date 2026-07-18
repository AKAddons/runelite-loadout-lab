package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
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
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(
			mobs, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED,
			new OwnedItems(owned, true), 1, false, false, "",
			Collections.emptyMap(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0, OptimizerService.OptimizeMode.MAX_DPS,
			Collections.emptyMap(), null, Collections.emptySet(),
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
			// Exactly one style tab answers all three phases - the kit tab.
			CombatStyle kitStyle = null;
			for (CombatStyle style : CombatStyle.values())
			{
				boolean complete = true;
				for (int j = 0; j < 3; j++)
				{
					OptimizerService.StyleResult r = roster.result.perMob.get(j).get(style);
					complete &= r != null && !r.owned.isEmpty() && r.owned.get(0).getDps() > 0;
				}
				if (complete)
				{
					Assert.assertNull("only the kit tab is hole-free", kitStyle);
					kitStyle = style;
				}
			}
			Assert.assertNotNull("some tab must answer every phase", kitStyle);
			// Phase order matches the immunity order: melee, ranged, magic.
			CombatStyle[] immuneTo = {CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC};
			List<GearItem> bench = null;
			for (int j = 0; j < 3; j++)
			{
				OptimizerService.StyleResult r = roster.result.perMob.get(j).get(kitStyle);
				Assert.assertNotEquals("phase " + j + " must not attack into its shield",
					immuneTo[j], styleOfAttack(r.owned.get(0).getAttackType()));
				if (bench == null)
				{
					bench = r.bench;
				}
				Assert.assertEquals("one bench across the roster", bench, r.bench);
			}
			Assert.assertFalse("the kit carries the cross-style weapon", bench.isEmpty());
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
			Assert.assertTrue("the torture rides the bench",
				vsGoblin.bench.stream().anyMatch(i -> i.getId() == 19553));
			Assert.assertEquals("one bench across the roster",
				vsVorkath.bench, vsGoblin.bench);
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
