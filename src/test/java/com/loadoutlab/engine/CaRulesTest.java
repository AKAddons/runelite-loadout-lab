package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import com.loadoutlab.model.PageState;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every combat achievement the set rules can express (ISS-01M5ZQH7H4ZS95N6274QQ7EAJB,
 * Andrew 2026-10-27: "Build group 1 plus the new rule kinds (groups 2-4); skip the
 * partial fits."): the data names real mobs and items, and each new rule kind
 * shapes the search.
 */
class CaRulesTest {
    private static LoadoutData data;

    @BeforeAll
    static void load() {
        data = new DataService().load();
    }

    private static Monster mob(String name) {
        return data.getMonsters().stream().filter(m -> m.getName().equalsIgnoreCase(name))
            .findFirst().orElseThrow(() -> new AssertionError(name));
    }

    private static Monster mob(String name, String version) {
        return data.getMonsters().stream().filter(m -> m.getName().equalsIgnoreCase(name)
            && Names.lower(m.getVersion()).contains(version)).findFirst().orElseThrow(() -> new AssertionError(name));
    }

    private static List<DpsResult> best(Monster monster, Style style, int task) {
        OptimizationRequest request = new OptimizationRequest(monster, style, PlayerLevels.MAXED,
            PrayerBonuses.bestAvailable(PlayerLevels.MAXED, new PrayerUnlocks(true, true, true, true, true)),
            null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 3)
            .withSetRules(CombatAchievements.byId(task).rulesFor(List.of(monster)));
        return new LoadoutOptimizer().optimize(data, request);
    }

    private static Loadout set(Monster monster, Style style, int task) {
        List<DpsResult> results = best(monster, style, task);
        assertFalse(results.isEmpty(), monster.getName() + " " + style + " has a set under task " + task);
        return results.get(0).getLoadout();
    }

    @Test
    @DisplayName("every task names mobs and items the data has")
    void dataIsReal() {
        Set<String> mobs = new HashSet<>(), items = new HashSet<>(), categories = new HashSet<>();
        data.getMonsters().forEach(m -> mobs.add(m.getNameLower()));
        for (GearSlot slot : GearSlot.values()) {
            for (Gear item : data.getGearItems(slot)) {
                items.add(item.getNameLower());
                categories.add(item.getCategoryLower());
            }
        }
        assertTrue(CombatAchievements.all().size() >= 40);
        for (CombatAchievements.Task t : CombatAchievements.all()) {
            for (String m : t.getMobs()) assertTrue(mobs.contains(m), t.getName() + ": mob " + m);
            assertFalse(data.getMonsters().stream().noneMatch(t::isFor), t.getName() + " matches some mob");
            SetRules r = t.getRules();
            if (r.getWeapons() != null) for (String w : r.getWeapons()) assertTrue(items.contains(w), t.getName() + ": " + w);
            if (t.getMobWeapons() != null) t.getMobWeapons().entrySet().forEach(m -> {
                assertTrue(t.getMobs().contains(m.getKey()), t.getName() + ": mob " + m.getKey());
                m.getValue().getAsJsonArray().forEach(w -> assertTrue(items.contains(w.getAsString()), t.getName() + ": " + w));
            });
            for (String b : r.getBanned()) assertTrue(items.contains(b), t.getName() + ": " + b);
            if (r.getWeaponCategories() != null) {
                for (String c : r.getWeaponCategories()) assertTrue(categories.contains(c), t.getName() + ": " + c);
            }
        }
    }

    @Test
    @DisplayName("an allowed-styles list leaves the other styles with no set")
    void stylesList() {
        Monster verzik = mob("Verzik Vitur", "hard mode, phase 3");
        assertTrue(best(verzik, Style.MELEE, 379).isEmpty());
        assertFalse(best(verzik, Style.RANGED, 379).isEmpty());
        assertTrue(CombatAchievements.tasksFor(mob("Verzik Vitur", "hard mode, phase 1")).stream().noneMatch(t -> t.getId() == 379),
            "a version-scoped task skips the other phases");
    }

    @Test
    @DisplayName("one attack type: Sarachnis on a crush weapon")
    void attackType() {
        String type = best(mob("Sarachnis"), Style.MELEE, 210).get(0).getAttackType();
        assertTrue(type.startsWith("crush"), type);
    }

    @Test
    @DisplayName("weapon categories and demonbane: a salamander, a spear or halberd, a demonbane weapon, none")
    void weaponKinds() {
        assertEquals("salamander", set(mob("Phantom Muspah"), Style.RANGED, 472).getWeapon().getCategoryLower());
        assertTrue(Set.of("spear", "polearm").contains(set(mob("Sol Heredit"), Style.MELEE, 540).getWeapon().getCategoryLower()));
        assertTrue(set(mob("Skotizo"), Style.MELEE, 48).getWeapon().isDemonbane());
        assertFalse(set(mob("Skotizo"), Style.MELEE, 49).getWeapon().isDemonbane());
    }

    @Test
    @DisplayName("no weapon: fists on Vorkath")
    void unarmed() {
        List<DpsResult> results = best(mob("Vorkath"), Style.MELEE, 275);
        assertFalse(results.isEmpty());
        assertNull(results.get(0).getLoadout().getWeapon());
        assertTrue(results.get(0).getDps() > 0);
    }

    @Test
    @DisplayName("one-handed with a shield worn: the Doom")
    void shieldWorn() {
        Loadout set = set(mob("Doom of Mokhaiotl"), Style.MELEE, 620);
        assertNotNull(set.get(GearSlot.SHIELD));
        assertFalse(set.getWeapon() != null && set.getWeapon().isTwoHanded());
    }

    @Test
    @DisplayName("counted items, value and level caps: anti-dragon shield, two Hueycoatl pieces, 2m, tier 74")
    void counts() {
        assertTrue(set(mob("King Black Dragon"), Style.MELEE, 166).get(GearSlot.SHIELD).getNameLower().startsWith("anti-dragon shield"));
        assertTrue(SetRules.total(set(mob("The Hueycoatl"), Style.RANGED, 575), "has:hueycoatl hide") >= 2);
        assertTrue(SetRules.total(set(mob("Vardorvis"), Style.MELEE, 493), "price") <= 2_000_000);
        for (Gear item : set(mob("Zebak"), Style.RANGED, 427).getGear().values()) {
            if (item != null) assertTrue(item.getRequirements().getSkills().values().stream().allMatch(l -> l <= 74), item.getName());
        }
    }

    @Test
    @DisplayName("spell families: earth spells on the Hueycoatl")
    void spellFamily() {
        DpsResult result = best(mob("The Hueycoatl"), Style.MAGIC, 574).get(0);
        assertTrue(Names.lower(result.getSpellName()).contains("earth"), result.getSpellName());
    }

    @Test
    @DisplayName("tasks combine with AND: KBD stab and the anti-dragon shield together; Skotizo demonbane on and off has no set")
    void tasksAnd() {
        PageState state = new PageState();
        state.setParam("caPicks", "165,166");
        SetRules both = state.computeParams().setRules;
        OptimizationRequest request = new OptimizationRequest(mob("King Black Dragon"), Style.MELEE, PlayerLevels.MAXED,
            PrayerBonuses.bestAvailable(PlayerLevels.MAXED, new PrayerUnlocks(true, true, true, true, true)),
            null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 3).withSetRules(both);
        DpsResult kbd = new LoadoutOptimizer().optimize(data, request).get(0);
        assertTrue(kbd.getAttackType().startsWith("stab"), kbd.getAttackType());
        assertTrue(kbd.getLoadout().get(GearSlot.SHIELD).getNameLower().startsWith("anti-dragon shield"));
        SetRules clash = CombatAchievements.byId(48).getRules().and(CombatAchievements.byId(49).getRules());
        assertTrue(new LoadoutOptimizer().optimize(data, request.withMonster(mob("Skotizo")).withSetRules(clash)).isEmpty(),
            "demonbane required and banned: no set");
        SetRules lists = CombatAchievements.byId(472).getRules().and(SetRules.builder().weapons(Set.of("toxic blowpipe")).build());
        assertFalse(lists.allows(data.getGearItems(GearSlot.WEAPON).stream().filter(i -> i.getNameLower().equals("toxic blowpipe"))
            .findFirst().orElseThrow()), "each weapon list must hold");
    }

    @Test
    @DisplayName("Vorkath runs the standard book for Crumble Undead and carries a slayer's staff")
    void vorkathCrumble() {
        assertEquals("standard", MonsterSpellbooks.bookFor(mob("Vorkath")));
        assertEquals("Crumble Undead", MonsterSpellbooks.spellFor(mob("Vorkath")));
        assertTrue(RequiredGear.swapIds(mob("Vorkath")).containsAll(List.of(21255, 4170)));
    }

    @Test
    @DisplayName("one spellbook reaches the compute; the no-prayer tasks are gone and the Mantis bans restores")
    void prayerAndBook() {
        PageState state = new PageState();
        state.setParam("caPicks", "501");
        assertEquals("arceuus", state.computeParams().spellbookLock);
        assertTrue(CombatAchievements.byId(617).getRules().isNoPrayerRestore(), "The Praying Mantis keeps prayer, bans restores");
        for (int gone : new int[]{30, 585, 126, 596, 250, 274, 154, 454}) assertNull(CombatAchievements.byId(gone), "mechanic-only " + gone);
    }

    @Test
    @DisplayName("round 3: Bryophyta on a poisoned weapon, free-to-play tasks lock F2P, Scurrius on a bone weapon")
    void round3() {
        Gear weapon = set(mob("Bryophyta"), Style.MELEE, 40).getWeapon();
        assertTrue(SetRules.poisons(weapon), weapon.label());
        for (String[] pick : new String[][]{{"Bryophyta", "41"}, {"Obor", "134"}}) {
            PageState state = new PageState();
            state.select(mob(pick[0]));
            state.setParam("caPicks", pick[1]);
            assertTrue(state.computeParams().f2pOnly, pick[0] + " runs F2P");
            assertNotEquals(Boolean.TRUE, state.paramsNode().get("f2pOnly"), "the F2P param itself is untouched");
        }
        assertTrue(set(mob("Scurrius"), Style.MELEE, 523).getWeapon().getNameLower().startsWith("bone "));
    }

    @Test
    @DisplayName("Betrayal is one task: each moon binds its own drop, the group any of them")
    void betrayalOnce() {
        List<Monster> moons = List.of(mob("Blood Moon"), mob("Blue Moon"), mob("Eclipse Moon"));
        for (Monster moon : moons) {
            assertEquals(1, CombatAchievements.tasksFor(moon).stream().filter(t -> t.getName().equals("Betrayal")).count());
        }
        assertEquals("blue moon spear", set(mob("Blue Moon"), Style.MELEE, 533).getWeapon().getNameLower());
        assertEquals(Set.of("dual macuahuitl", "blue moon spear", "eclipse atlatl"),
            CombatAchievements.byId(533).rulesFor(moons).getWeapons());
    }
}
