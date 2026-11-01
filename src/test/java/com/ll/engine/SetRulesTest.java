package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
    private static Tables data;

    @BeforeAll
    static void load() {
        data = new DataService().load();
    }

    private static Monster mob(String name) {
        return data.getMonsters().stream().filter(m -> m.getName().equalsIgnoreCase(name))
            .findFirst().orElseThrow(AssertionError::new);
    }

    private static List<Dps> best(String monster, Style style, Rules rules) {
        Req request = new Req(mob(monster), style, Levels.MAXED,
            PrayerBonuses.bestAvailable(Levels.MAXED, new Prayers(true, true, true, true, true)),
            null, 0, CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, RequirementProfile.MAXED, 5)
            .withSetRules(rules);
        return new LoadoutOptimizer().optimize(data, request);
    }

    private static double total(Dps r, String stat) {
        return Rules.total(r.getLoadout(), stat);
    }

    private static String worn(Dps r) {
        StringBuilder s = new StringBuilder();
        for (Gear g : r.getLoadout().getGear().values()) s.append(g.getLname()).append(", ");
        return s.toString();
    }

    @Test
    @DisplayName("a weight ceiling binds: under 20kg at the Shellbane gryphon, where the unrestricted melee set weighs more")
    void weightRuleBinds() {
        // Featherweight Fighter's 40kg does not bind on maxed gear (the best melee set weighs ~29kg), so a
        // 20kg ceiling proves the rule: the unrestricted set breaks it, the ruled one keeps it.
        Dps free = best("Shellbane gryphon", Style.MELEE, null).get(0);
        assertTrue(total(free, "weight") >= 20, "the rule must bind: " + total(free, "weight") + " " + worn(free));
        List<Dps> light = best("Shellbane gryphon", Style.MELEE,
            Rules.of(List.of(new Rules.Rule("weight", "<", 20)), Set.of(), null));
        assertFalse(light.isEmpty());
        for (Dps r : light) assertTrue(total(r, "weight") < 20, worn(r));
    }

    @Test
    @DisplayName("at least 12 prayer and at least 100 ranged defence hold on every result")
    void floorsHold() {
        for (Dps r : best("Vorkath", Style.RANGED,
            Rules.of(List.of(new Rules.Rule("prayer", ">=", 12), new Rules.Rule("def_ranged", ">=", 100)), Set.of(), null))) {
            assertTrue(total(r, "prayer") >= 12, worn(r));
            assertTrue(total(r, "def_ranged") >= 100, worn(r));
        }
    }

    @Test
    @DisplayName("Prayer Smasher: only the Verac's flail as a weapon, and no recoil items")
    void weaponAllowListAndBans() {
        List<Dps> results = best("Kalphite Queen", Style.MELEE, Rules.of(List.of(),
            Set.of("ring of recoil", "ring of suffering", "ring of suffering (i)", "echo boots"), Set.of("verac's flail")));
        assertFalse(results.isEmpty());
        for (Dps r : results) {
            String worn = worn(r);
            assertTrue(r.getLoadout().getWeapon().getLname().startsWith("verac's flail"), worn);
            assertFalse(worn.contains("recoil") || worn.contains("suffering") || worn.contains("echo boots"), worn);
        }
    }

    @Test
    @DisplayName("a rule no set can meet gives no results")
    void impossibleRule() {
        assertTrue(best("Vorkath", Style.RANGED,
            Rules.of(List.of(new Rules.Rule("prayer", ">=", 500)), Set.of(), null)).isEmpty());
    }

    @Test
    @DisplayName("no rules leaves the best set as it was")
    void noRulesNoChange() {
        assertEquals(worn(best("Vorkath", Style.RANGED, null).get(0)),
            worn(best("Vorkath", Style.RANGED, Rules.of(List.of(), Set.of(), null)).get(0)));
    }
}
