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
 * Ladder steps obey the task's rules (ISS-01M677A58TRKYM8HJB58SBRQBP, Andrew
 * 2026-10-30: "Yes, filter them"): under Budget Cutter a melee ladder at Vardorvis
 * offered a Scythe of Vitur at 1.09b. Prices come from the data, never literals,
 * so a market move cannot quietly make a case pass.
 */
class LadderRulesTest {
    private static LoadoutData data;
    private static Monster vardorvis;
    private final LoadoutOptimizer optimizer = new LoadoutOptimizer();

    @BeforeAll
    static void load() {
        data = new DataService().load();
        vardorvis = data.searchMonsters("vardorvis", 1).get(0);
    }

    private static Gear item(String name) {
        for (GearSlot slot : GearSlot.values()) {
            for (Gear g : data.getGearItems(slot)) if (g.name.equals(name)) return g;
        }
        throw new AssertionError(name + " is in the data");
    }

    private static Loadout wearing(Gear... items) {
        EnumMap<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
        for (Gear g : items) gear.put(g.slot, g);
        return new Loadout(gear);
    }

    private static OptimizationRequest request(Monster mob, SetRules rules) {
        OptimizationRequest request = new OptimizationRequest(mob, Style.MELEE, PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
            CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 10);
        return rules == null ? request : request.withSetRules(rules);
    }

    private static SetRules atMost(String stat, double value) {
        return SetRules.of(List.of(new SetRules.Rule(stat, "<=", value)), Set.of(), null);
    }

    private static DpsResult shown(OptimizationRequest request, Loadout set) {
        return new DpsCalculator().calculate(request, set);
    }

    private static List<OptimizerService.Rung> rungs(OptimizerService.Ladder ladder) {
        List<OptimizerService.Rung> all = new ArrayList<>(ladder.up);
        all.addAll(ladder.down);
        all.addAll(ladder.upMore);
        all.addAll(ladder.downMore);
        if (ladder.all != null) all.addAll(ladder.all);
        return all;
    }

    private static Set<String> names(List<DpsResult> steps, GearSlot slot) {
        return steps.stream().map(s -> s.getLoadout().get(slot).name).collect(Collectors.toSet());
    }

    private static DpsResult stepWearing(List<DpsResult> steps, Gear item) {
        return steps.stream().filter(s -> s.getLoadout().get(item.slot).id == item.id).findFirst().orElse(null);
    }

    @Test
    @DisplayName("a slot's steps under a task's rules all keep them, and the same search with no rules still offers the rest")
    void stepsKeepTheRules() {
        Gear whip = item("Abyssal whip");
        Gear fits = item("Warrior ring");
        Gear dear = item("Berserker ring");
        assertTrue(dear.getPriceOrZero() > fits.getPriceOrZero(), "the data still prices them apart");
        Loadout set = wearing(whip, item("Ring of shadows"));
        SetRules cap = atMost("price", whip.getPriceOrZero() + fits.getPriceOrZero());

        OptimizationRequest free = request(vardorvis, null);
        Set<String> offered = names(optimizer.slotSwaps(data, free, shown(free, set), GearSlot.RING), GearSlot.RING);
        assertTrue(offered.contains(dear.name), "no rules: every step is still offered");

        OptimizationRequest ruled = request(vardorvis, cap);
        List<DpsResult> steps = optimizer.slotSwaps(data, ruled, shown(ruled, set), GearSlot.RING);
        Set<String> kept = names(steps, GearSlot.RING);
        assertTrue(kept.contains(fits.name), "a ring that fits stays");
        assertFalse(kept.contains(dear.name), "over the cap");
        assertFalse(kept.contains("Berserker ring (i)"), "the imbued one counts its base");
        for (DpsResult step : steps) assertTrue(cap.holds(step.getLoadout()), step.getLoadout().get(GearSlot.RING).name);
    }

