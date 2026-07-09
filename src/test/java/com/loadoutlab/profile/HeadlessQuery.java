package com.loadoutlab.profile;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.PvpRisk;
import com.loadoutlab.optimizer.OptimizerService;
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
public final class HeadlessQuery
{
	private HeadlessQuery()
	{
	}

	public static void main(String[] args) throws Exception
	{
		System.out.println(run(args));
	}

	public static String run(String[] args) throws Exception
	{
		StringBuilder monsterName = new StringBuilder();
		Path profilePath = Path.of(System.getProperty("user.home"),
			".runelite", "loadout-lab", "profile.json");
		boolean maxed = false;
		boolean slayer = false;
		boolean f2p = false;
		boolean antifirePotion = false;
		String spellbook = "";
		int lowRisk = -1;
		int riskBudget = com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
		int upgradeBudget = 0;
		OptimizerService.OptimizeMode mode = OptimizerService.OptimizeMode.MAX_DPS;
		for (int i = 0; i < args.length; i++)
		{
			switch (args[i])
			{
				case "--profile": profilePath = Path.of(args[++i]); break;
				case "--maxed": maxed = true; break;
				case "--slayer": slayer = true; break;
				case "--f2p": f2p = true; break;
				case "--antifire-potion": antifirePotion = true; break;
				case "--spellbook": spellbook = args[++i]; break;
				case "--low-risk": lowRisk = Integer.parseInt(args[++i]); break;
				case "--risk-budget": riskBudget = Integer.parseInt(args[++i]); break;
				case "--budget": upgradeBudget = Integer.parseInt(args[++i]); break;
				case "--mode": mode = OptimizerService.OptimizeMode.valueOf(args[++i].toUpperCase()); break;
				default: monsterName.append(monsterName.length() > 0 ? " " : "").append(args[i]);
			}
		}
		PlayerProfile profile = maxed ? PlayerProfile.maxed()
			: PlayerProfile.fromJson(Files.readString(profilePath));

		LoadoutData data = new DataService().load();
		java.util.List<MonsterStats> hits = data.searchMonsters(monsterName.toString(), 1);
		if (hits.isEmpty())
		{
			return "No monster matches '" + monsterName + "'";
		}
		MonsterStats monster = hits.get(0);

		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(monster, profile.realLevels, profile.boostedLevels,
				profile.prayerUnlocks, profile.requirements, profile.ownedItems(),
				profile.owned.hashCode(), f2p, slayer, spellbook,
				java.util.Collections.emptySet(), lowRisk, riskBudget, antifirePotion,
				java.util.Collections.emptySet(), upgradeBudget, mode,
				results ->
				{
					out.set(results);
					done.countDown();
				});
			if (!done.await(180, TimeUnit.SECONDS))
			{
				return "Query timed out";
			}
			return render(monster, out.get(), lowRisk);
		}
		finally
		{
			service.shutdown();
		}
	}

	private static String render(MonsterStats monster,
		Map<CombatStyle, OptimizerService.StyleResult> results, int lowRisk)
	{
		StringBuilder sb = new StringBuilder("=== vs ").append(monster.label()).append(" ===\n");
		for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
		{
			OptimizerService.StyleResult result = results.get(style);
			sb.append("\n[").append(style).append("]\n");
			if (result == null || result.owned.isEmpty())
			{
				sb.append("  yours: no usable set\n");
			}
			else
			{
				DpsResult best = result.owned.get(0);
				sb.append(String.format("  yours: %.2f dps (max %d, %.0f%% acc)  assumes: %s%n",
					best.getDps(), best.getMaxHit(), best.getAccuracy() * 100, result.boostLabel));
				for (GearSlot slot : GearSlot.values())
				{
					GearItem item = best.getLoadout().get(slot);
					if (item != null)
					{
						sb.append(String.format("    %-7s %s%n", slot, item.label()));
					}
				}
				if (best.getSpellName() != null)
				{
					sb.append("    spell   ").append(best.getSpellName()).append('\n');
				}
				if (result.specWeapon != null)
				{
					sb.append(String.format("    spec    %s (avg %.0f dmg)%n",
						result.specWeapon.label(), result.specExpectedDamage));
				}
				if (result.incoming != null && result.incoming.protectPrayer != null)
				{
					sb.append(String.format("    incoming ~%.2f dps praying %s (~%.2f unprayed)%n",
						result.incoming.totalDps, result.incoming.protectPrayer,
						result.incoming.unprayedDps));
				}
				if (lowRisk >= 0)
				{
					PvpRisk.Assessment risk = PvpRisk.assess(best.getLoadout(), result.specWeapon, lowRisk);
					sb.append(String.format("    risk    %s gp (%d kept)%n",
						PvpRisk.formatGp(risk.riskGp), lowRisk));
				}
			}
			if (result != null && result.overallBest != null)
			{
				sb.append(String.format("  game best: %.2f dps  assumes: %s%n",
					result.overallBest.getDps(), result.gameBoostLabel));
			}
		}
		return sb.toString();
	}
}
