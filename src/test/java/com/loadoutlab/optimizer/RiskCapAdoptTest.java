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
 * Field report 2026-10-26 (Crazy archaeologist, Wilderness, 75k cap,
 * Protect Item): Yours showed a 14.3m melee set. The search kept to the cap;
 * the swaps strip's adopt-owned-upgrades step did not.
 */
class RiskCapAdoptTest {
    private static final String[] BANK = {"Berserker helm", "Fire cape", "Amulet of torture", "Rada's blessing 4",
        "Noxious halberd", "Bandos chestplate", "Proselyte tasset", "Ferocious gloves", "Primordial boots", "Ring of shadows",
        "Crystal helm", "Ardougne cloak 4", "Amulet of glory", "Bow of faerdhinen", "Crystal body", "Crystal legs",
        "Ancient bracers", "Aranea boots", "Archers ring (i)", "Ancient mitre", "Imbued zamorak cape", "Occult necklace",
        "Trident of the seas", "Ahrim's robetop", "Ancient wyvern shield", "Ahrim's robeskirt", "Tormented bracelet",
        "Mystic boots", "Lunar ring", "Rune gloves", "White boots", "Mixed hide top", "Rune defender", "Explorer's ring 4",
        "Guthix cape", "Mythical cape", "Mixed hide cape", "Climbing boots", "Leather boots", "Iron full helm"};

    @Test
    @DisplayName("every style's shown set stays within a 75k Wilderness risk cap")
    void yoursStaysWithinTheCap() throws Exception {
        LoadoutData data = new DataService().load();
        Map<Integer, Integer> bank = new HashMap<>();
        for (String name : BANK) {
            for (GearSlot slot : GearSlot.values()) {
                for (Gear item : data.getGearItems(slot)) {
                    if (item.getNameLower().startsWith(name.toLowerCase())) bank.put(item.getId(), 1);
                }
            }
        }
        Monster mob = data.getMonsters().stream()
            .filter(m -> m.getName().equalsIgnoreCase("Crazy archaeologist")).findFirst().orElseThrow(AssertionError::new);
        var done = new CompletableFuture<Map<Style, OptimizerService.StyleResult>>();
        ServiceCalls.bestPerStyle(new OptimizerService(data), mob, PlayerLevels.MAXED, PlayerLevels.MAXED,
            new PrayerUnlocks(true, true, true, true, true), RequirementProfile.MAXED, new OwnedItems(bank, true), 1,
            false, false, "", Set.of(), 4, 75_000, false, Set.of(), 0, done::complete);
        Map<Style, OptimizerService.StyleResult> results = done.get(5, TimeUnit.MINUTES);
        for (Style style : Style.concreteValues()) {
            List<DpsResult> owned = results.get(style).owned;
            assertFalse(owned.isEmpty(), style + " has a set");
            long risk = PvpRisk.riskGp(owned.get(0).getLoadout(), null, 4);
            assertTrue(risk <= 75_000, style + " risks " + risk + " gp");
        }
    }
}
