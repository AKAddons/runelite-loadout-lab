package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 1 of the combat achievement chips (ISS-01M5XA7NVZYGVR3EVTM05XHXKP):
 * rules over the worn set's totals, item bans and a weapon allow-list bind
 * the optimizer. Andrew, 2026-10-26: "give me a set with at least 12 prayer,
 * or at least 100 range def, etc."
 */
class SetRulesTest {
    private static LoadoutData data;

    @BeforeAll
    static void load() {
        data = new DataService().load();
    }

    private static MonsterStats mob(String name) {
        return data.getMonsters().stream().filter(m -> m.getName().equalsIgnoreCase(name))
            .findFirst().orElseThrow(AssertionError::new);
    }

    private static List<DpsResult> best(String monster, CombatStyle style, SetRules rules) {
        OptimizationRequest request = new OptimizationRequest(mob(monster), style, PlayerLevels.MAXED,
            PrayerBonuses.bestAvailable(PlayerLevels.MAXED, new PrayerUnlocks(true, true, true, true, true)),
            null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 5)
            .withSetRules(rules);
        return new LoadoutOptimizer().optimize(data, request);
    }

    private static double total(DpsResult r, String stat) {
        return SetRules.total(r.getLoadout(), stat);
    }

    private static String worn(DpsResult r) {
        StringBuilder s = new StringBuilder();
        for (GearItem g : r.getLoadout().getGear().values()) s.append(g.getNameLower()).append(", ");
        return s.toString();
    }

    @Test
    @DisplayName("a weight ceiling binds: under 20kg at the Shellbane gryphon, where the unrestricted melee set weighs more")
    void weightRuleBinds() {
        // Featherweight Fighter's 40kg does not bind on maxed gear (the best melee set weighs ~29kg), so a
        // 20kg ceiling proves the rule: the unrestricted set breaks it, the ruled one keeps it.
        DpsResult free = best("Shellbane gryphon", CombatStyle.MELEE, null).get(0);
        assertTrue(total(free, "weight") >= 20, "the rule must bind: " + total(free, "weight") + " " + worn(free));
        List<DpsResult> light = best("Shellbane gryphon", CombatStyle.MELEE,
            SetRules.of(List.of(new SetRules.Rule("weight", "<", 20)), Set.of(), null));
        assertFalse(light.isEmpty());
        for (DpsResult r : light) assertTrue(total(r, "weight") < 20, worn(r));
    }

    @Test
    @DisplayName("at least 12 prayer and at least 100 ranged defence hold on every result")
    void floorsHold() {
        for (DpsResult r : best("Vorkath", CombatStyle.RANGED,
            SetRules.of(List.of(new SetRules.Rule("prayer", ">=", 12), new SetRules.Rule("def_ranged", ">=", 100)), Set.of(), null))) {
            assertTrue(total(r, "prayer") >= 12, worn(r));
            assertTrue(total(r, "def_ranged") >= 100, worn(r));
        }
    }

    @Test
    @DisplayName("Prayer Smasher: only the Verac's flail as a weapon, and no recoil items")
    void weaponAllowListAndBans() {
        List<DpsResult> results = best("Kalphite Queen", CombatStyle.MELEE, SetRules.of(List.of(),
            Set.of("ring of recoil", "ring of suffering", "ring of suffering (i)", "echo boots"), Set.of("verac's flail")));
        assertFalse(results.isEmpty());
        for (DpsResult r : results) {
            String worn = worn(r);
            assertTrue(r.getLoadout().getWeapon().getNameLower().startsWith("verac's flail"), worn);
            assertFalse(worn.contains("recoil") || worn.contains("suffering") || worn.contains("echo boots"), worn);
        }
    }

    @Test
    @DisplayName("a rule no set can meet gives no results")
    void impossibleRule() {
        assertTrue(best("Vorkath", CombatStyle.RANGED,
            SetRules.of(List.of(new SetRules.Rule("prayer", ">=", 500)), Set.of(), null)).isEmpty());
    }

    @Test
    @DisplayName("no rules leaves the best set as it was")
    void noRulesNoChange() {
        assertEquals(worn(best("Vorkath", CombatStyle.RANGED, null).get(0)),
            worn(best("Vorkath", CombatStyle.RANGED, SetRules.of(List.of(), Set.of(), null)).get(0)));
    }
}
