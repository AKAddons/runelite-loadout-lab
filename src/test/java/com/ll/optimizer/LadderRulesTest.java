package com.ll.optimizer;

import com.ll.data.*;
import com.ll.engine.*;
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
    private static Tables data;
    private static Monster vardorvis;
    private final LoadoutOptimizer optimizer = new LoadoutOptimizer();

    @BeforeAll
    static void load() {
        data = new DataService().load();
        vardorvis = data.searchMonsters("vardorvis", 1).get(0);
    }

    private static Gear item(String name) {
        for (Slot slot : Slot.values()) {
            for (Gear g : data.getGearItems(slot)) if (g.name.equals(name)) return g;
        }
        throw new AssertionError(name + " is in the data");
    }

    private static Kit wearing(Gear... items) {
        EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
        for (Gear g : items) gear.put(g.slot, g);
        return new Kit(gear);
    }

    private static Req request(Monster mob, Rules rules) {
        Req request = new Req(mob, Style.MELEE, Levels.MAXED, PrayerBonuses.NONE, null, 0,
            CandidateMode.ALL_STANDARD, false, false, Owned.EMPTY, RequirementProfile.MAXED, 10);
        return rules == null ? request : request.withSetRules(rules);
    }

    private static Rules atMost(String stat, double value) {
        return Rules.of(List.of(new Rules.Rule(stat, "<=", value)), Set.of(), null);
    }

    private static Dps shown(Req request, Kit set) {
        return new Calc().calculate(request, set);
    }

    private static List<Service.Step> rungs(Service.Ladder ladder) {
        List<Service.Step> all = new ArrayList<>(ladder.up);
        all.addAll(ladder.down);
        all.addAll(ladder.upMore);
        all.addAll(ladder.downMore);
        if (ladder.all != null) all.addAll(ladder.all);
        return all;
    }

    private static Set<String> names(List<Dps> steps, Slot slot) {
        return steps.stream().map(s -> s.getLoadout().get(slot).name).collect(Collectors.toSet());
    }

    private static Dps stepWearing(List<Dps> steps, Gear item) {
        return steps.stream().filter(s -> s.getLoadout().get(item.slot).id == item.id).findFirst().orElse(null);
    }

    @Test
    @DisplayName("a slot's steps under a task's rules all keep them, and the same search with no rules still offers the rest")
    void stepsKeepTheRules() {
        Gear whip = item("Abyssal whip");
        Gear fits = item("Warrior ring");
        Gear dear = item("Berserker ring");
        assertTrue(dear.getPriceOrZero() > fits.getPriceOrZero(), "the data still prices them apart");
        Kit set = wearing(whip, item("Ring of shadows"));
        Rules cap = atMost("price", whip.getPriceOrZero() + fits.getPriceOrZero());

        Req free = request(vardorvis, null);
        Set<String> offered = names(optimizer.slotSwaps(data, free, shown(free, set), Slot.RING), Slot.RING);
        assertTrue(offered.contains(dear.name), "no rules: every step is still offered");

        Req ruled = request(vardorvis, cap);
        List<Dps> steps = optimizer.slotSwaps(data, ruled, shown(ruled, set), Slot.RING);
        Set<String> kept = names(steps, Slot.RING);
        assertTrue(kept.contains(fits.name), "a ring that fits stays");
        assertFalse(kept.contains(dear.name), "over the cap");
        assertFalse(kept.contains("Berserker ring (i)"), "the imbued one counts its base");
        for (Dps step : steps) assertTrue(cap.holds(step.getLoadout()), step.getLoadout().get(Slot.RING).name);
    }

    @Test
    @DisplayName("a weapon step whose best shield breaks a rule stays on the ladder with a shield that keeps it")
    void weaponStepRepicksItsShield() {
        Gear whip = item("Abyssal whip");
        Kit set = wearing(item("Dragon scimitar"));
        Req free = request(vardorvis, null);
        Dps best = stepWearing(optimizer.slotSwaps(data, free, shown(free, set), Slot.WEAPON), whip);
        assertNotNull(best, "the whip is a step");
        Gear bestShield = best.getLoadout().get(Slot.SHIELD);
        assertNotNull(bestShield, "a one-hander brings its best shield");
        double limit = Rules.total(best.getLoadout(), "str") - 1;

        Req ruled = request(vardorvis, atMost("str", limit));
        Dps step = stepWearing(optimizer.slotSwaps(data, ruled, shown(ruled, set), Slot.WEAPON), whip);
        assertNotNull(step, "the whip stays on the ladder");
        assertTrue(Rules.total(step.getLoadout(), "str") <= limit);
        assertNotEquals(bestShield.id, step.getLoadout().get(Slot.SHIELD) == null ? -1 : step.getLoadout().get(Slot.SHIELD).id);
    }

    @Test
    @DisplayName("a rule every shield in the pool breaks still leaves the one-handers, wearing what the set wears")
    void aRuleThePoolBreaksKeepsOneHanders() {
        Req ruled = request(vardorvis, atMost("weight", 0));
        List<Dps> steps = optimizer.slotSwaps(data, ruled, shown(ruled, wearing(item("Imbued tephra"))), Slot.WEAPON);
        assertFalse(steps.isEmpty(), "the weightless one-handers are still steps");
        for (Dps step : steps) assertNull(step.getLoadout().get(Slot.SHIELD), step.getLoadout().getWeapon().name);
    }

    @Test
    @DisplayName("a task that needs the worn shield keeps its one-hander steps, each with that shield, and loses the two-handers")
    void aRuleAboutTheShieldKeepsOneHanders() {
        Monster kbd = data.searchMonsters("king black dragon", 1).get(0);
        // The task's own terms: antifire up, so no dragonfire lock narrows the shield
        // pool. The search itself finds no set here today (the score cut drops the
        // shield), so this holds the ladder's side for when it does.
        Req ruled = request(kbd, CombatAchievements.byId(166).rulesFor(List.of(kbd))).withAntifirePotion(true);
        Gear antiDragon = item("Anti-dragon shield");
        List<Dps> steps = optimizer.slotSwaps(data, ruled, shown(ruled, wearing(item("Dragon scimitar"), antiDragon)), Slot.WEAPON);
        Dps whip = stepWearing(steps, item("Abyssal whip"));
        assertNotNull(whip, "the whip stays on the ladder");
        assertEquals(antiDragon.id, whip.getLoadout().get(Slot.SHIELD).id);
        assertNull(stepWearing(steps, item("Elder maul")), "a two-hander cannot wear the shield the task needs");
    }

    @Test
    @DisplayName("every ladder step Vardorvis shows under Budget Cutter keeps the 2m, and there is still a ladder")
    void everyShownStepKeepsTheRule() throws Exception {
        Rules budget = CombatAchievements.byId(493).rulesFor(List.of(vardorvis));
        Map<Integer, Integer> bank = new HashMap<>();
        for (String name : List.of("Lightbearer", "Dragon claws", "Abyssal whip", "Berserker ring (i)", "Seers ring (i)")) {
            bank.put(item(name).id, 1);
        }
        ComputeParams p = new ComputeParams();
        p.setRules = budget;
        Service service = new Service(data);
        try {
            var done = new CompletableFuture<Map<Style, Service.Pick>>();
            service.bestPerStyle(vardorvis, Levels.MAXED, Levels.MAXED, Prayers.ALL, RequirementProfile.MAXED,
                new Owned(bank, true), 1, p, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
                Collections.<Integer>emptySet(), done::complete);
            int steps = 0;
            for (var style : done.get(5, TimeUnit.MINUTES).entrySet()) {
                for (var slot : style.getValue().ladder.entrySet()) {
                    for (Service.Step rung : rungs(slot.getValue())) {
                        assertNotNull(rung.set, "a solo step carries its set");
                        steps++;
                        assertTrue(budget.holds(rung.set.getLoadout()), style.getKey() + " " + slot.getKey() + " " + rung.item.name
                            + " " + (long) Rules.total(rung.set.getLoadout(), "price"));
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
        Rules cap = atMost("price", whip.getPriceOrZero() + dear.getPriceOrZero());
        // Owned, so a halved step would still show, as a step down.
        List<Service.Step> steps = groupRingSteps(cap, new Owned(Map.of(dear.id, 1, cheap.id, 1), true),
            wearing(whip, shadows), wearing(whip, shadows, glory));
        assertFalse(steps.isEmpty(), "the group's ring ladder is still there");
        for (Service.Step step : steps) {
            assertNotEquals(dear.name, step.item.name, "refused on the second member's set");
            assertTrue(step.pm[0] > 0 && step.pm[1] > 0, step.item.name + " has a number for every member");
        }
    }

    @Test
    @DisplayName("a group step a member already wears is no refusal: it stays for the others")
    void groupKeepsAStepAMemberWears() {
        Gear whip = item("Abyssal whip");
        Gear shadows = item("Ring of shadows");
        Gear ring = item("Warrior ring");
        Rules cap = atMost("price", whip.getPriceOrZero() + ring.getPriceOrZero());
        List<Service.Step> steps = groupRingSteps(cap, new Owned(Map.of(ring.id, 1), true),
            wearing(whip, shadows), wearing(whip, shadows), wearing(whip, ring));
        assertTrue(steps.stream().anyMatch(step -> step.item.id == ring.id), "the two in Ring of shadows can still take it");
    }

    // The ring steps of a group strip: one member per set, all under the rules.
    private static List<Service.Step> groupRingSteps(Rules rules, Owned owned, Kit... sets) {
        String[] names = {"vardorvis", "general graardor", "commander zilyana"};
        List<Monster> mobs = new ArrayList<>();
        List<Req> rq = new ArrayList<>();
        List<List<Dps>> bests = new ArrayList<>();
        List<Map<Style, Service.Pick>> pm = new ArrayList<>();
        double[] weights = new double[sets.length];
        for (int j = 0; j < sets.length; j++) {
            mobs.add(data.searchMonsters(names[j], 1).get(0));
            rq.add(request(mobs.get(j), rules));
            Dps set = shown(rq.get(j), sets[j]);
            bests.add(List.of(set));
            pm.add(Map.of(Style.MELEE, result(set)));
            weights[j] = 1;
        }
        var side = new Service.SharedSide(rq, bests, weights);
        side.shared = sets[0];
        Service service = new Service(data);
        try {
            var ctx = service.buildContext(Levels.MAXED, Levels.MAXED, Prayers.ALL, RequirementProfile.MAXED, owned, 1,
                new ComputeParams(), Map.of(), Set.of(), Map.of(), Map.of(), Map.of(), Set.of());
            Service.Ladder rings = service.groupLadderFor(ctx, side, mobs, pm, Style.MELEE).get(Slot.RING);
            return rings == null ? List.of() : rungs(rings);
        } finally {
            service.shutdown();
        }
    }

    private static Service.Pick result(Dps set) {
        return new Service.Pick(List.of(set), set, null, null, "", "", null, null, List.of(), List.of(), false, false);
    }
}
