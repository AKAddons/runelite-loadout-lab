package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A rule that names items finds its set (ISS-01M67DW3T4FRYM42ENDM6HAXGG, Andrew
 * 2026-10-30: "LL: find a set when a rule names items the score cut drops").
 * These search as the plugin does, with everything owned so Yours may reach anything.
 */
class RuleNamedItemsTest {
    private static final String HIDE = "has:hueycoatl hide";
    private static final List<Style> STYLES = List.of(Style.MELEE, Style.RANGED, Style.MAGIC);
    private static LoadoutData data;
    private static OwnedItems everything;

    @BeforeAll
    static void load() {
        data = new DataService().load();
        Map<Integer, Integer> bank = new HashMap<>();
        for (GearSlot slot : GearSlot.values()) for (Gear g : data.getGearItems(slot)) bank.put(g.id, 1);
        everything = new OwnedItems(bank, true);
    }

    private static Monster mob(String name) {
        return data.searchMonsters(name, 1).get(0);
    }

    private static SetRules rule(String stat, String op, double value) {
        return SetRules.of(List.of(new SetRules.Rule(stat, op, value)), Set.of(), null);
    }

    private static OptimizationRequest request(Monster mob, Style style) {
        return new OptimizationRequest(mob, style, PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
            null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 3);
    }