    @Test
    @DisplayName("a weapon step whose best shield breaks a rule stays on the ladder with a shield that keeps it")
    void weaponStepRepicksItsShield() {
        Gear whip = item("Abyssal whip");
        Loadout set = wearing(item("Dragon scimitar"));
        OptimizationRequest free = request(vardorvis, null);
        DpsResult best = stepWearing(optimizer.slotSwaps(data, free, shown(free, set), GearSlot.WEAPON), whip);
        assertNotNull(best, "the whip is a step");
        Gear bestShield = best.getLoadout().get(GearSlot.SHIELD);
        assertNotNull(bestShield, "a one-hander brings its best shield");
        double limit = SetRules.total(best.getLoadout(), "str") - 1;

        OptimizationRequest ruled = request(vardorvis, atMost("str", limit));
        DpsResult step = stepWearing(optimizer.slotSwaps(data, ruled, shown(ruled, set), GearSlot.WEAPON), whip);
        assertNotNull(step, "the whip stays on the ladder");
        assertTrue(SetRules.total(step.getLoadout(), "str") <= limit);
        assertNotEquals(bestShield.id, step.getLoadout().get(GearSlot.SHIELD) == null ? -1 : step.getLoadout().get(GearSlot.SHIELD).id);
    }

    @Test
    @DisplayName("a rule every shield in the pool breaks still leaves the one-handers, wearing what the set wears")
    void aRuleThePoolBreaksKeepsOneHanders() {
        OptimizationRequest ruled = request(vardorvis, atMost("weight", 0));
        List<DpsResult> steps = optimizer.slotSwaps(data, ruled, shown(ruled, wearing(item("Imbued tephra"))), GearSlot.WEAPON);
        assertFalse(steps.isEmpty(), "the weightless one-handers are still steps");
        for (DpsResult step : steps) assertNull(step.getLoadout().get(GearSlot.SHIELD), step.getLoadout().getWeapon().name);
    }

    @Test
    @DisplayName("a task that needs the worn shield keeps its one-hander steps, each with that shield, and loses the two-handers")
    void aRuleAboutTheShieldKeepsOneHanders() {
        Monster kbd = data.searchMonsters("king black dragon", 1).get(0);
        // The task's own terms: antifire up, so no dragonfire lock narrows the shield
        // pool. The search itself finds no set here today (the score cut drops the
        // shield), so this holds the ladder's side for when it does.
        OptimizationRequest ruled = request(kbd, CombatAchievements.byId(166).rulesFor(List.of(kbd))).withAntifirePotion(true);
        Gear antiDragon = item("Anti-dragon shield");
        List<DpsResult> steps = optimizer.slotSwaps(data, ruled, shown(ruled, wearing(item("Dragon scimitar"), antiDragon)), GearSlot.WEAPON);
        DpsResult whip = stepWearing(steps, item("Abyssal whip"));
        assertNotNull(whip, "the whip stays on the ladder");
        assertEquals(antiDragon.id, whip.getLoadout().get(GearSlot.SHIELD).id);
        assertNull(stepWearing(steps, item("Elder maul")), "a two-hander cannot wear the shield the task needs");
    }

