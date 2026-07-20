package com.loadoutlab.profile;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Golden-output capture for the ROSTER path - the multi-mob canvas that
 * {@link QueryGolden} (single-mob only) never touches: bestPerStyleAcross,
 * the cross-style kit search, slot/ammo unification, per-mob spec picks and
 * the inventory breakpoint curve. Together with QueryGolden this is the
 * behaviour net a refactor of OptimizerService or the engine must not move.
 *
 * Prints, per curated group and bench size, every mob's per-style answer
 * (worn set, spec, dps) plus the shared kit and the curve points.
 *
 *   ./gradlew -q rosterGolden > roster-golden.txt
 *
 * The contract: byte-identical output unless a behaviour change is intended.
 */
public final class RosterGolden
{
	/** Curated groups chosen for path coverage: multi-style immunities and
	 * synthetic phase variants (TDs, Grotesque Guardians), raid-supplied
	 * boosts (CoX/ToA), a wilderness roster (risk model), and a plain
	 * multi-mob roster. Resolved by name against the curated list - a name
	 * that stops resolving fails loudly rather than silently shrinking
	 * the matrix. */
	private static final String[] GROUPS = {
		"Tormented Demons",
		"Grotesque Guardians",
		"Chambers of Xeric",
		"Tombs of Amascut",
		"Dagannoth Kings",
		"Royal Titans",
	};

	/** Bench sizes: 0 = strictly one worn set (no carried swaps), 1 = the
	 * pre-bench default, 3 = a small tribrid kit, 8 = the raid preset. */
	private static final int[] BENCHES = {0, 1, 3, 8};

	private RosterGolden()
	{
	}

	public static void main(String[] args) throws Exception
	{
		LoadoutData data = new DataService().load();
		List<MonsterGroups.MonsterGroup> groups = MonsterGroups.load(data);
		PlayerProfile fixture = FixtureBank.profile(data);

		for (String wanted : GROUPS)
		{
			MonsterGroups.MonsterGroup group = null;
			for (MonsterGroups.MonsterGroup candidate : groups)
			{
				if (candidate.getName().equalsIgnoreCase(wanted))
				{
					group = candidate;
					break;
				}
			}
			if (group == null)
			{
				System.out.println("##### MISSING GROUP: " + wanted);
				continue;
			}
			for (int bench : BENCHES)
			{
				System.out.println("##### " + group.getName() + " | mobs=" + group.getMobs().size()
					+ " | bench=" + bench);
				System.out.println(run(data, group.getMobs(), fixture, bench));
			}
		}
	}

	private static String run(LoadoutData data, List<MonsterStats> mobs,
		PlayerProfile profile, int bench) throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
			com.loadoutlab.optimizer.ServiceCalls.bestPerStyleAcross(service, mobs,
				profile.realLevels, profile.boostedLevels, profile.prayerUnlocks,
				profile.requirements, profile.ownedItems(), profile.owned.hashCode(),
				false, false, "", Collections.emptyMap(), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false,
				Collections.emptySet(), 0, OptimizerService.OptimizeMode.MAX_DPS, bench,
				Collections.emptyMap(), null, Collections.emptySet(),
				roster ->
				{
					out.set(roster);
					done.countDown();
				});
			if (!done.await(300, TimeUnit.SECONDS))
			{
				return "roster query timed out";
			}
			return render(out.get());
		}
		finally
		{
			service.shutdown();
		}
	}

	private static String render(OptimizerService.RosterResult roster)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < roster.mobs.size(); i++)
		{
			sb.append("--- ").append(roster.mobs.get(i).label()).append('\n');
			Map<CombatStyle, OptimizerService.StyleResult> perStyle = roster.perMob.get(i);
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
			{
				OptimizerService.StyleResult result = perStyle == null ? null : perStyle.get(style);
				sb.append("  [").append(style).append("] ");
				if (result == null || result.owned == null || result.owned.isEmpty())
				{
					sb.append("no usable set\n");
					continue;
				}
				DpsResult best = result.owned.get(0);
				sb.append(String.format("%.4f dps (max %d, %.1f%% acc) assumes: %s%n",
					best.getDps(), best.getMaxHit(), best.getAccuracy() * 100, result.boostLabel));
				sb.append("      worn:");
				for (GearSlot slot : GearSlot.values())
				{
					GearItem item = best.getLoadout().get(slot);
					if (item != null)
					{
						sb.append(' ').append(item.label()).append(',');
					}
				}
				sb.append('\n');
				if (best.getSpellName() != null)
				{
					sb.append("      spell: ").append(best.getSpellName()).append('\n');
				}
				if (result.specWeapon != null)
				{
					sb.append(String.format("      spec: %s (adds ~%.3f dps, avg %.2f)%n",
						result.specWeapon.label(), result.specDpsAdded, result.specExpectedDamage));
				}
				sb.append("      inventory:");
				for (GearItem carried : result.bench == null ? List.<GearItem>of() : result.bench)
				{
					sb.append(' ').append(carried.label()).append(',');
				}
				sb.append('\n');
				if (result.overallBest != null)
				{
					sb.append(String.format("      game best: %.4f%n", result.overallBest.getDps()));
				}
			}
		}
		if (roster.curve != null)
		{
			sb.append("--- curve (mobs=").append(roster.curve.mobCount)
				.append(", specFirst=").append(roster.curve.specFirst).append(")\n");
			List<double[]> points = new ArrayList<>(roster.curve.points);
			for (double[] point : points)
			{
				sb.append(String.format("      slots=%.0f total=%.4f viable=%.0f%n",
					point[0], point[1], point[2]));
			}
		}
		return sb.toString();
	}
}
