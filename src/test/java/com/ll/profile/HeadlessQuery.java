package com.ll.profile;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Dps;
import com.ll.engine.PvpRisk;
import com.ll.optimizer.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The mock environment: run any Loadout Lab query from the command line
 * against a PlayerProfile - no client, no game login.
 *
 *   ./gradlew query -Pargs="vorkath"
 *   ./gradlew query -Pargs="callisto --low-risk 3 --slayer"
 *   ./gradlew query -Pargs="callisto --low-risk 3 --risk-budget 25000"
 *   ./gradlew query -Pargs="green dragon --profile /path/to/profile.json"
 *   ./gradlew query -Pargs="zulrah --maxed"
 *
 * Default profile: ~/.runelite/loadout-lab/profile.json (the plugin
 * exports your real account there on every in-game query). --maxed uses
 * a maxed account owning nothing (game-best only).
 */
public final class HeadlessQuery {
	private HeadlessQuery() {
	}

	public static void main(String[] args) throws Exception {
		System.out.println(run(args));
	}

	public static String run(String[] args) throws Exception {
		StringBuilder monsterName = new StringBuilder();
		Path profilePath = Path.of(System.getProperty("user.home"),
			".runelite", "loadout-lab", "profile.json");
		boolean maxed = false;
		boolean slayer = false;
		boolean f2p = false;
		boolean antifirePotion = false;
		boolean wilderness = false;
		String spellbook = "";
		int lowRisk = -1;
		int riskBudget = com.ll.engine.Req.DEFAULT_RISK_BUDGET_GP;
		int upgradeBudget = 0;
		java.util.Set<Integer> excluded = new java.util.HashSet<>();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--profile": profilePath = Path.of(args[++i]); break;
				case "--maxed": maxed = true; break;
				case "--slayer": slayer = true; break;
				case "--f2p": f2p = true; break;
				case "--antifire-potion": antifirePotion = true; break;
				case "--wilderness": wilderness = true; break;
				case "--spellbook": spellbook = args[++i]; break;
				case "--low-risk": lowRisk = Integer.parseInt(args[++i]); break;
				case "--risk-budget": riskBudget = Integer.parseInt(args[++i]); break;
				case "--budget": upgradeBudget = Integer.parseInt(args[++i]); break;
				// Comma-separated item ids - reproduce a client report with
				// the player's exclusion list (config: loadoutlab.excludedItems).
				case "--exclude":
					for (String id : args[++i].split(",")) {
						excluded.add(Integer.parseInt(id.trim()));
					}
					break;
				default: monsterName.append(monsterName.length() > 0 ? " " : "").append(args[i]);
			}
		}
		PlayerProfile profile = maxed ? PlayerProfileTestSupport.maxed()
			: PlayerProfile.fromJson(Files.readString(profilePath));

		Tables data = new DataService().load();
		java.util.List<Monster> hits = data.searchMonsters(monsterName.toString(), 1);
		if (hits.isEmpty()) {
			return "No monster matches '" + monsterName + "'";
		}
		Monster monster = hits.get(0);

		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			java.util.Map<Style, java.util.Set<Integer>> excludedByStyle =
				new java.util.EnumMap<>(Style.class);
			for (Style style : Style.concreteValues()) {
				excludedByStyle.put(style, excluded);
			}
			boolean inWilderness = wilderness
				|| com.ll.data.WildernessMonsters.isExclusive(monster);
			com.ll.optimizer.ServiceCalls.bestPerStyle(service, monster, profile.realLevels, profile.boostedLevels,
				profile.prayerUnlocks, profile.requirements, profile.oi(),
				profile.owned.hashCode(), f2p, slayer, spellbook,
				excludedByStyle, lowRisk, riskBudget, antifirePotion, inWilderness,
				java.util.Collections.emptySet(), upgradeBudget,
				java.util.Collections.emptyMap(), null, java.util.Collections.emptySet(),
				results -> {
					out.set(results);
					done.countDown();
				});
			if (!done.await(180, TimeUnit.SECONDS)) {
				return "Query timed out";
			}
			return render(monster, out.get(), lowRisk);
		} finally {
			service.shutdown();
		}
	}

	private static String render(Monster monster,
		Map<Style, Service.Pick> results, int lowRisk) {
		StringBuilder sb = new StringBuilder("=== vs ").append(monster.label()).append(" ===\n");
		for (Style style : new Style[]{Style.MELEE, Style.RANGED, Style.MAGIC}) {
			Service.Pick result = results.get(style);
			sb.append("\n[").append(style).append("]\n");
			if (result == null || result.owned.isEmpty()) {
				sb.append("  yours: no usable set\n");
			} else {
				Dps best = result.owned.get(0);
				sb.append(String.format("  yours: %.2f dps (max %d, %.0f%% acc)  assumes: %s%n",
					best.getDps(), best.getMaxHit(), best.getAccuracy() * 100, result.boostLabel));
				for (Slot slot : Slot.values()) {
					Gear item = best.getLoadout().get(slot);
					if (item != null) {
						sb.append(String.format("    %-7s %s%n", slot, item.label()));
					}
				}
				if (best.getSpellName() != null) {
					sb.append("    spell   ").append(best.getSpellName()).append('\n');
				}
				if (result.specWeapon != null) {
					sb.append(String.format("    spec    %s (adds ~%.2f dps, avg %.0f dmg)%n",
						result.specWeapon.label(), result.sda, result.specExpectedDamage));
				}
				if (result.incoming != null && result.incoming.protectPrayer != null) {
					sb.append(String.format("    incoming ~%.2f dps praying %s (~%.2f unprayed)%n",
						result.incoming.tot, result.incoming.protectPrayer,
						result.incoming.unprayedDps));
				}
				if (lowRisk >= 0) {
					PvpRisk.Assessment risk = PvpRisk.assess(best.getLoadout(), result.specWeapon, lowRisk);
					sb.append(String.format("    risk    %s gp (%d kept)%n",
						formatGp(risk.riskGp), lowRisk));
				}
			}
			if (result != null && result.overallBest != null) {
				sb.append(String.format("  game best: %.2f dps  assumes: %s%n",
					result.overallBest.getDps(), result.gbl));
			}
		}
		return sb.toString();
	}

	/** Compact gp: 1.20B / 45.3M / 820k / 950. */
	private static String formatGp(long gp) {
		if (gp >= 1_000_000_000L) {
			return String.format("%.2fB", gp / 1_000_000_000.0);
		}
		if (gp >= 1_000_000L) {
			return String.format("%.1fM", gp / 1_000_000.0);
		}
		return gp >= 1_000L ? gp / 1_000L + "k" : String.valueOf(gp);
	}
}