    @Test
    @DisplayName("every ladder step Vardorvis shows under Budget Cutter keeps the 2m, and there is still a ladder")
    void everyShownStepKeepsTheRule() throws Exception {
        SetRules budget = CombatAchievements.byId(493).rulesFor(List.of(vardorvis));
        Map<Integer, Integer> bank = new HashMap<>();
        for (String name : List.of("Lightbearer", "Dragon claws", "Abyssal whip", "Berserker ring (i)", "Seers ring (i)")) {
            bank.put(item(name).id, 1);
        }
        ComputeParams p = new ComputeParams();
        p.setRules = budget;
        OptimizerService service = new OptimizerService(data);
        try {
            var done = new CompletableFuture<Map<Style, OptimizerService.StyleResult>>();
            service.bestPerStyle(vardorvis, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED,
                new OwnedItems(bank, true), 1, p, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
                Collections.<Integer>emptySet(), done::complete);
            int steps = 0;
            for (var style : done.get(5, TimeUnit.MINUTES).entrySet()) {
                for (var slot : style.getValue().ladder.entrySet()) {
                    for (OptimizerService.Rung rung : rungs(slot.getValue())) {
                        assertNotNull(rung.set, "a solo step carries its set");
                        steps++;
                        assertTrue(budget.holds(rung.set.getLoadout()), style.getKey() + " " + slot.getKey() + " " + rung.item.name
                            + " " + (long) SetRules.total(rung.set.getLoadout(), "price"));
                    }
                }
            }
            assertTrue(steps > 0, "the owned side's ladder is still there");
        } finally {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("a group step one member's rules refuse is dropped for the group, not averaged in as a 0")
    void groupDropsARefusedStep() {
        Gear whip = item("Abyssal whip");
        Gear glory = item("Amulet of glory");
        Gear shadows = item("Ring of shadows");
        Gear dear = item("Berserker ring");
        Gear cheap = item("Warrior ring");
        assertTrue(glory.getPriceOrZero() > 0 && cheap.getPriceOrZero() + glory.getPriceOrZero() < dear.getPriceOrZero(), "the data still prices them apart");
        // The dear ring fits the plain set exactly; on the set with an amulet too it does not.
        SetRules cap = atMost("price", whip.getPriceOrZero() + dear.getPriceOrZero());
        // Owned, so a halved step would still show, as a step down.
        List<OptimizerService.Rung> steps = groupRingSteps(cap, new OwnedItems(Map.of(dear.id, 1, cheap.id, 1), true),
            wearing(whip, shadows), wearing(whip, shadows, glory));
        assertFalse(steps.isEmpty(), "the group's ring ladder is still there");
        for (OptimizerService.Rung step : steps) {
            assertNotEquals(dear.name, step.item.name, "refused on the second member's set");
            assertTrue(step.perMob[0] > 0 && step.perMob[1] > 0, step.item.name + " has a number for every member");
        }
    }

    @Test
    @DisplayName("a group step a member already wears is no refusal: it stays for the others")
    void groupKeepsAStepAMemberWears() {
        Gear whip = item("Abyssal whip");
        Gear shadows = item("Ring of shadows");
        Gear ring = item("Warrior ring");
        SetRules cap = atMost("price", whip.getPriceOrZero() + ring.getPriceOrZero());
        List<OptimizerService.Rung> steps = groupRingSteps(cap, new OwnedItems(Map.of(ring.id, 1), true),
            wearing(whip, shadows), wearing(whip, shadows), wearing(whip, ring));
        assertTrue(steps.stream().anyMatch(step -> step.item.id == ring.id), "the two in Ring of shadows can still take it");
    }

    // The ring steps of a group strip: one member per set, all under the rules.
    private static List<OptimizerService.Rung> groupRingSteps(SetRules rules, OwnedItems owned, Loadout... sets) {
        String[] names = {"vardorvis", "general graardor", "commander zilyana"};
        List<Monster> mobs = new ArrayList<>();
        List<OptimizationRequest> reqs = new ArrayList<>();
        List<List<DpsResult>> bests = new ArrayList<>();
        List<Map<Style, OptimizerService.StyleResult>> perMob = new ArrayList<>();
        double[] weights = new double[sets.length];
        for (int j = 0; j < sets.length; j++) {
            mobs.add(data.searchMonsters(names[j], 1).get(0));
            reqs.add(request(mobs.get(j), rules));
            DpsResult set = shown(reqs.get(j), sets[j]);
            bests.add(List.of(set));
            perMob.add(Map.of(Style.MELEE, result(set)));
            weights[j] = 1;
        }
        var side = new OptimizerService.SharedSide(reqs, bests, weights);
        side.shared = sets[0];
        OptimizerService service = new OptimizerService(data);
        try {
            var ctx = service.buildContext(PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED, owned, 1,
                new ComputeParams(), Map.of(), Set.of(), Map.of(), Map.of(), Map.of(), Set.of());
            OptimizerService.Ladder rings = service.groupLadderFor(ctx, side, mobs, perMob, Style.MELEE).get(GearSlot.RING);
            return rings == null ? List.of() : rungs(rings);
        } finally {
            service.shutdown();
        }
    }

    private static OptimizerService.StyleResult result(DpsResult set) {
        return new OptimizerService.StyleResult(List.of(set), set, null, null, "", "", null, null, List.of(), List.of(), false, false);
    }
}
