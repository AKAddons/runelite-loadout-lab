package com.ll.optimizer;

import com.ll.data.*;
import com.ll.engine.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-10-27 (Maggot King's three forms, Trying to fit in plus
 * prayer >= 10): Best in game melee showed the kisten in the magic kit's
 * Ancestral robes, 4 prayer. The group kit built sets the set rules never saw.
 */
class GroupSetRulesTest {
    @Test
    @DisplayName("a group's kit-built sets hold the task's rules and the requirements")
    void groupKitHoldsTheRules() throws Exception {
        Tables data = new DataService().load();
        List<Monster> mobs = data.getMonsters().stream()
            .filter(m -> m.getName().equalsIgnoreCase("Maggot King")).collect(Collectors.toList());
        assertTrue(mobs.size() > 1, "Maggot King is a group");
        Rules rules = CombatAchievements.byId(641).getRules().plus(Rules.parse("prayer>=10"));
        ComputeParams p = new ComputeParams();
        p.spellbookLock = "";
        p.maxSwaps = 4;
        p.setRules = rules;
        var done = new CompletableFuture<Service.RosterResult>();
        new Service(data).bestPerStyleAcross(mobs, Levels.MAXED, Levels.MAXED, Prayers.ALL,
            RequirementProfile.MAXED, new Owned(Map.of(), true), 1, p, Map.of(), Set.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), Map.of(), Set.of(), done::complete);
        Service.RosterResult roster = done.get(5, TimeUnit.MINUTES);
        int checked = 0;
        for (int i = 0; i < roster.mobs.size(); i++) {
            for (var e : roster.pm.get(i).entrySet()) {
                Dps best = e.getValue().overallBest;
                if (best == null || !e.getValue().gkb) continue;
                checked++;
                assertTrue(rules.holds(best.getLoadout()), roster.mobs.get(i).getName() + " " + e.getKey()
                    + " prayer " + Rules.total(best.getLoadout(), "prayer"));
            }
        }
        assertTrue(checked > 0);
    }
}
