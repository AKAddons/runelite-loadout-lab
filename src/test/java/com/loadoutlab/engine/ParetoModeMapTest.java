package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-7 phase-2 opener: the MODE-NECESSITY MAP. Runs the naive (mode-free)
 * frontier against the beam over the golden conditional monsters, both
 * sides consuming the beam's OWN candidate pools so eligibility is
 * identical and only the SEARCH differs.
 *
 * <p>Where the naive frontier LOSES to the beam, a multiplicative
 * conditional defeated domination pruning (a salve neck has no attack
 * bonus, so it is dominated and pruned before evaluation ever sees its
 * multiplier) - those monsters are exactly the empirical spec for the
 * mode-enumeration layer. Where it ties, no mode is needed. It must
 * never WIN on same pools by more than float noise unless the beam's
 * width cut missed (also worth knowing - cataloged separately).
 */
class ParetoModeMapTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static final String[] MONSTERS = {
		"goblin", "ankou", "aberrant spectre", "vorkath", "king black dragon",
		"revenant demon", "gargoyle", "dust devil", "kurask", "grey golem",
		"corporeal beast", "araxxor", "hellhound", "general graardor", "callisto",
	};

	@Test
	@DisplayName("mode map: catalog where naive frontier loses to the beam (conditionals)")
	void modeMap()
	{
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		DpsCalculator calculator = new DpsCalculator();
		Map<String, String> map = new LinkedHashMap<>();
		List<String> unexpectedTies = new ArrayList<>();

		for (String name : MONSTERS)
		{
			MonsterStats monster = data.searchMonsters(name, 10).stream()
				.filter(m -> m.getName().equalsIgnoreCase(name))
				.findFirst().orElse(data.searchMonsters(name, 1).get(0));
			OptimizationRequest request = TestRequests.of(monster, CombatStyle.MELEE,
				PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null,
				0, CandidateMode.ALL_STANDARD, true, false,
				OwnedItems.EMPTY, 1);

			DpsResult beamBest = optimizer.optimize(data, request).get(0);
			LoadoutOptimizer.CandidatePools pools = optimizer.preparePools(data, request);

			double dpBest = 0;
			for (GearItem weapon : pools.weapons)
			{
				for (WeaponStyles.MeleeStyle style : WeaponStyles.melee(weapon))
				{
					ParetoFrontier frontier = new ParetoFrontier();
					for (Map.Entry<GearSlot, List<GearItem>> slot : pools.slotCandidates.entrySet())
					{
						if (slot.getKey() == GearSlot.SHIELD && weapon.isTwoHanded())
						{
							continue;
						}
						List<GearItem> candidates = new ArrayList<>();
						for (GearItem item : slot.getValue())
						{
							if (item != null)
							{
								candidates.add(item);
							}
						}
						String type = style.attackType;
						frontier.fold(slot.getKey(), candidates,
							item -> item.getOffensive().getAttackBonus(type),
							item -> item.getBonuses().getStrength());
					}
					for (ParetoFrontier.State state : frontier.states())
					{
						Map<GearSlot, GearItem> gear = new EnumMap<>(state.picks);
						gear.put(GearSlot.WEAPON, weapon);
						DpsResult result = calculator.calculate(request, new Loadout(gear));
						if (result != null && result.getDps() > dpBest)
						{
							dpBest = result.getDps();
						}
					}
				}
			}

			double beam = beamBest.getDps();
			String verdict = Math.abs(dpBest - beam) < 1e-6 ? "TIE"
				: dpBest < beam ? String.format("NEEDS MODE (-%.2f%%)", 100 * (beam - dpBest) / beam)
				: String.format("BEAM MISS (+%.2f%%)", 100 * (dpBest - beam) / beam);
			map.put(monster.getName(), String.format("beam %.3f dp %.3f  %s", beam, dpBest, verdict));
		}

		StringBuilder report = new StringBuilder("\n=== D-7 mode-necessity map (melee, game-best pools) ===\n");
		map.forEach((m, v) -> report.append(String.format("  %-22s %s%n", m, v)));
		System.out.println(report);

		// Diagnostic only (first run 2026-07-24): 6 TIEs; NEEDS MODE =
		// ankou -11.4 / spectre -12.7 (salve), vorkath -6.9 (salve+DHL),
		// rev demon -14.6 (wildy weapons/avarice), goblin -0.41 (SOLVED by
		// probe: Dual macuahuitl + Blood Moon SET - near-zero raw bonuses
		// are dominated out, then the set bonus wins in calculate(); SET
		// COMPLETIONS are the second mode category alongside item
		// multipliers - void/crystal/blood moon/inquisitor). Apparent BEAM
		// MISSES still suspected DP constraint-cheats: KBD +10.5 = the
		// dragonfire-shield rule the fold does not honor yet; corp +2.0 and
		// kurask +1.8 unverified. Hard assertion deferred until the fold
		// carries the beam's full constraint set.
		assertFalse(map.isEmpty());
	}
}
