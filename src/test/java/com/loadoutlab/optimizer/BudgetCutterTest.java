package com.loadoutlab.optimizer;

import com.loadoutlab.data.*;
import com.loadoutlab.engine.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Budget Cutter (ISS-01M674YPVT5GFVSAT2PAK8RXS1, field report 2026-10-28: "the
 * vardorvis budget cutter option in the loadout lab plugin, gives gear that is
 * greater than the maximum 2m"; Andrew 2026-10-30: "1, 2 and 3a"): the Lightbearer
 * obeys the task's rules, a capped price never wraps back to free, and the price
 * rule counts an imbued item or slayer helmet at what it is made from.
 */
class BudgetCutterTest {
    private static LoadoutData data;
    private static MonsterStats vardorvis;
    private static SetRules budget;

    @BeforeAll
    static void load() {
        data = new DataService().load();
        vardorvis = data.searchMonsters("vardorvis", 1).get(0);
        budget = CombatAchievements.byId(493).rulesFor(List.of(vardorvis));
    }

    private static List<GearItem> rows(String name) {
        List<GearItem> found = new ArrayList<>();
        for (GearSlot slot : GearSlot.values()) {
            found.addAll(data.getGearItems(slot).stream().filter(g -> g.name.equals(name)).collect(Collectors.toList()));
        }
        assertFalse(found.isEmpty(), name + " is in the data");
        return found;
    }

    private static GearItem item(String name) { return rows(name).get(0); }

    private static GearItem item(String name, String version) {
        return rows(name).stream().filter(g -> g.version.equals(version)).findFirst()
            .orElseThrow(() -> new AssertionError(name + " " + version));
    }

    private static Loadout wearing(GearItem... items) {
        EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
        for (GearItem g : items) gear.put(g.slot, g);
        return new Loadout(gear);
    }

    private static OptimizationRequest request(MonsterStats mob, CombatStyle style, SetRules rules) {
        OptimizationRequest request = new OptimizationRequest(mob, style, PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
            CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 10);
        return rules == null ? request : request.withSetRules(rules);
    }

    /** A price cap just under the Lightbearer's, so no market move can make the ring fit. */
    private static SetRules tooDearForTheLightbearer() {
        double price = item("Lightbearer").getPriceOrZero();
        assertTrue(price > 0, "the Lightbearer has a price");
        return SetRules.of(List.of(new SetRules.Rule("price", "<=", price - 1)), Set.of(), null);
    }

    private static String describe(Loadout set) {
        return set.getGear().values().stream().map(g -> g.name + " " + (long) SetRules.of(g, "price"))
            .collect(Collectors.joining(", ")) + " = " + (long) SetRules.total(set, "price");
    }

    // REQ-1

    @Test
    @DisplayName("the Lightbearer comes into a set only when the set still keeps the request's rules")
    void swapObeysTheRules() {
        Loadout whip = wearing(item("Abyssal whip"));
        Loadout free = OptimizerService.lightbearerSwap(data, whip, List.of(request(vardorvis, CombatStyle.MELEE, null)), null);
        assertNotNull(free, "no rules: the ring comes in");
        assertEquals("Lightbearer", free.get(GearSlot.RING).name);
        assertNull(OptimizerService.lightbearerSwap(data, whip,
            List.of(request(vardorvis, CombatStyle.MELEE, tooDearForTheLightbearer())), null), "over a price rule");
        assertNull(OptimizerService.lightbearerSwap(data, whip,
            List.of(request(vardorvis, CombatStyle.MELEE, SetRules.of(List.of(), Set.of("Lightbearer"), null))), null), "banned by name");
        SetRules roomy = SetRules.of(List.of(new SetRules.Rule("price", "<=", 2_000_000_000d)), Set.of(), null);
        assertNotNull(OptimizerService.lightbearerSwap(data, whip, List.of(request(vardorvis, CombatStyle.MELEE, roomy)), null),
            "a rule it fits does not keep it out");
    }

    @Test
    @DisplayName("a group's Lightbearer stays out when any one member's rules break it")
    void rosterVariantObeysEveryMember() {
        Loadout whip = wearing(item("Abyssal whip"));
        MonsterStats other = data.searchMonsters("general graardor", 1).get(0);
        assertNotNull(OptimizerService.lightbearerVariant(data, whip,
            List.of(request(vardorvis, CombatStyle.MELEE, null), request(other, CombatStyle.MELEE, null)), null));
        assertNull(OptimizerService.lightbearerVariant(data, whip,
            List.of(request(vardorvis, CombatStyle.MELEE, null), request(other, CombatStyle.MELEE, tooDearForTheLightbearer())), null));
    }

    @Test
    @DisplayName("every set Vardorvis shows under Budget Cutter keeps the 2m, best in game and yours, in every style and mode")
    void everyShownSetKeepsTheRule() throws Exception {
        // Yours holds the ring and a spec weapon, so the swap has something to win.
        Map<Integer, Integer> bank = new HashMap<>();
        for (String name : List.of("Lightbearer", "Dragon claws", "Abyssal whip", "Berserker ring (i)", "Seers ring (i)", "Amulet of glory")) {
            bank.put(item(name).id, 1);
        }
        OwnedItems owned = new OwnedItems(bank, true);
        OptimizerService service = new OptimizerService(data);
        try {
            int mine = 0;
            for (String mode : List.of("MAX_DPS", "BALANCED", "TANKY")) {
                ComputeParams p = new ComputeParams();
                p.setRules = budget;
                p.optimizeMode = mode;
                var done = new CompletableFuture<Map<CombatStyle, OptimizerService.StyleResult>>();
                service.bestPerStyle(vardorvis, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED,
                    owned, 1, p, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
                    Collections.<Integer>emptySet(), done::complete);
                Map<CombatStyle, OptimizerService.StyleResult> styles = done.get(5, TimeUnit.MINUTES);
                assertEquals(3, styles.size(), mode);
                for (var e : styles.entrySet()) {
                    Loadout best = e.getValue().overallBest.getLoadout();
                    assertTrue(budget.holds(best), mode + " " + e.getKey() + " best in game: " + describe(best));
                    for (DpsResult yours : e.getValue().owned) {
                        mine++;
                        assertTrue(budget.holds(yours.getLoadout()), mode + " " + e.getKey() + " yours: " + describe(yours.getLoadout()));
                    }
                }
            }
            assertTrue(mine > 0, "the owned side was searched too");
        } finally {
            service.shutdown();
        }
    }

    // REQ-2

    @Test
    @DisplayName("a capped price plus anything costs int max, not a wrapped negative the card shows as free")
    void cappedCostStaysCapped() {
        GearItem obligator = item("Obligator", "Charged");
        assertEquals(Integer.MAX_VALUE, obligator.getPriceOrZero(), "its 2.4b price caps at load");
        GearItem amulet = item("Amulet of fury");
        assertTrue(amulet.getPriceOrZero() > 0);
        Loadout set = wearing(obligator, amulet);
        assertEquals(Integer.MAX_VALUE, set.cost);
        assertEquals(Integer.MAX_VALUE, new DpsCalculator().calculate(request(vardorvis, CombatStyle.MELEE, null), set).purchaseCost);
        assertFalse(budget.holds(set), "the price rule never read it as free");
    }

    @Test
    @DisplayName("Zulrah's forced recoil on a capped set stays at int max too")
    void recoilOnACappedSet() {
        MonsterStats zulrah = data.searchMonsters("zulrah", 1).get(0);
        // The free recoils out, so the priced Ring of recoil is the one that comes in.
        Set<Integer> free = new HashSet<>();
        for (String name : List.of("Ring of suffering (i)", "Ring of suffering", "Echo boots")) {
            for (GearItem row : rows(name)) free.add(row.id);
        }
        OptimizationRequest request = request(zulrah, CombatStyle.MAGIC, null).withExcludedItems(free);
        Loadout set = wearing(item("Trident of the Swamp", "Charged"), item("3rd Age druidic robe top"));
        DpsResult shown = new DpsCalculator().calculate(request, set);
        assertEquals(Integer.MAX_VALUE, shown.purchaseCost, "the robe top's price caps");
        DpsResult recoiled = new LoadoutOptimizer().ensureRequiredUtility(data, request, shown);
        assertEquals("Ring of recoil", recoiled.getLoadout().get(GearSlot.RING).name);
        assertTrue(recoiled.getLoadout().get(GearSlot.RING).getPriceOrZero() > 0, "a priced recoil, or no sum can wrap");
        assertEquals(Integer.MAX_VALUE, recoiled.purchaseCost);
    }

    // REQ-3

    @Test
    @DisplayName("an imbued ring counts as the ring it was made from, in every version")
    void imbuedRingsCountTheirBase() {
        for (String ring : List.of("Berserker ring", "Seers ring", "Archers ring", "Warrior ring", "Ring of the gods")) {
            int base = item(ring).getPriceOrZero();
            assertTrue(base > 0, ring + " has a price");
            List<GearItem> imbued = rows(ring + " (i)");
            assertTrue(imbued.size() >= 2, ring + ": the Soul Wars and Emir's Arena rows too");
            for (GearItem row : imbued) assertEquals(base, (int) SetRules.of(row, "price"), row.name + " " + row.version);
        }
    }

    @Test
    @DisplayName("an imbued item whose base has untradeable rows too still counts the tradeable one")
    void cheapestTradeableBaseCounts() {
        assertEquals(item("Ring of suffering", "Uncharged").getPriceOrZero(), (int) SetRules.of(rows("Ring of suffering (i)").get(0), "price"));
    }

    @Test
    @DisplayName("every slayer helmet, plain, imbued or recoloured, and the imbued black mask count as the cheapest black mask")
    void slayerHelmetsCountTheBlackMask() {
        int mask = rows("Black mask").stream().filter(g -> g.tradeable).mapToInt(GearItem::getPriceOrZero).min().getAsInt();
        assertTrue(mask > 0);
        for (String name : List.of("Slayer helmet", "Slayer helmet (i)", "Hydra slayer helmet", "Hydra slayer helmet (i)", "Black mask (i)")) {
            for (GearItem row : rows(name)) assertEquals(mask, (int) SetRules.of(row, "price"), row.name + " " + row.version);
        }
    }

    @Test
    @DisplayName("an untradeable with nothing tradeable behind it stays at 0 until the in-game read, and a priced item counts its own price")
    void everythingElseAsBefore() {
        for (String name : List.of("Fire cape", "Barrows gloves", "Crystal bow (i)", "Salve amulet(i)", "Imbued Guthix cape")) {
            assertEquals(0, (int) SetRules.of(item(name), "price"), name);
        }
        GearItem fury = item("Amulet of fury");
        assertEquals(fury.getPriceOrZero(), (int) SetRules.of(fury, "price"));
    }

    // REQ-4

    @Test
    @DisplayName("only the price rule reads it: budgets, risk and the card still see an imbued ring as unpriced")
    void marketPriceUntouched() {
        GearItem ring = rows("Berserker ring (i)").get(0);
        assertNull(ring.estimatedPrice);
        assertEquals(0, ring.getPriceOrZero());
        assertEquals(0, wearing(ring).cost);
    }
}
