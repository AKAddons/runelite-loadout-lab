package com.loadoutlab.optimizer;

import com.loadoutlab.data.*;
import com.loadoutlab.engine.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-10-27 (ISS-01M5ZS534AA325AZ0DW97B5G33): the set wore the
 * Guthix d'hide boots kept in the costume room while the same-stats Saradomin
 * pair sat in the inventory.
 */
class PreferAtHandTest {
    private static LoadoutData data;

    private static int id(String name) {
        for (GearSlot slot : GearSlot.values()) {
            for (GearItem item : data.getGearItems(slot)) if (item.getNameLower().equals(name)) return item.getId();
        }
        throw new AssertionError(name);
    }

    private static GearItem boots(String mob, Set<Integer> atHand, Map<Integer, Integer> bank) throws Exception {
        MonsterStats monster = data.getMonsters().stream().filter(m -> m.getName().equalsIgnoreCase(mob))
            .findFirst().orElseThrow(AssertionError::new);
        var done = new CompletableFuture<Map<CombatStyle, OptimizerService.StyleResult>>();
        ServiceCalls.bestPerStyle(new OptimizerService(data), monster, PlayerLevels.MAXED, PlayerLevels.MAXED,
            new PrayerUnlocks(true, true, true, true, true), RequirementProfile.MAXED, new OwnedItems(bank, true, atHand), 1,
            false, false, "", Set.of(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Set.of(), 0, done::complete);
        return done.get(5, TimeUnit.MINUTES).get(CombatStyle.RANGED).owned.get(0).getLoadout().get(GearSlot.FEET);
    }

    @Test
    @DisplayName("a same-stats twin at hand beats the one in storage, except where the god matters")
    void twinAtHandWins() throws Exception {
        data = new DataService().load();
        int guthix = id("guthix d'hide boots"), sara = id("saradomin d'hide boots"), bow = id("magic shortbow");
        Map<Integer, Integer> bank = Map.of(guthix, 1, sara, 1, bow, 1, id("rune arrow"), 1000);
        assertEquals(sara, boots("Abyssal demon", Set.of(sara, bow), bank).getId(), "Saradomin is at hand");
        assertEquals(guthix, boots("Abyssal demon", Set.of(guthix, bow), bank).getId(), "and the other way round");
    }
}
