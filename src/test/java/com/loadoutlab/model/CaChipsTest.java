package com.loadoutlab.model;

import com.loadoutlab.data.CombatAchievements;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 2 of the combat achievement chips (ISS-01M5XC7XYMMEN3Q4QC6CGZFVPK):
 * the task data, the caTask and requirements params into the compute, the
 * page's task list, the style lock and manual magic with no weapon.
 */
class CaChipsTest {
    private static LoadoutData data;

    @BeforeAll
    static void load() {
        data = new DataService().load();
    }

    private static final class Link extends CompanionLink {
        private Map<String, Object> last;

        @Override
        public void publishPage(Map page) { last = page; }

        @Override
        public Map<String, Object> lastPage() { return last; }
    }

    @Test
    @DisplayName("the four tasks load with their rules")
    void tasksLoad() {
        SetRules smasher = CombatAchievements.byId(161).getRules();
        assertEquals(java.util.Set.of("verac's flail"), smasher.getWeapons());
        assertTrue(smasher.getBanned().contains("ring of recoil") && smasher.getBanned().contains("echo boots"));
        assertEquals(java.util.Set.of(CombatStyle.MAGIC), CombatAchievements.byId(72).getRules().getStyles());
        SetRules.Rule weight = CombatAchievements.byId(631).getRules().getRules().get(0);
        assertEquals(List.of("weight", "<", 40.0), List.of(weight.getStat(), weight.getOp(), weight.getValue()));
        SetRules fit = CombatAchievements.byId(641).getRules();
        assertEquals(18, fit.getWeapons().size());
        assertTrue(fit.isNoWeaponMagic());
        assertEquals("Prayer Smasher", CombatAchievements.tasksFor(mob("Kalphite Queen")).get(0).getName());
    }

    @Test
    @DisplayName("caTask and requirements reach the compute as one set-param each, undo in one step, and a new search clears the task")
    void paramsReachTheCompute() throws Exception {
        AtomicInteger computes = new AtomicInteger();
        AtomicReference<ComputeParams> last = new AtomicReference<>();
        PageState state = new PageState();
        Link link = new Link();
        CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
            computes.incrementAndGet();
            last.set(p);
        }, link);
        // One form by id: the name opens the two-phase group, and the chips are single-mob.
        assertTrue(engine.execute("select", Map.of("id", mob("Kalphite Queen").getId())));
        assertNull(last.get().setRules, "no task, no requirements: no rules");
        engine.onResults(mob("Kalphite Queen"), Map.of());
        javax.swing.SwingUtilities.invokeAndWait(() -> { }); // the page publishes on the EDT
        @SuppressWarnings("unchecked")
        List<Map> tasks = (List<Map>) link.lastPage().get("caTasks");
        assertEquals("Prayer Smasher", tasks.get(0).get("name"));

        int before = computes.get();
        assertTrue(engine.execute("set-param", Map.of("param", "caPicks", "value", "161")));
        assertEquals(before + 1, computes.get(), "one recompute");
        assertEquals(java.util.Set.of("verac's flail"), last.get().setRules.getWeapons());

        assertTrue(engine.execute("set-param", Map.of("param", "requirements", "value", "prayer>=12;nonsense;def_ranged>=100")));
        assertEquals(2, last.get().setRules.getRules().size(), "the bad part is ignored");
        assertEquals("prayer", last.get().setRules.getRules().get(0).getStat());
        assertEquals(java.util.Set.of("verac's flail"), last.get().setRules.getWeapons(), "task and requirements together");

        assertTrue(engine.execute("undo", Map.of()));
        assertEquals("", state.paramsNode().get("requirements"), "undone in one step");

        assertTrue(engine.execute("select", Map.of("query", "vorkath")));
        assertEquals("", state.paramsNode().get("caPicks"), "a new search clears the task");
    }

    private static MonsterStats mob(String name) {
        return data.getMonsters().stream().filter(m -> m.getName().equalsIgnoreCase(name))
            .findFirst().orElseThrow(AssertionError::new);
    }

    private static List<DpsResult> best(String monster, CombatStyle style, SetRules rules) {
        OptimizationRequest request = new OptimizationRequest(mob(monster), style, PlayerLevels.MAXED,
            PrayerBonuses.bestAvailable(PlayerLevels.MAXED, new PrayerUnlocks(true, true, true, true, true)),
            null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 3)
            .withSetRules(rules);
        return new LoadoutOptimizer().optimize(data, request);
    }

    @Test
    @DisplayName("Mage of the Ruins: the style lock leaves melee with no set and magic with one")
    void styleLock() {
        SetRules mage = CombatAchievements.byId(72).getRules();
        assertTrue(best("Crazy archaeologist", CombatStyle.MELEE, mage).isEmpty());
        assertFalse(best("Crazy archaeologist", CombatStyle.MAGIC, mage).isEmpty());
    }

    @Test
    @DisplayName("Trying to fit in: magic casts with no weapon, since no Vampyrium weapon is a staff")
    void manualMagic() {
        List<DpsResult> results = best("Maggot King", CombatStyle.MAGIC, CombatAchievements.byId(641).getRules());
        assertFalse(results.isEmpty());
        assertNull(results.get(0).getLoadout().getWeapon());
    }
}