    private static void hasASet(Monster mob, SetRules rules, boolean superAntifire, List<Style> styles) throws Exception {
        ComputeParams p = new ComputeParams();
        p.setRules = rules;
        p.antifirePotion = superAntifire;
        OptimizerService service = new OptimizerService(data);
        Map<Style, OptimizerService.StyleResult> results;
        try {
            var done = new CompletableFuture<Map<Style, OptimizerService.StyleResult>>();
            service.bestPerStyle(mob, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED, everything, 1, p,
                Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), done::complete);
            results = done.get(5, TimeUnit.MINUTES);
        } finally {
            service.shutdown();
        }
        for (Style style : styles) {
            String label = mob.getName() + " " + style + " " + rules.getRules();
            OptimizerService.StyleResult result = results.get(style);
            assertNotNull(result, label);
            assertNotNull(result.overallBest, label + ": a best set");
            assertTrue(rules.holds(result.overallBest.getLoadout()), label + ": the best set holds, wears " + worn(result.overallBest));
            assertFalse(result.owned.isEmpty(), label + ": a set of yours");
            assertTrue(rules.holds(result.owned.get(0).getLoadout()), label + ": your set holds, wears " + worn(result.owned.get(0)));
        }
    }

    private static List<String> worn(DpsResult result) {
        List<String> names = new ArrayList<>();
        for (Gear item : result.getLoadout().getGear().values()) names.add(item.name);
        return names;
    }

    @Test
    @DisplayName("I'm your son: two Hueycoatl pieces on every style, gear that scores nothing for melee or magic")
    void hueycoatlHide() throws Exception {
        Monster hueycoatl = mob("the hueycoatl");
        hasASet(hueycoatl, CombatAchievements.byId(575).rulesFor(List.of(hueycoatl)), false, STYLES);
    }

    @Test
    @DisplayName("Antifire Protection with super antifire on: the anti-dragon shield still reaches the set")
    void antiDragonShield() throws Exception {
        Monster kbd = mob("king black dragon");
        hasASet(kbd, CombatAchievements.byId(166).rulesFor(List.of(kbd)), true, STYLES);
    }

    @Test
    @DisplayName("a salve amulet task keeps its amulet on every style")
    void salveAmulet() throws Exception {
        Monster bloat = mob("pestilent bloat");
        hasASet(bloat, CombatAchievements.byId(387).rulesFor(List.of(bloat)), false, STYLES);
    }

    @Test
    @DisplayName("the Lightbearer swap leaves a ring the rule asked for")
    void lightbearerKeepsTheRule() throws Exception {
        hasASet(mob("general graardor"), rule("has:berserker ring", ">=", 1), false, List.of(Style.MELEE));
    }

    @Test
    @DisplayName("the beam: Hueycoatl pieces on melee keep the 11.4 dps set, which falls to 10.6 without the reserve")
    void beamReserve() {
        Monster hueycoatl = mob("the hueycoatl");
        OptimizationRequest req = request(hueycoatl, Style.MELEE).withSetRules(CombatAchievements.byId(575).rulesFor(List.of(hueycoatl)));
        double dps = new LoadoutOptimizer().optimize(data, req).get(0).dps;
        assertTrue(dps > 11.2, "dps " + dps);
    }

    @Test
    @DisplayName("the pool: a rule's 3 best items join their slot, no weapon joins, and an at-most rule or a plain stat rule brings nothing")
    void thePool() {
        LoadoutOptimizer optimizer = new LoadoutOptimizer();
        OptimizationRequest free = request(mob("the hueycoatl"), Style.MELEE);
        OptimizationRequest hide = free.withSetRules(rule(HIDE, ">=", 2));
        for (GearSlot slot : List.of(GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.HANDS)) {
            List<Gear> base = optimizer.candidates(data, free, slot, LoadoutOptimizer.SLOT_LIMIT, null);
            List<Gear> added = new ArrayList<>(optimizer.candidates(data, hide, slot, LoadoutOptimizer.SLOT_LIMIT, null));
            assertTrue(added.containsAll(base), slot + " keeps its pool");
            added.removeAll(base);
            assertFalse(added.isEmpty(), slot + " gains hide");
            assertTrue(added.size() <= 3, slot + " gains " + added);
            for (Gear item : added) assertEquals(1, SetRules.of(item, HIDE), item.name);
            assertEquals(base, optimizer.candidates(data, free.withSetRules(rule(HIDE, "<", 1)), slot, LoadoutOptimizer.SLOT_LIMIT, null), slot + " under an at-most rule");
            assertEquals(base, optimizer.candidates(data, free.withSetRules(rule("prayer", ">=", 12)), slot, LoadoutOptimizer.SLOT_LIMIT, null), slot + " under a stat rule");
        }
        assertEquals(optimizer.candidates(data, free, GearSlot.WEAPON, LoadoutOptimizer.WEAPON_LIMIT, null),
            optimizer.candidates(data, hide, GearSlot.WEAPON, LoadoutOptimizer.WEAPON_LIMIT, null), "no weapon line joins");
    }

    @Test
    @DisplayName("two rules in one slot each keep their own items in the pool")
    void eachRuleInThePool() {
        LoadoutOptimizer optimizer = new LoadoutOptimizer();
        String other = "has:black d'hide";
        SetRules both = rule(HIDE, ">=", 1).plus(List.of(new SetRules.Rule(other, ">=", 1)));
        OptimizationRequest req = request(mob("the hueycoatl"), Style.MELEE).withSetRules(both);
        List<Gear> pool = optimizer.candidates(data, req, GearSlot.BODY, LoadoutOptimizer.SLOT_LIMIT, null);
        for (String stat : List.of(HIDE, other)) assertTrue(pool.stream().anyMatch(g -> SetRules.of(g, stat) > 0), stat);
    }

    private static Gear firstOf(GearSlot slot, String stat) {
        return data.getGearItems(slot).stream().filter(g -> SetRules.of(g, stat) > 0).findFirst().get();
    }

    @Test
    @DisplayName("namedProgress: a met rule counts the same however far past it, ranks above any unmet count, and plain or at-most rules count nothing")
    void namedProgress() {
        var two = new EnumMap<GearSlot, Gear>(GearSlot.class);
        two.put(GearSlot.BODY, firstOf(GearSlot.BODY, HIDE));
        two.put(GearSlot.LEGS, firstOf(GearSlot.LEGS, HIDE));
        Loadout worn = Loadout.adopting(two);
        assertEquals(12, rule(HIDE, ">=", 1).namedProgress(worn));
        assertEquals(12, rule(HIDE, ">=", 2).namedProgress(worn));
        assertEquals(12, rule(HIDE, ">", 0).namedProgress(worn));
        assertEquals(2, rule(HIDE, ">=", 3).namedProgress(worn));
        assertEquals(0, rule(HIDE, "<=", 1).namedProgress(worn));
        assertEquals(0, rule("prayer", ">=", 1).namedProgress(worn));
    }
}
