package com.ll.profile;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.MonsterGroups;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Dps;
import com.ll.engine.Req;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import com.ll.optimizer.Service;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Golden-output capture for the ROSTER path - the multi-mob canvas that
 * {@link QueryGolden} (single-mob only) never touches: bestPerStyleAcross,
 * the cross-style kit search, slot/ammo unification and per-mob spec
 * picks. Together with QueryGolden this is the behaviour net a refactor of
 * Service or the engine must not move.
 *
 * Prints, per curated group and bench size, every mob's per-style answer
 * (worn set, spec, dps) plus the shared kit.
 *
 *   ./gradlew -q rosterGolden > roster-golden.txt
 *
 * The contract: byte-identical output unless a behaviour change is intended.
 */
public final class RosterGolden {
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

	private RosterGolden() {
	}

	public static void main(String[] args) throws Exception {
		Tables data = new DataService().load();
		List<MonsterGroups.MonsterGroup> groups = MonsterGroups.load(data);
		PlayerProfile fixture = FixtureBank.profile(data);

		for (String wanted : GROUPS) {
			MonsterGroups.MonsterGroup group = null;
			for (MonsterGroups.MonsterGroup candidate : groups) {
				if (candidate.getName().equalsIgnoreCase(wanted)) {
					group = candidate;
					break;
				}
			}
			if (group == null) {
				System.out.println("##### MISSING GROUP: " + wanted);
				continue;
			}
			for (int bench : BENCHES) {
				System.out.println("##### " + group.getName() + " | mobs=" + group.getMobs().size()
					+ " | bench=" + bench);
				System.out.println(run(data, group.getMobs(), fixture, bench));
			}
		}
	}

	private static String run(Tables data, List<Monster> mobs,
		PlayerProfile profile, int bench) throws Exception {
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Service.RosterResult> out = new AtomicReference<>();
			com.ll.optimizer.ServiceCalls.bestPerStyleAcross(service, mobs,
				profile.realLevels, profile.boostedLevels, profile.prayerUnlocks,
				profile.requirements, profile.oi(), profile.owned.hashCode(),
				false, false, "", Collections.emptyMap(), -1,
				Req.DEFAULT_RISK_BUDGET_GP, false, false,
				Collections.emptySet(), 0, bench,
				Collections.emptyMap(), null, Collections.emptySet(),
				roster -> {
					out.set(roster);
					done.countDown();
				});
			if (!done.await(300, TimeUnit.SECONDS)) {
				return "roster query timed out";
			}
			return render(out.get());
		} finally {
			service.shutdown();
		}
	}

	private static String render(Service.RosterResult roster) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < roster.mobs.size(); i++) {
			sb.append("--- ").append(roster.mobs.get(i).label()).append('\n');
			Map<Style, Service.Pick> perStyle = roster.pm.get(i);
			for (Style style : new Style[]{Style.MELEE, Style.RANGED, Style.MAGIC}) {
				Service.Pick result = perStyle == null ? null : perStyle.get(style);
				sb.append("  [").append(style).append("] ");
				if (result == null || result.owned == null || result.owned.isEmpty()) {
					sb.append("no usable set\n");
					continue;
				}
				Dps best = result.owned.get(0);
				sb.append(String.format("%.4f dps (max %d, %.1f%% acc) assumes: %s%n",
					best.getDps(), best.getMaxHit(), best.getAccuracy() * 100, result.boostLabel));
				sb.append("      worn:");
				for (Slot slot : Slot.values()) {
					Gear item = best.getLoadout().get(slot);
					if (item != null) {
						sb.append(' ').append(item.label()).append(',');
					}
				}
				sb.append('\n');
				if (best.getSpellName() != null) {
					sb.append("      spell: ").append(best.getSpellName()).append('\n');
				}
				if (result.specWeapon != null) {
					sb.append(String.format("      spec: %s (adds ~%.3f dps, avg %.2f)%n",
						result.specWeapon.label(), result.sda, result.specExpectedDamage));
				}
				sb.append("      inventory:");
				for (Gear carried : result.bench == null ? List.<Gear>of() : result.bench) {
					sb.append(' ').append(carried.label()).append(',');
				}
				sb.append('\n');
				if (result.overallBest != null) {
					sb.append(String.format("      game best: %.4f%n", result.overallBest.getDps()));
				}
			}
		}
		return sb.toString();
	}
}
